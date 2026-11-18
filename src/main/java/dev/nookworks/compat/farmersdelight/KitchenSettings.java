package dev.nookworks.compat.farmersdelight;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * How the two kitchens meet, as {@code data/nookworks/farmersdelight/kitchen.json} says (a data pack may put its own
 * file there): which items are the same food in both mods, which mod's twin a shared station gives, which of Farmer's
 * Delight's pot dishes our Frying Pan makes rather than the Iron Pot, and the recipes of Farmer's Delight's that the
 * owner's settled rules stand down (the flour bakery's cake, bread from wheat).
 *
 * @param twins     each item of a pair, mapped to the other
 * @param prefer    the mod whose twin comes out of a furnace, smoker or campfire when both have a recipe there
 * @param pan       Farmer's Delight pot recipes cooked in our Frying Pan's oil
 * @param notCrossed recipes (either mod's) never brought over to the other's pot
 * @param bakery    Farmer's Delight recipes that stand down while the flour bakery is on
 * @param noWheatBread those that stand down while bread is not made from wheat
 */
public record KitchenSettings(Map<ResourceLocation, ResourceLocation> twins, String prefer, Set<ResourceLocation> pan,
        Set<ResourceLocation> notCrossed, Set<ResourceLocation> bakery, Set<ResourceLocation> noWheatBread, int water, int oil,
        float experience) {
    public static final ResourceLocation FILE = Nookworks.id("farmersdelight/kitchen.json");
    public static final KitchenSettings NONE = new KitchenSettings(Map.of(), FarmersDelight.MODID, Set.of(), Set.of(), Set.of(),
            Set.of(), 250, 25, 0.35F);

    /** The file the server's data packs give, or {@link #NONE} without one. */
    public static KitchenSettings read(ResourceManager resources) {
        var found = resources.getResource(FILE);
        if (found.isEmpty()) {
            return NONE;
        }
        try (Reader reader = found.get().openAsReader()) {
            return of(JsonParser.parseReader(reader).getAsJsonObject());
        } catch (Exception e) {
            Nookworks.LOGGER.warn("Could not read {}: the kitchens are left apart", FILE, e);
            return NONE;
        }
    }

    static KitchenSettings of(JsonObject json) {
        Map<ResourceLocation, ResourceLocation> twins = new LinkedHashMap<>();
        for (JsonElement pair : array(json, "twins")) {
            JsonArray two = pair.getAsJsonArray();
            ResourceLocation a = ResourceLocation.parse(two.get(0).getAsString());
            ResourceLocation b = ResourceLocation.parse(two.get(1).getAsString());
            twins.put(a, b);
            twins.put(b, a);
        }
        return new KitchenSettings(Map.copyOf(twins), json.has("prefer") ? json.get("prefer").getAsString() : FarmersDelight.MODID,
                ids(json, "pan"), ids(json, "not_crossed"), ids(json, "bakery_stands_down"), ids(json, "no_wheat_bread_stands_down"),
                json.has("water") ? json.get("water").getAsInt() : 250, json.has("oil") ? json.get("oil").getAsInt() : 25,
                json.has("experience") ? json.get("experience").getAsFloat() : 0.35F);
    }

    private static List<JsonElement> array(JsonObject json, String key) {
        return json.has(key) ? StreamSupport.stream(json.getAsJsonArray(key).spliterator(), false).toList() : List.of();
    }

    private static Set<ResourceLocation> ids(JsonObject json, String key) {
        return array(json, key).stream().map(e -> ResourceLocation.parse(e.getAsString())).collect(Collectors.toUnmodifiableSet());
    }
}
