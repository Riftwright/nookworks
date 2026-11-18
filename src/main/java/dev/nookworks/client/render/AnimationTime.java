package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.level.Level;

/**
 * Time for what moves on the client, kept small. A float of the whole game time loses its fractions past 2^24 ticks
 * (233 hours of a world's time, which a long-kept server passes): animations stutter, then jump a tick at a time.
 */
public final class AnimationTime {
    /** An hour of game time: the view through a door or a mirror starts over once an hour, a moment nobody watches. */
    public static final long VIEW_CYCLE = 72_000L;

    private AnimationTime() {}

    /** The game time round a cycle of {@code period} ticks, and the part of a tick: for what repeats at that period. */
    public static float cycle(@Nullable Level level, float partialTicks, long period) {
        return level == null ? 0 : Math.floorMod(level.getGameTime(), period) + partialTicks;
    }

    /** Ticks since {@code since} (a game time), worked out in whole ticks before the fraction is added. */
    public static float since(@Nullable Level level, float partialTicks, long since) {
        return level == null ? 0 : (float) (level.getGameTime() - since) + partialTicks;
    }
}
