package dev.nookworks.client.render;

import java.util.List;

import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import dev.nookworks.decor.BookendBlock;
import dev.nookworks.decor.BookendBlockEntity;
import dev.nookworks.decor.PlacedBooksBlock;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;

/**
 * The books a Bookend holds ({@link BookendBlock}): each stood upright on its foot, cover to the upright (or to the book
 * before it), spine out - the standing books' models, as leant against a wall. The bookend itself is its block model.
 */
public class BookendRenderer implements BlockEntityRenderer<BookendBlockEntity, BookendRenderer.State> {
    private static final int[] NO_TINTS = new int[0];
    /** The model's book (lying, its spine to the west, its length along z) stood on its foot, its spine to the south. */
    private static final Quaternionf UPRIGHT = new Quaternionf().setFromNormalized(new Matrix3f(0, 0, -1, 1, 0, 0, 0, -1, 0));

    public static final class State extends BlockEntityRenderState {
        int count;
        final int[] kind = new int[BookendBlock.MAX];
        float facingYaw;
        boolean right;
        /** A Row of Books: no upright, the books a fifth of the block apart. */
        boolean row;
    }

    public BookendRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(BookendBlockEntity books, State state, float partialTicks, Vec3 camera,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(books, state, partialTicks, camera, breakProgress);
        state.count = books.count();
        var block = books.getBlockState();
        state.facingYaw = block.hasProperty(BookendBlock.FACING) ? -block.getValue(BookendBlock.FACING).toYRot() : 0.0F;
        state.right = block.hasProperty(BookendBlock.HINGE) && block.getValue(BookendBlock.HINGE) == DoorHingeSide.RIGHT;
        state.row = block.getBlock() instanceof BookendBlock bookend && !bookend.upright();
        for (int i = 0; i < state.count; i++) {
            state.kind[i] = PlacedBooksBlock.Kind.of(books.book(i)).ordinal();
        }
    }

    /**
     * In the block turned so its spines face south, the upright to the west (or the east): each book's lower cover where
     * the row has got to, its fore-edge three pixels from the north side, its foot on the floor. In a Row of Books, each
     * fills its fifth of the block, so the row runs on from block to block with no gaps.
     */
    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float thick = PlacedBooksBlock.THICK / 16.0F;
        float upright = BookendBlock.UPRIGHT / 16.0F;
        pose.pushPose();
        pose.translate(0.5F, 0.0F, 0.5F);
        pose.rotate(Axis.YP.rotationDegrees(state.facingYaw));
        pose.translate(-0.5F, 0.0F, -0.5F);
        for (int i = 0; i < state.count; i++) {
            BlockStateModelPart book = PlacedBooksRenderer.book(state.kind[i], true);
            if (book == null) {
                continue;
            }
            float x;
            // in a Row of Books each book fills its fifth of the block, a little thicker than three pixels, so the row
            // runs on with no gap between books (the user, 2026-10-20: spaced a fifth apart, the gaps showed)
            float across = 1.0F;
            if (state.row) {
                float slot = BookendBlock.ROW_STEP / 16.0F;
                across = slot / thick;
                x = state.right ? 1.0F - (i + 1) * slot : i * slot;
            } else {
                x = state.right ? 1.0F - upright - (i + 1) * thick : upright + i * thick;
            }
            pose.pushPose();
            pose.translate(x, 0.0F, (8.0F - PlacedBooksBlock.WIDE / 2.0F) / 16.0F);
            pose.rotate(UPRIGHT);
            pose.scale(1.0F, across, 1.0F); // the model's y, its thickness, is the row's way
            // the model's book: its fore-edge at x 8 + WIDE/2, its back at y 0, its foot at z 8 + TALL/2
            pose.translate(-(8.0F + PlacedBooksBlock.WIDE / 2.0F) / 16.0F, 0.0F, -(8.0F + PlacedBooksBlock.TALL / 2.0F) / 16.0F);
            collector.submitMultiLayerBlockModel(pose, List.of(book), false, NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
            if (state.breakProgress != null) {
                collector.submitBreakingBlockModel(pose, List.of(book), state.breakProgress.progress(), false);
            }
            pose.popPose();
        }
        pose.popPose();
    }
}
