package dev.nookworks.machine;

import dev.nookworks.client.NookworksClientConfig;

/**
 * The sounds machines make now and then as they work ({@code MachineBlock.soundNowAndThen}, ours and an add-on's), as
 * the player wants them: how loud and how often (client settings), and never more than a few starting in any one second,
 * however many machines stand about (the user, 2026-11-10: 「できそうなことはぜんぶやっていいよ」, of the sound session's
 * survey - the only way to turn the machines down was vanilla's whole Blocks slider, and a hall of thirty machines starts
 * about a phrase a second, two overlapping much of the time). Client side only.
 */
public final class WorkSounds {
    /** At most this many work sounds start in any second, whatever the number of machines in hearing. */
    public static final int MAX_PER_SECOND = 3;
    private static final long[] STARTS = new long[MAX_PER_SECOND];
    private static int next;

    static {
        java.util.Arrays.fill(STARTS, Long.MIN_VALUE / 2);
    }

    private WorkSounds() {}

    /** {@code base}, the chance a machine's animate tick starts its sound, with the frequency setting. */
    public static float chance(float base) {
        return base * percent(NookworksClientConfig.MACHINE_SOUND_FREQUENCY, 100) / 100.0F;
    }

    /** The volume the setting asks, 0 to 1; 0 is off. */
    public static float volume() {
        return percent(NookworksClientConfig.MACHINE_SOUND_VOLUME, 100) / 100.0F;
    }

    /**
     * Whether one more work sound may start at game time {@code now}: not when {@link #MAX_PER_SECOND} have started in the
     * 20 ticks before it. Counts it if so. A start after {@code now} is another world's (one joined since, its clock
     * behind): no reason to wait - held against it, every work sound stayed silent until the new world's clock caught up.
     */
    public static boolean mayStart(long now) {
        long since = now - STARTS[next];
        if (since >= 0 && since < 20) {
            return false;
        }
        STARTS[next] = now;
        next = (next + 1) % MAX_PER_SECOND;
        return true;
    }

    /** Forgets what started, for tests. */
    public static void reset() {
        java.util.Arrays.fill(STARTS, Long.MIN_VALUE / 2);
        next = 0;
    }

    private static int percent(net.neoforged.neoforge.common.ModConfigSpec.IntValue value, int fallback) {
        return NookworksClientConfig.SPEC.isLoaded() ? value.get() : fallback;
    }
}
