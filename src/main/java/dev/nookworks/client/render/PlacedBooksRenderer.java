package dev.nookworks.client.render;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.Nookworks;
import dev.nookworks.decor.PlacedBooksBlock;
import dev.nookworks.decor.PlacedBooksBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;

/**
 * The books lying on a block ({@link PlacedBooksBlock}): each of them as it fell - its cover's model, turned and set off as
 * its block entity keeps it, on the one under it (the block itself draws nothing). Cracks as it is broken.
 */
public class PlacedBooksRenderer implements BlockEntityRenderer<PlacedBooksBlockEntity, PlacedBooksRenderer.State> {
    private static final String[] KINDS = {"plain", "enchanted", "written", "writable"};
    /** By kind: lying (a little smaller) and standing (block/placed_book_<kind>_tall). */
    private static final List<StandaloneModelKey<BlockStateModelPart>> MODELS = List.of(key("plain"), key("enchanted"),
            key("written"), key("writable"));
    private static final List<StandaloneModelKey<BlockStateModelPart>> TALL_MODELS = List.of(key("plain_tall"),
            key("enchanted_tall"), key("written_tall"), key("writable_tall"));
    private static final int[] NO_TINTS = new int[0];

    private static StandaloneModelKey<BlockStateModelPart> key(String kind) {
        return new StandaloneModelKey<>(() -> Nookworks.MODID + ":placed_book_" + kind);
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        for (int i = 0; i < KINDS.length; i++) {
            event.register(MODELS.get(i), SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/placed_book_" + KINDS[i])));
            event.register(TALL_MODELS.get(i), SimpleUnbakedStandaloneModel.simpleModelWrapper(
                    Nookworks.id("block/placed_book_" + KINDS[i] + "_tall")));
        }
    }

    /** A book's model by its kind (PlacedBooksBlock.Kind's ordinal), lying or standing; null before the models are baked. */
    static @Nullable BlockStateModelPart book(int kind, boolean standing) {
        return Minecraft.getInstance().getModelManager().getStandaloneModel((standing ? TALL_MODELS : MODELS).get(kind));
    }

    public static final class State extends BlockEntityRenderState {
        int count;
        final int[] kind = new int[PlacedBooksBlock.MAX];
        final float[] yaw = new float[PlacedBooksBlock.MAX];
        final float[] dx = new float[PlacedBooksBlock.MAX];
        final float[] dz = new float[PlacedBooksBlock.MAX];
        boolean standing;
        float facingYaw;
        float lean;
    }

    public PlacedBooksRenderer(BlockEntityRendererProvider.Context context) {}

    /** It draws the whole pile (the block draws nothing): seen as far as a block is, near enough, as the bottles are. */
    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlacedBooksBlockEntity books, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(books, state, partialTicks, camera, breakProgress);
        state.count = books.count();
        var block = books.getBlockState();
        state.standing = block.hasProperty(PlacedBooksBlock.STANDING) && block.getValue(PlacedBooksBlock.STANDING);
        state.facingYaw = state.standing ? -block.getValue(PlacedBooksBlock.FACING).toYRot() : 0.0F;
        state.lean = books.lean();
        boolean mirrored = block.hasProperty(PlacedBooksBlock.MIRRORED) && block.getValue(PlacedBooksBlock.MIRRORED);
        for (int i = 0; i < state.count; i++) {
            state.kind[i] = PlacedBooksBlock.Kind.of(books.book(i)).ordinal();
            if (state.standing) {
                // along the wall, in the block turned to its wall: a mirrored row leans and sits the other way about
                state.yaw[i] = mirrored ? -books.yaw(i) : books.yaw(i);
                state.dx[i] = mirrored ? -books.dx(i) : books.dx(i);
                state.dz[i] = books.dz(i);
            } else {
                float[] lie = PlacedBooksBlock.lieInWorld(block, books.yaw(i), books.dx(i), books.dz(i));
                state.yaw[i] = lie[0];
                state.dx[i] = lie[1];
                state.dz[i] = lie[2];
            }
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.standing) {
            this.submitStanding(state, pose, collector);
            return;
        }
        for (int i = 0; i < state.count; i++) {
            BlockStateModelPart book = book(state.kind[i], false);
            if (book == null) {
                continue;
            }
            pose.pushPose();
            // its middle where it fell, on the books under it; turned about its middle
            pose.translate(0.5F + state.dx[i] / 16.0F, i * PlacedBooksBlock.THICK / 16.0F, 0.5F + state.dz[i] / 16.0F);
            pose.rotate(Axis.YP.rotationDegrees(state.yaw[i]));
            pose.translate(-0.5F, 0.0F, -0.5F);
            collector.submitMultiLayerBlockModel(pose, List.of(book), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            if (state.breakProgress != null) {
                collector.submitBreakingBlockModel(pose, List.of(book), state.breakProgress.progress(), false);
            }
            pose.popPose();
        }
    }

    /**
     * Books leant against a wall, cover out: in the block turned so its wall is to the north, each stood up on its foot
     * (its spine to the left), leant back until its top is on the wall - or on the book behind it - and turned a few
     * degrees as it was put there. Each pivots on its back foot, as a book leant back does.
     */
    private void submitStanding(State state, PoseStack pose, SubmitNodeCollector collector) {
        double lean = Math.toRadians(state.lean);
        float tall = PlacedBooksBlock.TALL / 16.0F;
        float thick = PlacedBooksBlock.THICK / 16.0F;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(state.facingYaw));
        pose.translate(-0.5F, 0.0F, -0.5F);
        for (int i = 0; i < state.count; i++) {
            BlockStateModelPart book = book(state.kind[i], true);
            if (book == null) {
                continue;
            }
            // its back foot as far from the wall as its top must lean to touch the wall, or the book behind
            float foot = (float) (tall * Math.sin(lean) + i * thick / Math.cos(lean));
            pose.pushPose();
            pose.translate(0.5F + state.dx[i] / 16.0F, 0.0F, foot);
            pose.rotate(Axis.XP.rotationDegrees(-state.lean));
            pose.rotate(Axis.YP.rotationDegrees(state.yaw[i]));
            pose.rotate(Axis.XP.rotationDegrees(90.0F));
            // the model's book lies in the block's middle, its foot (the end of its length) at z 8 + TALL/2 px, its back at y 0
            pose.translate(-0.5F, 0.0F, -(8.0F + PlacedBooksBlock.TALL / 2.0F) / 16.0F);
            collector.submitMultiLayerBlockModel(pose, List.of(book), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            if (state.breakProgress != null) {
                collector.submitBreakingBlockModel(pose, List.of(book), state.breakProgress.progress(), false);
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
