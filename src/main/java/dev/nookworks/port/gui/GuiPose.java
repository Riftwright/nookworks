package dev.nookworks.port.gui;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Stands in for the 2D matrix stack 26.3's {@code GuiGraphicsExtractor.pose()} returns (a {@code Matrix3x2fStack}): the
 * calls the mod makes on it, onto 1.21.1's {@link PoseStack}. Scaling and moving are in the screen's plane only - depth
 * is left to the drawing ({@link GuiGraphicsExtractor} keeps later things over earlier ones), so a scale here never
 * stretches an item's depth.
 */
public final class GuiPose {
    private final PoseStack stack;

    GuiPose(PoseStack stack) {
        this.stack = stack;
    }

    public GuiPose pushMatrix() {
        this.stack.pushPose();
        return this;
    }

    public GuiPose popMatrix() {
        this.stack.popPose();
        return this;
    }

    public GuiPose translate(float x, float y) {
        this.stack.translate(x, y, 0.0F);
        return this;
    }

    public GuiPose scale(float x, float y) {
        this.stack.scale(x, y, 1.0F);
        return this;
    }

    public GuiPose scale(float xy) {
        return this.scale(xy, xy);
    }

    /** Turns by {@code angle} radians in the screen's plane, as {@code Matrix3x2f.rotate} does. */
    public GuiPose rotate(float angle) {
        this.stack.mulPose(com.mojang.math.Axis.ZP.rotation(angle));
        return this;
    }

    /** The 1.21.1 stack underneath, for code that hands it on to 1.21.1 drawing. */
    public PoseStack stack() {
        return this.stack;
    }
}
