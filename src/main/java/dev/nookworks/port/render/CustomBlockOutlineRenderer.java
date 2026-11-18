package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

/** 26.3 NeoForge's {@code CustomBlockOutlineRenderer}: an outline of one's own; true if the block's own is not drawn. */
@FunctionalInterface
public interface CustomBlockOutlineRenderer {
    boolean render(BlockOutlineRenderState renderState, SubmitNodeCollector submitNodeCollector, PoseStack poseStack,
            LevelRenderState levelRenderState);
}
