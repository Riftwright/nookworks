package dev.nookworks.furniture;

import dev.nookworks.block.RopeBlock;
import dev.nookworks.registry.GlazedPots;
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
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hanging Planter: a glazed bowl on four cords, hung from whatever is above it (anything a lantern can hang from,
 * a chain, a rope), with a trailing plant spilling over the rim. Decoration only. It falls if what holds it goes.
 * <p>
 * The plant grows down below it, now and then or with bone meal, a block at a time ({@link TrailingPlantBlock}) up
 * to {@link #MAX_TRAIL} blocks, and stops at anything in its way.
 */
public class HangingPlanterBlock extends Block implements BonemealableBlock {
    /** The bowl and the cords to the hook: you walk under the trailing leaves, not into them. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 2, 3, 13, 8, 13), Block.box(7, 8, 7, 9, 16, 9));
    /** How many blocks the plant trails down below the planter at most. */
    public static final int MAX_TRAIL = 3;
    /** One random tick in this many grows the plant: a block every few minutes. */
    private static final int GROWTH_CHANCE = 5;

    public HangingPlanterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN) || level.getBlockState(pos.above()).getBlock() instanceof ChainBlock
                || level.getBlockState(pos.above()).getBlock() instanceof RopeBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    /** The strands this planter grows. */
    public TrailingPlantBlock trail() {
        return GlazedPots.trailOf(this);
    }

    /** Where the plant under the planter at {@code pos} would grow next, or null if it is full grown or blocked. */
    public static BlockPos growthSpot(LevelReader level, BlockPos pos) {
        if (!(level.getBlockState(pos).getBlock() instanceof HangingPlanterBlock planter)) {
            return null;
        }
        BlockPos cursor = pos.below();
        for (int i = 0; i < MAX_TRAIL; i++, cursor = cursor.below()) {
            if (!level.getBlockState(cursor).is(planter.trail())) {
                return !level.isOutsideBuildHeight(cursor) && level.isEmptyBlock(cursor) ? cursor : null;
            }
        }
        return null;
    }

    /** Grows the plant under the planter at {@code pos} a block further down, if it can. */
    public static boolean grow(ServerLevel level, BlockPos pos) {
        BlockPos spot = growthSpot(level, pos);
        if (spot == null) {
            return false;
        }
        level.setBlockAndUpdate(spot, ((HangingPlanterBlock) level.getBlockState(pos).getBlock()).trail().defaultBlockState());
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(GROWTH_CHANCE) == 0) {
            grow(level, pos);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return growthSpot(level, pos) != null;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        grow(level, pos);
    }
}
