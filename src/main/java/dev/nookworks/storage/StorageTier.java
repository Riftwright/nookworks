package dev.nookworks.storage;

import java.util.Arrays;

import com.mojang.serialization.Codec;

import net.minecraft.util.StringRepresentable;

/**
 * Storage Barrel tiers: slot count, and how many of the item's own stacks one slot holds. Iron is a double chest
 * in one block; each step up is a real step, and Netherite, which costs a netherite block, is the warehouse.
 * <p>
 * A barrel broken spills its contents (only the Crate keeps them), which is why capacity is kept to something that
 * can be spilled - see StorageSpill. One holding more stacks than storageBreakStackLimit cannot be broken by hand;
 * taken away by anything else, it leaves as one item with everything in it, as a shulker box does
 * (StorageBlockEntity.preRemoveSideEffects).
 */
public enum StorageTier implements StringRepresentable {
    IRON("iron", 27, 2),
    GOLD("gold", 54, 4),
    DIAMOND("diamond", 81, 16),
    NETHERITE("netherite", 108, 256),
    /** Velarium can be made without end, so the warehouse beyond the warehouse: 15 rows, a thousand stacks a slot. */
    VELARIUM("velarium", 135, 1024);

    public static final Codec<StorageTier> CODEC = StringRepresentable.fromEnum(StorageTier::values);
    /** The slots of the biggest barrel. */
    public static final int MOST_SLOTS = Arrays.stream(values()).mapToInt(tier -> tier.slots).max().orElseThrow();

    private final String name;
    public final int slots;
    private final int multiplier;

    StorageTier(String name, int slots, int multiplier) {
        this.name = name;
        this.slots = slots;
        this.multiplier = multiplier;
    }

    /** How many of an item with the given normal stack size fit in one slot. */
    public int slotCapacity(int itemMaxStackSize) {
        return itemMaxStackSize * this.multiplier;
    }

    public int rows() {
        return this.slots / 9;
    }

    /** How many stacks the whole barrel holds when full, whatever it is full of. */
    public int stackCapacity() {
        return this.slots * this.multiplier;
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
