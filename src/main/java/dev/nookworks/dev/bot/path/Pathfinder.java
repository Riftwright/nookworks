package dev.nookworks.dev.bot.path;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

import org.jspecify.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A* over the cells a player can be in, with every way a player gets about: walking, stepping up slabs and stairs,
 * jumping up a block, dropping down (safely, or into water from any height), swimming, climbing ladders and vines,
 * running jumps over gaps, opening doors, and - when its {@link Abilities} allow - tunnelling through blocks, bridging
 * gaps and pillaring up with blocks of its own. Costs are in ticks.
 * <p>
 * A search is run a slice at a time ({@link #run}), so a long one never holds up a tick for long. When the goal is out of
 * reach - not loaded yet, or too far for one search - it ends with the best partial path (the cell nearest the goal), and
 * the walker plans again from there.
 */
public final class Pathfinder {
    public static final double WALK = 4.633;
    public static final double SPRINT = 3.564;
    static final double JUMP = 2.5;
    static final double SWIM = 8.0;
    static final double SWIM_UP = 5.0;
    static final double SWIM_DOWN = 4.0;
    static final double CLIMB_UP = 8.5;
    static final double CLIMB_DOWN = 6.0;
    static final double PLACE = 12.0;
    /** The most cells in a row with the head under water: a breath's worth, with room to spare. */
    public static final int MAX_UNDERWATER = 8;
    /** A pillar's jump and the block set down at its top, over and above a plain jump. */
    static final double PILLAR = 14.0;
    static final double DOOR = 8.0;
    static final double UNDERWATER = 7.0;
    static final double FALL_PER_BLOCK = 1.2;
    static final double HURT_PER_BLOCK = 40.0;
    /** Weight on the estimate: a little greedy, for speed, at a small cost in path length. */
    static final double GREED = 1.25;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    public enum Status { SEARCHING, FOUND, PARTIAL, FAILED }

    private final PathWorld world;
    private final Goal goal;
    private final Long2ObjectOpenHashMap<Node> nodes = new Long2ObjectOpenHashMap<>();
    private final PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.f, b.f));
    private final Node start;
    private Node best;
    private Status status = Status.SEARCHING;
    private @Nullable List<Step> path;
    private int expanded;

    public Pathfinder(PathWorld world, BlockPos from, Goal goal) {
        this.world = world;
        this.goal = goal;
        PathWorld.Kind kind = world.kind(from.getX(), from.getY(), from.getZ(), false);
        if (kind == PathWorld.Kind.NONE) {
            // standing somewhere the planner would not (mid-air, half in a block): plan as if on the ground here
            kind = PathWorld.Kind.GROUND;
        }
        double feet = kind == PathWorld.Kind.GROUND ? world.standY(from.getX(), from.getY(), from.getZ(), false) : from.getY();
        this.start = new Node(from.getX(), from.getY(), from.getZ(), kind, Double.isNaN(feet) ? from.getY() : feet, false);
        this.start.g = 0.0;
        this.start.h = goal.estimate(from.getX(), from.getY(), from.getZ());
        this.start.f = this.start.h * GREED;
        this.start.move = Move.START;
        this.nodes.put(this.start.key(), this.start);
        this.open.add(this.start);
        this.best = this.start;
    }

    /** Searches for up to {@code nanos}; the status after. */
    public Status run(long nanos) {
        long until = System.nanoTime() + nanos;
        int sinceClock = 0;
        while (this.status == Status.SEARCHING) {
            Node n = this.open.poll();
            if (n == null || this.expanded >= this.world.ab.maxNodes) {
                this.finish(false);
                break;
            }
            if (n.closed) {
                continue;
            }
            n.closed = true;
            this.expanded++;
            if (this.goal.reached(n.x, n.y, n.z)) {
                this.best = n;
                this.finish(true);
                break;
            }
            if (n.h < this.best.h || n.h == this.best.h && n.g < this.best.g) {
                this.best = n;
            }
            this.expand(n);
            if (++sinceClock >= 64) {
                sinceClock = 0;
                if (System.nanoTime() > until) {
                    break;
                }
            }
        }
        return this.status;
    }

    /** Ends a search that has run long enough: the best partial path so far (PARTIAL), if it gets any nearer. A long
     *  trip (130 blocks out of a cave, 2026-10-21) ran out of planning time and failed, where legs of it went fine. */
    public Status stop() {
        if (this.status == Status.SEARCHING) {
            this.finish(false);
        }
        return this.status;
    }

    /** Runs to the end, however long that takes (tests). */
    public Status runToEnd() {
        while (this.run(1_000_000_000L) == Status.SEARCHING) {
            // on
        }
        return this.status;
    }

    public Status status() {
        return this.status;
    }

    public int expanded() {
        return this.expanded;
    }

    /** The path found (to the goal, or the best partial one), start excluded; empty if none. */
    public List<Step> path() {
        return this.path == null ? List.of() : this.path;
    }

    private void finish(boolean found) {
        if (found) {
            this.status = Status.FOUND;
        } else if (this.best != this.start && this.best.h < this.start.h - 0.5) {
            this.status = Status.PARTIAL;
        } else {
            this.status = Status.FAILED;
        }
        List<Step> steps = new ArrayList<>();
        if (this.status != Status.FAILED) {
            for (Node n = this.best; n != null && n.parent != null; n = n.parent) {
                steps.add(new Step(new BlockPos(n.x, n.y, n.z), n.move, n.feet, n.breaks, n.place, n.against, n.face, n.stepCost));
            }
            Collections.reverse(steps);
        }
        this.path = steps;
    }

    // ---- moves

    private void expand(Node n) {
        PathWorld w = this.world;
        int x = n.x;
        int y = n.y;
        int z = n.z;
        double feet = n.feet;
        boolean onGround = n.kind == PathWorld.Kind.GROUND;
        boolean inWater = n.kind == PathWorld.Kind.WATER;
        boolean fullFloor = onGround && Math.abs(feet - y) < 1e-3; // stood on a full block: room to bridge or pillar

        for (int[] d : DIRS) {
            int dx = d[0];
            int dz = d[1];
            boolean straight = dx == 0 || dz == 0;
            int nx = x + dx;
            int nz = z + dz;
            if (!straight && !(w.bodyFits(x + dx, z, feet) && w.bodyFits(x, z + dz, feet))) {
                continue; // no cutting corners past a block
            }
            double flat = straight ? 1.0 : Math.sqrt(2.0);

            // the same level, or one up: walk, step, jump
            boolean levelDone = false;
            for (int ny = y; ny <= y + 1; ny++) {
                PathWorld.Kind kind = w.kind(nx, ny, nz, false);
                if (kind == PathWorld.Kind.NONE) {
                    continue;
                }
                double to = w.feet(nx, ny, nz, kind, false);
                double rise = to - feet;
                if (rise <= PathWorld.STEP + 1e-3 && rise >= -0.5) {
                    double cost = (kind == PathWorld.Kind.WATER ? SWIM : WALK) * flat * this.slowness(nx, ny, nz, kind)
                            + this.doorCost(nx, ny, nz);
                    this.offer(n, nx, ny, nz, kind, to, false, kind == PathWorld.Kind.WATER || inWater ? Move.SWIM : Move.WALK,
                            cost, List.of(), null, null, null);
                    levelDone = true;
                } else if (rise > PathWorld.STEP && rise <= PathWorld.JUMP + 1e-3 && straight
                        && w.bodyFits(x, z, feet, PathWorld.HEIGHT + rise + 0.05)) {
                    this.offer(n, nx, ny, nz, kind, to, false, Move.ASCEND,
                            WALK * this.slowness(nx, ny, nz, kind) + JUMP + this.doorCost(nx, ny, nz), List.of(), null, null, null);
                    levelDone = true;
                }
            }
            if (levelDone) {
                continue;
            }

            // off the edge: drop to the first floor below (any height into water)
            if (w.bodyFits(nx, nz, feet) && w.kind(nx, y, nz, false) == PathWorld.Kind.NONE) {
                if (this.drop(n, nx, nz, flat)) {
                    // a drop found; a bridge or a jump over may still be better, so go on
                }
                if (straight && onGround && !inWater) {
                    this.bridge(n, dx, dz, fullFloor);
                    this.parkour(n, dx, dz, fullFloor);
                }
                continue;
            }

            // a wall: tunnel through it, or up a step into it
            if (straight && w.ab.canBreak && !inWater) {
                this.dig(n, dx, dz);
            }
        }

        // straight up and down
        if (inWater) {
            PathWorld.Kind up = w.kind(x, y + 1, z, false);
            if (up != PathWorld.Kind.NONE) {
                this.offer(n, x, y + 1, z, up, w.feet(x, y + 1, z, up, false), false, Move.SWIM, SWIM_UP, List.of(), null, null, null);
            }
            PathWorld.Kind down = w.kind(x, y - 1, z, false);
            if (down != PathWorld.Kind.NONE) {
                this.offer(n, x, y - 1, z, down, w.feet(x, y - 1, z, down, false), false, Move.SWIM, SWIM_DOWN, List.of(), null, null, null);
            }
        }
        BlockState here = w.state(x, y, z);
        if (PathWorld.climbable(here)) {
            PathWorld.Kind up = w.kind(x, y + 1, z, false);
            if (up == PathWorld.Kind.CLIMB || up == PathWorld.Kind.GROUND && PathWorld.climbable(w.state(x, y + 1, z))) {
                this.offer(n, x, y + 1, z, up, w.feet(x, y + 1, z, up, false), false, Move.CLIMB, CLIMB_UP, List.of(), null, null, null);
            }
        }
        PathWorld.Kind below = w.kind(x, y - 1, z, false);
        if (below == PathWorld.Kind.CLIMB) {
            this.offer(n, x, y - 1, z, below, y - 1, false, Move.CLIMB, CLIMB_DOWN, List.of(), null, null, null);
        }
        if (onGround && fullFloor && w.ab.placeBlocks > n.placed) {
            this.pillar(n);
        }
        if (onGround && w.ab.canBreak) {
            this.digDown(n);
        }
    }

    private double slowness(int x, int y, int z, PathWorld.Kind kind) {
        return kind == PathWorld.Kind.GROUND ? PathWorld.slowness(this.world.state(x, y, z), this.world.state(x, y - 1, z)) : 1.0;
    }

    private double doorCost(int x, int y, int z) {
        return PathWorld.openable(this.world.state(x, y, z)) || PathWorld.openable(this.world.state(x, y + 1, z)) ? DOOR : 0.0;
    }

    /** Off the edge into column nx, nz: down to the first place to be, if the fall is survivable. */
    private boolean drop(Node n, int nx, int nz, double flat) {
        PathWorld w = this.world;
        double feet = n.feet;
        for (int ny = n.y - 1; ny >= n.y - 64; ny--) {
            if (!w.known(nx, ny, nz)) {
                return false;
            }
            BlockState cell = w.state(nx, ny, nz);
            if (PathWorld.hazard(cell)) {
                return false;
            }
            PathWorld.Kind kind = w.kind(nx, ny, nz, false);
            if (kind != PathWorld.Kind.NONE) {
                double to = w.feet(nx, ny, nz, kind, false);
                double fall = feet - to;
                double cost = WALK * flat + fall * FALL_PER_BLOCK;
                boolean soft = PathWorld.water(cell); // landing in water, even a shallow pool, takes no damage
                if (kind != PathWorld.Kind.WATER && kind != PathWorld.Kind.CLIMB && !soft) {
                    if (fall > w.ab.maxFall + 1e-3) {
                        return false;
                    }
                    if (fall > w.ab.safeFall + 1e-3) {
                        cost += (fall - w.ab.safeFall) * HURT_PER_BLOCK;
                    }
                }
                this.offer(n, nx, ny, nz, kind, to, false, Move.DESCEND, cost, List.of(), null, null, null);
                return true;
            }
            if (!w.passable(nx, ny, nz, ny, ny + 1)) {
                return false; // something in the way of the fall that is not a floor (a fence's post, say)
            }
        }
        return false;
    }

    /** A block set down in the gap ahead, against the side of the floor: across a gap one block at a time. */
    private void bridge(Node n, int dx, int dz, boolean fullFloor) {
        PathWorld w = this.world;
        if (!fullFloor || w.ab.placeBlocks <= n.placed) {
            return;
        }
        int nx = n.x + dx;
        int nz = n.z + dz;
        int fy = n.y - 1;
        if (!w.replaceable(nx, fy, nz) || !w.bodyFits(nx, nz, n.y)) {
            return;
        }
        // set against the floor the walker stands on (or a block it set down itself)
        boolean against = n.virtualFloor || w.sturdy(n.x, fy, n.z, Direction.getNearest(dx, 0, dz, Direction.NORTH));
        if (!against) {
            return;
        }
        Direction face = Direction.getNearest(dx, 0, dz, Direction.NORTH);
        this.offer(n, nx, n.y, nz, PathWorld.Kind.GROUND, n.y, true, Move.WALK, WALK * 3.0 + PLACE, List.of(),
                new BlockPos(nx, fy, nz), new BlockPos(n.x, fy, n.z), face);
    }

    /** A running jump over a gap of one to three blocks, landing level or a block lower. */
    private void parkour(Node n, int dx, int dz, boolean fullFloor) {
        PathWorld w = this.world;
        if (!w.ab.parkour || !fullFloor || n.virtualFloor) {
            return;
        }
        int x = n.x;
        int y = n.y;
        int z = n.z;
        if (!w.bodyFits(x, z, y, PathWorld.HEIGHT + PathWorld.JUMP)) {
            return; // no room over the head for the jump
        }
        for (int gap = 1; gap <= 3; gap++) {
            int gx = x + dx * gap;
            int gz = z + dz * gap;
            // the gap's column must be open to the jump's height
            if (!w.bodyFits(gx, gz, y, PathWorld.HEIGHT + PathWorld.JUMP)) {
                return;
            }
            if (w.kind(gx, y, gz, false) != PathWorld.Kind.NONE) {
                return; // a floor in the gap: walk instead
            }
            for (int below = y - 1; below >= y - 4; below--) {
                if (PathWorld.lava(w.state(gx, below, gz))) {
                    return; // no jumping over lava: a slip is death
                }
            }
            if (gap >= 2 && !w.ab.canSprint) {
                return;
            }
            if (gap == 3) {
                // the long jump needs a run-up behind
                if (w.kind(x - dx, y, z - dz, false) != PathWorld.Kind.GROUND
                        || Math.abs(w.standY(x - dx, y, z - dz, false) - y) > 1e-3) {
                    return;
                }
            }
            int lx = x + dx * (gap + 1);
            int lz = z + dz * (gap + 1);
            for (int ly = y; ly >= y - 1; ly--) {
                if (w.kind(lx, ly, lz, false) == PathWorld.Kind.GROUND) {
                    double to = w.standY(lx, ly, lz, false);
                    if (to <= y + 1e-3 && to >= y - 1.0 - 1e-3 && w.bodyFits(lx, lz, Math.max(to, y), PathWorld.HEIGHT + 0.6)) {
                        double cost = (gap + 1) * SPRINT + 6.0 + gap * 4.0 + w.danger(lx, ly, lz);
                        this.offer(n, lx, ly, lz, PathWorld.Kind.GROUND, to, false, Move.PARKOUR, cost, List.of(), null, null, null);
                        return;
                    }
                }
            }
        }
    }

    /** Through a wall ahead: the cells broken, on the level or a step up into it. */
    private void dig(Node n, int dx, int dz) {
        int nx = n.x + dx;
        int nz = n.z + dz;
        // level: break the body's two cells ahead; a floor must be there after
        this.tunnel(n, nx, n.y, nz, false);
        // up a step: the head room above and the two cells a block up
        this.tunnel(n, nx, n.y + 1, nz, true);
    }

    private void tunnel(Node n, int nx, int ny, int nz, boolean up) {
        PathWorld w = this.world;
        if (!w.known(nx, ny - 1, nz)) {
            return;
        }
        BlockState floor = w.state(nx, ny - 1, nz);
        if (floor.getCollisionShape(w.level, new BlockPos(nx, ny - 1, nz)).isEmpty() || PathWorld.hazardFloor(floor)
                || PathWorld.openable(floor)) {
            return; // nothing to stand on after
        }
        List<BlockPos> breaks = new ArrayList<>(3);
        double cost = 0.0;
        if (up && !w.passable(n.x, n.y + 2, n.z, n.y + 2, n.y + 3)) {
            double c = w.breakCost(n.x, n.y + 2, n.z);
            if (!Double.isFinite(c)) {
                return;
            }
            breaks.add(new BlockPos(n.x, n.y + 2, n.z));
            cost += c;
        }
        for (int cy = ny; cy <= ny + 1; cy++) {
            if (w.passable(nx, cy, nz, cy, cy + 1) || PathWorld.openable(w.state(nx, cy, nz))) {
                continue;
            }
            double c = w.breakCost(nx, cy, nz);
            if (!Double.isFinite(c)) {
                return;
            }
            breaks.add(new BlockPos(nx, cy, nz));
            cost += c;
        }
        if (breaks.isEmpty()) {
            return; // nothing to break: a plain walk covers it (or it was not possible anyway)
        }
        double to = ny;
        if (PathWorld.hazard(w.state(nx, ny, nz)) || PathWorld.hazard(w.state(nx, ny + 1, nz))) {
            return;
        }
        cost += (up ? WALK + JUMP : WALK) + w.danger(nx, ny, nz);
        this.offer(n, nx, ny, nz, PathWorld.Kind.GROUND, to, false, up ? Move.ASCEND : Move.WALK, cost, breaks, null, null, null);
    }

    /** Straight up: jump, and set a block down where the feet were. */
    private void pillar(Node n) {
        PathWorld w = this.world;
        int x = n.x;
        int y = n.y;
        int z = n.z;
        if (!w.replaceable(x, y, z)) {
            return;
        }
        List<BlockPos> breaks = List.of();
        double cost = WALK + JUMP + PLACE + PILLAR;
        if (!w.passable(x, y + 2, z, y + 2, y + 3)) {
            double c = w.breakCost(x, y + 2, z);
            if (!Double.isFinite(c)) {
                return;
            }
            breaks = List.of(new BlockPos(x, y + 2, z));
            cost += c;
        }
        if (!w.known(x, y + 2, z) || PathWorld.hazard(w.state(x, y + 2, z))) {
            return;
        }
        this.offer(n, x, y + 1, z, PathWorld.Kind.GROUND, y + 1, true, Move.PILLAR, cost + w.danger(x, y + 1, z), breaks,
                new BlockPos(x, y, z), new BlockPos(x, y - 1, z), Direction.UP);
    }

    /** Straight down: break the floor, and fall to the next (never into lava, never far). */
    private void digDown(Node n) {
        PathWorld w = this.world;
        int x = n.x;
        int y = n.y;
        int z = n.z;
        if (n.virtualFloor) {
            return;
        }
        double c = w.breakCost(x, y - 1, z);
        if (!Double.isFinite(c)) {
            return;
        }
        List<BlockPos> broken = List.of(new BlockPos(x, y - 1, z));
        // down the hole to the first floor (or water), never further than a safe fall
        for (int cy = y - 2; cy >= y - 2 - (int) Math.floor(w.ab.safeFall); cy--) {
            if (!w.known(x, cy, z)) {
                return;
            }
            BlockState cell = w.state(x, cy, z);
            if (PathWorld.hazard(cell)) {
                return;
            }
            if (PathWorld.water(cell) && cell.getCollisionShape(w.level, new BlockPos(x, cy, z)).isEmpty()) {
                this.offer(n, x, cy, z, PathWorld.Kind.WATER, cy, false, Move.DESCEND, c + (y - cy) * FALL_PER_BLOCK + 4.0,
                        broken, null, null, null);
                return;
            }
            var shape = cell.getCollisionShape(w.level, new BlockPos(x, cy, z));
            if (!shape.isEmpty()) {
                if (PathWorld.hazardFloor(cell) || PathWorld.openable(cell)) {
                    return;
                }
                double top = shape.max(Direction.Axis.Y);
                boolean inside = shape.min(Direction.Axis.Y) <= 1e-3 && top <= PathWorld.STEP;
                int cellY = inside ? cy : cy + 1;
                double to = cy + top;
                if (y - to > w.ab.safeFall + 1e-3 || !inside && top <= PathWorld.STEP) {
                    return;
                }
                this.offer(n, x, cellY, z, PathWorld.Kind.GROUND, to, false, Move.DESCEND,
                        c + (y - to) * FALL_PER_BLOCK + 4.0, broken, null, null, null);
                return;
            }
        }
    }

    private void offer(Node from, int x, int y, int z, PathWorld.Kind kind, double feet, boolean virtualFloor, Move move,
            double cost, List<BlockPos> breaks, @Nullable BlockPos place, @Nullable BlockPos against, @Nullable Direction face) {
        double g = from.g + cost + this.world.danger(x, y, z);
        boolean under = PathWorld.water(this.world.state(x, y + 1, z));
        if (under) {
            g += UNDERWATER; // a swimmer floats up: under the surface is slow, and air runs out
            if (from.underwater + 1 > this.world.ab.maxUnderwater) {
                return; // no longer a stretch with the head under: it drowned in a flooded tunnel (2026-10-22)
            }
        }
        long key = BlockPos.asLong(x, y, z);
        Node node = this.nodes.get(key);
        if (node == null) {
            node = new Node(x, y, z, kind, feet, virtualFloor);
            node.h = this.goal.estimate(x, y, z);
            this.nodes.put(key, node);
        } else if (node.closed || g >= node.g - 1e-6) {
            return;
        }
        node.kind = kind;
        node.feet = feet;
        node.virtualFloor = virtualFloor;
        node.g = g;
        node.f = g + node.h * GREED;
        node.parent = from;
        node.move = move;
        node.breaks = breaks;
        node.place = place;
        node.against = against;
        node.face = face;
        node.stepCost = cost;
        node.placed = from.placed + (place != null ? 1 : 0);
        node.underwater = under ? from.underwater + 1 : 0;
        this.open.add(node);
    }

    private static final class Node {
        final int x;
        final int y;
        final int z;
        PathWorld.Kind kind;
        double feet;
        boolean virtualFloor;
        double g = Double.MAX_VALUE;
        double h;
        double f;
        boolean closed;
        @Nullable Node parent;
        Move move = Move.WALK;
        List<BlockPos> breaks = List.of();
        @Nullable BlockPos place;
        @Nullable BlockPos against;
        @Nullable Direction face;
        double stepCost;
        int placed;
        /** Cells in a row so far with the head under water. */
        int underwater;

        Node(int x, int y, int z, PathWorld.Kind kind, double feet, boolean virtualFloor) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.feet = feet;
            this.virtualFloor = virtualFloor;
        }

        long key() {
            return BlockPos.asLong(this.x, this.y, this.z);
        }
    }
}
