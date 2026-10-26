package dev.nookworks.furniture;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.phys.Vec3;
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
 * seen by someone standing in front of it. Two shelves side by side are kept apart by a Wrench sneak-used on the end
 * between them ({@link #LEFT_APART}, {@link #RIGHT_APART}: set on both, cleared when the other goes; the user,
 * 2026-10-23), and joined again the same way.
 */
public class WallShelfBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty LEFT_APART = BooleanProperty.create("left_apart");
    public static final BooleanProperty RIGHT_APART = BooleanProperty.create("right_apart");

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
                .setValue(RIGHT, false).setValue(LEFT_APART, false).setValue(RIGHT_APART, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEFT, RIGHT, LEFT_APART, RIGHT_APART, WATERLOGGED);
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
        for (Direction side : new Direction[] {leftOf(facing), leftOf(facing).getOpposite()}) {
            boolean shelf = joins(level, pos, facing, side);
            BooleanProperty apart = side == leftOf(facing) ? LEFT_APART : RIGHT_APART;
            boolean kept = shelf && state.getValue(apart);
            state = state.setValue(apart, kept).setValue(side == leftOf(facing) ? LEFT : RIGHT, shelf && !kept);
        }
        return state;
    }

    /** The end of the shelf nearer {@code hit}, if a shelf it joins (or is kept apart from) hangs there; else null. */
    public static @Nullable Direction apartSide(LevelReader level, BlockPos pos, BlockState state, Vec3 hit) {
        Direction facing = state.getValue(FACING);
        Direction left = leftOf(facing);
        double along = (hit.x - pos.getX() - 0.5) * left.getStepX() + (hit.z - pos.getZ() - 0.5) * left.getStepZ();
        Direction side = along >= 0 ? left : left.getOpposite();
        return joins(level, pos, facing, side) ? side : null;
    }

    /** The shelf on {@code side} kept apart from this one, or joined to it again - on both; whether they are apart now. */
    public static boolean toggleApart(Level level, BlockPos pos, BlockState state, Direction side) {
        Direction facing = state.getValue(FACING);
        Direction left = leftOf(facing);
        BooleanProperty mine = side == left ? LEFT_APART : RIGHT_APART;
        BooleanProperty theirs = side == left ? RIGHT_APART : LEFT_APART;  // the same facing: our left is its right
        boolean apart = !state.getValue(mine);
        BlockPos nextPos = pos.relative(side);
        level.setBlock(pos, joined(state.setValue(mine, apart), level, pos), Block.UPDATE_ALL);
        level.setBlock(nextPos, joined(level.getBlockState(nextPos).setValue(theirs, apart), level, nextPos), Block.UPDATE_ALL);
        return apart;
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
            boolean shelf = neighbourState.getBlock() instanceof WallShelfBlock && neighbourState.getValue(FACING) == facing;
            BooleanProperty apart = directionToNeighbour == leftOf(facing) ? LEFT_APART : RIGHT_APART;
            boolean kept = shelf && state.getValue(apart);
            state = state.setValue(apart, kept).setValue(directionToNeighbour == leftOf(facing) ? LEFT : RIGHT, shelf && !kept);
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
        return state.rotate(mirror.getRotation(state.getValue(FACING))).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT))
                .setValue(LEFT_APART, state.getValue(RIGHT_APART)).setValue(RIGHT_APART, state.getValue(LEFT_APART));
    }
}
