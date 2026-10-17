package dev.nookworks.machine;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the loot tables our recipe pages show can give, and at what odds: the Sifter's tables (ordinary and through
 * the Brass Mesh) and the Vacuum Distiller's. Loot tables live on the server, so the server reads them as its data
 * packs have them - a pack that changes a table, or adds an item to {@code #nookworks:siftable} with a table of its
 * own, is shown as it plays - and sends them to each player ({@code NookworksNetwork}). Without them (no server sent
 * any yet) the tables in the mod jar are read instead. Plain code, no JEI classes, so tests can reach it.
 * <p>
 * A table is read as the Sifter's are written: weighted pools of item entries, each with an optional constant or
 * uniform {@code set_count}. Anything else in an entry is shown as one item at its weight's chance.
 */
public final class LootSummaries {
    /** One thing a table can give: how many, and the chance of it on a roll. */
    public record Drop(ItemStack stack, int min, int max, double chance) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Drop> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Drop::stack,
                ByteBufCodecs.VAR_INT, Drop::min,
                ByteBufCodecs.VAR_INT, Drop::max,
                ByteBufCodecs.DOUBLE, Drop::chance,
                Drop::new);
    }

    /** At most this many tables are taken from the server, and this many drops a table: more is no table of ours. */
    public static final int MOST_TABLES = 4096;
    public static final int MOST_DROPS = 256;
    public static final StreamCodec<RegistryFriendlyByteBuf, Map<Identifier, List<Drop>>> STREAM_CODEC =
            ByteBufCodecs.map(LinkedHashMap::new, Identifier.STREAM_CODEC, Drop.STREAM_CODEC.apply(ByteBufCodecs.list(MOST_DROPS)), MOST_TABLES);

    /** The server's tables as last sent, or null before any came. Set on the client's main thread only. */
    private static Map<Identifier, List<Drop>> received;

    private LootSummaries() {}

    /**
     * A chance as a page shows it: "12.5%", or, under a tenth of a percent, one in so many - "1/990", "1/1,000,000" -
     * where a percent to one place would read 0.0% (End Gravel's dragon egg, one in a million).
     */
    public static String chanceText(double chance) {
        if (chance >= 0.001 || chance <= 0) {
            return String.format(Locale.ROOT, "%.1f%%", chance * 100);
        }
        return String.format(Locale.ROOT, "1/%,d", Math.round(1 / chance));
    }

    /** The Sifter's table for {@code item}, ordinary or through the Brass Mesh. */
    public static Identifier siftingTable(Identifier item, boolean fine) {
        return Nookworks.id((fine ? "sifting_fine/" : "sifting/") + item.getNamespace() + "/" + item.getPath());
    }

    /** What the table {@code key} gives, as the server has it; or as the mod jar has it, before the server has said. */
    public static List<Drop> get(Identifier key) {
        Map<Identifier, List<Drop>> tables = received;
        return tables != null ? tables.getOrDefault(key, List.of()) : bundled(key);
    }

    /**
     * What sifting {@code item} gives, as the Sifter hands it out ({@link SifterBlockEntity#given}): Farmer's Delight's
     * rice and onion in place of ours when it is installed, and nothing of a part of the mod a pack has switched off.
     */
    public static List<Drop> sifted(Identifier item, boolean fine) {
        List<Drop> drops = new ArrayList<>();
        for (Drop drop : get(siftingTable(item, fine))) {
            ItemStack given = SifterBlockEntity.given(drop.stack());
            if (!given.isEmpty()) {
                drops.add(new Drop(given, drop.min(), drop.max(), drop.chance()));
            }
        }
        return drops;
    }

    /** Taken in from the server (see {@code NookworksNetwork}). */
    public static void receive(Map<Identifier, List<Drop>> tables) {
        received = Map.copyOf(tables);
    }

    /** Forgets what the server sent, as when leaving it. */
    public static void forget() {
        received = null;
    }

    /** Every table the pages show, read from the server's data packs as they are now. */
    public static Map<Identifier, List<Drop>> collect(MinecraftServer server) {
        ResourceManager resources = server.getResourceManager();
        Map<Identifier, List<Drop>> tables = new LinkedHashMap<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(SifterBlockEntity.SIFTABLE)) {
            Identifier id = holder.unwrapKey().orElseThrow().identifier();
            for (boolean fine : new boolean[] {false, true}) {
                put(tables, resources, siftingTable(id, fine));
            }
        }
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            var distil = holder.getData(MercuryRecipes.DISTILLATION);
            if (distil != null && distil.lootTable().isPresent()) {
                put(tables, resources, distil.lootTable().get().identifier());
            }
        });
        return tables;
    }

    private static void put(Map<Identifier, List<Drop>> tables, ResourceManager resources, Identifier key) {
        if (tables.size() >= MOST_TABLES || tables.containsKey(key)) {
            return;
        }
        Optional<Resource> file = resources.getResource(
                Identifier.fromNamespaceAndPath(key.getNamespace(), "loot_table/" + key.getPath() + ".json"));
        if (file.isEmpty()) {
            return;
        }
        try (Reader reader = file.get().openAsReader()) {
            List<Drop> drops = parse(JsonParser.parseReader(reader).getAsJsonObject());
            if (!drops.isEmpty()) {
                tables.put(key, drops.size() > MOST_DROPS ? List.copyOf(drops.subList(0, MOST_DROPS)) : drops);
            }
        } catch (Exception e) {
            Nookworks.LOGGER.warn("Could not read loot table {} for the recipe pages", key, e);
        }
    }

    /** The table {@code key} as the mod jar has it. Public for tests. */
    public static List<Drop> bundled(Identifier key) {
        String path = "/data/" + key.getNamespace() + "/loot_table/" + key.getPath() + ".json";
        try (InputStream in = LootSummaries.class.getResourceAsStream(path)) {
            if (in == null) {
                return List.of();
            }
            return parse(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
        } catch (Exception e) {
            Nookworks.LOGGER.warn("Could not read loot table {} for the recipe pages", path, e);
            return List.of();
        }
    }

    /** Weighted pools of item entries, with an optional constant or uniform set_count. Public for tests. */
    public static List<Drop> parse(JsonObject table) {
        List<Drop> drops = new ArrayList<>();
        if (!table.has("pools")) {
            return drops;
        }
        for (JsonElement poolEl : table.getAsJsonArray("pools")) {
            JsonArray entries = poolEl.getAsJsonObject().getAsJsonArray("entries");
            if (entries == null) {
                continue;
            }
            double total = 0;
            for (JsonElement e : entries) {
                total += weight(e.getAsJsonObject());
            }
            for (JsonElement e : entries) {
                JsonObject entry = e.getAsJsonObject();
                if (!"minecraft:item".equals(entry.get("type").getAsString())) {
                    continue;
                }
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(entry.get("name").getAsString()));
                if (item.isEmpty() || item.get() == Items.AIR) {
                    continue; // another mod's item, not installed here; or air, a roll of nothing (r15: sent as an item, it cut everyone off a server)
                }
                int[] range = countRange(entry);
                drops.add(new Drop(new ItemStack(item.get()), range[0], range[1], total > 0 ? weight(entry) / total : 0));
            }
        }
        return drops;
    }

    private static int[] countRange(JsonObject entry) {
        int[] range = {1, 1};
        JsonElement modifier = entry.get("modifier"); // 26.3: one function, or a list of them
        if (modifier == null) {
            return range;
        }
        for (JsonElement f : modifier.isJsonArray() ? modifier.getAsJsonArray() : List.of(modifier)) {
            if (!f.isJsonObject() || !"minecraft:set_count".equals(f.getAsJsonObject().get("type").getAsString())) {
                continue; // another function, or one named rather than written out
            }
            JsonObject fn = f.getAsJsonObject();
            JsonElement count = fn.get("count");
            if (count.isJsonPrimitive()) {
                range[0] = range[1] = count.getAsInt();
            } else if (count.getAsJsonObject().has("min")) {
                range[0] = count.getAsJsonObject().get("min").getAsInt();
                range[1] = count.getAsJsonObject().get("max").getAsInt();
            }
        }
        return range;
    }

    private static double weight(JsonObject entry) {
        return entry.has("weight") ? entry.get("weight").getAsDouble() : 1.0;
    }
}
