package dev.nookworks.recycling;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Baler set down: a scrap baler press for decor (the user, 2026-11-10: 「べーラーも中間素材だけど、装飾用に置けるブロック
 * として、機械っぽい見た目でおしゃれに作りこんで」). It does nothing - the part a Recycler is made from, as before, and now a
 * press that stands among the machines: two steel cheeks on a plinth, a crosshead with the cylinder on it, the chrome
 * rod down to the ram on a strapped bale, brass brackets and a gauge (tools/textures/gen_baler.py). It faces whoever
 * sets it down.
 */
public class BalerBlock extends HorizontalDirectionalBlock {
    /** The plinth, the cheeks, the crosshead and cylinder, the ram on the bale, the rod and the gauge, facing north. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(0, 2, 2, 2, 11, 14), Block.box(14, 2, 2, 16, 11, 14),
            Block.box(0, 11, 2, 16, 13, 14), Block.box(3, 13, 3, 13, 16, 13),
            Block.box(3, 2, 3, 13, 7, 13), Block.box(2, 7, 2, 14, 8, 14), Block.box(6, 8, 6, 10, 11, 10),
            Block.box(0, 12, 1, 3, 15, 2)));

    public BalerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
