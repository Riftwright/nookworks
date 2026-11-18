package dev.nookworks.port.core;

import java.util.function.IntFunction;
import java.util.stream.IntStream;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The shape helpers 26.3 has as static methods on {@code Block} and 1.21.1 lacks ({@code Block.column},
 * {@code Block.boxZ}, {@code Block.cube}, {@code Block.boxes}); tx/core.py points {@code Block.column(...)} and the
 * others here. Sizes are in sixteenths, centred on the block as 26.3's are.
 */
public final class BlockShapes {
    private BlockShapes() {}

    /** One shape per index 0..endInclusive (a crop's shape per age). */
    public static VoxelShape[] boxes(int endInclusive, IntFunction<VoxelShape> factory) {
        return IntStream.rangeClosed(0, endInclusive).mapToObj(factory).toArray(VoxelShape[]::new);
    }

    public static VoxelShape cube(double size) {
        return cube(size, size, size);
    }

    public static VoxelShape cube(double sizeX, double sizeY, double sizeZ) {
        double halfY = sizeY / 2.0;
        return column(sizeX, sizeZ, 8.0 - halfY, 8.0 + halfY);
    }

    /** A post standing in the middle of the block: {@code sizeXZ} wide, from {@code minY} to {@code maxY}. */
    public static VoxelShape column(double sizeXZ, double minY, double maxY) {
        return column(sizeXZ, sizeXZ, minY, maxY);
    }

    public static VoxelShape column(double sizeX, double sizeZ, double minY, double maxY) {
        double halfX = sizeX / 2.0;
        double halfZ = sizeZ / 2.0;
        return Block.box(8.0 - halfX, minY, 8.0 - halfZ, 8.0 + halfX, maxY, 8.0 + halfZ);
    }

    /** A bar along z, centred in x and y. */
    public static VoxelShape boxZ(double sizeXY, double minZ, double maxZ) {
        return boxZ(sizeXY, sizeXY, minZ, maxZ);
    }

    public static VoxelShape boxZ(double sizeX, double sizeY, double minZ, double maxZ) {
        double halfY = sizeY / 2.0;
        return boxZ(sizeX, 8.0 - halfY, 8.0 + halfY, minZ, maxZ);
    }

    public static VoxelShape boxZ(double sizeX, double minY, double maxY, double minZ, double maxZ) {
        double halfX = sizeX / 2.0;
        return Block.box(8.0 - halfX, minY, minZ, 8.0 + halfX, maxY, maxZ);
    }
}
