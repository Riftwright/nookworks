package dev.nookworks.compat.create;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.RotationPropagator;
import com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity;
import com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.gearbox.GearboxBlockEntity;
import com.simibubi.create.content.kinetics.transmission.SplitShaftBlockEntity;
import com.simibubi.create.foundation.utility.CreateLang;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.ForeignDrives;
import dev.nookworks.machine.MotorBlockEntity;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Our turning parts in Create's own rotation network (the 1.21.1 port's Create part; the design:
 * tools/port1211/design_create.md, "Ends and trains"). No block of Create's is replaced: what stands at an end of ours
 * is driven where it stands, or joined to what stands at a train's other ends, through hooks into Create's own block
 * entity and propagation (mixin.compat.create.KineticEndMixin, PropagatorEndMixin, ShaftHalvesFacingMixin), all
 * answered here.
 * <ul>
 * <li><b>Giving</b>: a Motor's end makes the Create block entity beyond it a source of its network - its
 * {@code getGeneratedSpeed()} and {@code calculateAddedStressCapacity()} answer what the Motor gives ({@link State}) -
 * started and stopped by the public calls Create's own generators use, its {@code source} naming our block's place (so a
 * Gearbox or Clutch knows which face the turning enters at). The Motor tells it each tick ({@link Request}); with no
 * word for {@link #GRACE} ticks it stops.</li>
 * <li><b>Passing on</b>: the Create block entities at the ends of a train with no Motor are neighbours to Create's
 * propagation ({@link #neighbours}, {@link #modifier}), the train's sign between them ({@link #join}); never where two
 * already turn against each other.</li>
 * </ul>
 * Everything here runs on the server thread but the hooks' reads, which a client's drawing asks too.
 */
public final class CreateEnds {
    private CreateEnds() {}

    /** Ticks a driven block runs on with no word from its Motor: the two tick in no set order. */
    private static final int GRACE = 2;
    /**
     * Create breaks a block whose speed came and went too often (its flicker tally past 128, five a change, one off a
     * tick): nothing of ours starts or joins a block whose tally is over this - it waits the tally out instead.
     */
    private static final int FLICKER_LIMIT = 60;
    /** Two speeds through a train are one within this (Create's own propagation allows a like slack). */
    private static final float SLACK = 1.0E-3F;

    /** What the mixin keeps on every Create kinetic block entity: its state with us (null until a train meets it), and two of its figures. */
    public interface Holder {
        @Nullable State nookworks$ends();

        void nookworks$ends(@Nullable State state);

        /** Its network's stress and capacity as it last heard them (Create's own fields). */
        float nookworks$stress();

        float nookworks$capacity();
    }

    /** What our trains make of one Create block entity. */
    public static final class State {
        // ---- a Motor's end drives it ----
        /** The speed given: RPM about the positive end of the axis of the face the turning enters at. */
        float given;
        /** The capacity given, su per RPM of {@link #given}. */
        float capacity;
        /** Whether a Motor's end gives it turning now. */
        boolean generating;
        /** Our block the turning comes in from. */
        @Nullable BlockPos from;
        /** The Motor that drives it, and when it last said so. */
        @Nullable BlockPos motor;
        long ranAt = Long.MIN_VALUE;
        /** Told to turn and giving nothing: its network turns the other way and the Motor's way is fixed, or it is a generator standing still. */
        boolean standsBy;
        /** Whether the Motor's way may not be changed to follow a network turning the other way. */
        boolean fixed;
        /** Standing by for that: the network turns against the Motor's fixed way. */
        boolean against;
        /** What its Motor draws in all, FE a tick, and what is drawn of this block, su: for the goggles (sent to clients). */
        float motorFe;
        float drawnSu;
        private float sentFe = -1.0F;
        private float sentSu = -1.0F;

        // ---- a train passes turning on through it ----
        /** The Create blocks at the train's other ends it is joined to (server). */
        final List<Link> links = new ArrayList<>(0);
        /** A client: the face its turning enters at through a train, and the place it comes from, as the server sent them. */
        @Nullable Direction entry;
        @Nullable BlockPos entryFrom;

        @Nullable Link link(BlockPos other) {
            for (Link link : this.links) {
                if (link.other.equals(other)) {
                    return link;
                }
            }
            return null;
        }

        public boolean generating() {
            return this.generating;
        }

        public float given() {
            return this.given;
        }

        public float capacityPerRpm() {
            return this.capacity;
        }

        public @Nullable BlockPos from() {
            return this.from;
        }

        public float motorFe() {
            return this.motorFe;
        }

        public float drawnSu() {
            return this.drawnSu;
        }

        public boolean standsBy() {
            return this.standsBy;
        }

        /** The places it is joined to through trains (server). */
        public List<BlockPos> joined() {
            List<BlockPos> out = new ArrayList<>(this.links.size());
            for (Link link : this.links) {
                out.add(link.other);
            }
            return out;
        }
    }

    /** One side of a join: the other block's place, this block's face toward the train and the other's, the train's sign between them. */
    static final class Link {
        final BlockPos other;
        final Direction face;
        final Direction otherFace;
        final int sign;
        long seen;

        Link(BlockPos other, Direction face, Direction otherFace, int sign, long seen) {
            this.other = other;
            this.face = face;
            this.otherFace = otherFace;
            this.sign = sign;
            this.seen = seen;
        }
    }

    /** The Create block entities a train of ours has a say in (server), looked over each tick ({@link #sweep}). */
    private static final Set<KineticBlockEntity> TRACKED = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Whether the hooks took (Create asked one): without them nothing here can work, and a test says so. */
    static volatile boolean hooked;
    /** Whether a join of ours is being made now: Create's own call for a block set down is ours here. */
    private static boolean joining;

    public static @Nullable State state(@Nullable KineticBlockEntity kinetic) {
        return kinetic instanceof Holder holder ? holder.nookworks$ends() : null;
    }

    private static State stateOrNew(KineticBlockEntity kinetic) {
        Holder holder = (Holder) kinetic;
        State state = holder.nookworks$ends();
        if (state == null) {
            state = new State();
            holder.nookworks$ends(state);
        }
        return state;
    }

    /** Whether Create's block entities carry our state at all (the mixin went in). */
    public static boolean ready(KineticBlockEntity kinetic) {
        return kinetic instanceof Holder;
    }

    public static boolean hooked() {
        return hooked;
    }

    // ---- what the hooks answer ----

    /** {@code getGeneratedSpeed()}: Create's own where it has one, else what a Motor's end gives the block. */
    public static float generated(KineticBlockEntity kinetic, float own) {
        hooked = true;
        if (own != 0.0F) {
            return own;
        }
        State state = state(kinetic);
        return state != null && state.generating ? state.given : 0.0F;
    }

    /** {@code calculateAddedStressCapacity()}: Create's own figure with the Motor's share added, su per RPM of the block's generated speed. */
    public static float capacity(KineticBlockEntity kinetic, float own) {
        State state = state(kinetic);
        if (state == null || !state.generating) {
            return own;
        }
        if (kinetic instanceof GeneratingKineticBlockEntity) {
            // Create's own generator: its speed is its own, ours is counted in its terms
            float theirs = Math.abs(kinetic.getGeneratedSpeed());
            return theirs == 0.0F ? own : own + state.capacity * Math.abs(state.given) / theirs;
        }
        return own + state.capacity;
    }

    /** {@code validateKinetics()}: whether the block's turning comes in from a block of ours (its source names no kinetic block). */
    public static boolean turnedFromOurs(KineticBlockEntity kinetic) {
        State state = state(kinetic);
        return state != null && state.generating && state.from != null && state.from.equals(kinetic.source);
    }

    /** {@code RotationPropagator.getPotentialNeighbourLocations}: with the Create blocks at the other ends of our trains. */
    public static List<BlockPos> neighbours(KineticBlockEntity kinetic, List<BlockPos> places) {
        State state = state(kinetic);
        if (state == null || state.links.isEmpty()) {
            return places;
        }
        Level level = kinetic.getLevel();
        List<BlockPos> with = new ArrayList<>(places); // Create's own list as it was, ours after it
        for (Link link : state.links) {
            if (level != null && level.isLoaded(link.other) && !with.contains(link.other)) {
                with.add(link.other);
            }
        }
        return with;
    }

    /**
     * {@code RotationPropagator.getRotationSpeedModifier}: for two blocks joined through a train, what a turn of the one
     * makes of the other - the train's sign, with each block's own face modifier as Create applies it between neighbours.
     * Create's own answer stands for two it really connects (neighbours, meshing cogs: within a block of each other);
     * beyond that its answer for two blocks in line is blind to what lies between, and ours stands.
     */
    public static float modifier(KineticBlockEntity from, KineticBlockEntity to, float own) {
        State state = state(from);
        if (state == null || state.links.isEmpty()) {
            return own;
        }
        Link link = state.link(to.getBlockPos());
        if (link == null || own != 0.0F && near(from.getBlockPos(), to.getBlockPos())) {
            return own;
        }
        float target = faceModifier(to, link.otherFace);
        return target == 0.0F ? 0.0F : faceModifier(from, link.face) * link.sign / target;
    }

    /** {@code DirectionalShaftHalvesBlockEntity.getSourceFacing()}: for a source many blocks away through a train, the face the turning enters at. */
    public static Direction sourceFacing(KineticBlockEntity kinetic, Direction nearest) {
        State state = state(kinetic);
        if (state == null || kinetic.source == null) {
            return nearest;
        }
        Link link = state.link(kinetic.source);
        if (link != null) {
            return link.face;
        }
        return state.entry != null && kinetic.source.equals(state.entryFrom) ? state.entry : nearest;
    }

    /** Whether two places are within a block of each other every way: where Create's own connections reach. */
    private static boolean near(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) <= 1 && Math.abs(a.getY() - b.getY()) <= 1 && Math.abs(a.getZ() - b.getZ()) <= 1;
    }

    /**
     * How the shaft end of {@code kinetic} on its {@code face} turns against the block's own speed, as Create's
     * propagation counts it between neighbours: a Gearbox's ends by where its source is (the end facing the source and
     * the block's speed are one; the opposite end turns back; of the two across, the one on the source's side of its
     * axis turns back), a Clutch's or Gearshift's by its own rule, 1 for everything else.
     */
    static float faceModifier(KineticBlockEntity kinetic, Direction face) {
        if (!(kinetic instanceof DirectionalShaftHalvesBlockEntity halves) || kinetic.source == null) {
            return 1.0F;
        }
        if (kinetic instanceof GearboxBlockEntity) {
            Direction source = halves.getSourceFacing();
            if (face.getAxis() == source.getAxis()) {
                return face == source ? 1.0F : -1.0F;
            }
            return face.getAxisDirection() == source.getAxisDirection() ? -1.0F : 1.0F;
        }
        return kinetic instanceof SplitShaftBlockEntity split ? split.getRotationSpeedModifier(face) : 1.0F;
    }

    /** The speed at {@code kinetic}'s shaft end on its {@code face} it is meant to turn at, overstressed or not: RPM about the positive end of the face's axis. */
    static float theoreticalFaceSpeed(KineticBlockEntity kinetic, Direction face) {
        float speed = kinetic.getTheoreticalSpeed();
        return speed == 0.0F ? 0.0F : speed * faceModifier(kinetic, face);
    }

    // ---- giving: a Motor's ends ----

    /** One machine's worth of turning at {@code rpm}, in su: the Motor's budget over its eight machines. */
    static float machineSu(float rpm) {
        return (float) (CreateConfig.get(CreateConfig.MOTOR_CAPACITY) * Math.abs(rpm) / DriveNetwork.MOST_MACHINES);
    }

    /**
     * The Create blocks a Motor at {@code motor} drives ({@link ForeignDrives.Hook#motorOutputs}): the one its axle points
     * into when {@code wheels} is empty, else the one beyond each axle end of its line's wheels, and an add-on's further
     * places. Any block of Create's with a shaft end toward ours; across a claim's edge only where whoever set the Motor
     * down could break that block.
     */
    static List<ForeignDrives.Output> outputs(ServerLevel level, BlockPos motor, Direction facing, List<DriveWheelBlockEntity> wheels) {
        List<ForeignDrives.Output> found = new ArrayList<>(1);
        if (!(level.getBlockEntity(motor) instanceof MotorBlockEntity entity)) {
            return found;
        }
        boolean fixed = ForeignDrives.motorFixed(entity);
        if (wheels.isEmpty()) {
            request(level, entity, motor.relative(facing), facing, ForeignDrives.motorRpm(entity), fixed, found);
        }
        for (DriveWheelBlockEntity wheel : wheels) {
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                Direction out = Direction.fromAxisAndDirection(wheel.axis(), way);
                BlockPos end = wheel.getBlockPos().relative(out);
                if (!end.equals(motor)) {
                    request(level, entity, end, out, ForeignDrives.wheelRpm(entity, wheel), fixed, found);
                }
            }
        }
        for (ForeignDrives.Place place : ForeignDrives.motorPlaces(level, entity)) {
            request(level, entity, place.at(), place.out(), place.rpm(), place.fixed(), found);
        }
        return found;
    }

    private static void request(ServerLevel level, MotorBlockEntity motor, BlockPos at, Direction out, float rpm, boolean fixed,
            List<ForeignDrives.Output> found) {
        if (rpm == 0.0F || !level.isLoaded(at)) {
            return;
        }
        KineticBlockEntity kinetic = CreateDrives.kineticAt(level, at, out);
        if (kinetic == null || !(kinetic instanceof Holder) || !DrawRights.mayMove(level, motor.getBlockPos(), motor, at, kinetic)) {
            return;
        }
        for (ForeignDrives.Output had : found) {
            if (had instanceof Request request && request.kinetic == kinetic) {
                return; // one block met at two of its ends: driven once
            }
        }
        found.add(new Request(kinetic, stateOrNew(kinetic), at.relative(out.getOpposite()), out.getOpposite(), rpm, fixed));
    }

    /** One Motor's word for one Create block this tick: the block of ours the turning comes from, the block's face toward it, the speed there. */
    static final class Request implements ForeignDrives.Output {
        final KineticBlockEntity kinetic;
        private final State state;
        private final BlockPos from;
        private final Direction face;
        private final float rpm;
        private final boolean fixed;

        Request(KineticBlockEntity kinetic, State state, BlockPos from, Direction face, float rpm, boolean fixed) {
            this.kinetic = kinetic;
            this.state = state;
            this.from = from.immutable();
            this.face = face;
            this.rpm = rpm;
            this.fixed = fixed;
        }

        private boolean mine() {
            return this.state.generating && this.from.equals(this.state.from);
        }

        @Override
        public double drawn() {
            if (!this.mine()) {
                return 0.0;
            }
            float per = machineSu(this.state.given);
            return per <= 0.0F ? 0.0 : CreateEnds.drawnSu(this.kinetic, this.state) / per;
        }

        @Override
        public boolean overstressed() {
            return this.mine() && this.kinetic.isOverStressed();
        }

        @Override
        public boolean heldByAnother(BlockPos motor, long now) {
            return this.state.generating && this.state.motor != null && !this.state.motor.equals(motor) && this.state.ranAt >= now - 1;
        }

        @Override
        public boolean standsBy() {
            return this.state.standsBy && this.from.equals(this.state.from);
        }

        @Override
        public boolean against() {
            return this.standsBy() && this.state.against;
        }

        @Override
        public void supply(BlockPos motor, long now, boolean run, double share, double fe) {
            State state = this.state;
            if (this.heldByAnother(motor, now) || this.kinetic.isRemoved()) {
                return; // another Motor runs it: that one keeps it
            }
            TRACKED.add(this.kinetic);
            state.motor = motor.immutable();
            if (!run) {
                state.standsBy = false;
                if (state.generating) {
                    stop(this.kinetic, state);
                }
                return;
            }
            state.ranAt = now;
            state.fixed = this.fixed;
            drive(this.kinetic, state, this.from, this.face, this.rpm, this.fixed, (float) (share * CreateConfig.get(CreateConfig.MOTOR_CAPACITY)));
            state.motorFe = state.generating ? (float) fe : 0.0F;
            state.drawnSu = CreateEnds.drawnSu(this.kinetic, state);
            if (Math.abs(state.motorFe - state.sentFe) >= 0.05F || Math.abs(state.drawnSu - state.sentSu) >= 0.5F) {
                state.sentFe = state.motorFe;
                state.sentSu = state.drawnSu;
                this.kinetic.sendData(); // the goggles on a client read them
            }
        }
    }

    /** What Create draws of a driven block: its network's stress by the block's part of the capacity, at most what it gives. */
    static float drawnSu(KineticBlockEntity kinetic, State state) {
        if (!state.generating || kinetic.isOverStressed() || !(kinetic instanceof Holder holder)) {
            return 0.0F;
        }
        float capacity = holder.nookworks$capacity();
        if (capacity <= 0.0F) {
            return 0.0F;
        }
        float own = state.capacity * Math.abs(state.given);
        return Math.min(own, holder.nookworks$stress() * own / capacity);
    }

    /** What a driven block gives its network now, su. */
    public static float givenSu(State state) {
        return state.generating ? state.capacity * Math.abs(state.given) : 0.0F;
    }

    /**
     * This tick's turning for {@code kinetic} from the block of ours at {@code from} on its {@code face}: {@code rpm} there
     * with {@code capacity} su per RPM. Create's rule for a second source where its network already turns: the faster sets
     * the speed, ours adds its capacity; the way round followed unless {@code fixed}.
     */
    private static void drive(KineticBlockEntity kinetic, State state, BlockPos from, Direction face, float rpm, boolean fixed, float capacity) {
        state.standsBy = false;
        state.against = false;
        if (kinetic.needsSpeedUpdate()) {
            // set down this tick, not yet joined by Create to what stands round it: its speed says nothing yet. Started now
            // it could be turned against a network it is about to join - and Create would break it. Its own tick first
            return;
        }
        if (kinetic instanceof GeneratingKineticBlockEntity) {
            // Create's own generator: its speed is its own. Running, it is added to; standing, nothing can be given through it
            float atFace = theoreticalFaceSpeed(kinetic, face);
            if (kinetic.getGeneratedSpeed() == 0.0F || atFace == 0.0F || Math.signum(atFace) != Math.signum(rpm) && fixed) {
                standBy(kinetic, state, from);
                state.against = kinetic.getGeneratedSpeed() != 0.0F && atFace != 0.0F;
                return;
            }
            give(kinetic, state, from, Math.signum(atFace) * Math.abs(rpm), capacity);
            return;
        }
        float theirs = kinetic.getTheoreticalSpeed();
        boolean mine = state.generating && from.equals(state.from) && from.equals(kinetic.source);
        if (theirs != 0.0F && !mine) {
            // a network turns it already
            float atFace = theoreticalFaceSpeed(kinetic, face);
            if (atFace == 0.0F || Math.signum(atFace) != Math.signum(rpm) && fixed) {
                standBy(kinetic, state, from); // its end toward us is cut (a Clutch), or it turns against a way we may not change
                state.against = atFace != 0.0F;
                return;
            }
            float follows = Math.signum(atFace) * Math.abs(rpm);
            if (Math.abs(atFace) >= Math.abs(rpm) || kinetic.getFlickerScore() > FLICKER_LIMIT) {
                give(kinetic, state, from, follows, capacity); // a source beside the faster one
                return;
            }
            // ours is the faster: started from our side, Create's propagation makes the others follow
            float before = kinetic.getSpeed();
            state.generating = true;
            state.given = follows;
            state.capacity = capacity;
            state.from = from;
            kinetic.detachKinetics();
            kinetic.setSpeed(follows);
            kinetic.source = from;
            kinetic.setNetwork(kinetic.getBlockPos().asLong());
            kinetic.attachKinetics();
            kinetic.onSpeedChanged(before);
            kinetic.sendData();
            return;
        }
        if (theirs == 0.0F) {
            if (kinetic.getFlickerScore() > FLICKER_LIMIT) {
                return; // it came and went too often of late: waited out, lest Create break it
            }
            state.generating = true;
            state.given = rpm;
            state.capacity = capacity;
            state.from = from;
            kinetic.setSpeed(rpm);
            kinetic.source = from;
            kinetic.setNetwork(kinetic.getBlockPos().asLong());
            kinetic.attachKinetics();
            kinetic.onSpeedChanged(0.0F);
            kinetic.sendData();
            return;
        }
        // turned from our side already: the way it took to follow a network is kept while it turns (a source of that
        // network may still be turning beside it), its own again once it starts from standing
        float wanted = !fixed && Math.signum(state.given) != Math.signum(rpm) ? -rpm : rpm;
        if (theirs != wanted) {
            if (kinetic.getFlickerScore() > FLICKER_LIMIT) {
                return;
            }
            float before = kinetic.getSpeed();
            state.given = wanted;
            state.capacity = capacity;
            kinetic.detachKinetics();
            kinetic.setSpeed(wanted);
            kinetic.attachKinetics();
            if (kinetic.hasNetwork()) {
                kinetic.getOrCreateNetwork().updateCapacityFor(kinetic, kinetic.calculateAddedStressCapacity());
            }
            kinetic.onSpeedChanged(before);
            kinetic.sendData();
        } else if (capacity != state.capacity) {
            state.capacity = capacity;
            if (kinetic.hasNetwork()) {
                kinetic.getOrCreateNetwork().updateCapacityFor(kinetic, kinetic.calculateAddedStressCapacity());
            }
            kinetic.setChanged();
        }
    }

    /** A source beside what already turns the block: only its capacity is ours. */
    private static void give(KineticBlockEntity kinetic, State state, BlockPos from, float rpm, float capacity) {
        boolean changed = !state.generating || state.capacity != capacity || state.given != rpm || !from.equals(state.from);
        state.generating = true;
        state.given = rpm;
        state.capacity = capacity;
        state.from = from;
        if (changed && kinetic.hasNetwork()) {
            kinetic.getOrCreateNetwork().updateCapacityFor(kinetic, kinetic.calculateAddedStressCapacity());
            kinetic.setChanged();
            kinetic.sendData();
        }
    }

    private static void standBy(KineticBlockEntity kinetic, State state, BlockPos from) {
        if (state.generating) {
            stop(kinetic, state);
        }
        state.from = from;
        state.standsBy = true;
    }

    /** The Motor gives no more: the block is let go as Create lets a generator go that stopped. */
    private static void stop(KineticBlockEntity kinetic, State state) {
        if (!state.generating) {
            return;
        }
        boolean mine = state.from != null && state.from.equals(kinetic.source);
        if (kinetic instanceof GeneratingKineticBlockEntity || !mine) {
            // a source beside another: only our capacity goes
            state.generating = false;
            if (kinetic.hasNetwork()) {
                kinetic.getOrCreateNetwork().updateCapacityFor(kinetic, kinetic.calculateAddedStressCapacity());
            }
            kinetic.setChanged();
            kinetic.sendData();
            return;
        }
        float before = kinetic.getSpeed();
        if (kinetic.getTheoreticalSpeed() != 0.0F) {
            kinetic.detachKinetics(); // what it turned loses its source; another source among them takes over, Create's rule
        }
        kinetic.setSpeed(0.0F);
        kinetic.source = null;
        kinetic.setNetwork(null);
        state.generating = false;
        kinetic.onSpeedChanged(before);
        kinetic.sendData();
    }

    /** The Create blocks the Motor at {@code motor} drives, as they stand (on a client too: for its goggles). */
    public static List<KineticBlockEntity> drivenBy(BlockGetter getter, BlockPos motor) {
        List<KineticBlockEntity> found = new ArrayList<>(1);
        BlockState state = getter.getBlockState(motor);
        if (!(state.getBlock() instanceof MotorBlock) || !(getter instanceof Level level)) {
            return found;
        }
        Direction facing = state.getValue(MotorBlock.FACING);
        BlockPos front = motor.relative(facing);
        if (level.getBlockEntity(front) instanceof DriveWheelBlockEntity wheel && wheel.axis() == facing.getAxis()) {
            for (DriveWheelBlockEntity each : DriveNetwork.wheels(level, wheel)) {
                for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                    Direction out = Direction.fromAxisAndDirection(each.axis(), way);
                    BlockPos end = each.getBlockPos().relative(out);
                    if (!end.equals(motor)) {
                        driven(level, end, out, each.getBlockPos(), found);
                    }
                }
            }
        } else {
            driven(level, front, facing, motor, found);
        }
        return found;
    }

    private static void driven(Level level, BlockPos at, Direction out, BlockPos from, List<KineticBlockEntity> found) {
        KineticBlockEntity kinetic = level.isLoaded(at) ? CreateDrives.kineticAt(level, at, out) : null;
        State state = state(kinetic);
        if (state != null && (state.generating || state.standsBy) && from.equals(state.from) && !found.contains(kinetic)) {
            found.add(kinetic);
        }
    }

    // ---- passing on: a train with no Motor ----

    /** An end of a train as Create has it: the block entity, its face toward the train, the train's sign there. */
    private record End(KineticBlockEntity kinetic, Direction face, int sign) {
        BlockPos pos() {
            return this.kinetic.getBlockPos();
        }

        float number() {
            return theoreticalFaceSpeed(this.kinetic, this.face) * this.sign;
        }
    }

    private static List<End> ends(Level level, List<ForeignDrives.Meet> meets) {
        List<End> ends = new ArrayList<>(meets.size());
        for (ForeignDrives.Meet meet : meets) {
            if (!level.isLoaded(meet.at())) {
                continue;
            }
            KineticBlockEntity kinetic = CreateDrives.kineticAt(level, meet.at(), meet.out());
            if (kinetic == null || !(kinetic instanceof Holder)) {
                continue;
            }
            boolean had = false;
            for (End end : ends) {
                had |= end.kinetic == kinetic;
            }
            if (!had) {
                ends.add(new End(kinetic, meet.out().getOpposite(), meet.sign() < 0 ? -1 : 1));
            }
        }
        return ends;
    }

    /**
     * Whether the Create blocks at a train's ends {@code meets} turn it one way: false where two of them turn it at
     * another speed or another way through the train (it stands). On a client too, for the train's look.
     */
    public static boolean agree(Level level, List<ForeignDrives.Meet> meets) {
        return agree(ends(level, meets));
    }

    private static boolean agree(List<End> ends) {
        float number = 0.0F;
        for (End end : ends) {
            float here = end.number();
            if (here != 0.0F) {
                if (number != 0.0F && Math.abs(here - number) > SLACK) {
                    return false;
                }
                number = here;
            }
        }
        return true;
    }

    /**
     * A train with no Motor, each server tick ({@link ForeignDrives.Hook#join}): the Create blocks at its ends are joined
     * in a chain, so Create's own propagation carries speed, stress and capacity across. Never where two of them already
     * turn against each other: then nothing is joined (what was is cut at the next sweep), and the train stands.
     */
    static ForeignDrives.Bridge join(ServerLevel level, List<ForeignDrives.Meet> meets) {
        List<End> ends = ends(level, meets);
        if (ends.size() < 2) {
            return ForeignDrives.Bridge.NONE;
        }
        for (End end : ends) {
            if (end.kinetic.needsSpeedUpdate()) {
                // set down this tick, not yet joined by Create to what stands round it: whether it turns is not known
                // yet. Joined now as a still block, it could be turned against its own network - and Create would break
                // it. Its own tick first; the train asks again next tick
                return ForeignDrives.Bridge.NONE;
            }
        }
        if (!agree(ends)) {
            return ForeignDrives.Bridge.STALLED;
        }
        ends.sort(Comparator.comparingLong((End end) -> end.pos().asLong()));
        long now = level.getGameTime();
        for (int i = 0; i + 1 < ends.size(); i++) {
            link(level, ends.get(i), ends.get(i + 1), now);
        }
        boolean turning = false;
        boolean overstressed = false;
        for (End end : ends) {
            if (end.number() != 0.0F) {
                turning = true;
                overstressed |= end.kinetic.isOverStressed();
            }
        }
        return !turning ? ForeignDrives.Bridge.NONE : overstressed ? ForeignDrives.Bridge.OVERSTRESSED : ForeignDrives.Bridge.TOGETHER;
    }

    private static void link(ServerLevel level, End a, End b, long now) {
        int sign = a.sign * b.sign;
        State sa = stateOrNew(a.kinetic);
        State sb = stateOrNew(b.kinetic);
        Link la = sa.link(b.pos());
        Link lb = sb.link(a.pos());
        if (la != null && lb != null && la.sign == sign && la.face == a.face && lb.face == b.face) {
            la.seen = now;
            lb.seen = now;
            return;
        }
        if (la != null || lb != null) {
            cut(a.kinetic, b.kinetic); // another sign or face now (a Reversing Gear thrown): undone, then made anew
        }
        if (a.kinetic.getFlickerScore() > FLICKER_LIMIT || b.kinetic.getFlickerScore() > FLICKER_LIMIT
                || !DrawRights.mayMove(level, a.pos(), a.kinetic, b.pos(), b.kinetic) || !DrawRights.mayMove(level, b.pos(), b.kinetic, a.pos(), a.kinetic)) {
            return;
        }
        sa.links.add(new Link(b.pos().immutable(), a.face, b.face, sign, now));
        sb.links.add(new Link(a.pos().immutable(), b.face, a.face, sign, now));
        TRACKED.add(a.kinetic);
        TRACKED.add(b.kinetic);
        float va = theoreticalFaceSpeed(a.kinetic, a.face);
        float vb = theoreticalFaceSpeed(b.kinetic, b.face);
        // attached as Create attaches a block set down beside one that turns: the still side takes the turning; two that
        // turn alike become one network
        KineticBlockEntity attach = va == 0.0F && vb == 0.0F ? null : vb == 0.0F ? b.kinetic : va == 0.0F ? a.kinetic
                : a.kinetic.hasNetwork() && a.kinetic.network.equals(b.kinetic.network) ? null : b.kinetic;
        if (attach != null) {
            boolean was = joining;
            joining = true;
            try {
                RotationPropagator.handleAdded(level, attach.getBlockPos(), attach);
            } finally {
                joining = was;
            }
        }
    }

    /**
     * The join between {@code a} and {@code b} undone, as Create undoes a connection (its Gearshift thrown): whichever of
     * the two turned from the other is taken off its network, lets go of what it turned, and is attached again at its next
     * tick - turning on with whatever else turns it.
     */
    private static void cut(KineticBlockEntity a, KineticBlockEntity b) {
        State sa = state(a);
        State sb = state(b);
        if (sa != null) {
            sa.links.removeIf(link -> link.other.equals(b.getBlockPos()));
        }
        if (sb != null) {
            sb.links.removeIf(link -> link.other.equals(a.getBlockPos()));
        }
        if (a.isRemoved() || b.isRemoved() || a.getLevel() == null) {
            return; // Create let its dependents go when it went
        }
        KineticBlockEntity dependent = b.hasSource() && b.source.equals(a.getBlockPos()) ? b
                : a.hasSource() && a.source.equals(b.getBlockPos()) ? a : null;
        if (dependent == null) {
            return;
        }
        KineticBlockEntity other = dependent == a ? b : a;
        if (near(a.getBlockPos(), b.getBlockPos()) && (RotationPropagator.isConnected(other, dependent) || RotationPropagator.isConnected(dependent, other))) {
            return; // Create's own connection between them stands
        }
        if (dependent.hasNetwork()) {
            dependent.getOrCreateNetwork().remove(dependent);
        }
        dependent.detachKinetics();
        dependent.removeSource();
        dependent.updateSpeed = true;
        dependent.sendData();
    }

    // ---- before Create joins a block set down ----

    /**
     * Create is about to join {@code added} to the blocks it connects to (RotationPropagator.handleAdded: a block set
     * down, a clutch let go, a source started), and breaks it where they turn it two ways. Never over a turning only we
     * gave:
     * <ul>
     * <li>where it brings together two networks that turn, the joins our trains made in them are cut first (what a train
     * alone turned stands, and the train joins again only if its ends then agree), and one turned by our Motors' ends
     * alone is stopped: Create attaches the block to a network standing still, and at their next tick our ends join the
     * way it turns;</li>
     * <li>where a still block is set between two blocks of one network that turn - it closes a loop - the joins our
     * trains made in that network are cut first: what they alone turned stands, Create attaches the block, and each
     * train joins again at its next tick if its ends still agree (else it stands, saying so).</li>
     * </ul>
     */
    public static void beforeJoin(Level level, BlockPos pos, KineticBlockEntity added) {
        if (level.isClientSide() || joining || TRACKED.isEmpty() || !(added instanceof Holder)) {
            return;
        }
        State own = state(added);
        if (own != null && own.generating && own.from != null && own.from.equals(added.source)) {
            return; // an end of ours being started: it has looked already
        }
        List<KineticNetwork> turning = new ArrayList<>(2);
        if (added.getTheoreticalSpeed() != 0.0F && added.hasNetwork()) {
            turning.add(added.getOrCreateNetwork());
        }
        boolean lone = added.getTheoreticalSpeed() != 0.0F && !added.hasNetwork();
        int neighbours = 0;
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1))) {
            if (near.equals(pos) || !level.isLoaded(near) || !(level.getBlockEntity(near) instanceof KineticBlockEntity neighbour)
                    || neighbour.getTheoreticalSpeed() == 0.0F || !neighbour.hasNetwork()
                    || !(RotationPropagator.isConnected(added, neighbour) || RotationPropagator.isConnected(neighbour, added))) {
                continue;
            }
            neighbours++;
            KineticNetwork network = neighbour.getOrCreateNetwork();
            if (network != null && !turning.contains(network)) {
                turning.add(network);
            }
        }
        if (turning.size() == 1 && neighbours >= 2 && added.getTheoreticalSpeed() == 0.0F) {
            // a loop closed: our trains' joins in it are cut, so Create rules on its own shafts alone
            cutJoins(level, turning.get(0));
            return;
        }
        if (turning.size() + (lone ? 1 : 0) < 2) {
            return;
        }
        // two networks that turn are brought together: neither is turning only through a train of ours when Create rules
        for (KineticNetwork network : turning) {
            cutJoins(level, network);
        }
        // one network keeps turning: Create's own if there is one, else the first of ours
        boolean kept = lone;
        for (KineticNetwork network : turning) {
            kept |= !oursAlone(network);
        }
        for (KineticNetwork network : turning) {
            if (!oursAlone(network)) {
                continue;
            }
            if (!kept) {
                kept = true;
                continue;
            }
            for (KineticBlockEntity source : new ArrayList<>(network.sources.keySet())) {
                State state = state(source);
                if (state != null && state.generating) {
                    stop(source, state);
                }
            }
        }
    }

    /** Every join our trains made among the members of {@code network} cut ({@link #cut}). */
    private static void cutJoins(Level level, KineticNetwork network) {
        for (KineticBlockEntity member : new ArrayList<>(network.members.keySet())) {
            State state = state(member);
            if (state == null || state.links.isEmpty() || member.getLevel() != level) {
                continue;
            }
            for (Link link : new ArrayList<>(state.links)) {
                if (level.isLoaded(link.other) && level.getBlockEntity(link.other) instanceof KineticBlockEntity other) {
                    cut(member, other);
                }
            }
        }
    }

    /** Whether every source of {@code network} is a block one of our Motors' ends drives. */
    private static boolean oursAlone(KineticNetwork network) {
        return oursAloneBut(network, null);
    }

    private static boolean oursAloneBut(KineticNetwork network, @Nullable KineticBlockEntity but) {
        boolean any = false;
        for (KineticBlockEntity source : network.sources.keySet()) {
            if (source == but || !source.isSource()) {
                continue; // (the network keeps what was a source once)
            }
            State state = state(source);
            if (source instanceof GeneratingKineticBlockEntity || state == null || !state.generating) {
                return false;
            }
            any = true; // (one whose way is fixed stands down too: it then stands by against the network, saying so)
        }
        return any;
    }

    /**
     * Before Create rules on a generator of its own whose speed changes (GeneratingKineticBlockEntity.applyNewSpeed, its
     * head): it breaks a generator that turns against the network turning it. Never over a turning only we gave. Where
     * that network is turned by our Motors' ends alone, all free to follow, they are stopped first - Create lets the
     * generator go with them - and at their next tick our ends join the way it turns. Where the turning reaches the
     * generator through a train's join, that join is cut: the generator's side stands, and the train - its two ends now
     * turned against each other - stays unjoined, standing, until they agree. Either way the generator is then started as
     * on a still network ({@code 0} answered for the speed it had). Else the speed it had, unchanged.
     */
    public static float beforeNewSpeed(GeneratingKineticBlockEntity generator, float had, float speed) {
        Level level = generator.getLevel();
        if (level == null || level.isClientSide() || TRACKED.isEmpty() || speed == 0.0F || had == 0.0F || !generator.hasSource() || !generator.hasNetwork()
                || Math.signum(had) == Math.signum(speed) || Math.abs(had) < Math.abs(speed)) {
            return had; // not the case Create breaks it in
        }
        KineticNetwork network = generator.getOrCreateNetwork();
        if (network != null && oursAloneBut(network, generator)) {
            for (KineticBlockEntity source : new ArrayList<>(network.sources.keySet())) {
                State state = state(source);
                if (source != generator && state != null && state.generating) {
                    stop(source, state);
                }
            }
            return generator.hasSource() ? had : 0.0F;
        }
        // up the way its turning comes: the first join of a train's on it is cut
        KineticBlockEntity at = generator;
        for (int steps = 0; steps < 4096 && at.hasSource() && generator.hasSource(); steps++) {
            if (!level.isLoaded(at.source) || !(level.getBlockEntity(at.source) instanceof KineticBlockEntity from)) {
                break;
            }
            State state = state(at);
            if (state != null && state.link(from.getBlockPos()) != null) {
                cut(at, from);
            }
            at = from;
        }
        return generator.hasSource() ? had : 0.0F;
    }

    // ---- each tick's end ----

    /**
     * After every block entity's tick: a driven block with no word from a Motor for {@link #GRACE} ticks is stopped (the
     * Motor broken, unloaded, turned away); a join no train named this tick or the last is cut - but one whose other
     * block is not loaded is kept as it is, as Create keeps its own networks across a chunk's edge.
     */
    static void sweep(ServerLevel level) {
        if (TRACKED.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        List<KineticBlockEntity> all;
        synchronized (TRACKED) {
            all = new ArrayList<>(TRACKED);
        }
        for (KineticBlockEntity kinetic : all) {
            if (kinetic.isRemoved()) {
                TRACKED.remove(kinetic);
                continue;
            }
            if (kinetic.getLevel() != level) {
                continue;
            }
            State state = state(kinetic);
            if (state == null) {
                TRACKED.remove(kinetic);
                continue;
            }
            if (state.generating || state.standsBy) {
                if (state.ranAt == Long.MIN_VALUE) {
                    state.ranAt = now; // loaded with the chunk: its Motor's first tick says what is to be
                } else if (now - state.ranAt > GRACE) {
                    state.standsBy = false;
                    if (state.generating) {
                        stop(kinetic, state);
                    }
                }
            }
            if (!state.links.isEmpty()) {
                for (Link link : new ArrayList<>(state.links)) {
                    if (link.seen >= now - 1 || !level.isLoaded(link.other)) {
                        continue;
                    }
                    if (level.getBlockEntity(link.other) instanceof KineticBlockEntity other) {
                        cut(kinetic, other);
                    } else {
                        state.links.remove(link);
                    }
                }
            }
            if (!state.generating && !state.standsBy && state.links.isEmpty()) {
                TRACKED.remove(kinetic);
            }
        }
    }

    // ---- kept with the block ----

    /** At the end of Create's {@code write}: what we keep with the block (design_create.md, "Saved"). */
    public static void write(KineticBlockEntity kinetic, CompoundTag tag, boolean clientPacket) {
        State state = state(kinetic);
        if (state == null) {
            return;
        }
        if (state.generating && state.from != null) {
            CompoundTag drive = new CompoundTag();
            drive.putFloat("speed", state.given);
            drive.putFloat("capacity", state.capacity);
            drive.putLong("from", state.from.asLong());
            if (clientPacket) {
                drive.putFloat("fe", state.motorFe);
                drive.putFloat("drawn", state.drawnSu);
            }
            tag.put("nookworks_drive", drive);
        }
        if (clientPacket && kinetic.source != null) {
            Link link = state.link(kinetic.source);
            if (link != null) {
                tag.putInt("nookworks_entry", link.face.get3DDataValue());
            }
        }
    }

    /** At the end of Create's {@code read}: a driven block loaded with its chunk comes back a source; a client's copy follows the server's. */
    public static void read(KineticBlockEntity kinetic, CompoundTag tag, boolean clientPacket) {
        if (!(kinetic instanceof Holder)) {
            return;
        }
        boolean driven = tag.contains("nookworks_drive");
        State state = driven || tag.contains("nookworks_entry") ? stateOrNew(kinetic) : state(kinetic);
        if (state == null) {
            return;
        }
        if (driven) {
            CompoundTag drive = tag.getCompound("nookworks_drive");
            state.given = drive.getFloat("speed");
            state.capacity = drive.getFloat("capacity");
            state.from = BlockPos.of(drive.getLong("from"));
            state.generating = state.given != 0.0F;
            if (clientPacket) {
                state.motorFe = drive.getFloat("fe");
                state.drawnSu = drive.getFloat("drawn");
            } else {
                TRACKED.add(kinetic);
            }
        } else if (clientPacket) {
            state.generating = false;
        }
        if (clientPacket) {
            state.entry = tag.contains("nookworks_entry") ? Direction.from3DDataValue(tag.getInt("nookworks_entry")) : null;
            state.entryFrom = state.entry == null ? null : kinetic.source;
        }
    }

    // ---- read with goggles ----

    /**
     * At the end of Create's {@code addToGoggleTooltip}: on a block a Motor's end drives, Create's Generator Stats (it
     * shows them only for its own generator class) and that a Nookworks Motor turns it.
     */
    public static boolean goggles(KineticBlockEntity kinetic, List<Component> tooltip, boolean added) {
        State state = state(kinetic);
        if (state == null || !state.generating) {
            return added;
        }
        if (!(kinetic instanceof GeneratingKineticBlockEntity) && IRotate.StressImpact.isEnabled()) {
            CreateLang.translate("gui.goggles.generator_stats").forGoggles(tooltip);
            CreateLang.translate("tooltip.capacityProvided").style(ChatFormatting.GRAY).forGoggles(tooltip);
            CreateLang.number(givenSu(state)).translate("generic.unit.stress").style(ChatFormatting.AQUA).space()
                    .add(CreateLang.translate("gui.goggles.at_current_speed").style(ChatFormatting.DARK_GRAY)).forGoggles(tooltip, 1);
        }
        new LangBuilder(Nookworks.MODID).translate("create.goggles.motor_shaft").style(ChatFormatting.GRAY).forGoggles(tooltip);
        new LangBuilder(Nookworks.MODID).translate("create.goggles.motor_fe", format(state.motorFe)).style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
        return true;
    }

    static String format(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
