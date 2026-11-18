package dev.nookworks.port.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 26.3's {@code net.minecraft.world.phys.shapes.Shapes} as the mod uses it: vanilla 1.21.1's methods passed through,
 * plus 26.3's {@code rotateHorizontal} and {@code rotateAll}, which 1.21.1 lacks.
 *
 * <p>26.3 turns a shape by an {@code OctahedralGroup} about the block's centre; the turns used here are, for a point
 * (x, y, z) of the unit block: Y 90 (north to east) (1-z, y, x), Y 180 (1-x, y, 1-z), Y 270 (z, y, 1-x), X 270 (north
 * to up) (x, 1-z, y), X 90 (north to down) (x, z, 1-y) - read off 26.3's group elements BLOCK_ROT_Y_90 = ROT_90_Y_NEG
 * and so on (permutation and inverted axes) and its {@code Shapes.rotate}. Each box of the shape is turned and the
 * boxes joined again: the same space, though the boxes it is stored as may differ.
 */
public final class Shapes {
    private Shapes() {}

    public static VoxelShape empty() {
        return net.minecraft.world.phys.shapes.Shapes.empty();
    }

    public static VoxelShape block() {
        return net.minecraft.world.phys.shapes.Shapes.block();
    }

    public static VoxelShape box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return net.minecraft.world.phys.shapes.Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape create(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return net.minecraft.world.phys.shapes.Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static VoxelShape create(AABB aabb) {
        return net.minecraft.world.phys.shapes.Shapes.create(aabb);
    }

    public static VoxelShape or(VoxelShape first, VoxelShape second) {
        return net.minecraft.world.phys.shapes.Shapes.or(first, second);
    }

    public static VoxelShape or(VoxelShape first, VoxelShape... tail) {
        return net.minecraft.world.phys.shapes.Shapes.or(first, tail);
    }

    public static VoxelShape join(VoxelShape first, VoxelShape second, BooleanOp op) {
        return net.minecraft.world.phys.shapes.Shapes.join(first, second, op);
    }

    public static VoxelShape joinUnoptimized(VoxelShape first, VoxelShape second, BooleanOp op) {
        return net.minecraft.world.phys.shapes.Shapes.joinUnoptimized(first, second, op);
    }

    public static boolean joinIsNotEmpty(VoxelShape first, VoxelShape second, BooleanOp op) {
        return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(first, second, op);
    }

    /** 26.3's spelling of 1.21.1's {@code blockOccudes}. */
    public static boolean blockOccludes(VoxelShape shape, VoxelShape occluder, Direction direction) {
        return net.minecraft.world.phys.shapes.Shapes.blockOccudes(shape, occluder, direction);
    }

    /** 26.3: the shape given for north, turned to face each horizontal way. */
    public static Map<Direction, VoxelShape> rotateHorizontal(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, north);
        map.put(Direction.EAST, turn(north, Turn.Y_90));
        map.put(Direction.SOUTH, turn(north, Turn.Y_180));
        map.put(Direction.WEST, turn(north, Turn.Y_270));
        return map;
    }

    /** 26.3: the shape given for north, turned to face each of the six ways (up: north's front turned upwards). */
    public static Map<Direction, VoxelShape> rotateAll(VoxelShape north) {
        Map<Direction, VoxelShape> map = rotateHorizontal(north);
        map.put(Direction.UP, turn(north, Turn.X_270));
        map.put(Direction.DOWN, turn(north, Turn.X_90));
        return map;
    }

    private enum Turn {
        Y_90, Y_180, Y_270, X_270, X_90
    }

    private static VoxelShape turn(VoxelShape shape, Turn turn) {
        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty()) {
            return empty();
        }
        List<VoxelShape> turned = new ArrayList<>(boxes.size());
        for (AABB box : boxes) {
            turned.add(net.minecraft.world.phys.shapes.Shapes.create(turn(box, turn)));
        }
        VoxelShape first = turned.get(0);
        return turned.size() == 1 ? first : or(first, turned.subList(1, turned.size()).toArray(VoxelShape[]::new));
    }

    private static AABB turn(AABB box, Turn turn) {
        return switch (turn) {
            case Y_90 -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
            case Y_180 -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
            case Y_270 -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
            case X_270 -> new AABB(box.minX, 1 - box.maxZ, box.minY, box.maxX, 1 - box.minZ, box.maxY);
            case X_90 -> new AABB(box.minX, box.minZ, 1 - box.maxY, box.maxX, box.maxZ, 1 - box.minY);
        };
    }
}
