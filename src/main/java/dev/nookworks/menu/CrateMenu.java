package dev.nookworks.menu;

import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A 3x3 menu, laid out like a dispenser, whose crate slots refuse crates and shulker boxes.
 * <p>
 * It has a menu type of its own rather than borrowing {@code GENERIC_3x3}: with the vanilla type the client
 * built a plain dispenser menu, so it predicted a nested crate would fit and the slot only snapped back once
 * the server disagreed.
 */
public class CrateMenu extends AbstractContainerMenu {
    private static final int GRID_X = 62;
    private static final int GRID_Y = 17;

    private final Container crate;

    /** Client side. */
    public CrateMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(CrateBlockEntity.SIZE));
    }

    public CrateMenu(int containerId, Inventory inventory, Container crate) {
        super(ModMenus.CRATE.get(), containerId);
        checkContainerSize(crate, CrateBlockEntity.SIZE);
        this.crate = crate;
        crate.startOpen(inventory.player);
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                this.addSlot(new Slot(crate, x + y * 3, GRID_X + x * 18, GRID_Y + y * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return CrateBlockEntity.canStore(stack);
                    }
                });
            }
        }
        this.addStandardInventorySlots(inventory, 8, 84);
    }

    public Container getCrate() {
        return this.crate;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < CrateBlockEntity.SIZE) {
            if (!this.moveItemStackTo(stack, CrateBlockEntity.SIZE, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, CrateBlockEntity.SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.crate.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.crate.stopOpen(player);
    }
}
