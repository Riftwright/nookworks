package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.feature.Fireproofing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Coated blocks (see {@link Fireproofing}) never burn away, never feed fire spread and cannot hold fire. */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void nookworks$coatedDoesNotBurnOut(Level level, BlockPos pos, int chance, RandomSource random, int age,
            Direction face, CallbackInfo ci) {
        if (Fireproofing.isCoated(level, pos)) {
            ci.cancel();
        }
    }

    @WrapOperation(method = "getIgniteOdds(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)I",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getFireSpreadSpeed(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)I"))
    private int nookworks$coatedDoesNotSpreadFire(BlockState state, BlockGetter level, BlockPos pos, Direction face,
            Operation<Integer> original) {
        return Fireproofing.isCoated(level, pos) ? 0 : original.call(state, level, pos, face);
    }

    @Inject(method = "canCatchFire", at = @At("HEAD"), cancellable = true)
    private void nookworks$coatedCannotCatchFire(BlockGetter level, BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (Fireproofing.isCoated(level, pos)) {
            cir.setReturnValue(false);
        }
    }
}
