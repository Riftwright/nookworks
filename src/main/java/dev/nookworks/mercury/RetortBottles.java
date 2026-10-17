package dev.nookworks.mercury;

import java.util.Locale;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Where a glass bottle a hopper or pipe puts into the Sealed Retort goes, set from its screen (the user, 2026-10-12: a
 * biodiesel line lost its bottles to the fill box whenever the logs ran out). By hand a bottle goes where it is put.
 */
public enum RetortBottles {
    /** With logs in, to char them with; otherwise into the fill box. As it always was. */
    AUTO,
    /** Only into the fill box, to be filled with the experience: logs get no bottle from a hopper. */
    EXPERIENCE,
    /** Only to the reagents, for wood vinegar, waiting there for the logs; never into the fill box. */
    VINEGAR;

    public static final RetortBottles DEFAULT = AUTO;
    private static final String KEY = "Bottles";

    public RetortBottles next() {
        return values()[(this.ordinal() + 1) % values().length];
    }

    public String translationKey() {
        return "gui.nookworks.retort.bottles." + this.name().toLowerCase(Locale.ROOT);
    }

    public static RetortBottles byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : DEFAULT;
    }

    public static RetortBottles load(ValueInput input) {
        String name = input.getStringOr(KEY, DEFAULT.name());
        for (RetortBottles mode : values()) {
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
