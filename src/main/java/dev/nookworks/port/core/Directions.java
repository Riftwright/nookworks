package dev.nookworks.port.core;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;

import net.minecraft.core.Direction;

/**
 * 26.3's {@code Direction.LEGACY_ID_CODEC_2D}, which 1.21.1 lacks: a horizontal direction saved as its 2D data value
 * in a byte (south 0, west 1, north 2, east 3), the way hanging entities keep their facing.
 */
public final class Directions {
    private Directions() {}

    public static final Codec<Direction> LEGACY_ID_CODEC_2D =
            Codec.BYTE.xmap(Direction::from2DDataValue, direction -> (byte) direction.get2DDataValue());

    /**
     * 26.3's {@code Direction.getNearest(int, int, int, orElse)}: the axis the vector leans along most, or {@code orElse}
     * when no one axis is longest (a tie, or no vector). 1.21.1's float {@code getNearest} settles a tie by the order of
     * the directions instead.
     */
    public static @Nullable Direction getNearest(int x, int y, int z, @Nullable Direction orElse) {
        int absX = Math.abs(x);
        int absY = Math.abs(y);
        int absZ = Math.abs(z);
        if (absX > absZ && absX > absY) {
            return x < 0 ? Direction.WEST : Direction.EAST;
        } else if (absZ > absX && absZ > absY) {
            return z < 0 ? Direction.NORTH : Direction.SOUTH;
        } else if (absY > absX && absY > absZ) {
            return y < 0 ? Direction.DOWN : Direction.UP;
        }
        return orElse;
    }
}
