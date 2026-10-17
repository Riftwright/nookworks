package dev.nookworks.inventory;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Plans Mouse-Tweaks-style inventory moves as sequences of ordinary vanilla clicks.
 * Because only vanilla clicks are sent, the tweaks work on any server, even without Nookworks installed.
 * The client sends the planned clicks; GameTests replay them on real menus.
 */
public final class InventoryTweaks {
    /** One vanilla click: slot index in the menu, mouse button (0 left, 1 right) and click type. */
    public record Click(int slot, int button, ContainerInput input) {
        static Click left(Slot slot) {
            return new Click(slot.index, 0, ContainerInput.PICKUP);
        }

        static Click right(Slot slot) {
            return new Click(slot.index, 1, ContainerInput.PICKUP);
        }
    }

    /** Which half of the screen a slot belongs to. Moves always go to the opposite half. */
    enum Side { HOTBAR, MAIN, OTHER, NONE }

    /**
     * Moves one item from {@code from} to the opposite side. Empty list if not possible.
     * <p>
     * Only between slots the player may really take from and put into: a filter slot shows an item that is not
     * there (the Sorter's ghost filters), and a click on it must never be planned as a move. And only slots the screen
     * shows: the Printing Press's sample slot is hidden on its poster page, the Sorter's gear slot while its tag picker
     * is open, and the wheel put things in them unseen (r7 review).
     */
    public static List<Click> pushOne(AbstractContainerMenu menu, Player player, Slot from) {
        if (!menu.getCarried().isEmpty() || !from.isActive() || !from.hasItem() || !from.mayPickup(player) || !from.mayPlace(from.getItem())) {
            return List.of();
        }
        Side side = side(menu, from);
        if (side == Side.NONE) {
            return List.of();
        }
        Slot to = findTarget(menu, opposite(menu, side), from.getItem());
        return to == null ? List.of() : moveOne(from, to);
    }

    /** Pulls one matching item from the opposite side into {@code to}. Empty list if not possible. */
    public static List<Click> pullOne(AbstractContainerMenu menu, Player player, Slot to) {
        if (!menu.getCarried().isEmpty() || !to.isActive() || !to.hasItem() || !to.mayPlace(to.getItem()) || !to.mayPickup(player)) {
            return List.of();
        }
        Side side = side(menu, to);
        ItemStack want = to.getItem();
        if (side == Side.NONE || to.getItem().getCount() >= maxFor(to, want)) {
            return List.of();
        }
        Side source = opposite(menu, side);
        for (Slot slot : menu.slots) {
            if (side(menu, slot) == source && slot.isActive() && slot.hasItem() && slot.mayPickup(player) && slot.mayPlace(slot.getItem())
                    && ItemStack.isSameItemSameComponents(slot.getItem(), want)) {
                return moveOne(slot, to);
            }
        }
        return List.of();
    }

    /** Pick up the stack, drop one with a right click, put the rest back. */
    private static List<Click> moveOne(Slot from, Slot to) {
        if (from.getItem().getCount() == 1) {
            return List.of(Click.left(from), Click.left(to));
        }
        return List.of(Click.left(from), Click.right(to), Click.left(from));
    }

    private static @Nullable Slot findTarget(AbstractContainerMenu menu, Side target, ItemStack stack) {
        Slot empty = null;
        for (Slot slot : menu.slots) {
            if (side(menu, slot) != target || !slot.isActive() || !slot.mayPlace(stack)) {
                continue;
            }
            ItemStack there = slot.getItem();
            if (there.isEmpty()) {
                if (empty == null) {
                    empty = slot;
                }
            } else if (ItemStack.isSameItemSameComponents(there, stack) && there.getCount() < maxFor(slot, stack)) {
                return slot; // topping up an existing stack beats starting a new one
            }
        }
        return empty;
    }

    /** What the slot takes of this item: a normal slot's own answer is the item's stack size, a barrel's its capacity. */
    private static int maxFor(Slot slot, ItemStack stack) {
        return slot.getMaxStackSize(stack);
    }

    private static Side opposite(AbstractContainerMenu menu, Side side) {
        if (!hasOtherSide(menu)) {
            return side == Side.HOTBAR ? Side.MAIN : Side.HOTBAR;
        }
        return side == Side.OTHER ? Side.MAIN : Side.OTHER;
    }

    private static boolean hasOtherSide(AbstractContainerMenu menu) {
        if (menu instanceof InventoryMenu) {
            return false;
        }
        for (Slot slot : menu.slots) {
            if (!(slot.container instanceof Inventory) && !(slot instanceof ResultSlot)) {
                return true;
            }
        }
        return false;
    }

    static Side side(AbstractContainerMenu menu, Slot slot) {
        if (slot instanceof ResultSlot) {
            return Side.NONE;
        }
        if (slot.container instanceof Inventory) {
            int index = slot.getContainerSlot();
            if (index >= Inventory.INVENTORY_SIZE) {
                return Side.NONE; // armour and offhand
            }
            if (hasOtherSide(menu)) {
                return Side.MAIN; // the whole player inventory counts as one side next to a chest
            }
            return index < Inventory.getSelectionSize() ? Side.HOTBAR : Side.MAIN;
        }
        return menu instanceof InventoryMenu ? Side.NONE : Side.OTHER;
    }

    private InventoryTweaks() {}
}
