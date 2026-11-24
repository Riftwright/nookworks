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
 * <p>
 * With no more of the chimney under it, its open bottom is a mouth (2026-11-22): over a campfire or a fire up to three
 * blocks down it takes the fire's smoke - a fireplace, of a campfire or netherrack set alight. Its shape is as it looks
 * (the user, 2026-11-23: 「煙道は見た目通りの当たり判定にして、松明とかは置けない仕様のほうがいいんじゃない？」): its four
 * walls, open down the middle - a fire's own smoke rises on up into it rather than gathering under it, its outline
 * shows the opening, and a torch or a lantern finds nothing to stand on at its top. Too narrow for anyone to fall in.
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
        return WALLS;
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state) {
        return WALLS;
    }

    /**
     * A click into the opening still counts as on the face it went in by, as a cauldron's does: a chimney is set on top of
     * a flue clicked in the middle of its top, not beside it on the inner wall the click struck.
     */
    @Override
    protected VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }
}
