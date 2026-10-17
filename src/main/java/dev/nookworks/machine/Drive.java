package dev.nookworks.machine;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.MotorBlock;
import dev.nookworks.drive.DriveWheelBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * Which side of a hand-worked machine it is driven from, if any - in the world's directions, not the machine's: a Motor
 * whose shaft points into it, or a Drive Wheel on its axle's line. The machine's look shows it: an axle (or for the
 * Hand Quern, a coupling or a base plate) where the drive meets it, the handle off where the drive is in its way. A
 * machine is driven only from the sides it can be ({@link Crankable#drivenFrom}): a quern from above or below, the rest
 * from their sides.
 */
public enum Drive implements StringRepresentable {
    NONE("none", null),
    NORTH("north", Direction.NORTH),
    EAST("east", Direction.EAST),
    SOUTH("south", Direction.SOUTH),
    WEST("west", Direction.WEST),
    UP("up", Direction.UP),
    DOWN("down", Direction.DOWN);

    public static final EnumProperty<Drive> PROPERTY = EnumProperty.create("drive", Drive.class);

    private final String name;
    private final Direction side;

    Drive(String name, Direction side) {
        this.name = name;
        this.side = side;
    }

    /** The side the drive is on, or null for none. */
    public @Nullable Direction side() {
        return this.side;
    }

    /** The side a drive beside {@code pos} drives a machine there from, among its sides (vertical: the quern's). */
    public static Drive at(LevelReader level, BlockPos pos, boolean vertical) {
        for (Drive drive : values()) {
            if (drive.side != null && drive.side.getAxis().isVertical() == vertical && drives(level.getBlockState(pos.relative(drive.side)), drive.side)) {
                return drive;
            }
        }
        return NONE;
    }

    /** A machine driven from its sides. */
    public static Drive at(LevelReader level, BlockPos pos) {
        return at(level, pos, false);
    }

    /** Whether {@code next}, on a machine's {@code side}, drives it: a motor pointing in, a drive wheel on the line. */
    public static boolean drives(BlockState next, Direction side) {
        if (next.getBlock() instanceof MotorBlock) {
            return next.getValue(MotorBlock.FACING) == side.getOpposite();
        }
        return next.getBlock() instanceof DriveWheelBlock && next.getValue(DriveWheelBlock.FACING).getAxis() == side.getAxis();
    }

    @Override
    public String getSerializedName() {
        return this.name;
    }
}
