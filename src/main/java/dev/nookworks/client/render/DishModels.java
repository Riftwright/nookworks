package dev.nookworks.client.render;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;

/**
 * Food drawn as the food itself, a small model, where it used to lie as its item's picture (the user, 2026-11-13:
 * ほとんどの料理はアイコンが載るだけで、正直かっこ悪い). Trial of the cooking review, 2026-11-14.
 * <p>
 * The models are found by their files, so a resource pack or an add-on adds its own with no code: for the item
 * {@code namespace:path},
 * <ul>
 * <li>{@code models/dish/namespace/path.json} - set out on a Plate or a Wooden Tray;</li>
 * <li>{@code models/dish_in_pot/namespace/path.json} - lying in an Iron Pot, as an ingredient or as the dish waiting;</li>
 * <li>{@code models/dish_in_pan/namespace/path.json} - the same in a Frying Pan.</li>
 * </ul>
 * in any namespace's assets (ours are all in {@code assets/nookworks}, vanilla's food included; drawn by
 * tools/textures/gen_dishes.py). Each stands in a block's space, centred, its foot at the bottom. An item with no model
 * is drawn as its item, as before.
 */
public final class DishModels {
    /** Where the food is. */
    public enum Place {
        TABLE("dish"),
        POT("dish_in_pot"),
        PAN("dish_in_pan");

        private final String folder;

        Place(String folder) {
            this.folder = folder;
        }
    }

    private static final int[] NO_TINTS = new int[0];
    /** A key to a model file, kept from one reload to the next. */
    private static final Map<Identifier, StandaloneModelKey<BlockStateModelPart>> KEYS = new ConcurrentHashMap<>();
    private static volatile Map<Place, Map<Item, StandaloneModelKey<BlockStateModelPart>>> found = Map.of();

    private DishModels() {}

    /** Every dish model the resources have now, looked up afresh at each reload. */
    public static void registerModels(ModelEvent.RegisterStandalone event) {
        ResourceManager resources = Minecraft.getInstance().getResourceManager();
        Map<Place, Map<Item, StandaloneModelKey<BlockStateModelPart>>> all = new EnumMap<>(Place.class);
        for (Place place : Place.values()) {
            Map<Item, StandaloneModelKey<BlockStateModelPart>> models = new HashMap<>();
            String folder = "models/" + place.folder;
            for (Identifier file : resources.listResources(folder, id -> id.getPath().endsWith(".json")).keySet()) {
                String rest = file.getPath().substring(folder.length() + 1, file.getPath().length() - ".json".length());
                int slash = rest.indexOf('/');
                Identifier id = slash <= 0 ? null : Identifier.tryBuild(rest.substring(0, slash), rest.substring(slash + 1));
                Item item = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                if (item == null) {
                    continue; // a model for another mod's food, and that mod is not here
                }
                Identifier model = Identifier.fromNamespaceAndPath(file.getNamespace(), place.folder + "/" + rest);
                StandaloneModelKey<BlockStateModelPart> key = KEYS.computeIfAbsent(model, m -> new StandaloneModelKey<>(m::toString));
                event.register(key, SimpleUnbakedStandaloneModel.simpleModelWrapper(model));
                models.put(item, key);
            }
            all.put(place, Map.copyOf(models));
        }
        found = all;
    }

    /** The model of {@code stack} set out on a plate or tray, or null for food drawn as an item. */
    public static @Nullable StandaloneModelKey<BlockStateModelPart> of(ItemStack stack) {
        return of(stack, Place.TABLE);
    }

    /** The model of {@code stack} at {@code place}, or null. */
    public static @Nullable StandaloneModelKey<BlockStateModelPart> of(ItemStack stack, Place place) {
        return stack.isEmpty() ? null : found.getOrDefault(place, Map.of()).get(stack.getItem());
    }

    /** A model's size, worked out once from its faces (a reload makes new models, and they are measured anew). */
    private static final Map<BlockStateModelPart, float[]> SIZES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static final float[] NO_SIZE = {8, 8, 4, 0, 0};

    /**
     * How much room dish {@code key} takes: {across, deep, high} in pixels, and how far the middle of that lies from the
     * block's middle, {x, z} (a thing that leans, an onigiri, is not about its foot). For laying several side by side.
     */
    public static float[] size(@Nullable StandaloneModelKey<BlockStateModelPart> key) {
        BlockStateModelPart model = key == null ? null : Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        if (model == null) {
            return NO_SIZE;
        }
        return SIZES.computeIfAbsent(model, part -> {
            float x0 = 1, x1 = 0, z0 = 1, z1 = 0, y = 0;
            boolean any = false;
            for (int side = -1; side < net.minecraft.core.Direction.values().length; side++) {
                for (var quad : part.getQuads(side < 0 ? null : net.minecraft.core.Direction.values()[side])) {
                    for (int vertex = 0; vertex < 4; vertex++) {
                        var at = quad.position(vertex);
                        x0 = Math.min(x0, at.x());
                        x1 = Math.max(x1, at.x());
                        z0 = Math.min(z0, at.z());
                        z1 = Math.max(z1, at.z());
                        y = Math.max(y, at.y());
                        any = true;
                    }
                }
            }
            return any ? new float[] {(x1 - x0) * 16, (z1 - z0) * 16, y * 16, (x0 + x1) * 8 - 8, (z0 + z1) * 8 - 8} : NO_SIZE;
        });
    }

    /** Draws dish {@code key} at the pose's origin, its foot there, centred, at {@code scale}. */
    public static void draw(StandaloneModelKey<BlockStateModelPart> key, float scale, int light, PoseStack pose, SubmitNodeCollector collector) {
        draw(key, scale, NO_TINTS, false, light, pose, collector);
    }

    /** The same, its tinted faces in {@code tints} (by tint index), {@code translucent} for a model with see-through faces. */
    public static void draw(StandaloneModelKey<BlockStateModelPart> key, float scale, int[] tints, boolean translucent, int light,
            PoseStack pose, SubmitNodeCollector collector) {
        BlockStateModelPart model = Minecraft.getInstance().getModelManager().getStandaloneModel(key);
        if (model == null) {
            return;
        }
        pose.pushPose();
        pose.scale(scale, scale, scale);
        pose.translate(-0.5F, 0, -0.5F);
        collector.submitMultiLayerBlockModel(pose, List.of(model), translucent, tints, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
