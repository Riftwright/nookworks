package dev.nookworks.storage;

import java.util.Comparator;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.TranslatableEnum;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * How a Storage Barrel's Sort button orders what it gathers, chosen per barrel with the button beside Sort. Whatever
 * the order, each kind of item ends up in as few slots as it fits in. Each runs its own way by itself - count and last
 * added from the most and the latest (descending), the game's order and the mods' from the start (ascending) - and may
 * be turned round (the user, 2026-10-31: 「昇順」「降順」のボタンもあったほうが親切).
 */
public enum SortOrder implements TranslatableEnum {
    /** The most of a thing first. */
    COUNT,
    /** The game's own order of items, vanilla's first (stone, granite, diorite...), much as the creative tabs have it. */
    ID,
    /** Mod by mod, Minecraft first, and by name within each. */
    MOD,
    /** Whatever was put in last first. */
    RECENT;

    public static final SortOrder DEFAULT = ID;

    /** One kind of item gathered from the barrel: how many, and when some last went in. */
    public record Lot(ItemResource resource, long count, long lastAdded) {}

    public SortOrder next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public SortOrder previous() {
        return values()[(this.ordinal() + values().length - 1) % values().length];
    }

    public static SortOrder byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : DEFAULT;
    }

    public String translationKey() {
        return "gui.nookworks.storage.order." + this.name().toLowerCase(Locale.ROOT);
    }

    /** Its name turned round ({@code reversed}: "Count, fewest first"), or as it runs by itself. */
    public String translationKey(boolean reversed) {
        return reversed ? this.translationKey() + ".reversed" : this.translationKey();
    }

    /** Whether it runs from the most or the latest by itself: count and last added. The others start at the start. */
    public boolean descending() {
        return this == COUNT || this == RECENT;
    }

    /** Its name on the config screen too (the chest Sort button's order), as on the barrel's button. */
    @Override
    public Component getTranslatedName() {
        return Component.translatable(this.translationKey());
    }

    /** The lots in this order; ties fall back to the game's item order, then to the items' components. */
    public Comparator<Lot> comparator() {
        return this.comparator(false);
    }

    /**
     * The lots in this order, or in it turned round ({@code reversed}: fewest first, the game's order from its end, the
     * mods Z-A with Minecraft last, first added first). Ties fall back as they do either way: the game's item order, and
     * a plain item before the same item named or enchanted.
     */
    public Comparator<Lot> comparator(boolean reversed) {
        Comparator<Lot> byId = Comparator.comparingInt((Lot lot) -> BuiltInRegistries.ITEM.getId(lot.resource().getItem()))
                .thenComparing(SortOrder::components);
        Comparator<Lot> first = switch (this) {
            case COUNT -> Comparator.comparingLong(Lot::count).reversed();
            case ID -> Comparator.comparingInt((Lot lot) -> BuiltInRegistries.ITEM.getId(lot.resource().getItem()));
            case MOD -> Comparator.comparing((Lot lot) -> mod(lot.resource())).thenComparing(lot -> key(lot.resource()).getPath());
            case RECENT -> Comparator.comparingLong(Lot::lastAdded).reversed();
        };
        if (reversed) {
            first = first.reversed();
        }
        return switch (this) {
            case COUNT, RECENT -> first.thenComparing(byId);
            case ID, MOD -> first.thenComparing(SortOrder::components);
        };
    }

    private static Identifier key(ItemResource resource) {
        return BuiltInRegistries.ITEM.getKey(resource.getItem());
    }

    /** Minecraft sorts before every mod. */
    private static String mod(ItemResource resource) {
        String namespace = key(resource).getNamespace();
        return namespace.equals(Identifier.DEFAULT_NAMESPACE) ? "" : namespace;
    }

    /** The item's own components, as text to order by: a plain one (none of its own) first, before a named or enchanted one. */
    private static String components(Lot lot) {
        var patch = lot.resource().toStack(1).getComponentsPatch();
        return patch.isEmpty() ? "" : patch.toString();
    }
}
