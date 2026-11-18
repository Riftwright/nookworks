package dev.nookworks.port.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's removal order, run from our blocks' 1.21.1 {@code onRemove} (tx/core.py writes that override on the topmost
 * of our classes in each block's line; it is called on the server only, for every change of state at the position).
 *
 * <p>26.3's {@code LevelChunk.setBlockState}, when the block at a position is replaced by another block:
 * <ol>
 * <li>unless the new block keeps the block entity ({@code shouldChangedStateKeepBlockEntity}), and unless the update
 * skips block entity side effects (flag 256), calls the old block entity's {@code preRemoveSideEffects} - which by
 * default drops a container's contents;</li>
 * <li>removes the block entity (1.21.1's {@code BlockBehaviour.onRemove} does this: here, {@code superOnRemove});</li>
 * <li>when the block changed (or the new one is a rail) and the update tells neighbours (flag 1) or a piston moved it,
 * calls the old block's {@code affectNeighborsAfterRemoval}.</li>
 * </ol>
 * The flags are known only when the {@code Level.setBlock} mixin reports them ({@link BlockFlags}); without it every
 * removal is taken as {@code UPDATE_ALL}, as most are.
 */
public final class BlockRemoval {
    private BlockRemoval() {}

    public static void onRemove(RemovalHooks block, BlockState state, Level level, BlockPos pos, BlockState newState,
            boolean movedByPiston, Runnable superOnRemove) {
        boolean blockChanged = !state.is(newState.getBlock());
        boolean keep = blockChanged && newState.getBlock() instanceof RemovalHooks next
                && next.shouldChangedStateKeepBlockEntity(state);
        if (blockChanged && state.hasBlockEntity() && !keep && !level.isClientSide()
                && !BlockFlags.has(BlockFlags.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                preRemoveSideEffects(blockEntity, level, pos, state);
            }
        }
        if (!keep) {
            // PORT: when the block entity is kept, the parent's onRemove is skipped whole; for a BaseEntityBlock (the
            // only kind that keeps one, the sorters) all it does is remove the block entity
            superOnRemove.run();
        }
        int flags = BlockFlags.current();
        boolean tellsNeighbours = flags == BlockFlags.UNKNOWN || (flags & Block.UPDATE_NEIGHBORS) != 0 || movedByPiston;
        if ((blockChanged || newState.getBlock() instanceof BaseRailBlock) && level instanceof ServerLevel serverLevel
                && tellsNeighbours) {
            block.affectNeighborsAfterRemoval(state, serverLevel, pos, movedByPiston);
        }
    }

    /** A block entity's 26.3 removal side effects: its own override, or the default for a container. */
    public static void preRemoveSideEffects(BlockEntity blockEntity, Level level, BlockPos pos, BlockState state) {
        if (blockEntity instanceof PreRemoveSideEffects hooked) {
            hooked.preRemoveSideEffects(pos, state);
        } else if (blockEntity instanceof Container container) {
            net.minecraft.world.Containers.dropContents(level, pos, container);
        }
    }
}
