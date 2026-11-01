package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
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
 * Draws what is on a Wooden Tray, laid out by how much there is - one in the middle, two side by side, three or four
 * in rows - turned to face whoever set the tray down. Bread Dough is a ball of dough ({@code block/dough_ball}) that
 * swells as it rises, and risen, the proofed ball ({@code block/dough_ball_risen}); udon, tonkatsu, karaage and donuts
 * are set out as the dishes themselves ({@link DishModels}); other food lies flat, as on a campfire, and a block stands
 * up on it.
 */
public class TrayRenderer implements BlockEntityRenderer<TrayBlockEntity, TrayRenderer.State> {
    public static final StandaloneModelKey<BlockStateModelPart> BALL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dough_ball");
    public static final StandaloneModelKey<BlockStateModelPart> RISEN_BALL = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dough_ball_risen");
    private static final int[] NO_TINTS = new int[0];
    /** The top of the board, where things sit. */
    private static final float TOP = 1.0F / 16;
    /** Where the things go, by how many there are: pixels across and back, for a tray whose front faces south. */
    private static final float[][][] LAYOUT = {
            {{8, 8}},
            {{5, 8}, {11, 8}},
            {{5, 5.5F}, {11, 5.5F}, {8, 11}},
            {{5, 5}, {11, 5}, {5, 11}, {11, 11}},
    };
    /** A little turn to each place, so four loaves do not lie like soldiers. */
    private static final float[] TWIST = {-8, 6, 10, -5};

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        event.register(BALL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dough_ball")));
        event.register(RISEN_BALL, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dough_ball_risen")));
    }

    enum Kind { ITEM, DOUGH, RISEN, DISH }

    public static final class State extends BlockEntityRenderState {
        Direction facing = Direction.SOUTH;
        final List<ItemStackRenderState> items = new ArrayList<>();
        final List<Kind> kinds = new ArrayList<>();
        final List<Float> rise = new ArrayList<>();
        final List<@Nullable StandaloneModelKey<BlockStateModelPart>> dishes = new ArrayList<>();
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
        state.items.clear();
        state.kinds.clear();
        state.rise.clear();
        state.dishes.clear();
        int seed = (int) tray.getBlockPos().asLong();
        long now = tray.getLevel() == null ? 0 : tray.getLevel().getGameTime();
        for (int slot = 0; slot < TrayBlockEntity.SLOTS; slot++) {
            ItemStack stack = tray.items().get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModels.updateForTopItem(item, stack, ItemDisplayContext.FIXED, tray.getLevel(), null, seed + slot);
            state.items.add(item);
            var dish = DishModels.of(stack);
            state.dishes.add(dish);
            state.kinds.add(stack.is(ModKitchen.BREAD_DOUGH.get()) ? Kind.DOUGH : stack.is(ModKitchen.RISEN_DOUGH.get()) ? Kind.RISEN
                    : dish != null ? Kind.DISH : Kind.ITEM);
            state.rise.add(tray.rise(slot, now, partialTicks));
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        int n = state.items.size();
        if (n == 0) {
            return;
        }
        float[][] places = LAYOUT[n - 1];
        pose.pushPose();
        pose.translate(0.5F, 0, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(-0.5F, 0, -0.5F);
        for (int i = 0; i < n; i++) {
            pose.pushPose();
            pose.translate(places[i][0] / 16, TOP, places[i][1] / 16);
            switch (state.kinds.get(i)) {
                case DOUGH, RISEN -> this.ball(state, i, n, pose, collector);
                case ITEM -> this.item(state, i, n, pose, collector);
                case DISH -> {
                    // set out as the dish itself, a little smaller when it shares the tray (the user, 2026-10-31)
                    pose.rotate(Axis.YP.rotationDegrees(TWIST[i]));
                    DishModels.draw(state.dishes.get(i), n == 1 ? 1.0F : 0.72F, state.lightCoords, pose, collector);
                }
            }
            pose.popPose();
        }
        pose.popPose();
    }

    /** A ball of dough, a little smaller when it shares the tray, swelling towards the risen ball as it proofs. */
    private void ball(State state, int i, int n, PoseStack pose, SubmitNodeCollector collector) {
        boolean risen = state.kinds.get(i) == Kind.RISEN;
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(risen ? RISEN_BALL : BALL);
        if (model == null) {
            return;
        }
        float size = n == 1 ? 1.1F : n == 2 ? 0.85F : 0.72F; // balls cut from one dough, smaller the more of them
        float t = risen ? 0 : state.rise.get(i);
        // the raw ball is 3 px high and 6 wide, the risen one 5 and 7: it goes up more than it spreads
        pose.rotate(Axis.YP.rotationDegrees(TWIST[i] * 2));
        pose.scale(size * (1 + 0.2F * t), size * (1 + 0.6F * t), size * (1 + 0.2F * t));
        pose.translate(-0.5F, 0, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(model), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }

    /** Food lies flat, its top away from the tray's front; a block model stands on the board. */
    private void item(State state, int i, int n, PoseStack pose, SubmitNodeCollector collector) {
        ItemStackRenderState item = state.items.get(i);
        float scale = n == 1 ? 0.55F : 0.4F;
        pose.rotate(Axis.YP.rotationDegrees(TWIST[i]));
        if (item.usesBlockLight()) {
            pose.translate(0, 0.25F * scale, 0); // a block's item model is half a block across, centred
            pose.scale(scale, scale, scale);
        } else {
            pose.translate(0, 0.02F, 0);
            pose.rotate(Axis.XP.rotationDegrees(-90)); // face up, its top to the back of the tray
            pose.scale(scale, scale, scale);
        }
        item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
    }
}
