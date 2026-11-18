package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.NukadokoBlockEntity;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import dev.nookworks.port.render.BlockEntityRenderState;
import dev.nookworks.port.render.ModelFeatureRenderer;
import dev.nookworks.port.render.ItemModelResolver;
import dev.nookworks.port.render.ItemStackRenderState;
import dev.nookworks.port.render.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/**
 * Draws what is buried in a Nukadoko: each vegetable standing in the bran with its top showing, one in each quarter
 * of the tub, as pickles are pushed into a real bed - so a glance says what is in and how many.
 */
public class NukadokoRenderer implements BlockEntityRenderer<NukadokoBlockEntity, NukadokoRenderer.State> {
    /** The bran's surface: the tub's top, 11 px up. */
    private static final float BRAN = 11.0F / 16;

    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
    }

    private final ItemModelResolver itemModels;

    public NukadokoRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = dev.nookworks.port.render.ItemModelResolver.get();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(NukadokoBlockEntity tub, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tub, state, partialTicks, camera, breakProgress);
        state.items.clear();
        int seed = (int) tub.getBlockPos().asLong();
        for (int slot = 0; slot < NukadokoBlockEntity.SLOTS; slot++) {
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModels.updateForTopItem(item, tub.items().get(slot), ItemDisplayContext.FIXED, tub.getLevel(), null, seed + slot);
            state.items.add(item);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        for (int slot = 0; slot < state.items.size(); slot++) {
            ItemStackRenderState item = state.items.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            pose.pushPose();
            // standing up in its quarter, pushed in so the lower half is under the bran, leaning a little
            pose.translate(slot % 2 == 0 ? 0.33F : 0.67F, BRAN + 0.06F, slot < 2 ? 0.33F : 0.67F);
            dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(slot * 90.0F + 35.0F));
            dev.nookworks.port.render.Poses.rotate(pose, Axis.ZP.rotationDegrees(slot % 2 == 0 ? 12.0F : -9.0F));
            pose.scale(0.34F, 0.34F, 0.34F);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
