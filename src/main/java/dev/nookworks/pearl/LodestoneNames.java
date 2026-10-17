package dev.nookworks.pearl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * Lodestones' names, as Waystones names its stones: so a pocket of Lode Pearls says which is which. A lodestone is named
 * the moment it is set down - by the name it was given at an anvil, or else after where it stands ("Plains", and
 * "Plains 2" for the next there) - and an empty-handed click on it opens a box to rename it, any time; cleared, it goes
 * back to a name of its own. A pearl bound to it carries its name ({@link LodePearlItem}), and so does a lodestone
 * compass's tooltip.
 * <p>
 * Kept for the whole world, by where each stands; sent to every player as they join and as names change, which is how
 * the client knows them. A lodestone taken away by a player is forgotten; one gone some other way is forgotten when a
 * pearl finds it gone, or when another is set down in its place.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class LodestoneNames extends SavedData {
    /** As long as a name an anvil gives. */
    public static final int MAX_LENGTH = 50;
    /** How far a player may be from the lodestone they rename, as for clicking it. */
    private static final double REACH = 8.0;
    /** The fewest ticks between two renames from one player's box: a quarter of a second. */
    private static final int BOX_EVERY = 5;
    /** When each player's box last sent a rename that was heard, in server ticks. Server thread only. */
    private static final Map<ServerPlayer, Integer> BOX_SENT = new WeakHashMap<>();

    private record Entry(Component name, boolean custom) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ComponentSerialization.CODEC.fieldOf("name").forGetter(Entry::name),
                Codec.BOOL.optionalFieldOf("custom", false).forGetter(Entry::custom)).apply(i, Entry::new));
    }

    private record Named(GlobalPos pos, Entry entry) {
        static final Codec<Named> CODEC = RecordCodecBuilder.create(i -> i.group(
                GlobalPos.CODEC.fieldOf("pos").forGetter(Named::pos),
                Entry.CODEC.fieldOf("entry").forGetter(Named::entry)).apply(i, Named::new));
    }

    public static final Codec<LodestoneNames> CODEC = RecordCodecBuilder.create(i -> i.group(
            Named.CODEC.listOf().optionalFieldOf("names", List.of()).forGetter(n -> n.names.entrySet().stream()
                    .map(e -> new Named(e.getKey(), e.getValue())).toList()),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("counts", Map.of()).forGetter(n -> n.counts))
            .apply(i, LodestoneNames::new));
    public static final SavedDataType<LodestoneNames> TYPE = new SavedDataType<>(Nookworks.id("lodestone_names"), LodestoneNames::new, CODEC);

    /** On the client: the names the server sent. */
    private static final Map<GlobalPos, Component> CLIENT = new ConcurrentHashMap<>();
    /** On the client: opens the box to rename the lodestone at a place (set by the client's set-up). */
    public static Consumer<GlobalPos> editor = pos -> {};

    private final Map<GlobalPos, Entry> names;
    /**
     * Kept as it was read, for older saves: lodestones are numbered by what is free now ({@link #autoName}), no longer
     * by how many have ever been named.
     */
    private final Map<String, Integer> counts;

    public LodestoneNames() {
        this(List.of(), Map.of());
    }

    private LodestoneNames(List<Named> names, Map<String, Integer> counts) {
        this.names = new HashMap<>();
        for (Named named : names) {
            this.names.put(named.pos(), named.entry());
        }
        this.counts = new HashMap<>(counts);
    }

    public static LodestoneNames get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * The name of the lodestone at {@code pos}, if it has one: the server's own on the server thread, else what the
     * server sent this client.
     */
    public static Optional<Component> of(GlobalPos pos) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            Entry entry = get(server).names.get(pos);
            return entry == null ? Optional.empty() : Optional.of(entry.name());
        }
        return Optional.ofNullable(CLIENT.get(pos));
    }

    /** The lodestone at {@code pos} in {@code level}, named after where it stands if it has no name yet; its name. */
    public static Component ensure(ServerLevel level, BlockPos pos) {
        GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
        LodestoneNames names = get(level.getServer());
        Entry entry = names.names.get(at);
        if (entry == null) {
            entry = new Entry(names.autoName(level, pos, null), false);
            names.put(level.getServer(), at, entry);
        }
        return entry.name();
    }

    /**
     * A name after the biome {@code pos} is in: the biome's own for the first, then numbered, each taking the lowest
     * number no lodestone has now (so clearing a name, or breaking a stone and setting it down again, does not count
     * on: "Plains" stays "Plains", and a "Plains 2" broken leaves 2 free for the next). {@code except} is the lodestone
     * being renamed, whose own name is free to take again.
     */
    private Component autoName(ServerLevel level, BlockPos pos, @Nullable GlobalPos except) {
        var biome = level.getBiome(pos).unwrapKey();
        String key = biome.map(k -> k.identifier().toLanguageKey("biome")).orElse("block.minecraft.lodestone");
        java.util.Set<Integer> taken = new java.util.HashSet<>();
        this.names.forEach((at, entry) -> {
            if (!entry.custom() && !at.equals(except)) {
                int n = numberOf(entry.name(), key);
                if (n > 0) {
                    taken.add(n);
                }
            }
        });
        int n = 1;
        while (taken.contains(n)) {
            n++;
        }
        Component place = Component.translatable(key);
        return n == 1 ? place : Component.translatable("lodestone.nookworks.numbered", place, n);
    }

    /** The number of a name {@link #autoName} gave after the biome {@code key} (1 for the plain one), or 0. */
    private static int numberOf(Component name, String key) {
        if (!(name.getContents() instanceof TranslatableContents contents)) {
            return 0;
        }
        if (contents.getKey().equals(key)) {
            return 1;
        }
        Object[] args = contents.getArgs();
        if (contents.getKey().equals("lodestone.nookworks.numbered") && args.length == 2
                && args[0] instanceof Component place && place.getContents() instanceof TranslatableContents inner
                && inner.getKey().equals(key)) {
            Object n = args[1];
            if (n instanceof Integer i) {
                return i;
            }
            try {
                return Integer.parseInt(n instanceof Component c ? c.getString() : String.valueOf(n));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * {@code player} renames the lodestone at {@code pos} {@code text} - cleared, it gets a name of its own again (the
     * one it had, if it had one of its own). Only a lodestone there, within their reach, by a player who may change
     * blocks (not a spectator, nor in adventure) and whom claim mods and spawn protection let change that one - asked
     * here too, not only as the click opened the box: the box's answer comes back by itself, and a client could send it
     * for any lodestone. Whether it was renamed.
     */
    public static boolean rename(ServerPlayer player, BlockPos pos, String text) {
        ServerLevel level = player.level();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(Blocks.LODESTONE) || !player.isWithinBlockInteractionRange(pos, REACH - 4.5)
                || !mayRename(player) || !Permission.mayPlace(level, pos, Direction.UP, player)) {
            return false;
        }
        String name = StringUtil.filterText(text).strip();
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH);
        }
        GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
        LodestoneNames names = get(level.getServer());
        Entry had = names.names.get(at);
        Entry next = !name.isEmpty() ? new Entry(Component.literal(name), true)
                : had != null && !had.custom() ? had : new Entry(names.autoName(level, pos, at), false);
        if (next.equals(had)) {
            return true; // the name it has: nothing to save, nor to send every player on the server
        }
        names.put(level.getServer(), at, next);
        level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.BLOCKS, 0.6F, 1.4F);
        return true;
    }

    /**
     * A rename as a player's box sends it ({@link #rename}), at most one every {@link #BOX_EVERY} ticks for each player:
     * a box sends one as it closes, and a client sending them in a stream had each asked of claim mods and sent on to
     * every player on the server. Whether it was renamed.
     */
    public static boolean renameFromBox(ServerPlayer player, BlockPos pos, String text) {
        int now = player.level().getServer().getTickCount();
        Integer last = BOX_SENT.get(player);
        if (last != null && now - last >= 0 && now - last < BOX_EVERY) {
            return false;
        }
        BOX_SENT.put(player, now);
        return rename(player, pos, text);
    }

    /** The lodestone at {@code pos} gone. */
    public static void forget(ServerLevel level, BlockPos pos) {
        GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
        LodestoneNames names = get(level.getServer());
        if (names.names.remove(at) != null) {
            names.setDirty();
            send(level.getServer(), Map.of(at, Optional.empty()), false);
        }
    }

    private void put(MinecraftServer server, GlobalPos at, Entry entry) {
        this.names.put(at, entry);
        this.setDirty();
        send(server, Map.of(at, Optional.of(entry.name())), false);
    }

    // ---- to the clients ----

    /** {@code changes} - a name, or none for a lodestone gone - to every player; with {@code all}, the whole list. */
    private static void send(MinecraftServer server, Map<GlobalPos, Optional<Component>> changes, boolean all) {
        var payload = new NookworksNetwork.LodestoneNamesPayload(changes, all);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.connection.hasChannel(NookworksNetwork.LodestoneNamesPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** On the client: names from the server - every one at once (replacing those it had) or a few changed. */
    public static void received(Map<GlobalPos, Optional<Component>> changes, boolean all) {
        if (all) {
            CLIENT.clear();
        }
        changes.forEach((pos, name) -> name.ifPresentOrElse(n -> CLIENT.put(pos, n), () -> CLIENT.remove(pos)));
    }

    /** On the client, leaving a world: another's names are its own. */
    public static void clientForget() {
        CLIENT.clear();
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.connection.hasChannel(NookworksNetwork.LodestoneNamesPayload.TYPE)) {
            Map<GlobalPos, Optional<Component>> all = new HashMap<>();
            get(player.level().getServer()).names.forEach((pos, entry) -> all.put(pos, Optional.of(entry.name())));
            PacketDistributor.sendToPlayer(player, new NookworksNetwork.LodestoneNamesPayload(all, true));
        }
    }

    // ---- in the world ----

    /**
     * Set down: named at once - by the name the stone was given at an anvil, or after where it stands. Last, after claim
     * mods may have stopped it (a placing they stop never comes here), and not when it is only our own question
     * ({@link Permission#asking}: renaming asks as a change to the block).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!event.getPlacedBlock().is(Blocks.LODESTONE) || !(event.getLevel() instanceof ServerLevel level) || Permission.asking()) {
            return;
        }
        GlobalPos at = GlobalPos.of(level.dimension(), event.getPos().immutable());
        LodestoneNames names = get(level.getServer());
        Component given = null;
        if (event.getEntity() instanceof Player player) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack held = player.getItemInHand(hand);
                if (held.is(Items.LODESTONE) && held.has(DataComponents.CUSTOM_NAME)) {
                    given = held.get(DataComponents.CUSTOM_NAME);
                    break;
                }
            }
        }
        names.put(level.getServer(), at, given != null ? new Entry(given, true) : new Entry(names.autoName(level, event.getPos(), at), false));
    }

    /**
     * Taken away by a player - last, after claim mods may have stopped it (a break they stop never comes here). Not when
     * it is only our own question ({@link Permission#asking}): a Repair Box or a pipe beside the stone asks whose it is,
     * and its name went every five minutes (r15 fix check 3).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onBroken(BreakBlockEvent event) {
        if (event.getState().is(Blocks.LODESTONE) && event.getLevel() instanceof ServerLevel level && !Permission.asking()) {
            forget(level, event.getPos());
        }
    }

    /**
     * An empty-handed click on a lodestone opens the box to rename it (after claim mods have had their say: a click they
     * stop never comes here) - when the other hand is empty, or holds what is kept there for good and does nothing to a
     * block (a shield, a totem, a map, a clock, a spyglass). Anything else there - a compass to point at the lodestone,
     * a pearl to bind to it, a block or a bucket - is left to vanilla, which lets the other hand act when the main hand
     * does nothing. Not for a spectator, nor a player who may not change blocks.
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onClick(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (event.getHand() != InteractionHand.MAIN_HAND || !event.getItemStack().isEmpty() || !level.getBlockState(event.getPos()).is(Blocks.LODESTONE)
                || !mayRename(event.getEntity()) || !leavesRenaming(event.getEntity().getOffhandItem())) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level instanceof ServerLevel server && event.getEntity() instanceof ServerPlayer player) {
            ensure(server, event.getPos()); // a lodestone from before names: named now, so the box has one to show
            if (player.connection.hasChannel(NookworksNetwork.OpenLodestoneName.TYPE)) {
                PacketDistributor.sendToPlayer(player, new NookworksNetwork.OpenLodestoneName(GlobalPos.of(server.dimension(), event.getPos().immutable())));
            }
        }
    }

    /** Whether {@code player} may rename lodestones at all: not as a spectator, nor where they may not build. */
    private static boolean mayRename(Player player) {
        return !player.isSpectator() && player.mayBuild();
    }

    /** Whether {@code held}, in the other hand, leaves an empty-handed click to rename: nothing, or what is kept there. */
    private static boolean leavesRenaming(ItemStack held) {
        return held.isEmpty() || held.is(Items.SHIELD) || held.is(Items.TOTEM_OF_UNDYING) || held.is(Items.MAP)
                || held.is(Items.FILLED_MAP) || held.is(Items.CLOCK) || held.is(Items.SPYGLASS);
    }

    /** On the client: the server says to open the box for the lodestone at {@code pos}. */
    public static void openEditor(GlobalPos pos) {
        editor.accept(pos);
    }

    /** What the box shows to begin with: the lodestone's name as this client reads it. */
    public static String nameText(@Nullable GlobalPos pos) {
        Component name = pos == null ? null : CLIENT.get(pos);
        return name == null ? "" : name.getString();
    }
}
