package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.PlateBlock;
import dev.nookworks.cooking.PlateBlockEntity;
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
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.render.StandaloneModelKey;

/**
 * Draws what is on a Plate: the food itself where it has a model ({@link DishModels}) - one of it, or the few a plate
 * holds of that food, laid side by side ({@link TableLots}) - and any other food flat on it, as its item's picture;
 * turned to face whoever set the plate down.
 */
public class PlateRenderer implements BlockEntityRenderer<PlateBlockEntity, PlateRenderer.State> {
    /** The plate's top, where the food sits, and the room on it, in pixels. */
    private static final float TOP = 1.0F / 16;
    private static final float ROOM = 10.6F;
    private static final float SCALE = 0.85F;

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.SOUTH;
        TableLots.@Nullable Lot lot;
    }

    private final ItemModelResolver itemModels;

    public PlateRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = dev.nookworks.port.render.ItemModelResolver.get();
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
        state.lot = food.isEmpty() ? null : TableLots.lot(food, 0, this.itemModels, plate.getLevel(), (int) plate.getBlockPos().asLong());
        if (state.lot != null) {
            state.lot.plated = false; // this plate is its plate
        }
    }

    /** Draws {@code lot} as it lies on a plate, the pose at the middle of the plate's top, its front to the south. */
    static void draw(TableLots.Lot lot, int light, PoseStack pose, SubmitNodeCollector collector) {
        // food drawn as its item was a little larger on a plate than the 0.55 of a block it is on a tray: kept
        TableLots.draw(lot, lot.kind == TableLots.Kind.ITEM ? 1.0F : SCALE, ROOM, ROOM, 0, true, light, pose, collector);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.lot == null) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, TOP, 0.5F);
        dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(-state.facing.toYRot()));
        // food drawn as its item was a little larger on a plate than the 0.55 of a block it is on a tray: kept
        draw(state.lot, state.lightCoords, pose, collector);
        pose.popPose();
    }
}
