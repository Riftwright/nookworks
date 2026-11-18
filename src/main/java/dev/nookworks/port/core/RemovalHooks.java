package dev.nookworks.port.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The two block hooks around removal that 26.3's {@code BlockBehaviour} has and 1.21.1's lacks, put on our blocks by
 * tx/core.py (the topmost of our classes in a block's line implements this and gets an {@code onRemove} that calls
 * {@link BlockRemoval#onRemove}). The mod's overrides keep their bodies; they are {@code public} here because an
 * interface method is.
 */
public interface RemovalHooks {
    /**
     * 26.3: called on the server after the block at {@code pos} was replaced by another block (or a rail by
     * anything), its block entity already gone. Vanilla's own part of it runs in 1.21.1's {@code super.onRemove}.
     */
    default void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
    }

    /** 26.3: whether this block, replacing {@code oldState}'s block, keeps the block entity that was there. */
    default boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return false;
    }
}
