package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.PlateBlock;
import dev.nookworks.cooking.PlateBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws what is on a Plate: udon, tonkatsu, karaage and donuts as the dishes themselves ({@link DishModels}), any
 * other food flat on it as on a tray - turned to face whoever set the plate down.
 */
public class PlateRenderer implements BlockEntityRenderer<PlateBlockEntity, PlateRenderer.State> {
    /** The plate's top, where the food sits. */
    private static final float TOP = 1.0F / 16;

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.SOUTH;
        @Nullable StandaloneModelKey<BlockStateModelPart> dish;
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean empty = true;
    }

    private final ItemModelResolver itemModels;

    public PlateRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlateBlockEntity plate, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(plate, state, partialTicks, camera, breakProgress);
        var block = plate.getBlockState();
        state.facing = block.hasProperty(PlateBlock.FACING) ? block.getValue(PlateBlock.FACING) : Direction.SOUTH;
        ItemStack food = plate.food();
        state.empty = food.isEmpty();
        state.dish = DishModels.of(food);
        if (!state.empty && state.dish == null) {
            this.itemModels.updateForTopItem(state.item, food, ItemDisplayContext.FIXED, plate.getLevel(), null, (int) plate.getBlockPos().asLong());
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.empty) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, TOP, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        if (state.dish != null) {
            DishModels.draw(state.dish, 0.85F, state.lightCoords, pose, collector);
        } else if (state.item.usesBlockLight()) {
            pose.translate(0, 0.25F * 0.5F, 0);
            pose.scale(0.5F, 0.5F, 0.5F);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        } else {
            pose.translate(0, 0.02F, 0);
            pose.rotate(Axis.XP.rotationDegrees(-90));
            pose.scale(0.55F, 0.55F, 0.55F);
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }
}
