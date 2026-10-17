package dev.nookworks.feature;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

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
 */
public final class Features {
    private static final JsonObject FILE = file();
    /** Each part, in the file's order, with the ids of its items. */
    public static final Map<String, List<Identifier>> PARTS = parts();
    /** What a part (by name) or an item (by id) needs on besides its own part. */
    public static final Map<String, List<String>> NEEDS = needs();

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

    private static boolean switchedOn(String name) {
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

    /** Whether {@code item} can be had: of no part, or of a part that is on, and the parts it needs on. */
    public static boolean enabled(Item item) {
        return enabled(item, Features::switchedOn);
    }

    /** Whether {@code item} can be had, given which parts are switched on. Public for tests. */
    public static boolean enabled(Item item, Predicate<String> switchedOn) {
        String part = partOf(item);
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return (part == null || enabled(part, switchedOn))
                && NEEDS.getOrDefault(id, List.of()).stream().allMatch(need -> enabled(need, switchedOn));
    }

    public static boolean enabled(ItemStack stack) {
        return stack.isEmpty() || enabled(stack.getItem());
    }

    /** Every item of a part that is off, or that needs one. */
    public static List<Item> disabledItems() {
        List<Item> items = new ArrayList<>();
        PARTS.values().forEach(ids -> ids.forEach(id -> BuiltInRegistries.ITEM.getOptional(id).filter(item -> !enabled(item)).ifPresent(items::add)));
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

    /** Whether any part is off: the creative tabs and JEI need do nothing otherwise. */
    public static boolean anyOff() {
        return PARTS.keySet().stream().anyMatch(name -> !enabled(name));
    }
}
