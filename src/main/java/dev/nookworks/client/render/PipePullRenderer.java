package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.experience.ExperiencePipeBlock;
import dev.nookworks.fluid.CopperPipeBlock;
import dev.nookworks.fluid.FluidPipeBlockEntity;
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
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

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

    private static StandaloneModelKey<BlockStateModelPart> key(Identifier model) {
        return new StandaloneModelKey<>(model::toString);
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
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
        BlockStateModelPart part = Minecraft.getInstance().getModelManager().getStandaloneModel(this.flange);
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
            case EAST -> pose.rotate(Axis.YP.rotationDegrees(-90));
            case SOUTH -> pose.rotate(Axis.YP.rotationDegrees(-180));
            case WEST -> pose.rotate(Axis.YP.rotationDegrees(-270));
            case UP -> pose.rotate(Axis.XP.rotationDegrees(-270));
            case DOWN -> pose.rotate(Axis.XP.rotationDegrees(-90));
            default -> { } // north: as drawn
        }
    }
}
