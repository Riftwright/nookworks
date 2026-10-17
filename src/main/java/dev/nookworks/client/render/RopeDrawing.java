package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Thin Rope that is not a block: a cord a pixel thick along a line of points, as a square tube whose four sides
 * carry the rope's twist a step apart, so it reads as twisted from any side ({@code textures/entity/thin_rope_span.png},
 * a face to a column, one turn of the twist down it). Each point is lit as the air it passes through.
 */
final class RopeDrawing {
    static final Identifier TEXTURE = Nookworks.id("textures/entity/thin_rope_span.png");
    /** The cord's thickness: the Thin Rope block's, a pixel. */
    static final float WIDTH = 1.0F / 16;
    /** One turn of the twist, which is one run down the texture. */
    static final double TWIST = 3.0 / 16;
    /** Corners of the square section, as (side, up), a half-width out; face k runs from corner k to k + 1. */
    private static final float[][] CORNERS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    private static final float[][] NORMALS = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};

    private RopeDrawing() {}

    /** How many pieces a cord this long is drawn in: one a turn of its twist. */
    static int steps(double length) {
        return Math.max(2, (int) Math.ceil(length / TWIST));
    }

    /** The packed light at {@code point} (world coordinates). */
    static int light(Level level, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        return LightCoordsUtil.pack(level.getBrightness(LightLayer.BLOCK, at), level.getBrightness(LightLayer.SKY, at));
    }

    /** The cord through {@code points} (relative to the pose), each lit by {@code lights}. */
    static void cord(PoseStack.Pose pose, VertexConsumer buffer, Vec3[] points, int[] lights) {
        int n = points.length;
        Vec3[] side = new Vec3[n];
        Vec3[] up = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 along = points[Math.min(n - 1, i + 1)].subtract(points[Math.max(0, i - 1)]).normalize();
            Vec3 s = along.cross(new Vec3(0, 1, 0));
            s = s.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : s.normalize();
            side[i] = s;
            up[i] = s.cross(along).normalize();
        }
        float r = WIDTH / 2;
        for (int i = 0; i < n - 1; i++) {
            float v1 = (float) Math.min(1.0, points[i + 1].distanceTo(points[i]) / TWIST);
            for (int k = 0; k < 4; k++) {
                int next = (k + 1) % 4;
                float u0 = k / 4.0F;
                float u1 = (k + 1) / 4.0F;
                Vec3 normal = side[i].scale(NORMALS[k][0]).add(up[i].scale(NORMALS[k][1]));
                vertex(pose, buffer, corner(points[i], side[i], up[i], k, r), u0, 0, lights[i], normal);
                vertex(pose, buffer, corner(points[i], side[i], up[i], next, r), u1, 0, lights[i], normal);
                vertex(pose, buffer, corner(points[i + 1], side[i + 1], up[i + 1], next, r), u1, v1, lights[i + 1], normal);
                vertex(pose, buffer, corner(points[i + 1], side[i + 1], up[i + 1], k, r), u0, v1, lights[i + 1], normal);
            }
        }
    }

    /** A little box of cord, {@code half} out each way from the pose's origin: a knot. */
    static void knot(PoseStack.Pose pose, VertexConsumer buffer, float half, float height, int light) {
        box(pose, buffer, -half, -height / 2, -half, half, height / 2, half, light, -1);
    }

    /** A knot round something not square: {@code halfX}, {@code halfY} and {@code halfZ} out from the pose's origin. */
    static void knot(PoseStack.Pose pose, VertexConsumer buffer, float halfX, float halfY, float halfZ, int light) {
        box(pose, buffer, -halfX, -halfY, -halfZ, halfX, halfY, halfZ, light, -1);
    }

    /**
     * A tie: a few turns of cord round a rope that runs along the pose's x axis (or, {@code upright}, its y axis) at its
     * origin, {@code length} along it and a little thicker than the rope, in {@code colour} (ARGB, multiplying the cord's
     * own texture) - what a thing hanging from the rope is tied on by, so its string does not seem to grow out of it.
     */
    static void tie(PoseStack.Pose pose, VertexConsumer buffer, float length, float thickness, boolean upright, int light, int colour) {
        float a = length / 2;
        float r = thickness / 2;
        if (upright) {
            box(pose, buffer, -r, 0, -r, r, length, r, light, colour);
        } else {
            box(pose, buffer, -a, -r, -r, a, r, r, light, colour);
        }
    }

    /** Turns of cord round more than the rope - a banner's bar, the rope over it - from one corner to the other. */
    static void lashing(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
            int light, int colour) {
        box(pose, buffer, x0, y0, z0, x1, y1, z1, light, colour);
    }

    private static void box(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
            int light, int colour) {
        // the four sides round, and the two ends
        quad(pose, buffer, light, colour, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0);
        quad(pose, buffer, light, colour, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(pose, buffer, light, colour, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(pose, buffer, light, colour, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1);
        quad(pose, buffer, light, colour, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(pose, buffer, light, colour, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, int light, int colour, float nx, float ny, float nz,
            float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        Vec3 normal = new Vec3(nx, ny, nz);
        vertex(pose, buffer, new Vec3(ax, ay, az), 0, 0, light, normal, colour);
        vertex(pose, buffer, new Vec3(bx, by, bz), 1, 0, light, normal, colour);
        vertex(pose, buffer, new Vec3(cx, cy, cz), 1, 1, light, normal, colour);
        vertex(pose, buffer, new Vec3(dx, dy, dz), 0, 1, light, normal, colour);
    }

    private static Vec3 corner(Vec3 at, Vec3 side, Vec3 up, int k, float r) {
        return at.add(side.scale(CORNERS[k][0] * r)).add(up.scale(CORNERS[k][1] * r));
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float u, float v, int light, Vec3 normal) {
        vertex(pose, buffer, at, u, v, light, normal, -1);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float u, float v, int light, Vec3 normal, int colour) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
