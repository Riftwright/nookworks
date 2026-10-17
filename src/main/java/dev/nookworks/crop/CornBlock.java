package dev.nookworks.crop;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * Corn: a kernel planted in farmland comes up a single block tall, and from {@link #DOUBLE_FROM} on grows a second
 * block above - the tall stalk, the tassel at the top, and at the last stage the ears on its side. It is ripe at
 * {@link #MAX_AGE} and gives two to four ears; broken sooner, nothing, as the kernel it grew from is spent (the user,
 * 2026-10-03). Bone meal grows it a stage at a time, as it does a pitcher plant.
 */
public class CornBlock extends DoublePlantBlock implements BonemealableBlock {
    public static final int MAX_AGE = 5;
    public static final IntegerProperty AGE = BlockStateProperties.AGE_5;
    public static final int DOUBLE_FROM = 3;
    /** Heights of the plant at each age, in pixels over its lower block. */
    private static final int[] HEIGHTS = {5, 10, 15, 22, 28, 30};

    public CornBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AGE, 0).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
        super.createBlockStateDefinition(builder);
    }

    public static boolean isDouble(int age) {
        return age >= DOUBLE_FROM;
    }

    /**
     * Broken from the top, the plant drops as it does from the bottom - with the tool in hand, so Fortune counts either
     * way. Left to vanilla, the lower half went with its top without a tool, and Fortune only from below.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.preventsBlockDrops() && state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockPos below = pos.below();
            BlockState lower = level.getBlockState(below);
            if (lower.is(this) && lower.getValue(HALF) == DoubleBlockHalf.LOWER) {
                Block.dropResources(lower, level, below, null, player, player.getMainHandItem());
                level.setBlock(below, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int height = HEIGHTS[state.getValue(AGE)];
        int width = state.getValue(AGE) == 0 ? 6 : 12;
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? Block.column(width, 0.0, Math.min(16, height))
                : height <= 16 ? Shapes.empty() : Block.column(width, 0.0, height - 16);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /** Planted, it is the young plant alone: its top half comes later, as it grows. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(BlockTags.SUPPORTS_CROPS);
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
            BlockState below = level.getBlockState(pos.below());
            return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER && isDouble(below.getValue(AGE));
        }
        // the soil has its say first, as it does for any crop: Nourished Soil once tilled, another mod's farmland
        BlockState soil = level.getBlockState(pos.below());
        var decision = soil.canSustainPlant(level, pos.below(), Direction.UP, state);
        if (!decision.isDefault()) {
            return decision.isTrue();
        }
        return this.mayPlaceOn(soil, level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (isDouble(state.getValue(AGE))) {
            return super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
        }
        return state.canSurvive(level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(AGE) < MAX_AGE;
    }

    /**
     * In good light, a stage every few minutes; twice as fast in watered farmland as in dry. Asked and told by NeoForge's
     * crop events as vanilla's crops are, so a seasons or growth mod rules corn as it rules wheat.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockState soil = level.getBlockState(pos.below());
        float speed = soil.hasProperty(FarmlandBlock.MOISTURE)
                && soil.getValue(FarmlandBlock.MOISTURE) > 0 ? 5.0F : 2.5F;
        if (CropBlock.hasSufficientLight(level, pos)
                && CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25.0F / speed) + 1) == 0)
                && grow(level, pos, state)) {
            CommonHooks.fireCropGrowPost(level, pos, state);
        }
    }

    /** One stage, reaching into the block above from {@link #DOUBLE_FROM} on - if there is room for it there. */
    public static boolean grow(ServerLevel level, BlockPos lowerPos, BlockState lower) {
        int age = lower.getValue(AGE) + 1;
        if (age > MAX_AGE || !canGrowTo(level, lowerPos, age)) {
            return false;
        }
        BlockState grown = lower.setValue(AGE, age);
        level.setBlock(lowerPos, grown, Block.UPDATE_CLIENTS);
        if (isDouble(age)) {
            level.setBlock(lowerPos.above(), grown.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        }
        return true;
    }

    private static boolean canGrowTo(LevelReader level, BlockPos lowerPos, int age) {
        if (!isDouble(age)) {
            return true;
        }
        BlockState above = level.getBlockState(lowerPos.above());
        return level.isInsideBuildHeight(lowerPos.above()) && (above.isAir() || above.getBlock() instanceof CornBlock);
    }

    /** The lower half's position and state, from either half; null for a top half that lost its stalk. */
    private @Nullable BlockPos lowerOf(LevelReader level, BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
            return pos;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.is(this) && below.getValue(HALF) == DoubleBlockHalf.LOWER ? pos.below() : null;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        BlockPos lower = this.lowerOf(level, pos, state);
        if (lower == null) {
            return false;
        }
        int age = level.getBlockState(lower).getValue(AGE);
        return age < MAX_AGE && canGrowTo(level, lower, age + 1);
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state, BonemealSource source) {
        BlockPos lower = this.lowerOf(level, pos, state);
        if (lower != null) {
            grow(level, lower, level.getBlockState(lower));
        }
    }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(ModKitchen.CORN_KERNELS.get());
    }
}
