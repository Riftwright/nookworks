package dev.nookworks.port.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code BlockTintSource}: the colour of one tint index of a block's model, held (no world) or where it stands.
 * 1.21.1 asks a {@code BlockColor} for every index at once, with a null world for a held block; the port's
 * {@link RegisterColorHandlersEvent.BlockTintSources} turns a list of these, by index, into one.
 */
public interface BlockTintSource {
    int color(BlockState state);

    default int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return this.color(state);
    }
}
