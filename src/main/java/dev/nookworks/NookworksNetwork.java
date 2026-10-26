package dev.nookworks;

import dev.nookworks.feature.Squinting;
import dev.nookworks.inventory.ChestSorting;
import dev.nookworks.inventory.RecipeChoice;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.Photos;
import dev.nookworks.trading.TradingCounterView;
import dev.nookworks.trading.TradingCounterMenu;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.Component;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.BlockPos;
import java.util.Optional;
import java.util.HashMap;
import dev.nookworks.pearl.LodestoneNames;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.recycling.Compressing;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotLine;
import dev.nookworks.registry.ModCreativeTabs;
import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonTravel;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * What the server tells its players besides what Minecraft and NeoForge already send: the loot tables our recipe
 * pages show ({@link LootSummaries}), as the server's data packs have them - they go out with the rest of the data on
 * joining and on every {@code /reload}, before the recipes that set JEI going, so its pages are built from them - and
 * with them which parts of the mod its world has ({@link WorldFeatures}), which the creative tabs and JEI follow; on
 * joining, its start-up settings, which a client with other ones is told of ({@link NookworksStartupConfig#mismatch});
 * and, as a player goes through a Dock-a-Daemon Door, whose gate it was, for the screen while the far side loads.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class NookworksNetwork {
    /** The tables, by loot table id. */
    public record LootTables(Map<Identifier, List<LootSummaries.Drop>> tables) implements CustomPacketPayload {
        public static final Type<LootTables> TYPE = new Type<>(Nookworks.id("loot_tables"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LootTables> STREAM_CODEC =
                LootSummaries.STREAM_CODEC.map(LootTables::new, LootTables::tables);

        @Override
        public Type<LootTables> type() {
            return TYPE;
        }
    }

    /**
     * What an Iron Pot holds, for the action bar: its liquid (0 none, 1 water, 2 oil), how much of what it holds, and the
     * things in it; the client fits the line to its screen ({@link PotLine}).
     */
    public record PotContents(int liquid, int amount, int capacity, List<ItemStack> items) implements CustomPacketPayload {
        public static final Type<PotContents> TYPE = new Type<>(Nookworks.id("pot_contents"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PotContents> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PotContents::liquid, ByteBufCodecs.VAR_INT, PotContents::amount,
                ByteBufCodecs.VAR_INT, PotContents::capacity,
                ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(IronPotBlockEntity.SLOTS)), PotContents::items, PotContents::new);

        @Override
        public Type<PotContents> type() {
            return TYPE;
        }
    }

    /** What nine of presses in a Compressor, on this server ({@link Compressing#compressible}). */
    public record Compressible(List<Item> items) implements CustomPacketPayload {
        public static final Type<Compressible> TYPE = new Type<>(Nookworks.id("compressible"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Compressible> STREAM_CODEC =
                ByteBufCodecs.registry(Registries.ITEM).apply(ByteBufCodecs.list(65536)).map(Compressible::new, Compressible::items);

        @Override
        public Type<Compressible> type() {
            return TYPE;
        }
    }

    /** The server's start-up settings, by name. */
    public record StartupSettings(Map<String, Boolean> values) implements CustomPacketPayload {
        public static final Type<StartupSettings> TYPE = new Type<>(Nookworks.id("startup_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, StartupSettings> STREAM_CODEC = ByteBufCodecs
                .<RegistryFriendlyByteBuf, String, Boolean, Map<String, Boolean>>map(LinkedHashMap::new,
                        ByteBufCodecs.stringUtf8(64), ByteBufCodecs.BOOL, 64)
                .map(StartupSettings::new, StartupSettings::values);

        @Override
        public Type<StartupSettings> type() {
            return TYPE;
        }
    }

    /** Which parts of the mod the server's world has, by name ({@link WorldFeatures#effective}). */
    public record WorldParts(Map<String, Boolean> parts) implements CustomPacketPayload {
        public static final Type<WorldParts> TYPE = new Type<>(Nookworks.id("world_parts"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WorldParts> STREAM_CODEC = ByteBufCodecs
                .<RegistryFriendlyByteBuf, String, Boolean, Map<String, Boolean>>map(LinkedHashMap::new,
                        ByteBufCodecs.stringUtf8(64), ByteBufCodecs.BOOL, 64)
                .map(WorldParts::new, WorldParts::parts);

        @Override
        public Type<WorldParts> type() {
            return TYPE;
        }
    }

    /** A player going through a Dock-a-Daemon Door showing the gate of {@code gate} ({@link DaemonDestinations#gate}). */
    public record DaemonTrip(ResourceKey<Level> gate) implements CustomPacketPayload {
        public static final Type<DaemonTrip> TYPE = new Type<>(Nookworks.id("daemon_trip"));
        public static final StreamCodec<ByteBuf, DaemonTrip> STREAM_CODEC =
                ResourceKey.streamCodec(Registries.DIMENSION).map(DaemonTrip::new, DaemonTrip::gate);

        @Override
        public Type<DaemonTrip> type() {
            return TYPE;
        }
    }

    /** Lodestones' names ({@link LodestoneNames}): every one, replacing what the client had, or a few changed (none: gone). */
    public record LodestoneNamesPayload(Map<GlobalPos, Optional<Component>> names, boolean all) implements CustomPacketPayload {
        public static final Type<LodestoneNamesPayload> TYPE = new Type<>(Nookworks.id("lodestone_names"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LodestoneNamesPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.map(HashMap::new, GlobalPos.STREAM_CODEC, ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC, 65536),
                LodestoneNamesPayload::names, ByteBufCodecs.BOOL, LodestoneNamesPayload::all, LodestoneNamesPayload::new);

        @Override
        public Type<LodestoneNamesPayload> type() {
            return TYPE;
        }
    }

    /** Open the box to rename the lodestone at {@code pos}. */
    public record OpenLodestoneName(GlobalPos pos) implements CustomPacketPayload {
        public static final Type<OpenLodestoneName> TYPE = new Type<>(Nookworks.id("open_lodestone_name"));
        public static final StreamCodec<ByteBuf, OpenLodestoneName> STREAM_CODEC = GlobalPos.STREAM_CODEC.map(OpenLodestoneName::new, OpenLodestoneName::pos);

        @Override
        public Type<OpenLodestoneName> type() {
            return TYPE;
        }
    }

    /** From a player: rename the lodestone at {@code pos} (in their own dimension) {@code name}; empty for a name of its own. */
    public record RenameLodestone(BlockPos pos, String name) implements CustomPacketPayload {
        public static final Type<RenameLodestone> TYPE = new Type<>(Nookworks.id("rename_lodestone"));
        public static final StreamCodec<ByteBuf, RenameLodestone> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, RenameLodestone::pos, ByteBufCodecs.stringUtf8(LodestoneNames.MAX_LENGTH * 4), RenameLodestone::name,
                RenameLodestone::new);

        @Override
        public Type<RenameLodestone> type() {
            return TYPE;
        }
    }

    /** A Trading Counter's screen, as it is now ({@link TradingCounterView}), for the menu {@code containerId} open on it. */
    public record TradingCounterViewPayload(int containerId, TradingCounterView view) implements CustomPacketPayload {
        public static final Type<TradingCounterViewPayload> TYPE = new Type<>(Nookworks.id("trading_counter_view"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TradingCounterViewPayload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TradingCounterViewPayload::containerId, TradingCounterView.STREAM_CODEC, TradingCounterViewPayload::view,
                TradingCounterViewPayload::new);

        @Override
        public Type<TradingCounterViewPayload> type() {
            return TYPE;
        }
    }

    /** The Sort button on a vanilla chest's screen ({@link ChestSorting}): sort the menu {@code containerId} in {@code order}. */
    /** Client to server: the player's squint key went down ({@code on}) or up (see {@link Squinting}). */
    public record Squint(boolean on) implements CustomPacketPayload {
        public static final Type<Squint> TYPE = new Type<>(Nookworks.id("squint"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Squint> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Squint::on, Squint::new);

        @Override
        public Type<Squint> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: what the open crafting grid could make, one of each thing, and which it makes ({@code selected});
     * an empty list when it makes one thing or none (see {@link RecipeChoice}).
     */
    public record RecipeChoices(int containerId, List<ItemStack> outputs, int selected) implements CustomPacketPayload {
        public static final Type<RecipeChoices> TYPE = new Type<>(Nookworks.id("recipe_choices"));
        public static final StreamCodec<RegistryFriendlyByteBuf, RecipeChoices> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, RecipeChoices::containerId, ItemStack.STREAM_CODEC.apply(ByteBufCodecs.list(RecipeChoice.MAX_OFFERED)), RecipeChoices::outputs,
                ByteBufCodecs.VAR_INT, RecipeChoices::selected, RecipeChoices::new);

        @Override
        public Type<RecipeChoices> type() {
            return TYPE;
        }
    }

    /** Client to server: the player picked the {@code index}th thing their crafting grid was said to make. */
    public record PickRecipe(int containerId, int index) implements CustomPacketPayload {
        public static final Type<PickRecipe> TYPE = new Type<>(Nookworks.id("pick_recipe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PickRecipe> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PickRecipe::containerId, ByteBufCodecs.VAR_INT, PickRecipe::index, PickRecipe::new);

        @Override
        public Type<PickRecipe> type() {
            return TYPE;
        }
    }

    /** Client to server: the game lost sight of its crafting grid's result, and asks for menu {@code containerId} again. */
    public record ShowGridAgain(int containerId) implements CustomPacketPayload {
        public static final Type<ShowGridAgain> TYPE = new Type<>(Nookworks.id("show_grid_again"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShowGridAgain> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ShowGridAgain::containerId, ShowGridAgain::new);

        @Override
        public Type<ShowGridAgain> type() {
            return TYPE;
        }
    }

    public record SortChest(int containerId, int order) implements CustomPacketPayload {
        public static final Type<SortChest> TYPE = new Type<>(Nookworks.id("sort_chest"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SortChest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SortChest::containerId, ByteBufCodecs.VAR_INT, SortChest::order, SortChest::new);

        @Override
        public Type<SortChest> type() {
            return TYPE;
        }
    }

    /** Client to server: a photo the player's Camera took, the pixels cropped and shrunk by their game (see {@link Photos#take}). */
    public record TakePhoto(byte[] pixels) implements CustomPacketPayload {
        public static final Type<TakePhoto> TYPE = new Type<>(Nookworks.id("take_photo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, TakePhoto> STREAM_CODEC =
                ByteBufCodecs.byteArray(Photo.BYTES).<RegistryFriendlyByteBuf>cast().map(TakePhoto::new, TakePhoto::pixels);

        @Override
        public Type<TakePhoto> type() {
            return TYPE;
        }
    }

    /** Client to server: send photo {@code id}, to draw it (see {@link Photos#ask}). */
    public record AskPhoto(int id) implements CustomPacketPayload {
        public static final Type<AskPhoto> TYPE = new Type<>(Nookworks.id("ask_photo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AskPhoto> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AskPhoto::id, AskPhoto::new);

        @Override
        public Type<AskPhoto> type() {
            return TYPE;
        }
    }

    /**
     * Server to client: photo {@code id}'s pixels and who took it ({@link #SHOWN}); or, with no pixels, why not: it is
     * {@link #GONE} (let go of, or taken out), {@link #HIDDEN} from this player for now (not printed, and in no camera
     * of theirs - which may change, so the game asks again while it shows the photo), or photos are {@link #OFF}.
     */
    public record PhotoPixels(int id, byte[] pixels, String author, byte status) implements CustomPacketPayload {
        public static final byte SHOWN = 0;
        public static final byte GONE = 1;
        public static final byte HIDDEN = 2;
        public static final byte OFF = 3;
        public static final Type<PhotoPixels> TYPE = new Type<>(Nookworks.id("photo_pixels"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PhotoPixels> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, PhotoPixels::id, ByteBufCodecs.byteArray(Photo.BYTES), PhotoPixels::pixels,
                ByteBufCodecs.stringUtf8(64), PhotoPixels::author, ByteBufCodecs.BYTE, PhotoPixels::status, PhotoPixels::new);

        public static PhotoPixels shown(int id, Photo photo) {
            return new PhotoPixels(id, photo.pixels(), photo.author(), SHOWN);
        }

        public static PhotoPixels none(int id, byte why) {
            return new PhotoPixels(id, new byte[0], "", why);
        }

        @Override
        public Type<PhotoPixels> type() {
            return TYPE;
        }
    }

    /** Server to client: whether this server shows photos (on joining, and as an operator turns the setting). */
    public record PhotosAllowed(boolean on) implements CustomPacketPayload {
        public static final Type<PhotosAllowed> TYPE = new Type<>(Nookworks.id("photos_allowed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PhotosAllowed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, PhotosAllowed::on, PhotosAllowed::new);

        @Override
        public Type<PhotosAllowed> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToClient(LootTables.TYPE, LootTables.STREAM_CODEC, (payload, context) -> LootSummaries.receive(payload.tables()));
        registrar.playToClient(RecipeChoices.TYPE, RecipeChoices.STREAM_CODEC, (payload, context) -> RecipeChoice.shown.accept(payload));
        registrar.playToServer(PickRecipe.TYPE, PickRecipe.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                RecipeChoice.pick(player, payload.containerId(), payload.index());
            }
        });
        registrar.playToServer(ShowGridAgain.TYPE, ShowGridAgain.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                RecipeChoice.showAgain(player, payload.containerId());
            }
        });
        registrar.playToClient(PotContents.TYPE, PotContents.STREAM_CODEC, (payload, context) -> PotLine.shown.accept(payload));
        registrar.playToClient(Compressible.TYPE, Compressible.STREAM_CODEC, (payload, context) -> Compressing.heard(Set.copyOf(payload.items())));
        registrar.playToClient(StartupSettings.TYPE, StartupSettings.STREAM_CODEC, (payload, context) ->
                NookworksStartupConfig.mismatch(payload.values()).ifPresent(message -> context.player().sendSystemMessage(message)));
        registrar.playToClient(WorldParts.TYPE, WorldParts.STREAM_CODEC, (payload, context) -> {
            WorldFeatures.heard(payload.parts(), context.connection().isMemoryConnection()); // (single player: the same world's)
            ModCreativeTabs.partsKnown();
        });
        registrar.playToClient(DaemonTrip.TYPE, DaemonTrip.STREAM_CODEC, (payload, context) -> DaemonTravel.tripping(payload.gate()));
        registrar.playToClient(LodestoneNamesPayload.TYPE, LodestoneNamesPayload.STREAM_CODEC,
                (payload, context) -> LodestoneNames.received(payload.names(), payload.all()));
        registrar.playToClient(OpenLodestoneName.TYPE, OpenLodestoneName.STREAM_CODEC, (payload, context) -> LodestoneNames.openEditor(payload.pos()));
        registrar.playToClient(TradingCounterViewPayload.TYPE, TradingCounterViewPayload.STREAM_CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof TradingCounterMenu menu && menu.containerId == payload.containerId()) {
                menu.view = payload.view();
            }
        });
        registrar.playToServer(Squint.TYPE, Squint.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Squinting.set(player, payload.on());
            }
        });
        registrar.playToServer(SortChest.TYPE, SortChest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player && NookworksConfig.CHEST_SORT_ALLOWED.getAsBoolean()) {
                ChestSorting.sortOpen(player, payload.containerId(), payload.order());
            }
        });
        registrar.playToServer(TakePhoto.TYPE, TakePhoto.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Photos.take(player, payload.pixels());
            }
        });
        registrar.playToServer(AskPhoto.TYPE, AskPhoto.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                Photos.ask(player, payload.id());
            }
        });
        registrar.playToClient(PhotoPixels.TYPE, PhotoPixels.STREAM_CODEC, (payload, context) -> Photos.received.accept(payload));
        registrar.playToClient(PhotosAllowed.TYPE, PhotosAllowed.STREAM_CODEC, (payload, context) -> Photos.allowedHere.accept(payload.on()));
        registrar.playToServer(RenameLodestone.TYPE, RenameLodestone.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                LodestoneNames.renameFromBox(player, payload.pos(), payload.name());
            }
        });
    }

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        // the world's parts first: the creative tabs and JEI, set going by what follows, show only those
        WorldParts parts = new WorldParts(WorldFeatures.effective());
        event.getRelevantPlayers()
                .filter((ServerPlayer player) -> player.connection.hasChannel(WorldParts.TYPE))
                .forEach(player -> PacketDistributor.sendToPlayer(player, parts));
        Compressible compressible = new Compressible(List.copyOf(Compressing.compressible(event.getPlayerList().getServer().overworld())));
        event.getRelevantPlayers()
                .filter((ServerPlayer player) -> player.connection.hasChannel(Compressible.TYPE))
                .forEach(player -> PacketDistributor.sendToPlayer(player, compressible));
        LootTables payload = new LootTables(LootSummaries.collect(event.getPlayerList().getServer()));
        event.getRelevantPlayers()
                .filter((ServerPlayer player) -> player.connection.hasChannel(LootTables.TYPE)) // a test's stand-in player has none
                .forEach(player -> PacketDistributor.sendToPlayer(player, payload));
        ServerPlayer joining = event.getPlayer();
        if (joining != null && joining.connection.hasChannel(StartupSettings.TYPE)) {
            PacketDistributor.sendToPlayer(joining, new StartupSettings(NookworksStartupConfig.values()));
        }
    }

    private NookworksNetwork() {}
}
