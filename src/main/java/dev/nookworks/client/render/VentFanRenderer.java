package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.VentFanBlock;
import dev.nookworks.ventilation.VentFanBlockEntity;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws a Ventilation Fan's blades turning behind its guard: their own JSON model ({@code block/ventilation_fan_blades},
 * facing north), turned to the fan's facing and spun about its axis, speeding up as it starts and slowing as it stops.
 */
public class VentFanRenderer implements BlockEntityRenderer<VentFanBlockEntity, VentFanRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> BLADES = new StandaloneModelKey<>(() -> Nookworks.MODID + ":ventilation_fan_blades");
    private static final int[] NO_TINTS = new int[0];

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(BLADES, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/ventilation_fan_blades")));
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
        float angle;
    }

    public VentFanRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(VentFanBlockEntity fan, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(fan, state, partialTicks, camera, breakProgress);
        var block = fan.getBlockState();
        state.facing = block.hasProperty(VentFanBlock.FACING) ? block.getValue(VentFanBlock.FACING) : Direction.NORTH;
        boolean on = block.hasProperty(VentFanBlock.ON) && block.getValue(VentFanBlock.ON);
        state.angle = fan.getLevel() == null ? 0 : fan.bladeAngle(AnimationTime.cycle(fan.getLevel(), partialTicks, 24_000L), on);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart blades = Minecraft.getInstance().getModelManager().getStandaloneModel(BLADES);
        if (blades == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-((state.facing.toYRot() + 180) % 360))); // as the blockstate turns the model
        pose.rotate(Axis.ZP.rotationDegrees(state.angle));
        pose.translate(-0.5F, -0.5F, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(blades), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
