package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.item.ItemStack;

/** 26.3's {@code NoDataSpecialModelRenderer}: a {@link SpecialModelRenderer} that takes nothing off the stack. */
public interface NoDataSpecialModelRenderer extends SpecialModelRenderer<Void> {
    @Override
    default @Nullable Void extractArgument(ItemStack stack) {
        return null;
    }

    @Override
    default void submit(@Nullable Void argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
            int overlayCoords, boolean hasFoil, int outlineColor) {
        this.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, hasFoil, outlineColor);
    }

    void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil,
            int outlineColor);

    interface Unbaked extends SpecialModelRenderer.Unbaked<Void> {
        @Override
        MapCodec<? extends NoDataSpecialModelRenderer.Unbaked> type();
    }
}
