package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.experience.ExperiencePipeBlock;
import dev.nookworks.fluid.CopperPipeBlock;
import dev.nookworks.fluid.FluidPipeBlockEntity;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import dev.nookworks.port.render.SimpleUnbakedStandaloneModel;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws the steel flange on each arm of a Copper or Experience Pipe whose side is set to pull, so a pulling side looks
 * different from a joined one. The pulling sides are kept in the block entity (see {@link FluidPipeBlockEntity}), which
 * a blockstate cannot read, so the flange is drawn here: its JSON model (the pipe block's {@code pullFlange}, facing
 * north as the arm's own model does), turned to each pulling side as the blockstate turns that arm. A pipe with no
 * pulling side is passed over before anything is worked out for it.
 */
public class PipePullRenderer<T extends FluidPipeBlockEntity> implements BlockEntityRenderer<T, PipePullRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> COPPER = key(CopperPipeBlock.PULL_FLANGE);
    public static final StandaloneModelKey<BlockStateModelPart> EXPERIENCE = key(ExperiencePipeBlock.PULL_FLANGE);
    private static final int[] NO_TINTS = new int[0];
    private static final Direction[] SIDES = Direction.values();

    private static StandaloneModelKey<BlockStateModelPart> key(ResourceLocation model) {
        return new StandaloneModelKey<>(model::toString);
    }

    public static void registerModels(dev.nookworks.port.render.ModelEvent.RegisterStandalone event) {
        event.register(COPPER, SimpleUnbakedStandaloneModel.simpleModelWrapper(CopperPipeBlock.PULL_FLANGE));
        event.register(EXPERIENCE, SimpleUnbakedStandaloneModel.simpleModelWrapper(ExperiencePipeBlock.PULL_FLANGE));
    }

    public static final class State extends BlockEntityRenderState {
        /** The sides drawn with a flange, a bit for each direction's ordinal. */
        int sides;
    }

    private final StandaloneModelKey<BlockStateModelPart> flange;

    public PipePullRenderer(BlockEntityRendererProvider.Context context, StandaloneModelKey<BlockStateModelPart> flange) {
        this.flange = flange;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    /** Most pipes pull from nowhere: those are not drawn here at all. */
    @Override
    public boolean shouldRender(T pipe, Vec3 camera) {
        return pipe.pullingJoined() != 0 && BlockEntityRenderer.super.shouldRender(pipe, camera);
    }

    @Override
    public void extractRenderState(T pipe, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pipe, state, partialTicks, camera, breakProgress);
        state.sides = pipe.pullingJoined();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart part = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager()).getStandaloneModel(this.flange);
        if (part == null || state.sides == 0) {
            return;
        }
        for (Direction side : SIDES) {
            if ((state.sides & 1 << side.ordinal()) == 0) {
                continue;
            }
            pose.pushPose();
            pose.translate(0.5F, 0.5F, 0.5F);
            turnTo(pose, side);
            pose.translate(-0.5F, -0.5F, -0.5F);
            collector.submitMultiLayerBlockModel(pose, List.of(part), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }

    /**
     * Turns a model facing north to face {@code side}, as the pipe's blockstate turns an arm: "y" 90, 180, 270 for east,
     * south, west and "x" 270, 90 for up, down - vanilla turns a model by y and then x, each the other way round.
     */
    private static void turnTo(PoseStack pose, Direction side) {
        switch (side) {
            case EAST -> dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-90));
            case SOUTH -> dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-180));
            case WEST -> dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-270));
            case UP -> dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(-270));
            case DOWN -> dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(-90));
            default -> { } // north: as drawn
        }
    }
}
