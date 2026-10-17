package dev.nookworks.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.CommonHooks;

/**
 * A crop that shows three stages and then opens into another plant, as vanilla's torchflower crop becomes a
 * torchflower: the Sunflower Seeds' crop into vanilla's sunflower, the End Seed's sprout into whatever it draws. A
 * bud that cannot open yet waits as a bud, and takes no bone meal. Bone meal grows it a stage at a time.
 */
public abstract class BloomingCropBlock extends CropBlock {
    /** The stages it shows: sprout, young plant, bud. The age after the last is the plant it opens into. */
    public static final IntegerProperty AGE = BlockStateProperties.AGE_2;
    public static final int BLOOM = 3;
    private static final VoxelShape[] SHAPES = Block.boxes(2, age -> Block.column(8.0, 0.0, 5 + age * 5));

    protected BloomingCropBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    /** Whether the bud at {@code pos} could open now. */
    protected abstract boolean canBloom(LevelReader level, BlockPos pos);

    /** Opens the bud at {@code pos}; false, and nothing changed, when it cannot. */
    protected abstract boolean bloom(Level level, BlockPos pos);

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected IntegerProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return BLOOM;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.getAge(state)];
    }

    /** Never the bloom's own age here: that is what {@link #bloom} puts in its place. */
    @Override
    public BlockState getStateForAge(int age) {
        return super.getStateForAge(Math.min(age, BLOOM - 1));
    }

    /** As the torchflower's: a third of its random ticks pass it by, so it grows slower than wheat for its fewer stages. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1) || random.nextInt(3) == 0 || level.getRawBrightness(pos, 0) < 9) {
            return;
        }
        int age = this.getAge(state);
        float speed = getGrowthSpeed(state, level, pos);
        if (CommonHooks.canCropGrow(level, pos, state, random.nextInt((int) (25.0F / speed) + 1) == 0)) {
            if (this.grow(level, pos, age + 1)) {
                CommonHooks.fireCropGrowPost(level, pos, state);
            }
        }
    }

    @Override
    public void growCrops(Level level, BlockPos pos, BlockState state) {
        this.grow(level, pos, this.getAge(state) + 1);
    }

    /** A bud that cannot open takes no bone meal: it would be spent for nothing. */
    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, BonemealSource source) {
        return this.getAge(state) < BLOOM - 1 || canBloom(level, pos);
    }

    /** To {@code age}, or open; false when it is a bud that cannot open. */
    private boolean grow(Level level, BlockPos pos, int age) {
        if (age < BLOOM) {
            level.setBlock(pos, this.getStateForAge(age), Block.UPDATE_CLIENTS);
            return true;
        }
        return bloom(level, pos);
    }

    /** Room above for a two-block plant's top half. */
    public static boolean roomAbove(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.above()).canBeReplaced() && pos.getY() < level.getMaxY();
    }
}
