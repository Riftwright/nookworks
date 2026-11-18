package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.anchor.WarpAnchorBlock;
import dev.nookworks.anchor.WarpAnchorBlockEntity;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockStateModelPart;
import dev.nookworks.port.render.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import dev.nookworks.port.render.BlockEntityRenderState;
import dev.nookworks.port.render.ModelFeatureRenderer;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import dev.nookworks.port.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws a Warp Anchor: the cube in ender pearl colours floating over the block's middle, rising and sinking a little
 * and turning slowly, inside a ring tilted like Saturn's that turns the other way about the upright. Its glow is its
 * own while on (drawn full bright). Switched off it is still: the cube sunk low and dull grey-green, the ring level and
 * at rest - a glance tells which.
 */
public class WarpAnchorRenderer implements BlockEntityRenderer<WarpAnchorBlockEntity, WarpAnchorRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> CORE = key("warp_anchor_core");
    public static final StandaloneModelKey<BlockStateModelPart> CORE_OFF = key("warp_anchor_core_off");
    public static final StandaloneModelKey<BlockStateModelPart> RING = key("warp_anchor_ring");
    private static final int[] NO_TINTS = new int[0];
    /** Ticks for the cube's bob and for one turn of the cube and of the ring. */
    private static final float BOB = 60;
    private static final float CUBE_TURN = 240;
    private static final float RING_TURN = 160;
    /** The ring's tilt, and how far the cube rises and sinks, in blocks. */
    private static final float TILT = 24;
    private static final float BOB_HEIGHT = 0.04F;

    private static StandaloneModelKey<BlockStateModelPart> key(String name) {
        return new StandaloneModelKey<>(() -> Nookworks.MODID + ":" + name);
    }

    public static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        for (var entry : List.of(CORE, CORE_OFF, RING)) {
            String name = entry == CORE ? "warp_anchor_core" : entry == CORE_OFF ? "warp_anchor_core_off" : "warp_anchor_ring";
            event.register(entry, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/" + name)));
        }
    }

    public static final class State extends BlockEntityRenderState {
        boolean on;
        float time;
        /** A little offset of its own, so two anchors side by side do not bob as one. */
        float phase;
    }

    public WarpAnchorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WarpAnchorBlockEntity anchor, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(anchor, state, partialTicks, camera, breakProgress);
        state.on = anchor.getBlockState().getValue(WarpAnchorBlock.ON);
        state.time = AnimationTime.cycle(anchor.getLevel(), partialTicks, 2400);
        state.phase = Math.floorMod(anchor.getBlockPos().asLong() * 31, 97);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        var models = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager());
        BlockStateModelPart core = models.getStandaloneModel(state.on ? CORE : CORE_OFF);
        BlockStateModelPart ring = models.getStandaloneModel(RING);
        float t = state.time + state.phase;
        int light = state.on ? LightCoordsUtil.FULL_BRIGHT : state.lightCoords;
        // the cube: bobbing and turning while on, sunk a little and square while off
        pose.pushPose();
        pose.translate(0.5F, state.on ? 0.5F + BOB_HEIGHT * Mth.sin(t / BOB * Mth.TWO_PI) : 0.42F, 0.5F);
        if (state.on) {
            dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(t / CUBE_TURN * 360));
            dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(12));
            dev.nookworks.port.render.Poses.rotate(pose, Axis.ZP.rotationDegrees(12));
        }
        pose.translate(-0.5F, -0.5F, -0.5F);
        draw(core, light, pose, collector);
        pose.popPose();
        // the ring: tilted, turning the other way about the upright; level and still while off
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        if (state.on) {
            dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-t / RING_TURN * 360));
            dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(TILT));
        }
        pose.translate(-0.5F, -0.5F, -0.5F);
        draw(ring, state.on ? LightCoordsUtil.max(state.lightCoords, LightCoordsUtil.pack(10, 0)) : state.lightCoords, pose, collector);
        pose.popPose();
    }

    private static void draw(@Nullable BlockStateModelPart part, int light, PoseStack pose, SubmitNodeCollector collector) {
        if (part != null) {
            collector.submitMultiLayerBlockModel(pose, List.of(part), false, NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
