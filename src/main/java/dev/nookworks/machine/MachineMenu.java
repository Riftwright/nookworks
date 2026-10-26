package dev.nookworks.machine;

import org.jspecify.annotations.Nullable;

import dev.nookworks.barter.BarterTerminalBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The one menu every machine screen uses: the slots its {@link MachineLayout} lists, the player's inventory, and
 * the machine's numbers. Button {@link #BUTTON_REDSTONE} steps its redstone mode.
 * <p>
 * Menu data travels as 16-bit shorts, and a machine's energy does not fit in one, so each number takes two data
 * slots: the low half, then the high half.
 */
public class MachineMenu extends AbstractContainerMenu {
    public static final int BUTTON_REDSTONE = 0;
    /** The Sealed Retort's setting for bottles a hopper puts in. */
    public static final int BUTTON_RETORT_BOTTLES = 1;

    private static final int ENERGY = 0;
    private static final int ENERGY_CAPACITY = 1;
    private static final int ENERGY_RATE = 2;
    private static final int PROGRESS = 3;
    private static final int PROGRESS_MAX = 4;
    private static final int EXTRA = 5;
    /** Each tank's amount and capacity, two wide numbers per tank. */
    private static final int TANKS = 6;
    public static final int MAX_TANKS = 4;
    private static final int WIDE = TANKS + MAX_TANKS * 2;
    private static final int STATUS = WIDE * 2;
    private static final int REDSTONE = STATUS + 1;
    /** What each tank holds ({@link GuiTank.Kind}), or -1 where there is none. */
    private static final int KINDS = REDSTONE + 1;
    public static final int DATA_COUNT = KINDS + MAX_TANKS;

    private final MachineLayout layout;
    private final Container container;
    private final ContainerData data;
    private final @Nullable BlockEntity machine;

    /** Client side: the server says which layout. */
    public static MachineMenu client(int containerId, Inventory inventory, FriendlyByteBuf buf) {
        MachineLayout layout = MachineLayout.byId(buf.readVarInt());
        return new MachineMenu(containerId, inventory, layout, clientSlots(layout), new SimpleContainerData(DATA_COUNT), null);
    }

    /**
     * The client's stand-in for the machine's slots. It knows enough of the machine's rules to put a shift-clicked
     * item where the server will (the Sifter's mesh in the mesh slot, not the input), so the screen does not show a
     * move that the next sync takes back. Anything it cannot know on the client (recipes, loot tables) it lets
     * through, and the server has the last word.
     */
    private static Container clientSlots(MachineLayout layout) {
        return new SimpleContainer(layout.slotCount()) {
            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                if (layout == MachineLayout.BARTER_TERMINAL) {
                    return slot == BarterTerminalBlockEntity.GOLD ? stack.isPiglinCurrency()
                            : slot == BarterTerminalBlockEntity.BOTTLE && BarterTerminalBlockEntity.takesBottle(stack);
                }
                if (layout == MachineLayout.SIFTER) {
                    return slot == SifterBlockEntity.MESH
                            ? stack.is(ModItems.BRASS_MESH.get())
                            : slot == SifterBlockEntity.INPUT
                                    && stack.is(SifterBlockEntity.SIFTABLE);
                }
                if (layout == MachineLayout.DIESEL_GENERATOR) {
                    return DieselGeneratorBlockEntity.mbIn(stack) > 0;
                }
                return true;
            }
        };
    }

    /** Opens the machine's screen for {@code player}. */
    public static <T extends BlockEntity & MachineInfo> void open(Player player, T machine) {
        MachineLayout layout = machine.layout();
        Component title = machine.getBlockState().getBlock().getName();
        player.openMenu(new SimpleMenuProvider(
                (id, inventory, p) -> new MachineMenu(id, inventory, layout, machine.slots(), data(machine), machine), title),
                buf -> buf.writeVarInt(layout.ordinal()));
    }

    public MachineMenu(int containerId, Inventory inventory, MachineLayout layout, Container container, ContainerData data,
            @Nullable BlockEntity machine) {
        super(ModMenus.MACHINE.get(), containerId);
        this.layout = layout;
        this.container = container;
        this.data = data;
        this.machine = machine;
        for (int i = 0; i < layout.slotCount(); i++) {
            MachineLayout.Spot spot = layout.spots().get(i);
            this.addSlot(new Slot(container, i, spot.x(), spot.y()) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !spot.output() && this.container.canPlaceItem(this.getContainerSlot(), stack);
                }
            });
        }
        this.addStandardInventorySlots(inventory, 8, MachineLayout.INVENTORY_Y);
        this.addDataSlots(data);
    }

    /** The machine's numbers, read live each time the menu syncs. */
    public static ContainerData data(MachineInfo info) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                if (index < STATUS) {
                    int field = index / 2;
                    int value = switch (field) {
                        case ENERGY -> info.guiEnergy();
                        case ENERGY_CAPACITY -> info.guiEnergyCapacity();
                        case ENERGY_RATE -> info.guiEnergyRate();
                        case PROGRESS -> info.guiProgress();
                        case PROGRESS_MAX -> info.guiProgressMax();
                        case EXTRA -> info.guiExtra();
                        default -> {
                            GuiTank tank = tank(info, (field - TANKS) / 2);
                            yield tank == null ? 0 : (field - TANKS) % 2 == 0 ? tank.amount() : tank.capacity();
                        }
                    };
                    return index % 2 == 0 ? value & 0xFFFF : value >>> 16;
                }
                if (index >= KINDS) {
                    GuiTank tank = tank(info, index - KINDS);
                    return tank == null ? -1 : tank.kind().ordinal();
                }
                return index == STATUS ? info.guiStatus().ordinal() : info.redstoneMode().ordinal();
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private static @Nullable GuiTank tank(MachineInfo info, int i) {
        var tanks = info.guiTanks();
        return i < tanks.size() ? tanks.get(i) : null;
    }

    private int wide(int field) {
        return (this.data.get(field * 2) & 0xFFFF) | (this.data.get(field * 2 + 1) & 0xFFFF) << 16;
    }

    public MachineLayout layout() {
        return this.layout;
    }

    public int energy() {
        return this.wide(ENERGY);
    }

    public int energyCapacity() {
        return this.wide(ENERGY_CAPACITY);
    }

    public int energyRate() {
        return this.wide(ENERGY_RATE);
    }

    public int progress() {
        return this.wide(PROGRESS);
    }

    public int progressMax() {
        return this.wide(PROGRESS_MAX);
    }

    /** The machine's own extra number ({@link MachineInfo#guiExtra()}). */
    public int extra() {
        return this.wide(EXTRA);
    }

    public int tankAmount(int i) {
        return this.wide(TANKS + i * 2);
    }

    public int tankCapacity(int i) {
        return this.wide(TANKS + i * 2 + 1);
    }

    public GuiTank.Kind tankKind(int i) {
        return GuiTank.Kind.byId(this.data.get(KINDS + i));
    }

    public MachineStatus status() {
        return MachineStatus.byId(this.data.get(STATUS));
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(REDSTONE));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REDSTONE && this.machine instanceof MachineInfo info) {
            info.setRedstoneMode(info.redstoneMode().next());
            return true;
        }
        if (id == BUTTON_RETORT_BOTTLES && this.machine instanceof RetortBlockEntity retort) {
            retort.setBottles(retort.bottles().next());
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
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int machineSlots = this.layout.slotCount();
        if (index < machineSlots) {
            if (!this.moveItemStackTo(stack, machineSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveIntoInputs(stack)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * Shift-click into the machine. One slot at a time and only its inputs: moveItemStackTo tops up a matching
     * stack without asking the slot, which would let a player stuff sulfur dust into the Retort's output.
     */
    private boolean moveIntoInputs(ItemStack stack) {
        boolean moved = false;
        int first = this.machine instanceof MachineInfo info ? info.shiftClickSlot(stack) : -1;
        if (first >= 0 && first < this.layout.slotCount() && this.slots.get(first).mayPlace(stack)) {
            moved = this.moveItemStackTo(stack, first, first + 1, false);
        }
        for (int i = 0; i < this.layout.slotCount() && !stack.isEmpty(); i++) {
            if (!this.layout.spots().get(i).output() && this.slots.get(i).mayPlace(stack)) {
                moved |= this.moveItemStackTo(stack, i, i + 1, false);
            }
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.machine == null || Container.stillValidBlockEntity(this.machine, player);
    }
}
