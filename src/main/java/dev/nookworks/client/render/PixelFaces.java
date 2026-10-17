package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Pixels drawn one at a time on the face of something two blocks high - a door's leaf, a mirror's glass - each a
 * sixteenth of a block square and of one colour, lit by itself (as a portal is, it glows at night). Used for what
 * changes as it is watched: the world beyond a Dock-a-Daemon Door, an If Mirror's dice and view.
 */
final class PixelFaces {
    /** Plain white, coloured by each pixel's vertices. */
    static final Identifier LIGHT = Nookworks.id("textures/misc/daemon_door_light.png");
    /** Unlit, opaque, fogged as the world is. */
    static final RenderType TYPE = RenderTypes.beaconBeam(LIGHT, false);

    private PixelFaces() {}

    /**
     * One pixel of a face looking {@code normal}-wards, {@code out} blocks from the block's middle: column {@code x} from
     * the left as one looks at it (0 to 15 across the block), row {@code y} from the top of the two blocks (0 to 15 the
     * upper one).
     */
    static void pixel(PoseStack.Pose pose, VertexConsumer buffer, Direction normal, float out, int x, int y, int colour) {
        Direction right = normal.getCounterClockWise(); // the right hand of whoever looks at this face
        float cx = 0.5F + normal.getStepX() * out;
        float cz = 0.5F + normal.getStepZ() * out;
        float a = -0.5F + x / 16.0F;
        float b = a + 1 / 16.0F;
        float top = 2 - y / 16.0F;
        float bottom = top - 1 / 16.0F;
        float ax = cx + right.getStepX() * a;
        float az = cz + right.getStepZ() * a;
        float bx = cx + right.getStepX() * b;
        float bz = cz + right.getStepZ() * b;
        // anticlockwise as seen from the front: bottom left, bottom right, top right, top left
        vertex(pose, buffer, ax, bottom, az, colour, normal);
        vertex(pose, buffer, bx, bottom, bz, colour, normal);
        vertex(pose, buffer, bx, top, bz, colour, normal);
        vertex(pose, buffer, ax, top, az, colour, normal);
    }

    /**
     * Columns {@code x0} to {@code x1} and rows {@code y0} to {@code y1} of the face (as {@link #pixel} counts them, ends
     * exclusive) showing {@code u0}-{@code u1}, {@code v0}-{@code v1} of a texture, at full light, tinted {@code colour}.
     */
    static void textured(PoseStack.Pose pose, VertexConsumer buffer, Direction normal, float out, float x0, float x1, float y0, float y1,
            float u0, float u1, float v0, float v1, int colour) {
        float[] c = corners(normal, out, x0, x1, y0, y1);
        // anticlockwise as seen from the front: bottom left, bottom right, top right, top left
        textured(pose, buffer, c[0], c[2], c[1], u0, v1, colour, normal);
        textured(pose, buffer, c[3], c[2], c[4], u1, v1, colour, normal);
        textured(pose, buffer, c[3], c[5], c[4], u1, v0, colour, normal);
        textured(pose, buffer, c[0], c[5], c[1], u0, v0, colour, normal);
    }

    /** The same rectangle as bare corners: for a render type that draws from where it is on the screen, as the End portal's. */
    static void bare(PoseStack.Pose pose, VertexConsumer buffer, Direction normal, float out, float x0, float x1, float y0, float y1) {
        float[] c = corners(normal, out, x0, x1, y0, y1);
        buffer.addVertex(pose, c[0], c[2], c[1]);
        buffer.addVertex(pose, c[3], c[2], c[4]);
        buffer.addVertex(pose, c[3], c[5], c[4]);
        buffer.addVertex(pose, c[0], c[5], c[1]);
    }

    /** A rectangle's corners: {its left edge's x, z, its bottom's y, its right edge's x, z, its top's y}. */
    private static float[] corners(Direction normal, float out, float x0, float x1, float y0, float y1) {
        Direction right = normal.getCounterClockWise();
        float cx = 0.5F + normal.getStepX() * out;
        float cz = 0.5F + normal.getStepZ() * out;
        float a = -0.5F + x0 / 16.0F;
        float b = -0.5F + x1 / 16.0F;
        return new float[] {cx + right.getStepX() * a, cz + right.getStepZ() * a, 2 - y1 / 16.0F,
                cx + right.getStepX() * b, cz + right.getStepZ() * b, 2 - y0 / 16.0F};
    }

    private static void textured(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int colour,
            Direction normal) {
        buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(pose, normal.getStepX(), 0, normal.getStepZ());
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, int colour, Direction normal) {
        buffer.addVertex(pose, x, y, z).setColor(colour).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0)
                .setNormal(pose, normal.getStepX(), 0, normal.getStepZ());
    }
}
