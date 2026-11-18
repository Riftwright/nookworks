package dev.nookworks.port.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code net.minecraft.util.LightCoordsUtil}: packed light, block light in bits 4-7 and sky light in bits 20-23,
 * as 1.21.1's {@code LightTexture.pack} packs it.
 */
public final class LightCoordsUtil {
    public static final int FULL_BRIGHT = 0xF000F0;
    public static final int FULL_SKY = 0xF00000;

    private LightCoordsUtil() {}

    public static int pack(int block, int sky) {
        return block << 4 | sky << 20;
    }

    public static int block(int packed) {
        return packed >> 4 & 15;
    }

    public static int sky(int packed) {
        return packed >> 20 & 15;
    }

    public static int withBlock(int coords, int block) {
        return coords & 0xFF0000 | block << 4;
    }

    /** The brighter of the two, block and sky each on its own. */
    public static int max(int coords1, int coords2) {
        return pack(Math.max(block(coords1), block(coords2)), Math.max(sky(coords1), sky(coords2)));
    }

    /**
     * The light the world draws a thing at {@code pos} with: full for a block that renders emissive, else the block and
     * sky light there, the block light at least what the block there gives off itself.
     */
    public static int getLightCoords(BlockAndTintGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.emissiveRendering(level, pos)) {
            return FULL_BRIGHT;
        }
        int packed = pack(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos));
        int emission = state.getLightEmission(level, pos);
        return block(packed) < emission ? withBlock(packed, emission) : packed;
    }
}
