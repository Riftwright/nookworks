package dev.nookworks.machine;

import java.util.Locale;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * How a machine answers redstone, set from its screen. The default runs until it gets a signal, so a lever
 * works as an off switch and a machine nobody wired up simply runs.
 */
public enum RedstoneMode {
    /** Runs whatever the signal. */
    ALWAYS,
    /** Runs only while powered. */
    WITH_SIGNAL,
    /** Runs until powered. */
    WITHOUT_SIGNAL,
    /** Never runs: a machine parked without breaking it. */
    NEVER;

    public static final RedstoneMode DEFAULT = WITHOUT_SIGNAL;
    private static final String KEY = "Redstone";

    public boolean allows(boolean powered) {
        return switch (this) {
            case ALWAYS -> true;
            case WITH_SIGNAL -> powered;
            case WITHOUT_SIGNAL -> !powered;
            case NEVER -> false;
        };
    }

    public RedstoneMode next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "gui.nookworks.redstone." + this.name().toLowerCase(Locale.ROOT);
    }

    public static RedstoneMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : DEFAULT;
    }

    public static RedstoneMode load(ValueInput input) {
        String name = input.getStringOr(KEY, DEFAULT.name());
        for (RedstoneMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return DEFAULT;
    }

    public void save(ValueOutput output) {
        output.putString(KEY, this.name());
    }
}
