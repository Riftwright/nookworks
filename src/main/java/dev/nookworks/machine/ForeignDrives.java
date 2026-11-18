package dev.nookworks.machine;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.drive.DriveWheelBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Another mod's turning and ours, met with nothing in between: in the 1.21.1 port with Create installed, our turning
 * parts take part in Create's own rotation network (dev.nookworks.compat.create; the design:
 * tools/port1211/design_create.md, "Ends and trains"). Without such a mod nothing is set here and every call is a field
 * read.
 * <p>
 * The one notion is an <b>end</b>: a face of a block of ours where turning goes in or out (a Motor's axle, a Drive
 * Wheel's axle end, a hand machine's drive side; an add-on's own). Everything of ours coupled to turn together is a
 * <b>train</b>. A train with a Motor gives at its ends ({@link #motorOutputs}: the other mod's block there is driven
 * where it stands); one with none takes ({@link #tickMachine}, {@link #tickWheel}, {@link #taken}) and passes turning
 * on between its ends ({@link #join}). The machines and wheels also ask {@link Drive#at} whether a block beside them
 * drives them (so they show their axle there), and the renderers the drive's angle ({@link #source}).
 */
public final class ForeignDrives {
    private ForeignDrives() {}

    /** What another mod's drives answer. */
    public interface Hook {
        /** Whether the block at {@code at} ({@code state}) drives a machine or wheel on its {@code side} of it. */
        boolean drives(LevelReader level, BlockPos at, BlockState state, Direction side);

        /** A hand-worked machine whose drive is on {@code side}: turned this tick, if a drive of this mod's is there. */
        void tickMachine(ServerLevel level, BlockEntity machine, Direction side);

        /** A Drive Wheel, each tick, on the server and on the client: turned with its line if one of these drives is on its axle. */
        void tickWheel(Level level, DriveWheelBlockEntity wheel);

        /** The drive at {@code at}, for drawing what it turns on its {@code side}; null if none of this mod's is there. */
        @Nullable DriveSource source(BlockGetter level, BlockPos at, Direction side);

        /**
         * What a Motor at {@code motor} pointing {@code facing} turns of this mod's (the port's Create: any block of its
         * with a shaft end toward ours): the block its axle points into when {@code wheels} is empty, else the block
         * beyond each axle end of each of its line's wheels, and an add-on's further places ({@link Relay#motorPlaces}).
         */
        default List<Output> motorOutputs(ServerLevel level, BlockPos motor, Direction facing, List<DriveWheelBlockEntity> wheels) {
            return List.of();
        }

        /**
         * For a taker of an add-on's own at {@code at} (a belt's gearbox, a line's end piece): the drive of this mod's at
         * its {@code side}, seen through relays, with its speed there; null if none. On the server it also keeps that
         * drive's load current from now on ({@link Relay#load} is asked for it).
         */
        default @Nullable Taken taken(Level level, BlockPos at, Direction side) {
            return null;
        }

        /**
         * A train with no Motor, each server tick, with this mod's blocks at its ends {@code ends}: they are joined to each
         * other in that mod's own network, as through one of its own shafts, the train's sign between them; what the
         * train does with them is answered ({@link Bridge}). Two that already turn against each other are not joined.
         */
        default Bridge join(ServerLevel level, List<Meet> ends) {
            return Bridge.NONE;
        }

        /** {@link #join} for a train of two ends, saying which end the turning comes from. */
        default Bridge bridge(ServerLevel level, BlockPos a, Direction outA, BlockPos b, Direction outB, int sign) {
            return this.join(level, List.of(new Meet(a, outA, 1), new Meet(b, outB, sign)));
        }
    }

    /**
     * Where a train's end meets another mod's block: that block's place {@code at}, the way {@code out} from our end
     * toward it, and {@code sign}, how the train's turning shows there about the positive end of {@code out}'s axis
     * (one end's sign times another's is what the train makes of a turn from the one to the other).
     */
    public record Meet(BlockPos at, Direction out, int sign) {}

    /**
     * What a drive of another mod's gives a taker of an add-on's: where it is, its speed at the taker's side in RPM about
     * the positive end of that side's axis (0 when it does not turn, overstressed among others), and whether its network
     * is overstressed.
     */
    public record Taken(BlockPos drive, float rpm, boolean overstressed) {}

    /** What a train with another mod's blocks at two or more ends does ({@link Hook#join}). */
    public enum Bridge {
        /** Fewer than two of its ends meet that mod's blocks, or none of them turns. */
        NONE,
        /** Joined and turning, the turning coming in at the {@code a} end ({@link Hook#bridge} only). */
        A_TO_B,
        /** Joined and turning, the turning coming in at the {@code b} end ({@link Hook#bridge} only). */
        B_TO_A,
        /** Joined and turning with them. */
        TOGETHER,
        /** Two of its ends turn it against each other (another speed, or another way through the train): not joined, it stands. */
        STALLED,
        /** Joined, and the network is overstressed: it stands with it. */
        OVERSTRESSED
    }

    /**
     * Something of an add-on's that turning passes through as if it were not there (the add-on Nook Technology's lines of
     * Shafts): whatever stands at one end meets what stands at the other exactly as if the two touched. Set by that
     * add-on ({@link #addRelay}); without one, nothing here is asked.
     */
    public interface Relay {
        /**
         * Entered at {@code at} from its neighbour on its {@code from} side: the block beyond its other end, the way out
         * toward it and what it makes of the turning; null where {@code at} passes nothing on from that side (no piece,
         * cut, too long) - and, {@code taking}, where what it is coupled to holds a Motor, which only gives.
         */
        @Nullable Far through(BlockGetter level, BlockPos at, Direction from, boolean taking);

        /** Whether the block at {@code pos} is one of its parts: no machine of its own, it weighs nothing (a line's end beside a wheel). */
        default boolean part(BlockGetter level, BlockPos pos) {
            return false;
        }

        /** The stress, su per RPM, a part of the add-on's at {@code at} driven from its {@code side} puts on the drive at {@code drive}; 0 for none. */
        default float load(Level level, BlockPos at, Direction side, BlockPos drive) {
            return 0.0F;
        }

        /** Further places the Motor {@code motor} turns another mod's shafts at, beyond what it meets itself; asked each of its ticks. */
        default List<Place> motorPlaces(ServerLevel level, MotorBlockEntity motor) {
            return List.of();
        }

        /** The Motor's axle's speed, RPM about the positive end of its axis: NaN for Nookworks' own (30 RPM, one way). */
        default float motorRpm(MotorBlockEntity motor) {
            return Float.NaN;
        }

        /** A wheel {@code motor} turns: its speed, RPM about the positive end of its axis; NaN for the Motor's own. */
        default float wheelRpm(MotorBlockEntity motor, DriveWheelBlockEntity wheel) {
            return Float.NaN;
        }

        /** Whether a Motor, running or not, is coupled to {@code wheels} by the add-on's parts (so they only give). */
        default boolean motorReaches(Level level, List<DriveWheelBlockEntity> wheels) {
            return false;
        }

        /**
         * Whether the Motor's way round is set and may not be changed to follow another mod's network that already turns
         * the other way (the add-on's Forward and Reverse); false where it has none of its own (Nookworks alone).
         */
        default boolean motorFixed(MotorBlockEntity motor) {
            return false;
        }
    }

    /**
     * A relay's far end: the block {@code at} beyond it, {@code out} the way from the relay toward it, {@code sign} how a
     * turn about the positive end of the axis it was entered on comes out about the positive end of {@code out}'s axis.
     */
    public record Far(BlockPos at, Direction out, int sign) {}

    /**
     * A further place a Motor's train ends in another mod's block: that block's place {@code at}, the way {@code out}
     * from our end toward it, the train's speed there ({@code rpm}, signed about the positive end of {@code out}'s
     * axis), and whether that way is {@code fixed} ({@link Relay#motorFixed}).
     */
    public record Place(BlockPos at, Direction out, float rpm, boolean fixed) {}

    private static final List<Relay> RELAYS = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Adds a relay (an add-on's, at its setup; a test's). */
    public static void addRelay(Relay relay) {
        RELAYS.add(relay);
    }

    /** Adds a relay asked before the others (a test's, beside an add-on's own). */
    public static void addRelayFirst(Relay relay) {
        RELAYS.add(0, relay);
    }

    /** Takes a relay off again (a test's). */
    public static void removeRelay(Relay relay) {
        RELAYS.remove(relay);
    }

    public static List<Relay> relays() {
        return RELAYS;
    }

    /** The first relay's far end through {@code at} entered from {@code from}, or null ({@link Relay#through}). */
    public static @Nullable Far through(BlockGetter level, BlockPos at, Direction from, boolean taking) {
        for (Relay relay : RELAYS) {
            Far far = relay.through(level, at, from, taking);
            if (far != null) {
                return far;
            }
        }
        return null;
    }

    /** Whether the block at {@code pos} is a relay's part ({@link Relay#part}): never counted as a machine a drive turns. */
    public static boolean relayPart(BlockGetter level, BlockPos pos) {
        for (Relay relay : RELAYS) {
            if (relay.part(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /** The stress the relays' parts at {@code at} put on the drive at {@code drive} ({@link Relay#load}). */
    public static float relayLoad(Level level, BlockPos at, Direction side, BlockPos drive) {
        float sum = 0.0F;
        for (Relay relay : RELAYS) {
            sum += relay.load(level, at, side, drive);
        }
        return sum;
    }

    /** The relays' further places for a Motor ({@link Relay#motorPlaces}). */
    public static List<Place> motorPlaces(ServerLevel level, MotorBlockEntity motor) {
        if (RELAYS.isEmpty()) {
            return List.of();
        }
        List<Place> places = new java.util.ArrayList<>();
        for (Relay relay : RELAYS) {
            places.addAll(relay.motorPlaces(level, motor));
        }
        return places;
    }

    /** A Motor's axle's speed, RPM about its axis's positive end: 30, or what a relay makes of it (its gear, its way). */
    public static float motorRpm(MotorBlockEntity motor) {
        for (Relay relay : RELAYS) {
            float rpm = relay.motorRpm(motor);
            if (!Float.isNaN(rpm)) {
                return rpm;
            }
        }
        return MOTOR_RPM;
    }

    /** A wheel a Motor turns: its speed, RPM about its axis's positive end (the Motor's own, but for a relay's word). */
    public static float wheelRpm(MotorBlockEntity motor, DriveWheelBlockEntity wheel) {
        for (Relay relay : RELAYS) {
            float rpm = relay.wheelRpm(motor, wheel);
            if (!Float.isNaN(rpm)) {
                return rpm;
            }
        }
        return motorRpm(motor);
    }

    /** Whether a relay couples a Motor to {@code wheels} ({@link Relay#motorReaches}). */
    public static boolean motorReaches(Level level, List<DriveWheelBlockEntity> wheels) {
        for (Relay relay : RELAYS) {
            if (relay.motorReaches(level, wheels)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a relay says the Motor's way round is fixed ({@link Relay#motorFixed}). */
    public static boolean motorFixed(MotorBlockEntity motor) {
        for (Relay relay : RELAYS) {
            if (relay.motorFixed(motor)) {
                return true;
            }
        }
        return false;
    }

    /** A bare Motor's speed: a quarter turn of a handle every half second (MotorBlockEntity.TICKS_PER_TURN). */
    public static final float MOTOR_RPM = 30.0F;

    /** For a taker of an add-on's: the drive of another mod's at its {@code side} ({@link Hook#taken}); null without one. */
    public static @Nullable Taken taken(Level level, BlockPos at, Direction side) {
        Hook h = hook;
        return h == null ? null : h.taken(level, at, side);
    }

    /** A train of two ends with another mod's blocks at both ({@link Hook#bridge}); {@link Bridge#NONE} without such a mod. */
    public static Bridge bridge(ServerLevel level, BlockPos a, Direction outA, BlockPos b, Direction outB, int sign) {
        Hook h = hook;
        return h == null ? Bridge.NONE : h.bridge(level, a, outA, b, outB, sign);
    }

    /** A train with no Motor and another mod's blocks at its ends ({@link Hook#join}); {@link Bridge#NONE} without such a mod. */
    public static Bridge join(ServerLevel level, List<Meet> ends) {
        Hook h = hook;
        return h == null ? Bridge.NONE : h.join(level, ends);
    }

    /** Whether another mod's drives are there at all (Create, in the port). */
    public static boolean present() {
        return hook != null;
    }

    /**
     * Another mod's block a Motor's end drives (the port's Create: whatever of its has a shaft end toward ours). The
     * Motor pays for it in FE by what it gives, in machines' worth - one machine's worth is what the Motor turns a
     * machine of ours for - and tells it each tick whether to turn.
     */
    public interface Output {
        /** What is drawn of it now, in machines' worth (0 while it does not turn). */
        double drawn();

        /** Whether its network is overstressed: nothing turns. */
        boolean overstressed();

        /** Whether another Motor than the one at {@code motor} turned it this tick or the last, and so keeps it. */
        boolean heldByAnother(BlockPos motor, long now);

        /**
         * Whether it gives nothing though told to turn: its network already turns the other way and the Motor's way is
         * fixed ({@link Relay#motorFixed}), or the block is that mod's own generator standing still.
         */
        default boolean standsBy() {
            return false;
        }

        /** Whether it stands by because its network already turns the other way and the Motor's way is fixed. */
        default boolean against() {
            return false;
        }

        /**
         * This tick's word from the Motor at {@code motor}: turn or not, with {@code share} of the Motor's whole budget, the
         * Motor drawing {@code fe} FE a tick in all (for what its goggles show).
         */
        void supply(BlockPos motor, long now, boolean run, double share, double fe);
    }

    private static volatile @Nullable Hook hook;

    /** Set once, at setup, by the mod whose drives these are. */
    public static void set(Hook drives) {
        hook = drives;
    }

    public static boolean drives(LevelReader level, BlockPos at, BlockState state, Direction side) {
        Hook h = hook;
        return h != null && h.drives(level, at, state, side);
    }

    /** From a machine's server tick: {@code state} is the machine's, whose drive side its look keeps. */
    public static void tickMachine(ServerLevel level, BlockEntity machine, BlockState state) {
        Hook h = hook;
        if (h != null) {
            Direction side = state.getOptionalValue(Drive.PROPERTY).map(Drive::side).orElse(null);
            if (side != null) {
                h.tickMachine(level, machine, side);
            }
        }
    }

    public static void tickWheel(Level level, DriveWheelBlockEntity wheel) {
        Hook h = hook;
        if (h != null) {
            h.tickWheel(level, wheel);
        }
    }

    public static @Nullable DriveSource source(BlockGetter level, BlockPos at, Direction side) {
        Hook h = hook;
        return h == null ? null : h.source(level, at, side);
    }

    /** From a Motor's server tick: what it turns of another mod's ({@link Hook#motorOutputs}); nothing without such a mod. */
    public static List<Output> motorOutputs(ServerLevel level, BlockPos motor, Direction facing, List<DriveWheelBlockEntity> wheels) {
        Hook h = hook;
        return h == null ? List.of() : h.motorOutputs(level, motor, facing, wheels);
    }
}
