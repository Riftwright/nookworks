package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.DoubleToIntFunction;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.Nookworks;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws a Drive Wheel - its toothed wheel and axle (models/block/drive_wheel, pointing north), turned to face as it is
 * and going round about its line while a motor drives it - and the belts it owns ({@link DriveWheelBlockEntity#owns}):
 * each a loop, taut, round the far half of both wheels' teeth and straight between them, in the wheels' plane. A belt
 * is a thin square cord whose texture - rope's strands, or chain links, flat and edge-on by turns - moves along it as
 * the wheels turn, the way their rims go and as fast; lit, as Thin Rope is, by where it runs. Two belts on one wheel
 * ride to either side of its middle ({@link DriveWheelBlockEntity#lane}).
 */
public class DriveWheelRenderer implements BlockEntityRenderer<DriveWheelBlockEntity, DriveWheelRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> WHEEL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":drive_wheel");
    private static final SpriteId ROPE = Sheets.BLOCKS_MAPPER.apply(Nookworks.id("drive_belt_rope"));
    private static final SpriteId CHAIN = Sheets.BLOCKS_MAPPER.apply(Nookworks.id("drive_belt_chain"));
    /** Half the belt's thickness, in blocks: a hair over half the teeth's, so where it lies on them they are under it. */
    private static final float HALF = 0.8F / 16;
    /** Straight pieces in the half of a wheel a belt goes round. */
    private static final int WRAP_STEPS = 8;
    /** The texture's middle columns the cord shows across it (it is drawn four pixels wide in the middle of 16). */
    private static final float U0 = 6 / 16F, U1 = 10 / 16F;
    private static final int[] NO_TINTS = new int[0];

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(WHEEL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/drive_wheel")));
    }

    /**
     * A belt to draw: from this wheel's middle to the far one's, relative to this block; where it rides along each
     * wheel's axle; what it is; and the light at each block along it, from this end.
     */
    record Run(Vec3 to, double here, double there, BeltKind kind, int[] lights) {}

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        Direction spin = Direction.SOUTH;
        float degrees;
        float travel;
        final List<Run> belts = new ArrayList<>();
    }

    /** A wheel's belts' light along them, looked up again once a second (or when its belts change), as rope's is. */
    private record Lit(long at, List<BlockPos> others, List<int[]> lights) {}

    private static final int LIGHT_EVERY = 20;
    private final Map<DriveWheelBlockEntity, Lit> lit = new WeakHashMap<>();

    private final SpriteGetter sprites;

    public DriveWheelRenderer(BlockEntityRendererProvider.Context context) {
        this.sprites = context.sprites();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DriveWheelBlockEntity wheel, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(wheel, state, partialTicks, camera, breakProgress);
        state.facing = wheel.getBlockState().getOptionalValue(DriveWheelBlock.FACING).orElse(Direction.NORTH);
        state.spin = wheel.spinAxis();
        state.degrees = wheel.shaftDegrees(partialTicks);
        state.travel = wheel.beltTravel(partialTicks);
        state.belts.clear();
        Level level = wheel.getLevel();
        if (level == null) {
            return;
        }
        List<BlockPos> others = new ArrayList<>();
        for (DriveWheelBlockEntity.Belt belt : wheel.belts()) {
            if (wheel.owns(belt.other())) {
                others.add(belt.other());
            }
        }
        Lit known = this.lit.get(wheel);
        if (known == null || level.getGameTime() - known.at() >= LIGHT_EVERY || !known.others().equals(others)) {
            List<int[]> lights = new ArrayList<>();
            for (BlockPos other : others) {
                lights.add(lightsAlong(level, wheel.getBlockPos(), other));
            }
            known = new Lit(level.getGameTime(), others, lights);
            this.lit.put(wheel, known);
        }
        for (int i = 0; i < others.size(); i++) {
            BlockPos other = others.get(i);
            var d = other.subtract(wheel.getBlockPos());
            double here = wheel.lane(other);
            double there = here;
            if (level.getBlockEntity(other) instanceof DriveWheelBlockEntity far) {
                there = far.lane(wheel.getBlockPos());
                // a wheel with this belt alone takes the other end's lane, so the belt runs square across both
                if (wheel.belts().size() < DriveWheelBlockEntity.MOST_BELTS) {
                    here = there;
                } else if (far.belts().size() < DriveWheelBlockEntity.MOST_BELTS) {
                    there = here;
                }
            }
            BeltKind kind = wheel.belts().stream().filter(b -> b.other().equals(other)).findFirst().map(DriveWheelBlockEntity.Belt::kind)
                    .orElse(BeltKind.ROPE);
            state.belts.add(new Run(new Vec3(d.getX() + 0.5, d.getY() + 0.5, d.getZ() + 0.5), here, there, kind,
                    known.lights().get(i)));
        }
    }

    /** The light at each whole block along the line from {@code from}'s middle to {@code to}'s, both ends included. */
    private static int[] lightsAlong(Level level, BlockPos from, BlockPos to) {
        Vec3 a = Vec3.atCenterOf(from);
        Vec3 b = Vec3.atCenterOf(to);
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b)));
        int[] lights = new int[steps + 1];
        for (int i = 0; i <= steps; i++) {
            lights[i] = RopeDrawing.light(level, a.lerp(b, i / (double) steps));
        }
        return lights;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(WHEEL);
        if (model != null) {
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            pose.rotate(MotorRenderer.spin(state.spin, state.degrees));
            pose.rotate(MotorRenderer.aim(state.facing));
            pose.translate(-0.5F, -0.5F, -0.5F);
            collector.submitMultiLayerBlockModel(pose, List.of(model), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (state.belts.isEmpty()) {
            return;
        }
        Vec3 axis = new Vec3(state.spin.getStepX(), state.spin.getStepY(), state.spin.getStepZ());
        // how far a point on the rim has gone, in blocks: what the belt has moved by
        double moved = state.travel;
        for (Run run : state.belts) {
            Vec3 middle = new Vec3(0.5, 0.5, 0.5);
            List<Vec3> loop = loop(middle.add(axis.scale(run.here())), run.to().add(axis.scale(run.there())), axis);
            if (loop.isEmpty()) {
                continue;
            }
            TextureAtlasSprite sprite = this.sprites.get(run.kind() == BeltKind.CHAIN ? CHAIN : ROPE);
            int[] lights = run.lights();
            int last = lights.length - 1;
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(sprite.atlasLocation()), (p, buffer) -> {
                double at = 0; // how far along the loop, so its texture runs on unbroken from piece to piece
                for (int i = 0; i + 1 < loop.size(); i++) {
                    Vec3 along = loop.get(i + 1).subtract(loop.get(i));
                    double length = along.length();
                    if (length < 1.0E-6) {
                        continue;
                    }
                    Vec3 dir = along.scale(1 / length);
                    // the pieces: round this wheel, along to the far one, round it, and back
                    DoubleToIntFunction light;
                    if (i < WRAP_STEPS) {
                        light = t -> lights[0];
                    } else if (i == WRAP_STEPS) {
                        light = t -> lights[Math.min(last, (int) Math.round(t))];
                    } else if (i <= 2 * WRAP_STEPS) {
                        light = t -> lights[last];
                    } else {
                        double whole = length;
                        light = t -> lights[Math.max(0, Math.min(last, (int) Math.round(whole - t)))];
                    }
                    cord(p, buffer, sprite, loop.get(i), dir, length, axis.cross(dir), axis, at - moved, light);
                    at += length;
                }
            });
        }
    }

    /**
     * A belt's path from the wheel at {@code a} to the one at {@code b} and back, the way it runs as they turn about
     * {@code axis}: round the far half of {@code a}, along to {@code b}, round its far half and back. (Turning about
     * +axis, a rim's point at axis x dir, dir toward the other wheel, goes away from it, round the wheel's far side.)
     */
    static List<Vec3> loop(Vec3 a, Vec3 b, Vec3 axis) {
        Vec3 along = b.subtract(a);
        double length = along.length();
        List<Vec3> points = new ArrayList<>();
        if (length < 1.0E-6) {
            return points;
        }
        Vec3 dir = along.scale(1 / length);
        wrap(points, a, dir, axis);
        wrap(points, b, dir.reverse(), axis);
        points.add(points.get(0));
        return points;
    }

    /** The half of the wheel at {@code middle} away from {@code toward}, from axis x toward round to its opposite. */
    private static void wrap(List<Vec3> points, Vec3 middle, Vec3 toward, Vec3 axis) {
        Vec3 across = axis.cross(toward);
        for (int k = 0; k <= WRAP_STEPS; k++) {
            double angle = Math.PI / 2 + Math.PI * k / WRAP_STEPS;
            points.add(middle.add(toward.scale(Math.cos(angle) * Belts.RADIUS)).add(across.scale(Math.sin(angle) * Belts.RADIUS)));
        }
    }

    /**
     * A square cord from {@code start} along {@code dir} for {@code length} blocks, its texture laid a block a tile and
     * moved on by {@code shift} blocks; its faces square to {@code across} and to {@code axis}, the latter half a link
     * on from the former, so a chain shows its links flat and edge-on by turns.
     */
    private static void cord(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Vec3 start, Vec3 dir, double length,
            Vec3 across, Vec3 axis, double shift, DoubleToIntFunction light) {
        Vec3[][] faces = {{across, axis}, {across.reverse(), axis.reverse()}, {axis, across.reverse()}, {axis.reverse(), across}};
        for (int f = 0; f < faces.length; f++) {
            Vec3 out = faces[f][0];
            Vec3 side = faces[f][1];
            double offset = shift + (f >= 2 ? 0.25 : 0.0); // the faces across the axle half a link on
            // cut where the tiles end, so each piece of the texture is laid whole, 0 to 1
            double t = 0;
            while (t < length - 1.0E-6) {
                // the tile t lies in; a hair short of a seam counts as past it (else one texel row is laid a block long)
                double tile = Math.floor(t + offset + 1.0E-6);
                double end = Math.min(length, tile + 1 - offset);
                float v0 = (float) Math.max(0, t + offset - tile);
                float v1 = (float) (end + offset - tile);
                Vec3 a = start.add(dir.scale(t)).add(out.scale(HALF));
                Vec3 b = start.add(dir.scale(end)).add(out.scale(HALF));
                Vec3 s = side.scale(HALF);
                int lit = light.applyAsInt((t + end) / 2);
                vertex(pose, buffer, sprite, a.subtract(s), U0, v0, out, lit);
                vertex(pose, buffer, sprite, b.subtract(s), U0, Math.min(1, v1), out, lit);
                vertex(pose, buffer, sprite, b.add(s), U1, Math.min(1, v1), out, lit);
                vertex(pose, buffer, sprite, a.add(s), U1, v0, out, lit);
                t = end;
            }
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Vec3 at, float u, float v, Vec3 normal,
            int light) {
        buffer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(-1).setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, (float) normal.x, (float) normal.y, (float) normal.z);
    }

    @Override
    public AABB getRenderBoundingBox(DriveWheelBlockEntity wheel) {
        return wheel.renderBounds();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** Seen from as far as a block is: the wheel is all drawn here (its block model has nothing in it), belts too. */
    @Override
    public int getViewDistance() {
        return 128;
    }
}
