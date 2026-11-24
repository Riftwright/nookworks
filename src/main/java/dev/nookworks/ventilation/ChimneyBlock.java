package dev.nookworks.ventilation;

import java.util.Map;

import dev.nookworks.block.PipeSides;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Chimney: a stack of brick, a stone cap and a round clay chimney pot on the top one ({@link #TOP}: nothing of the
 * chimney over it). Smoke that reaches it comes out of the pot (see {@link Ventilation}): up a Range Hood, along Ducts,
 * which join its sides with a collar, or up a Flue over a fireplace. Stood straight on a campfire or a fire it takes
 * that fire's smoke itself (the user, 2026-11-23: 「焚き火の直上の煙突や煙道は煙出てもいいよ」); on a furnace, which has no
 * hole on top, nothing. A side's collar is parted from the Duct beside it with a sneak-use of the Wrench, as a duct's
 * side is ({@link DuctBlock#toggleSide}; the user, 2026-11-23).
 */
public class ChimneyBlock extends Block {
    public static final BooleanProperty TOP = BooleanProperty.create("top");
    public static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, PipeBlock.NORTH, Direction.EAST, PipeBlock.EAST,
            Direction.SOUTH, PipeBlock.SOUTH, Direction.WEST, PipeBlock.WEST);
    private static final VoxelShape STACK = Block.box(3, 0, 3, 13, 16, 13);
    private static final VoxelShape TOP_SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 12, 13), Block.box(2, 12, 2, 14, 13, 14),
            Block.box(5, 13, 5, 11, 16, 11));

    public ChimneyBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(TOP, true);
        for (Direction side : SIDES.keySet()) {
            state = state.setValue(SIDES.get(side), false).setValue(PipeSides.OFF.get(side), false);
        }
        this.registerDefaultState(state);
    }

    /** Its joins and the sides parted with the Wrench turn with it, in a structure set down turned or mirrored. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipeSides.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipeSides.turned(state, mirror::mirror);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TOP);
        SIDES.values().forEach(builder::add);
        SIDES.keySet().forEach(side -> builder.add(PipeSides.OFF.get(side)));
    }

    private BlockState shaped(LevelReader level, BlockPos pos, BlockState state) {
        state = state.setValue(TOP, !(level.getBlockState(pos.above()).getBlock() instanceof ChimneyBlock));
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            BlockState neighbour = level.getBlockState(pos.relative(side.getKey()));
            state = state.setValue(side.getValue(), neighbour.getBlock() instanceof DuctBlock && !PipeSides.isOff(state, side.getKey())
                    && !PipeSides.isOff(neighbour, side.getKey().getOpposite()));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.shaped(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return this.shaped(level, pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TOP) ? TOP_SHAPE : STACK;
    }
}
