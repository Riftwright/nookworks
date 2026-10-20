package dev.nookworks.sorter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.Tags;

/**
 * Tag entries in a Precision Sorter's filter. The player picks a sample item, the screen lists its tags, and each tag
 * they tick goes in the filter as its own entry: the sample's picture marked "#", carrying the tag
 * ({@link ModDataComponents#SORTER_TAG}). An entry like that catches everything in its tag and nothing else - an iron
 * ingot's {@code c:ingots/iron}, and not the diamond that shares the beacon's tag with it.
 * <p>
 * Above the tags the screen offers the sample's mod: ticked, it goes in as a mod entry, marked "@" (as JEI's search
 * writes a mod), carrying the namespace of the sample's id ({@link ModDataComponents#SORTER_MOD}). It catches every item
 * of that mod - Minecraft's own for a vanilla sample - so vanilla things can be sorted from Nookworks' (the user,
 * 2026-10-06).
 */
public final class SorterTags {
    /**
     * Tags bigger than this are not offered. Vanilla has catch-all tags that hold most of the game -
     * {@code minecraft:sulfur_cube_swallowable} puts cobblestone and planks in the same bucket - and sorting on one of
     * those would send everything the same way. A real sorting category is small. Our own tags are offered whatever
     * their size: they are made to sort by (#nookworks:crushable, recyclable, compressible - the user, 2026-10-18).
     */
    public static final int MAX_SIZE = 128;

    /** The order the screen lists a sample's tags in: the common {@code c:} ones first, then vanilla's, then the rest. */
    private static final Comparator<TagKey<Item>> ORDER = Comparator
            .comparingInt((TagKey<Item> tag) -> switch (tag.location().getNamespace()) {
                case "c" -> 0;
                case "minecraft" -> 1;
                default -> 2;
            })
            .thenComparing(tag -> tag.location().getNamespace())
            .thenComparing(tag -> tag.location().getPath());

    /** The tags of {@code sample} that can be sorted by, in the screen's order. Empty for an empty sample. */
    public static List<TagKey<Item>> choices(ItemStack sample) {
        if (sample.isEmpty()) {
            return List.of();
        }
        return sample.typeHolder().tags().filter(SorterTags::sortable).sorted(ORDER).toList();
    }

    /**
     * Whether a tag is worth listing to sort by: something in it, not too much - and not one of vanilla's tags of a
     * single thing (repairs_iron_armor, iron_tool_materials: the game's own bookkeeping), which sorts nothing the item
     * itself would not (the user, 2026-10-01). A common {@code c:} tag of one stays: in a pack, other mods' iron joins it.
     */
    public static boolean sortable(TagKey<Item> tag) {
        int size = size(tag);
        String namespace = tag.location().getNamespace();
        return size > 0 && (size <= MAX_SIZE || namespace.equals(Nookworks.MODID)) && !(size == 1 && namespace.equals("minecraft"));
    }

    /** How many items the tag holds. */
    public static int size(TagKey<Item> tag) {
        return BuiltInRegistries.ITEM.get(tag).map(members -> members.size()).orElse(0);
    }

    /** Up to {@code max} of the tag's items, for its tooltip. */
    public static List<ItemStack> members(TagKey<Item> tag, int max) {
        return BuiltInRegistries.ITEM.get(tag).map(members -> members.stream().limit(max).map(item -> new ItemStack(item)).toList())
                .orElse(List.of());
    }

    /** The filter entry for {@code tag}, drawn as {@code sample}. */
    public static ItemStack entry(ItemStack sample, TagKey<Item> tag) {
        ItemStack entry = sample.copyWithCount(1);
        entry.set(ModDataComponents.SORTER_TAG.get(), tag);
        return entry;
    }

    /** The tag a filter entry stands for, or null for an entry that is its item alone. */
    public static @Nullable TagKey<Item> of(ItemStack entry) {
        return entry.get(ModDataComponents.SORTER_TAG.get());
    }

    /**
     * The tag's name as the game names it: the common tags have names (NeoForge's "Iron Ingots", which we give a
     * Japanese name too); one without is named by what is in it - "Oak Planks and 11 more" - its id in the tooltip.
     * (It was named by the last part of its id, "beacon_payment_items", which says little in English and nothing in
     * Japanese.)
     */
    public static Component name(TagKey<Item> tag) {
        String key = Tags.getTagTranslationKey(tag);
        if (Language.getInstance().has(key)) {
            return Component.translatable(key);
        }
        List<ItemStack> first = members(tag, 1);
        if (first.isEmpty()) {
            String path = tag.location().getPath();
            return Component.literal(path.substring(path.lastIndexOf('/') + 1));
        }
        int more = size(tag) - 1;
        return more > 0 ? Component.translatable("gui.nookworks.sorter.tag_like", first.get(0).getHoverName(), more)
                : Component.translatable("gui.nookworks.sorter.tag_of", first.get(0).getHoverName());
    }

    /**
     * The one tag an entry of a 0.2.0 Precision Sorter with its Tags switch on stood for, where there is a good one: the
     * most particular common tag ({@code c:ingots/iron} over {@code c:ingots}), else the smallest vanilla tag of two or
     * more ({@code minecraft:planks}). Null when there is none - the entry is then its item alone.
     */
    public static @Nullable TagKey<Item> bestFor(ItemStack stack) {
        List<TagKey<Item>> tags = choices(stack);
        TagKey<Item> common = tags.stream().filter(tag -> tag.location().getNamespace().equals("c"))
                .max(Comparator.comparingInt((TagKey<Item> tag) -> tag.location().getPath().split("/").length)
                        .thenComparing(tag -> -size(tag)))
                .orElse(null);
        if (common != null) {
            return common;
        }
        return tags.stream().filter(tag -> size(tag) >= 2).min(Comparator.comparingInt(SorterTags::size)).orElse(null);
    }

    // ---- mod entries ----

    /** How many items each mod has, as counted: see {@link #modSize}. */
    private static final Map<String, Integer> MOD_SIZES = new ConcurrentHashMap<>();

    /** The mod entry for {@code sample}'s mod, drawn as {@code sample}. */
    public static ItemStack modEntry(ItemStack sample) {
        ItemStack entry = sample.copyWithCount(1);
        entry.set(ModDataComponents.SORTER_MOD.get(), namespace(sample));
        return entry;
    }

    /** The mod a filter entry stands for, by its namespace, or null for an entry that is not a mod entry. */
    public static @Nullable String modOf(ItemStack entry) {
        return entry.get(ModDataComponents.SORTER_MOD.get());
    }

    /** The namespace of an item's id, which is its mod's: "minecraft" for vanilla's, "nookworks" for ours. */
    public static String namespace(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    }

    /** Every item of the mod, in the game's own order. */
    private static Stream<Item> modItems(String namespace) {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR && BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(namespace));
    }

    /**
     * How many items the mod has: counted once, the item registry staying as it is while the game runs (in a big pack
     * it is tens of thousands of items, and the picker shows the count every frame).
     */
    public static int modSize(String namespace) {
        return MOD_SIZES.computeIfAbsent(namespace, n -> (int) modItems(n).count());
    }

    /** Up to {@code max} of the mod's items, for its tooltip. */
    public static List<ItemStack> modMembers(String namespace, int max) {
        return modItems(namespace).limit(max).map(ItemStack::new).toList();
    }

    /**
     * The mod's name as the mod list gives it ("Minecraft", "Nookworks"), or its namespace where no mod of that id is
     * installed (one taken out of the pack since its entry was made).
     */
    public static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(mod -> mod.getModInfo().getDisplayName()).orElse(namespace);
    }

    private SorterTags() {}
}
