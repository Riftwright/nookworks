package dev.nookworks.ventilation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Flue Brick (煙道レンガ; the user, 2026-10-31): a whole block of a chimney's masonry with a flue down its middle, open
 * only at the top and the bottom - for where a chimney passes through a roof or a floor, which the slimmer stack left
 * gaps round. It is part of the chimney: smoke goes up through it as up the stack ({@link Ventilation}), a stack
 * stood on it carries on, and with nothing over it its open top is where the smoke comes out. One for each chimney's
 * masonry. Its faces are whole on every side, so it walls and roofs like the block it is made of; only its top and
 * bottom show the opening.
 */
public class FlueBlock extends ChimneyBlock {
    /** The four walls round the flue: what a neighbour's face is hidden against (the opening hides nothing). */
    private static final VoxelShape WALLS = Shapes.or(Block.box(0, 0, 0, 16, 16, 5), Block.box(0, 0, 11, 16, 16, 16),
            Block.box(0, 0, 5, 5, 16, 11), Block.box(11, 0, 5, 16, 16, 11));

    public FlueBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return WALLS;
    }
}
