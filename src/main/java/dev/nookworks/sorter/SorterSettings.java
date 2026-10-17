package dev.nookworks.sorter;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How a Precision Sorter decides what counts as a match: its Exclude and Exact switches. Plain sorters behave as if both
 * are off: same item, components ignored, matches go to the registered side. Sorting by tag or by mod is not a switch
 * but entries of their own ({@link SorterTags}), each for the one tag or mod the player picked.
 */
public final class SorterSettings {
    public static final int COUNT = 3;
    public static final int BLACKLIST = 0;
    /**
     * The Tags switch of 0.2.0, which made every entry stand for all of its tags at once (an iron ingot caught diamonds,
     * which share the beacon's tag with it). Read from an older sorter only, to turn its entries into tag entries.
     */
    public static final int MATCH_TAGS = 1;
    public static final int MATCH_COMPONENTS = 2;

    private final boolean[] flags = new boolean[COUNT];

    public boolean get(int flag) {
        return this.flags[flag];
    }

    public void toggle(int flag) {
        this.flags[flag] = !this.flags[flag];
    }

    public void clear(int flag) {
        this.flags[flag] = false;
    }

    /**
     * Whether a filter entry catches this item.
     * <p>
     * A tag entry catches everything in its tag; a mod entry, every item of its mod (by the namespace of the item's
     * id); any other entry, its item. Components - durability, enchantments, a custom name - are ignored unless
     * {@link #MATCH_COMPONENTS} is on, so a worn pickaxe sorts with a fresh one by default; on, a candidate has to be in
     * the same condition as the entry too (the same changes from its item's defaults: a tag entry of a fresh iron
     * pickaxe catches every fresh pickaxe of its tag, and no worn or enchanted one; a mod entry, every fresh item of
     * its mod).
     */
    public boolean matches(ItemStack candidate, ItemStack entry) {
        if (entry.isEmpty() || candidate.isEmpty()) {
            return false;
        }
        TagKey<Item> tag = SorterTags.of(entry);
        if (tag != null) {
            return candidate.is(tag) && (!this.get(MATCH_COMPONENTS) || sameCondition(candidate, entry));
        }
        String mod = SorterTags.modOf(entry);
        if (mod != null) {
            return SorterTags.namespace(candidate).equals(mod) && (!this.get(MATCH_COMPONENTS) || sameCondition(candidate, entry));
        }
        return this.get(MATCH_COMPONENTS)
                ? ItemStack.isSameItemSameComponents(candidate, entry)
                : ItemStack.isSameItem(candidate, entry);
    }

    /** Whether {@code candidate} has the same changes from its item's defaults as a tag or mod entry's sample has. */
    private static boolean sameCondition(ItemStack candidate, ItemStack entry) {
        ItemStack sample = entry.copy();
        sample.remove(ModDataComponents.SORTER_TAG.get());
        sample.remove(ModDataComponents.SORTER_MOD.get());
        return candidate.getComponentsPatch().equals(sample.getComponentsPatch());
    }

    /** Turns "the filter caught it" into "it goes to the registered side". */
    public boolean sortsAsRegistered(boolean caught) {
        return this.get(BLACKLIST) != caught;
    }

    public void save(ValueOutput output) {
        for (int i = 0; i < COUNT; i++) {
            output.putBoolean("Flag" + i, this.flags[i]);
        }
    }

    public void load(ValueInput input) {
        for (int i = 0; i < COUNT; i++) {
            this.flags[i] = input.getBooleanOr("Flag" + i, false);
        }
    }
}
