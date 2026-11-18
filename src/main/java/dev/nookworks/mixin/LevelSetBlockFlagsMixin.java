package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.nookworks.port.core.BlockFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The block update flags later versions have and 1.21.1 passes on unread - 256, a block goes without its block
 * entity's side effects (a carried crate keeps its contents instead of spilling them), and 512, the new block's
 * {@code onPlace} is not called: told to {@link BlockFlags} for the length of the call, where the port's removal
 * code ({@code BlockRemoval}) and {@link LevelChunkSkipOnPlaceMixin} read them.
 * <p>
 * The whole call is wrapped, so the flags are taken off again however it ends: an inject at the head and one at each
 * return left them on when another mod's hook cancelled the call after ours had run, or when something in it threw.
 */
@Mixin(Level.class)
abstract class LevelSetBlockFlagsMixin {
    @WrapMethod(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z")
    private boolean nookworks$withFlags(BlockPos pos, BlockState state, int flags, int recursionLeft, Operation<Boolean> original) {
        BlockFlags.push(flags);
        try {
            return original.call(pos, state, flags, recursionLeft);
        } finally {
            BlockFlags.pop();
        }
    }
}
