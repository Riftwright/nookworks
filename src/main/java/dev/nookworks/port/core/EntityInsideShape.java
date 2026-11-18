package dev.nookworks.port.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 26.3's {@code getEntityInsideCollisionShape}: the part of a block's cell an entity must be in for the block's
 * {@code entityInside} to run (26.3's default, the whole cell). 1.21.1 has no such hook and calls {@code entityInside}
 * for anything in the cell; a block of ours with a smaller shape implements this, and
 * {@code mixin/EntityInsideShapeMixin} asks it before 1.21.1's call, as 26.3's entity does - so {@code entityInside}
 * keeps 26.3's body, and a test calling it directly gets what 26.3 gives.
 */
public interface EntityInsideShape {
    VoxelShape getEntityInsideCollisionShape(BlockState state, BlockGetter level, BlockPos pos, Entity entity);
}
