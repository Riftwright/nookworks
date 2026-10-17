package dev.nookworks.crop;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Rice, the lower half: planted in a block of standing water one deep, on soil (dirt, grass, farmland, mud, clay). It
 * grows through four stages, then heads up into the block above ({@link RicePaniclesBlock}); the panicles ripen
 * gold and are the harvest. The plant itself stays when they are cut, and heads up again.
 * <p>
 * It stands in its water: the block is always a water source, like seagrass, and turns back into water when broken.
 */
public class RiceBlock extends VegetationBlock implements BonemealableBlock, LiquidBlockContainer {
    public static final int MAX_AGE = 3;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    /** One random tick in this many grows it a stage, in good light: about as quick as wheat. */
    static final int GROWTH_CHANCE = 5;
    private static final VoxelShape[] SHAPES = {Block.column(8.0, 0.0, 6.0), Block.column(10.0, 0.0, 10.0),
            Block.column(12.0, 0.0, 14.0), Block.column(12.0, 0.0, 16.0)};

    public RiceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(AGE)];
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        // Mud and clay are not #dirt, but they are what a paddy is floored with.
        return state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.MUD) || state.is(Blocks.CLAY);
    }

    /** Only into a full block of water with open air above: rice wants its feet wet and its head dry. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState water = context.getLevel().getFluidState(context.getClickedPos());
        BlockPos above = context.getClickedPos().above();
        return water.is(FluidTags.WATER) && water.isSource() && context.getLevel().getFluidState(above).isEmpty()
                ? super.getStateForPlacement(context) : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos) && level.getFluidState(pos.above()).isEmpty();
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState result = super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
        ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        if (result.isAir()) {
            // Drowned or cut off from its soil: it comes away next tick, dropping what it would if broken (a grain
            // at least), and leaves its water behind. Doing it here would lose the drops: there is no level to drop in.
            ticks.scheduleTick(pos, this, 1);
            return state;
        }
        return result;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true); // the loot, and the water it stood in (its fluid state) stays
        } else {
            super.tick(state, level, pos, random);
        }
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return Fluids.WATER.getSource(false);
    }

    @Override
    public boolean canPlaceLiquid(@Nullable LivingEntity user, BlockGetter level, BlockPos pos, BlockState state, Fluid type) {
        return false;
    }

    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluid) {
        return false;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // its own stages, then heading up; the heads above ripen on their own ticks (this one ripening them too, they
        // ripened twice as fast as the plant grew). NeoForge's crop events ask and are told, as for vanilla's crops.
        if (level.getRawBrightness(pos.above(), 0) >= 9
                && (state.getValue(AGE) < MAX_AGE || level.getBlockState(pos.above()).isAir())
                && CommonHooks.canCropGrow(level, pos, state, random.nextInt(GROWTH_CHANCE) == 0)
                && grow(level, pos, state)) {
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    /** One step: a stage of the plant, then heading up, then a stage of the heads above. */
    static boolean grow(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(AGE) < MAX_AGE) {
            level.setBlock(pos, state.setValue(AGE, state.getValue(AGE) + 1), Block.UPDATE_CLIENTS);
            return true;
        }
        BlockPos above = pos.above();
        BlockState top = level.getBlockState(above);
        if (top.isAir()) {
            level.setBlock(above, ModCrops.RICE_PANICLES.get().defaultBlockState(), Block.UPDATE_ALL);
            return true;
        }
        if (top.getBlock() instanceof RicePaniclesBlock && top.getValue(RicePaniclesBlock.AGE) < RicePaniclesBlock.MAX_AGE) {
            level.setBlock(above, top.setValue(RicePaniclesBlock.AGE, top.getValue(RicePaniclesBlock.AGE) + 1), Block.UPDATE_CLIENTS);
            return true;
        }
        return false;
    }

    /** Whether {@link #grow} would do anything. */
    static boolean canGrow(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(AGE) < MAX_AGE) {
            return true;
        }
        BlockState top = level.getBlockState(pos.above());
        return top.isAir() || top.getBlock() instanceof RicePaniclesBlock && top.getValue(RicePaniclesBlock.AGE) < RicePaniclesBlock.MAX_AGE;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return canGrow(level, pos, state);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        grow(level, pos, state);
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModCrops.BROWN_RICE.get());
    }
}
