package dev.nookworks.client.render;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.poster.PaintingFrames;
import dev.nookworks.poster.PictureFrames;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;
import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's painting renderer, with a frame round a framed painting ({@link PaintingFrames}): the painting drawn as
 * vanilla draws it, then the rim on its face ({@link FrameRims}, standing on the board's face as round a poster it stands
 * on the wall). In a glowing frame the painting and its frame are drawn at full light, as a poster and its frame are:
 * vanilla lights the board's edges as its face, and a frame left dark showed a bright line of board round it at night.
 */
public class FramedPaintingRenderer extends PaintingRenderer {
    /** A painting's board: its face a thirty-second out from the middle, as vanilla draws it. */
    private static final float FACE = -0.03125F;
    private static final int FULL_BRIGHT = 0xF000F0;

    public FramedPaintingRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public static class State extends PaintingRenderState {
        /** The block texture the frame's rim is cut from; null, no frame. */
        public @Nullable Identifier rim;
        public boolean moulded;
        /** The light the rim takes, block by block: the painting's own. */
        public int[] rimLight = new int[0];
    }

    @Override
    public PaintingRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(Painting entity, PaintingRenderState state, float partialTicks) {
        super.extractRenderState(entity, state, partialTicks);
        if (!(state instanceof State framed)) {
            return;
        }
        ItemStack frame = PaintingFrames.frame(entity);
        PictureFrames.Look look = frame.isEmpty() ? null : PictureFrames.look(frame);
        framed.rim = look == null ? null : look.rim();
        framed.moulded = look != null && look.moulded();
        if (look != null) {
            if (look.glow()) {
                Arrays.fill(state.lightCoordsPerBlock, FULL_BRIGHT);
            }
            framed.rimLight = Arrays.copyOf(state.lightCoordsPerBlock, state.lightCoordsPerBlock.length);
        }
    }

    @Override
    public void submit(PaintingRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        PaintingVariant variant = state.variant;
        if (variant == null || !(state instanceof State framed) || framed.rim == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.rotateDegrees(Axis.YP, 180 - state.direction.get2DDataValue() * 90);
        FrameRims.submit(poseStack, collector, framed.rim, framed.moulded, variant.width(), variant.height(), framed.rimLight, FACE);
        poseStack.popPose();
    }
}
