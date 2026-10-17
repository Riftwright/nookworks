package dev.nookworks.client.render;

import java.util.List;

import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.Nookworks;
import dev.nookworks.block.DiceBlock;
import dev.nookworks.block.DiceTumble;
import dev.nookworks.block.entity.DiceBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * A die ({@link DiceTumble}): its model unturned, posed as the roll has it this frame - tossed, spinning, striking the
 * ground and tumbling over - and once it rests, as it lies, off the middle of its block and turned as it came to rest
 * (its block draws nothing of itself). Cracks as it is broken, as a block does.
 */
public class DiceRenderer implements BlockEntityRenderer<DiceBlockEntity, DiceRenderer.State> {
    /** The die with 1 up and 2 to the north: every pose is this one turned. */
    public static final StandaloneModelKey<BlockStateModelPart> BODY = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dice_body");
    private static final int[] NO_TINTS = new int[0];

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(BODY, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dice_1")));
    }

    public static final class State extends BlockEntityRenderState {
        final double[] pose = new double[7];
    }

    public DiceRenderer(BlockEntityRendererProvider.Context context) {}

    /** It draws the whole die, at rest too (the block draws nothing): seen as far as a block is, near enough. */
    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DiceBlockEntity die, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(die, state, partialTicks, camera, breakProgress);
        DiceTumble.Result roll = die.getBlockState().getValue(DiceBlock.ROLL) > 0 ? die.tumble() : null;
        double[] pose = roll != null ? roll.at(die.elapsed(partialTicks)) : die.lying(die.getBlockState());
        System.arraycopy(pose, 0, state.pose, 0, 7);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart body = Minecraft.getInstance().getModelManager().getStandaloneModel(BODY);
        if (body == null) {
            return;
        }
        double[] p = state.pose;
        pose.pushPose();
        pose.translate(p[0], p[1], p[2]);
        pose.rotate(new Quaternionf((float) p[3], (float) p[4], (float) p[5], (float) p[6]));
        // the model's die sits on the block's floor, its middle 4 px up
        pose.translate(-0.5F, (float) -DiceTumble.HALF, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(body), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        if (state.breakProgress != null) {
            collector.submitBreakingBlockModel(pose, List.of(body), state.breakProgress.progress(), false);
        }
        pose.popPose();
    }
}
