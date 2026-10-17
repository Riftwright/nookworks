package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.Fireproofing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.material.LavaFluid;

/** Lava does not set coated blocks (see {@link Fireproofing}) alight. */
@Mixin(LavaFluid.class)
abstract class LavaFluidMixin {
    @Inject(method = "isFlammable(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
            at = @At("HEAD"), cancellable = true)
    private void nookworks$coatedIsNotFlammable(LevelReader level, BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Fireproofing.isCoated(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
