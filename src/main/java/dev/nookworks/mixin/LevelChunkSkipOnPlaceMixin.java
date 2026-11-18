package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import dev.nookworks.port.core.BlockFlags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The update flag 512 of later versions (a block set down without its {@code onPlace}): 1.21.1 always calls
 * {@code onPlace}, so a block set with that flag ({@link BlockFlags}, told by {@link LevelSetBlockFlagsMixin}) has the
 * call left out here.
 */
@Mixin(LevelChunk.class)
abstract class LevelChunkSkipOnPlaceMixin {
    @WrapWithCondition(method = "setBlockState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;onPlace(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)V"))
    private boolean nookworks$skipOnPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        return !BlockFlags.has(BlockFlags.UPDATE_SKIP_ON_PLACE);
    }
}
