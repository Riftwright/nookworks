package dev.nookworks.client.render;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * The dishes set out as dishes (the user, 2026-10-31): udon in its bowl, sliced tonkatsu with cabbage, a pile of karaage
 * with lemon, a glazed donut - drawn as small models ({@code models/block/dish_*}, tools/textures/gen_tableware.py) on a
 * Wooden Tray or a Plate, where any other food lies flat as an item. Each model stands in a block's space, centred, its
 * foot at the bottom.
 */
public final class DishModels {
    private static final int[] NO_TINTS = new int[0];
    private static final Map<String, StandaloneModelKey<BlockStateModelPart>> KEYS = new LinkedHashMap<>();
    private static final StandaloneModelKey<BlockStateModelPart> UDON = key("udon");
    private static final StandaloneModelKey<BlockStateModelPart> TONKATSU = key("tonkatsu");
    private static final StandaloneModelKey<BlockStateModelPart> KARAAGE = key("karaage");
    private static final StandaloneModelKey<BlockStateModelPart> DONUT = key("donut");

    private DishModels() {}

    private static StandaloneModelKey<BlockStateModelPart> key(String dish) {
        StandaloneModelKey<BlockStateModelPart> key = new StandaloneModelKey<>(() -> Nookworks.MODID + ":dish_" + dish);
        KEYS.put(dish, key);
        return key;
    }

    public static void registerModels(ModelEvent.RegisterStandalone event) {
        KEYS.forEach((dish, key) -> event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(Nookworks.id("block/dish_" + dish))));
    }

    /** The dish model of {@code stack}, or null for food drawn as an item. */
    public static @Nullable StandaloneModelKey<BlockStateModelPart> of(ItemStack stack) {
        Item item = stack.getItem();
        return item == ModKitchen.UDON.get() ? UDON : item == ModCooking.TONKATSU.get() ? TONKATSU
                : item == ModCooking.KARAAGE.get() ? KARAAGE : item == ModKitchen.DONUT.get() ? DONUT : null;
    }

    /** Draws dish {@code key} at the pose's origin, its foot there, centred, at {@code scale}. */
    public static void draw(StandaloneModelKey<BlockStateModelPart> key, float scale, int light, PoseStack pose, SubmitNodeCollector collector) {
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        if (model == null) {
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, 0, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(model), false, NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
