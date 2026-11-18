package dev.nookworks.port.render;

import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.block.state.BlockState;

/** 26.3's {@code BlockTintSources}, the ones the mod uses: grass and water, by the biome where the block stands. */
public final class BlockTintSources {
    private BlockTintSources() {}

    public static BlockTintSource grass() {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return GrassColor.getDefaultColor();
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return BiomeColors.getAverageGrassColor(level, pos);
            }
        };
    }

    public static BlockTintSource water() {
        return new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return -1;
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return BiomeColors.getAverageWaterColor(level, pos);
            }
        };
    }
}
