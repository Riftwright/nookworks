package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import dev.nookworks.darkmatter.DarkMatter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.lighting.LightEngine;

/**
 * Dark Matter's discs read as light 0, sky and block light alike, to every reader on either side - drawing, spawning,
 * burning, growing - while what the engine stores and sends stays as it was (see {@link DarkMatter}). This is the one
 * place both light layers are read from a position; the engine's own spreading reads its storage and never comes here.
 */
@Mixin(LightEngine.class)
abstract class LightEngineMixin {
    @Shadow
    @Final
    protected LightChunkGetter chunkSource;

    @ModifyReturnValue(method = "getLightValue(Lnet/minecraft/core/BlockPos;)I", at = @At("RETURN"))
    private int nookworks$darkMatter(int light, BlockPos pos) {
        return light > 0 && DarkMatter.dark(this.chunkSource.getLevel(), pos) ? 0 : light;
    }
}
