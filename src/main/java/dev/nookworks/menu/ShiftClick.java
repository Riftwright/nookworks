package dev.nookworks.menu;

import net.minecraft.world.item.ItemStack;

/**
 * Vanilla's furnace rule for a machine's screen (the user, 2026-11-06: 「ぜひバニラと合わせて」): what the machine does
 * not take, shift-clicked in the player's inventory, moves to the hotbar, and from the hotbar to the inventory above it;
 * what it takes goes into the machine, or nowhere when there is no room. Out of the machine, an output goes to the
 * hotbar first and anything else to the inventory first, as the furnace's result and its input and fuel do.
 */
public final class ShiftClick {
    /** The player's inventory in a menu: 27 slots, then the hotbar's 9 ({@code addStandardInventorySlots}). */
    public static final int INVENTORY_SLOTS = 36;
    private static final int ABOVE_HOTBAR = 27;

    /** A menu's own moveItemStackTo, which only the menu may call. */
    @FunctionalInterface
    public interface Mover {
        boolean move(ItemStack stack, int start, int end, boolean backwards);
    }

    private ShiftClick() {}

    /**
     * Moves {@code stack}, shift-clicked at menu slot {@code index} of the player's inventory (which starts at
     * {@code inventoryStart}), to the hotbar, or from the hotbar to the 27 slots above it. False if nothing moved, or
     * {@code index} is not in the player's inventory (an add-on's slot after it).
     */
    public static boolean acrossInventory(ItemStack stack, int index, int inventoryStart, Mover mover) {
        int hotbar = inventoryStart + ABOVE_HOTBAR;
        if (index < inventoryStart || index >= inventoryStart + INVENTORY_SLOTS) {
            return false;
        }
        return index < hotbar ? mover.move(stack, hotbar, hotbar + 9, false) : mover.move(stack, inventoryStart, hotbar, false);
    }
}
