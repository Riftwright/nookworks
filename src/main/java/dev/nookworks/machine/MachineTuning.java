package dev.nookworks.machine;

import dev.nookworks.NookworksConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The server's say over our machines (the {@code machines} settings): how much power they use, how long they take
 * over a job, and how much a Generator makes - for a pack whose other mods make power cheaply or dearly. Every figure a
 * machine works by goes through here; at the defaults they are as made. The settings travel to the players with the
 * rest of the server's, so the screens show the figures the server works by.
 */
public final class MachineTuning {
    private MachineTuning() {}

    /** Power a machine uses: a tick's work, a plant, a repair step, a felled tree, a controlled spawner. */
    public static int energy(int base) {
        return scaled(base, NookworksConfig.MACHINE_ENERGY);
    }

    /**
     * Power a machine draws each tick of a job whose length {@link #time} sets: spread over the job, so the job costs
     * what it did however long the workTime setting makes it (the user, 2026-10-12: a slower machine costing more for the
     * same work made no sense - at four times the time and four times the power, biodiesel ran at a loss).
     */
    public static int workEnergy(int base) {
        return workEnergy(base, factor(NookworksConfig.MACHINE_ENERGY), factor(NookworksConfig.MACHINE_TIME));
    }

    /** {@link #workEnergy(int)} at the settings given. Public for tests. */
    public static int workEnergy(int base, double energyUse, double workTime) {
        return scale(base, energyUse / workTime);
    }

    /** {@link #workEnergy(int)} unrounded: what a tick of a job owes, fractions of a FE and all (MachineBlockEntity#payWork). */
    public static double workEnergyExact(int base) {
        return workEnergyExact(base, factor(NookworksConfig.MACHINE_ENERGY), factor(NookworksConfig.MACHINE_TIME));
    }

    /** {@link #workEnergyExact(int)} at the settings given. Public for tests. */
    public static double workEnergyExact(int base, double energyUse, double workTime) {
        return base * energyUse / workTime;
    }

    /**
     * The whole FE due this tick: what is {@code owed} from the ticks before and {@code exact} more, rounded down - the
     * rest carried on, so a job pays its exact cost (r14: each tick rounded on its own, and never under 1 FE, a slow job
     * at a low energyUse cost five to ten times its due). Public for tests.
     */
    public static int due(double owed, double exact) {
        return (int) Math.floor(owed + exact + 1e-9);
    }

    /**
     * How far along {@code progress} of {@code max} is, in percent, 0 to 100: with the workTime setting lowered mid-piece
     * a machine's progress may stand past its new time for the tick before it finishes, and read 120 % meanwhile.
     */
    public static int percent(int progress, int max) {
        return max <= 0 ? 0 : (int) Math.clamp(progress * 100L / max, 0, 100);
    }

    /** Ticks a machine takes over a job, or between one and the next. */
    public static int time(int base) {
        return scaled(base, NookworksConfig.MACHINE_TIME);
    }

    /** Power a Generator or Diesel Generator makes a tick. */
    public static int generated(int base) {
        return scaled(base, NookworksConfig.GENERATOR_OUTPUT);
    }

    /** Whether any is set away from its default: JEI's pages, whose figures are the defaults', then say so. */
    public static boolean tuned() {
        return factor(NookworksConfig.MACHINE_ENERGY) != 1.0 || factor(NookworksConfig.MACHINE_TIME) != 1.0
                || factor(NookworksConfig.GENERATOR_OUTPUT) != 1.0;
    }

    /** A setting's value; its default before the server's settings are there to read (a client in the menus). */
    public static double factor(ModConfigSpec.DoubleValue value) {
        try {
            return value.getAsDouble();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    private static int scaled(int base, ModConfigSpec.DoubleValue value) {
        return scale(base, factor(value));
    }

    /** {@code base} times {@code factor}, to the nearest whole, never below 1. Public for tests. */
    public static int scale(int base, double factor) {
        return factor == 1.0 ? base : Math.max(1, (int) Math.round(base * factor));
    }
}
