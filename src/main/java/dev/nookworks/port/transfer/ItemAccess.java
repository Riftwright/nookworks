package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.ItemCapability;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.access.ItemAccess}: where an item stack is (a player's hand, a menu's
 * cursor, a handler's slot, a loose stack), so that a handler of the item (a bucket's fluid) can swap it for another
 * inside a transaction - an empty bucket in the hand becomes a filled one only if the transaction commits.
 *
 * <p>The item capabilities of ours take an access as their context ({@link Capabilities.Fluid#ITEM}); 1.21.1's take
 * none, and change the stack they are given: {@link TransferBridges} bridges the two.
 */
public interface ItemAccess {
    /** The item in a player's hand; a player with infinite materials (creative) keeps theirs, as 26.3 has it. */
    static ItemAccess forPlayerInteraction(Player player, InteractionHand hand) {
        if (player.hasInfiniteMaterials()) {
            return forInfiniteMaterials(player, player.getItemInHand(hand));
        }
        return forPlayerSlot(player, hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND);
    }

    /** An item of a creative player's: taking from it costs nothing, and what it becomes is added once if not held yet. */
    static ItemAccess forInfiniteMaterials(Player player, ItemStack contents) {
        if (!player.hasInfiniteMaterials()) {
            throw new IllegalArgumentException("Player " + player + " does not have infinite materials");
        }
        return new ItemAccesses.Infinite(player, ItemResource.of(contents), contents.getCount());
    }

    /** The stack on a menu's cursor; what does not fit back on it goes into the player's inventory, or is dropped. */
    static ItemAccess forPlayerCursor(Player player, AbstractContainerMenu menu) {
        return new ItemAccesses.OfPlayer(player, ItemAccesses.Cursor.of(menu));
    }

    /** One slot of a player's inventory; what does not fit back in it goes elsewhere in the inventory, or is dropped. */
    static ItemAccess forPlayerSlot(Player player, int slot) {
        return new ItemAccesses.OfPlayer(player, new ItemAccesses.OneIndex(VanillaContainerWrapper.of(player.getInventory()), slot));
    }

    /** One index of a handler; what does not fit back in it goes anywhere else in the handler. */
    static ItemAccess forHandlerIndex(ResourceHandler<ItemResource> handler, int index) {
        return new ItemAccesses.OfHandler(handler, index);
    }

    /** One index of a handler, and only it: what does not fit back in it is refused. */
    static ItemAccess forHandlerIndexStrict(ResourceHandler<ItemResource> handler, int index) {
        return new ItemAccesses.OfHandler(new ItemAccesses.OneIndex(handler, index), 0);
    }

    /** A loose stack (it is read, never changed: a check of what the item holds). */
    static ItemAccess forStack(ItemStack stack) {
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Expected a non-empty stack");
        }
        return new ItemAccesses.OfStack(stack);
    }

    /** The same place, shown as holding at most one item (a handler of it then swaps one at a time). */
    default ItemAccess oneByOne() {
        return new ItemAccesses.OneByOne(this);
    }

    /** A capability of the item here, given this access as its context. */
    default <T> @Nullable T getCapability(ItemCapability<T, ItemAccess> capability) {
        ItemResource resource = getResource();
        return resource.isEmpty() ? null : capability.getCapability(resource.toStack(), this);
    }

    ItemResource getResource();

    int getAmount();

    int insert(ItemResource resource, int amount, TransactionContext transaction);

    int extract(ItemResource resource, int amount, TransactionContext transaction);

    /**
     * Swaps {@code amount} of what is here for as many of {@code newResource}, all or nothing; returns the amount
     * swapped. With a null transaction, a root of its own.
     */
    default int exchange(ItemResource newResource, int amount, @Nullable TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(newResource, amount);
        ItemResource current = getResource();
        TransferPreconditions.checkNonEmpty(current);
        try (Transaction tx = Transaction.open(transaction)) {
            int taken = extract(current, amount, tx);
            if (taken > 0 && insert(newResource, taken, tx) == taken) {
                tx.commit();
                return taken;
            }
        }
        return 0;
    }
}
