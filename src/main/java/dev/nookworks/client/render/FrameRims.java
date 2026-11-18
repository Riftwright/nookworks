package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.Sheets;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * The rim of a frame round a picture on a wall ({@code poster.PictureFrames}): a poster's ({@link PosterRenderer}) and a
 * vanilla painting's ({@link FramedPaintingRenderer}). Drawn in the picture's own space as both renderers turn it: the
 * picture {@code width} x {@code height} blocks round the origin, x along it, y up, out from the wall -z; the rim stands
 * on the plane {@code base} (the wall under a poster, a painting's face), inside the picture's edge, lit block by block
 * from {@code light} (column + row x width, from the bottom left as both renderers keep it).
 */
final class FrameRims {
    private FrameRims() {}

    /** Draws the rim cut from the block texture {@code rim}: the brass moulding if {@code moulded}, else a plain rim. */
    static void submit(PoseStack poseStack, SubmitNodeCollector collector, ResourceLocation rim, boolean moulded, int width, int height,
            int[] light, float base) {
        TextureAtlasSprite sprite = dev.nookworks.port.render.SpriteGetter.atlases().get(Sheets.BLOCKS_MAPPER.apply(rim));
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(sprite.atlasLocation()), (pose, buffer) -> {
            Out out = new Out(pose, buffer, sprite);
            if (moulded) {
                moulding(out, width, height, light, base);
            } else {
                plain(out, width, height, light, base);
            }
        });
    }

    private record Out(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite) {
        /**
         * A quad with corners {@code a}, {@code b}, {@code c}, {@code d}, its texture the sprite's part from (u0, v0) at a
         * to (u1, v1) at c, in sixteenths of the sprite.
         */
        void quad(float[] a, float[] b, float[] c, float[] d, float u0, float v0, float u1, float v1, float nx, float ny, float nz,
                int light) {
            float su0 = this.sprite.getU(u0 / 16F);
            float su1 = this.sprite.getU(u1 / 16F);
            float sv0 = this.sprite.getV(v0 / 16F);
            float sv1 = this.sprite.getV(v1 / 16F);
            this.vertex(a, su0, sv0, nx, ny, nz, light);
            this.vertex(b, su1, sv0, nx, ny, nz, light);
            this.vertex(c, su1, sv1, nx, ny, nz, light);
            this.vertex(d, su0, sv1, nx, ny, nz, light);
        }

        private void vertex(float[] p, float u, float v, float nx, float ny, float nz, int light) {
            this.buffer.addVertex(this.pose, p[0], p[1], p[2]).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(this.pose, nx, ny, nz);
        }
    }

    /**
     * A plain rim, a sixteenth wide and a sixteenth out from {@code base}, as an item frame's (its map frame's) is: the top
     * and bottom runs the whole width, the sides between them, so no two faces of the rim lie in one plane at a corner; a
     * block of the texture to each block of the rim. The face out, the face towards the picture and the face out to the
     * side are drawn; the face on the base is not.
     */
    private static void plain(Out out, int width, int height, int[] light, float base) {
        float px = 1 / 16F;
        float left = -width / 2.0F;
        float right = width / 2.0F;
        float bottom = -height / 2.0F;
        float top = height / 2.0F;
        float wall = base;
        float face = wall - px;
        for (int column = 0; column < width; column++) {
            float x0 = left + column;
            float x1 = x0 + 1;
            int topLight = light[column + (height - 1) * width];
            int bottomLight = light[column];
            // the top run: its face out from the texture's top row, the face towards the picture under it, the top over it
            out.quad(p(x0, top, face), p(x1, top, face), p(x1, top - px, face), p(x0, top - px, face), 0, 0, 16, 1, 0, 0, -1, topLight);
            out.quad(p(x0, top - px, face), p(x1, top - px, face), p(x1, top - px, wall), p(x0, top - px, wall), 0, 1, 16, 2, 0, -1, 0, topLight);
            out.quad(p(x0, top, wall), p(x1, top, wall), p(x1, top, face), p(x0, top, face), 0, 0, 16, 1, 0, 1, 0, topLight);
            // the bottom run, from the texture's bottom row
            out.quad(p(x0, bottom + px, face), p(x1, bottom + px, face), p(x1, bottom, face), p(x0, bottom, face), 0, 15, 16, 16, 0, 0, -1, bottomLight);
            out.quad(p(x0, bottom + px, wall), p(x1, bottom + px, wall), p(x1, bottom + px, face), p(x0, bottom + px, face), 0, 14, 16, 15, 0, 1, 0, bottomLight);
            out.quad(p(x0, bottom, face), p(x1, bottom, face), p(x1, bottom, wall), p(x0, bottom, wall), 0, 15, 16, 16, 0, -1, 0, bottomLight);
        }
        // the runs' ends, out to the sides
        int topLeft = light[(height - 1) * width];
        int topRight = light[width - 1 + (height - 1) * width];
        out.quad(p(left, top, wall), p(left, top, face), p(left, top - px, face), p(left, top - px, wall), 15, 0, 16, 1, -1, 0, 0, topLeft);
        out.quad(p(right, top, face), p(right, top, wall), p(right, top - px, wall), p(right, top - px, face), 0, 0, 1, 1, 1, 0, 0, topRight);
        out.quad(p(left, bottom + px, wall), p(left, bottom + px, face), p(left, bottom, face), p(left, bottom, wall), 15, 15, 16, 16, -1, 0, 0, light[0]);
        out.quad(p(right, bottom + px, face), p(right, bottom + px, wall), p(right, bottom, wall), p(right, bottom, face), 0, 15, 1, 16, 1, 0, 0, light[width - 1]);
        for (int row = 0; row < height; row++) {
            float y0 = Math.max(bottom + row, bottom + px);
            float y1 = Math.min(bottom + row + 1, top - px);
            // the texture's rows for this part of the block, from its top down
            float v0 = 16 * (bottom + row + 1 - y1);
            float v1 = 16 * (bottom + row + 1 - y0);
            int leftLight = light[row * width];
            int rightLight = light[width - 1 + row * width];
            // the left side: its face out from the texture's first column, the face towards the picture, the face out to the side
            out.quad(p(left, y1, face), p(left + px, y1, face), p(left + px, y0, face), p(left, y0, face), 0, v0, 1, v1, 0, 0, -1, leftLight);
            out.quad(p(left + px, y1, face), p(left + px, y1, wall), p(left + px, y0, wall), p(left + px, y0, face), 1, v0, 2, v1, 1, 0, 0, leftLight);
            out.quad(p(left, y1, wall), p(left, y1, face), p(left, y0, face), p(left, y0, wall), 0, v0, 1, v1, -1, 0, 0, leftLight);
            // the right side, from the last column
            out.quad(p(right - px, y1, face), p(right, y1, face), p(right, y0, face), p(right - px, y0, face), 15, v0, 16, v1, 0, 0, -1, rightLight);
            out.quad(p(right - px, y1, wall), p(right - px, y1, face), p(right - px, y0, face), p(right - px, y0, wall), 14, v0, 15, v1, -1, 0, 0, rightLight);
            out.quad(p(right, y1, face), p(right, y1, wall), p(right, y0, wall), p(right, y0, face), 15, v0, 16, v1, 1, 0, 0, rightLight);
        }
    }

    /**
     * The brass frame's moulding (the user, 2026-11-13: 「真鍮は、もう少し額縁を厚く、存在感ある感じに」): runs two sixteenths
     * wide inside the picture's edge and two out from {@code base} - three and three round a picture three blocks or more
     * on its longer side, as a bigger picture takes a heavier frame - and at each corner a boss a sixteenth more each way,
     * the runs between the bosses. Its texture is laid out for it (tools/textures/gen_frames.py): along a run the
     * texture's u, a block of it to a block; the front from rows 0-2 (as many as the rim is wide), the outer edge the top
     * row (a lit bead, then the fall into shade towards the picture); the sides from rows 4-7; the boss's stud at (0, 8)
     * for a rim two wide, at (4, 8) for three, its sides at (8, 8).
     */
    private static void moulding(Out out, int width, int height, int[] light, float base) {
        int wide = Math.max(width, height) >= 3 ? 3 : 2; // the rim, in sixteenths: wide and deep
        float px = 1 / 16F;
        float rim = wide * px;
        float boss = (wide + 1) * px;
        float left = -width / 2.0F;
        float right = width / 2.0F;
        float bottom = -height / 2.0F;
        float top = height / 2.0F;
        float wall = base;
        for (int column = 0; column < width; column++) {
            float a0 = Math.max(left + column, left + boss);
            float a1 = Math.min(left + column + 1, right - boss);
            if (a1 > a0) {
                float u0 = 16 * (a0 - left - column);
                float u1 = 16 * (a1 - left - column);
                run(out, true, a0, a1, u0, u1, top, top - rim, wall, wall - rim, wide, light[column + (height - 1) * width]);
                run(out, true, a0, a1, u0, u1, bottom, bottom + rim, wall, wall - rim, wide, light[column]);
            }
        }
        for (int row = 0; row < height; row++) {
            float a0 = Math.max(bottom + row, bottom + boss);
            float a1 = Math.min(bottom + row + 1, top - boss);
            if (a1 > a0) {
                float u0 = 16 * (a0 - bottom - row);
                float u1 = 16 * (a1 - bottom - row);
                run(out, false, a0, a1, u0, u1, left, left + rim, wall, wall - rim, wide, light[row * width]);
                run(out, false, a0, a1, u0, u1, right, right - rim, wall, wall - rim, wide, light[width - 1 + row * width]);
            }
        }
        float front = wall - boss;
        int size = wide + 1;
        boss(out, left, left + boss, top - boss, top, wall, front, size, light[(height - 1) * width]);
        boss(out, right - boss, right, top - boss, top, wall, front, size, light[width - 1 + (height - 1) * width]);
        boss(out, left, left + boss, bottom, bottom + boss, wall, front, size, light[0]);
        boss(out, right - boss, right, bottom, bottom + boss, wall, front, size, light[width - 1]);
    }

    /**
     * A run of the moulding from {@code a0} to {@code a1} along x ({@code alongX} false: along y), from its outer edge
     * {@code outer} in to {@code inner}, from the wall out to {@code front}, {@code wide} sixteenths each way: its face,
     * the side towards the picture and the side out.
     */
    private static void run(Out out, boolean alongX, float a0, float a1, float u0, float u1, float outer, float inner, float wall,
            float front, int wide, int light) {
        float n = Math.signum(outer - inner); // out from the picture, along the run's cross axis
        float nx = alongX ? 0 : n;
        float ny = alongX ? n : 0;
        out.quad(at(alongX, a0, outer, front), at(alongX, a1, outer, front), at(alongX, a1, inner, front), at(alongX, a0, inner, front),
                u0, 0, u1, wide, 0, 0, -1, light);
        out.quad(at(alongX, a0, inner, front), at(alongX, a1, inner, front), at(alongX, a1, inner, wall), at(alongX, a0, inner, wall),
                u0, 4, u1, 4 + wide, -nx, -ny, 0, light);
        out.quad(at(alongX, a0, outer, front), at(alongX, a1, outer, front), at(alongX, a1, outer, wall), at(alongX, a0, outer, wall),
                u0, 4, u1, 4 + wide, nx, ny, 0, light);
    }

    /** A point of a run: {@code along} on its own axis, {@code across} on the other. */
    private static float[] at(boolean alongX, float along, float across, float z) {
        return alongX ? p(along, across, z) : p(across, along, z);
    }

    /**
     * A corner's boss over x0..x1, y0..y1, from the wall out to {@code front}, {@code size} sixteenths each way: its stud
     * (3x3 or 4x4) and its four sides.
     */
    private static void boss(Out out, float x0, float x1, float y0, float y1, float wall, float front, int size, int light) {
        float s = size == 3 ? 0 : 4; // the stud's column in the texture
        out.quad(p(x0, y1, front), p(x1, y1, front), p(x1, y0, front), p(x0, y0, front), s, 8, s + size, 8 + size, 0, 0, -1, light);
        out.quad(p(x0, y1, front), p(x1, y1, front), p(x1, y1, wall), p(x0, y1, wall), 8, 8, 8 + size, 8 + size, 0, 1, 0, light);
        out.quad(p(x0, y0, front), p(x1, y0, front), p(x1, y0, wall), p(x0, y0, wall), 8, 8, 8 + size, 8 + size, 0, -1, 0, light);
        out.quad(p(x0, y1, front), p(x0, y0, front), p(x0, y0, wall), p(x0, y1, wall), 8, 8, 8 + size, 8 + size, -1, 0, 0, light);
        out.quad(p(x1, y1, front), p(x1, y0, front), p(x1, y0, wall), p(x1, y1, wall), 8, 8, 8 + size, 8 + size, 1, 0, 0, light);
    }

    private static float[] p(float x, float y, float z) {
        return new float[] {x, y, z};
    }
}
