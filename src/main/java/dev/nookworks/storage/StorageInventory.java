package dev.nookworks.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.mojang.serialization.Codec;

import dev.nookworks.item.CrateItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The Storage Chest's slots: a NeoForge item handler whose slots hold far more than a normal stack. Hoppers and
 * pipes reach it through the item capability (the chest deliberately is not a vanilla Container, because vanilla
 * hopper code would never fill a slot past 64). Saved in {@link StorageContents} form.
 */
public class StorageInventory extends ItemStacksResourceHandler {
    private final StorageTier tier;
    private final Runnable onChanged;
    private final LongSupplier clock;
    /** The game time something was last put into each slot, for sorting by what went in last. */
    private final long[] added;

    public StorageInventory(StorageTier tier, Runnable onChanged) {
        this(tier, onChanged, () -> 0L);
    }

    public StorageInventory(StorageTier tier, Runnable onChanged, LongSupplier clock) {
        super(tier.slots);
        this.tier = tier;
        this.onChanged = onChanged;
        this.clock = clock;
        this.added = new long[tier.slots];
    }

    public StorageTier tier() {
        return this.tier;
    }

    @Override
    protected int getCapacity(int index, ItemResource resource) {
        return this.tier.slotCapacity(resource.isEmpty() ? 64 : resource.getMaxStackSize());
    }

    /**
     * Put in where the same thing is already, then into empty slots - as vanilla's shift-click fills a chest, and so a
     * barrel's slots, each of many stacks, fill up rather than scatter: a shift-click, a hopper and a pipe alike. (The
     * transfer API's own way fills the first slot that takes it, an empty one before a part-full one further on.)
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int inserted = 0;
        for (int pass = 0; pass < 2 && inserted < amount; pass++) {
            for (int index = 0; index < this.size() && inserted < amount; index++) {
                if (this.getResource(index).isEmpty() == (pass == 1)) {
                    inserted += this.insert(index, resource, amount - inserted, transaction);
                }
            }
        }
        return inserted;
    }

    /** Whatever way a thing arrives - the screen, a shift-click, a hopper or a pipe - the same rule: {@link #accepts}. */
    @Override
    public boolean isValid(int index, ItemResource resource) {
        return accepts(resource.toStack(1));
    }

    /**
     * Whether a barrel slot may take {@code stack}. Not what vanilla keeps out of a shulker box: a shulker box, another
     * mod's backpack, a barrel with things in it - a barrel holds a great deal, and that much again inside each of
     * those would make one item of more data than a server should send round. A Crate may go in all the same, full or
     * not (the user, 2026-10-01): a crate holds no shulker box. Nor a bundle: clicking a bundle changes the stack in
     * the slot, and a barrel slot only ever hands out a copy, so a bundle in one could be emptied and refilled for
     * nothing.
     */
    public static boolean accepts(ItemStack stack) {
        return !stack.has(DataComponents.BUNDLE_CONTENTS)
                && (stack.getItem() instanceof CrateItem || stack.getItem().canFitInsideContainerItems(stack));
    }

    @Override
    protected void onContentsChanged(int index, ItemStack previousContents) {
        ItemStack now = this.stacks.get(index);
        if (!now.isEmpty() && (!ItemStack.isSameItemSameComponents(now, previousContents) || now.getCount() > previousContents.getCount())) {
            this.added[index] = this.clock.getAsLong();
        }
        this.onChanged.run();
    }

    /** When something was last put into this slot, in game ticks (0: never, or before the barrels kept count). */
    public long lastAdded(int index) {
        return this.added[index];
    }

    /** Sorts in the game's own item order. */
    public void sort() {
        this.sort(SortOrder.DEFAULT);
    }

    /**
     * Sorts the barrel: every kind of item gathered into as few slots as it fits in, in the given order, identical
     * items with different components (names, enchantments, damage) kept apart as they must be. Nothing is added or
     * lost: it only moves what is there, and each lot keeps when it was last added to.
     */
    public void sort(SortOrder by) {
        Map<ItemResource, long[]> totals = new LinkedHashMap<>();
        for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack stack = this.stacks.get(i);
            if (!stack.isEmpty()) {
                long[] lot = totals.computeIfAbsent(ItemResource.of(stack), r -> new long[2]);
                lot[0] += stack.getCount();
                lot[1] = Math.max(lot[1], this.added[i]);
            }
        }
        List<SortOrder.Lot> lots = new ArrayList<>();
        totals.forEach((resource, lot) -> lots.add(new SortOrder.Lot(resource, lot[0], lot[1])));
        lots.sort(by.comparator());
        List<ItemStack> sorted = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        this.packInto(lots, sorted, times, false);
        if (sorted.size() > this.stacks.size()) {
            // More than today's capacity fits: what a slot held past it (an item whose stack size a pack lowered since)
            // is packed as load() keeps it, so a sort never drops anything.
            sorted.clear();
            times.clear();
            this.packInto(lots, sorted, times, true);
        }
        for (int i = 0; i < this.stacks.size(); i++) {
            this.stacks.set(i, i < sorted.size() ? sorted.get(i) : ItemStack.EMPTY);
            this.added[i] = i < times.size() ? times.get(i) : 0L;
        }
        this.onChanged.run();
    }

    /** Lots cut into slots: of today's capacity, or of the most a slot of this tier could ever hold ({@code most}). */
    private void packInto(List<SortOrder.Lot> lots, List<ItemStack> sorted, List<Long> times, boolean most) {
        for (SortOrder.Lot lot : lots) {
            long left = lot.count();
            int capacity = Math.max(1, most ? this.tier().slotCapacity(Item.ABSOLUTE_MAX_STACK_SIZE) : this.getCapacity(0, lot.resource()));
            while (left > 0) {
                int count = (int) Math.min(capacity, left);
                sorted.add(lot.resource().toStack(count));
                times.add(lot.lastAdded());
                left -= count;
            }
        }
    }

    public StorageContents toContents() {
        List<StorageContents.Entry> entries = new ArrayList<>();
        for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack stack = this.stacks.get(i);
            if (!stack.isEmpty()) {
                entries.add(new StorageContents.Entry(i, stack.copyWithCount(1), stack.getCount(), this.added[i]));
            }
        }
        return new StorageContents(List.copyOf(entries));
    }

    public void load(StorageContents contents) {
        for (int i = 0; i < this.stacks.size(); i++) {
            this.stacks.set(i, ItemStack.EMPTY);
            this.added[i] = 0L;
        }
        // Saved data is trusted no further than the tier allows: a hand-edited or corrupt count of two billion would
        // otherwise come out as tens of millions of stacks the moment the barrel is broken. Held to the most a slot of
        // this tier could ever hold, not to what it holds of this item today: a datapack or a mod that lowers an
        // item's stack size would otherwise have the rest vanish at the next load. A slot over today's capacity only
        // lets things out. What a slot would not take today comes back too (a shulker box put in before barrels
        // refused them): it can be taken out, and nothing a barrel held is ever lost to a rule made since.
        for (StorageContents.Entry e : contents.entries()) {
            ItemResource resource = ItemResource.of(e.item());
            if (e.slot() < this.stacks.size() && !resource.isEmpty()) {
                this.stacks.set(e.slot(), e.item().copyWithCount(Math.min(e.count(), this.tier().slotCapacity(Item.ABSOLUTE_MAX_STACK_SIZE))));
                this.added[e.slot()] = e.added();
            }
        }
    }

    /** Filled fraction for comparators, like vanilla containers but against the enlarged capacity. */
    public int comparatorSignal() {
        float fill = 0;
        int used = 0;
        for (int i = 0; i < this.stacks.size(); i++) {
            ItemStack stack = this.stacks.get(i);
            if (!stack.isEmpty()) {
                fill += stack.getCount() / (float) this.getCapacity(i, ItemResource.of(stack));
                used++;
            }
        }
        // a slot over today's capacity would read past 15
        return used == 0 ? 0 : Math.min(15, 1 + (int) (fill / this.stacks.size() * 14.0F));
    }

    @Override
    public void serialize(ValueOutput output) {
        output.store("contents", StorageContents.CODEC, this.toContents());
    }

    @Override
    public void deserialize(ValueInput input) {
        this.load(input.read("contents", StorageContents.CODEC).orElse(StorageContents.EMPTY));
        // saved before the times went into the contents: they were kept as a list beside them
        input.read("added", Codec.LONG.listOf()).ifPresent(times -> {
            for (int i = 0; i < times.size() && i < this.added.length; i++) {
                this.added[i] = times.get(i);
            }
        });
    }
}
