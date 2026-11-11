package dev.nookworks.client.carry;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import dev.nookworks.carry.CarriedLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rope wound round a tied block - the layer a Tied Load draws over {@link CarriedBlockRendering} (an add-on's cart
 * leaves it off): a band of rope round the block each way, crossing on its top and under it, knotted where they cross
 * on top - where a rope let down onto it is tied. Round a set of blocks (a double chest), a band round each and one the
 * long way round them all. Drawn in the rope of a Drive Wheel's belt (textures/block/drive_belt_rope.png, made by
 * tools/textures/gen_drive.py), the same pose origin as the block: the first part's corner.
 */
public final class RopeWrapRendering {
    private static final SpriteId ROPE = Sheets.BLOCKS_MAPPER.apply(Nookworks.id("drive_belt_rope"));
    /** The cord across the texture: its columns 6-9. */
    private static final float U0 = 6 / 16F;
    private static final float U1 = 10 / 16F;
    private static final float PX = 1 / 16F;
    /** The band: how wide across the face, how thick off it, how far off the block (so no face is drawn on the block's). */
    static final float WIDTH = 2.0F * PX;
    static final float THICK = 1.0F * PX;
    static final float GAP = 0.1F * PX;
    /** The bands one way ride over the others where they cross, this much higher. */
    private static final float OVER = 0.5F * PX;
    /** The knot on top where the bands cross: a square across and how tall over the bands. */
    private static final float KNOT = 3.0F * PX;
    private static final float KNOT_HEIGHT = 1.5F * PX;
    /** The loop out of the knot that a rope let down onto it ties to: its cord, its width and height over the knot. */
    private static final float LOOP_CORD = 0.9F * PX;
    private static final float LOOP_WIDTH = 3.2F * PX;
    private static final float LOOP_HEIGHT = 2.6F * PX;
    /** One run of the texture down the cord, in blocks: its four pixels across stand for the band's width. */
    private static final float TILE = WIDTH * 4;

    private RopeWrapRendering() {}

    /** Draws the wrap round {@code look}'s parts, lit by {@code light}. */
    public static void submit(CarriedLook look, PoseStack pose, SubmitNodeCollector collector, int light) {
        if (look.isEmpty()) {
            return;
        }
        // round what the blocks' outlines fill (a chest is a pixel in from its cube and two lower), not their cubes
        float x0 = Float.MAX_VALUE, y0 = Float.MAX_VALUE, z0 = Float.MAX_VALUE;
        float x1 = -Float.MAX_VALUE, y1 = -Float.MAX_VALUE, z1 = -Float.MAX_VALUE;
        List<BlockPos> columns = new ArrayList<>();
        for (CarriedLook.LookPart part : look.parts()) {
            BlockPos o = part.offset();
            VoxelShape shape = part.state().getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            AABB bounds = shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds();
            x0 = Math.min(x0, o.getX() + (float) bounds.minX);
            y0 = Math.min(y0, o.getY() + (float) bounds.minY);
            z0 = Math.min(z0, o.getZ() + (float) bounds.minZ);
            x1 = Math.max(x1, o.getX() + (float) bounds.maxX);
            y1 = Math.max(y1, o.getY() + (float) bounds.maxY);
            z1 = Math.max(z1, o.getZ() + (float) bounds.maxZ);
            BlockPos column = new BlockPos(o.getX(), 0, o.getZ());
            if (!columns.contains(column)) {
                columns.add(column);
            }
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getAtlasManager().get(ROPE);
        Box box = new Box(x0, y0, z0, x1, y1, z1);
        collector.submitCustomGeometry(pose, RenderTypes.entityCutout(sprite.atlasLocation()), (p, buffer) -> {
            Out out = new Out(p, buffer, sprite, light);
            List<Float> xs = new ArrayList<>();
            List<Float> zs = new ArrayList<>();
            for (BlockPos column : columns) {
                // a band through each block's middle each way (the box's middle where it is narrower than a block)
                float x = Math.max(box.x0 + WIDTH, Math.min(box.x1 - WIDTH, column.getX() + 0.5F));
                float z = Math.max(box.z0 + WIDTH, Math.min(box.z1 - WIDTH, column.getZ() + 0.5F));
                if (!xs.contains(x)) {
                    xs.add(x);
                    band(out, Direction.Axis.X, x, box, 0);
                }
                if (!zs.contains(z)) {
                    zs.add(z);
                    band(out, Direction.Axis.Z, z, box, OVER); // riding over the first where they cross
                }
            }
            // a knot where they cross on top, over each block's middle, and a loop out of it to tie a rope to
            float top = box.y1 + GAP + THICK + OVER;
            for (BlockPos column : columns) {
                float x = Math.max(box.x0 + WIDTH, Math.min(box.x1 - WIDTH, column.getX() + 0.5F));
                float z = Math.max(box.z0 + WIDTH, Math.min(box.z1 - WIDTH, column.getZ() + 0.5F));
                out.box(x - KNOT / 2, top - 0.2F * PX, z - KNOT / 2, x + KNOT / 2, top + KNOT_HEIGHT, z + KNOT / 2, null);
                float base = top + KNOT_HEIGHT - 0.2F * PX;
                float c = LOOP_CORD / 2;
                float w = LOOP_WIDTH / 2;
                out.box(x - w, base, z - c, x - w + LOOP_CORD, base + LOOP_HEIGHT, z + c, Direction.Axis.Y);
                out.box(x + w - LOOP_CORD, base, z - c, x + w, base + LOOP_HEIGHT, z + c, Direction.Axis.Y);
                out.box(x - w, base + LOOP_HEIGHT - LOOP_CORD, z - c, x + w, base + LOOP_HEIGHT, z + c, Direction.Axis.X);
            }
        });
    }

    private record Box(float x0, float y0, float z0, float x1, float y1, float z1) {}

    /**
     * One band round {@code box}, crossing {@code axis} at {@code at}: over the top, down two sides, under the bottom.
     * {@code over} lifts its top and bottom runs off the block that much more.
     */
    private static void band(Out out, Direction.Axis axis, float at, Box box, float over) {
        float a0 = at - WIDTH / 2;
        float a1 = at + WIDTH / 2;
        float top0 = box.y1 + GAP + over;
        float bottom1 = box.y0 - GAP - over;
        if (axis == Direction.Axis.X) {
            // along z: the sides are the north and south faces
            float n = box.z0 - GAP - THICK;
            float s = box.z1 + GAP + THICK;
            out.box(a0, top0, n, a1, top0 + THICK, s, Direction.Axis.Z); // over the top
            out.box(a0, bottom1 - THICK, n, a1, bottom1, s, Direction.Axis.Z); // under the bottom
            out.box(a0, bottom1 - THICK, n, a1, top0 + THICK, n + THICK, Direction.Axis.Y); // down the north face
            out.box(a0, bottom1 - THICK, s - THICK, a1, top0 + THICK, s, Direction.Axis.Y); // and the south
        } else {
            float w = box.x0 - GAP - THICK;
            float e = box.x1 + GAP + THICK;
            out.box(w, top0, a0, e, top0 + THICK, a1, Direction.Axis.X);
            out.box(w, bottom1 - THICK, a0, e, bottom1, a1, Direction.Axis.X);
            out.box(w, bottom1 - THICK, a0, w + THICK, top0 + THICK, a1, Direction.Axis.Y);
            out.box(e - THICK, bottom1 - THICK, a0, e, top0 + THICK, a1, Direction.Axis.Y);
        }
    }

    /** Where the quads go. */
    private record Out(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, int light) {
        /**
         * A box of cord whose length runs along {@code along} (the texture runs down it, a tile at a time), or a knot
         * ({@code along} null: each face a square of the cord's twist).
         */
        void box(float x0, float y0, float z0, float x1, float y1, float z1, Direction.@Nullable Axis along) {
            if (along == null) {
                for (Direction face : Direction.values()) {
                    this.quad(face, x0, y0, z0, x1, y1, z1, null, 1);
                }
                return;
            }
            float start = (float) (along == Direction.Axis.X ? x0 : along == Direction.Axis.Y ? y0 : z0);
            float end = (float) (along == Direction.Axis.X ? x1 : along == Direction.Axis.Y ? y1 : z1);
            // cut into pieces a tile long, so the texture runs on without wrapping within one quad
            for (float from = start; from < end - 1.0E-4F; from += TILE) {
                float to = Math.min(end, from + TILE);
                float v1 = (to - from) / TILE;
                float bx0 = along == Direction.Axis.X ? from : x0, bx1 = along == Direction.Axis.X ? to : x1;
                float by0 = along == Direction.Axis.Y ? from : y0, by1 = along == Direction.Axis.Y ? to : y1;
                float bz0 = along == Direction.Axis.Z ? from : z0, bz1 = along == Direction.Axis.Z ? to : z1;
                for (Direction face : Direction.values()) {
                    if (face.getAxis() == along) {
                        // a piece's ends: only the band's own two ends show, the rest lie inside the next piece
                        boolean bandEnd = face.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? from == start : to >= end - 1.0E-4F;
                        if (!bandEnd) {
                            continue;
                        }
                    }
                    this.quad(face, bx0, by0, bz0, bx1, by1, bz1, along, v1);
                }
            }
        }

        /**
         * The face of the box on side {@code face}, its four corners counter-clockwise seen from outside; v runs along
         * {@code along} (0 to {@code v1}), u across the cord.
         */
        private void quad(Direction face, float x0, float y0, float z0, float x1, float y1, float z1, Direction.@Nullable Axis along, float v1) {
            float[][] c = corners(face, x0, y0, z0, x1, y1, z1);
            float[][] uv = new float[4][];
            for (int k = 0; k < 4; k++) {
                float[] p = c[k];
                float t;
                float s;
                if (along == null) {
                    // a knot: the face's two axes, each across the four pixels
                    float[] axes = inPlane(face, p, x0, y0, z0, x1, y1, z1);
                    s = axes[0];
                    t = axes[1];
                } else {
                    float length = coordinate(along, p);
                    float lo = coordinate(along, new float[] {x0, y0, z0});
                    float hi = coordinate(along, new float[] {x1, y1, z1});
                    t = hi > lo ? (length - lo) / (hi - lo) * v1 : 0;
                    s = across(face, along, p, x0, y0, z0, x1, y1, z1);
                }
                uv[k] = new float[] {U0 + (U1 - U0) * s, t};
            }
            float nx = face.getStepX(), ny = face.getStepY(), nz = face.getStepZ();
            for (int k = 0; k < 4; k++) {
                this.buffer.addVertex(this.pose, c[k][0], c[k][1], c[k][2]).setColor(-1)
                        .setUv(this.sprite.getU(uv[k][0]), this.sprite.getV(uv[k][1])).setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(this.light).setNormal(this.pose, nx, ny, nz);
            }
        }

        private static float coordinate(Direction.Axis axis, float[] p) {
            return axis == Direction.Axis.X ? p[0] : axis == Direction.Axis.Y ? p[1] : p[2];
        }

        /** Where {@code p} lies across the cord on this face, 0 to 1: along the face's other axis. */
        private static float across(Direction face, Direction.Axis along, float[] p, float x0, float y0, float z0, float x1, float y1, float z1) {
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis != along && axis != face.getAxis()) {
                    float lo = coordinate(axis, new float[] {x0, y0, z0});
                    float hi = coordinate(axis, new float[] {x1, y1, z1});
                    return hi > lo ? (coordinate(axis, p) - lo) / (hi - lo) : 0;
                }
            }
            return 0;
        }

        private static float[] inPlane(Direction face, float[] p, float x0, float y0, float z0, float x1, float y1, float z1) {
            float[] out = new float[2];
            int i = 0;
            for (Direction.Axis axis : Direction.Axis.values()) {
                if (axis != face.getAxis()) {
                    float lo = coordinate(axis, new float[] {x0, y0, z0});
                    float hi = coordinate(axis, new float[] {x1, y1, z1});
                    out[i++] = hi > lo ? (coordinate(axis, p) - lo) / (hi - lo) : 0;
                }
            }
            return out;
        }

        /** The four corners of a box's face, counter-clockwise from outside. */
        private static float[][] corners(Direction face, float x0, float y0, float z0, float x1, float y1, float z1) {
            return switch (face) {
                case DOWN -> new float[][] {{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case NORTH -> new float[][] {{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
                case SOUTH -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                case WEST -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
                case EAST -> new float[][] {{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
            };
        }
    }
}
