package dev.nookworks.port.core;

import java.util.function.Consumer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * 26.3's {@code AbstractContainerMenu.addStandardInventorySlots(container, left, top)}, which 1.21.1's menu lacks:
 * the player's 27 inventory slots in three rows from (left, top), then the hotbar's 9, 58 below the top (a 4 px gap
 * under the rows), every slot 18 apart - the same slots in the same order as 26.3's. {@code addSlot} is protected, so
 * a menu passes its own: {@code InventorySlots.addStandardInventorySlots(inventory, 8, 84, this::addSlot);}.
 */
public final class InventorySlots {
    private InventorySlots() {}

    public static void addStandardInventorySlots(Container container, int left, int top, Consumer<Slot> addSlot) {
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 9; x++) {
                addSlot.accept(new Slot(container, x + (y + 1) * 9, left + x * 18, top + y * 18));
            }
        }
        for (int x = 0; x < 9; x++) {
            addSlot.accept(new Slot(container, x, left + x * 18, top + 58));
        }
    }
}
