package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.GrillBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;

/** Draws what lies on a Grill, flat on the grate, one piece in each quarter, as a campfire draws its food. */
public class GrillRenderer implements BlockEntityRenderer<GrillBlockEntity, GrillRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        /** The top of the grate: low over a fire, at the top of the block standing on its own. */
        float grate = 1.2F / 16;
    }

    private final ItemModelResolver itemModels;

    public GrillRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(GrillBlockEntity grill, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(grill, state, partialTicks, camera, breakProgress);
        state.items.clear();
        state.grate = GrillBlock.overFire(grill.getBlockState()) ? 1.2F / 16 : 16.2F / 16;
        int seed = (int) grill.getBlockPos().asLong();
        for (int slot = 0; slot < GrillBlockEntity.SLOTS; slot++) {
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModels.updateForTopItem(item, grill.items().get(slot), ItemDisplayContext.FIXED, grill.getLevel(), null, seed + slot);
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
            pose.translate(slot % 2 == 0 ? 0.28F : 0.72F, state.grate, slot < 2 ? 0.28F : 0.72F);
            pose.rotate(Axis.YP.rotationDegrees(slot * 90.0F + 20.0F));
            pose.rotate(Axis.XP.rotationDegrees(90.0F));
            pose.scale(0.36F, 0.36F, 0.36F);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
