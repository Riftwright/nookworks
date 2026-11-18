package dev.nookworks.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import dev.nookworks.port.storage.ValueIO;

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

    private int progress;
    /** Its shafts of another mod's (Create's) stopped for want of power: not again before this tick, nor before it holds this. */
    private long shaftWaitUntil;
    private int shaftWaitFor;
    private static final int SHAFT_WAIT = 40;
    private static final int SHAFT_BUFFER = 100;
    /** As it last ticked: none of another mod's blocks at its ends took its turning, one at least for turning against its way. */
    private boolean standsAgainst;

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

    /**
     * Whether, as it last ticked (server), it ran and none of another mod's blocks at its ends took its turning, one at
     * least because a network there already turns against its way - a way an add-on fixed
     * ({@link ForeignDrives.Relay#motorFixed}; a Motor with no way of its own follows and never stands so). For that
     * add-on, which may then turn the Motor's way round. Not saved: true again a tick after loading if it still holds.
     */
    public boolean standsAgainst() {
        return this.standsAgainst;
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
        // another mod's blocks its ends drive (the port's Create: whatever has a shaft end toward its axle, or toward an
        // axle end of its line's wheels), which it keeps unless another Motor turned one last; none without such a mod
        List<ForeignDrives.Output> outputs = new ArrayList<>();
        boolean another = false;
        for (ForeignDrives.Output output : ForeignDrives.motorOutputs(level, pos, facing, wheels)) {
            if (output.heldByAnother(pos, now)) {
                another = true;
            } else {
                outputs.add(output);
            }
        }
        List<BlockEntity> wanting = new ArrayList<>();
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
        // the shafts first, paid by what is drawn of them: at the Motor's own rate, a machine's worth for FE_PER_TICK
        double drawn = 0.0;
        boolean overstressed = false;
        boolean giving = false; // one of them takes its turning (not all standing by: a network turning against a fixed way, a still generator)
        boolean against = false;
        for (ForeignDrives.Output output : outputs) {
            drawn += output.drawn();
            overstressed |= output.overstressed();
            giving |= !output.standsBy();
            against |= output.against();
        }
        double shaftFe = overstressed ? IDLE_FE_PER_TICK : Math.max(IDLE_FE_PER_TICK, drawn * FE_PER_TICK);
        boolean shafts = !outputs.isEmpty() && allowed && now >= this.shaftWaitUntil && this.energyStored() >= this.shaftWaitFor
                && this.workCostFe(shaftFe) <= this.energyStored();
        if (!outputs.isEmpty() && allowed && !shafts && now >= this.shaftWaitUntil && this.shaftWaitFor == 0) {
            // stopped for want of power: again once two seconds have gone and it holds five seconds' cost, so a Motor on a
            // thin cable turns Create in bursts rather than stuttering every few ticks
            this.shaftWaitUntil = now + SHAFT_WAIT;
            this.shaftWaitFor = (int) Math.min(this.energy().getCapacityAsLong() / 2, Math.ceil(shaftFe * SHAFT_BUFFER));
        } else if (shafts) {
            this.shaftWaitFor = 0;
        }
        double paidFe = shafts ? (overstressed ? 0.0 : drawn * FE_PER_TICK) : 0.0;
        int affordable = 0;
        if (paidFe == 0.0) {
            while (allowed && affordable < wanting.size() && this.workCost(FE_PER_TICK, affordable + 1) <= this.energyStored()) {
                affordable++;
            }
        } else {
            while (allowed && affordable < wanting.size() && this.workCostFe(paidFe + FE_PER_TICK * (affordable + 1)) <= this.energyStored()) {
                affordable++;
            }
        }
        // powered, it turns whether or not anything has work: on its own power when nothing does. A machine with work
        // it cannot pay for keeps it waiting, not idling: idle payments would eat what it saves up for the next turn
        boolean spins = allowed && (affordable > 0 || shafts
                || wanting.isEmpty() && outputs.isEmpty() && this.workCost(IDLE_FE_PER_TICK) <= this.energyStored());
        this.status = !allowed ? MachineStatus.STOPPED
                : shafts && overstressed ? MachineStatus.OVERSTRESSED
                : affordable > 0 ? MachineStatus.RUNNING
                : shafts && giving ? MachineStatus.TURNING_CREATE
                : shafts ? (against ? MachineStatus.CREATE_AGAINST : another ? MachineStatus.ANOTHER_MOTOR : MachineStatus.IDLING)
                : !spins || !wanting.isEmpty() || !outputs.isEmpty() ? MachineStatus.NO_POWER
                : another ? MachineStatus.ANOTHER_MOTOR : MachineStatus.IDLING;
        if (!outputs.isEmpty()) {
            // the budget: eight machines' worth, less one for each machine of ours on its line, shared by its shafts
            int present = wheels.isEmpty() ? 0 : DriveNetwork.machines(level, wheels,
                    machine -> DrawRights.mayMove(level, pos, this, machine.getBlockPos(), machine)).size();
            double share = Math.max(0, DriveNetwork.MOST_MACHINES - present) / (double) DriveNetwork.MOST_MACHINES / outputs.size();
            double fe = !shafts ? 0.0 : MachineTuning.workEnergyExact(1) * Math.max(IDLE_FE_PER_TICK, paidFe + FE_PER_TICK * affordable);
            boolean stands = shafts;
            boolean opposed = false;
            for (ForeignDrives.Output output : outputs) {
                output.supply(pos, now, shafts, share, fe);
                stands &= output.standsBy();
                opposed |= output.against();
            }
            this.standsAgainst = stands && opposed;
        } else {
            this.standsAgainst = false;
        }
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
            if (paidFe > IDLE_FE_PER_TICK) {
                this.payWorkFe(paidFe); // what Create draws of its shafts
            } else {
                this.payWork(IDLE_FE_PER_TICK);
            }
            savePower(this);
            return; // the turn counts only the ticks it turns a machine on
        }
        if (paidFe > 0.0) {
            this.payWorkFe(paidFe + FE_PER_TICK * served.size());
        } else {
            this.payWork(FE_PER_TICK, served.size());
        }
        if (++this.progress >= MachineTuning.time(TICKS_PER_TURN)) {
            this.progress = 0;
            for (BlockEntity machine : served) {
                ((Crankable) machine).turn(level);
            }
        }
        savePower(this); // only its turn's progress to keep: nothing reads it with a comparator
    }

    /**
     * Whether a drive other than the one at {@code drive} turned {@code machine} this tick or the last, and so keeps it:
     * the one-drive-a-machine rule (2026-10-01), for another kind of drive than a Motor (the port's Create shafts,
     * {@link ForeignDrives}).
     */
    public static boolean heldByAnother(BlockEntity machine, BlockPos drive, long now) {
        Turning turning = TURNED_BY.get(machine);
        return turning != null && !turning.motor().equals(drive) && turning.tick() >= now - 1;
    }

    /** {@code machine} is turned by the drive at {@code drive} this tick: another stands by while it does. */
    public static void hold(BlockEntity machine, BlockPos drive, long now) {
        TURNED_BY.put(machine, new Turning(drive.immutable(), now));
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
    protected void saveAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueOutput output = ValueIO.output(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.saveAdditional(portTag, portLookup);
        output.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(CompoundTag portTag, HolderLookup.Provider portLookup) {
        ValueInput input = ValueIO.input(this, portTag, portLookup); // PORT: 1.21.1 hands a tag
        super.loadAdditional(portTag, portLookup);
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
