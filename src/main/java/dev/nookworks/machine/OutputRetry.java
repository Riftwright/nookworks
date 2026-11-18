package dev.nookworks.machine;

import net.minecraft.world.level.Level;

/**
 * When a machine next passes what it has made on to the container below. With no room there - the chest under a line
 * of Sifters filled up, the usual state of a backed-up line - every output slot was tried again every tick, each try a
 * capability lookup, a transaction and a look through the whole container, for as long as it stayed full (nine a tick
 * for a Sifter, r7 review). After a try that moved nothing it waits a hopper's beat instead, as a hopper does; while
 * anything moves, it tries each tick. Not saved: a machine just loaded tries at once.
 */
public final class OutputRetry {
    /** A hopper's beat, in ticks. */
    public static final int BEAT = 8;
    private long at;

    /** Whether the outputs are to be tried this tick. */
    public boolean due(Level level) {
        return level.getGameTime() >= this.at;
    }

    /** After a try: {@code stuck} - something waited, and none of it moved - the next is a beat off; else next tick. */
    public void tried(Level level, boolean stuck) {
        this.at = stuck ? level.getGameTime() + BEAT : 0;
    }
}
