package dev.nookworks.client.render;

import java.util.Optional;

import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonDoorBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the Dock-a-Daemon Door, a sliding door: its leaf hung from a rail in front of the wall, sliding off the doorway
 * towards its hinge side ({@link DaemonDoorBlock#slideSide}) over a few ticks as it opens, back as it shuts; its zinc pull
 * bar standing off the leaf; and behind the leaf, in the doorway, the gate it is ({@link DaemonGates}) - through the
 * leaf's glass slits while it is shut, the whole doorway once it is open. The gate shows only on its front, only set
 * into a wall, and only while the door leads somewhere for the player looking. The block's own model is empty: all of
 * it is here, from entity/daemon_door.png (laid out by gen_daemon_door.py).
 */
public class DaemonDoorRenderer implements BlockEntityRenderer<DaemonDoorBlockEntity, DaemonDoorRenderer.State> {
    private static final Identifier TEXTURE = Nookworks.id("textures/entity/daemon_door.png");
    private static final float TEX = 64.0F;
    /** How long the leaf takes to slide, in ticks. */
    private static final float SLIDE_TICKS = 6.0F;
    /** How thick the leaf is, standing out from the wall; how far the rail does. */
    private static final float LEAF = 2.0F;
    private static final float RAIL = 3.0F;
    /** Where the gate is drawn: a pixel back from the wall's face, in the doorway. */
    private static final float GATE_PLANE = 0.5F - 1 / 16.0F;

    // where each part is in the texture: x, y, w, h (gen_daemon_door.LAYOUT)
    private static final float[] LEAF_FRONT = {0, 0, 16, 32};
    private static final float[] LEAF_BACK = {16, 0, 16, 32};
    private static final float[] LEAF_SIDE = {32, 0, 2, 32};
    private static final float[] LEAF_TOP = {34, 0, 16, 2};
    private static final float[] LEAF_FOOT = {34, 2, 16, 2};
    private static final float[] RAIL_FRONT = {0, 32, 32, 3};
    private static final float[] RAIL_TOP = {0, 35, 32, 3};
    private static final float[] RAIL_END = {32, 32, 3, 3};
    private static final float[] ZINC_FACE = {48, 32, 1, 12};
    private static final float[] ZINC_SIDE = {49, 32, 1, 12};
    private static final float[] ZINC_END = {50, 32, 1, 1};

    public DaemonDoorRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final class State extends BlockEntityRenderState {
        DaemonGates.@Nullable Gate gate;
        Direction facing = Direction.NORTH;
        /** Which way it slides, as its front's viewer sees it: to their right, or their left. */
        boolean slidesRight;
        /** How far open, 0 to 1, as it slides. */
        float openness;
        float time;
        /** The camera, from the lower half's corner. */
        Vec3 eye = Vec3.ZERO;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DaemonDoorBlockEntity door, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(door, state, partialTicks, camera, breakProgress);
        BlockState block = door.getBlockState();
        Level level = door.getLevel();
        state.gate = null;
        if (level == null || !(block.getBlock() instanceof DoorBlock)) {
            return;
        }
        state.facing = block.getValue(DoorBlock.FACING);
        Direction front = DaemonDoorBlock.front(block);
        // lit as the room in front of it is: the leaf and rail hang there, and the doorway's own block is walled in
        state.lightCoords = LightCoordsUtil.getLightCoords(level, door.getBlockPos().relative(front).above());
        state.slidesRight = DaemonDoorBlock.slideSide(block) == front.getCounterClockWise();
        state.time = AnimationTime.cycle(level, partialTicks, AnimationTime.VIEW_CYCLE);
        state.eye = camera.subtract(Vec3.atLowerCornerOf(door.getBlockPos()));
        // the slide: from when the client last saw it change, eased in and out
        boolean open = block.getValue(DoorBlock.OPEN);
        double now = level.getGameTime() + partialTicks;
        if (door.shownOpen == null) {
            door.shownOpen = open; // first seen: as it is, no slide
            door.slideStart = now - SLIDE_TICKS;
        } else if (door.shownOpen != open) {
            door.shownOpen = open;
            door.slideStart = now;
        }
        float t = Mth.clamp((float) ((now - door.slideStart) / SLIDE_TICKS), 0.0F, 1.0F);
        t = t * t * (3 - 2 * t);
        state.openness = open ? t : 1 - t;
        var player = Minecraft.getInstance().player;
        Optional<ResourceKey<Level>> to = player == null ? Optional.empty() : door.leadsFor(player);
        if (to.isPresent() && DaemonDoorBlock.framed(level, door.getBlockPos(), state.facing)) {
            state.gate = DaemonGates.of(DaemonDestinations.gate(level.dimension(), to.get()));
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        Direction front = state.facing.getOpposite();
        if (state.gate != null && eye(state.eye, front, GATE_PLANE).away() > 0) {
            gate(pose, collector, state.gate, state.time, front);
        }
        Direction right = front.getCounterClockWise(); // the right hand of whoever looks at the front
        float slide = (state.slidesRight ? 16 : -16) * state.openness;
        int light = state.lightCoords;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (p, buffer) -> {
            Geometry g = new Geometry(p, buffer, front, right, light);
            // the leaf; its handle's stile away from the side it slides to
            boolean mirrored = !state.slidesRight;
            g.box(slide, 0, 0, slide + 16, 32, LEAF, LEAF_FRONT, LEAF_BACK, LEAF_SIDE, LEAF_TOP, LEAF_FOOT, mirrored);
            // the pull bar on its two posts, on the front
            float bar = mirrored ? 16 - 2.5F : 1.5F;
            g.box(slide + bar, 10, LEAF + 1, slide + bar + 1, 22, LEAF + 2, ZINC_FACE, ZINC_FACE, ZINC_SIDE, ZINC_END, ZINC_END, false);
            for (float y : new float[] {10.5F, 20.5F}) {
                g.box(slide + bar, y, LEAF, slide + bar + 1, y + 1, LEAF + 1, ZINC_END, ZINC_END, ZINC_END, ZINC_END, ZINC_END, false);
            }
            // the rail, over the doorway and the wall it slides along
            float from = state.slidesRight ? 0 : -16;
            g.box(from, 31, 0, from + 32, 34, RAIL, RAIL_FRONT, RAIL_FRONT, RAIL_END, RAIL_TOP, RAIL_TOP, mirrored);
        });
    }

    /** The gate filling the doorway, a pixel back from the wall's face, facing {@code front}. */
    private static void gate(PoseStack pose, SubmitNodeCollector collector, DaemonGates.Gate gate, float time, Direction front) {
        switch (gate.kind()) {
            case SPRITE -> {
                TextureAtlasSprite sprite = DaemonGates.sprite(gate);
                // opaque, so it is drawn before the leaf and shows through its glass (drawn after it, the leaf's depth hid it)
                collector.submitCustomGeometry(pose, RenderTypes.entitySolid(sprite.atlasLocation()), (p, buffer) -> {
                    for (int half = 0; half < 2; half++) {
                        PixelFaces.textured(p, buffer, front, GATE_PLANE, 0, 16, half * 16, half * 16 + 16, sprite.getU0(), sprite.getU1(),
                                sprite.getV0(), sprite.getV1(), 0xFFFFFFFF);
                    }
                });
            }
            case END -> collector.submitCustomGeometry(pose, RenderTypes.endPortal(), (p, buffer) -> PixelFaces.bare(p, buffer, front, GATE_PLANE, 0, 16, 0, 32));
            case MIRROR, SWIRL -> collector.submitCustomGeometry(pose, PixelFaces.TYPE, (p, buffer) -> {
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 32; y++) {
                        PixelFaces.pixel(p, buffer, front, GATE_PLANE, x, y, DaemonGates.pixel(gate, x, y, time));
                    }
                }
            });
        }
    }

    /**
     * Boxes in the door's own measure: {@code a} across the doorway from its left as its front's viewer sees it, {@code y}
     * up from its foot, {@code o} out from the wall's face - all in pixels, a block being 16 - drawn into a block's
     * space, each face lit as the door's block is and wound to face outwards.
     */
    private record Geometry(PoseStack.Pose pose, VertexConsumer buffer, Direction front, Direction right, int light) {
        void box(float a0, float y0, float o0, float a1, float y1, float o1, float[] frontUv, float[] backUv, float[] sideUv,
                float[] topUv, float[] footUv, boolean mirrored) {
            Direction left = this.right.getOpposite();
            Direction back = this.front.getOpposite();
            // each face: its corners bottom-left, bottom-right, top-right, top-left as its own viewer sees it
            this.face(this.front, frontUv, mirrored, new float[][] {{a0, y0, o1}, {a1, y0, o1}, {a1, y1, o1}, {a0, y1, o1}});
            this.face(back, backUv, !mirrored, new float[][] {{a1, y0, o0}, {a0, y0, o0}, {a0, y1, o0}, {a1, y1, o0}});
            this.face(this.right, sideUv, false, new float[][] {{a1, y0, o1}, {a1, y0, o0}, {a1, y1, o0}, {a1, y1, o1}});
            this.face(left, sideUv, false, new float[][] {{a0, y0, o0}, {a0, y0, o1}, {a0, y1, o1}, {a0, y1, o0}});
            this.face(Direction.UP, topUv, mirrored, new float[][] {{a0, y1, o1}, {a1, y1, o1}, {a1, y1, o0}, {a0, y1, o0}});
            this.face(Direction.DOWN, footUv, mirrored, new float[][] {{a0, y0, o0}, {a1, y0, o0}, {a1, y0, o1}, {a0, y0, o1}});
        }

        private void face(Direction normal, float[] uv, boolean flip, float[][] corners) {
            float u0 = uv[0] / TEX;
            float u1 = (uv[0] + uv[2]) / TEX;
            float v0 = uv[1] / TEX;
            float v1 = (uv[1] + uv[3]) / TEX;
            if (flip) {
                float t = u0;
                u0 = u1;
                u1 = t;
            }
            float[][] uvs = {{u0, v1}, {u1, v1}, {u1, v0}, {u0, v0}};
            Vector3f[] at = new Vector3f[4];
            for (int i = 0; i < 4; i++) {
                at[i] = this.place(corners[i][0], corners[i][1], corners[i][2]);
            }
            // wound to face outwards: anticlockwise as seen from the side the normal points to
            Vector3f across = new Vector3f(at[1]).sub(at[0]);
            Vector3f up = new Vector3f(at[3]).sub(at[0]);
            boolean outwards = across.cross(up).dot(normal.getStepX(), normal.getStepY(), normal.getStepZ()) >= 0;
            for (int k = 0; k < 4; k++) {
                int i = outwards ? k : 3 - k;
                this.buffer.addVertex(this.pose, at[i].x, at[i].y, at[i].z).setColor(0xFFFFFFFF).setUv(uvs[i][0], uvs[i][1])
                        .setOverlay(OverlayTexture.NO_OVERLAY).setLight(this.light)
                        .setNormal(this.pose, normal.getStepX(), normal.getStepY(), normal.getStepZ());
            }
        }

        /** A point in the door's measure as a point in the block's space. */
        private Vector3f place(float a, float y, float o) {
            float x = 0.5F + (this.right.getStepX() * (a - 8) + this.front.getStepX() * (8 + o)) / 16.0F;
            float z = 0.5F + (this.right.getStepZ() * (a - 8) + this.front.getStepZ() * (8 + o)) / 16.0F;
            return new Vector3f(x, y / 16.0F, z);
        }
    }

    /**
     * Where the eye at {@code camera} (from the lower half's corner) is against the face looking {@code normal}-wards,
     * {@code out} from the block's middle, in that face's pixels as {@link PixelFaces} lays them out.
     */
    static DaemonView.Eye eye(Vec3 camera, Direction normal, float out) {
        Direction right = normal.getCounterClockWise();
        double x = camera.x - 0.5;
        double z = camera.z - 0.5;
        float across = (float) (x * right.getStepX() + z * right.getStepZ() + 0.5) * 16;
        float down = (float) (2 - camera.y) * 16;
        float away = (float) (x * normal.getStepX() + z * normal.getStepZ() - out) * 16;
        return new DaemonView.Eye(across, down, away);
    }

    /** Both halves, the rail over them, and the wall either side the leaf may slide along. */
    @Override
    public AABB getRenderBoundingBox(DaemonDoorBlockEntity door) {
        return new AABB(door.getBlockPos()).inflate(1.1, 0, 1.1).expandTowards(0, 1.2, 0);
    }

    /** It is the whole door, not a detail on a block: seen as far as a block is, near enough. */
    @Override
    public int getViewDistance() {
        return 128;
    }
}
