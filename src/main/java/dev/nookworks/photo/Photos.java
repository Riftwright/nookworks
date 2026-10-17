package dev.nookworks.photo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The world's photos, one file each under {@code data/nookworks/photos/<number>.dat}, numbered as maps are. A camera keeps
 * the numbers of its last {@link #KEPT}; a photo pushed out of it that was never printed is let go - its file deleted -
 * and one that was printed stays for as long as the world, as the posters on its walls show it.
 * <p>
 * The client asks for a photo's pixels when it has one to draw ({@link #ask}): given for a printed photo, or one in a
 * camera the asking player carries. With the server setting {@code playerPhotos} off, cameras take nothing and the server
 * sends no photo at all - posters of them show blank paper, in every game, as the setting turns - and an operator can
 * take one photo out of the world with {@code /nookworks photo remove}.
 * <p>
 * What a game may ask is bounded (review r12): a number never given out is turned away before any file is looked for,
 * the photos read are kept only {@link #CACHED} at a time, and each player's game has {@link #ASKS} asks in hand, one
 * coming back a tick - a modified client asking for every number there is gets nowhere and fills nothing.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Photos {
    /** How many photos a camera keeps. */
    public static final int KEPT = 9;
    /** Ticks between two photos from one camera. */
    public static final int COOLDOWN = 10;

    /** How many photos are kept read, the ones used last; any other is read from its file again when wanted. */
    public static final int CACHED = 256;
    /** Asks a player's game may make at once; one more comes back each tick (a wall of photos coming into view asks for each). */
    public static final int ASKS = 128;

    /** Where the client takes the pixels the server sends (set by the client's photo store). */
    public static Consumer<NookworksNetwork.PhotoPixels> received = payload -> {};
    /** Where the client hears whether this server shows photos. */
    public static Consumer<Boolean> allowedHere = on -> {};

    /** Each player's asks in hand, and the tick they were counted at. */
    private static final Map<ServerPlayer, long[]> ASKED = new WeakHashMap<>();

    private Photos() {}

    /** The next number to give out, and the photos read so far (a gap for one that is not there). */
    public static final class Index extends SavedData {
        static final Codec<Index> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("next", 1).forGetter(index -> index.next)).apply(i, Index::new));
        static final SavedDataType<Index> TYPE = new SavedDataType<>(Nookworks.id("photos"), Index::new, CODEC);

        private int next;
        /** Photos printed this run whose files could not take the mark: kept from being let go, whatever the cache drops. */
        private final Set<Integer> printedUnsaved = new HashSet<>();
        /** The photos read last, by number, least recent first (a gap for one deleted). */
        private final Map<Integer, Optional<Photo>> read = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Integer, Optional<Photo>> eldest) {
                return this.size() > CACHED;
            }
        };

        Index() {
            this(1);
        }

        private Index(int next) {
            this.next = Math.max(1, next);
        }
    }

    private static Index index(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(Index.TYPE);
    }

    /** Where photo {@code id} is kept. Public for tests. */
    public static Path file(MinecraftServer server, int id) {
        return server.getWorldPath(LevelResource.DATA).resolve(Nookworks.MODID).resolve("photos").resolve(id + ".dat");
    }

    /** The number the next photo will be given. For tests. */
    public static int nextNumber(MinecraftServer server) {
        return index(server).next;
    }

    /** How many photos (and gaps) are kept read now. For tests. */
    public static int readCount(MinecraftServer server) {
        return index(server).read.size();
    }

    /** Photo {@code id}, or null when there is none (never taken, let go, or removed). */
    public static @Nullable Photo get(MinecraftServer server, int id) {
        Index index = index(server);
        if (id <= 0 || id >= index.next) {
            return null; // never given out: not looked for on the disk, nor remembered
        }
        return index.read.computeIfAbsent(id, key -> {
            Path file = file(server, key);
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                return Photo.CODEC.parse(NbtOps.INSTANCE, tag).resultOrPartial(error -> Nookworks.LOGGER.warn("Photo {}: {}", key, error));
            } catch (IOException e) {
                Nookworks.LOGGER.warn("Could not read photo {}", key, e);
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Keeps {@code photo} under the next number, which it returns; 0 when its file could not be written (nothing is kept). */
    public static int add(MinecraftServer server, Photo photo) {
        Index index = index(server);
        int id = index.next++;
        // a number whose file is there already is someone's photo: after a crash the count saved can be behind the files
        // written (r15: the next photo took that number and wrote over it)
        while (Files.isRegularFile(file(server, id)) || Files.isRegularFile(file(server, id).resolveSibling(id + ".dat.partial"))) {
            id = index.next++;
        }
        index.setDirty();
        return write(server, id, photo) ? id : 0;
    }

    /** Writes photo {@code id}'s file, and only once it is there keeps it read; false when the file could not be written. */
    private static boolean write(MinecraftServer server, int id, Photo photo) {
        Path file = file(server, id);
        Path partial = file.resolveSibling(id + ".dat.partial");
        try {
            Files.createDirectories(file.getParent());
            NbtIo.writeCompressed((CompoundTag) Photo.CODEC.encodeStart(NbtOps.INSTANCE, photo).getOrThrow(), partial);
            Files.move(partial, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("Could not save photo {}", id, e);
            try {
                Files.deleteIfExists(partial); // no half-written file left beside the photos
            } catch (IOException ignored) {
                // left; the next save of the number writes over it
            }
            return false;
        }
        index(server).read.put(id, Optional.of(photo));
        return true;
    }

    /**
     * Marks photo {@code id} as printed, so it stays when the camera lets it go. Should its file not take the mark, it is
     * kept printed for as long as the server runs (and marked again at the next print), rather than let go under a poster.
     */
    public static void printed(MinecraftServer server, int id) {
        Photo photo = get(server, id);
        if (photo != null && !photo.printed() && !write(server, id, photo.asPrinted())) {
            index(server).read.put(id, Optional.of(photo.asPrinted()));
            index(server).printedUnsaved.add(id);
        }
    }

    /** What taking a photo out came to. */
    public enum Removal { REMOVED, NONE, FAILED }

    /**
     * Takes photo {@code id} out of the world, telling every player's game to forget it - once its file is gone: one that
     * could not be deleted (in use, say) stays as it was, and the operator is told so, rather than come back at the next
     * start after being said gone.
     */
    public static Removal remove(MinecraftServer server, int id) {
        if (get(server, id) == null) {
            return Removal.NONE;
        }
        try {
            Files.deleteIfExists(file(server, id));
        } catch (IOException e) {
            Nookworks.LOGGER.warn("Could not delete photo {}", id, e);
            return Removal.FAILED;
        }
        index(server).read.put(id, Optional.empty());
        NookworksNetwork.PhotoPixels gone = NookworksNetwork.PhotoPixels.none(id, NookworksNetwork.PhotoPixels.GONE);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(NookworksNetwork.PhotoPixels.TYPE)) {
                PacketDistributor.sendToPlayer(player, gone);
            }
        }
        return Removal.REMOVED;
    }

    /** A photo pushed out of a camera: gone, unless it was printed. */
    private static void letGo(MinecraftServer server, int id) {
        Photo photo = get(server, id);
        if (photo != null && !photo.printed() && !index(server).printedUnsaved.contains(id)) {
            remove(server, id);
        }
    }

    public static boolean allowed() {
        return !NookworksConfig.SPEC.isLoaded() || NookworksConfig.PLAYER_PHOTOS.get();
    }

    /** The camera {@code player} has up, or holds; empty when they have none in hand. */
    public static ItemStack cameraInHand(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.CAMERA.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * {@code player} took a photo with the camera in their hand: {@code pixels} of the view, as their game cropped and
     * shrank it. Kept, and its number put in the camera, the oldest let go past {@link #KEPT}; the shutter heard around.
     * Nothing without a camera in hand, while it cools down, or with photos turned off (which the player is told).
     */
    public static int take(ServerPlayer player, byte[] pixels) {
        ItemStack camera = cameraInHand(player);
        if (camera.isEmpty() || pixels.length != Photo.BYTES || player.getCooldowns().isOnCooldown(camera)) {
            return 0;
        }
        if (!allowed()) {
            player.sendOverlayMessage(Component.translatable("item.nookworks.camera.off"));
            return 0;
        }
        MinecraftServer server = player.level().getServer();
        long day = server.overworld().getOverworldClockTime() / 24000L;
        int id = add(server, new Photo(pixels.clone(), player.getName().getString(), player.getUUID(), day, false));
        if (id == 0) {
            player.getCooldowns().addCooldown(camera, COOLDOWN);
            player.sendOverlayMessage(Component.translatable("item.nookworks.camera.failed"));
            return 0;
        }
        List<Integer> kept = new ArrayList<>(CameraItem.photos(camera));
        kept.add(id);
        while (kept.size() > KEPT) {
            letGo(server, kept.removeFirst());
        }
        camera.set(ModDataComponents.CAMERA_PHOTOS.get(), List.copyOf(kept));
        player.getCooldowns().addCooldown(camera, COOLDOWN);
        player.level().playSound(player, player.getX(), player.getEyeY(), player.getZ(), ModSounds.CAMERA_SHUTTER.get(), SoundSource.PLAYERS, 0.9F, 1.0F);
        return id;
    }

    /** Whether {@code player} carries a camera holding photo {@code id}. */
    public static boolean carries(ServerPlayer player, int id) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ModItems.CAMERA.get()) && CameraItem.photos(stack).contains(id)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether {@code player} may be sent photo {@code id}: one printed (it hangs on walls for anyone to see), their own,
     * or one in a camera they carry - not anybody's photo by its number. None at all with photos turned off.
     */
    public static boolean mayShow(ServerPlayer player, int id) {
        Photo photo = allowed() ? get(player.level().getServer(), id) : null;
        return photo != null && mayShow(player, id, photo);
    }

    private static boolean mayShow(ServerPlayer player, int id, Photo photo) {
        return photo.printed() || photo.authorId().equals(player.getUUID()) || carries(player, id);
    }

    /** What {@code player}'s game is told of photo {@code id}: its pixels, or why not. */
    public static NookworksNetwork.PhotoPixels answer(ServerPlayer player, int id) {
        if (!allowed()) {
            return NookworksNetwork.PhotoPixels.none(id, NookworksNetwork.PhotoPixels.OFF);
        }
        Photo photo = get(player.level().getServer(), id);
        if (photo == null) {
            return NookworksNetwork.PhotoPixels.none(id, NookworksNetwork.PhotoPixels.GONE);
        }
        return mayShow(player, id, photo) ? NookworksNetwork.PhotoPixels.shown(id, photo)
                : NookworksNetwork.PhotoPixels.none(id, NookworksNetwork.PhotoPixels.HIDDEN);
    }

    /** Whether {@code player}'s game has an ask in hand, spending it if so: {@link #ASKS} at most, one back a tick. */
    public static boolean mayAsk(ServerPlayer player) {
        long now = player.level().getServer().getTickCount();
        long[] asked = ASKED.computeIfAbsent(player, p -> new long[] {ASKS, now});
        asked[0] = Math.min(ASKS, asked[0] + Math.max(0, now - asked[1]));
        asked[1] = now;
        if (asked[0] < 1) {
            return false;
        }
        asked[0]--;
        return true;
    }

    /** {@code player}'s game asks for photo {@code id}: sent if they may see it, else why not (it shows blank paper). */
    public static void ask(ServerPlayer player, int id) {
        if (!player.connection.hasChannel(NookworksNetwork.PhotoPixels.TYPE) || !mayAsk(player)) {
            return; // asked too much at once: unanswered, its game asks again in a while
        }
        PacketDistributor.sendToPlayer(player, answer(player, id));
    }

    /** A player joining is told whether this server shows photos. */
    @SubscribeEvent
    static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(NookworksNetwork.PhotosAllowed.TYPE)) {
            PacketDistributor.sendToPlayer(player, new NookworksNetwork.PhotosAllowed(allowed()));
        }
    }

    /**
     * The settings read again while the server runs: every game is told whether photos are on, on the server's own thread
     * (the file is watched from another), and one that has it so already changes nothing. Said each time, not only on a
     * change from what was said before: a server started with photos off and turned on was otherwise never heard.
     */
    @SubscribeEvent
    static void onConfig(ModConfigEvent.Reloading event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.getConfig().getSpec() != NookworksConfig.SPEC || server == null) {
            return;
        }
        server.execute(() -> {
            NookworksNetwork.PhotosAllowed said = new NookworksNetwork.PhotosAllowed(allowed());
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.connection.hasChannel(NookworksNetwork.PhotosAllowed.TYPE)) {
                    PacketDistributor.sendToPlayer(player, said);
                }
            }
        });
    }
}
