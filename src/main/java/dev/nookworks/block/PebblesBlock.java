package dev.nookworks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Pebbles: small smooth stones to scatter on a path, a yard or a pond's floor - for the look and nothing else. One to
 * four lie in a block, as sea pickles do: another used on them joins them, each a little apart from the rest, its own
 * size and turned its own way, so they lie scattered (tools/textures/gen_pebbles.py), and the block is turned a quarter
 * at random from place to place, so a path of them never repeats. They hold the water they are set down in.
 * <p>
 * They want a whole top face under them, as leaf litter does, since four spread over the block. There is nothing to
 * bump into: a path of them is walked over as one of flowers is, not stepped up onto at every block, and the stones are
 * heard over the ground under them (in #minecraft:combination_step_sound_blocks).
 */
public class PebblesBlock extends Block implements SimpleWaterloggedBlock {
    public static final int MAX = 4;
    public static final IntegerProperty PEBBLES = IntegerProperty.create("pebbles", 1, MAX);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /**
     * What a click finds: square about the middle, so it holds the stones whichever way the model is turned - one near
     * the middle, two to four reaching to a pixel from the block's sides (gen_pebbles.OUTLINE, which checks them).
     */
    private static final VoxelShape ONE = Block.column(10.0, 0.0, 3.0);
    private static final VoxelShape MORE = Block.column(14.0, 0.0, 3.0);

    public PebblesBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PEBBLES, 1).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PEBBLES, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState there = context.getLevel().getBlockState(context.getClickedPos());
        if (there.is(this)) {
            return there.setValue(PEBBLES, Math.min(MAX, there.getValue(PEBBLES) + 1));
        }
        return this.defaultBlockState().setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).is(Fluids.WATER));
    }

    /** Another pebble in hand adds one to those lying here, unless sneaking, as with sea pickles and candles. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive() && context.getItemInHand().is(this.asItem()) && state.getValue(PEBBLES) < MAX
                || super.canBeReplaced(state, context);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    /** With the ground taken from under them they come up, as many as there were. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PEBBLES) == 1 ? ONE : MORE;
    }
}
