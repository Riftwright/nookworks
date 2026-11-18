package dev.nookworks.port.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.client.renderer.RenderType;

/**
 * 26.3's {@code BlockModelRenderState}: a block state's model as it is to be drawn away from the world - its parts, its
 * tints (held, no world) and the light it gives off itself ({@link #blockLightCoords}). Filled by
 * {@link BlockModelResolver}.
 */
public class BlockModelRenderState {
    public static final int[] EMPTY_TINTS = new int[0];
    @Nullable BlockStateModelPart part;
    int[] tints = EMPTY_TINTS;
    boolean translucent;
    public int blockLightCoords;

    public void clear() {
        this.part = null;
        this.tints = EMPTY_TINTS;
        this.translucent = false;
        this.blockLightCoords = 0;
    }

    public boolean isEmpty() {
        return this.part == null;
    }

    /** The model in the render type its block is drawn in as an item, at the brighter of the light given and its own. */
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int externalLightCoords, int overlayCoords, int outlineColor) {
        if (this.part != null) {
            RenderType type = this.translucent ? net.minecraft.client.renderer.Sheets.translucentItemSheet()
                    : net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
            submitNodeCollector.submitBlockModel(poseStack, type, List.of(this.part), this.tints,
                    LightCoordsUtil.max(externalLightCoords, this.blockLightCoords), overlayCoords, outlineColor);
        }
    }

    /** The model in its own render types, at the light given (26.3's NeoForge call; the caller adds the block's own light). */
    public void submitMultiLayer(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int externalLightCoords, int overlayCoords,
            int outlineColor) {
        if (this.part != null) {
            submitNodeCollector.submitMultiLayerBlockModel(poseStack, List.of(this.part), this.translucent, this.tints, externalLightCoords,
                    overlayCoords, outlineColor);
        }
    }
}
