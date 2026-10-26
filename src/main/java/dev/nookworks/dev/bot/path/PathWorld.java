package dev.nookworks.dev.bot.path;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The world as the planner sees it: which cells a player stands in, walks through, swims or climbs, which blocks are
 * dangerous, which may be broken or built on. Reads any {@link BlockGetter} - the client's level for the walker, a test's
 * server level for the GameTests - and keeps what it read for the length of one search.
 * <p>
 * A cell is where the player's feet are. Standing in it, the feet are at {@link #standY}: the cell's own height on a slab
 * or a carpet, the top of the block below on a full block (or above it, on a fence).
 */
public final class PathWorld {
    /** The highest step taken without a jump (vanilla's step height). */
    public static final double STEP = 0.6;
    /** The highest a jump climbs onto. */
    public static final double JUMP = 1.25;
    /** A player's height, standing. */
    public static final double HEIGHT = 1.8;

    public enum Kind { NONE, GROUND, WATER, CLIMB }

    final BlockGetter level;
    final Abilities ab;
    private final Long2ObjectOpenHashMap<BlockState> states = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public PathWorld(BlockGetter level, Abilities ab) {
        this.level = level;
        this.ab = ab;
    }

    public Abilities abilities() {
        return this.ab;
    }

    /** Whether the planner may look at this cell at all: loaded, in the build height, in the bounds. */
    public boolean known(int x, int y, int z) {
        if (this.ab.bounds != null && !this.ab.bounds.isInside(x, y, z)) {
            return false;
        }
        if (this.level instanceof LevelReader reader) {
            return !reader.isOutsideBuildHeight(y) && reader.hasChunkAt(x, z);
        }
        return true;
    }

    public BlockState state(int x, int y, int z) {
        long key = BlockPos.asLong(x, y, z);
        BlockState state = this.states.get(key);
        if (state == null) {
            state = this.known(x, y, z) ? this.level.getBlockState(this.cursor.set(x, y, z)) : Blocks.BEDROCK.defaultBlockState();
            this.states.put(key, state);
        }
        return state;
    }

    private VoxelShape shape(BlockState state, int x, int y, int z) {
        return state.getCollisionShape(this.level, this.cursor.set(x, y, z));
    }

    /** The player's footprint stood in the middle of a cell: 0.6 wide, with a little to spare. */
    private static final double FOOT_LO = 0.19;
    private static final double FOOT_HI = 0.81;

    /**
     * The low and high heights of the parts of the block's collision that a body stood in the middle of the cell would
     * touch, or null where none would: a ladder or a vine's thin back, a door's panel at the edge, are not in its way.
     */
    private double @Nullable [] body(BlockState state, int x, int y, int z) {
        VoxelShape shape = this.shape(state, x, y, z);
        if (shape.isEmpty()) {
            return null;
        }
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            // a panel at the edge is still in the way of walking through the cell's side
            return new double[] {shape.min(Direction.Axis.Y), shape.max(Direction.Axis.Y)};
        }
        double lo = Double.MAX_VALUE;
        double hi = -Double.MAX_VALUE;
        for (AABB box : shape.toAabbs()) {
            if (box.maxX > FOOT_LO && box.minX < FOOT_HI && box.maxZ > FOOT_LO && box.minZ < FOOT_HI) {
                lo = Math.min(lo, box.minY);
                hi = Math.max(hi, box.maxY);
            }
        }
        return lo == Double.MAX_VALUE ? null : new double[] {lo, hi};
    }

    // ---- what a block is, to the walker

    public static boolean lava(BlockState state) {
        return state.getFluidState().is(FluidTags.LAVA);
    }

    public static boolean water(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
    }

    /** Hurts or holds whoever stands in it. */
    public static boolean hazard(BlockState state) {
        return lava(state) || state.is(BlockTags.FIRE) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.CACTUS)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** Hurts whoever stands on it (magma without sneaking, a lit campfire, a cactus). */
    public static boolean hazardFloor(BlockState state) {
        return state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CACTUS) || state.is(Blocks.POINTED_DRIPSTONE)
                || state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT);
    }

    /** A door or a gate a hand opens: a wall to the planner's collision test, but a way through. */
    public static boolean openable(BlockState state) {
        return DoorBlock.isWoodenDoor(state) || state.getBlock() instanceof FenceGateBlock;
    }

    public static boolean climbable(BlockState state) {
        return state.is(BlockTags.CLIMBABLE) || state.getBlock() instanceof ScaffoldingBlock;
    }

    /** Slows whoever walks in or on it. */
    static double slowness(BlockState body, BlockState floor) {
        if (body.is(Blocks.COBWEB)) {
            return 12.0;
        }
        if (body.is(Blocks.SWEET_BERRY_BUSH)) {
            return 6.0;
        }
        if (floor.is(Blocks.SOUL_SAND) || floor.is(Blocks.HONEY_BLOCK)) {
            return 2.5;
        }
        if (floor.is(Blocks.FARMLAND)) {
            return 4.0; // round the field, not over it: a jump onto farmland tramples it
        }
        return 1.0;
    }

    // ---- cells

    /** Whether nothing in the cell stops a body passing through it between heights {@code from} and {@code to}
     *  (absolute): no collision there, no fire or lava; an openable door counts as open. */
    public boolean passable(int x, int y, int z, double from, double to) {
        BlockState state = this.state(x, y, z);
        if (hazard(state) && !state.is(Blocks.SWEET_BERRY_BUSH)) {
            return false;
        }
        if (openable(state)) {
            return true;
        }
        double[] body = this.body(state, x, y, z);
        if (body == null) {
            return true;
        }
        return y + body[1] <= from + 1e-3 || y + body[0] >= to - 1e-3;
    }

    /** Whether a body stood with its feet at {@code feet} in column x, z fits: every cell it reaches is passable. */
    public boolean bodyFits(int x, int z, double feet, double height) {
        int top = (int) Math.floor(feet + height - 1e-3);
        for (int y = (int) Math.floor(feet); y <= top; y++) {
            if (!this.known(x, y, z) || !this.passable(x, y, z, feet, feet + height)) {
                return false;
            }
        }
        return true;
    }

    public boolean bodyFits(int x, int z, double feet) {
        return this.bodyFits(x, z, feet, HEIGHT);
    }

    /** Where the feet stand in this cell, or NaN where nobody stands: on its own low block (a slab, a carpet), or on the
     *  block below it. {@code virtualFloor}: a block is to be set down under it on the way (a bridge, a pillar). */
    public double standY(int x, int y, int z, boolean virtualFloor) {
        if (!this.known(x, y, z)) {
            return Double.NaN;
        }
        BlockState cell = this.state(x, y, z);
        if (virtualFloor) {
            return this.body(cell, x, y, z) == null && !hazard(cell) ? y : Double.NaN;
        }
        double[] own = this.body(cell, x, y, z);
        if (own != null && !openable(cell)) {
            if (own[0] <= 1e-3 && own[1] <= STEP + 1e-3 && !hazardFloor(cell) && !hazard(cell)) {
                return y + own[1];
            }
            return Double.NaN;
        }
        if (!this.known(x, y - 1, z)) {
            return Double.NaN;
        }
        BlockState below = this.state(x, y - 1, z);
        if (openable(below) || hazardFloor(below) || lava(below)) {
            return Double.NaN;
        }
        double[] floor = this.body(below, x, y - 1, z);
        if (floor == null) {
            return Double.NaN;
        }
        double top = floor[1];
        return top > STEP + 1e-3 ? y - 1 + top : Double.NaN;
    }

    /** What sort of place a cell is to be in: stood in, swum in, climbed, or none. */
    public Kind kind(int x, int y, int z, boolean virtualFloor) {
        if (!this.known(x, y, z)) {
            return Kind.NONE;
        }
        BlockState cell = this.state(x, y, z);
        if (lava(cell)) {
            return Kind.NONE;
        }
        double stand = this.standY(x, y, z, virtualFloor);
        if (!Double.isNaN(stand)) {
            return this.bodyFits(x, z, stand) ? Kind.GROUND : Kind.NONE;
        }
        if (water(cell) && this.body(cell, x, y, z) == null) {
            // a swimmer's body is shorter than a stander's; the cell above need only be open
            return this.passable(x, y + 1, z, y + 1, y + 1.6) ? Kind.WATER : Kind.NONE;
        }
        if (climbable(cell) && (this.body(cell, x, y, z) == null || cell.getBlock() instanceof ScaffoldingBlock)) {
            BlockState above = this.state(x, y + 1, z);
            return climbable(above) || this.passable(x, y + 1, z, y + 1, y + 1.8) ? Kind.CLIMB : Kind.NONE;
        }
        return Kind.NONE;
    }

    /** Where the feet are taken to be in a cell of this kind. */
    public double feet(int x, int y, int z, Kind kind, boolean virtualFloor) {
        return kind == Kind.GROUND ? this.standY(x, y, z, virtualFloor) : y;
    }

    /** Whether the cell is empty enough for a block to be set down in it (air, water, grass, snow...). */
    public boolean replaceable(int x, int y, int z) {
        if (!this.known(x, y, z)) {
            return false;
        }
        BlockState state = this.state(x, y, z);
        return !lava(state) && (state.isAir() || state.canBeReplaced());
    }

    /** Whether the face of the block at x, y, z holds a block set against it. */
    public boolean sturdy(int x, int y, int z, Direction face) {
        return this.known(x, y, z) && this.state(x, y, z).isFaceSturdy(this.level, this.cursor.set(x, y, z), face);
    }

    /**
     * The ticks it takes to break the block safely, or infinity: not allowed (someone's build, a fluid, bedrock), or
     * lava waits behind it. A falling block resting on it (gravel, sand) comes down into the gap and is counted in.
     */
    public double breakCost(int x, int y, int z) {
        if (!this.ab.canBreak || !this.known(x, y, z)) {
            return Double.POSITIVE_INFINITY;
        }
        BlockState state = this.state(x, y, z);
        BlockPos pos = new BlockPos(x, y, z);
        if (state.isAir() || !state.getFluidState().isEmpty() && this.shape(state, x, y, z).isEmpty() || climbable(state)) {
            return Double.POSITIVE_INFINITY;
        }
        if (!this.ab.mayBreak.test(state, pos)) {
            return Double.POSITIVE_INFINITY;
        }
        double ticks = this.ab.breakTicks.applyAsDouble(state, pos);
        if (!Double.isFinite(ticks)) {
            return Double.POSITIVE_INFINITY;
        }
        double extra = 0.0;
        for (Direction d : Direction.values()) {
            BlockState next = this.state(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ());
            if (lava(next)) {
                return Double.POSITIVE_INFINITY;
            }
            if (water(next) && d != Direction.DOWN) {
                extra += 20.0; // the tunnel floods
            }
        }
        // sand or gravel over it: it comes down into the gap, onto whoever is under it (it suffocated digging in sand
        // over stone, 2026-10-22) - dear, so a way round or a way through rock is taken
        if (this.state(x, y + 1, z).getBlock() instanceof net.minecraft.world.level.block.FallingBlock) {
            extra += 60.0;
        }
        // gravel or sand stacked above falls into the gap, block after block
        for (int up = 1; up <= 6; up++) {
            BlockState above = this.state(x, y + up, z);
            if (!(above.getBlock() instanceof FallingBlock)) {
                break;
            }
            double more = this.ab.mayBreak.test(above, pos.above(up)) ? this.ab.breakTicks.applyAsDouble(above, pos.above(up))
                    : Double.POSITIVE_INFINITY;
            if (!Double.isFinite(more)) {
                return Double.POSITIVE_INFINITY;
            }
            extra += more + 4.0;
        }
        return ticks + 4.0 + extra;
    }

    /** The cost of keeping near danger: lava beside the body, the places the abilities say to keep away from. */
    public double danger(int x, int y, int z) {
        double cost = 0.0;
        for (int dy = -1; dy <= 2; dy++) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (lava(this.state(x + d.getStepX(), y + dy, z + d.getStepZ()))) {
                    cost += 30.0;
                }
            }
        }
        if (lava(this.state(x, y - 1, z))) {
            cost += 60.0;
        }
        // running water: its current pushes the body off each step (a cave's stream held it for minutes, going up out
        // of a cave, 2026-10-22) - dry ground, or a way dug, is better
        for (int dy = 0; dy <= 1; dy++) {
            var fluid = this.state(x, y + dy, z).getFluidState();
            if (fluid.is(FluidTags.WATER) && !fluid.isSource()) {
                // a waterfall most of all: going up one it never got anywhere
                cost += fluid.hasProperty(net.minecraft.world.level.material.FlowingFluid.FALLING)
                        && fluid.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING) ? 80.0 : 15.0;
            }
        }
        for (Abilities.Avoid avoid : this.ab.avoid) {
            double dx = x + 0.5 - avoid.center().x;
            double dy = y - avoid.center().y;
            double dz = z + 0.5 - avoid.center().z;
            if (dx * dx + dy * dy + dz * dz < avoid.radius() * avoid.radius()) {
                cost += avoid.penalty();
            }
        }
        return cost;
    }
}
