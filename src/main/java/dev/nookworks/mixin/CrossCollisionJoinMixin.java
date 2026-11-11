package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.JoinOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A fence's or pane's side the Wrench joined or parted stays so when a neighbour changes: vanilla's answer, worked out
 * again at every neighbour update, has the sides set by hand laid over it ({@link JoinOverrides}). Iron bars, glass
 * panes and other mods' fences and panes are subclasses, and keep theirs too unless they shape themselves another way.
 * A fence gate keeps the height set for it the same way (lowered as between walls, or at a fence's).
 */
@Mixin({FenceBlock.class, IronBarsBlock.class, FenceGateBlock.class})
abstract class CrossCollisionJoinMixin {
    @Inject(method = "updateShape", at = @At("RETURN"), cancellable = true)
    private void nookworks$keepJoins(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random, CallbackInfoReturnable<BlockState> cir) {
        BlockState computed = cir.getReturnValue();
        BlockState kept = JoinOverrides.keepCross(computed, level, pos);
        if (kept != computed) {
            cir.setReturnValue(kept);
        }
    }
}
