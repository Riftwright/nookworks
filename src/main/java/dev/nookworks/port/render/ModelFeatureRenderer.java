package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * 26.3's {@code ModelFeatureRenderer}, for its {@link CrumblingOverlay}: a block being broken, how far ({@code progress}
 * 0-9), and the pose the crack's picture is laid from.
 * <p>
 * 1.21.1 hands a block entity renderer no such thing: while its block is being broken, the buffers it draws into lay the
 * cracks over everything drawn in a render type that takes them (the entity and block-sheet types do). So the port's
 * renderers are always handed {@code null} here, and the cracks still come, from the buffers.
 */
public final class ModelFeatureRenderer {
    private ModelFeatureRenderer() {}

    public record CrumblingOverlay(int progress, PoseStack.Pose cameraPose) {}
}
