package dev.nookworks.wood;

import net.minecraft.world.level.block.LeavesBlock;

/**
 * Dawnwood leaves. Their colour is drawn into the texture rather than taken from the biome.
 * <p>
 * PORT: 26.3's leaves let falling-leaf particles go (FallingParticlesLeavesBlock with ParticleTypes.TINTED_LEAVES,
 * given the needles' green). 1.21.1 has neither that block class nor the particle, and none of its own trees lets
 * leaves fall - every tree's falling leaves came with 1.21.5; only cherry leaves let petals go. The owner's ruling
 * (2026-11-17): where the other trees let nothing fall, Dawnwood lets nothing fall either. A particle of the port's
 * own was made for it first and taken out on that ruling; do not bring it back.
 */
public class DawnwoodLeavesBlock extends LeavesBlock {
    public DawnwoodLeavesBlock(Properties properties) {
        super(properties);
    }
}
