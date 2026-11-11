package dev.nookworks.client.gui.tab;

/**
 * A screen with machine tabs on its right edge ({@link ScreenTabs}): every Nookworks machine screen's, and any add-on's
 * built on them. JEI keeps its item list off the tabs of a screen that is one ({@code ScreenTabs.areas()}).
 */
public interface TabbedScreen {
    ScreenTabs tabs();
}
