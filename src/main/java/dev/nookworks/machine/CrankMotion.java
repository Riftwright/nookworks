package dev.nookworks.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * How far a machine's handle has gone round, for drawing it: a quarter turn each time it is worked, eased over the
 * ticks until it is worked again, so a handle cranked steadily goes round steadily and one worked now and then moves
 * and stops. The server says when with a block event ({@link #announce}); the count is the client's alone, and only
 * how the handle looks depends on it.
 */
public final class CrankMotion {
    /** The block event a turn is announced with. */
    public static final int EVENT = 7;
    /** Ticks a quarter turn takes: as long as holding the use key takes to work it again. */
    private static final float TICKS = 4.0F;

    private int turns;
    private long last;
    /** What the handle is drawn turned by beyond the hand's count: where a drive left it, so it goes on from there. */
    private float offset;
    /** How far the handle is ahead of the drive turning it: where the hand had it when the drive took it up. */
    private float lead;
    private boolean driven;

    /** Tells the players near {@code pos} that the handle there has just gone round a quarter. */
    public static void announce(Level level, BlockPos pos, BlockState state) {
        level.blockEvent(pos, state.getBlock(), EVENT, 0);
    }

    /** On the client: the handle has been worked, at {@code gameTime}. */
    public void turned(long gameTime) {
        this.turns++;
        this.last = gameTime;
    }

    /** Where a drive has the handle now, as an offset from the hand's count: the hand carries on from there. */
    public void follow(float offset) {
        this.offset = offset;
    }

    /**
     * The handle as a drive at {@code shaft} turns it: on from where it was ({@code handle}, worked by hand) when the
     * drive took it up, so it does not jump back to the drive's angle. On the client, each frame it is driven.
     */
    public float driven(float shaft, float handle) {
        if (!this.driven) {
            this.lead = handle - shaft;
            this.driven = true;
        }
        return shaft + this.lead;
    }

    /** No drive turns it now: next time one does, it takes the handle up where it is then. */
    public void released() {
        this.driven = false;
    }

    public float offset() {
        return this.offset;
    }

    /** How far round the handle is now, in degrees. */
    public float degrees(long gameTime, float partialTick) {
        if (this.turns == 0) {
            return 0;
        }
        float t = Math.min(1.0F, Math.max(0.0F, (gameTime - this.last + partialTick) / TICKS));
        return (this.turns - 1 + t * t * (3 - 2 * t)) * 90.0F;
    }
}
