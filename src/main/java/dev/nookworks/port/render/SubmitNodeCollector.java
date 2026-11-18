package dev.nookworks.port.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.port.util.ARGB;
import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.RenderTypeHelper;

/**
 * 26.3's {@code SubmitNodeCollector}, the calls the mod makes, drawing at once into the 1.21.1 buffers it wraps. 26.3
 * queues each call and draws them later, sorted by kind (solid, then see-through by distance) and by {@link #order};
 * 1.21.1's buffers already keep each render type's geometry apart and draw the see-through types last, so drawing at
 * once gives the same picture. The pose is read as it is at the call, as 26.3 copies it.
 */
public class SubmitNodeCollector {
    private final MultiBufferSource buffers;

    public SubmitNodeCollector(MultiBufferSource buffers) {
        this.buffers = buffers;
    }

    /** The 1.21.1 buffers drawn into. */
    public MultiBufferSource buffers() {
        return this.buffers;
    }

    /**
     * 26.3's draw order within a kind (a banner's patterns over its cloth). Within one render type 1.21.1 draws in the
     * order things are added, and each pattern is a render type of its own drawn after the cloth's: nothing to do.
     */
    public SubmitNodeCollector order(int order) {
        return this;
    }

    /** Geometry of one's own, into a buffer of {@code renderType}. */
    public void submitCustomGeometry(PoseStack poseStack, RenderType renderType, CustomGeometryRenderer customGeometryRenderer) {
        customGeometryRenderer.render(poseStack.last(), this.buffers.getBuffer(renderType));
    }

    @FunctionalInterface
    public interface CustomGeometryRenderer {
        void render(PoseStack.Pose pose, VertexConsumer buffer);
    }

    /**
     * Block model parts, each quad in its colour - its tint index's in {@code tintLayers}, white past them - and the light
     * and overlay given, on the block atlas: cut out, or see-through where the part's model says so (its
     * {@code render_type}) or {@code translucent} says the parts have see-through faces. 26.3 sorts each quad into
     * solid, cut out or see-through by itself; a cut-out drawing of a solid face looks the same.
     */
    public void submitMultiLayerBlockModel(PoseStack poseStack, List<BlockStateModelPart> modelParts, boolean translucent, int[] tintLayers,
            int lightCoords, int overlayCoords, int outlineColor) {
        for (BlockStateModelPart part : modelParts) {
            RenderType type = translucent || part.translucent()
                    ? RenderTypeHelper.getEntityRenderType(RenderType.translucent(), false)
                    : net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
            putQuads(poseStack.last(), this.buffers.getBuffer(type), part.allQuads(), tintLayers, -1, lightCoords, overlayCoords);
        }
    }

    /** The same parts in one render type (26.3's {@code submitBlockModel}). */
    public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> modelParts, int[] tintLayers,
            int lightCoords, int overlayCoords, int outlineColor) {
        VertexConsumer buffer = this.buffers.getBuffer(renderType);
        for (BlockStateModelPart part : modelParts) {
            putQuads(poseStack.last(), buffer, part.allQuads(), tintLayers, -1, lightCoords, overlayCoords);
        }
    }

    /** A block's cracks, {@code progress} 0-9, laid over the parts' faces as over a block being broken. */
    public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress, boolean isBlockTranslucent) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = new SheetedDecalTextureGenerator(
                this.buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(Mth.clamp(progress, 0, 9))), pose, 1.0F);
        for (BlockStateModelPart part : parts) {
            putQuads(pose, buffer, part.allQuads(), BlockModelRenderState.EMPTY_TINTS, -1, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }
    }

    static void putQuads(PoseStack.Pose pose, VertexConsumer buffer, List<BakedQuad> quads, int[] tintLayers, int baseColour, int light,
            int overlay) {
        for (BakedQuad quad : quads) {
            int tint = quad.getTintIndex();
            int colour = tint >= 0 && tint < tintLayers.length ? ARGB.multiply(baseColour, tintLayers[tint]) : baseColour;
            buffer.putBulkData(pose, quad, ARGB.redFloat(colour), ARGB.greenFloat(colour), ARGB.blueFloat(colour), ARGB.alphaFloat(colour),
                    light, overlay, false);
        }
    }

    /**
     * A shape's edges as lines, in {@code color} (ARGB). PORT: 1.21.1 draws every line the width its options set; the
     * {@code width} asked for is not kept, nor is {@code afterTerrain} (1.21.1 draws an outline where the call is made).
     */
    public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width, boolean afterTerrain) {
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer buffer = this.buffers.getBuffer(renderType);
        shape.forAllEdges((x0, y0, z0, x1, y1, z1) -> {
            float dx = (float) (x1 - x0);
            float dy = (float) (y1 - y0);
            float dz = (float) (z1 - z0);
            float length = Mth.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0) {
                dx /= length;
                dy /= length;
                dz /= length;
            }
            buffer.addVertex(pose, (float) x0, (float) y0, (float) z0).setColor(color).setNormal(pose, dx, dy, dz);
            buffer.addVertex(pose, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, dx, dy, dz);
        });
    }

    /**
     * A model posed from {@code state}, in {@code renderType}, tinted {@code tintedColor}; with {@code uvMapping}, its
     * texture coordinates laid into that sprite of an atlas (as 1.21.1's {@code Material.buffer} does).
     */
    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
            int tintedColor, @Nullable TextureAtlasSprite uvMapping, int outlineColor) {
        VertexConsumer buffer = this.buffers.getBuffer(renderType);
        if (uvMapping != null) {
            buffer = uvMapping.wrap(buffer);
        }
        model.setupAnim(state);
        model.renderToBuffer(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
    }

    public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords, int overlayCoords,
            int outlineColor) {
        this.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, -1, null, outlineColor);
    }

    /** A model in a sprite of an atlas, drawn in the model's own kind of render type for that atlas. */
    public <S> void submitModel(Model<S> model, S state, PoseStack poseStack, int lightCoords, int overlayCoords, int tintedColor,
            Material sprite, SpriteGetter sprites, int outlineColor) {
        this.submitModel(model, state, poseStack, sprite.renderType(model.renderType()), lightCoords, overlayCoords, tintedColor,
                sprites.get(sprite), outlineColor);
    }

    /** A model's cracks, as a block's are laid over it while it is broken. */
    public <S> void submitCrumblingOverlay(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType, int lightCoords,
            int overlayCoords, int tintedColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        VertexConsumer buffer = new SheetedDecalTextureGenerator(
                this.buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(Mth.clamp(crumblingOverlay.progress(), 0, 9))),
                crumblingOverlay.cameraPose(), 1.0F);
        model.setupAnim(state);
        model.renderToBuffer(poseStack, buffer, lightCoords, overlayCoords, tintedColor);
    }

    /** Text, its baseline's left at ({@code x}, {@code y}) in the pose, in {@code color} (ARGB) over {@code backgroundColor}. */
    public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow, Font.DisplayMode displayMode,
            int lightCoords, int color, int backgroundColor, int outlineColor) {
        Minecraft.getInstance().font.drawInBatch(string, x, y, color, dropShadow, poseStack.last().pose(), this.buffers, displayMode,
                backgroundColor, lightCoords);
    }

    /** A block as the world draws it where it stands, as a falling block is drawn: tinted and lit there. */
    public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState block, int outlineColor) {
        block.submit(poseStack, this.buffers);
    }
}
