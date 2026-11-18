package dev.nookworks.compat.create;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import dev.nookworks.Nookworks;
import dev.nookworks.drive.DriveNetwork;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.MotorBlockEntity;
import net.createmod.catnip.lang.LangBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What Create's Engineer's Goggles show on a Nookworks machine or Drive Wheel a Create shaft turns (through
 * mixin.compat.create.MachineGogglesMixin): the shaft's speed, the handle's turns a second, the stress it puts on the
 * shaft - in Create's goggle style. Nothing on one no shaft turns.
 */
public final class CreateGoggles {
    private CreateGoggles() {}

    /** What the goggles read off a machine or wheel a Create shaft turns: its speed, its stress per RPM, a wheel's line's machines (-1 for a machine). */
    public record Facts(float rpm, float impactPerRpm, int machines) {}

    /** The goggles' facts for {@code be}, or null when no Create shaft turns it (either side: no client class is touched). */
    public static @Nullable Facts facts(BlockEntity be) {
        Level level = be.getLevel();
        if (level == null) {
            return null;
        }
        if (be instanceof DriveWheelBlockEntity wheel) {
            if (CreateDrives.motorOn(level, DriveNetwork.wheels(level, wheel))) {
                return null; // a Motor's line: the Motor turns it, and Create's shafts at its ends with it
            }
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                Direction side = Direction.fromAxisAndDirection(wheel.axis(), way);
                CreateDrives.Reach reach = CreateDrives.reachAt(level, be.getBlockPos().relative(side), side);
                if (reach != null) {
                    List<BlockEntity> machines = DriveNetwork.machines(level, DriveNetwork.wheels(level, wheel),
                            m -> !dev.nookworks.machine.ForeignDrives.relayPart(level, m.getBlockPos()));
                    float impact = 0.0F;
                    for (BlockEntity machine : machines) {
                        impact += CreateDrives.impactOf(machine);
                    }
                    // and what an add-on's parts at the wheels' axle ends weigh (a belt run on a drum)
                    impact += CreateDrives.wheelEndLoads(level, DriveNetwork.wheels(level, wheel), CreateDrives.holder(reach.kinetic()).getBlockPos());
                    return new Facts(reach.speed(), impact, machines.size());
                }
            }
            return null;
        }
        CreateDrives.Reach reach = reachOf(be);
        return reach == null ? null : new Facts(reach.speed(), CreateDrives.impactOf(be), -1);
    }

    /** What the goggles read off a Motor driving Create's blocks: their speed, the su drawn of what it gives, its FE a tick. */
    public record MotorFacts(float rpm, float drawnSu, float capacitySu, float fe) {}

    /** The goggles' facts for a Motor, or null when no end of its meets a block of Create's. */
    public static @Nullable MotorFacts motorFacts(MotorBlockEntity motor) {
        if (motor.getLevel() == null) {
            return null;
        }
        List<KineticBlockEntity> driven = CreateEnds.drivenBy(motor.getLevel(), motor.getBlockPos());
        if (driven.isEmpty()) {
            return null;
        }
        float rpm = 0.0F;
        float drawn = 0.0F;
        float capacity = 0.0F;
        float fe = 0.0F;
        for (KineticBlockEntity kinetic : driven) {
            CreateEnds.State state = CreateEnds.state(kinetic);
            if (state != null && state.generating()) {
                rpm = Math.max(rpm, Math.abs(kinetic.getSpeed()));
                drawn += state.drawnSu();
                capacity += CreateEnds.givenSu(state);
                fe = Math.max(fe, state.motorFe());
            }
        }
        return new MotorFacts(rpm, drawn, capacity, fe);
    }

    /** The goggles' lines (on the client, where Create's goggles are drawn). */
    public static boolean add(BlockEntity be, List<Component> tooltip) {
        if (be instanceof MotorBlockEntity motor) {
            MotorFacts facts = motorFacts(motor);
            if (facts == null) {
                return false;
            }
            lang().translate("create.goggles.motor_turns", format(facts.rpm())).forGoggles(tooltip);
            lang().translate("create.goggles.motor_gives", format(facts.drawnSu()), format(facts.capacitySu()))
                    .style(ChatFormatting.AQUA).forGoggles(tooltip, 1);
            lang().translate("create.goggles.motor_draws", format(facts.fe())).style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
            return true;
        }
        boolean passes = false;
        if (be instanceof DriveWheelBlockEntity wheel && be.getLevel() != null) {
            // its line as a train: standing between Create's shafts that turn it against each other, or passing their turning on
            CreateDrives.WheelTrain train = CreateDrives.train(be.getLevel(), wheel);
            if (!train.motor() && !train.agree()) {
                lang().translate("create.goggles.stalled").style(ChatFormatting.GOLD).forGoggles(tooltip);
                return true;
            }
            passes = !train.motor() && train.ends() >= 2;
        }
        Facts facts = facts(be);
        if (facts == null) {
            return false;
        }
        lines(tooltip, facts.rpm(), facts.impactPerRpm(), facts.machines(), passes);
        return true;
    }

    /** The lines: {@code machines} below 0 for a machine itself, else a wheel's line's count; {@code passes} for a wheel line joining Create's shafts. */
    private static void lines(List<Component> tooltip, float rpm, float impactPerRpm, int machines, boolean passes) {
        lang().translate(passes && rpm != 0.0F ? "create.goggles.passes" : "create.goggles.driven", format(Math.abs(rpm))).forGoggles(tooltip);
        if (machines >= 0) {
            lang().translate("create.goggles.machines", machines).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        if (rpm != 0.0F && machines != 0) {
            lang().translate("create.goggles.turns", format(CreateDrives.turnsPerTick(rpm) * 20.0F)).style(ChatFormatting.GRAY).forGoggles(tooltip, 1);
        }
        lang().translate("create.goggles.stress", format(impactPerRpm * Math.abs(rpm)), format(impactPerRpm))
                .style(ChatFormatting.GOLD).forGoggles(tooltip, 1);
    }

    private static String format(float value) {
        return value == Math.rint(value) ? String.valueOf((int) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static LangBuilder lang() {
        return new LangBuilder(Nookworks.MODID);
    }

    /** The Create block entity at {@code be}'s drive side, if one turns it there (straight, or through an add-on's line). */
    static @Nullable KineticBlockEntity driveOf(BlockEntity be) {
        CreateDrives.Reach reach = reachOf(be);
        return reach == null ? null : reach.kinetic();
    }

    static CreateDrives.@Nullable Reach reachOf(BlockEntity be) {
        Direction side = be.getBlockState().getOptionalValue(Drive.PROPERTY).map(Drive::side).orElse(null);
        return side == null || be.getLevel() == null ? null : CreateDrives.reachAt(be.getLevel(), be.getBlockPos().relative(side), side);
    }
}
