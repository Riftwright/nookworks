package dev.nookworks.mixin.compat;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.nookworks.darkmatter.DarkMatter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

/**
 * Sodium builds its chunk meshes from a copy of the light it takes straight out of the chunk sections (its
 * {@code LevelSlice}), not through the light engine that {@code LightEngineMixin} darkens - so with Sodium (and Iris,
 * which draws on Sodium's meshes) a Dark Matter disc was dark to monsters but lit to the eye. Its slice reads light
 * through these two methods, and in the discs they read 0 as the engine does.
 * <p>
 * Applied only when Sodium is there: the config's plugin ({@link CompatMixins}) adds it then and only then, and it is
 * {@code @Pseudo} besides.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice", remap = false)
abstract class SodiumLevelSliceMixin {
    @Shadow
    @Final
    private ClientLevel level;

    @ModifyReturnValue(method = "getBrightness(Lnet/minecraft/world/level/LightLayer;Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private int nookworks$darkMatter(int light, LightLayer layer, BlockPos pos) {
        return light > 0 && DarkMatter.dark(this.level, pos) ? 0 : light;
    }

    @ModifyReturnValue(method = "getRawBrightness(Lnet/minecraft/core/BlockPos;I)I", at = @At("RETURN"))
    private int nookworks$darkMatterRaw(int light, BlockPos pos, int darkening) {
        return light > 0 && DarkMatter.dark(this.level, pos) ? 0 : light;
    }
}
