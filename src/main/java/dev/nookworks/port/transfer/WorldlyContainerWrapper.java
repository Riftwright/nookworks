package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.world.WorldlyContainer;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.item.WorldlyContainerWrapper}: a {@link WorldlyContainer} as seen from one
 * face - index {@code i} is the face's {@code i}th slot, and its {@code canPlaceItemThroughFace} /
 * {@code canTakeItemThroughFace} decide. Seen from no face, every slot, and only {@code canPlaceItemThroughFace(.., null)}
 * guards an insert. Changes go through the container's {@link VanillaContainerWrapper}, so they share its journals.
 */
public class WorldlyContainerWrapper implements ResourceHandler<ItemResource> {
    private final WorldlyContainer container;
    private final VanillaContainerWrapper whole;
    private final @Nullable Direction side;

    public WorldlyContainerWrapper(WorldlyContainer container, @Nullable Direction side) {
        this.container = container;
        this.whole = VanillaContainerWrapper.internalOf(container);
        this.side = side;
    }

    /** The container's slot behind index {@code index} of this face. */
    private int slotOf(int index) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("Negative slot index " + index);
        }
        if (this.side == null) {
            return index;
        }
        int[] slots = this.container.getSlotsForFace(this.side);
        if (index >= slots.length) {
            throw new IndexOutOfBoundsException("Slot index " + index + " is past the " + slots.length + " slots of face " + this.side);
        }
        return slots[index];
    }

    @Override
    public int size() {
        return this.side == null ? this.container.getContainerSize() : this.container.getSlotsForFace(this.side).length;
    }

    @Override
    public ItemResource getResource(int index) {
        return this.whole.getResource(slotOf(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return this.whole.getAmountAsLong(slotOf(index));
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return this.whole.getCapacityAsLong(slotOf(index), resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return this.whole.isValid(slotOf(index), resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        int slot = slotOf(index);
        if (!this.container.canPlaceItemThroughFace(slot, resource.toStack(), this.side)) {
            return 0;
        }
        return this.whole.insert(slot, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        int slot = slotOf(index);
        if (this.side != null && !this.container.canTakeItemThroughFace(slot, resource.toStack(), this.side)) {
            return 0;
        }
        return this.whole.extract(slot, resource, amount, transaction);
    }
}
