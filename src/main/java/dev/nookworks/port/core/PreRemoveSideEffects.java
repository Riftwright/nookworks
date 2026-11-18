package dev.nookworks.port.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code BlockEntity.preRemoveSideEffects}, which 1.21.1's {@code BlockEntity} lacks: called on the server
 * just before a block entity is taken away because its block was replaced. Our block entities that override it
 * implement this (tx/core.py adds the clause; a {@code super.preRemoveSideEffects} call that went to vanilla becomes
 * {@code PreRemoveSideEffects.super.preRemoveSideEffects}). {@link BlockRemoval} calls it from our blocks'
 * {@code onRemove}.
 */
public interface PreRemoveSideEffects {
    /** As 26.3's default: a block entity that is a container drops what it holds. */
    default void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (this instanceof Container container && this instanceof BlockEntity blockEntity) {
            Level level = blockEntity.getLevel();
            if (level != null) {
                net.minecraft.world.Containers.dropContents(level, pos, container);
            }
        }
    }
}
