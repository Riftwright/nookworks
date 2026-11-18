package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import dev.nookworks.Nookworks;
import net.minecraft.client.renderer.GameRenderer;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import dev.nookworks.port.render.SubmitCustomGeometryEvent;

/**
 * The outline a tool in the hand draws over the blocks a click would work on - the Building Wands' (the user,
 * 2026-11-07), drawn as nookTechnology draws a Conveyor Belt about to be laid (its BeltPreview), so the two read as one
 * family (the user: 「色とかは雰囲気を揃えたら統一感あっていい」). Public for add-ons, so their tools' frames match it
 * (nookTechnology's Hand Truck, 2026-11-10): a faint skin over the outer faces of the blocks, and an edge a brighter
 * colour - brass corner brackets at a box's corners, as a workshop squares up a job before cutting, or a rim round a
 * spread's outline on the face clicked. Only the outer faces are skinned, so a layer of thirty-two reads as one slab,
 * not a pile of panes. Client only; drawn in {@code RenderTypes.debugQuads()} from a {@link SubmitCustomGeometryEvent}.
 * <p>
 * Under an Iris shader pack no program of the pack's draws that render type (Iris: "Missing program
 * minecraft:pipeline/debug_quads in override list"), and the frame was not there at all (r22 CXM-003, Complementary):
 * then it is kept, and drawn over the pack's finished picture the game's own way - tested against the world's depth,
 * so a wall before it hides it - as a Standing Mirror's glass is ({@link MirrorReflections}).
 * <p>
 * PORT: 1.21.1 has no render pipelines to register: over a shader pack the kept frames are drawn after the world with
 * 1.21.1's position-and-colour shader, tested against the world's depth; the view's bob is in 1.21.1's projection already.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class WorkOutline {
    /** Lifted off the blocks' faces, so the skin is not lost in them. */
    private static final float LIFT = 0.006F;
    /** A bracket's arm, or the rim: how thick; and how long a bracket's arm is at most. */
    private static final float BAR = 0.05F;
    private static final float REACH = 0.25F;

    /** A frame to draw over the shader pack's picture this frame: where (from the camera), and how. */
    private record Later(Matrix4f at, SubmitNodeCollector.CustomGeometryRenderer drawing) {}

    private static final List<Later> overShaders = new ArrayList<>();

    private WorkOutline() {}

    /** A skin colour, faint and see-through, and its edge's (ARGB). */
    public record Style(int fill, int edge) {
        /**
         * BeltPreview's dark steel and brass, the steel a little fainter, as it skins the sides of a volume here, not one
         * flat top: what a click would build or take.
         */
        public static final Style WORK = new Style(ARGB.color(0x34, 0x3A3428), ARGB.color(0xE0, 0xD9B56A));
        /** A faint copper: a surface a click would replace. */
        public static final Style REPLACE = new Style(ARGB.color(0x50, 0x8A4A28), ARGB.color(0xE0, 0xD08A5A));
        /** BeltPreview's muted brick red: what will not do (a box too big to fill, a surface Replace refuses). */
        public static final Style REFUSED = new Style(ARGB.color(0x58, 0x4A1E18), ARGB.color(0xE0, 0xD06A52));
    }

    /**
     * The box of blocks from {@code a} to {@code b} (both inside, any two opposite corners): its outer faces skinned,
     * brackets at its eight corners. One block: {@code box(event, pos, pos, style)}.
     */
    public static void box(SubmitCustomGeometryEvent event, BlockPos a, BlockPos b, Style style) {
        BlockPos min = BlockPos.min(a, b);
        BlockPos size = BlockPos.max(a, b).subtract(min).offset(1, 1, 1);
        submit(event, min, (at, out) -> drawBox(at, out, size.getX(), size.getY(), size.getZ(), style));
    }

    /**
     * The blocks in {@code cells}, as the walk from a click reached them (a diamond, a ragged patch): the outer faces of
     * the shape they make skinned, and a rim round its outline on the side {@code face} looks out of - the face clicked.
     */
    public static void cells(SubmitCustomGeometryEvent event, Set<BlockPos> cells, Direction face, Style style) {
        if (cells.isEmpty()) {
            return;
        }
        BlockPos min = null;
        for (BlockPos cell : cells) {
            min = min == null ? cell : BlockPos.min(min, cell);
        }
        BlockPos origin = min;
        submit(event, origin, (at, out) -> {
            skin(at, out, cells, origin, style.fill());
            rim(at, out, cells, origin, face, style.edge());
        });
    }

    /**
     * For one's own geometry: a box {@code sx} by {@code sy} by {@code sz} from the pose's origin, skinned and bracketed
     * as {@link #box} draws one - into a {@code RenderTypes.debugQuads()} consumer.
     */
    public static void drawBox(PoseStack.Pose at, VertexConsumer out, float sx, float sy, float sz, Style style) {
        for (Direction side : Direction.values()) {
            face(at, out, 0, 0, 0, sx, sy, sz, side, style.fill());
        }
        brackets(at, out, sx, sy, sz, style.edge());
    }

    /**
     * Draws {@code drawing} (into a {@code RenderTypes.debugQuads()} consumer: {@link #drawBox}, or one's own quads
     * in position and colour) from {@code origin}, as this class draws its own: in the world, or under an Iris shader
     * pack over the pack's picture. For an add-on's own frame, which drawn into {@code debugQuads} itself is not seen
     * under a shader pack.
     */
    public static void submit(SubmitCustomGeometryEvent event, BlockPos origin, SubmitNodeCollector.CustomGeometryRenderer drawing) {
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        if (IrisHooks.shaderPackInUse()) {
            if (!IrisHooks.shadowPass()) {
                overShaders.add(new Later(new Matrix4f(pose.last().pose()), drawing));
            }
        } else {
            event.getSubmitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), drawing);
        }
        pose.popPose();
    }

    /** The world drawn and the shader pack's picture of it finished: the frames kept for it, over it. */
    @SubscribeEvent
    static void onAfterLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL || overShaders.isEmpty()) {
            return;
        }
        // PORT: at this stage 1.21.1's model-view matrix is the camera's turn alone, handed with the event
        Matrix4f view = new Matrix4f(event.getModelViewMatrix());
        IrisHooks.vanilla(() -> drawOverShaders(view));
        overShaders.clear();
    }

    /** A frame kept with no world drawn after it is not kept for the next. */
    @SubscribeEvent
    static void onFrameEnd(RenderFrameEvent.Post event) {
        overShaders.clear();
    }

    /** The kept frames in 1.21.1's immediate way: one mesh of quads, see-through, both sides, behind what is nearer. */
    private static void drawOverShaders(Matrix4f view) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        PoseStack pose = new PoseStack();
        for (Later later : overShaders) {
            pose.last().pose().set(view).mul(later.at());
            later.drawing().render(pose.last(), builder);
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    /** The skin over the outer faces of the cells: every face with no other cell beside it. */
    private static void skin(PoseStack.Pose at, VertexConsumer out, Set<BlockPos> cells, BlockPos origin, int colour) {
        for (BlockPos cell : cells) {
            BlockPos p = cell.subtract(origin);
            for (Direction side : Direction.values()) {
                if (!cells.contains(cell.relative(side))) {
                    face(at, out, p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1, side, colour);
                }
            }
        }
    }

    /**
     * One face of the box from (x0, y0, z0) to (x1, y1, z1), lifted off it along its own normal only - not spread
     * sideways, where two squares' faces side by side would overlap in a darker seam - drawn both ways round.
     */
    private static void face(PoseStack.Pose at, VertexConsumer out, float x0, float y0, float z0, float x1, float y1, float z1,
            Direction side, int colour) {
        switch (side) {
            case DOWN -> y0 -= LIFT;
            case UP -> y1 += LIFT;
            case NORTH -> z0 -= LIFT;
            case SOUTH -> z1 += LIFT;
            case WEST -> x0 -= LIFT;
            case EAST -> x1 += LIFT;
        }
        quad(at, out, corners(side, x0, y0, z0, x1, y1, z1), colour);
    }

    private static float[][] corners(Direction side, float x0, float y0, float z0, float x1, float y1, float z1) {
        return switch (side) {
            case DOWN -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
            case NORTH -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
            case SOUTH -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case WEST -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            case EAST -> new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
        };
    }

    private static void quad(PoseStack.Pose at, VertexConsumer out, float[][] q, int colour) {
        for (int i = 0; i < 4; i++) {
            out.addVertex(at, q[i][0], q[i][1], q[i][2]).setColor(colour);
        }
        for (int i = 3; i >= 0; i--) {
            out.addVertex(at, q[i][0], q[i][1], q[i][2]).setColor(colour);
        }
    }

    /**
     * A thin rim round the outline of the cells' faces toward {@code face}: along every edge of such a face where no cell
     * lies beyond it, lying on the face and a bar wide inward.
     */
    private static void rim(PoseStack.Pose at, VertexConsumer out, Set<BlockPos> cells, BlockPos origin, Direction face, int colour) {
        int w = face.getAxis().ordinal();
        for (BlockPos cell : cells) {
            BlockPos p = cell.subtract(origin);
            float[] lo = {p.getX(), p.getY(), p.getZ()};
            for (Direction side : Direction.values()) {
                if (side.getAxis() == face.getAxis() || cells.contains(cell.relative(side))) {
                    continue;
                }
                int d = side.getAxis().ordinal();
                float[] a = new float[3];
                float[] b = new float[3];
                for (int i = 0; i < 3; i++) {
                    a[i] = lo[i] - LIFT;
                    b[i] = lo[i] + 1 + LIFT;
                }
                // on the face clicked: from a bar's depth in to just over it
                float plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? lo[w] + 1 : lo[w];
                float sign = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1 : -1;
                a[w] = Math.min(plane - sign * BAR, plane + sign * LIFT * 2);
                b[w] = Math.max(plane - sign * BAR, plane + sign * LIFT * 2);
                // at the edge toward the side with no cell, a bar wide
                if (side.getAxisDirection() == Direction.AxisDirection.POSITIVE) {
                    a[d] = lo[d] + 1 - BAR;
                    b[d] = lo[d] + 1 + LIFT * 2;
                } else {
                    a[d] = lo[d] - LIFT * 2;
                    b[d] = lo[d] + BAR;
                }
                bar(at, out, a[0], a[1], a[2], b[0], b[1], b[2], colour);
            }
        }
    }

    /**
     * Corner brackets on the box from the origin to (sx, sy, sz): at each of its eight corners three short bars along
     * its edges, each a little square in section so it reads from any side.
     */
    private static void brackets(PoseStack.Pose at, VertexConsumer out, float sizeX, float sizeY, float sizeZ, int colour) {
        float sx = sizeX + 2 * LIFT;
        float sy = sizeY + 2 * LIFT;
        float sz = sizeZ + 2 * LIFT;
        for (int cx = 0; cx < 2; cx++) {
            for (int cy = 0; cy < 2; cy++) {
                for (int cz = 0; cz < 2; cz++) {
                    float x = cx * sx - LIFT;
                    float y = cy * sy - LIFT;
                    float z = cz * sz - LIFT;
                    float dx = cx == 0 ? 1 : -1;
                    float dy = cy == 0 ? 1 : -1;
                    float dz = cz == 0 ? 1 : -1;
                    float ix = BAR * dx;
                    float iy = BAR * dy;
                    float iz = BAR * dz;
                    bar(at, out, x, y, z, x + dx * Math.min(REACH, sx / 2), y + iy, z + iz, colour);
                    bar(at, out, x, y, z, x + ix, y + dy * Math.min(REACH, sy / 2), z + iz, colour);
                    bar(at, out, x, y, z, x + ix, y + iy, z + dz * Math.min(REACH, sz / 2), colour);
                }
            }
        }
    }

    /** A solid bar between two corners, any way round. */
    private static void bar(PoseStack.Pose at, VertexConsumer out, float ax, float ay, float az, float bx, float by, float bz, int colour) {
        float x0 = Math.min(ax, bx);
        float y0 = Math.min(ay, by);
        float z0 = Math.min(az, bz);
        float x1 = Math.max(ax, bx);
        float y1 = Math.max(ay, by);
        float z1 = Math.max(az, bz);
        for (Direction side : Direction.values()) {
            quad(at, out, corners(side, x0, y0, z0, x1, y1, z1), colour);
        }
    }
}
