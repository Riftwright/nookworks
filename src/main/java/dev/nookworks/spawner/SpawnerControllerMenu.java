package dev.nookworks.spawner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModMenus;
import net.minecraft.resources.Identifier;
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
 * Spawner Controller GUI: the synced settings and one button id per change - and, with add-ons' pages
 * ({@link SpawnerControllerExtensions}), their slots, numbers and buttons, and the player's inventory for a page that
 * asks for it, each there only while its page is shown. Without extensions it has no slots at all, as it always had.
 */
public class SpawnerControllerMenu extends AbstractContainerMenu {
    public static final int DATA_COUNT = 14;

    public static final int TOGGLE_ENABLED = 0;
    public static final int INTERVAL_DOWN = 1;
    public static final int INTERVAL_UP = 2;
    public static final int COUNT_DOWN = 3;
    public static final int COUNT_UP = 4;
    public static final int NEARBY_DOWN = 5;
    public static final int NEARBY_UP = 6;
    public static final int TOGGLE_LIGHT = 7;
    public static final int TOGGLE_PLAYER = 8;
    public static final int BUTTON_REDSTONE = 9;

    /** Where the player's inventory goes on a page that shows it, relative to the panel (the hotbar 58 lower). */
    public static final int INVENTORY_X = 8;
    public static final int INVENTORY_Y = 106;

    private final ContainerData data;
    private final @Nullable SpawnerControllerBlockEntity controller;
    private final List<SpawnerControllerExtensions.Extension> extensions = SpawnerControllerExtensions.all();
    private final Map<Identifier, ContainerData> extensionData = new HashMap<>();
    /** Stand-ins for containers an extension did not give, on the server: what is put in them goes back on closing. */
    private final java.util.List<Container> standIns = new java.util.ArrayList<>();
    /** Where each extension's slots start and end in the menu, in the extensions' order. */
    private final java.util.List<int[]> extensionSlots = new java.util.ArrayList<>();
    /** The extensions' slots end here and the player's inventory (36) starts, when there are extensions. */
    private int playerSlotsFrom = -1;
    /** Which extensions' pages are left out ({@link SpawnerControllerExtensions.Extension#shown}), a bit each, in step. */
    private ContainerData hidden = new SimpleContainerData(1);
    /** The page shown on the client: null for the controller's own; on the server any page's slots take clicks. */
    private @Nullable Identifier page;

    /** Client side, with no inventory: no extension slots (a GameTest's). */
    public SpawnerControllerMenu(int containerId) {
        this(containerId, null, new SimpleContainerData(DATA_COUNT), null);
    }

    /** Client side. */
    public SpawnerControllerMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainerData(DATA_COUNT), null);
    }

    public SpawnerControllerMenu(int containerId, ContainerData data, @Nullable SpawnerControllerBlockEntity controller) {
        this(containerId, null, data, controller);
    }

    public SpawnerControllerMenu(int containerId, @Nullable Inventory inventory, ContainerData data,
            @Nullable SpawnerControllerBlockEntity controller) {
        super(ModMenus.SPAWNER_CONTROLLER.get(), containerId);
        this.data = data;
        this.controller = controller;
        this.addDataSlots(data);
        if (inventory == null || this.extensions.isEmpty()) {
            return;
        }
        // a bit set for each page left out: 0, all shown, until the server's first word (a data slot is 16 bits: the
        // extensions past the sixteenth are always shown)
        if (controller != null) {
            this.hidden = new ContainerData() {
                @Override
                public int get(int index) {
                    int bits = 0;
                    for (int i = 0; i < Math.min(16, SpawnerControllerMenu.this.extensions.size()); i++) {
                        if (!SpawnerControllerMenu.this.extensions.get(i).shown(controller)) {
                            bits |= 1 << i;
                        }
                    }
                    return bits;
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 1;
                }
            };
        }
        this.addDataSlots(this.hidden);
        for (SpawnerControllerExtensions.Extension extension : this.extensions) {
            int dataCount = Math.max(0, extension.dataCount());
            ContainerData given = controller == null ? null : extension.data(controller);
            // as many numbers as it says on both sides, whatever it hands over: a mismatch made the two sides' data
            // slots differ and threw the client out (r20)
            ContainerData numbers = given == null ? new SimpleContainerData(dataCount) : sized(given, dataCount);
            this.addDataSlots(numbers);
            this.extensionData.put(extension.id(), numbers);
            int count = extension.slotCount();
            Container container = controller == null ? null : extension.container(controller);
            if (container == null || container.getContainerSize() < count) {
                container = new SimpleContainer(count);
                if (controller != null) {
                    this.standIns.add(container);
                }
            }
            int from = this.slots.size();
            for (int i = 0; i < count; i++) {
                int[] at = extension.slotPosition(i);
                this.addSlot(new PageSlot(container, i, at[0], at[1], extension));
            }
            this.extensionSlots.add(new int[] {from, this.slots.size()});
        }
        this.playerSlotsFrom = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new InventorySlot(inventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new InventorySlot(inventory, col, INVENTORY_X + col * 18, INVENTORY_Y + 58));
        }
    }

    /** {@code data} seen as exactly {@code count} numbers: past its own end they read 0 and are not kept. */
    private static ContainerData sized(ContainerData data, int count) {
        if (data.getCount() == count) {
            return data;
        }
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index < data.getCount() ? data.get(index) : 0;
            }

            @Override
            public void set(int index, int value) {
                if (index < data.getCount()) {
                    data.set(index, value);
                }
            }

            @Override
            public int getCount() {
                return count;
            }
        };
    }

    /** The page shown (the client's screen sets it, and tells the server): null for the controller's own settings. */
    public void showPage(@Nullable Identifier page) {
        this.page = page;
    }

    /** The button id that tells the server page {@code page} is shown. */
    public int showPageButton(@Nullable Identifier page) {
        for (int i = 0; i < this.extensions.size(); i++) {
            if (this.extensions.get(i).id().equals(page)) {
                return SpawnerControllerExtensions.SHOW_PAGE + 1 + i;
            }
        }
        return SpawnerControllerExtensions.SHOW_PAGE;
    }

    public @Nullable Identifier page() {
        return this.page;
    }

    /** Whether extension {@code id}'s page is there on this controller (both sides; false for an id not registered). */
    public boolean pageShown(Identifier id) {
        for (int i = 0; i < this.extensions.size(); i++) {
            if (this.extensions.get(i).id().equals(id)) {
                return i >= 16 || (this.hidden.get(0) & 1 << i) == 0;
            }
        }
        return false;
    }

    /** The pages left out, as bits (the screen builds its tabs again when they change). */
    public int hiddenPages() {
        return this.hidden.get(0);
    }

    /** An extension's numbers, kept in step with the client, by its id ({@link SpawnerControllerExtensions}). */
    public @Nullable ContainerData extensionData(Identifier id) {
        return this.extensionData.get(id);
    }

    /** The controller this menu was opened on (server side; null on the client). */
    public @Nullable SpawnerControllerBlockEntity controller() {
        return this.controller;
    }

    private boolean shown(SpawnerControllerExtensions.Extension extension) {
        return this.pageShown(extension.id()) && (this.controller != null || extension.id().equals(this.page));
    }

    private boolean inventoryShown() {
        return this.controller != null || this.page != null
                && this.extensions.stream().anyMatch(e -> e.id().equals(this.page) && e.showsInventory());
    }

    /** One of an extension's slots: there only on its page. */
    private final class PageSlot extends Slot {
        private final SpawnerControllerExtensions.Extension extension;

        PageSlot(Container container, int index, int x, int y, SpawnerControllerExtensions.Extension extension) {
            super(container, index, x, y);
            this.extension = extension;
        }

        @Override
        public boolean isActive() {
            return SpawnerControllerMenu.this.shown(this.extension);
        }

        /** Nothing into a slot that would hold none of it (an add-on's slot closed for now, as an unpowered bank's). */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return this.isActive() && this.extension.mayPlace(this.getContainerSlot(), stack) && this.getMaxStackSize(stack) > 0;
        }
    }

    /** The player's inventory: there only on a page that asks for it. */
    private final class InventorySlot extends Slot {
        InventorySlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return SpawnerControllerMenu.this.inventoryShown();
        }
    }

    public boolean enabled() {
        return this.data.get(0) != 0;
    }

    public int intervalSeconds() {
        return this.data.get(1) / 20;
    }

    public int count() {
        return this.data.get(2);
    }

    public int maxNearby() {
        return this.data.get(3);
    }

    public boolean anyLight() {
        return this.data.get(4) != 0;
    }

    public boolean needsPlayer() {
        return this.data.get(5) != 0;
    }

    public int energy() {
        return (this.data.get(6) & 0xFFFF) | (this.data.get(11) & 0xFFFF) << 16;
    }

    /** The settings as synced, for what the screen works out of them (a spawner's cost). */
    public SpawnerSettings settings() {
        return new SpawnerSettings(this.enabled(), this.data.get(1), this.count(), this.maxNearby(), this.anyLight(), this.needsPlayer());
    }

    /** FE a tick it costs now, for all its spawners. */
    public int costPerTick() {
        return (this.data.get(12) & 0xFFFF) | this.data.get(13) << 16;
    }

    public int linked() {
        return this.data.get(7);
    }

    public boolean powered() {
        return this.data.get(8) != 0;
    }

    public RedstoneMode redstoneMode() {
        return RedstoneMode.byId(this.data.get(9));
    }

    public boolean heldByRedstone() {
        return this.data.get(10) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= SpawnerControllerExtensions.SHOW_PAGE && id < SpawnerControllerExtensions.FIRST_BUTTON) {
            int index = id - SpawnerControllerExtensions.SHOW_PAGE - 1;
            this.page = index >= 0 && index < this.extensions.size() && this.pageShown(this.extensions.get(index).id())
                    ? this.extensions.get(index).id() : null;
            return true;
        }
        if (id >= SpawnerControllerExtensions.FIRST_BUTTON) {
            int index = (id - SpawnerControllerExtensions.FIRST_BUTTON) / SpawnerControllerExtensions.BUTTONS;
            if (index < this.extensions.size() && this.pageShown(this.extensions.get(index).id())) {
                this.extensions.get(index).buttonPressed(this, this.controller, player,
                        (id - SpawnerControllerExtensions.FIRST_BUTTON) % SpawnerControllerExtensions.BUTTONS);
            }
            return true;
        }
        if (this.controller == null) {
            return true;
        }
        if (id == BUTTON_REDSTONE) {
            this.controller.setRedstoneMode(this.controller.redstoneMode().next());
            return true;
        }
        SpawnerSettings s = this.controller.settings();
        this.controller.setSettings(switch (id) {
            case TOGGLE_ENABLED -> s.withEnabled(!s.enabled());
            case INTERVAL_DOWN -> s.stepInterval(-1);
            case INTERVAL_UP -> s.stepInterval(1);
            case COUNT_DOWN -> s.withCount(s.count() - 1);
            case COUNT_UP -> s.withCount(s.count() + 1);
            case NEARBY_DOWN -> s.withMaxNearby(s.maxNearby() - 1);
            case NEARBY_UP -> s.withMaxNearby(s.maxNearby() + 1);
            case TOGGLE_LIGHT -> s.withAnyLight(!s.anyLight());
            case TOGGLE_PLAYER -> s.withNeedsPlayer(!s.needsPlayer());
            default -> s;
        });
        return true;
    }

    /**
     * Shift-click: between the player's inventory and the page's slots (the extensions' own, in order), as a machine's
     * do; nothing to move without extensions.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (this.playerSlotsFrom < 0 || index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        // into the slots of the page shown (the server is told it), or every extension's when it was not told
        int from = 0;
        int to = this.playerSlotsFrom;
        for (int i = 0; i < this.extensions.size(); i++) {
            if (this.extensions.get(i).id().equals(this.page)) {
                from = this.extensionSlots.get(i)[0];
                to = this.extensionSlots.get(i)[1];
            }
        }
        boolean moved = index >= this.playerSlotsFrom
                ? this.moveItemStackTo(stack, from, to, false)
                : this.moveItemStackTo(stack, this.playerSlotsFrom, this.slots.size(), true);
        // a move that took nothing is no move: vanilla's shift-click asks again while this returns something and the slot
        // still holds the same, and a slot that "took" none of it (an unpowered Data Bank's) held the server in that loop
        // for ever (r21, 2026-11-06)
        if (!moved || stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return before;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.controller == null || Container.stillValidBlockEntity(this.controller, player)
                && this.extensions.stream().allMatch(e -> e.stillValid(this.controller, player));
    }

    /** Closed: what went into a stand-in goes back to the player, and each extension is told. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.controller == null) {
            return;
        }
        for (Container standIn : this.standIns) {
            this.clearContainer(player, standIn);
        }
        for (SpawnerControllerExtensions.Extension extension : this.extensions) {
            extension.removed(this, this.controller, player);
        }
    }
}
