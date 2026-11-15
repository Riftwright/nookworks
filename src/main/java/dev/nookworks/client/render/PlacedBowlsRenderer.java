package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.PlacedBowlsBlock;
import dev.nookworks.cooking.PlacedBowlsBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the dishes in bowls set straight down on a block ({@link PlacedBowlsBlock}): each as the dish itself, bowl and
 * all ({@link DishModels}), one in the middle or two side by side as seen by whoever set the first one down. The block
 * has no model of its own: all of it is drawn here.
 */
public class PlacedBowlsRenderer implements BlockEntityRenderer<PlacedBowlsBlockEntity, PlacedBowlsRenderer.State> {
    /** A bowl's own room, in pixels: it is alone in it. */
    private static final float ROOM = 7;

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.SOUTH;
        final List<TableLots.Lot> bowls = new ArrayList<>();
    }

    private final ItemModelResolver itemModels;

    public PlacedBowlsRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlacedBowlsBlockEntity bowls, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(bowls, state, partialTicks, camera, breakProgress);
        var block = bowls.getBlockState();
        state.facing = block.hasProperty(PlacedBowlsBlock.FACING) ? block.getValue(PlacedBowlsBlock.FACING) : Direction.SOUTH;
        state.bowls.clear();
        int seed = (int) bowls.getBlockPos().asLong();
        for (int i = 0; i < bowls.count(); i++) {
            state.bowls.add(TableLots.lot(bowls.bowl(i), 0, this.itemModels, bowls.getLevel(), seed + i));
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int n = state.bowls.size();
        if (n == 0) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            // the first to the left of whoever faces the front, the second to the right
            pose.translate(n == 1 ? 0 : (i == 0 ? -1 : 1) * PlacedBowlsBlock.APART / 16, 0, 0);
            TableLots.draw(state.bowls.get(i), PlacedBowlsBlock.SCALE, ROOM, ROOM, i == 0 ? -6 : 8, false, state.lightCoords, pose, collector);
            pose.popPose();
        }
        pose.popPose();
    }
}
