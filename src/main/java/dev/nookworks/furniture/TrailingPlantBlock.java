package dev.nookworks.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The strands a Hanging Planter grows down below itself, one block at a time. It hangs from its planter (or the
 * strands above), is walked through like vines, drops nothing, and goes when what holds it goes. Bone meal on it is
 * bone meal on its planter.
 */
public class TrailingPlantBlock extends Block implements BonemealableBlock {
    /** The lowest block of the strands, where they end. */
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 16.0);

    public TrailingPlantBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.is(this) || above.getBlock() instanceof HangingPlanterBlock planter && planter.trail() == this;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (side == Direction.UP && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return side == Direction.DOWN ? state.setValue(TIP, !neighbourState.is(this)) : state;
    }

    /** The planter these strands grow from, or null. */
    private BlockPos planter(LevelReader level, BlockPos pos) {
        BlockPos cursor = pos.above();
        for (int i = 0; i < HangingPlanterBlock.MAX_TRAIL && level.getBlockState(cursor).is(this); i++) {
            cursor = cursor.above();
        }
        return level.getBlockState(cursor).getBlock() instanceof HangingPlanterBlock ? cursor : null;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        BlockPos planter = this.planter(level, pos);
        return planter != null && HangingPlanterBlock.growthSpot(level, planter) != null;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        BlockPos planter = this.planter(level, pos);
        if (planter != null) {
            HangingPlanterBlock.grow(level, planter);
        }
    }
}
