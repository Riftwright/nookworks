package dev.nookworks.menu.tab;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;

/**
 * A slot inside one of a machine screen's tabs: drawn, hovered and clicked only while its tab is open. Where it stands
 * is fixed (a slot's x and y are final): {@link MenuTabs} gives the spots. An add-on's slot class for the Add-on tab
 * extends this one and is registered with {@link MenuTabs#add}.
 */
public class TabSlot extends Slot {
    private final MenuTabs tabs;
    private final MachineTab tab;

    public TabSlot(MenuTabs tabs, MachineTab tab, Container container, int slot, int x, int y) {
        super(container, slot, x, y);
        this.tabs = tabs;
        this.tab = tab;
    }

    public MachineTab tab() {
        return this.tab;
    }

    @Override
    public boolean isActive() {
        return this.tabs.isOpen(this.tab);
    }
}
