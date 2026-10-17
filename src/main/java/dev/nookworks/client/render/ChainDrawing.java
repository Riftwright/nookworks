package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Thin Chain: small iron links one through the next, turned flat and edge on by turns along the line, as a chain
 * hangs. Each link is two crossed quads - the link face on (a ring, its hole see-through) and the link edge on (a bar as
 * thick as its wire) - so from any side it shows a ring, then a bar, then a ring ({@code textures/entity/thin_chain.png},
 * tools/textures/gen_thin_chain.py). Its links are a little wider than the Thin Rope's cord is thick, of wire half as
 * thick: as thin a chain as still reads as one. Drawn double-sided (vanilla's entity cutout culls no faces), which lights
 * a quad's back with its normal turned round: each quad's normal is its front's, as its corners wind, so whichever side
 * is seen is lit as the side it is - a link lying flat bright from above, not dark.
 */
final class ChainDrawing {
    static final Identifier TEXTURE = Nookworks.id("textures/entity/thin_chain.png");
    /** A link, outside: 3.5 pixels long, 2 across. */
    static final double LENGTH = 3.5 / 16;
    static final double WIDTH = 2.0 / 16;
    /** Its wire, as thick as a link seen edge on. */
    static final double WIRE = 0.5 / 16;
    /** How far apart links' middles are: a link's inside, through which the next one passes. */
    static final double PITCH = LENGTH - 2 * WIRE;
    /** The texture, 8 x 8: the link face on in its top four rows, seven across; edge on in row 5. */
    private static final float RING_U1 = 7 / 8F;
    private static final float RING_V1 = 4 / 8F;
    private static final float BAR_V0 = 5 / 8F;
    private static final float BAR_V1 = 6 / 8F;

    /** A link: its middle, the way it runs, its two crosswise ways, whether it lies flat (its face up), its light. */
    record Link(Vec3 middle, Vec3 along, Vec3 side, Vec3 up, boolean flat, int light) {}

    private ChainDrawing() {}

    /**
     * The links of a chain along the line through {@code points}, from end to end, each lit as the point it is nearest:
     * as many as fit at {@link #PITCH} apart (a little closer or further, so they come out even), turned by turns.
     */
    static List<Link> along(Vec3[] points, int[] lights) {
        int n = points.length;
        double[] at = new double[n];
        for (int i = 1; i < n; i++) {
            at[i] = at[i - 1] + points[i].distanceTo(points[i - 1]);
        }
        double total = at[n - 1];
        int count = Math.max(1, (int) Math.round(total / PITCH));
        double pitch = total / count;
        List<Link> links = new ArrayList<>(count);
        int segment = 0;
        for (int i = 0; i < count; i++) {
            double s = (i + 0.5) * pitch;
            while (segment < n - 2 && at[segment + 1] < s) {
                segment++;
            }
            Vec3 p0 = points[segment];
            Vec3 p1 = points[segment + 1];
            double length = at[segment + 1] - at[segment];
            double f = length > 1.0E-9 ? (s - at[segment]) / length : 0;
            Vec3 along = p1.subtract(p0);
            along = along.lengthSqr() < 1.0E-12 ? new Vec3(1, 0, 0) : along.normalize();
            Vec3 side = along.cross(new Vec3(0, 1, 0));
            side = side.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : side.normalize();
            Vec3 up = side.cross(along).normalize();
            int light = lights[f < 0.5 ? segment : segment + 1];
            links.add(new Link(p0.lerp(p1, f), along, side, up, i % 2 == 0, light));
        }
        return links;
    }

    /**
     * {@code count} links wound round what stands along the pose's y axis at its origin, on a ring {@code rx} out along x
     * and {@code rz} along z: a chain wrapped round a post.
     */
    static List<Link> wrap(double rx, double rz, int count, int light) {
        List<Link> links = new ArrayList<>(count);
        Vec3 up = new Vec3(0, 1, 0);
        for (int i = 0; i < count; i++) {
            double a = 2 * Math.PI * (i + 0.5) / count;
            Vec3 middle = new Vec3(rx * Math.cos(a), 0, rz * Math.sin(a));
            Vec3 along = new Vec3(-rx * Math.sin(a), 0, rz * Math.cos(a)).normalize();
            Vec3 out = along.cross(up).normalize();
            links.add(new Link(middle, along, out, up, i % 2 == 0, light));
        }
        return links;
    }

    static void draw(PoseStack.Pose pose, VertexConsumer buffer, List<Link> links) {
        for (Link link : links) {
            link(pose, buffer, link);
        }
    }

    /** A link: face on across its width, and edge on across its wire. */
    static void link(PoseStack.Pose pose, VertexConsumer buffer, Link link) {
        Vec3 half = link.along().scale(LENGTH / 2);
        Vec3 face = link.flat() ? link.side() : link.up();
        Vec3 edge = link.flat() ? link.up() : link.side();
        quad(pose, buffer, link.middle(), half, face.scale(WIDTH / 2), 0, 0, RING_U1, RING_V1, link.light());
        quad(pose, buffer, link.middle(), half, edge.scale(WIRE / 2), 0, BAR_V0, RING_U1, BAR_V1, link.light());
    }

    /** A quad {@code half} each way along and {@code across} each way across {@code middle}, its normal its front's. */
    private static void quad(PoseStack.Pose pose, VertexConsumer buffer, Vec3 middle, Vec3 half, Vec3 across,
            float u0, float v0, float u1, float v1, int light) {
        Vec3 normal = half.cross(across).normalize(); // the corners below wind anticlockwise seen from this side
        vertex(pose, buffer, middle.subtract(half).subtract(across), u0, v1, light, normal);
        vertex(pose, buffer, middle.add(half).subtract(across), u1, v1, light, normal);
        vertex(pose, buffer, middle.add(half).add(across), u1, v0, light, normal);
        vertex(pose, buffer, middle.subtract(half).add(across), u0, v0, light, normal);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, Vec3 at, float u, float v, int light, Vec3 normal) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
