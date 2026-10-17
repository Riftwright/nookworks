package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws a label: a thin card of paper, and printed on it its letters in dark ink, or the item's picture as the
 * inventory shows it - a block as
 * its little three-sided cube, an item as its sprite. The picture is pressed flat onto the card but keeps the way its
 * faces are turned, so the light falls on a printed cube's top as it would on the icon's.
 * <p>
 * Client-only: it is reached solely from {@link ClientRenderers}, which the client event bus registers.
 */
public class LabelRenderer extends EntityRenderer<LabelEntity, LabelRenderer.State> {
    private static final Identifier TEXTURE = Nookworks.id("textures/entity/label.png");
    /** How big the picture is printed: an icon's 16 pixels across this much of a block. */
    private static final float ICON = 5 / 16F;
    /** How thin the pressed picture is, as a share of its size. */
    private static final float FLAT = 0.01F;

    /** Letters are printed within this much of the card across, and no taller than this. */
    private static final float TEXT_W = 7 / 16F;
    private static final float TEXT_H = 3.5F / 16;
    private static final int INK = 0xFF26221E;

    private final ItemModelResolver itemModelResolver;
    private final Font font;

    public LabelRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.font = context.getFont();
    }

    public static class State extends EntityRenderState {
        public Direction direction = Direction.SOUTH;
        public String text = "";
        public final ItemStackRenderState item = new ItemStackRenderState();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(LabelEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        state.direction = entity.getDirection();
        state.text = entity.text();
        this.itemModelResolver.updateForNonLiving(state.item, entity.shows(), ItemDisplayContext.GUI, entity);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        // Local -z is out of the block, +y is up, and the viewer's right is local -x.
        poseStack.rotate(Axis.YP.rotationDegrees(180 - state.direction.get2DDataValue() * 90));
        int light = state.lightCoords;
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(TEXTURE), (pose, buffer) -> card(pose, buffer, light));
        if (!state.text.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -LabelEntity.DEPTH / 2 - 0.001F);
            poseStack.rotate(Axis.YP.rotationDegrees(180)); // the text's front faces out
            int width = this.font.width(state.text);
            // the font's letters stand 7 of its pixels high (of a 9-pixel line)
            float scale = Math.min(TEXT_W / width, TEXT_H / 7);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, -width / 2F, -3.5F, Component.literal(state.text).getVisualOrderText(),
                    false, Font.DisplayMode.POLYGON_OFFSET, light, INK, 0, 0);
            poseStack.popPose();
        } else if (!state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.translate(0, 0, -LabelEntity.DEPTH / 2 - ICON * FLAT / 2 - 0.0005F);
            poseStack.rotate(Axis.YP.rotationDegrees(180)); // the icon's front, +z, faces out
            poseStack.scale(ICON, ICON, ICON);
            // Pressed flat on the pose alone: the normals are left as they were, so the shading stays the icon's.
            poseStack.last().pose().scale(1, 1, FLAT);
            state.item.submit(poseStack, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
            poseStack.popPose();
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    /** The card: its face (the texture's top-left 8x6) and its four thin edges; the back lies on the block. */
    private static void card(PoseStack.Pose pose, VertexConsumer buffer, int light) {
        float w = LabelEntity.WIDTH / 2;
        float h = LabelEntity.HEIGHT / 2;
        float front = -LabelEntity.DEPTH / 2;
        float back = LabelEntity.DEPTH / 2;
        float faceU = 8 / 16F;
        float faceV = 6 / 16F;
        // face, seen from the front: left-bottom, right-bottom, right-top, left-top
        quad(pose, buffer, light, 0, 0, -1,
                w, -h, front, 0, faceV,
                -w, -h, front, faceU, faceV,
                -w, h, front, faceU, 0,
                w, h, front, 0, 0);
        float edgeV0 = 6 / 16F;
        float edgeV1 = 7 / 16F;
        quad(pose, buffer, light, 0, 1, 0, // top
                w, h, front, 0, edgeV0,
                -w, h, front, faceU, edgeV0,
                -w, h, back, faceU, edgeV1,
                w, h, back, 0, edgeV1);
        quad(pose, buffer, light, 0, -1, 0, // bottom
                -w, -h, front, faceU, edgeV0,
                w, -h, front, 0, edgeV0,
                w, -h, back, 0, edgeV1,
                -w, -h, back, faceU, edgeV1);
        float edgeU0 = 8 / 16F;
        float edgeU1 = 9 / 16F;
        quad(pose, buffer, light, 1, 0, 0, // the viewer's left
                w, -h, back, edgeU1, faceV,
                w, -h, front, edgeU0, faceV,
                w, h, front, edgeU0, 0,
                w, h, back, edgeU1, 0);
        quad(pose, buffer, light, -1, 0, 0, // the viewer's right
                -w, -h, front, edgeU0, faceV,
                -w, -h, back, edgeU1, faceV,
                -w, h, back, edgeU1, 0,
                -w, h, front, edgeU0, 0);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
            float x0, float y0, float z0, float u0, float v0, float x1, float y1, float z1, float u1, float v1,
            float x2, float y2, float z2, float u2, float v2, float x3, float y3, float z3, float u3, float v3) {
        vertex(pose, buffer, light, nx, ny, nz, x0, y0, z0, u0, v0);
        vertex(pose, buffer, light, nx, ny, nz, x1, y1, z1, u1, v1);
        vertex(pose, buffer, light, nx, ny, nz, x2, y2, z2, u2, v2);
        vertex(pose, buffer, light, nx, ny, nz, x3, y3, z3, u3, v3);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, int light, float nx, float ny, float nz,
            float x, float y, float z, float u, float v) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
