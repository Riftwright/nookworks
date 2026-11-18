package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.GrillBlockEntity;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.BlockStateModelPart;
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
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws what lies on a Grill, one piece in each quarter of the grate: food that has a dish model ({@link DishModels}:
 * a steak, a fish, an ear of corn) as that model, smaller (trial of the cooking review, 2026-11-14), anything else
 * flat on the grate as a campfire draws its food.
 */
public class GrillRenderer implements BlockEntityRenderer<GrillBlockEntity, GrillRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<@Nullable StandaloneModelKey<BlockStateModelPart>> dishes = new ArrayList<>();
        /** The top of the grate: low over a fire, at the top of the block standing on its own. */
        float grate = 1.2F / 16;
    }

    private final ItemModelResolver itemModels;

    public GrillRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = dev.nookworks.port.render.ItemModelResolver.get();
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
        state.dishes.clear();
        state.grate = GrillBlock.overFire(grill.getBlockState()) ? 1.2F / 16 : 16.2F / 16;
        int seed = (int) grill.getBlockPos().asLong();
        for (int slot = 0; slot < GrillBlockEntity.SLOTS; slot++) {
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModels.updateForTopItem(item, grill.items().get(slot), ItemDisplayContext.FIXED, grill.getLevel(), null, seed + slot);
            state.items.add(item);
            state.dishes.add(DishModels.of(grill.items().get(slot)));
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
            var dish = state.dishes.get(slot);
            if (dish != null) {
                dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(slot * 90.0F + 20.0F));
                DishModels.draw(dish, 0.62F, state.lightCoords, pose, collector);
                pose.popPose();
                continue;
            }
            dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(slot * 90.0F + 20.0F));
            dev.nookworks.port.render.Poses.rotate(pose, Axis.XP.rotationDegrees(90.0F));
            pose.scale(0.36F, 0.36F, 0.36F);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
