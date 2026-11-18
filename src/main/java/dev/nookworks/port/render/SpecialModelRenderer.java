package dev.nookworks.port.render;

import java.util.function.Consumer;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3's {@code SpecialModelRenderer<T>}: an item drawn by code, from an argument taken off the stack, in a block's space
 * (0 to 1). 1.21.1 draws such an item with a {@code BlockEntityWithoutLevelRenderer} under an item model whose parent is
 * {@code builtin/entity}; the port's one is {@link SpecialItems}, which bakes these from the codecs registered with
 * {@link RegisterSpecialModelRendererEvent} and calls them for the items listed there.
 */
public interface SpecialModelRenderer<T> {
    void submit(@Nullable T argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor);

    void getExtents(Consumer<Vector3fc> output);

    @Nullable T extractArgument(ItemStack stack);

    interface BakingContext {
        EntityModelSet entityModelSet();

        SpriteGetter sprites();

        /** The game's models and atlases, as they are now. */
        static BakingContext current() {
            return new BakingContext() {
                @Override
                public EntityModelSet entityModelSet() {
                    return Minecraft.getInstance().getEntityModels();
                }

                @Override
                public SpriteGetter sprites() {
                    return SpriteGetter.atlases();
                }
            };
        }
    }

    interface Unbaked<T> {
        @Nullable SpecialModelRenderer<T> bake(BakingContext context);

        MapCodec<? extends Unbaked<T>> type();
    }
}
