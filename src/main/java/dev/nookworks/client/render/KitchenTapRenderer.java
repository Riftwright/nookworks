package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import dev.nookworks.cooking.KitchenTapBlock;
import dev.nookworks.cooking.KitchenTapBlockEntity;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import dev.nookworks.port.render.BlockEntityRenderState;
import dev.nookworks.port.render.ModelFeatureRenderer;
import dev.nookworks.port.render.RenderTypes;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import dev.nookworks.port.render.FluidModel;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

/**
 * A Kitchen Tap's stream while it pours: a thin column of what it pours, in that liquid's own picture and colour, from
 * the spout's mouth down into the vessel under it - as far as a pot's water is, near enough, half a block into it.
 */
public class KitchenTapRenderer implements BlockEntityRenderer<KitchenTapBlockEntity, KitchenTapRenderer.State> {
    /** Half the stream's width, and where it ends below the tap's block, in blocks. */
    private static final float HALF = 0.75F / 16;
    private static final float FOOT = -0.55F;

    public static final class State extends BlockEntityRenderState {
        boolean pouring;
        Direction facing = Direction.NORTH;
        @Nullable TextureAtlasSprite sprite;
        int tint = -1;
        double ticks;
    }

    public KitchenTapRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(KitchenTapBlockEntity tap, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tap, state, partialTicks, camera, breakProgress);
        var block = tap.getBlockState();
        Fluid fluid = tap.poured();
        state.pouring = block.getValue(KitchenTapBlock.POURING) && block.getValue(KitchenTapBlock.OPEN) && fluid != Fluids.EMPTY;
        state.facing = block.getValue(KitchenTapBlock.FACING);
        state.ticks = tap.getLevel() == null ? 0 : tap.getLevel().getGameTime() + (double) partialTicks;
        state.sprite = null;
        if (state.pouring) {
            try {
                // the liquid's own flowing picture and colour, as the game draws it running
                FluidModel model = dev.nookworks.port.render.PortModelManager.of(Minecraft.getInstance().getModelManager()).getFluidStateModelSet().get(fluid.defaultFluidState());
                state.sprite = model.flowingMaterial().sprite();
                state.tint = model.fluidTintSource() != null ? model.fluidTintSource().color(fluid.defaultFluidState()) : -1;
            } catch (RuntimeException unreadable) {
                state.pouring = false; // another mod's liquid drawn some way not looked for: no stream rather than a wrong one
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        TextureAtlasSprite sprite = state.sprite;
        if (!state.pouring || sprite == null) {
            return;
        }
        // the spout's mouth, turned with the tap (drawn facing north)
        float x = 0.5F;
        float z = 0.5F;
        float top = (float) (KitchenTapBlock.SPOUT_Y / 16);
        int colour = state.tint | 0xFF000000;
        // the picture runs down the stream as a pour does
        float scroll = (float) ((state.ticks * 0.08) % 1.0);
        int light = state.lightCoords;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(sprite.atlasLocation()), (p, buffer) -> {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                face(p, buffer, sprite, side, x, z, top, FOOT, scroll, colour, light);
            }
        });
    }

    /** One side of the stream: a strip a pixel and a half wide from {@code top} down to {@code foot}. */
    private static void face(PoseStack.Pose pose, VertexConsumer buffer, TextureAtlasSprite sprite, Direction side, float x, float z,
            float top, float foot, float scroll, int colour, int light) {
        float nx = side.getStepX();
        float nz = side.getStepZ();
        // the strip's two edges, across the side it faces, a half-width out from the middle
        float ox = x + nx * HALF;
        float oz = z + nz * HALF;
        float ax = -nz * HALF;
        float az = nx * HALF;
        float u0 = sprite.getU(0.45F);
        float u1 = sprite.getU(0.55F);
        float v0 = sprite.getV(scroll * 0.5F);
        float v1 = sprite.getV(scroll * 0.5F + (top - foot) * 0.5F);
        vertex(buffer, pose, ox - ax, top, oz - az, u0, v0, colour, light, nx, nz);
        vertex(buffer, pose, ox - ax, foot, oz - az, u0, v1, colour, light, nx, nz);
        vertex(buffer, pose, ox + ax, foot, oz + az, u1, v1, colour, light, nx, nz);
        vertex(buffer, pose, ox + ax, top, oz + az, u1, v0, colour, light, nx, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int colour,
            int light, float nx, float nz) {
        buffer.addVertex(pose, x, y, z).setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, 0, nz);
    }
}
