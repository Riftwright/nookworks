package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.TrimmedVines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A vine cut with the Fine Shears ({@link dev.nookworks.feature.TrimmedVines}) does not spread; one that falls for losing what it
 * hung on takes its mark with it, so a vine grown there again spreads as any. And a vine growing into a place where there
 * is no vine leaves no mark there: the cut one there may have burned, been blown up or washed away.
 */
@Mixin(VineBlock.class)
abstract class VineBlockMixin {
    @Inject(method = "updateShape", at = @At("RETURN"))
    private void nookworks$fallen(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
        if (!(cir.getReturnValue().getBlock() instanceof VineBlock) && level instanceof Level full) {
            TrimmedVines.forget(full, pos);
        }
    }

    @WrapOperation(method = "randomTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean nookworks$grownAnew(ServerLevel level, BlockPos at, BlockState grown, int flags, Operation<Boolean> original) {
        if (!(level.getBlockState(at).getBlock() instanceof VineBlock)) {
            TrimmedVines.forget(level, at);
        }
        return original.call(level, at, grown, flags);
    }

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void nookworks$trimmed(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, CallbackInfo ci) {
        if (TrimmedVines.isTrimmed(level, pos)) {
            ci.cancel();
        }
    }
}
