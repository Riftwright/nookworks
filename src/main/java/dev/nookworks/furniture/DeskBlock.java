package dev.nookworks.furniture;

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
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wooden desk: a top on four legs, purely decorative.
 * <p>
 * It has no facing, because it looks the same from every side, and no block entity - it is a shape and
 * nothing else. Put chairs round it. Desks side by side join into one (the user, 2026-10-05: "くっついて一つの長机に"):
 * where a desk stands beside another - any wood, as fences of two woods join - the legs at that side go, and the rail
 * under the top runs on to meet the next desk's, so a row of them is one long desk on legs at its corners.
 */
public class DeskBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Joined to a desk on that side. */
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;

    private static final VoxelShape TOP = Block.box(0.0, 13.0, 0.0, 16.0, 16.0, 16.0);
    /** The four legs, so you can stand between them and an arrow can pass under the desk. */
    private static final VoxelShape LEG_NW = Block.box(1.0, 0.0, 1.0, 4.0, 13.0, 4.0);
    private static final VoxelShape LEG_NE = Block.box(12.0, 0.0, 1.0, 15.0, 13.0, 4.0);
    private static final VoxelShape LEG_SW = Block.box(1.0, 0.0, 12.0, 4.0, 13.0, 15.0);
    private static final VoxelShape LEG_SE = Block.box(12.0, 0.0, 12.0, 15.0, 13.0, 15.0);
    /** By which sides are joined (north 1, east 2, south 4, west 8): the top, and a leg at each corner neither side of which is. */
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int joined = 0; joined < 16; joined++) {
            boolean n = (joined & 1) != 0;
            boolean e = (joined & 2) != 0;
            boolean s = (joined & 4) != 0;
            boolean w = (joined & 8) != 0;
            VoxelShape shape = TOP;
            if (!n && !w) {
                shape = Shapes.or(shape, LEG_NW);
            }
            if (!n && !e) {
                shape = Shapes.or(shape, LEG_NE);
            }
            if (!s && !w) {
                shape = Shapes.or(shape, LEG_SW);
            }
            if (!s && !e) {
                shape = Shapes.or(shape, LEG_SE);
            }
            SHAPES[joined] = shape;
        }
    }

    public DeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false)
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED, NORTH, EAST, SOUTH, WEST);
    }

    /** The property for whether the desk is joined on {@code side} (a horizontal one). */
    public static BooleanProperty joined(Direction side) {
        return switch (side) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            default -> WEST;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        BlockState state = this.defaultBlockState().setValue(WATERLOGGED, water);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = state.setValue(joined(side), context.getLevel().getBlockState(context.getClickedPos().relative(side)).getBlock() instanceof DeskBlock);
        }
        return state;
    }

    /** Joined to a desk set down or taken away beside it; standing water keeps flowing out, as from any waterlogged block. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction directionToNeighbour,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (directionToNeighbour.getAxis().isHorizontal()) {
            state = state.setValue(joined(directionToNeighbour), neighbourState.getBlock() instanceof DeskBlock);
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    /** Turned with a structure, as fences are: the sides it is joined on turn with it. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return switch (rotation) {
            case CLOCKWISE_180 -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(EAST, state.getValue(WEST))
                    .setValue(SOUTH, state.getValue(NORTH)).setValue(WEST, state.getValue(EAST));
            case COUNTERCLOCKWISE_90 -> state.setValue(NORTH, state.getValue(EAST)).setValue(EAST, state.getValue(SOUTH))
                    .setValue(SOUTH, state.getValue(WEST)).setValue(WEST, state.getValue(NORTH));
            case CLOCKWISE_90 -> state.setValue(NORTH, state.getValue(WEST)).setValue(EAST, state.getValue(NORTH))
                    .setValue(SOUTH, state.getValue(EAST)).setValue(WEST, state.getValue(SOUTH));
            default -> state;
        };
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return switch (mirror) {
            case LEFT_RIGHT -> state.setValue(NORTH, state.getValue(SOUTH)).setValue(SOUTH, state.getValue(NORTH));
            case FRONT_BACK -> state.setValue(EAST, state.getValue(WEST)).setValue(WEST, state.getValue(EAST));
            default -> state;
        };
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int joined = (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0)
                | (state.getValue(WEST) ? 8 : 0);
        return SHAPES[joined];
    }
}
