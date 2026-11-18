package dev.nookworks.compat.create;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;

import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.machine.Crankable;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.DriveSource;
import dev.nookworks.machine.ForeignDrives;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.MotorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Create's rotation straight into Nookworks' hand-worked machines and Drive Wheels, nothing in between: any block of
 * Create's with a shaft end against a machine's driven side, or on a Drive Wheel's axle, turns it - and through the
 * wheel's belts every machine of its line - as a Motor's shaft does ({@link ForeignDrives}). The design and the
 * numbers: tools/port1211/design_create.md. (The other senses - a Motor's ends driving Create's blocks, a train with
 * no Motor joining the Create blocks at its ends - are {@link CreateEnds}; this is the hook both are reached through.)
 * <ul>
 * <li>Turning: N RPM at the shaft gives N/15 turns of a handle a second (a turn is a quarter round), at most one a tick,
 * either way round, the server's workTime counted as for a Motor. Overstressed, Create's speed is 0 and they stop.</li>
 * <li>Stress: each machine weighs its impact per RPM (2 by default: hand-sized, half Create's Millstone) on the Create block turning
 * it, whether or not it has work, as Create counts an idle machine. The load is added to that block entity's own
 * impact by a hook into Create ({@code mixin.compat.create.KineticStressMixin}), and told to its network here each
 * tick it changes ({@link #sweep}); without the hook, the sweep alone keeps the network's figure.</li>
 * <li>One drive a machine: whichever turned it last keeps it (the Motor's rule); a machine another drive keeps is not
 * counted. Across a claim's edge only where pipes and cables may go ({@link DrawRights}).</li>
 * </ul>
 * Nothing is saved: the loads are worked out from the world each tick, so a chunk going or coming, a machine taken away
 * or a shaft turned leaves nothing stale (the map is weak, and a block entity gone is dropped from it).
 */
public final class CreateDrives implements ForeignDrives.Hook {
    static final CreateDrives INSTANCE = new CreateDrives();

    /** RPM for one turn of a handle a tick at the default work time: a turn is a quarter round, 20 ticks a second. */
    public static final float RPM_PER_TURN_A_TICK = 300.0F;

    /** The Create block entities turning our machines (server): the load they were last given, in su per RPM. */
    private static final Map<KineticBlockEntity, float[]> LOADS = new WeakHashMap<>();
    /**
     * Turns owed to each machine, a fraction carried from tick to tick, and the tick it was last advanced: once a tick at
     * most, so a Create block reaching a machine twice (a shaft with belted wheels on both its ends, a gearbox turning a
     * press straight and through a wheel line) turns it no faster (review r23, 4).
     */
    private static final Map<BlockEntity, double[]> PROGRESS = new WeakHashMap<>();
    /** The figure the server last sent for a kinetic block entity's stress (Create's AddedStress), on a client. */
    private static final Map<KineticBlockEntity, Float> CLIENT_APPLIED = new WeakHashMap<>();
    /** Whether Create's own figure for a block entity includes our load (the hook into Create took). */
    static volatile boolean stressHooked;
    private CreateDrives() {}

    // ---- what counts as a drive ----

    @Override
    public boolean drives(LevelReader level, BlockPos at, BlockState state, Direction side) {
        return state.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(level, at, state, side.getOpposite());
    }

    /** The Create block entity at {@code at} with a shaft end toward its {@code side} neighbour, or null. */
    static @Nullable KineticBlockEntity kineticAt(BlockGetter level, BlockPos at, Direction side) {
        if (!(level.getBlockEntity(at) instanceof KineticBlockEntity kinetic)) {
            return null;
        }
        BlockState state = kinetic.getBlockState();
        LevelReader reader = level instanceof LevelReader r ? r : null;
        return reader != null && state.getBlock() instanceof IRotate rotate && rotate.hasShaftTowards(reader, at, state, side.getOpposite())
                ? kinetic : null;
    }

    /**
     * A Create block entity turning what is at the near end: {@code face} its shaft end toward it, {@code sign} what a
     * relay between makes of the turning (1 straight against it).
     */
    record Reach(KineticBlockEntity kinetic, Direction face, int sign) {
        /** The speed at the near end, RPM about the positive end of its axis (0 when overstressed). */
        float speed() {
            return faceSpeed(this.kinetic, this.face) * this.sign;
        }
    }

    /**
     * The Create block entity with a shaft end toward its {@code side} neighbour at {@code at} - or, where {@code at} is a
     * relay's part (an add-on's line of Shafts with no Motor coupled to it), the one at the relay's far end; null if none.
     */
    static @Nullable Reach reachAt(BlockGetter level, BlockPos at, Direction side) {
        KineticBlockEntity kinetic = kineticAt(level, at, side);
        if (kinetic != null) {
            return new Reach(kinetic, side.getOpposite(), 1);
        }
        if (ForeignDrives.relays().isEmpty() || level instanceof Level world && !world.isLoaded(at)) {
            return null;
        }
        ForeignDrives.Far far = ForeignDrives.through(level, at, side.getOpposite(), true);
        if (far == null || level instanceof Level world && !world.isLoaded(far.at())) {
            return null;
        }
        KineticBlockEntity beyond = kineticAt(level, far.at(), far.out());
        return beyond == null ? null : new Reach(beyond, far.out().getOpposite(), far.sign());
    }

    /**
     * The block entity whose stress figure carries what {@code kinetic} turns: itself, but a belt's controller for any
     * segment of it (Create counts a belt's stress on its controller alone, the other segments giving none: a press at
     * the far pulley weighed nothing, review r23, 2).
     */
    static KineticBlockEntity holder(KineticBlockEntity kinetic) {
        if (kinetic instanceof BeltBlockEntity belt && !belt.isController()) {
            BeltBlockEntity controller = belt.getControllerBE();
            if (controller != null) {
                return controller;
            }
        }
        return kinetic;
    }

    /** How a shaft end of {@code kinetic} on its {@code face} turns against the block itself: 0 where a clutch cuts it ({@link CreateEnds#faceModifier}). */
    static float faceModifier(KineticBlockEntity kinetic, Direction face) {
        return CreateEnds.faceModifier(kinetic, face);
    }

    /**
     * The speed at {@code kinetic}'s shaft end on its {@code face}, in RPM about the positive end of that face's axis:
     * its own, but through a clutch or gearshift as that side turns, and out of a gearbox's side as Create draws it.
     */
    static float faceSpeed(KineticBlockEntity kinetic, Direction face) {
        float speed = kinetic.getSpeed();
        return speed == 0.0F ? 0.0F : speed * CreateEnds.faceModifier(kinetic, face);
    }

    /** {@link #faceSpeed}, for the client's look. */
    public static float faceSpeedForLook(KineticBlockEntity kinetic, Direction face) {
        return faceSpeed(kinetic, face);
    }

    /** Turns of a handle a tick at {@code rpm}, the server's work-time setting counted as for a Motor. */
    public static float turnsPerTick(float rpm) {
        float turns = Math.abs(rpm) / RPM_PER_TURN_A_TICK * MotorBlockEntity.TICKS_PER_TURN / MachineTuning.time(MotorBlockEntity.TICKS_PER_TURN);
        return Math.min(1.0F, turns);
    }

    /** A test's own impact for one machine, the settings left alone (tests run side by side). */
    private static final Map<BlockEntity, Float> TEST_IMPACT = new WeakHashMap<>();

    public static void impactForTest(BlockEntity machine, float suPerRpm) {
        TEST_IMPACT.put(machine, suPerRpm);
    }

    /** A machine's stress impact in su/RPM (a test's, if it set one). */
    static float impactOf(BlockEntity machine) {
        Float test = TEST_IMPACT.get(machine);
        return test != null ? test : impactOf(machine.getBlockState());
    }

    /** Whether the hook into Create's stress took (it is asked by Create, once a network counts a block entity). */
    public static boolean stressHooked() {
        return stressHooked;
    }

    /** Whether {@code kinetic}'s load is being kept (it turns something of ours now). */
    public static boolean tracks(KineticBlockEntity kinetic) {
        return LOADS.containsKey(kinetic);
    }

    /** A machine's stress impact in su/RPM: its own registered with Create, else the setting for any other's. */
    public static float impactOf(BlockState machine) {
        double impact = BlockStressValues.getImpact(machine.getBlock());
        return (float) (impact > 0 ? impact : CreateConfig.get(CreateConfig.OTHER_MACHINE_IMPACT));
    }

    // ---- turning ----

    @Override
    public void tickMachine(ServerLevel level, BlockEntity machine, Direction side) {
        BlockPos at = machine.getBlockPos().relative(side);
        if (!(machine instanceof Crankable crankable) || !crankable.drivenFrom(side) || !level.isLoaded(at)) {
            return;
        }
        Reach reach = reachAt(level, at, side); // straight against it, or through a relay's line (the add-on's Shafts)
        if (reach == null) {
            return;
        }
        KineticBlockEntity holder = holder(reach.kinetic());
        touch(holder);
        long now = level.getGameTime();
        if (!mayTurn(level, holder, machine, now)) {
            return;
        }
        float speed = reach.speed();
        if (speed != 0.0F && crankable.wantsTurn()) {
            MotorBlockEntity.hold(machine, holder.getBlockPos(), now); // it is this drive's while it turns it
            if (advance(machine, speed, now)) {
                crankable.turn(level);
            }
        }
    }

    @Override
    public void tickWheel(Level level, DriveWheelBlockEntity wheel) {
        Direction.Axis axis = wheel.axis();
        Reach[] reaches = new Reach[2];
        boolean any = false;
        for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
            Direction side = Direction.fromAxisAndDirection(axis, way);
            BlockPos at = wheel.getBlockPos().relative(side);
            if (level.isLoaded(at)) {
                reaches[way.ordinal()] = reachAt(level, at, side);
                any |= reaches[way.ordinal()] != null;
            }
        }
        if (!any) {
            return;
        }
        // its line as a train: a Motor's gives at its ends and is never turned by Create (design_create.md); one with
        // none joins the Create blocks at its ends, and stands where two of them turn it against each other
        WheelTrain train = train(level, wheel);
        if (train.motor() || !train.agree()) {
            return;
        }
        for (Reach reach : reaches) {
            if (reach == null) {
                continue;
            }
            float speed = reach.speed();
            if (level instanceof ServerLevel server) {
                turnLine(server, reach.kinetic(), wheel, speed);
            } else if (speed != 0.0F) {
                long now = level.getGameTime();
                for (DriveWheelBlockEntity each : DriveNetwork.wheels(level, wheel)) {
                    each.pace(speed * 0.3F, now); // Create's degrees a tick: RPM x 360 / 60 / 20
                }
            }
        }
    }

    /** A Drive Wheel line as a train, worked out once a tick for all its wheels: whether a Motor is on it, and what its Create ends make of it. */
    public record WheelTrain(boolean motor, boolean agree, ForeignDrives.Bridge bridge, int ends) {}

    private static final class TrainTick {
        long time = Long.MIN_VALUE;
        final Map<BlockPos, WheelTrain> trains = new java.util.HashMap<>();
    }

    /** By level (a client's and a server's each its own, each touched by its own thread). */
    private static final Map<Level, TrainTick> TRAINS = java.util.Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * The line of {@code wheel} as a train: the Create blocks at the axle ends of its wheels (straight against them, or
     * at the far end of an add-on's line there). With no Motor on it they are joined through it on the server
     * ({@link CreateEnds#join}); a client only asks whether they agree, for the wheels' look.
     */
    public static WheelTrain train(Level level, DriveWheelBlockEntity wheel) {
        TrainTick tick = TRAINS.computeIfAbsent(level, l -> new TrainTick());
        long now = level.getGameTime();
        if (tick.time != now) {
            tick.time = now;
            tick.trains.clear();
        }
        WheelTrain had = tick.trains.get(wheel.getBlockPos());
        if (had != null) {
            return had;
        }
        List<DriveWheelBlockEntity> wheels = DriveNetwork.wheels(level, wheel);
        boolean motor = motorOn(level, wheels);
        List<ForeignDrives.Meet> meets = new ArrayList<>(2);
        if (!motor) {
            for (DriveWheelBlockEntity each : wheels) {
                for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                    Direction side = Direction.fromAxisAndDirection(each.axis(), way);
                    BlockPos at = each.getBlockPos().relative(side);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    if (kineticAt(level, at, side) != null) {
                        meets.add(new ForeignDrives.Meet(at, side, 1));
                    } else if (!ForeignDrives.relays().isEmpty()) {
                        ForeignDrives.Far far = ForeignDrives.through(level, at, side.getOpposite(), true);
                        if (far != null && level.isLoaded(far.at()) && kineticAt(level, far.at(), far.out()) != null) {
                            meets.add(new ForeignDrives.Meet(far.at(), far.out(), far.sign()));
                        }
                    }
                }
            }
        }
        ForeignDrives.Bridge bridge = ForeignDrives.Bridge.NONE;
        boolean agree = true;
        if (meets.size() >= 2) {
            if (level instanceof ServerLevel server) {
                bridge = CreateEnds.join(server, meets);
                agree = bridge != ForeignDrives.Bridge.STALLED;
            } else {
                agree = CreateEnds.agree(level, meets);
                bridge = agree ? ForeignDrives.Bridge.NONE : ForeignDrives.Bridge.STALLED;
            }
        }
        WheelTrain train = new WheelTrain(motor, agree, bridge, meets.size());
        for (DriveWheelBlockEntity each : wheels) {
            tick.trains.put(each.getBlockPos().immutable(), train);
        }
        return train;
    }

    /** A Create shaft on a wheel's axle: the wheels and belts go round with it, and every machine of the line it may turn. */
    private static void turnLine(ServerLevel level, KineticBlockEntity kinetic, DriveWheelBlockEntity wheel, float speed) {
        KineticBlockEntity holder = holder(kinetic);
        touch(holder);
        if (speed == 0.0F) {
            return;
        }
        long now = level.getGameTime();
        List<DriveWheelBlockEntity> wheels = DriveNetwork.wheels(level, wheel);
        List<BlockEntity> machines = DriveNetwork.machines(level, wheels, machine -> !ForeignDrives.relayPart(level, machine.getBlockPos())
                && mayTurn(level, holder, machine, now)); // a relay's line at an axle end is no machine (its own end is reached as such)
        for (DriveWheelBlockEntity each : wheels) {
            each.driven(now);
        }
        for (BlockEntity machine : machines) {
            Crankable crankable = (Crankable) machine;
            if (crankable.wantsTurn()) {
                MotorBlockEntity.hold(machine, holder.getBlockPos(), now);
                if (advance(machine, speed, now)) {
                    crankable.turn(level);
                }
            }
        }
    }

    /** Carries a fraction of a turn on, once a tick at most; whether a whole one is due now. */
    private static boolean advance(BlockEntity machine, float speed, long now) {
        double[] progress = PROGRESS.computeIfAbsent(machine, k -> new double[] {0.0, Long.MIN_VALUE});
        if (progress[1] == now) {
            return false; // the same drive reached it again this tick
        }
        progress[1] = now;
        progress[0] += turnsPerTick(speed);
        if (progress[0] >= 1.0) {
            progress[0] -= 1.0;
            return true;
        }
        return false;
    }

    /** Whether the Create block at {@code kinetic} may turn {@code machine}: no other drive keeps it, and no claim forbids it. */
    private static boolean mayTurn(ServerLevel level, KineticBlockEntity kinetic, BlockEntity machine, long now) {
        return !MotorBlockEntity.heldByAnother(machine, kinetic.getBlockPos(), now)
                && DrawRights.mayMove(level, kinetic.getBlockPos(), kinetic, machine.getBlockPos(), machine);
    }

    // ---- stress ----

    /** {@code kinetic} turns something of ours: its load is kept current from now on (until it turns nothing). */
    private static void touch(KineticBlockEntity kinetic) {
        LOADS.computeIfAbsent(kinetic, k -> new float[] {Float.NaN});
    }

    /**
     * The load our machines put on {@code kinetic}, in su per RPM: each machine set against one of its shaft ends (whose
     * look shows its drive there) that the end turns (not one a powered clutch cuts off, review r23, 3), and each one of
     * the lines of Drive Wheels on its shaft ends that no Motor's axle meets - each machine once however many ways it is
     * reached (review r23, 4), and on the server only those it may turn (no other drive keeps them, no claim forbids). A
     * belt's controller carries what all its pulleys turn, its other segments nothing. Worked out on the server; a
     * client reads the server's figure ({@link #loadOn}).
     */
    public static float load(KineticBlockEntity kinetic) {
        Level level = kinetic.getLevel();
        if (level == null || kinetic.isRemoved()) {
            return 0.0F;
        }
        Set<BlockEntity> machines = new LinkedHashSet<>();
        RELAY_LOAD.get()[0] = 0.0F;
        if (kinetic instanceof BeltBlockEntity belt) {
            if (!belt.isController()) {
                return 0.0F;
            }
            for (int i = 0; i < belt.beltLength; i++) {
                BeltBlockEntity segment = BeltHelper.getBeltAtSegment(belt, i);
                if (segment != null) {
                    reached(level, kinetic, segment, machines);
                }
            }
        } else {
            reached(level, kinetic, kinetic, machines);
        }
        float sum = RELAY_LOAD.get()[0];
        for (BlockEntity machine : machines) {
            sum += impactOf(machine);
        }
        return sum;
    }

    /** The machines {@code part} (of {@code holder}: itself, or a segment of a belt) turns, into {@code machines}. */
    private static void reached(Level level, KineticBlockEntity holder, KineticBlockEntity part, Set<BlockEntity> machines) {
        BlockPos pos = part.getBlockPos();
        BlockState state = part.getBlockState();
        if (!(state.getBlock() instanceof IRotate rotate)) {
            return;
        }
        ServerLevel server = level instanceof ServerLevel s ? s : null;
        long now = level.getGameTime();
        for (Direction face : Direction.values()) {
            BlockPos next = pos.relative(face);
            if (!rotate.hasShaftTowards(level, pos, state, face) || !level.isLoaded(next) || faceModifier(part, face) == 0.0F) {
                continue;
            }
            Direction toward = face; // the way from what turns to what is turned, at the block reached
            BlockEntity there = level.getBlockEntity(next);
            if (!ForeignDrives.relays().isEmpty()) {
                // a relay's line (the add-on's Shafts) with no Motor coupled: what is at its far end, as if set against this end
                ForeignDrives.Far far = ForeignDrives.through(level, next, face.getOpposite(), true);
                if (far != null && level.isLoaded(far.at())) {
                    next = far.at();
                    toward = far.out();
                    there = level.getBlockEntity(next);
                }
            }
            if (there instanceof Crankable crankable && !(there instanceof DriveWheelBlockEntity) && crankable.drivenFrom(toward.getOpposite())
                    && there.getBlockState().getOptionalValue(Drive.PROPERTY).map(Drive::side).orElse(null) == toward.getOpposite()) {
                if (server == null || mayTurn(server, holder, there, now)) {
                    machines.add(there);
                }
            } else if (there instanceof DriveWheelBlockEntity wheel && wheel.axis() == toward.getAxis()) {
                List<DriveWheelBlockEntity> wheels = DriveNetwork.wheels(level, wheel);
                if (!motorOn(level, wheels)) {
                    machines.addAll(DriveNetwork.machines(level, wheels, m -> !ForeignDrives.relayPart(level, m.getBlockPos())
                            && (server == null || mayTurn(server, holder, m, now))));
                    if (!ForeignDrives.relays().isEmpty()) {
                        RELAY_LOAD.get()[0] += wheelEndLoads(level, wheels, holder.getBlockPos());
                    }
                }
            } else if (there != null && !ForeignDrives.relays().isEmpty()) {
                RELAY_LOAD.get()[0] += ForeignDrives.relayLoad(level, next, toward.getOpposite(), holder.getBlockPos());
            }
        }
    }

    /**
     * What the relays' parts at the axle ends of {@code wheels} (an add-on's belt run with its drum on a wheel's axle) put on
     * the Create block at {@code drive} turning those wheels: su per RPM, beside the line's machines.
     */
    static float wheelEndLoads(Level level, List<DriveWheelBlockEntity> wheels, BlockPos drive) {
        if (ForeignDrives.relays().isEmpty()) {
            return 0.0F;
        }
        float sum = 0.0F;
        for (DriveWheelBlockEntity wheel : wheels) {
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                Direction side = Direction.fromAxisAndDirection(wheel.axis(), way);
                BlockPos end = wheel.getBlockPos().relative(side);
                if (level.isLoaded(end) && !(level.getBlockEntity(end) instanceof DriveWheelBlockEntity) && level.getBlockEntity(end) != null) {
                    sum += ForeignDrives.relayLoad(level, end, side.getOpposite(), drive);
                }
            }
        }
        return sum;
    }

    /** What the relays' parts (an add-on's belt runs) put on the block being worked out, besides our machines: su per RPM. */
    private static final ThreadLocal<float[]> RELAY_LOAD = ThreadLocal.withInitial(() -> new float[1]);

    /**
     * Whether a Motor turns {@code wheels} - its axle against one, or coupled to them by a relay's parts (the add-on's
     * lines): then they are the Motor's, and turn Create's shafts rather than being turned by them.
     */
    static boolean motorOn(Level level, List<DriveWheelBlockEntity> wheels) {
        return DriveNetwork.motorOn(level, wheels) || !ForeignDrives.relays().isEmpty() && ForeignDrives.motorReaches(level, wheels);
    }

    /** What the hook into Create adds to a block entity's own impact: on the server the load last worked out, on a client now. */
    public static float loadOn(KineticBlockEntity kinetic) {
        stressHooked = true;
        Level level = kinetic.getLevel();
        if (level == null) {
            return 0.0F;
        }
        if (level.isClientSide()) {
            // what the server worked out (its AddedStress, sent with the block's data), less the block's own: the client
            // cannot ask who keeps a machine or what a claim allows (review r23, 6)
            return clientLoad(kinetic);
        }
        float[] kept = LOADS.get(kinetic);
        return kept == null || Float.isNaN(kept[0]) ? 0.0F : kept[0];
    }

    /** On a client, our load on {@code kinetic} as the server sent it: its figure less the block's own impact. */
    public static float clientLoad(KineticBlockEntity kinetic) {
        Float applied = CLIENT_APPLIED.get(kinetic);
        return applied == null ? 0.0F : Math.max(0.0F, applied - (float) BlockStressValues.getImpact(kinetic.getBlockState().getBlock()));
    }

    /** A client's copy of the server's stress figure for {@code kinetic}, as its data came (the hook into Create's read). */
    public static void clientApplied(KineticBlockEntity kinetic, float applied) {
        CLIENT_APPLIED.put(kinetic, applied);
    }

    /**
     * After every block entity's tick: each Create block entity turning our machines has its load worked out again, and
     * its network told when it changed (Create's own way, {@link KineticNetwork#updateStressFor}); one that turns nothing
     * any more, or is gone, is let go.
     */
    static void sweep(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        CreateEnds.sweep(level); // the blocks our Motors' ends drive and our trains join
        if (LOADS.isEmpty()) {
            return;
        }
        List<KineticBlockEntity> mine = new ArrayList<>();
        for (Iterator<Map.Entry<KineticBlockEntity, float[]>> it = LOADS.entrySet().iterator(); it.hasNext(); ) {
            KineticBlockEntity kinetic = it.next().getKey();
            if (kinetic.isRemoved()) {
                it.remove();
            } else if (kinetic.getLevel() == level) {
                mine.add(kinetic);
            }
        }
        for (KineticBlockEntity kinetic : mine) {
            float[] kept = LOADS.get(kinetic);
            float load = load(kinetic);
            boolean changed = kept[0] != load;
            kept[0] = load;
            if (kinetic.hasNetwork()) {
                KineticNetwork network = kinetic.getOrCreateNetwork();
                Float member = network.members.get(kinetic);
                if (member != null) {
                    float applied = stressHooked ? kinetic.calculateStressApplied()
                            : (float) BlockStressValues.getImpact(kinetic.getBlockState().getBlock()) + load;
                    if (member != applied) {
                        network.updateStressFor(kinetic, applied);
                    }
                }
            }
            if (changed) {
                kinetic.sendData(); // the goggles and the stressometer on a client read the new figure
            }
            if (load == 0.0F) {
                LOADS.remove(kinetic);
            }
        }
    }

    // ---- an add-on's own takers, and its lines between two of Create's networks ----

    @Override
    public ForeignDrives.@Nullable Taken taken(Level level, BlockPos at, Direction side) {
        Reach reach = reachAt(level, at.relative(side), side);
        if (reach == null) {
            return null;
        }
        KineticBlockEntity holder = holder(reach.kinetic());
        if (level instanceof ServerLevel server) {
            touch(holder);
            if (!DrawRights.mayMove(server, holder.getBlockPos(), holder, at, level.getBlockEntity(at))) {
                return null; // across a claim's edge only where pipes and cables may go
            }
        }
        return new ForeignDrives.Taken(holder.getBlockPos(), reach.speed(), reach.kinetic().isOverStressed());
    }

    /** The speed at {@code kinetic}'s {@code face} it is meant to turn at, overstressed or not (Create's theoretical speed). */
    static float theoreticalFaceSpeed(KineticBlockEntity kinetic, Direction face) {
        return CreateEnds.theoreticalFaceSpeed(kinetic, face);
    }

    /** A train with no Motor: the Create blocks at its ends joined in Create's own network ({@link CreateEnds#join}). */
    @Override
    public ForeignDrives.Bridge join(ServerLevel level, List<ForeignDrives.Meet> ends) {
        return CreateEnds.join(level, ends);
    }

    /** {@link #join} for a train of two ends, saying which end the turning comes in at. */
    @Override
    public ForeignDrives.Bridge bridge(ServerLevel level, BlockPos a, Direction outA, BlockPos b, Direction outB, int sign) {
        ForeignDrives.Bridge joined = CreateEnds.join(level, List.of(new ForeignDrives.Meet(a, outA, 1), new ForeignDrives.Meet(b, outB, sign)));
        if (joined != ForeignDrives.Bridge.TOGETHER) {
            return joined;
        }
        KineticBlockEntity ka = kineticAt(level, a, outA);
        KineticBlockEntity kb = kineticAt(level, b, outB);
        if (ka != null && kb != null) {
            if (kb.hasSource() && kb.source.equals(a)) {
                return ForeignDrives.Bridge.A_TO_B;
            }
            if (ka.hasSource() && ka.source.equals(b)) {
                return ForeignDrives.Bridge.B_TO_A;
            }
        }
        return ForeignDrives.Bridge.TOGETHER;
    }

    // ---- a Motor turning Create ----

    @Override
    public List<ForeignDrives.Output> motorOutputs(ServerLevel level, BlockPos motor, Direction facing, List<DriveWheelBlockEntity> wheels) {
        return CreateEnds.outputs(level, motor, facing, wheels);
    }

    // ---- the look ----

    @Override
    public @Nullable DriveSource source(BlockGetter level, BlockPos at, Direction side) {
        KineticBlockEntity kinetic = kineticAt(level, at, side);
        return kinetic == null ? null : new Source(kinetic, side.getOpposite());
    }

    /** A Create block entity as what turns a machine's axle on the client: at its shaft end's angle, as Create draws it. */
    private record Source(KineticBlockEntity kinetic, Direction face) implements DriveSource {
        @Override
        public float shaftDegrees(float partialTick) {
            return dev.nookworks.compat.create.client.ShaftLook.degrees(this.kinetic, this.face);
        }

        @Override
        public boolean turning() {
            return faceSpeed(this.kinetic, this.face) != 0.0F;
        }

        @Override
        public Direction spinAxis() {
            return DriveSource.positive(this.face.getAxis());
        }
    }
}
