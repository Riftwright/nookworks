package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import dev.nookworks.feature.JoinOverrides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A wall's side the Wrench joined or parted stays so when a neighbour changes ({@link JoinOverrides}). Every way vanilla
 * shapes a wall - set down, a side's neighbour changed, the block above changed - ends in this one method, given which
 * sides connect: those set by hand are put in before the heights are worked out, so a side joined is low or tall as the
 * block above makes it, and the post rises or not as vanilla's rule has it for the sides as they now are.
 */
@Mixin(WallBlock.class)
abstract class WallBlockJoinMixin {
    @WrapMethod(method = "updateShape(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;ZZZZ)Lnet/minecraft/world/level/block/state/BlockState;")
    private BlockState nookworks$keepJoins(LevelReader level, BlockState state, BlockPos topPos, BlockState topNeighbour,
            boolean north, boolean east, boolean south, boolean west, Operation<BlockState> original) {
        BlockPos pos = topPos.below();
        int bits = JoinOverrides.bits(level, pos);
        if (bits == 0) {
            return original.call(level, state, topPos, topNeighbour, north, east, south, west);
        }
        boolean n = JoinOverrides.side(bits, Direction.NORTH, north);
        boolean e = JoinOverrides.side(bits, Direction.EAST, east);
        boolean s = JoinOverrides.side(bits, Direction.SOUTH, south);
        boolean w = JoinOverrides.side(bits, Direction.WEST, west);
        if (n != north || e != east || s != south || w != west) {
            JoinOverrides.resend(level, pos);
        }
        return original.call(level, state, topPos, topNeighbour, n, e, s, w);
    }
}
