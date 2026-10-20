package dev.nookworks.recycling;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A corrugated sheet: a thin ridged plate set against the face it was put on - a wall, a floor, a ceiling - for sheds,
 * fences and roofs. {@code facing} is the way its face looks (out from what it lies on). On a wall its ridges run up and
 * down; lying flat they run the way the player was looking ({@code axis}), so a roof's ridges run down its fall.
 * It needs nothing behind it, as a trapdoor does not: a roof of it may span open beams. A wrench, sneaking, moves it to the
 * middle of its block, where a glass pane stands ({@code centered}), and back (the user, 2026-10-18): a sheet between
 * fence posts, or a roof at half height.
 */
public class CorrugatedSheetBlock extends Block implements SimpleWaterloggedBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** In the middle of its block, as a glass pane stands, rather than against the face it was set on. */
    public static final BooleanProperty CENTERED = BooleanProperty.create("centered");
    /** Two pixels thick: the sheet and its ridges, against the face behind it. */
    private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateAll(Block.box(0, 0, 14, 16, 16, 16));
    /** The same, in the middle of the block. */
    private static final Map<Direction, VoxelShape> CENTERED_SHAPES = Shapes.rotateAll(Block.box(0, 0, 7, 16, 16, 9));

    public CorrugatedSheetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(AXIS, Direction.Axis.Z).setValue(WATERLOGGED, false).setValue(CENTERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, AXIS, WATERLOGGED, CENTERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(CENTERED) ? CENTERED_SHAPES : SHAPES).get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // set into a replaceable block clicked (grass, snow), it lies flat on the ground under that - where there is
        // ground: into vines or lichen up a wall, it goes against the face as any click
        BlockPos below = context.getClickedPos().below();
        boolean onGround = context.getLevel().getBlockState(below).isFaceSturdy(context.getLevel(), below, Direction.UP);
        Direction facing = context.replacingClickedOnBlock() && onGround ? Direction.UP : context.getClickedFace();
        Direction.Axis axis = context.getHorizontalDirection().getAxis();
        return this.defaultBlockState().setValue(FACING, facing).setValue(AXIS, axis)
                .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    /**
     * Standing up (against a wall, or in the middle of its block) a sheet is a wall to mobs, as a closed trapdoor or a
     * glass pane is - they took it for open air and walked into it (a zombie at a sheet fence). Lying on a floor or under
     * a ceiling it is walked over and under, as a carpet is.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        if (type == PathComputationType.WATER) {
            return state.getValue(WATERLOGGED);
        }
        return state.getValue(FACING).getAxis() == Direction.Axis.Y && super.isPathfindable(state, type);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90
                ? turned.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X) : turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
