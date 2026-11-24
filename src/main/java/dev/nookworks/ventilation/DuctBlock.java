package dev.nookworks.ventilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.nookworks.barter.BarterTerminalBlock;
import dev.nookworks.block.PipeSides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Duct: square ducting of galvanized sheet steel, spangled grey where the zinc crystallised on it. It joins up with the
 * ducts beside it, the back of a Ventilation Fan, the collar on top of a Range Hood, the side of a Chimney and the top
 * or bottom of a Flue (closed on its sides), and carries their smoke (see {@link Ventilation}); where a run of it ends in the open, the smoke comes out.
 * <p>
 * A side is parted with a sneak-use of the Wrench, as a pipe's is ({@link PipeSides}: its {@code _off} flags), and stays
 * so till joined back the same way - beside a chimney or another run it should not meet, a duct joined whatever was
 * next to it (the user, 2026-11-23: 「隣にあると、どこでも繋がっちまう」). A duct or chimney on the other side is
 * parted with it.
 */
public class DuctBlock extends Block {
    public static final Map<Direction, BooleanProperty> SIDES = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5), Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11), Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11), Direction.UP, Block.box(5, 11, 5, 11, 16, 11));

    /** Each state's shape, made once: it was put together on every ask, and a run of duct is asked about often. */
    private final Function<BlockState, VoxelShape> shapes;

    public DuctBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = PipeSides.allOn(this.stateDefinition.any());
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        this.registerDefaultState(state);
        this.shapes = this.getShapeForEachState(each -> {
            VoxelShape shape = CORE;
            for (Direction side : connections(each)) {
                shape = Shapes.or(shape, ARMS.get(side));
            }
            return shape;
        });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        SIDES.values().forEach(builder::add);
        PipeSides.addTo(builder);
    }

    /** The sides a duct in {@code state} is joined on. */
    public static List<Direction> connections(BlockState state) {
        List<Direction> joined = new ArrayList<>();
        if (state.getBlock() instanceof DuctBlock) {
            for (Direction side : Direction.values()) {
                if (state.getValue(SIDES.get(side))) {
                    joined.add(side);
                }
            }
        }
        return joined;
    }

    /** Whether a duct joins up with {@code neighbour}, lying on its {@code side}. */
    public static boolean joins(BlockState neighbour, Direction side) {
        if (neighbour.getBlock() instanceof FlueBlock) {
            return side.getAxis() == Direction.Axis.Y; // open only at its top and bottom (the user, 2026-11-01)
        }
        if (neighbour.getBlock() instanceof DuctBlock || neighbour.getBlock() instanceof ChimneyBlock) {
            return true;
        }
        if (neighbour.getBlock() instanceof RangeHoodBlock) {
            return side == Direction.DOWN; // on the hood's collar, on top of it
        }
        if (neighbour.getBlock() instanceof VentFanBlock) {
            return neighbour.getValue(VentFanBlock.FACING) == side; // on its back, the fan's front facing away from the duct
        }
        if (neighbour.getBlock() instanceof BarterTerminalBlock) {
            return true; // on any face: a Barter Terminal sends the Nether's air along the duct to the fan in its room
        }
        return false;
    }

    /** Whether the duct in {@code state} joins {@code neighbour} on {@code side}: it would, and neither has parted that side. */
    private static boolean joinsNow(BlockState state, BlockState neighbour, Direction side) {
        return joins(neighbour, side) && !PipeSides.isOff(state, side) && !PipeSides.isOff(neighbour, side.getOpposite());
    }

    private BlockState joined(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.values()) {
            state = state.setValue(SIDES.get(side), joinsNow(state, level.getBlockState(pos.relative(side)), side));
        }
        return state;
    }

    /**
     * Parts the duct or chimney at {@code pos} from what is on its {@code side}, or joins it back; a duct or chimney on
     * that side is set the same way, so the two never disagree. Returns whether the side is now parted.
     */
    public static boolean toggleSide(Level level, BlockPos pos, BlockState state, Direction side) {
        boolean off = !PipeSides.isOff(state, side);
        level.setBlock(pos, state.setValue(PipeSides.OFF.get(side), off), Block.UPDATE_ALL);
        BlockPos next = pos.relative(side);
        BlockState neighbour = level.getBlockState(next);
        BooleanProperty back = PipeSides.OFF.get(side.getOpposite());
        if (partable(neighbour) && neighbour.hasProperty(back)) { // a Flue is never parted, nor asked about by the Wrench
            level.setBlock(next, neighbour.setValue(back, off), Block.UPDATE_ALL);
        }
        for (BlockPos at : new BlockPos[] {pos, next}) { // setting a flag runs no shape update on the block itself
            BlockState now = level.getBlockState(at);
            BlockState updated = Block.updateFromNeighbourShapes(now, level, at);
            if (updated != now) {
                level.setBlock(at, updated, Block.UPDATE_ALL);
            }
        }
        return off;
    }

    /** Whether the Wrench parts and joins the sides of {@code state}: a duct, or a chimney's four sides. */
    public static boolean partable(BlockState state) {
        return state.getBlock() instanceof DuctBlock || state.getBlock() instanceof ChimneyBlock && !(state.getBlock() instanceof FlueBlock);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipeSides.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipeSides.turned(state, mirror::mirror);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.joined(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(SIDES.get(side), joinsNow(state, neighbourState, side));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    /** A duct: no way through, as a pipe is not. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
