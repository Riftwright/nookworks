package dev.nookworks.mobcapture;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.menu.ShiftClick;
import dev.nookworks.menu.tab.MachineTab;
import dev.nookworks.menu.tab.MenuTabs;
import dev.nookworks.menu.tab.TabbedMenu;
import dev.nookworks.registry.ModMenus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import dev.nookworks.port.core.InventorySlots;

/** Spawn Egg Brewer GUI: bottle and egg on the left, blaze powder below, the spawn egg on the right. */
public class SpawnEggBrewerMenu extends AbstractContainerMenu implements TabbedMenu {
    public static final int BOTTLE_X = 44;
    public static final int BOTTLE_Y = 20;
    public static final int EGG_X = 44;
    public static final int EGG_Y = 48;
    public static final int FUEL_X = 8;
    public static final int FUEL_Y = 48;
    public static final int OUTPUT_X = 116;
    public static final int OUTPUT_Y = 34;
    public static final int INVENTORY_Y = 98;
    private static final int MACHINE_SLOTS = 4;
    /** Progress, fuel, the redstone mode, and whether it is holding a brew. */
    public static final int DATA_COUNT = 5;
    public static final int BUTTON_REDSTONE = 0;

    private final Container container;
    private final MenuTabs tabs = new MenuTabs(176);
    private final ContainerData data;
    private final @Nullable SpawnEggBrewerBlockEntity brewer;

    /** Client side. */
    public SpawnEggBrewerMenu(int containerId, Inventory inventory) {
        // knowing the slots' rules, as the brewer does: a stone put in the bottle slot is not drawn there until refused
        this(containerId, inventory, new SimpleContainer(MACHINE_SLOTS) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return SpawnEggBrewerBlockEntity.accepts(slot, stack);
            }
        }, new SimpleContainerData(DATA_COUNT), null);
    }

    public SpawnEggBrewerMenu(int containerId, Inventory inventory, Container container, ContainerData data,
            @Nullable SpawnEggBrewerBlockEntity brewer) {
        super(ModMenus.SPAWN_EGG_BREWER.get(), containerId);
        this.container = container;
        this.data = data;
        this.brewer = brewer;
        this.addSlot(new InputSlot(container, SpawnEggBrewerBlockEntity.BOTTLE, BOTTLE_X, BOTTLE_Y));
        this.addSlot(new InputSlot(container, SpawnEggBrewerBlockEntity.EGG, EGG_X, EGG_Y));
        this.addSlot(new InputSlot(container, SpawnEggBrewerBlockEntity.FUEL, FUEL_X, FUEL_Y));
        this.addSlot(new Slot(container, SpawnEggBrewerBlockEntity.OUTPUT, OUTPUT_X, OUTPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        InventorySlots.addStandardInventorySlots(inventory, 8, INVENTORY_Y, this::addSlot);
        this.addDataSlots(data);
        // the tabs on the screen's right edge (1.5): its redstone mode in Settings; an add-on may add an Add-on tab
        this.tabs.enable(MachineTab.SETTINGS);
    }

    public int progress() {
        return this.data.get(0);
    }

    public int fuel() {
        return this.data.get(1);
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(2));
    }

    public boolean heldByRedstone() {
        return this.data.get(3) != 0;
    }

    /** A brew ready, its egg with nowhere to go. */
    public boolean outputBlocked() {
        return this.data.get(4) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.tabs.clickMenuButton(player, id)) {
            return true;
        }
        if (id == BUTTON_REDSTONE && this.brewer != null) {
            this.brewer.setRedstoneMode(this.brewer.redstoneMode().next());
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack tabbed = this.tabs.quickMove(slot, MACHINE_SLOTS, this::moveItemStackTo);
        if (tabbed != null) {
            return tabbed;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < MACHINE_SLOTS) {
            boolean output = index == SpawnEggBrewerBlockEntity.OUTPUT;
            if (!this.moveItemStackTo(stack, MACHINE_SLOTS, MACHINE_SLOTS + ShiftClick.INVENTORY_SLOTS, output)) {
                return ItemStack.EMPTY;
            }
        } else {
            int target = SpawnEggBrewerBlockEntity.isFilledBottle(stack) ? SpawnEggBrewerBlockEntity.BOTTLE
                    : SpawnEggBrewerBlockEntity.isEgg(stack) ? SpawnEggBrewerBlockEntity.EGG
                    : SpawnEggBrewerBlockEntity.isFuel(stack) ? SpawnEggBrewerBlockEntity.FUEL : -1;
            if (target < 0 ? !ShiftClick.acrossInventory(stack, index, MACHINE_SLOTS, this::moveItemStackTo)
                    : !this.moveItemStackTo(stack, target, target + 1, false)) {
                return ItemStack.EMPTY;
            }
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
        return this.container.stillValid(player);
    }

    private static class InputSlot extends Slot {
        InputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.container.canPlaceItem(this.getContainerSlot(), stack);
        }
    }

    @Override
    public MenuTabs tabs() {
        return this.tabs;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        this.tabs.removed(player);
    }
}
