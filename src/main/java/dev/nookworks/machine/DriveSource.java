package dev.nookworks.machine;

import net.minecraft.core.Direction;

/**
 * What turns a hand-worked machine's axle: a Motor's shaft, or a Drive Wheel's. One way in for every source of
 * turning, so that another mod's (Create's shafts, once it is on 26.x - docs/compat.md) can come in here too.
 * <p>
 * The angle is the client's, for drawing: shaft, wheel, belt, axle and handle all go round by it about the source's
 * line, turned about the positive end of that line ({@link #spinAxis}), so everything along one line turns one way.
 */
public interface DriveSource {
    /** How far round it is now, in degrees about {@link #spinAxis}. */
    float shaftDegrees(float partialTick);

    /** Whether it is going round now (on the client): standing still, a machine on it is free to be turned by hand. */
    boolean turning();

    /** The line it turns about, as the positive direction of its axis. */
    Direction spinAxis();

    static Direction positive(Direction.Axis axis) {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }
}
