package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;

/** Stand-in for NeoForge 26.3's {@code transfer.item.ItemUtil}: item stacks in and out of handlers. */
public final class ItemUtil {
    private ItemUtil() {}

    /** A new stack of what index {@code index} holds (its count may be over the item's stack size). */
    public static ItemStack getStack(ResourceHandler<ItemResource> handler, int index) {
        ItemResource resource = handler.getResource(index);
        return resource.isEmpty() ? ItemStack.EMPTY : resource.toStack(handler.getAmountAsInt(index));
    }

    /**
     * Inserts a stack where the handler puts it and returns what did not fit ({@code stack} itself is not changed). Done
     * in a transaction under {@code transaction} (a root if null), committed unless {@code simulate}.
     */
    public static ItemStack insertItemReturnRemaining(ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate,
            @Nullable TransactionContext transaction) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.open(transaction)) {
            int moved = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
            int left = stack.getCount() - moved;
            return left == 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }
    }

    /** The same, into one index. */
    public static ItemStack insertItemReturnRemaining(ResourceHandler<ItemResource> handler, int index, ItemStack stack,
            boolean simulate, @Nullable TransactionContext transaction) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try (Transaction tx = Transaction.open(transaction)) {
            int moved = handler.insert(index, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) {
                tx.commit();
            }
            int left = stack.getCount() - moved;
            return left == 0 ? ItemStack.EMPTY : stack.copyWithCount(left);
        }
    }
}
