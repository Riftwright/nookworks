package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Draws what is in an Iron Pot: the ingredients lying in the water or oil, in a ring round the pot as they were put
 * in, rocking a little on the surface while it simmers; and once the cooking is done, the dish waiting in the middle.
 * A pot seen across a kitchen says what is on the go in it without being opened.
 */
public class IronPotRenderer implements BlockEntityRenderer<IronPotBlockEntity, IronPotRenderer.State> {
    public static final class State extends BlockEntityRenderState {
        final List<ItemStackRenderState> items = new ArrayList<>();
        final ItemStackRenderState dish = new ItemStackRenderState();
        /** The top of the liquid (or of the pot's floor), in blocks: a pixel higher when the pot stands on a grill. */
        float surface = 5.1F / 16;
        boolean simmering;
        double ticks;
    }

    private final ItemModelResolver itemModels;

    public IronPotRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(IronPotBlockEntity pot, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(pot, state, partialTicks, camera, breakProgress);
        state.items.clear();
        var block = pot.getBlockState();
        boolean onGrill = block.hasProperty(IronPotBlock.ON_GRILL) && block.getValue(IronPotBlock.ON_GRILL);
        state.surface = (onGrill ? 6.1F : 5.1F) / 16;
        state.simmering = block.hasProperty(IronPotBlock.COOKING) && block.getValue(IronPotBlock.COOKING);
        // the bubbling's periods are no whole number of ticks, so no cycle of the time wraps cleanly: the angle is worked
        // out from the whole time, in doubles, and only the angle is a float
        state.ticks = pot.getLevel() == null ? 0 : pot.getLevel().getGameTime() + (double) partialTicks;
        int seed = (int) pot.getBlockPos().asLong();
        List<ItemStack> contents = pot.contents();
        for (int slot = 0; slot < contents.size(); slot++) {
            if (contents.get(slot).isEmpty()) {
                continue;
            }
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModels.updateForTopItem(item, contents.get(slot), ItemDisplayContext.FIXED, pot.getLevel(), null, seed + slot);
            state.items.add(item);
        }
        this.itemModels.updateForTopItem(state.dish, pot.dish(), ItemDisplayContext.FIXED, pot.getLevel(), null, seed);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int n = state.items.size();
        for (int i = 0; i < n; i++) {
            ItemStackRenderState item = state.items.get(i);
            // round the pot, a ring of them: one alone lies in the middle
            float angle = (float) (i * Math.PI * 2 / Math.max(1, n)) + 0.4F;
            float reach = n == 1 ? 0 : 0.15F;
            float bob = state.simmering ? (float) Math.sin(state.ticks * 0.15 + i * 1.7) * 0.2F / 16 : 0;
            pose.pushPose();
            pose.translate(0.5F + Mth.cos(angle) * reach, state.surface + bob + i * 0.001F, 0.5F + Mth.sin(angle) * reach);
            pose.rotate(Axis.YP.rotationDegrees(i * 67.0F + 15.0F));
            pose.rotate(Axis.XP.rotationDegrees(90.0F + (state.simmering ? (float) Math.sin(state.ticks * 0.11 + i) * 6.0F : 0)));
            pose.scale(0.24F, 0.24F, 0.24F);
            item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
        if (!state.dish.isEmpty()) {
            pose.pushPose();
            pose.translate(0.5F, state.surface + 0.012F, 0.5F);
            pose.rotate(Axis.YP.rotationDegrees(30.0F));
            pose.rotate(Axis.XP.rotationDegrees(90.0F));
            pose.scale(0.34F, 0.34F, 0.34F);
            state.dish.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            pose.popPose();
        }
    }
}
