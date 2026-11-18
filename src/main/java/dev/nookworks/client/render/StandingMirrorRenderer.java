package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.decor.ChevalMirrorBlock;
import dev.nookworks.decor.StandingMirrorBlockEntity;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import dev.nookworks.port.render.BlockEntityRenderState;
import dev.nookworks.port.render.ModelFeatureRenderer;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Standing Mirror's glass, over the model's silver: what is before it, from {@link MirrorReflections}, while it is one
 * of the few near enough to be drawn; otherwise the silver shows. Under an Iris shader pack it is drawn over the pack's
 * finished picture instead, so the pack neither lights nor blooms it; beneath it, for the pack to light, goes the board
 * the pack's light is measured on ({@link MirrorLight}).
 */
public class StandingMirrorRenderer implements BlockEntityRenderer<StandingMirrorBlockEntity, StandingMirrorRenderer.State> {
    /** The glass in the two blocks' picture, as the model has it: columns and rows, ends exclusive. */
    static final int LEFT = 3;
    static final int RIGHT = 13;
    static final int TOP = 3;
    static final int BOTTOM = 29;
    /** A little in front of the model's glass. */
    private static final float OUT = MirrorReflections.GLASS_OUT + 0.002F;
    /** Over a pack's picture, further out: before the board the pack's light is measured on (MirrorLight). */
    static final float OUT_OVER_SHADERS = MirrorReflections.GLASS_OUT + 0.016F;

    public StandingMirrorRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.NORTH;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(StandingMirrorBlockEntity mirror, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(mirror, state, partialTicks, camera, breakProgress);
        var block = mirror.getBlockState();
        state.facing = block.hasProperty(ChevalMirrorBlock.FACING) ? block.getValue(ChevalMirrorBlock.FACING) : Direction.NORTH;
        // PORT: 26.3 offers the mirror for a picture here; 1.21.1 calls this once the world is half drawn, too late, so
        // MirrorReflections looks for the mirrors in view itself before the world's blocks are drawn, and is told here
        // which the world found in sight (for the next frame's choice)
        if (!IrisHooks.shadowPass()) {
            MirrorReflections.rendered(mirror.getBlockPos());
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (IrisHooks.shadowPass()) {
            return; // the frame casts the shadow; the glass is drawn in the view only
        }
        MirrorReflections.Reflection glass = MirrorReflections.glass(state.blockPos);
        if (glass == null) {
            return;
        }
        if (IrisHooks.shaderPackInUse()) {
            if (MirrorLight.mode() != MirrorLight.Mode.OFF) {
                // for the pack to light: read where the glass is once its picture is finished, then painted over
                collector.submitCustomGeometry(pose, MirrorLight.BOARD, (p, buffer) -> MirrorLight.board(p, buffer, state.facing));
            }
            if (MirrorLight.mode() != MirrorLight.Mode.SHOW) {
                MirrorReflections.overShaders(state.blockPos, state.facing, glass.slot()); // drawn over the pack's picture, after it
            }
        } else {
            collector.submitCustomGeometry(pose, glass.type(), (p, buffer) -> PixelFaces.bare(p, buffer, state.facing, OUT, LEFT, RIGHT, TOP, BOTTOM));
        }
    }

    /**
     * The glass's face on {@code buffer} as drawn over a shader pack's picture: its four corners, for a mirror at
     * {@code pose}'s block facing {@code facing}, a little further out (BSL's depth lost it to the model's glass beneath
     * when seen from aside).
     */
    static void faceOverShaders(PoseStack.Pose pose, VertexConsumer buffer, Direction facing) {
        PixelFaces.bare(pose, buffer, facing, OUT_OVER_SHADERS, LEFT, RIGHT, TOP, BOTTOM);
    }

    /** Both halves: the glass stands two blocks high. */
    @Override
    public AABB getRenderBoundingBox(StandingMirrorBlockEntity mirror) {
        return new AABB(mirror.getBlockPos()).expandTowards(0, 1, 0);
    }
}
