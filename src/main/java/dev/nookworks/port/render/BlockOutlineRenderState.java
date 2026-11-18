package dev.nookworks.port.render;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 26.3's {@code BlockOutlineRenderState}, as far as an outline of one's own reads it. 1.21.1 has no high-contrast
 * outline, and draws the outline once, after the see-through blocks.
 */
public record BlockOutlineRenderState(BlockPos pos, BlockState state, boolean isTranslucent, boolean highContrast) {}
