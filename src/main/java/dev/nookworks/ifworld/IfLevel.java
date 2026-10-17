package dev.nookworks.ifworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.MinecraftServerAccessor;
import dev.nookworks.travel.DaemonDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.PatrolSpawner;
import net.minecraft.world.level.levelgen.PhantomSpawner;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Makes the If's level. It is in no world's list of dimensions (a world without Nookworks opens as if it never was), so
 * it is made when its mirror is rolled, and again at each start from its number; a reset waiting from an operator is
 * carried out at a start, before it would be made.
 * <p>
 * It is the overworld's like: the overworld's dimension type (its sky, its beds, its clock - so its days and nights are
 * the overworld's), weather and time read from the overworld, its sleepers counted with the overworld's players
 * ({@link #sleepingWith}), and the overworld's own spawners (phantoms, patrols, cats, sieges, the wandering trader),
 * which vanilla gives no other dimension.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class IfLevel {
    private IfLevel() {}

    /** The If, made now if it is rolled and not made yet; null while it is not rolled. */
    public static @Nullable ServerLevel open(MinecraftServer server) {
        ServerLevel existing = server.getLevel(IfWorld.KEY);
        IfWorld world = IfWorld.get(server);
        if (existing != null || !world.rolled()) {
            return existing;
        }
        long seed = IfWorld.seedOf(world.number());
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        LevelStem stem = new LevelStem(type, IfChunkGenerator.create(registries.lookupOrThrow(Registries.BIOME),
                registries.lookupOrThrow(Registries.PLACED_FEATURE)), OptionalLong.of(seed));
        MinecraftServerAccessor access = (MinecraftServerAccessor) server;
        List<CustomSpawner> spawners = List.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(),
                new TraderSpawner());
        ServerLevel level = new ServerLevel(server, access.nookworks$getExecutor(), access.nookworks$getStorageSource(),
                new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), IfWorld.KEY, stem, false,
                BiomeManager.obfuscateSeed(seed), spawners, false);
        access.nookworks$getLevels().put(IfWorld.KEY, level);
        level.getWorldBorder().setAbsoluteMaxSize(server.getAbsoluteMaxWorldSize());
        server.getPlayerList().addWorldborderListener(level);
        NeoForge.EVENT_BUS.post(new LevelEvent.Load(level));
        prepare(level);
        Nookworks.LOGGER.info("The If No. {} is open", world.number());
        return level;
    }

    /**
     * What the server does for each of its levels as it starts, and the If - made after that - missed: monsters spawned
     * only as the rules say (with spawn_monsters off they still came in the If, phantoms and patrols too), and the chunks
     * kept loaded there (/forceload, another mod's chunk loader), set aside at the last stop, kept loaded again (they
     * stopped at every restart). Public for tests.
     */
    public static void prepare(ServerLevel level) {
        level.setSpawnSettings(level.isSpawningMonsters());
        TicketStorage tickets = level.getDataStorage().get(TicketStorage.TYPE);
        if (tickets != null) {
            tickets.activateAllDeactivatedTickets();
            ForcedChunkManager.activateAllDeactivatedTickets(level, tickets);
        }
    }

    /**
     * Who sleeps through a night with {@code level}'s {@code players}: in the overworld, the If's players too, under the
     * one clock (ServerLevelSleepMixin) - the If counts nobody itself, or one sleeper there skipped the night and the rain
     * for the whole server, the overworld's players never asked. Anywhere else, {@code players} as they are.
     */
    public static List<ServerPlayer> sleepingWith(ServerLevel level, List<ServerPlayer> players) {
        ServerLevel ifLevel = level.dimension().equals(Level.OVERWORLD) ? level.getServer().getLevel(IfWorld.KEY) : null;
        if (ifLevel == null || ifLevel.players().isEmpty()) {
            return players;
        }
        List<ServerPlayer> together = new ArrayList<>(players);
        together.addAll(ifLevel.players());
        return together;
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        IfWorld world = IfWorld.get(server);
        if (world.resetPending()) {
            reset(server, world);
        }
        open(server);
    }

    /** An operator's reset, at a start: the If's chunks and records gone, its mirror unrolled. */
    private static void reset(MinecraftServer server, IfWorld world) {
        Path folder = ((MinecraftServerAccessor) server).nookworks$getStorageSource().getDimensionPath(IfWorld.KEY);
        if (Files.isDirectory(folder)) {
            // moved aside in one step first: if that fails, the old If is whole; once it is aside, a file that will not go
            // (held open by something) is left in the folder set aside, not in the new If
            Path aside = folder.resolveSibling(folder.getFileName() + "_reset_" + System.currentTimeMillis());
            try {
                Files.move(folder, aside);
            } catch (IOException e) {
                Nookworks.LOGGER.error("Could not take the old If away ({}); it stays as it was", folder, e);
                return;
            }
            try (Stream<Path> files = Files.walk(aside)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(file);
                }
            } catch (IOException e) {
                Nookworks.LOGGER.warn("The old If is set aside but not all deleted ({}); it can be deleted by hand", aside, e);
            }
        }
        DaemonDoors.get(server).forgetDimension(IfWorld.KEY); // its door went with it: the next trip sets one down
        Nookworks.LOGGER.info("The If No. {} is gone, as an operator asked; its mirror can be rolled again", world.number());
        world.clearForReset();
    }

    /** The wandering trader's spawner, keeping its own count in the If (sharing the overworld's would double its pace). */
    private static final class TraderSpawner implements CustomSpawner {
        private @Nullable WanderingTraderSpawner inner;

        @Override
        public void tick(ServerLevel level, boolean spawnEnemies) {
            if (this.inner == null) {
                this.inner = new WanderingTraderSpawner(level.getDataStorage());
            }
            this.inner.tick(level, spawnEnemies);
        }
    }
}
