package dev.nookworks.machine;

import java.util.Locale;

/**
 * What a machine is doing right now, and if it has stopped, why - shown on its screen, so a stalled line can be
 * read at a glance instead of worked out.
 */
public enum MachineStatus {
    RUNNING(Tone.GOOD),
    /** Nothing to work on. */
    IDLE(Tone.QUIET),
    NO_FUEL(Tone.QUIET),
    /** A generator with nowhere to put more power. */
    FULL(Tone.QUIET),
    REDSTONE(Tone.QUIET),
    NO_POWER(Tone.BAD),
    OUTPUT_BLOCKED(Tone.WARN),
    NO_REAGENT(Tone.WARN),
    NO_MERCURY(Tone.WARN),
    MERCURY_FULL(Tone.WARN),
    /** A planter with seeds but no free soil in front of it. */
    NO_SPACE(Tone.WARN),
    /** A Vacuum Distiller without its tower. */
    INCOMPLETE(Tone.BAD),
    /** A Water Pump with no water source under it. */
    NO_WATER(Tone.WARN),
    /** Waiting for the chunk next door to load before it can tell whether its water is refilled. */
    UNLOADED(Tone.QUIET),
    /** A Repair Box with gear to mend and no experience to mend it with. */
    NO_EXPERIENCE(Tone.WARN),
    /** A tank with no room for what the machine makes: pumped water, pressed oil, stirred biodiesel. */
    TANK_FULL(Tone.QUIET),
    /** A Laser with nothing in its beam to cut, burn or hurt: it waits on a trickle of power, the beam off. */
    STANDBY(Tone.QUIET),
    /** A Laser at a tree, storing the surge to fell it: the beam off until it has it. */
    CHARGING(Tone.QUIET),
    /** A Planter whose field is someone else's land (a claim, spawn protection): it plants nothing there. */
    PROTECTED(Tone.WARN),
    /** A Generator whose cables run on past the reach a network is followed through: machines further on get nothing. */
    TOO_MANY_CABLES(Tone.WARN),
    /** A machine whose water tank, filled by pipe or bucket, has too little in it for a run (not a pump's source below). */
    LOW_WATER(Tone.WARN),
    /** A Motor standing by: another motor turns the machine it is set against. */
    ANOTHER_MOTOR(Tone.QUIET),
    /** A Barter Terminal with no Ventilation Fan at the end of its ducts, nor against it. */
    NO_FAN(Tone.BAD),
    /** A Barter Terminal's room with a gap in it; the Nether's air is seen leaking out there. */
    ROOM_OPEN(Tone.BAD),
    /** A Barter Terminal's room too big, too small or not a clean cuboid inside. */
    ROOM_SIZE(Tone.BAD),
    /** A Barter Terminal's room floored with something other than the Nether's ground. */
    NETHER_FLOOR(Tone.BAD),
    /** A Barter Terminal's room with no grown piglin in it to barter. */
    NO_PIGLINS(Tone.QUIET),
    /** A Barter Terminal's room with more piglins in it than its floor takes: kept, but not bartering. */
    CROWDED(Tone.WARN),
    /** A Barter Terminal's room on land a claim keeps from its owner: nothing handed in or taken out. */
    ROOM_PROTECTED(Tone.WARN),
    /**
     * A Stirring Tank set to make biodiesel, its methanol and caustic soda in, with less vegetable oil in its tank than a
     * batch takes (it said "idle", as if nothing were in it, r8).
     */
    LOW_OIL(Tone.WARN),
    /** A Compressor with fewer than nine of a thing it packs: waiting for the rest (it said "idle, nothing in it"). */
    NEEDS_NINE(Tone.QUIET);

    public enum Tone {
        GOOD, QUIET, WARN, BAD
    }

    private final Tone tone;

    MachineStatus(Tone tone) {
        this.tone = tone;
    }

    public Tone tone() {
        return this.tone;
    }

    public String translationKey() {
        return "gui.nookworks.machine.status." + this.name().toLowerCase(Locale.ROOT);
    }

    public static MachineStatus byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IDLE;
    }
}
