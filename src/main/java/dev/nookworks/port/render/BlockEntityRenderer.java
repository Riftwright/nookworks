package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 26.3's two-step block entity renderer - fill a state ({@link #extractRenderState}), then queue what it draws
 * ({@link #submit}) - on top of 1.21.1's, which draws at once: {@link #render} makes a state, fills it as 26.3 does, and
 * submits it to a {@link SubmitNodeCollector} that draws straight into the buffers 1.21.1 hands over.
 * <p>
 * The light is worked out from the world as 26.3 does it (1.21.1's dispatcher passes the same), so a renderer may still set
 * its own; the cracks of a block being broken come from the buffers ({@link ModelFeatureRenderer.CrumblingOverlay}), so
 * {@code breakProgress} is null.
 */
public interface BlockEntityRenderer<T extends BlockEntity, S extends BlockEntityRenderState>
        extends net.minecraft.client.renderer.blockentity.BlockEntityRenderer<T> {
    S createRenderState();

    default void extractRenderState(T blockEntity, S state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderState.extractBase(blockEntity, state, breakProgress);
    }

    void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera);

    /** 1.21.1's call: the state made and filled, then drawn at once. */
    @Override
    default void render(T blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        CameraRenderState camera = CameraRenderState.current();
        S state = this.createRenderState();
        this.extractRenderState(blockEntity, state, partialTick, camera.pos, null);
        // the light 1.21.1 passes is the block's own, which extractBase has set from the world (a renderer may set its own);
        // with no world (a block entity drawn as an item) the light passed is the one there is
        if (blockEntity.getLevel() == null) {
            state.lightCoords = light;
        }
        this.submit(state, poseStack, new SubmitNodeCollector(buffers), camera);
    }

    /** 26.3 asks this without the block entity. */
    default boolean shouldRenderOffScreen() {
        return false;
    }

    @Override
    default boolean shouldRenderOffScreen(T blockEntity) {
        return this.shouldRenderOffScreen();
    }

    @Override
    default int getViewDistance() {
        return 64;
    }

    @Override
    default boolean shouldRender(T blockEntity, Vec3 cameraPosition) {
        return Vec3.atCenterOf(blockEntity.getBlockPos()).closerThan(cameraPosition, this.getViewDistance());
    }
}
