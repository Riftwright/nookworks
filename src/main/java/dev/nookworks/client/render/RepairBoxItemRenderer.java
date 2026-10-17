package dev.nookworks.client.render;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.special.ChestSpecialRenderer;
import net.minecraft.client.renderer.special.NoDataSpecialModelRenderer;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.resources.Identifier;

/**
 * The Repair Box's item: drawn as vanilla draws a chest's ({@link ChestSpecialRenderer}), but with the box's own model,
 * the handle on its lid and all ({@link RepairBoxModel}) - vanilla's "minecraft:chest" item model knows only the plain
 * chest's. items/repair_box.json names it as {@code nookworks:repair_box}.
 */
public final class RepairBoxItemRenderer {
    private RepairBoxItemRenderer() {}

    public static final Identifier TYPE = Nookworks.id("repair_box");

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            ChestModel model = new ChestModel(context.entityModelSet().bakeLayer(RepairBoxModel.LAYERS.single()));
            return new ChestSpecialRenderer(context.sprites(), model, Sheets.CHEST_MAPPER.apply(Nookworks.id("repair_box")), 0.0F);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
