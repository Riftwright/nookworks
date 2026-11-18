package dev.nookworks.port.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 26.3's {@code RenderTypes} factory, by its names, each giving 1.21.1's render type that draws the same way: 26.3's
 * {@code entity_cutout} draws both sides (1.21.1's {@code entityCutoutNoCull}), {@code entity_cutout_z_offset} likewise
 * ({@code entityCutoutNoCullZOffset}). 1.21.1 has one kind of line: the outline kinds 26.3 adds (high contrast, depth
 * bias, translucent) are all its {@code lines}.
 */
public final class RenderTypes {
    private RenderTypes() {}

    public static RenderType entitySolid(ResourceLocation texture) {
        return RenderType.entitySolid(texture);
    }

    /** Both sides drawn, cut out where the picture is clear, as 26.3's entity cutout. */
    public static RenderType entityCutout(ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture, true);
    }

    public static RenderType entityCutout(ResourceLocation texture, boolean affectsOutline) {
        return RenderType.entityCutoutNoCull(texture, affectsOutline);
    }

    /** 26.3's {@code entityCutoutCull}: one side, as 1.21.1's plain entity cutout. */
    public static RenderType entityCutoutCull(ResourceLocation texture) {
        return RenderType.entityCutout(texture);
    }

    public static RenderType entityCutoutZOffset(ResourceLocation texture) {
        return RenderType.entityCutoutNoCullZOffset(texture, true);
    }

    public static RenderType entityCutoutZOffset(ResourceLocation texture, boolean affectsOutline) {
        return RenderType.entityCutoutNoCullZOffset(texture, affectsOutline);
    }

    public static RenderType entityTranslucent(ResourceLocation texture) {
        return RenderType.entityTranslucent(texture, true);
    }

    public static RenderType entityTranslucent(ResourceLocation texture, boolean affectsOutline) {
        return RenderType.entityTranslucent(texture, affectsOutline);
    }

    /** A banner's pattern layer, as 1.21.1 draws one (no outline of its own). */
    public static RenderType bannerPattern(ResourceLocation texture) {
        return RenderType.entityNoOutline(texture);
    }

    public static RenderType text(ResourceLocation texture) {
        return RenderType.text(texture);
    }

    public static RenderType beaconBeam(ResourceLocation texture, boolean translucent) {
        return RenderType.beaconBeam(texture, translucent);
    }

    public static RenderType endPortal() {
        return RenderType.endPortal();
    }

    public static RenderType lines() {
        return RenderType.lines();
    }

    /** PORT: 1.21.1 has no depth-biased lines; its plain lines are drawn instead. */
    public static RenderType linesDepthBias() {
        return RenderType.lines();
    }

    /** PORT: 1.21.1's lines are translucent already. */
    public static RenderType linesTranslucent() {
        return RenderType.lines();
    }

    /** PORT: 1.21.1 has no high-contrast outline (it came later): its plain lines. */
    public static RenderType secondaryBlockOutline() {
        return RenderType.lines();
    }

    /**
     * 26.3's {@code windowRenderState.appropriateLineWidth}: the width the window's size calls for, as 26.3 works it out
     * (2.5, more on a window over 1920 pixels across). PORT: 1.21.1 draws its lines at one width; this is only passed on.
     */
    public static float lineWidth() {
        return Math.max(2.5F, net.minecraft.client.Minecraft.getInstance().getWindow().getWidth() / 1920.0F * 2.5F);
    }

    public static RenderType debugQuads() {
        return RenderType.debugQuads();
    }
}
