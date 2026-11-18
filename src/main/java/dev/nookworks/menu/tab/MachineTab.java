package dev.nookworks.menu.tab;

import java.util.Locale;

/**
 * The tabs on a machine screen's right edge, top to bottom (the user's design, 2026-11-08): Settings (調整) - the
 * redstone mode and what an add-on sets; Liquid (液体) - a bucket or bottle filled from the machine's tanks or poured
 * into them by hand; Add-on (アドオン) - the slots an add-on fits the machine with. Their order is the order they stand
 * in; new ones go last.
 */
public enum MachineTab {
    SETTINGS,
    LIQUID,
    ADDON;

    public static MachineTab byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : SETTINGS;
    }

    /** The tab's name, shown on hover. */
    public String translationKey() {
        return "gui.nookworks.tab." + this.name().toLowerCase(Locale.ROOT);
    }
}
