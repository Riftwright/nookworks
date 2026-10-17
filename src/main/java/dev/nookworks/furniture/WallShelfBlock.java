package dev.nookworks.furniture;

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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wall shelf (the user, 2026-10-11, showing a shop's: a board on two black L brackets; "上の板はトラップドアサイズ"): a
 * board a trapdoor's size - the whole block across, three pixels thick, at its top - held off the wall by two iron
 * brackets under it. Its top is a full face, so a lantern, a candle or a pot stands on it. It hangs on the wall behind
 * it and falls when that goes. Shelves side by side facing the same way join into one long shelf: the brackets at the
 * joins go, leaving those at its ends, as a long board is hung.
 * <p>
 * It faces away from its wall ({@link #FACING}); {@link #LEFT} and {@link #RIGHT} say it is joined on that side, as
 * seen by someone standing in front of it.
 */
public class WallShelfBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");

    /** Facing north, its wall to the south: the board, and a bracket on each side - an arm down the wall, one under the board. */
    private static final VoxelShape BOARD = Block.box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape LEFT_BRACKET = Shapes.or(Block.box(12.0, 6.0, 15.0, 14.0, 13.0, 16.0),
            Block.box(12.0, 12.0, 6.0, 14.0, 13.0, 15.0));
    private static final VoxelShape RIGHT_BRACKET = Shapes.or(Block.box(2.0, 6.0, 15.0, 4.0, 13.0, 16.0),
            Block.box(2.0, 12.0, 6.0, 4.0, 13.0, 15.0));
    /** By facing, and by which sides are joined (left 1, right 2). */
    @SuppressWarnings("unchecked")
    private static final Map<Direction, VoxelShape>[] SHAPES = new Map[4];

    static {
        for (int joined = 0; joined < 4; joined++) {
            VoxelShape shape = BOARD;
            if ((joined & 1) == 0) {
                shape = Shapes.or(shape, LEFT_BRACKET);
            }
            if ((joined & 2) == 0) {
                shape = Shapes.or(shape, RIGHT_BRACKET);
            }
            SHAPES[joined] = Shapes.rotateHorizontal(shape);
        }
    }

    public WallShelfBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEFT, false)
                .setValue(RIGHT, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, WATERLOGGED);
    }

    /** The side a shelf facing {@code facing} has on its left, to someone in front of it. */
    public static Direction leftOf(Direction facing) {
        return facing.getClockWise();
    }

    /** Whether a shelf facing {@code facing} at {@code pos} is joined to whatever is on {@code side}. */
    private static boolean joins(LevelReader level, BlockPos pos, Direction facing, Direction side) {
        BlockState next = level.getBlockState(pos.relative(side));
        return next.getBlock() instanceof WallShelfBlock && next.getValue(FACING) == facing;
    }

    private static BlockState joined(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, joins(level, pos, facing, leftOf(facing)))
                .setValue(RIGHT, joins(level, pos, facing, leftOf(facing).getOpposite()));
    }

    /** On the wall it is set against; set on a floor or a ceiling, against the wall the player looks at, if there is one. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        LevelReader level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        boolean water = level.getFluidState(pos).getType() == Fluids.WATER;
        Direction clicked = context.getClickedFace();
        Direction[] tries = clicked.getAxis().isHorizontal() ? new Direction[] {clicked.getOpposite()} : context.getNearestLookingDirections();
        for (Direction towardsWall : tries) {
            if (towardsWall.getAxis().isVertical()) {
                continue;
            }
            BlockState state = this.defaultBlockState().setValue(FACING, towardsWall.getOpposite()).setValue(WATERLOGGED, water);
            if (state.canSurvive(level, pos)) {
                return joined(state, level, pos);
            }
        }
        return null;
    }

    /** Held by the wall behind it: enough of its face to screw a bracket to. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos wall = pos.relative(facing.getOpposite());
        return level.getBlockState(wall).isFaceSturdy(level, wall, facing, SupportType.CENTER);
    }

    /** Down when its wall goes; joined to a shelf set beside it, or let go of one taken away; water flows on. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        Direction facing = state.getValue(FACING);
        if (directionToNeighbour == facing.getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (directionToNeighbour.getAxis() != facing.getAxis() && directionToNeighbour.getAxis().isHorizontal()) {
            boolean join = neighbourState.getBlock() instanceof WallShelfBlock && neighbourState.getValue(FACING) == facing;
            state = state.setValue(directionToNeighbour == leftOf(facing) ? LEFT : RIGHT, join);
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int joined = (state.getValue(LEFT) ? 1 : 0) | (state.getValue(RIGHT) ? 2 : 0);
        return SHAPES[joined].get(state.getValue(FACING));
    }

    /** Turned with a structure: its sides go with it, so they stay its left and right. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, its left is its right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }
}
