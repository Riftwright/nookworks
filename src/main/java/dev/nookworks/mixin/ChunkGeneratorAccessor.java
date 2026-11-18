package dev.nookworks.mixin;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * Lets the If's generator ({@code dev.nookworks.ifworld.IfChunkGenerator}) decorate its biomes by vanilla's own lists
 * of features rather than the server's biomes' (which other mods add to): a noise generator takes them from each biome,
 * with no way to pass others in. The features on those lists are the server's of vanilla's names.
 */
@Mixin(ChunkGenerator.class)
public interface ChunkGeneratorAccessor {
    @Mutable
    @Accessor("generationSettingsGetter")
    void nookworks$setGenerationSettingsGetter(Function<Holder<Biome>, BiomeGenerationSettings> getter);

    @Mutable
    @Accessor("featuresPerStep")
    void nookworks$setFeaturesPerStep(Supplier<List<FeatureSorter.StepFeatureData>> features);
}
