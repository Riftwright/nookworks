package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import dev.nookworks.client.PhotoTextures;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.PictureFrames;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.nookworks.port.render.EntityRenderState;
import net.minecraft.client.renderer.RenderType;
import dev.nookworks.port.render.RenderTypes;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Draws a poster as a single printed face lying flat against the wall. Unlike a painting there is no board: no
 * back, no edges, and the sheet sits {@link PosterEntity#DEPTH} off the blocks, so from the side it reads as
 * paper. The artwork is its own texture rather than an atlas sprite, which keeps datapack posters to "drop in a
 * PNG" and lets them use transparency.
 * <p>
 * Client-only: it is reached solely from {@link ClientRenderers}, which the client event bus registers.
 */
public class PosterRenderer extends EntityRenderer<PosterEntity, PosterRenderer.State> {
    /** A picture in a glowing frame, and the frame: full light, as a glowing frame's item is drawn. */
    private static final int FULL_BRIGHT = 0xF000F0;

    public PosterRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static class State extends EntityRenderState {
        public @Nullable ResourceLocation texture;
        public Direction direction = Direction.SOUTH;
        public int width = 1;
        public int height = 1;
        public int[] lightPerBlock = new int[1];
        public boolean photo;
        /** The block texture a frame's rim is cut from; null, no frame. */
        public @Nullable ResourceLocation rim;
        /** In a glowing frame: the picture shows in the dark. */
        public boolean glow;
        /** The rim is the brass moulding, not a plain rim ({@link FrameRims}). */
        public boolean moulded;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PosterEntity entity, State state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        PosterVariant variant = entity.variant();
        state.direction = entity.getDirection();
        PhotoPrints.Print print = PhotoPrints.parse(entity.variantId());
        // a photo's picture is made here from what the server sent: blank paper until then, or if it never will be
        state.texture = variant == null ? null : print != null ? PhotoTextures.textureOrBlank(print.photo(), print.size()) : variant.texture();
        state.photo = print != null;
        ItemStack frame = entity.frame();
        PictureFrames.Look look = frame.isEmpty() ? null : PictureFrames.look(frame);
        state.rim = look == null ? null : look.rim();
        state.glow = look != null && look.glow();
        state.moulded = look != null && look.moulded();
        state.width = variant == null ? 1 : variant.width();
        state.height = variant == null ? 1 : variant.height();
        if (state.lightPerBlock.length != state.width * state.height) {
            state.lightPerBlock = new int[state.width * state.height];
        }
        // One light sample per block of the sheet, so a big poster is lit like the wall behind it.
        Level level = entity.level();
        float originX = -state.width / 2.0F;
        float originY = -state.height / 2.0F;
        for (int row = 0; row < state.height; row++) {
            for (int column = 0; column < state.width; column++) {
                float acrossWall = column + originX + 0.5F;
                int x = entity.getBlockX();
                int y = Mth.floor(entity.getY() + row + originY + 0.5F);
                int z = entity.getBlockZ();
                switch (state.direction) {
                    case NORTH -> x = Mth.floor(entity.getX() + acrossWall);
                    case SOUTH -> x = Mth.floor(entity.getX() - acrossWall);
                    case WEST -> z = Mth.floor(entity.getZ() - acrossWall);
                    case EAST -> z = Mth.floor(entity.getZ() + acrossWall);
                    default -> { }
                }
                state.lightPerBlock[column + row * state.width] = LightCoordsUtil.getLightCoords(level, new BlockPos(x, y, z));
            }
        }
        if (state.glow) { // the picture and its frame, as round a painting (FramedPaintingRenderer)
            java.util.Arrays.fill(state.lightPerBlock, FULL_BRIGHT);
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) {
            return;
        }
        poseStack.pushPose();
        dev.nookworks.port.render.Poses.rotate(poseStack, Axis.YP.rotationDegrees(180 - state.direction.get2DDataValue() * 90));
        int width = state.width;
        int height = state.height;
        int[] light = state.lightPerBlock;
        ResourceLocation texture = state.texture;
        boolean photo = state.photo;
        boolean glow = state.glow;
        // A photo is lit as a map in a frame is - by the light where it hangs, not dimmed again for the way the wall faces,
        // as a painting is (to three quarters facing north or south, half facing east or west): a photo's sunny sky
        // came out the grey-blue of evening on an east wall (the user, 2026-10-14: 眠い…ぼんやり).
        RenderType type = photo ? RenderTypes.text(texture) : RenderTypes.entityCutoutZOffset(texture);
        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            float originX = -width / 2.0F;
            float originY = -height / 2.0F;
            float z = -PosterEntity.DEPTH / 2.0F;
            for (int column = 0; column < width; column++) {
                for (int row = 0; row < height; row++) {
                    float x0 = originX + (column + 1);
                    float x1 = originX + column;
                    float y0 = originY + (row + 1);
                    float y1 = originY + row;
                    // The texture is one image across the whole poster, so each block takes its slice of it.
                    float u0 = (float) (width - column) / width;
                    float u1 = (float) (width - (column + 1)) / width;
                    float v0 = (float) (height - row) / height;
                    float v1 = (float) (height - (row + 1)) / height;
                    int lightCoords = glow ? FULL_BRIGHT : light[column + row * width];
                    vertex(pose, buffer, x0, y1, u1, v0, z, lightCoords, photo);
                    vertex(pose, buffer, x1, y1, u0, v0, z, lightCoords, photo);
                    vertex(pose, buffer, x1, y0, u0, v1, z, lightCoords, photo);
                    vertex(pose, buffer, x0, y0, u1, v1, z, lightCoords, photo);
                }
            }
        });
        if (state.rim != null) { // on the wall, round the sheet
            FrameRims.submit(poseStack, collector, state.rim, state.moulded, width, height, light, PosterEntity.DEPTH / 2.0F);
        }
        poseStack.popPose();
        super.submit(state, poseStack, collector, camera);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, float z, int lightCoords,
            boolean photo) {
        if (photo) { // a map's vertices: no overlay, no normal
            buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setLight(lightCoords);
            return;
        }
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(lightCoords).setNormal(pose, 0, 0, -1);
    }
}
