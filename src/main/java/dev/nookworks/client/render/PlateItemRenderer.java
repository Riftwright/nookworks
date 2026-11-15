package dev.nookworks.client.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What lies on a Plate that was lifted with its food, drawn on the plate's item - in a hand, on the ground, in a frame,
 * in a slot - as it lay on the block ({@link PlateRenderer#draw}). items/plate.json lays it over the plate's own model,
 * for an item that has food on it.
 */
public final class PlateItemRenderer implements SpecialModelRenderer<ItemContainerContents> {
    /** The special model's type in an item definition. */
    public static final Identifier TYPE = Nookworks.id("plate_contents");
    /** The plate's top, where the food sits. */
    private static final float TOP = 1.0F / 16;

    public record Unbaked() implements SpecialModelRenderer.Unbaked<ItemContainerContents> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<ItemContainerContents> bake(SpecialModelRenderer.BakingContext context) {
            return new PlateItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private PlateItemRenderer() {}

    @Override
    public void submit(@Nullable ItemContainerContents contents, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            int overlayCoords, boolean hasFoil, int outlineColor) {
        ItemStack food = contents == null ? ItemStack.EMPTY : contents.copyOne();
        if (food.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        TableLots.Lot lot = TableLots.lot(food, 0, minecraft.getItemModelResolver(), minecraft.level, 0);
        lot.plated = false; // the item's plate is its plate
        poseStack.pushPose();
        poseStack.translate(0.5F, TOP, 0.5F);
        PlateRenderer.draw(lot, lightCoords, poseStack, collector);
        poseStack.popPose();
    }

    /** As far as the plate's block, and what stands on it. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float x : new float[] {0, 1}) {
            for (float y : new float[] {0, 0.5F}) {
                for (float z : new float[] {0, 1}) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }

    @Override
    public @Nullable ItemContainerContents extractArgument(ItemStack stack) {
        return stack.get(DataComponents.CONTAINER);
    }
}
