package dev.nookworks.client.render;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.port.render.ChestModel;
import dev.nookworks.port.render.Sheets;
import dev.nookworks.port.render.ChestSpecialRenderer;
import dev.nookworks.port.render.NoDataSpecialModelRenderer;
import dev.nookworks.port.render.SpecialModelRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * The Repair Box's item: drawn as vanilla draws a chest's ({@link ChestSpecialRenderer}), but with the box's own model,
 * the handle on its lid and all ({@link RepairBoxModel}) - vanilla's "minecraft:chest" item model knows only the plain
 * chest's. items/repair_box.json names it as {@code nookworks:repair_box}.
 */
public final class RepairBoxItemRenderer {
    private RepairBoxItemRenderer() {}

    public static final ResourceLocation TYPE = Nookworks.id("repair_box");

    public record Unbaked() implements NoDataSpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Void> bake(SpecialModelRenderer.BakingContext context) {
            ChestModel model = new ChestModel(context.entityModelSet().bakeLayer(RepairBoxModel.LAYERS.single()));
            return new ChestSpecialRenderer(dev.nookworks.port.render.SpriteGetter.atlases(), model, Sheets.CHEST_MAPPER.apply(Nookworks.id("repair_box")), 0.0F);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
