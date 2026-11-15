package dev.nookworks.client.render;

import java.util.List;
import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.Serving;
import dev.nookworks.cooking.TrayBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What lies on a Wooden Tray that was lifted with its food, drawn on the tray's item - in a hand, on the ground, in a
 * frame, in a slot - as it lay on the block ({@link TableLots#drawTray}), so the meal is seen being carried.
 * items/wooden_tray.json lays it over the tray's own model, for an item that has things on it.
 */
public final class TrayItemRenderer implements SpecialModelRenderer<ItemContainerContents> {
    /** The special model's type in an item definition. */
    public static final Identifier TYPE = Nookworks.id("tray_contents");

    public record Unbaked() implements SpecialModelRenderer.Unbaked<ItemContainerContents> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<ItemContainerContents> bake(SpecialModelRenderer.BakingContext context) {
            return new TrayItemRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    private TrayItemRenderer() {}

    @Override
    public void submit(@Nullable ItemContainerContents contents, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            int overlayCoords, boolean hasFoil, int outlineColor) {
        if (contents == null) {
            return;
        }
        NonNullList<ItemStack> slots = NonNullList.withSize(TrayBlockEntity.SLOTS, ItemStack.EMPTY);
        contents.copyInto(slots);
        Minecraft minecraft = Minecraft.getInstance();
        List<TableLots.@Nullable Lot> lots = TableLots.lots(slots, slot -> 0F, minecraft.getItemModelResolver(), minecraft.level, 0);
        TableLots.drawTray(lots, Serving.trayPlaces(slots), lightCoords, poseStack, collector);
    }

    /** As far as the tray's block, and what stands on it: a bowl of rice is under half a block high. */
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
