package dev.nookworks.experience;

import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.menu.ShiftClick;
import dev.nookworks.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Repair Box GUI: the gear slots - six, or twelve for a double - between a power gauge and an experience gauge.
 * The gauges show both halves of a double together, since the pair spends them together.
 */
public class RepairBoxMenu extends AbstractContainerMenu {
    /** Energy and experience (two short slots each), how many boxes (1 or 2), the redstone mode, and whether it holds. */
    public static final int BOXES = 4;
    public static final int REDSTONE = 5;
    public static final int HELD = 6;
    public static final int DATA_COUNT = 7;
    public static final int BUTTON_REDSTONE = 0;
    public static final int SLOT_Y = 35;
    public static final int INVENTORY_Y = MachineLayout.INVENTORY_Y;

    private final Container container;
    private final ContainerData data;
    private final RepairBoxBlockEntity[] halves;

    /** Client side: the server says whether this is one box or a pair. */
    public static RepairBoxMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        int boxes = Math.max(1, Math.min(2, buf.readVarInt()));
        return new RepairBoxMenu(containerId, inventory, new SimpleContainer(RepairBoxBlockEntity.SLOTS * boxes),
                new SimpleContainerData(DATA_COUNT), new RepairBoxBlockEntity[0]);
    }

    public RepairBoxMenu(int containerId, Inventory inventory, Container container, ContainerData data,
            RepairBoxBlockEntity[] halves) {
        super(ModMenus.REPAIR_BOX.get(), containerId);
        this.container = container;
        this.data = data;
        this.halves = halves;
        int columns = container.getContainerSize() / 2;
        int left = slotLeft(columns);
        for (int i = 0; i < container.getContainerSize(); i++) {
            this.addSlot(new Slot(container, i, left + (i % columns) * 18, SLOT_Y + (i / columns) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return RepairBoxBlockEntity.repairable(stack);
                }
            });
        }
        this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        this.addDataSlots(data);
        for (RepairBoxBlockEntity box : halves) {
            box.startOpen(inventory.player);
        }
    }

    /** The slots sit centred in the 176-wide screen: three columns for one box, six for a pair. */
    public static int slotLeft(int columns) {
        return (176 - columns * 18) / 2 + 1;
    }

    /** Whether this screen is showing {@code box}, so its lid knows it is open. */
    public boolean shows(RepairBoxBlockEntity box) {
        for (RepairBoxBlockEntity half : this.halves) {
            if (half == box) {
                return true;
            }
        }
        return false;
    }

    private int wide(int low) {
        return (this.data.get(low) & 0xFFFF) | (this.data.get(low + 1) & 0xFFFF) << 16;
    }

    public int energy() {
        return this.wide(0);
    }

    public int experience() {
        return this.wide(2);
    }

    /** 1 for a single box, 2 for a double; the gauges scale with it. */
    public int boxes() {
        return Math.max(1, this.data.get(BOXES));
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(REDSTONE));
    }

    public boolean heldByRedstone() {
        return this.data.get(HELD) != 0;
    }

    /** The pair is one machine, so both halves take the new mode. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_REDSTONE || this.halves.length == 0) {
            return false;
        }
        RedstoneMode next = this.halves[0].redstoneMode().next();
        for (RepairBoxBlockEntity half : this.halves) {
            half.setRedstoneMode(next);
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = this.container.getContainerSize();
        if (index < machineSlots) {
            // mended gear is what the box gives back: to the hotbar first, as a furnace's result
            if (!this.moveItemStackTo(stack, machineSlots, machineSlots + ShiftClick.INVENTORY_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (RepairBoxBlockEntity.repairable(stack) ? !this.moveItemStackTo(stack, 0, machineSlots, false)
                : !ShiftClick.acrossInventory(stack, index, machineSlots, this::moveItemStackTo)) {
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
        for (RepairBoxBlockEntity box : this.halves) {
            if (!Container.stillValidBlockEntity(box, player)) {
                return false;
            }
        }
        return this.halves.length > 0 || this.container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        for (RepairBoxBlockEntity box : this.halves) {
            box.stopOpen(player);
        }
    }
}
