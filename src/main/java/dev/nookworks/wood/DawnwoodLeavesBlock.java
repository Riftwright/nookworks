package dev.nookworks.wood;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FallingParticlesLeavesBlock;
import net.minecraft.world.level.block.sounds.AmbientLeavesBlockSoundPlayer;

/**
 * Dawnwood leaves. Their colour is drawn into the texture rather than taken from the biome, so the falling needles
 * are given that same green instead of the biome's.
 */
public class DawnwoodLeavesBlock extends FallingParticlesLeavesBlock {
    private static final int NEEDLE_GREEN = 0xFF5A9638;

    public DawnwoodLeavesBlock(Properties properties) {
        super(0.01F, AmbientLeavesBlockSoundPlayer.noAmbientSound(), properties);
    }

    @Override
    protected void spawnFallingLeavesParticle(Level level, BlockPos pos, RandomSource random) {
        ParticleUtils.spawnParticleBelow(level, pos, random, ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, NEEDLE_GREEN));
    }
}
