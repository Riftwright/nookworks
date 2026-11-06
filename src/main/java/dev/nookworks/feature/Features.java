package dev.nookworks.feature;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The parts of the mod a modpack can switch off, each by a start-up setting ({@code [features]} in
 * {@code nookworks-startup.toml}), and a world can leave out when it is made ({@link WorldFeatures}), and the items that
 * are each part's - read from {@code /nookworks/features.json}, the one list the mod and the tools that write the recipe
 * and advancement conditions share.
 * <p>
 * Switched off, a part's items are made by no recipe and earn no advancement (both by a {@code nookworks:feature}
 * condition the tools put in their files), sit in no creative tab, are hidden in JEI, and come out of no sifting or
 * trade. What is already in a world stays and works: switching a part off stops it being had, not used.
 * <p>
 * A part or an item can need another part as well (the file's {@code _needs}): the Barter Terminal takes its piglin in
 * by a Mob Bottle, the Absorption Pad is made with a Magnet. With that part off it goes too, as if switched off itself.
 * <p>
 * And an item that nothing reaches any more with the parts that are off - caustic soda with the Electrolytic Cell off,
 * and the paper made with it - goes too ({@link PartGraph}, the user, 2026-11-01): every part can be switched off, and
 * what it takes with it is worked out, not listed. The parts list shows it before it is saved.
 */
public final class Features {
    private static final JsonObject FILE = file();
    /** Each part, in the file's order, with the ids of its items. */
    public static final Map<String, List<Identifier>> PARTS = parts();
    /** What a part (by name) or an item (by id) needs on besides its own part. */
    public static final Map<String, List<String>> NEEDS = needs();
    /** The part each item of a part is of, by id. */
    private static final Map<String, String> PART_OF = partOf();
    /** How the parts list groups the parts, each group's parts in order (the file's {@code _groups}). */
    public static final Map<String, List<String>> GROUPS = groups();

    /** The items lost, as worked out at {@code generation} of the parts' state ({@link WorldFeatures#generation()}). */
    private record Lost(int generation, Set<String> items) {}

    private static volatile @Nullable Lost lostNow;

    private Features() {}

    private static JsonObject file() {
        try (var in = Features.class.getResourceAsStream("/nookworks/features.json")) {
            if (in == null) {
                throw new IllegalStateException("no /nookworks/features.json in the mod jar");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException("cannot read /nookworks/features.json", e);
        }
    }

    private static Map<String, List<Identifier>> parts() {
        Map<String, List<Identifier>> parts = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> part : FILE.entrySet()) {
            if (part.getKey().startsWith("_")) {
                continue; // a note for whoever reads the file, or the needs
            }
            List<Identifier> items = new ArrayList<>();
            part.getValue().getAsJsonArray().forEach(id -> items.add(Identifier.parse(id.getAsString())));
            parts.put(part.getKey(), List.copyOf(items));
        }
        return Collections.unmodifiableMap(parts);
    }

    private static Map<String, List<String>> needs() {
        Map<String, List<String>> needs = new LinkedHashMap<>();
        if (FILE.has("_needs")) {
            for (Map.Entry<String, JsonElement> entry : FILE.getAsJsonObject("_needs").entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(part -> parts.add(part.getAsString()));
                needs.put(entry.getKey(), List.copyOf(parts));
            }
        }
        return Collections.unmodifiableMap(needs);
    }

    private static Map<String, String> partOf() {
        Map<String, String> map = new HashMap<>();
        PARTS.forEach((part, ids) -> ids.forEach(id -> map.put(id.toString(), part)));
        return Collections.unmodifiableMap(map);
    }

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        if (FILE.has("_groups")) {
            for (Map.Entry<String, JsonElement> entry : FILE.getAsJsonObject("_groups").entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue;
                }
                List<String> parts = new ArrayList<>();
                entry.getValue().getAsJsonArray().forEach(part -> parts.add(part.getAsString()));
                groups.put(entry.getKey(), List.copyOf(parts));
            }
        }
        return Collections.unmodifiableMap(groups);
    }

    /** Whether the part {@code name} is on; a name that is no part is (for a data pack's condition naming one). */
    public static boolean enabled(String name) {
        return enabled(name, Features::switchedOn);
    }

    /**
     * Whether the part {@code name} is on, given which parts are switched on ({@code switchedOn}): its own switch, and
     * those of the parts it needs. Public for tests.
     */
    public static boolean enabled(String name, Predicate<String> switchedOn) {
        return switchedOn.test(name) && NEEDS.getOrDefault(name, List.of()).stream().allMatch(need -> enabled(need, switchedOn));
    }

    /** Whether part {@code name}'s own switch is on now (its needs not asked). */
    public static boolean switchedOn(String name) {
        return WorldFeatures.switchedOn(name); // the start-up config's switch, and the world's choice under it
    }

    /** The part {@code item} is of, or null for an item of no part (always there). */
    public static String partOf(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        for (Map.Entry<String, List<Identifier>> part : PARTS.entrySet()) {
            if (part.getValue().contains(id)) {
                return part.getKey();
            }
        }
        return null;
    }

    /**
     * Whether {@code item} can be had: of no part, or of a part that is on, and the parts it needs on; and something
     * still reaches it ({@link PartGraph}).
     */
    public static boolean enabled(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return enabledByPart(id, Features::switchedOn) && !lost().contains(id) && addOnsHave(item);
    }

    /** Add-ons' word on their own items ({@link #addOnParts}). */
    private static final List<Predicate<Item>> ADD_ON_PARTS = new CopyOnWriteArrayList<>();

    /**
     * For add-ons with parts of their own to switch off: {@code enabled} answers false for an item of theirs a switched-off
     * part takes (and true for every other item, ours and other mods'). What we hand out by our own tables - sifting, the
     * Retort's reagents, JEI's pages of our machines - then leaves it out, as it leaves out ours (r21, 2026-11-06:
     * nookTechnology's Limestone Powder still came out of Crude Salt with its steelworks off).
     */
    public static void addOnParts(Predicate<Item> enabled) {
        ADD_ON_PARTS.add(enabled);
    }

    private static boolean addOnsHave(Item item) {
        for (Predicate<Item> test : ADD_ON_PARTS) {
            if (!test.test(item)) {
                return false;
            }
        }
        return true;
    }

    /** Whether {@code item} can be had, given which parts are switched on. Public for tests. */
    public static boolean enabled(Item item, Predicate<String> switchedOn) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return enabledByPart(id, switchedOn) && !PartGraph.lost(switchedOn, WorldFeatures.kept()).contains(id);
    }

    /**
     * Whether the item {@code id} is not taken by a switch: of no part, or of one that is on, and the parts it needs on
     * (what it is made from not asked).
     */
    public static boolean enabledByPart(String id, Predicate<String> switchedOn) {
        String part = PART_OF.get(id);
        List<String> needs = NEEDS.get(id);
        return (part == null || enabled(part, switchedOn)) && (needs == null || needs.stream().allMatch(need -> enabled(need, switchedOn)));
    }

    /** Whether the item {@code id} can be had now, as {@link #enabled(Item)} asks it by the item; an id of no item of ours can. */
    public static boolean available(String id) {
        return enabledByPart(id, Features::switchedOn) && !lost().contains(id);
    }

    /**
     * The items, each still on by its own part, that nothing reaches with the parts now on ({@link PartGraph#lost}),
     * worked out again only when the parts' state has changed since ({@link WorldFeatures#generation()}): from one look
     * at it, taken after its generation is read, so a change while it is worked out is not kept under the old one.
     */
    public static Set<String> lost() {
        int generation = WorldFeatures.generation();
        Lost known = lostNow;
        if (known != null && known.generation() == generation) {
            return known.items();
        }
        Map<String, Boolean> on = new HashMap<>();
        PARTS.keySet().forEach(part -> on.put(part, switchedOn(part)));
        Set<String> items = PartGraph.lost(part -> on.computeIfAbsent(part, Features::switchedOn), WorldFeatures.kept());
        lostNow = new Lost(generation, items);
        return items;
    }

    public static boolean enabled(ItemStack stack) {
        return stack.isEmpty() || enabled(stack.getItem());
    }

    /** Every item of ours that cannot be had: of a part that is off, or that needs one, or that nothing reaches any more. */
    public static List<Item> disabledItems() {
        List<Item> items = new ArrayList<>();
        PartGraph.ITEMS.forEach(id -> BuiltInRegistries.ITEM.getOptional(Identifier.parse(id)).filter(item -> !enabled(item)).ifPresent(items::add));
        return items;
    }

    /** A creative tab's contents without the items of a part that is off. */
    public static CreativeModeTab.DisplayItemsGenerator shown(
            CreativeModeTab.DisplayItemsGenerator generator) {
        return shown(generator, Features::enabled);
    }

    /** A creative tab's contents, only what {@code allowed} lets through. Public for tests. */
    public static CreativeModeTab.DisplayItemsGenerator shown(
            CreativeModeTab.DisplayItemsGenerator generator, Predicate<ItemStack> allowed) {
        return (parameters, output) -> generator.accept(parameters, (stack, visibility) -> {
            if (allowed.test(stack)) {
                output.accept(stack, visibility);
            }
        });
    }
}
