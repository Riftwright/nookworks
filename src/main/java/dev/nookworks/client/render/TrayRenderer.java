package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.Serving;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Draws what is on a Wooden Tray, each lot in the room its food takes on the board ({@link Serving#trayPlaces}): one
 * in the middle, two side by side, a long thing across the back with two before it - turned to face whoever set the
 * tray down. Bread Dough is a ball of dough that swells as it rises, and risen, the proofed ball; food with a model is
 * set out as the food itself, several of a small thing together ({@link TableLots}); other food lies flat, as on a
 * campfire, and a block stands up on it.
 */
public class TrayRenderer implements BlockEntityRenderer<TrayBlockEntity, TrayRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.SOUTH;
        List<TableLots.@Nullable Lot> lots = List.of();
        float @Nullable [][] places;
    }

    private final ItemModelResolver itemModels;

    public TrayRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(TrayBlockEntity tray, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(tray, state, partialTicks, camera, breakProgress);
        var block = tray.getBlockState();
        state.facing = block.hasProperty(TrayBlock.FACING) ? block.getValue(TrayBlock.FACING) : Direction.SOUTH;
        long now = tray.getLevel() == null ? 0 : tray.getLevel().getGameTime();
        state.lots = TableLots.lots(tray.items(), slot -> tray.rise(slot, now, partialTicks), this.itemModels, tray.getLevel(),
                (int) tray.getBlockPos().asLong());
        state.places = Serving.trayPlaces(tray.items());
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        TableLots.drawTray(state.lots, state.places, state.lightCoords, pose, collector);
        pose.popPose();
    }
}
