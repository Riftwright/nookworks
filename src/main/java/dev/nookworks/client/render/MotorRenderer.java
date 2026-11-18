package dev.nookworks.client.render;

import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.machine.DriveSource;
import dev.nookworks.machine.MotorBlockEntity;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import dev.nookworks.port.render.BlockEntityRenderState;
import dev.nookworks.port.render.ModelFeatureRenderer;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws a Motor's shaft going round while it runs (the user, 2026-10-10: as Create's shafts do): the shaft's own model
 * (models/block/motor_shaft, pointing north), turned to the way the motor faces and about its own line by
 * {@link MotorBlockEntity#shaftDegrees}, about the positive end of its line as every drive is ({@link DriveSource}). The
 * machine it drives - or the Drive Wheel on its shaft - turns by the same angle about the same line (CrankRenderer,
 * DriveWheelRenderer), so they go round as one.
 */
public class MotorRenderer implements BlockEntityRenderer<MotorBlockEntity, MotorRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> SHAFT = new StandaloneModelKey<>(() -> Nookworks.MODID + ":motor_shaft");
    private static final int[] NO_TINTS = new int[0];

    public static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        event.register(SHAFT, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/motor_shaft")));
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float degrees;
        /** Another mod's shaft against its axle (the port's Create): the join drawn there, at that shaft's angle. */
        boolean joined;
    }

    public MotorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MotorBlockEntity motor, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(motor, state, partialTicks, camera, breakProgress);
        state.facing = motor.getBlockState().getOptionalValue(MotorBlock.FACING).orElse(Direction.NORTH);
        state.degrees = motor.shaftDegrees(partialTicks);
        state.joined = false;
        if (motor.getLevel() != null) {
            // a Create shaft on its axle (the port's Create part): the axle turns as that shaft is drawn, as one piece
            DriveSource front = dev.nookworks.machine.ForeignDrives.source(motor.getLevel(), motor.getBlockPos().relative(state.facing), state.facing);
            if (front != null) {
                state.joined = true;
                if (front.turning()) {
                    state.degrees = front.shaftDegrees(partialTicks);
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart shaft = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager()).getStandaloneModel(SHAFT);
        if (shaft == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        dev.nookworks.port.render.Poses.rotate(pose, spin(DriveSource.positive(state.facing.getAxis()), state.degrees)); // as everything on its line turns
        dev.nookworks.port.render.Poses.rotate(pose, aim(state.facing));
        pose.translate(-0.5F, -0.5F, -0.5F);
        if (!state.joined || !ForeignJoins.draw("motor", pose, collector, state.lightCoords)) {
            collector.submitMultiLayerBlockModel(pose, List.of(shaft), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }

    /** A turn of {@code degrees} about the line a shaft pointing {@code way} runs along, through the block's middle. */
    public static Quaternionf spin(Direction way, float degrees) {
        return new Quaternionf().rotateAxis((float) Math.toRadians(degrees), way.getStepX(), way.getStepY(), way.getStepZ());
    }

    /** What turns a model pointing north to point {@code way}, as the motor's blockstate turns its housing. */
    public static Quaternionf aim(Direction way) {
        return switch (way) {
            case UP -> Axis.XP.rotationDegrees(90.0F);
            case DOWN -> Axis.XP.rotationDegrees(-90.0F);
            default -> Axis.YP.rotationDegrees(-(way.toYRot() + 180.0F) % 360.0F);
        };
    }
}
