package dev.nookworks.sorter;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * The two sorters: what they can tell apart. Speed is not theirs but the Sorter Gear's ({@link SorterGear}), which goes
 * in either.
 */
public enum SorterTier implements StringRepresentable {
    /** Nine filter slots, the same item. */
    ITEM("item", 9, false),
    /** Twice the filter, and the filter rules in {@link SorterSettings}: exclude, by tag, exactly. */
    PRECISION("precision", 18, true);

    public static final Codec<SorterTier> CODEC = StringRepresentable.fromEnum(SorterTier::values);

    private final String name;
    /** How many of the filter slots this sorter uses. */
    public final int filterSlots;
    /** Whether it offers the blacklist, tag and component switches. */
    public final boolean configurable;

    SorterTier(String name, int filterSlots, boolean configurable) {
        this.name = name;
        this.filterSlots = filterSlots;
        this.configurable = configurable;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
