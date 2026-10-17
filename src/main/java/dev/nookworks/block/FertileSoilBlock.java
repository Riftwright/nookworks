package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.feature.WoodVinegar;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Nourished Soil: dirt fed with bone meal, cheap to make and good while it lasts.
 * <p>
 * It is not a permanent upgrade. Tilled, it grows what is on it several times over, but its {@link #RICHNESS}
 * falls away over time until there is nothing left but the farmland or dirt underneath. It still wants water
 * like any other bed: feeding the earth does not excuse you from watering it.
 * Treading on tilled soil ruins it outright, the way farmland is ruined, so it wants a path beside the bed
 * rather than through it. Keeping a garden going is meant to be something you do, not something you finish.
 * <p>
 * Registered as {@code nookworks:nourished_soil}, its name in the game; the class keeps its first name, and saved
 * worlds keep the id.
 */
public class FertileSoilBlock extends Block {
    /** How much is left in it. Fresh soil is {@link #MAX_RICHNESS}; below zero it is ordinary ground again. */
    public static final IntegerProperty RICHNESS = IntegerProperty.create("richness", 0, 3);
    public static final BooleanProperty TILLED = BooleanProperty.create("tilled");
    /** Wetness, exactly as farmland measures it: dry beds grow nothing faster, however well fed they are. */
    public static final IntegerProperty MOISTURE = BlockStateProperties.MOISTURE;
    public static final int MAX_RICHNESS = 3;
    /** Extra growth ticks handed to the plant above per random tick, at full richness. */
    public static final int EXTRA_TICKS = 3;
    public static final int MAX_MOISTURE = 7;
    /**
     * Chance per random tick that the soil loses a step.
     * <p>
     * A block gets a random tick about every 68 seconds, so a step averages 68 / 0.025 seconds and the four
     * steps from full to spent come to roughly three hours of loaded time.
     */
    public static final float DECAY_CHANCE = 0.025F;
    /** How far farmland looks for water, and so how far this does. */
    private static final int WATER_RANGE = 4;

    private static final VoxelShape TILLED_SHAPE = Block.column(16.0, 0.0, 15.0);

    public FertileSoilBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(RICHNESS, MAX_RICHNESS)
                .setValue(TILLED, false)
                .setValue(MOISTURE, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RICHNESS, TILLED, MOISTURE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TILLED) ? TILLED_SHAPE : super.getShape(state, level, pos, context);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return !state.getValue(TILLED) && super.isPathfindable(state, type);
    }

    public static final DeferredRegister<MapCodec<? extends BlockStateProvider>> PROVIDERS =
            DeferredRegister.create(Registries.BLOCK_STATE_PROVIDER_TYPE, Nookworks.MODID);

    static {
        PROVIDERS.register("tilled_soil", () -> Tilling.CODEC);
    }

    /**
     * A hoe turns it over, the same as it would any other ground: tilled, the rest kept, where the ground above is open.
     * In 26.3 a hoe's turns are data (the minecraft:hoe block transformer), and this one is added to them by our entry in
     * neoforge:transformables (data_maps/block), which also keeps it off the bottom face. No rule there can test one of
     * a block's own properties, so the entry names this provider, which asks.
     */
    public record Tilling() implements BlockStateProvider {
        public static final MapCodec<Tilling> CODEC = MapCodec.unit(new Tilling());

        @Override
        public MapCodec<Tilling> codec() {
            return CODEC;
        }

        @Override
        public BlockState getState(LevelAccessor level, RandomSource random, BlockPos pos) {
            BlockState tilled = this.getOptionalState(level, random, pos);
            return tilled != null ? tilled : level.getBlockState(pos);
        }

        @Override
        public @Nullable BlockState getOptionalState(LevelAccessor level, RandomSource random, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.getBlock() instanceof FertileSoilBlock && !state.getValue(TILLED)
                    && level.getBlockState(pos.above()).isAir() ? state.setValue(TILLED, true) : null;
        }
    }

    @Override
    public TriState canSustainPlant(BlockState state, BlockGetter level, BlockPos pos, Direction facing, BlockState plant) {
        if (facing != Direction.UP) {
            return TriState.DEFAULT;
        }
        // Untilled it is dirt (it is in #minecraft:dirt) and grows what dirt grows, by the plant's own rule; tilled it
        // is also a seed bed for crops. CropBlock also asks with the soil itself as the "plant" when it works out how
        // fast to grow, which is why this block counts too. Anything else - nether wart, say - is left to its own rule.
        boolean crop = plant.getBlock() instanceof CropBlock
                || plant.getBlock() instanceof StemBlock
                || plant.getBlock() instanceof PitcherCropBlock
                || plant.getBlock() instanceof CornBlock;
        return plant.is(this) || (state.getValue(TILLED) && crop) ? TriState.TRUE : TriState.DEFAULT;
    }

    /** Wet beds only. Bone meal in the earth is no substitute for water. */
    @Override
    public boolean isFertile(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(TILLED) && state.getValue(MOISTURE) > 0;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, double fallDistance) {
        if (level instanceof ServerLevel server && state.getValue(TILLED)
                && CommonHooks.onFarmlandTrample(server, pos,
                        Blocks.DIRT.defaultBlockState(), fallDistance, entity)) {
            ((FarmlandBlock) Blocks.FARMLAND).turnToBaseBlock(entity, state, level, pos); // to dirt, as farmland goes
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(TILLED)) {
            state = water(level, pos, state);
        }
        boost(level, pos, random);
        // sprinkled with wood vinegar, it gives up its richness half as fast
        if (random.nextFloat() < DECAY_CHANCE && !(WoodVinegar.isTreated(level, pos) && random.nextBoolean())) {
            spend(level, pos, level.getBlockState(pos));
        }
    }

    /**
     * Wets or dries the bed the way farmland does. A bed that dries out with nothing growing on it falls back
     * to untilled soil rather than to dirt: the richness is in the earth, not in the furrow, so you have only
     * to turn it over again.
     */
    private static BlockState water(ServerLevel level, BlockPos pos, BlockState state) {
        int moisture = state.getValue(MOISTURE);
        if (nearWater(level, pos) || level.isRainingAt(pos.above())) {
            if (moisture < MAX_MOISTURE) {
                BlockState wet = state.setValue(MOISTURE, MAX_MOISTURE);
                level.setBlock(pos, wet, Block.UPDATE_CLIENTS);
                return wet;
            }
            return state;
        }
        if (moisture > 0) {
            BlockState drier = state.setValue(MOISTURE, moisture - 1);
            level.setBlock(pos, drier, Block.UPDATE_CLIENTS);
            return drier;
        }
        if (!level.getBlockState(pos.above()).is(BlockTags.MAINTAINS_FARMLAND)) {
            BlockState loose = state.setValue(TILLED, false).setValue(MOISTURE, 0);
            level.setBlockAndUpdate(pos, loose);
            return loose;
        }
        return state;
    }

    private static boolean nearWater(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-WATER_RANGE, 0, -WATER_RANGE),
                pos.offset(WATER_RANGE, 1, WATER_RANGE))) {
            if (state.canBeHydrated(level, pos, level.getFluidState(near))) {
                return true;
            }
        }
        return FarmlandWaterManager.hasBlockWaterTicket(level, pos);
    }

    /** Takes one step out of the soil, and once it is empty leaves plain ground behind. */
    public static void spend(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.is(ModBlocks.FERTILE_SOIL.get())) {
            return; // no longer soil: a sapling on it grew into a tree, and the tree turned it to dirt
        }
        int left = state.getValue(RICHNESS);
        if (left > 0) {
            level.setBlock(pos, state.setValue(RICHNESS, left - 1), Block.UPDATE_ALL);
            return;
        }
        BlockState spent = state.getValue(TILLED)
                ? Blocks.FARMLAND.defaultBlockState().setValue(MOISTURE, state.getValue(MOISTURE))
                : Blocks.DIRT.defaultBlockState();
        level.setBlockAndUpdate(pos, spent);
    }

    /** Gives the plant above its extra growth ticks, more while the soil is fresh. Public for tests. */
    public static void boost(ServerLevel level, BlockPos soil, RandomSource random) {
        BlockState state = level.getBlockState(soil);
        if (!state.is(ModBlocks.FERTILE_SOIL.get())
                || !state.getValue(TILLED) || state.getValue(MOISTURE) == 0) {
            return;
        }
        int ticks = Math.max(1, EXTRA_TICKS * (state.getValue(RICHNESS) + 1) / (MAX_RICHNESS + 1));
        BlockPos above = soil.above();
        for (int i = 0; i < ticks; i++) {
            BlockState plant = level.getBlockState(above);
            if (!(plant.getBlock() instanceof VegetationBlock) || !plant.isRandomlyTicking()) {
                return;
            }
            plant.randomTick(level, above, random);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(RICHNESS) > 0 && random.nextInt(12) == 0
                && level.getBlockState(pos.above()).getBlock() instanceof VegetationBlock) {
            level.addParticle(ParticleTypes.HAPPY_VILLAGER,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.1, pos.getZ() + random.nextDouble(), 0, 0, 0);
        }
    }
}
