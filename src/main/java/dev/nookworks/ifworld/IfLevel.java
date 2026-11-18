package dev.nookworks.ifworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongIterator;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.MinecraftServerAccessor;
import dev.nookworks.port.storage.SavedData;
import dev.nookworks.port.storage.SavedDataType;
import dev.nookworks.travel.DaemonDoors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ForcedChunksSavedData;
import net.minecraft.world.level.border.BorderChangeListener;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.npc.CatSpawner;
import net.minecraft.world.entity.npc.WanderingTraderSpawner;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
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

    /**
     * The If's seed, for the server it was opened on (ServerLevelSeedMixin answers {@code getSeed} with it). PORT: 26.3
     * gives a level its own seed through NeoForge's {@code LevelStem.seedOverride}; 1.21.1 has none, and every level
     * reads the world's seed - the If would be the overworld's own terrain, the same again after each reset.
     */
    private static volatile @Nullable MinecraftServer seededFor;
    private static volatile long seed;

    /** The If's seed on {@code server}, once it has been opened there; empty before. */
    public static OptionalLong seedOf(@Nullable MinecraftServer server) {
        return server != null && server == seededFor ? OptionalLong.of(seed) : OptionalLong.empty();
    }

    /** The If, made now if it is rolled and not made yet; null while it is not rolled. */
    public static @Nullable ServerLevel open(MinecraftServer server) {
        ServerLevel existing = server.getLevel(IfWorld.KEY);
        IfWorld world = IfWorld.get(server);
        if (existing != null || !world.rolled()) {
            return existing;
        }
        long seed = IfWorld.seedOf(world.number());
        // PORT: 1.21.1's LevelStem has no seed of its own: told to the mixin before the level reads it (as it is made)
        IfLevel.seed = seed;
        seededFor = server;
        RegistryAccess registries = server.registryAccess();
        Holder<DimensionType> type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(BuiltinDimensionTypes.OVERWORLD);
        LevelStem stem = new LevelStem(type, IfChunkGenerator.create(registries.lookupOrThrow(Registries.BIOME),
                registries.lookupOrThrow(Registries.PLACED_FEATURE)));
        MinecraftServerAccessor access = (MinecraftServerAccessor) server;
        List<CustomSpawner> spawners = List.of(new PhantomSpawner(), new PatrolSpawner(), new CatSpawner(), new VillageSiege(),
                new TraderSpawner());
        // 1.21.1's constructor also takes a progress listener (the loading screen at a start: none here) and the random
        // sequences, which its other dimensions share with the overworld
        ServerLevel level = new ServerLevel(server, access.nookworks$getExecutor(), access.nookworks$getStorageSource(),
                new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), IfWorld.KEY, stem, QUIET, false,
                BiomeManager.obfuscateSeed(seed), spawners, false, server.overworld().getRandomSequences());
        access.nookworks$getLevels().put(IfWorld.KEY, level);
        server.markWorldsDirty(); // 1.21.1 ticks a kept array of its levels: made again with the If in it
        // PORT: 26.3 gives every dimension a border of its own, told to the players; 1.21.1's other dimensions follow the
        // overworld's (its constructor sets the absolute size), and so does the If
        level.getWorldBorder().applySettings(server.overworld().getWorldBorder().createSettings());
        server.overworld().getWorldBorder().addListener(new BorderChangeListener.DelegateBorderChangeListener(level.getWorldBorder()));
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
        // 1.21.1: monsters by the difficulty (MinecraftServer#updateMobSpawningFlags, which reaches the If from then on)
        level.setSpawnSettings(level.getServer().isSpawningMonsters(), level.getServer().isSpawningAnimals());
        // as MinecraftServer#prepareLevels brings back each level's forced chunks (26.3 reactivates its tickets)
        ForcedChunksSavedData forced = level.getDataStorage().get(ForcedChunksSavedData.factory(), "chunks");
        if (forced != null) {
            LongIterator chunks = forced.getChunks().iterator();
            while (chunks.hasNext()) {
                level.getChunkSource().updateChunkForced(new ChunkPos(chunks.nextLong()), true);
            }
            ForcedChunkManager.reinstatePersistentChunks(level, forced);
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

    /** 1.21.1's levels are made with a progress listener, for the loading screen at a start: the If has none to show. */
    private static final ChunkProgressListener QUIET = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(ChunkPos pos) {}

        @Override
        public void onStatusChange(ChunkPos pos, @Nullable ChunkStatus status) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    };

    /** The wandering trader's spawner, keeping its own count in the If (sharing the overworld's would double its pace). */
    private static final class TraderSpawner implements CustomSpawner {
        private @Nullable WanderingTraderSpawner inner;

        @Override
        public int tick(ServerLevel level, boolean spawnEnemies, boolean spawnFriendlies) {
            if (this.inner == null) {
                // PORT: 1.21.1's spawner keeps its count in level data (26.3: saved data of the level's own); the If's
                // count is kept as 26.3 keeps it, in the If's own data folder
                this.inner = new WanderingTraderSpawner(new TraderCount(level.getServer(),
                        SavedDataType.computeIfAbsent(level, TraderData.TYPE)));
            }
            return this.inner.tick(level, spawnEnemies, spawnFriendlies);
        }
    }

    /** The If's wandering trader count, as 26.3's {@code minecraft:wandering_trader} saved data keeps it. */
    private static final class TraderData extends SavedData {
        static final Codec<TraderData> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("spawn_delay", WanderingTraderSpawner.DEFAULT_SPAWN_DELAY).forGetter(d -> d.spawnDelay),
                Codec.INT.optionalFieldOf("spawn_chance", 25).forGetter(d -> d.spawnChance)).apply(i, TraderData::new));
        static final SavedDataType<TraderData> TYPE = new SavedDataType<>(
                net.minecraft.resources.ResourceLocation.withDefaultNamespace("wandering_trader"), TraderData::new, CODEC);
        private int spawnDelay;
        private int spawnChance;

        TraderData() {
            this(WanderingTraderSpawner.DEFAULT_SPAWN_DELAY, 25);
        }

        TraderData(int spawnDelay, int spawnChance) {
            this.spawnDelay = spawnDelay;
            this.spawnChance = spawnChance;
        }
    }

    /** The overworld's level data, but for the wandering trader's count, which is the If's own ({@link TraderData}). */
    private static final class TraderCount extends DerivedLevelData {
        private final TraderData data;
        private @Nullable UUID trader;

        TraderCount(MinecraftServer server, TraderData data) {
            super(server.getWorldData(), server.getWorldData().overworldData());
            this.data = data;
        }

        @Override
        public int getWanderingTraderSpawnDelay() {
            return this.data.spawnDelay;
        }

        @Override
        public void setWanderingTraderSpawnDelay(int delay) {
            this.data.spawnDelay = delay;
            this.data.setDirty();
        }

        @Override
        public int getWanderingTraderSpawnChance() {
            return this.data.spawnChance;
        }

        @Override
        public void setWanderingTraderSpawnChance(int chance) {
            this.data.spawnChance = chance;
            this.data.setDirty();
        }

        @Override
        public @Nullable UUID getWanderingTraderId() {
            return this.trader;
        }

        @Override
        public void setWanderingTraderId(UUID id) {
            this.trader = id;
        }
    }
}
