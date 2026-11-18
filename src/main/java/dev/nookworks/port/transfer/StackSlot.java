package dev.nookworks.port.transfer;

import java.util.Objects;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One item stack kept somewhere else (a container's slot, a menu's cursor) as a transactional handler of one index: the
 * stack from before the first change in a transaction is kept and put back if it aborts. In the spirit of 26.3's
 * {@code ItemStackResourceHandler}, which the mod does not name; ours writes through {@link #write}, which a container
 * slot uses to change a count in place where it can (1.21.1's {@code Container.setItem} has side effects 26.3's
 * in-transaction form skips: a furnace resets its cooking when its input slot is set to another item).
 */
abstract class StackSlot extends SnapshotJournal<ItemStack> implements ResourceHandler<ItemResource> {
    /** The stack as it is now (the live one: it may be changed in place only through {@link #write}). */
    protected abstract ItemStack read();

    /** Puts {@code stack} in (a new stack, or the live one changed). */
    protected abstract void write(ItemStack stack);

    protected boolean isValid(ItemResource resource) {
        return true;
    }

    protected int getCapacity(ItemResource resource) {
        return resource.isEmpty() ? Item.ABSOLUTE_MAX_STACK_SIZE : Math.min(resource.getMaxStackSize(), Item.ABSOLUTE_MAX_STACK_SIZE);
    }

    @Override
    public final int size() {
        return 1;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack now = read();
        if ((now.isEmpty() || resource.matches(now)) && isValid(resource)) {
            int moved = Math.min(amount, getCapacity(resource) - now.getCount());
            if (moved > 0) {
                updateSnapshots(transaction);
                write(now.isEmpty() ? resource.toStack(moved) : now.copyWithCount(now.getCount() + moved));
                return moved;
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ItemStack now = read();
        if (resource.matches(now)) {
            int moved = Math.min(now.getCount(), amount);
            if (moved > 0) {
                updateSnapshots(transaction);
                write(moved == now.getCount() ? ItemStack.EMPTY : now.copyWithCount(now.getCount() - moved));
                return moved;
            }
        }
        return 0;
    }

    @Override
    public ItemResource getResource(int index) {
        Objects.checkIndex(index, 1);
        return ItemResource.of(read());
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, 1);
        return read().getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Objects.checkIndex(index, 1);
        return resource.isEmpty() || isValid(resource) ? getCapacity(resource) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Objects.checkIndex(index, 1);
        TransferPreconditions.checkNonEmpty(resource);
        return isValid(resource);
    }

    @Override
    protected ItemStack createSnapshot() {
        return read().copy();
    }

    @Override
    protected void revertToSnapshot(ItemStack snapshot) {
        write(snapshot);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + read() + "]";
    }
}
