package dev.nookworks.ifworld;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;

import dev.nookworks.mixin.ChunkGeneratorAccessor;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.RandomSupport;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The If's terrain: vanilla's overworld as vanilla's own code defines it, so another mod's changes to the overworld do
 * not reach it. Its shape (the noise settings), which features each biome is decorated with (trees, ores, flowers, ...)
 * and the mobs each spawns - at a chunk's making too - are vanilla's lists, built in code ({@link VanillaRegistries});
 * the biomes are vanilla's overworld biomes laid out by vanilla's own table. The features themselves and the structures
 * come from the server by vanilla's names, and of the structures only vanilla's.
 * <p>
 * A mod or datapack that rewrites vanilla's own files - its biomes', its features' or its structures' - can still show
 * through; one that adds its own does not, nor one that adds to vanilla's biomes (a biome modifier).
 * <p>
 * Each feature is the server's own of its name: built in code, a feature that names a block tag holds a stand-in for it
 * that cannot be read, and the coral of a warm ocean, drawn from #wall_corals, stopped the If's world being made (r15).
 * So a datapack that overrides a {@code minecraft:} feature changes it in the If too. Which features a biome has stays
 * vanilla's list; a feature the server lacks is the code's own.
 */
public class IfChunkGenerator extends NoiseBasedChunkGenerator {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Vanilla's worldgen as its code makes it, made once (it takes a moment) and kept. */
    private static final Lazy<HolderLookup.Provider> VANILLA = Lazy.of(VanillaRegistries::createWorldLookup);

    private final Map<ResourceKey<Biome>, Biome> pristine = new ConcurrentHashMap<>();
    private final Map<ResourceKey<Biome>, BiomeGenerationSettings> decoration = new ConcurrentHashMap<>();
    /** The server's features, by name. */
    private final HolderGetter<PlacedFeature> serverFeatures;

    /** The If's generator, its biomes the server's (by vanilla's names), and the features they are decorated with. */
    public static IfChunkGenerator create(HolderGetter<Biome> serverBiomes, HolderGetter<PlacedFeature> serverFeatures) {
        MultiNoiseBiomeSource biomes = MultiNoiseBiomeSource.createFromList(
                new MultiNoiseBiomeSourceParameterList(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD, serverBiomes).parameters());
        Holder<NoiseGeneratorSettings> settings = VANILLA.get().lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        return new IfChunkGenerator(biomes, settings, serverFeatures);
    }

    private IfChunkGenerator(MultiNoiseBiomeSource biomes, Holder<NoiseGeneratorSettings> settings, HolderGetter<PlacedFeature> serverFeatures) {
        super(biomes, settings);
        this.serverFeatures = serverFeatures;
        ChunkGeneratorAccessor self = (ChunkGeneratorAccessor) this;
        self.nookworks$setGenerationSettingsGetter(this::decoration);
        self.nookworks$setFeaturesPerStep(Lazy.of(() -> FeatureSorter.buildFeaturesPerStep(List.copyOf(biomes.possibleBiomes()),
                biome -> this.decoration(biome).features(), true)));
    }

    /** Vanilla's features for {@code biome}, step by step, each the server's of its name (see the class's note). */
    private BiomeGenerationSettings decoration(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElseThrow(() -> new IllegalStateException("an unnamed biome in the If: " + biome));
        return this.decoration.computeIfAbsent(key, k -> {
            BiomeGenerationSettings own = this.vanilla(biome).getGenerationSettings();
            List<HolderSet<PlacedFeature>> steps = new ArrayList<>();
            for (HolderSet<PlacedFeature> step : own.features()) {
                List<Holder<PlacedFeature>> features = new ArrayList<>();
                for (Holder<PlacedFeature> feature : step) {
                    features.add(feature.unwrapKey().<Holder<PlacedFeature>>flatMap(this.serverFeatures::get).orElse(feature));
                }
                steps.add(HolderSet.direct(features));
            }
            List<Holder<WorldCarver>> carvers = new ArrayList<>();
            own.getCarvers().forEach(carvers::add);
            return new BiomeGenerationSettings(HolderSet.direct(carvers), steps);
        });
    }

    /** The features {@code biome} is decorated with in the If, every step's. For tests. */
    public List<Holder<PlacedFeature>> featuresOf(Holder<Biome> biome) {
        List<Holder<PlacedFeature>> all = new ArrayList<>();
        this.decoration(biome).features().forEach(step -> step.forEach(all::add));
        return all;
    }

    /** Vanilla's own definition of {@code biome} (a server biome by vanilla's name; every biome the If has is one). */
    private Biome vanilla(Holder<Biome> biome) {
        ResourceKey<Biome> key = biome.unwrapKey().orElseThrow(() -> new IllegalStateException("an unnamed biome in the If: " + biome));
        return this.pristine.computeIfAbsent(key, k -> VANILLA.get().lookupOrThrow(Registries.BIOME).getOrThrow(k).value());
    }

    /** As a noise generator's ({@code minecraft:noise}): the If is never written into the world's dimension list. */
    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return NoiseBasedChunkGenerator.CODEC;
    }

    /**
     * What spawns: as vanilla's own biome would have it (a structure's own spawns, a witch hut's say, still come first).
     * In 26.3 a biome's spawns are its natural_mob_spawns attribute, laid over the dimension's; the level would read the
     * server's biome, so vanilla's is read here.
     */
    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Level level, StructureManager structures, MobCategory category,
            BlockPos pos) {
        WeightedList<MobSpawnSettings.SpawnerData> structure = structureSpawns(structures, category, pos);
        if (structure != null) {
            return structure;
        }
        var spawns = EnvironmentAttributes.NATURAL_MOB_SPAWNS;
        MobSpawnSettings dimension = level.dimensionType().attributes().applyModifier(spawns, spawns.defaultValue());
        return this.vanilla(level.getBiome(pos)).getAttributes().applyModifier(spawns, dimension).getMobsToSpawn(category);
    }

    /**
     * The animals a chunk is made with, as vanilla's own biome would have them, as {@link #getMobsAt} has the rest:
     * vanilla's steps ({@code NaturalSpawner.spawnMobsForChunkGeneration}, the same draws in the same order), but its list
     * and its chance read from vanilla's biome. Vanilla's read the server's, which a mod's biome modifier adds its own
     * animals to (r15 fix check 3).
     */
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        if (this.generatorSettings().value().disableMobGeneration() || !region.getLevel().getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        ChunkPos chunk = region.getCenter();
        BlockPos source = chunk.getWorldPosition().atY(region.getMaxY());
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
        random.setDecorationSeed(region.getSeed(), chunk.getMinBlockX(), chunk.getMinBlockZ());
        WeightedList<MobSpawnSettings.SpawnerData> mobs = this.vanillaAttribute(region, EnvironmentAttributes.NATURAL_MOB_SPAWNS, source)
                .getMobsToSpawn(MobCategory.CREATURE);
        if (mobs.isEmpty()) {
            return;
        }
        float chance = this.vanillaAttribute(region, EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, source);
        int x0 = chunk.getMinBlockX();
        int z0 = chunk.getMinBlockZ();
        while (random.nextFloat() < chance) {
            Optional<MobSpawnSettings.SpawnerData> drawn = mobs.getRandom(random);
            if (drawn.isEmpty()) {
                continue;
            }
            EntityType<?> type = drawn.get().type();
            int count = drawn.get().count().sample(random);
            SpawnGroupData group = null;
            int x = x0 + random.nextInt(16);
            int z = z0 + random.nextInt(16);
            int startX = x;
            int startZ = z;
            for (int i = 0; i < count; i++) {
                boolean placed = false;
                for (int tries = 0; !placed && tries < 4; tries++) {
                    BlockPos ground = topNonColliding(region, type, x, z);
                    if (type.canSummon() && SpawnPlacements.isSpawnPositionOk(type, region, ground)) {
                        float width = type.getWidth();
                        double fx = Mth.clamp(x, (double) x0 + width, x0 + 16.0 - width);
                        double fz = Mth.clamp(z, (double) z0 + width, z0 + 16.0 - width);
                        if (!region.noCollision(type.getSpawnAABB(fx, ground.getY(), fz)) || !SpawnPlacements.checkSpawnRules(type, region,
                                EntitySpawnReason.CHUNK_GENERATION, BlockPos.containing(fx, ground.getY(), fz), region.getRandom())) {
                            continue; // (as vanilla's: tried again at the same spot)
                        }
                        Entity entity;
                        try {
                            entity = type.create(region.getLevel(), EntitySpawnReason.NATURAL);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to create mob", e);
                            continue;
                        }
                        if (entity == null) {
                            continue;
                        }
                        entity.snapTo(fx, ground.getY(), fz, random.nextFloat() * 360.0F, 0.0F);
                        if (entity instanceof Mob mob && EventHooks.checkSpawnPosition(mob, region, EntitySpawnReason.CHUNK_GENERATION)) {
                            group = mob.finalizeSpawn(region, region.getCurrentDifficultyAt(mob.blockPosition()), EntitySpawnReason.CHUNK_GENERATION, group);
                            region.addFreshEntityWithPassengers(mob);
                            placed = true;
                        }
                    }
                    x += random.nextInt(5) - random.nextInt(5);
                    for (z += random.nextInt(5) - random.nextInt(5); x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16;
                            z = startZ + random.nextInt(5) - random.nextInt(5)) {
                        x = startX + random.nextInt(5) - random.nextInt(5);
                    }
                }
            }
        }
    }

    /** Where a mob of {@code type} would stand at the top of the column {@code x}, {@code z} (as NaturalSpawner finds it). */
    private static BlockPos topNonColliding(LevelReader level, EntityType<?> type, int x, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(SpawnPlacements.getHeightmapType(type), x, z), z);
        if (level.dimensionType().hasCeiling()) {
            do {
                pos.move(Direction.DOWN);
            } while (!level.getBlockState(pos).isAir());
            do {
                pos.move(Direction.DOWN);
            } while (level.getBlockState(pos).isAir() && pos.getY() > level.getMinY());
        }
        return SpawnPlacements.getPlacementType(type).adjustSpawnPosition(level, pos.immutable());
    }

    /**
     * {@code attribute} at {@code pos} as vanilla's own biome there sets it over the dimension's, where the level's
     * environment would read the server's biome - read as a chunk being made reads it: the biome at the block's centre,
     * at the noise's resolution unless the attribute asks for the full one.
     */
    private <V> V vanillaAttribute(LevelReader level, EnvironmentAttribute<V> attribute, BlockPos pos) {
        V dimension = level.dimensionType().attributes().applyModifier(attribute, attribute.defaultValue());
        Holder<Biome> biome = attribute.isFullResolutionBiomes() ? level.getBiomeManager().getBiome(pos)
                : level.getBiomeManager().getNoiseBiomeAtPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return this.vanilla(biome).getAttributes().applyModifier(attribute, dimension);
    }

    /** A structure's own spawns at {@code pos}, where one there has them for {@code category} (ChunkGenerator#getMobsAt). */
    private static @Nullable WeightedList<MobSpawnSettings.SpawnerData> structureSpawns(StructureManager structures,
            MobCategory category, BlockPos pos) {
        for (Map.Entry<Structure, LongSet> entry : structures.getAllStructuresAt(pos).entrySet()) {
            StructureSpawnOverride override = entry.getKey().spawnOverrides().get(category);
            if (override == null) {
                continue;
            }
            boolean[] inside = {false};
            structures.fillStartsForStructure(entry.getKey(), entry.getValue(), start -> inside[0] |=
                    override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE
                            ? structures.structureHasPieceAt(pos, start) : start.getBoundingBox().isInside(pos));
            if (inside[0]) {
                return override.spawns();
            }
        }
        return null;
    }

    /** Vanilla's structure sets only: villages, strongholds, ancient cities and the rest, and no other mod's. */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structureSets, RandomState randomState, long seed) {
        return super.createState(vanillaOnly(structureSets), randomState, seed);
    }

    private static HolderLookup<StructureSet> vanillaOnly(HolderLookup<StructureSet> all) {
        return new HolderLookup<>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return all.listElements().filter(set -> set.key().identifier().getNamespace().equals("minecraft"));
            }

            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return all.listTags();
            }

            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> id) {
                return id.identifier().getNamespace().equals("minecraft") ? all.get(id) : Optional.empty();
            }

            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> id) {
                return all.get(id);
            }
        };
    }
}
