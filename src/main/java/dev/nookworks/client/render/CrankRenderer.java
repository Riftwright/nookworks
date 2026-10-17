package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.machine.CrankMotion;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.DriveSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws a hand-worked machine's handle going round as it is worked ({@link dev.nookworks.machine.CrankMotion}), and,
 * when a drive turns it - a Motor's shaft, or a Drive Wheel's ({@link DriveSource}) - what meets the drive turning with
 * it as one, and the handle with them, round and round as long as it runs (the user, 2026-10-10: as Create's shafts
 * and machines turn together). Where the drive stands on the handle's side, the handle is off. With the drive standing
 * still, the handle is worked by hand as before, going on from where the drive left it.
 * <p>
 * A machine driven from a side has an axle out to it ({@code <machine>_axle}, drawn pointing north and turned to the
 * drive's side). The Hand Quern, driven from above or below by its spindle, has a coupling on the spindle's top
 * ({@code _axle_up}) or stands on a plate over the drive ({@code _axle_down}, still: what turns is the spindle up
 * through the stones), and from above it turns without its bar ({@code _crank_driven}), where the motor sits. The parts are the machine's own JSON models, turned here instead of
 * standing still in its blockstate.
 */
public class CrankRenderer<T extends BlockEntity & Crankable> implements BlockEntityRenderer<T, CrankRenderer.State> {
    /**
     * One machine's moving parts, and where its crank turns: about an axis through {@code pivot} (in pixels, the
     * model facing north), vertical or running east-west. A vertical one is driven from above or below.
     */
    public record Parts(String machine, Vec3 pivot, boolean vertical, StandaloneModelKey<BlockStateModelPart> crank,
            @Nullable StandaloneModelKey<BlockStateModelPart> axle, @Nullable StandaloneModelKey<BlockStateModelPart> crankDriven,
            @Nullable StandaloneModelKey<BlockStateModelPart> axleUp, @Nullable StandaloneModelKey<BlockStateModelPart> axleDown) {
        static Parts sideways(String machine, Vec3 pivot, boolean vertical) {
            return new Parts(machine, pivot, vertical, key(machine + "_crank"), key(machine + "_axle"), null, null, null);
        }

        static Parts upright(String machine, Vec3 pivot) {
            return new Parts(machine, pivot, true, key(machine + "_crank"), null, key(machine + "_crank_driven"),
                    key(machine + "_axle_up"), key(machine + "_axle_down"));
        }

        private static StandaloneModelKey<BlockStateModelPart> key(String name) {
            return new StandaloneModelKey<>(() -> Nookworks.MODID + ":" + name);
        }

        List<StandaloneModelKey<BlockStateModelPart>> all() {
            List<StandaloneModelKey<BlockStateModelPart>> keys = new ArrayList<>();
            for (var key : Arrays.asList(this.crank, this.axle, this.crankDriven, this.axleUp, this.axleDown)) {
                if (key != null) {
                    keys.add(key);
                }
            }
            return keys;
        }
    }

    /** The screw press: its bar across the top of the screw goes round the screw; driven from a side by a gear collar. */
    public static final Parts OIL_PRESS = Parts.sideways("oil_press", new Vec3(8, 0, 8), true);
    /** The polisher: its handle on the east side goes round the hub at 6 px up. */
    public static final Parts RICE_POLISHER = Parts.sideways("rice_polisher", new Vec3(0, 6, 8), false);
    /** The quern: its upper stone, spindle and bar go round in the frame; driven from above or below by the spindle. */
    public static final Parts HAND_QUERN = Parts.upright("hand_quern", new Vec3(8, 0, 8));
    /** The noodle maker: its handle on the east side turns the rollers, whose shaft runs through at 7 px up. */
    public static final Parts NOODLE_MAKER = Parts.sideways("noodle_maker", new Vec3(0, 7, 8), false);

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        for (Parts parts : List.of(OIL_PRESS, RICE_POLISHER, HAND_QUERN, NOODLE_MAKER)) {
            for (var key : parts.all()) {
                String name = key.getName().substring(Nookworks.MODID.length() + 1);
                event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/" + name)));
            }
        }
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        @Nullable Direction drive;
        float degrees;
        /** What drives it, if anything is there: the line it turns about, and how far round it is. */
        @Nullable Direction spin;
        float shaft;
        /** How far round the handle is, and whether about the drive's own spindle (the quern's, from above or below). */
        float handle;
        boolean onSpindle;
    }

    private static final int[] NO_TINTS = new int[0];

    private final Parts parts;

    public CrankRenderer(BlockEntityRendererProvider.Context context, Parts parts) {
        this.parts = parts;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T machine, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(machine, state, partialTicks, camera, breakProgress);
        var block = machine.getBlockState();
        state.facing = block.getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        state.drive = block.getOptionalValue(Drive.PROPERTY).map(Drive::side).orElse(null);
        state.degrees = machine.getLevel() == null ? 0 : machine.motion().degrees(machine.getLevel().getGameTime(), partialTicks);
        if (state.drive != null && !machine.drivenFrom(state.drive)) {
            state.drive = null; // a side it is not driven from, left in an older world's block (a quern's side motor)
        }
        state.spin = null;
        boolean driven = false;
        if (state.drive != null && machine.getLevel() != null
                && machine.getLevel().getBlockEntity(machine.getBlockPos().relative(state.drive)) instanceof DriveSource source) {
            state.spin = source.spinAxis();
            state.shaft = source.shaftDegrees(partialTicks);
            driven = source.turning();
        }
        state.onSpindle = this.parts.vertical() && state.spin != null && state.spin.getAxis().isVertical();
        // driven, as far round as the drive; by hand, a quarter each time it is worked, on from where a drive left it
        // (on the spindle the drive turns it the other way about, so the hand's count goes the other way too)
        CrankMotion motion = machine.motion();
        float byHand = state.onSpindle ? motion.offset() - state.degrees : state.degrees + motion.offset();
        if (driven) {
            state.handle = motion.driven(state.shaft, byHand);
            motion.follow(state.onSpindle ? state.handle + state.degrees : state.handle - state.degrees);
        } else {
            state.handle = byHand;
            motion.released();
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var models = Minecraft.getInstance().getModelManager();
        Direction drive = state.drive;
        if (drive != null) {
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            if (state.spin != null && drive != Direction.DOWN) {
                pose.rotate(MotorRenderer.spin(state.spin, state.shaft)); // the drive's axle: as far round as the drive
            }
            if (drive.getAxis().isHorizontal()) {
                // the axle points north in its model; turned to the drive's side, it goes round about the drive's line
                pose.rotate(Axis.YP.rotationDegrees(-yRot(drive)));
            }
            pose.translate(-0.5F, -0.5F, -0.5F);
            var axle = drive == Direction.UP ? this.parts.axleUp() : drive == Direction.DOWN ? this.parts.axleDown() : this.parts.axle();
            if (axle != null) {
                this.draw(models.getStandaloneModel(axle), state, pose, collector);
            }
            pose.popPose();
        }
        // the handle, on the machine's east side (seen facing north) or on top: off where the drive stands in its way
        if (drive != null && !this.parts.vertical() && drive == state.facing.getClockWise()) {
            return;
        }
        var crank = drive == Direction.UP && this.parts.crankDriven() != null ? this.parts.crankDriven() : this.parts.crank();
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-yRot(state.facing))); // turned to face as the machine is
        pose.translate(-0.5F, -0.5F, -0.5F);
        Vec3 p = this.parts.pivot().scale(1 / 16.0);
        pose.translate(p.x, p.y, p.z);
        if (state.onSpindle) {
            pose.rotate(MotorRenderer.spin(state.spin, state.handle)); // on the drive's own spindle: as it goes round
        } else {
            // clockwise, seen from above or from the handle's side
            pose.rotate(this.parts.vertical() ? Axis.YP.rotationDegrees(-state.handle) : Axis.XP.rotationDegrees(-state.handle));
        }
        pose.translate(-p.x, -p.y, -p.z);
        this.draw(models.getStandaloneModel(crank), state, pose, collector);
        pose.popPose();
    }

    private void draw(@Nullable BlockStateModelPart part, State state, PoseStack pose, SubmitNodeCollector collector) {
        if (part != null) {
            collector.submitMultiLayerBlockModel(pose, List.of(part), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
    }

    /** The blockstate's "y" for a model facing {@code side} (north 0, east 90, south 180, west 270). */
    private static float yRot(Direction side) {
        return (side.toYRot() + 180) % 360;
    }
}
