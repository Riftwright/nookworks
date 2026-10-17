package dev.nookworks.drive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import dev.nookworks.machine.Crankable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The wheels a Motor's wheel reaches by its belts - and by sharing an axle, wheels set end to end on one line turning as
 * one - and the machines they turn: each a hand-worked machine on a wheel's
 * axle line, at either end, driven from that side. Walked afresh each time it is asked (a motor asks every tick): a
 * belt cut, a wheel broken, a machine set down are seen at once, and there is nothing kept to go stale. Only loaded
 * blocks are looked at.
 */
public final class DriveNetwork {
    private DriveNetwork() {}

    /** Wheels one motor's belts reach at most, its own counted. */
    public static final int MOST_WHEELS = 32;
    /** Machines one motor turns at most. */
    public static final int MOST_MACHINES = 8;

    /** {@code root} and every wheel joined to it by belts, nearest first. */
    public static List<DriveWheelBlockEntity> wheels(Level level, DriveWheelBlockEntity root) {
        List<DriveWheelBlockEntity> found = new ArrayList<>();
        Deque<DriveWheelBlockEntity> next = new ArrayDeque<>();
        found.add(root);
        next.add(root);
        while (!next.isEmpty() && found.size() < MOST_WHEELS) {
            DriveWheelBlockEntity wheel = next.poll();
            for (DriveWheelBlockEntity.Belt belt : wheel.belts()) {
                if (level.isLoaded(belt.other()) && level.getBlockEntity(belt.other()) instanceof DriveWheelBlockEntity far
                        && far.has(wheel.getBlockPos()) && !found.contains(far) && found.size() < MOST_WHEELS) {
                    found.add(far);
                    next.add(far);
                }
            }
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                BlockPos at = wheel.getBlockPos().relative(Direction.fromAxisAndDirection(wheel.axis(), way));
                if (level.isLoaded(at) && level.getBlockEntity(at) instanceof DriveWheelBlockEntity beside
                        && beside.axis() == wheel.axis() && !found.contains(beside) && found.size() < MOST_WHEELS) {
                    found.add(beside);
                    next.add(beside);
                }
            }
        }
        return found;
    }

    /** The machines {@code wheels} turn, at most {@link #MOST_MACHINES}, in the order the wheels were found. */
    public static List<BlockEntity> machines(Level level, List<DriveWheelBlockEntity> wheels) {
        List<BlockEntity> machines = new ArrayList<>();
        for (DriveWheelBlockEntity wheel : wheels) {
            for (Direction.AxisDirection way : Direction.AxisDirection.values()) {
                Direction toward = Direction.fromAxisAndDirection(wheel.axis(), way);
                BlockPos at = wheel.getBlockPos().relative(toward);
                if (level.isLoaded(at) && level.getBlockEntity(at) instanceof Crankable machine
                        && machine.drivenFrom(toward.getOpposite()) && !machines.contains((BlockEntity) machine)) {
                    machines.add((BlockEntity) machine);
                    if (machines.size() >= MOST_MACHINES) {
                        return machines;
                    }
                }
            }
        }
        return machines;
    }
}
