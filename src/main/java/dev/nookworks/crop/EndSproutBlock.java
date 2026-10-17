package dev.nookworks.crop;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.registry.ModCrops;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A End Seed sown in farmland (the user, 2026-10-12: "植えたら、ランダムで花や作物が育つ種"). It comes up as a sprout
 * nobody can name and grows to a bud; what the bud opens into is drawn then, by weight, from {@link EndSeedPlant}'s
 * data map - a flower or a fern most often, now and then a tall flower, sweet berries, bamboo or corn, very rarely a
 * wither rose. One seed, one plant: what it becomes drops itself, never another End Seed. A plant that does not
 * stand on farmland gets dirt under it; a tall one needs room above, and without it the draw is of the short ones.
 * <p>
 * It looks a plain seedling, so that whatever it opens into follows it naturally; what is strange about it is shown
 * in particles (the user, 2026-10-14: "エンドらしい不思議感は、テクスチャじゃなくてパーティクルとかで"): now and then
 * a faint purple mote rises from it, as from a charged respawn anchor but far rarer, and it opens in a small puff of
 * them.
 */
public class EndSproutBlock extends BloomingCropBlock {
    /** The outline, as tall as each stage is drawn (5, 11 and 12 pixels). */
    private static final VoxelShape[] SHAPES = {Block.column(8.0, 0.0, 5.0), Block.column(10.0, 0.0, 11.0), Block.column(12.0, 0.0, 12.0)};

    public EndSproutBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected ItemLike getBaseSeedId() {
        return ModCrops.END_SEED.get();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[this.getAge(state)];
    }

    /**
     * A mote one time in twenty-four the client ticks it: a sprout at the player's feet gives one every six seconds or
     * so, fewer further off - a lit candle smokes one time in three, a charged respawn anchor gives one every time. It
     * rises from among the leaves, slowly and then faster, and fades as it goes.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(24) != 0) {
            return;
        }
        double height = (this.getAge(state) + 1) * 0.22;
        level.addParticle(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                pos.getY() + 0.1 + random.nextDouble() * height,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.4,
                0.0, 0.008 + random.nextDouble() * 0.012, 0.0);
    }

    @Override
    protected boolean canBloom(LevelReader level, BlockPos pos) {
        return !EndSeedPlant.fitting(level, pos).isEmpty();
    }

    /** Draws a plant and opens into it; one that stands on neither farmland nor dirt is put back and another drawn. */
    @Override
    protected boolean bloom(Level level, BlockPos pos) {
        List<EndSeedPlant.Entry> pool = new ArrayList<>(EndSeedPlant.fitting(level, pos));
        while (!pool.isEmpty()) {
            EndSeedPlant.Entry drawn = EndSeedPlant.draw(pool, level.getRandom());
            if (EndSeedPlant.open(level, pos, drawn.state())) {
                if (level instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.REVERSE_PORTAL, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 10, 0.2, 0.2, 0.2, 0.01);
                }
                return true;
            }
            pool.remove(drawn);
        }
        return false;
    }
}
