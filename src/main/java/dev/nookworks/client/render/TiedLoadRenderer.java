package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.carry.CarriedLook;
import dev.nookworks.client.carry.CarriedBlockRendering;
import dev.nookworks.client.carry.RopeWrapRendering;
import dev.nookworks.rope.TiedLoadEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;

/** A Tied Load: its block as the world draws it, with the rope wound round it, and cracks while it is being hit. */
public class TiedLoadRenderer extends EntityRenderer<TiedLoadEntity, TiedLoadRenderer.State> {
    public static final class State extends EntityRenderState {
        CarriedLook look = CarriedLook.EMPTY;
        float partialTick;
        BlockPos cell = BlockPos.ZERO;
        int crack = -1;
    }

    public TiedLoadRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TiedLoadEntity load, State state, float partialTicks) {
        super.extractRenderState(load, state, partialTicks);
        state.look = load.look();
        state.partialTick = partialTicks;
        state.cell = BlockPos.containing(load.getX(), load.getY() + 0.5, load.getZ());
        state.crack = load.crack();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(-0.5F, 0.0F, -0.5F); // from its bottom middle to the block's corner
        CarriedBlockRendering.submit(state.look, pose, collector, state.lightCoords, state.partialTick, camera, state.cell);
        CarriedBlockRendering.submitCracks(state.look, pose, collector, state.crack);
        RopeWrapRendering.submit(state.look, pose, collector, state.lightCoords);
        pose.popPose();
        super.submit(state, pose, collector, camera);
    }
}
