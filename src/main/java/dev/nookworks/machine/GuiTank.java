package dev.nookworks.machine;

import java.util.Locale;

/**
 * One tank as a machine's screen shows it: what it holds, how much, and how much it takes. A machine lists its
 * tanks in the order its {@link MachineLayout} places them ({@link MachineInfo#guiTanks()}).
 */
public record GuiTank(Kind kind, int amount, int capacity) {
    /** What a tank holds, for its colour, its icon and its name. */
    public enum Kind {
        MERCURY,
        WATER,
        VEGETABLE_OIL,
        BIODIESEL,
        /** A fuel tank with nothing in it yet: oil or biodiesel, whichever comes. */
        LIQUID_FUEL,
        EXPERIENCE,
        /** The Nether's air a Barter Terminal keeps its room filled with, in ticks it lasts. */
        NETHER_AIR;

        public static Kind byId(int id) {
            return id >= 0 && id < values().length ? values()[id] : WATER;
        }

        public String translationKey() {
            return "gui.nookworks.tank." + this.name().toLowerCase(Locale.ROOT);
        }
    }
}
