package dev.nookworks.menu.tab;

/**
 * A menu whose screen has machine tabs ({@link MenuTabs}): every Nookworks machine screen's, and any add-on's that
 * builds its own on them. Its screen side is {@code dev.nookworks.client.gui.tab.TabbedScreen}.
 */
public interface TabbedMenu {
    MenuTabs tabs();
}
