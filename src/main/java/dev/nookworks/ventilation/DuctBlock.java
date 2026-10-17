package dev.nookworks.ventilation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.nookworks.barter.BarterTerminalBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Duct: square ducting of galvanized sheet steel, spangled grey where the zinc crystallised on it. It joins up with the
 * ducts beside it, the back of a Ventilation Fan, the collar on top of a Range Hood and the side of a Chimney, and
 * carries their smoke (see {@link Ventilation}); where a run of it ends in the open, the smoke comes out.
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
        BlockState state = this.stateDefinition.any();
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

    private BlockState joined(LevelReader level, BlockPos pos, BlockState state) {
        for (Direction side : Direction.values()) {
            state = state.setValue(SIDES.get(side), joins(level.getBlockState(pos.relative(side)), side));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.joined(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(SIDES.get(side), joins(neighbourState, side));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }
}
