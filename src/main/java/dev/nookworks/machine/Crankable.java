package dev.nookworks.machine;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;

/**
 * A machine worked by a handle: turned by hand (right-click with an empty hand) or by a Motor set against it - or a
 * Drive Wheel a Motor turns by a belt. One turn is one turn either way; the motor just keeps turning.
 */
public interface Crankable {
    /** Whether a turn now would do anything; a motor waits, and spends nothing, while it would not. */
    boolean wantsTurn();

    /** One turn of the handle. */
    void turn(ServerLevel level);

    /** How far round the handle is, for drawing it. */
    CrankMotion motion();

    /**
     * Whether a drive on its {@code side} turns it: one that meets what turns in it. A machine with a crank on a level
     * shaft is driven from its sides; the Hand Quern, whose stone turns on an upright spindle, only from above or
     * below (the user, 2026-10-10: a shaft from the side met nothing, and one from above nothing in a polisher).
     */
    default boolean drivenFrom(Direction side) {
        return side.getAxis().isHorizontal();
    }
}
