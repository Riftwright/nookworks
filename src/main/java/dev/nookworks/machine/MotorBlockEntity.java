package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Motor: an electric motor that turns the handle of the machine its shaft points into (anything {@link Crankable},
 * such as the Oil Press, from a side it can be driven from), a turn every half second on FE. Powered, it runs - with work
 * or without, with a machine on its shaft or none - until a redstone signal stops it (the user, 2026-10-25: a motor that
 * stood still when power reached it puzzled anyone used to Create): {@link #IDLE_FE_PER_TICK} to turn on its own, and
 * {@link #FE_PER_TICK} for each machine it turns that has work.
 * <p>
 * With a Drive Wheel on its shaft instead, it turns every machine the wheel's belts reach - each through a wheel of its
 * own set against it - up to {@link DriveNetwork#MOST_MACHINES}, at {@link #FE_PER_TICK} for each that has work (the
 * user, 2026-10-10: a motor for every machine looked poor). As much as it has power for, the first found first.
 * <p>
 * One motor turns a machine: two set against the same press turn it no faster than one (the user, 2026-10-01). The
 * motor that turned it last keeps it; another stands by, turning on its own, and takes over the tick after the first
 * stops - for want of power, or a redstone signal.
 * <p>
 * Stopped by redstone, it holds what the machines it would turn have made, as a machine stopped by redstone does
 * ({@link #holdsHandOut}): an Oil Press's cakes wait in it, not out of its spout.
 * <p>
 * Across a claim's edge it turns a machine only where the machine's owner could break the motor, as power goes along a
 * cable ({@link DrawRights}): not a stranger's set against a claimed motor's shaft, or against a wheel of theirs set end
 * to end with the motor's.
 */
public class MotorBlockEntity extends MachineBlockEntity implements DriveSource {
    public static final int FE_PER_TICK = 20;
    /** What it draws turning with no machine at work (the user, 2026-10-25: little, as it runs whenever powered). */
    public static final int IDLE_FE_PER_TICK = 2;
    public static final int TICKS_PER_TURN = 10;
    private static final int[] NO_SLOTS = {};

    /** Which motor turns each machine, and when it last did: kept with the machine, and gone with it. */
    private static final Map<BlockEntity, Turning> TURNED_BY = new WeakHashMap<>();

    private record Turning(BlockPos motor, long tick) {}

    /**
     * The motors that lately reached each machine they would turn, with work or none, stopped or running - where each
     * stands, and the tick: {@link #holdsHandOut} reads their redstone as it stands. Kept with the machine, and gone with
     * it, as {@link #TURNED_BY} is.
     */
    private static final Map<BlockEntity, Map<BlockPos, Long>> REACHED_BY = new WeakHashMap<>();

    /**
     * Finds the motors that would turn a machine, where an add-on carries a motor's turn further than Nookworks' shafts and
     * wheels do: {@link #holdsHandOut} asks it when no motor has reached the machine yet (its chunk just in).
     */
    @FunctionalInterface
    public interface MotorFinder {
        Collection<MotorBlockEntity> motors(ServerLevel level, BlockEntity machine);
    }

    private static final List<MotorFinder> FINDERS = new CopyOnWriteArrayList<>();

    /** Adds a {@link MotorFinder} (nookTechnology's for its Shafts). */
    public static void addMotorFinder(MotorFinder finder) {
        FINDERS.add(finder);
    }

    /**
     * Whether a machine worked by its handle keeps what it has made in: while a motor stopped by redstone stands against
     * it, and none that may run does - as a machine stopped by redstone puts nothing out (the user, 2026-11-26: 「おすすめで
     * 作っていいよ」; an Oil Press loading a cart, its motor on a detector rail, went on dropping its cakes once the motor
     * stopped). Turned by hand alone, or by a motor short of power, it never holds.
     */
    public static boolean holdsHandOut(BlockEntity machine) {
        if (!(machine.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        long now = level.getGameTime();
        List<MotorBlockEntity> motors = new ArrayList<>();
        Map<BlockPos, Long> reached = REACHED_BY.get(machine);
        if (reached != null) {
            reached.values().removeIf(tick -> tick < now - 1);
            for (BlockPos at : reached.keySet()) {
                if (level.isLoaded(at) && level.getBlockEntity(at) instanceof MotorBlockEntity motor) {
                    motors.add(motor);
                }
            }
        }
        if (motors.isEmpty()) {
            // none reached it this tick or the last: with its chunk just in, or both just set down, the machine may tick
            // before its motor, and a held press dropped its cakes once (nookTechnology's find): the motors around it
            motors = motorsAround(level, machine);
        }
        // each read as it stands, not as it was when it ticked: stopped the tick the press would put out, it holds (r24
        // CX-02); two on one machine, one running, it does not (r24 GM-01)
        boolean held = false;
        for (MotorBlockEntity motor : motors) {
            if (motor.redstoneAllows()) {
                return false; // one that may run turns it
            }
            held = true;
        }
        return held;
    }

    /**
     * The motors set against {@code machine} - on its shaft, or at a wheel belted to one set against it, or as an add-on's
     * {@link MotorFinder} finds them - as far as each may turn it: {@link #holdsHandOut} with none noted.
     */
    private static List<MotorBlockEntity> motorsAround(ServerLevel level, BlockEntity machine) {
        if (!(machine instanceof Crankable crankable)) {
            return List.of();
        }
        BlockPos at = machine.getBlockPos();
        List<MotorBlockEntity> motors = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos next = at.relative(side);
            if (!crankable.drivenFrom(side) || !level.isLoaded(next)) {
                continue;
            }
            BlockEntity there = level.getBlockEntity(next);
            if (there instanceof MotorBlockEntity motor && motor.getBlockState().getValue(MotorBlock.FACING) == side.getOpposite()) {
                motors.add(motor);
            } else if (there instanceof DriveWheelBlockEntity wheel && wheel.axis() == side.getAxis()) {
                for (DriveWheelBlockEntity each : DriveNetwork.wheels(level, wheel)) {
                    for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                        Direction toward = Direction.fromAxisAndDirection(each.axis(), way);
                        BlockPos end = each.getBlockPos().relative(toward);
                        if (level.isLoaded(end) && level.getBlockEntity(end) instanceof MotorBlockEntity motor
                                && motor.getBlockState().getValue(MotorBlock.FACING) == toward.getOpposite()) {
                            motors.add(motor);
                        }
                    }
                }
            }
        }
        for (MotorFinder finder : FINDERS) {
            for (MotorBlockEntity motor : finder.motors(level, machine)) {
                if (!motors.contains(motor)) {
                    motors.add(motor);
                }
            }
        }
        motors.removeIf(motor -> !DrawRights.mayMove(level, motor.getBlockPos(), motor, at, machine));
        return motors;
    }

    private int progress;

    /**
     * How fast the shaft turns while the motor runs, in degrees a tick: a quarter turn - what a hand gives a machine's
     * handle each time it works it - each turn of the motor. Only how it looks, and the client's alone.
     */
    public static final float DEGREES_PER_TICK = 90.0F / TICKS_PER_TURN;
    /** How far round the shaft is, and how far it went last tick: it runs up and down, not starting or stopping dead. */
    private float shaft;
    private float step;

    public MotorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MOTOR.get(), pos, state, 0, 10_000, 200, 0);
    }

    /** A motor from before its cable sockets were drawn shows them once its chunk is in. */
    @Override
    public void onLoad() {
        super.onLoad();
        MotorBlock.socketsOnLoad(this);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, MotorBlockEntity motor) {
        if (level instanceof ServerLevel server) {
            motor.tick(server, pos, state);
        }
    }

    /** On the client: the shaft going round while the motor runs (its coil lit), running up and down over a few ticks. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, MotorBlockEntity motor) {
        float target = state.getValue(MotorBlock.POWERED) ? DEGREES_PER_TICK : 0.0F;
        motor.step += (target - motor.step) * 0.25F;
        if (motor.step < 0.05F && target == 0.0F) {
            motor.step = 0.0F;
        }
        motor.shaft = (motor.shaft + motor.step) % 360.0F;
        if (level.isClientSide()) {
            dev.nookworks.client.MotorHum.keep(level, pos, state); // its hum held on while it runs
        }
    }

    /**
     * How far round the shaft is now, in degrees about the way it points: what MotorRenderer turns it by, and what the
     * machine it drives turns its axle and handle by, so they go round as one (client/render/CrankRenderer).
     */
    public float shaftDegrees(float partialTick) {
        return this.shaft - this.step * (1.0F - partialTick);
    }

    @Override
    public boolean turning() {
        return this.step > 0.0F;
    }

    @Override
    public Direction spinAxis() {
        return DriveSource.positive(this.getBlockState().getValue(MotorBlock.FACING).getAxis());
    }

    private void tick(ServerLevel level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(MotorBlock.FACING);
        BlockPos target = pos.relative(facing);
        BlockEntity entity = level.isLoaded(target) ? level.getBlockEntity(target) : null;
        // what it turns: the machine on its shaft, if it is driven from that side; or every machine a wheel there reaches
        List<BlockEntity> machines = new ArrayList<>();
        List<DriveWheelBlockEntity> wheels = List.of();
        if (entity instanceof Crankable crankable && crankable.drivenFrom(facing.getOpposite())) {
            machines.add(entity);
        } else if (entity instanceof DriveWheelBlockEntity wheel && wheel.axis() == facing.getAxis()) {
            wheels = DriveNetwork.wheels(level, wheel);
            // counted only as it may turn them: one across a claim's edge it may not is no machine of its eight; nor is
            // one with no work, which took the place of a ninth that had some (2026-10-31, left to me: 「おまかせ」),
            // and is asked first, so an idle machine across a claim's edge costs the claim mod no question
            machines.addAll(DriveNetwork.machines(level, wheels, machine -> ((Crankable) machine).wantsTurn()
                    && DrawRights.mayMove(level, pos, this, machine.getBlockPos(), machine)));
        }
        long now = level.getGameTime();
        List<BlockEntity> wanting = new ArrayList<>();
        boolean another = false;
        for (BlockEntity machine : machines) {
            // its power goes into the machine as a cable's would, and is asked the same across a claim's edge, as the
            // machine's owner breaking this motor: a stranger's press set against a claimed motor's shaft from outside,
            // or beyond a wheel of theirs set end to end with its wheel, ran on the claim's owner's power (r15 fix check
            // 4). The one on its shaft is asked only when it has work to do; those beyond its wheels were asked as they
            // were counted (the answer kept, DrawRights). One refused is let be, as one with no work is
            if (!((Crankable) machine).wantsTurn() || !DrawRights.mayMove(level, pos, this, machine.getBlockPos(), machine)) {
                continue;
            }
            Turning turning = TURNED_BY.get(machine);
            if (turning != null && !turning.motor().equals(pos) && turning.tick() >= now - 1) {
                another = true; // another motor has it: that one keeps it
            } else {
                wanting.add(machine);
            }
        }
        // as many as it can pay for together, the fractions carried (r14 re-check: priced one by one, at under 1 FE a
        // machine it turned them all on nothing)
        boolean allowed = this.redstoneAllows();
        this.noteReach(level, pos, machines, wheels, now);
        int affordable = 0;
        while (allowed && affordable < wanting.size() && this.workCost(FE_PER_TICK, affordable + 1) <= this.energyStored()) {
            affordable++;
        }
        // powered, it turns whether or not anything has work: on its own power when nothing does. A machine with work
        // it cannot pay for keeps it waiting, not idling: idle payments would eat what it saves up for the next turn
        boolean spins = allowed && (affordable > 0
                || wanting.isEmpty() && this.workCost(IDLE_FE_PER_TICK) <= this.energyStored());
        this.status = !allowed ? MachineStatus.STOPPED
                : affordable > 0 ? MachineStatus.RUNNING
                : !spins || !wanting.isEmpty() ? MachineStatus.NO_POWER
                : another ? MachineStatus.ANOTHER_MOTOR : MachineStatus.IDLING;
        // its coil lit a moment after it last turned: short of power it turns every other tick, and would blink
        boolean lit = this.showsLit(spins);
        if (state.getValue(MotorBlock.POWERED) != lit) {
            level.setBlock(pos, state.setValue(MotorBlock.POWERED, lit), 3);
        }
        if (lit) {
            for (DriveWheelBlockEntity wheel : wheels) {
                wheel.driven(now); // the wheels and their belts go round with it
            }
        }
        if (!spins) {
            return;
        }
        List<BlockEntity> served = wanting.subList(0, affordable);
        for (BlockEntity machine : served) {
            TURNED_BY.put(machine, new Turning(pos.immutable(), now));
        }
        if (served.isEmpty()) {
            this.payWork(IDLE_FE_PER_TICK);
            savePower(this);
            return; // the turn counts only the ticks it turns a machine on
        }
        this.payWork(FE_PER_TICK, served.size());
        if (++this.progress >= MachineTuning.time(TICKS_PER_TURN)) {
            this.progress = 0;
            for (BlockEntity machine : served) {
                ((Crankable) machine).turn(level);
            }
        }
        savePower(this); // only its turn's progress to keep: nothing reads it with a comparator
    }

    /**
     * Notes, for {@link #holdsHandOut}, that it reaches every machine it would turn - with work or none, running or
     * stopped, as far as it may turn it: those on its shaft and its wheels' belts, those an add-on gathered for it, and
     * those these pass a turn on to ({@link Crankable#passesTo}). It reads no signal: holdsHandOut reads each motor's as
     * it stands, and an add-on stopping a motor for its own reasons (nookTechnology, one set against another through
     * what they turn) holds nothing.
     */
    private void noteReach(ServerLevel level, BlockPos pos, List<BlockEntity> machines, List<DriveWheelBlockEntity> wheels, long now) {
        Set<BlockEntity> reached = new LinkedHashSet<>(machines);
        if (!wheels.isEmpty()) {
            reached.addAll(DriveNetwork.machines(level, wheels, machine -> DrawRights.mayMove(level, pos, this, machine.getBlockPos(), machine)));
        }
        List<BlockEntity> open = new ArrayList<>(reached);
        for (int i = 0; i < open.size() && reached.size() < 4 * DriveNetwork.MOST_MACHINES; i++) {
            if (open.get(i) instanceof Crankable crankable) {
                for (BlockEntity next : crankable.passesTo()) {
                    if (reached.add(next)) {
                        open.add(next);
                    }
                }
            }
        }
        BlockPos at = pos.immutable();
        for (BlockEntity machine : reached) {
            if (DrawRights.mayMove(level, pos, this, machine.getBlockPos(), machine)) {
                REACHED_BY.computeIfAbsent(machine, key -> new HashMap<>()).put(at, now);
            }
        }
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.motor", this.energyText(), Component.translatable(this.status.translationKey()));
    }

    /** To Jade, only what it is doing: its power Jade shows itself. */
    @Override
    public void jade(java.util.function.Consumer<Component> lines) {
        lines.accept(Component.translatable(this.status.translationKey()));
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return NO_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return false;
    }
}
