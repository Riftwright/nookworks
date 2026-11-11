package dev.nookworks.menu.tab;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.menu.ShiftClick;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The menu's side of a machine screen's tabs (the user's design, 2026-11-08/10): which of the {@link MachineTab}s the
 * screen has, which one is open (one at a time, known on the server too, so a shift-click goes only into an open tab),
 * the slots each holds and where they stand. Built the same on the client and the server, in the menu's constructor -
 * or by an add-on's mixin at its RETURN - so the slots' indices and places match.
 * <p>
 * The tabs stand on the panel's right edge, {@link #TAB} square and {@link #GAP} apart from {@link #TOP} down; a tab
 * opens into a panel growing out to the right, and only the tabs under it move down. A slot's place is final, so a
 * tab's slots are placed where they stand while it is open: as nothing above an open tab is open, that place never
 * changes. Settings is the menu's to {@link #enable}, the Liquid tab comes with {@link LiquidTab#add}, the Add-on tab
 * with its first slot ({@link #nextAddonSpot}); decide them before any slot is placed under them - a tab turned on above
 * a tab that already has slots throws, as those slots would stand where the tab no longer is (enable Settings, then
 * {@code LiquidTab.add}, then the Add-on slots; an add-on's mixin at the constructor's RETURN comes after both).
 * <p>
 * Menu button ids {@link #BUTTON_TABS} (20: close; 21-23: open a tab by its ordinal) and {@link #BUTTON_LIQUID_CHOICE}
 * (30-39: draw from tank n) are the tabs'; 100-119 stay nookTechnology's (DECISIONS).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class MenuTabs {
    /** Close every tab; this plus one plus a tab's ordinal opens it. */
    public static final int BUTTON_TABS = 20;
    /** This plus a tank's index: draw from that tank in the Liquid tab. */
    public static final int BUTTON_LIQUID_CHOICE = 30;
    /** A shut tab's size, the gap between tabs, and the first tab's distance from the panel's top. */
    public static final int TAB = 24;
    public static final int GAP = 2;
    public static final int TOP = 4;
    /** Add-on slots to a row. */
    public static final int ADDON_COLUMNS = 4;

    private final int imageWidth;
    private final Set<MachineTab> shown = EnumSet.noneOf(MachineTab.class);
    private final List<TabSlot> slots = new ArrayList<>();
    private @Nullable MachineTab open;
    private @Nullable LiquidTab liquid;
    private int addonSlots;

    /** The tabs of a menu whose screen's panel is {@code imageWidth} wide. */
    public MenuTabs(int imageWidth) {
        this.imageWidth = imageWidth;
    }

    public int imageWidth() {
        return this.imageWidth;
    }

    /**
     * Gives the screen {@code tab}. Settings is the menu's to give; the others come with what goes in them.
     *
     * @throws IllegalStateException when a tab under {@code tab} already has slots: they were placed for a column without
     *         it, and would stand a tab too high
     */
    public void enable(MachineTab tab) {
        if (this.shown.contains(tab)) {
            return;
        }
        for (MachineTab below : MachineTab.values()) {
            if (below.ordinal() > tab.ordinal() && (!this.slots(below).isEmpty() || below == MachineTab.ADDON && this.addonSlots > 0)) {
                throw new IllegalStateException("the " + tab + " tab must be turned on before any slot of the " + below
                        + " tab is placed (enable Settings, then LiquidTab.add, then the Add-on slots)");
            }
        }
        this.shown.add(tab);
    }

    /** Whether the screen has {@code tab}: one with nothing in it is not drawn. */
    public boolean has(MachineTab tab) {
        return this.shown.contains(tab);
    }

    /** The tabs the screen has, top to bottom. */
    public List<MachineTab> shown() {
        return List.copyOf(this.shown);
    }

    public @Nullable MachineTab open() {
        return this.open;
    }

    public boolean isOpen(MachineTab tab) {
        return this.open == tab;
    }

    /** Opens {@code tab} (null: none). The client sets it as it sends the button; the server by the button. */
    public void setOpen(@Nullable MachineTab tab) {
        this.open = tab == null || this.has(tab) ? tab : null;
    }

    /** The menu button that opens {@code tab}, or closes every tab for null. */
    public static int button(@Nullable MachineTab tab) {
        return tab == null ? BUTTON_TABS : BUTTON_TABS + 1 + tab.ordinal();
    }

    /** A menu button of the tabs': true if it was one (the menu then does nothing more with it). */
    public boolean clickMenuButton(Player player, int id) {
        if (id >= BUTTON_TABS && id <= BUTTON_TABS + MachineTab.values().length) {
            MachineTab tab = id == BUTTON_TABS ? null : MachineTab.byId(id - BUTTON_TABS - 1);
            if (tab != null && !this.has(tab)) {
                return false;
            }
            this.open = tab;
            return true;
        }
        if (id >= BUTTON_LIQUID_CHOICE && id < BUTTON_LIQUID_CHOICE + LiquidTab.MAX_TANKS && this.liquid != null) {
            return this.liquid.choose(id - BUTTON_LIQUID_CHOICE);
        }
        return false;
    }

    // ---- where things stand (relative to the screen's panel, as a slot's x and y are) ----

    /** The top of {@code tab} while no tab above it is open. */
    public int tabTop(MachineTab tab) {
        int y = TOP;
        for (MachineTab above : MachineTab.values()) {
            if (above == tab) {
                break;
            }
            if (this.has(above)) {
                y += TAB + GAP;
            }
        }
        return y;
    }

    /** The left of an open tab's contents: an item's corner, two pixels into its face. */
    public int contentX() {
        return this.imageWidth + 2;
    }

    /** The top of an open tab's contents, under its icon: an item's corner. */
    public int contentY(MachineTab tab) {
        return this.tabTop(tab) + 22;
    }

    /**
     * Where the next slot of the Add-on tab goes (an item's corner, x then y): four to a row under its icon. Gives the
     * screen its Add-on tab. Asked once for each slot, in the order they are added.
     */
    public int[] nextAddonSpot() {
        this.enable(MachineTab.ADDON);
        int i = this.addonSlots++;
        return new int[] {this.contentX() + i % ADDON_COLUMNS * 18, this.contentY(MachineTab.ADDON) + i / ADDON_COLUMNS * 18};
    }

    /** Records {@code slot} as one of a tab's (for the shift-click rules) and gives it back, for the menu's addSlot. */
    public <S extends TabSlot> S add(S slot) {
        this.enable(slot.tab());
        this.slots.add(slot);
        return slot;
    }

    /** The slots of {@code tab}, in the order added. */
    public List<TabSlot> slots(MachineTab tab) {
        return this.slots.stream().filter(slot -> slot.tab() == tab).toList();
    }

    public @Nullable LiquidTab liquid() {
        return this.liquid;
    }

    void setLiquid(LiquidTab liquid) {
        this.liquid = liquid;
        this.enable(MachineTab.LIQUID);
    }

    // ---- shift-clicks ----

    /**
     * A shift-click as the tabs see it, for the menu's {@code quickMoveStack} to ask first; null when the tabs have no
     * say. Out of a tab's slot: into the inventory (from {@code inventoryStart}, its 36 slots), hotbar first, as a
     * furnace's result. From the inventory, with a tab open: into that tab's slots that take it, a container at a time
     * (each slot's own size holds), before the machine's inputs - and then {@code ItemStack.EMPTY}, so vanilla's
     * shift-click loop stops there and the rest of the stack does not go on into the machine. A shut tab is never a
     * target (the user, 2026-11-10: the Liquid tab's slots only while it is open).
     * <p>
     * A part - what a slot of the Add-on tab takes, full or not - never goes into the machine's own slots by a shift-click:
     * with the Add-on tab open it goes there or stays put; shut, it crosses between the inventory and the hotbar, as what
     * the machine does not take. Five gold gears shift-clicked at a Recycler fitted one and scrapped four (the user's
     * complaint, which nookTechnology's own guard answered before the tabs).
     */
    public @Nullable ItemStack quickMove(Slot clicked, int inventoryStart, ShiftClick.Mover mover) {
        if (!clicked.hasItem()) {
            return null;
        }
        ItemStack stack = clicked.getItem();
        if (clicked instanceof TabSlot tabbed && this.slots.contains(tabbed)) {
            ItemStack original = stack.copy();
            if (!mover.move(stack, inventoryStart, inventoryStart + ShiftClick.INVENTORY_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                clicked.setByPlayer(ItemStack.EMPTY);
            } else {
                clicked.setChanged();
            }
            return original;
        }
        if (clicked.index < inventoryStart || clicked.index >= inventoryStart + ShiftClick.INVENTORY_SLOTS) {
            return null;
        }
        boolean part = this.isPart(stack);
        if (part && this.open != MachineTab.ADDON) {
            ItemStack original = stack.copy();
            if (!ShiftClick.acrossInventory(stack, clicked.index, inventoryStart, mover)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                clicked.setByPlayer(ItemStack.EMPTY);
            } else {
                clicked.setChanged();
            }
            return original;
        }
        if (this.open == null) {
            return null;
        }
        boolean moved = false;
        for (TabSlot slot : this.slots) {
            if (slot.tab() == this.open && !stack.isEmpty() && slot.mayPlace(stack)) {
                moved |= mover.move(stack, slot.index, slot.index + 1, false);
            }
        }
        if (!moved) {
            return part ? ItemStack.EMPTY : null; // a part with its slots full stays where it is
        }
        if (stack.isEmpty()) {
            clicked.setByPlayer(ItemStack.EMPTY);
        } else {
            clicked.setChanged();
        }
        return ItemStack.EMPTY;
    }

    /** Whether {@code stack} is a part: something a slot of the Add-on tab takes, full or not. */
    public boolean isPart(ItemStack stack) {
        for (TabSlot slot : this.slots) {
            if (slot.tab() == MachineTab.ADDON && slot.mayPlace(stack)) {
                return true;
            }
        }
        return false;
    }

    // ---- the server's tick, and closing ----

    /** A player tick with the menu open: the Liquid tab's timers. */
    public void tick(ServerPlayer player) {
        if (this.liquid != null) {
            this.liquid.tick(player);
        }
    }

    /** The menu closed: what is left in the Liquid tab goes back to the player (dropped if they are gone). */
    public void removed(Player player) {
        if (this.liquid != null) {
            this.liquid.giveBack(player);
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.containerMenu instanceof TabbedMenu menu) {
            menu.tabs().tick(player);
        }
    }
}
