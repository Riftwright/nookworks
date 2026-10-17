package dev.nookworks.barter;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.DuctBlock;
import dev.nookworks.ventilation.VentFanBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * The room a Barter Terminal keeps: found from the terminal along its Ducts (or straight behind it) to the Ventilation
 * Fan set in the room's wall, and from the fan's guard into the room. A room is a cuboid of open space, 1x1 to 4x4 and
 * 2 to 4 high inside, closed all round - glass, any block, an iron door - over a floor of the Nether's own ground
 * ({@link #FLOOR}), the fan one of its walls (the user, 2026-10-03).
 *
 * <p>The open space is measured from the block in front of the fan straight out along each axis, to the walls; then
 * every block inside must be open and every block round it closed. Where one is not, that block is the room's fault,
 * and the terminal shows it (the Nether's air seen leaking out there).
 */
public final class BarterRoom {
    /** What a room's floor is made of: the Nether's ground (#nookworks:nether_ground). */
    public static final TagKey<Block> FLOOR = TagKey.create(Registries.BLOCK, Nookworks.id("nether_ground"));
    public static final int MAX_SIDE = 4;
    public static final int MIN_HEIGHT = 2;
    public static final int MAX_HEIGHT = 4;
    /** Piglins a floor block takes (the user: humane, for Minecraft). */
    public static final int PIGLINS_PER_BLOCK = 3;
    /** How long a run of ducts from the terminal to the fan may be. */
    public static final int DUCT_LIMIT = 16;

    public enum Fault {
        NONE,
        /** No Ventilation Fan behind the terminal or at the end of its ducts. */
        NO_FAN,
        /** A gap in the walls, floor or roof: {@link Found#at()} is where. */
        OPEN,
        /** Too big, too small, too low, or not a clean cuboid inside: {@link Found#at()} is where it was found out. */
        SIZE,
        /** A block of the floor that is not the Nether's ground: {@link Found#at()}. */
        FLOOR,
        /** Not looked at yet: a room as saved, until the terminal sees it again after a reload. */
        UNSEEN
    }

    /**
     * What was found: the fault, if any, and where; the fan; the open space inside (null unless the room is whole);
     * how many floor blocks it has.
     */
    public record Found(Fault fault, @Nullable BlockPos at, @Nullable BlockPos fan, @Nullable BoundingBox inside) {
        public boolean whole() {
            return this.fault == Fault.NONE;
        }

        public int floor() {
            return this.inside == null ? 0 : this.inside.getXSpan() * this.inside.getZSpan();
        }

        /** How many piglins the room takes. */
        public int capacity() {
            return this.floor() * PIGLINS_PER_BLOCK;
        }

        /** The space inside as a box for finding the entities in it; empty when there is no room. */
        public AABB box() {
            return this.inside == null ? new AABB(BlockPos.ZERO) : AABB.of(this.inside);
        }

        static Found fault(Fault fault, @Nullable BlockPos at, @Nullable BlockPos fan) {
            return new Found(fault, at, fan, null);
        }
    }

    /** The room of the terminal at {@code terminal}. */
    public static Found find(Level level, BlockPos terminal) {
        BlockPos fan = fan(level, terminal);
        if (fan == null) {
            return Found.fault(Fault.NO_FAN, null, null);
        }
        BlockPos start = fan.relative(level.getBlockState(fan).getValue(VentFanBlock.FACING));
        if (!open(level, start)) {
            return Found.fault(Fault.SIZE, start, fan);
        }
        // the open space, measured along each axis from the block in front of the fan
        int[] lo = new int[3];
        int[] hi = new int[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            int limit = axis == Direction.Axis.Y ? MAX_HEIGHT : MAX_SIDE;
            int down = reach(level, start, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE), limit);
            int up = reach(level, start, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE), limit);
            if (down < 0 || up < 0 || down + up + 1 > limit) {
                // open further than a room goes: too big, or a wall missing along this line
                Direction way = down < 0 ? Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE)
                        : Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
                return Found.fault(Fault.SIZE, start.relative(way, limit), fan);
            }
            int i = axis.ordinal();
            lo[i] = start.get(axis) - down;
            hi[i] = start.get(axis) + up;
        }
        BoundingBox inside = new BoundingBox(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2]);
        if (inside.getYSpan() < MIN_HEIGHT) {
            return Found.fault(Fault.SIZE, new BlockPos(start.getX(), inside.maxY() + 1, start.getZ()), fan);
        }
        // everything inside open: a block standing in the room makes it no cuboid
        for (BlockPos pos : BlockPos.betweenClosed(lo[0], lo[1], lo[2], hi[0], hi[1], hi[2])) {
            if (!open(level, pos)) {
                return Found.fault(Fault.SIZE, pos.immutable(), fan);
            }
        }
        // everything round it closed, the floor the Nether's ground
        for (BlockPos pos : BlockPos.betweenClosed(lo[0] - 1, lo[1] - 1, lo[2] - 1, hi[0] + 1, hi[1] + 1, hi[2] + 1)) {
            int faces = (pos.getX() < lo[0] || pos.getX() > hi[0] ? 1 : 0) + (pos.getY() < lo[1] || pos.getY() > hi[1] ? 1 : 0)
                    + (pos.getZ() < lo[2] || pos.getZ() > hi[2] ? 1 : 0);
            if (faces != 1) {
                continue; // inside, or an edge or corner of the shell, which closes nothing off
            }
            if (open(level, pos)) {
                return Found.fault(Fault.OPEN, pos.immutable(), fan);
            }
            if (pos.getY() < lo[1] && !level.getBlockState(pos).is(FLOOR)) {
                return Found.fault(Fault.FLOOR, pos.immutable(), fan);
            }
        }
        return new Found(Fault.NONE, null, fan, inside);
    }

    /**
     * How many open blocks there are from {@code start} going {@code way} before a closed one, or -1 if there are
     * more than {@code limit}.
     */
    private static int reach(Level level, BlockPos start, Direction way, int limit) {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int n = 0; n <= limit; n++) {
            pos.move(way);
            if (!open(level, pos)) {
                return n;
            }
        }
        return -1;
    }

    /**
     * Whether a mob can go through the block at {@code pos}: nothing to bump into (air, a torch, water, an open fence
     * gate), a door or trapdoor standing open, or a door a piglin opens itself (a wooden or copper one: iron keeps them
     * in). A barrel's lid open while someone looks in is no gap.
     */
    static boolean open(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock && state.is(BlockTags.MOB_INTERACTABLE_DOORS)) {
            return true;
        }
        if ((state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
            return true;
        }
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The Ventilation Fan the terminal at {@code terminal} blows into: one whose back is against the terminal, or at
     * the end of the Ducts joined to it, at most {@link #DUCT_LIMIT} of them. The first one found when there are more.
     */
    public static @Nullable BlockPos fan(Level level, BlockPos terminal) {
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        seen.add(terminal);
        queue.add(terminal);
        while (!queue.isEmpty()) {
            BlockPos from = queue.poll();
            for (Direction side : from.equals(terminal) ? List.of(Direction.values()) : DuctBlock.connections(level.getBlockState(from))) {
                BlockPos next = from.relative(side);
                BlockState there = level.getBlockState(next);
                if (there.getBlock() instanceof VentFanBlock && there.getValue(VentFanBlock.FACING) == side) {
                    return next; // its back is against us: its guard faces on, away from where we are
                }
                if (there.getBlock() instanceof DuctBlock && seen.size() <= DUCT_LIMIT && seen.add(next)
                        && DuctBlock.connections(there).contains(side.getOpposite())) {
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private BarterRoom() {}
}
