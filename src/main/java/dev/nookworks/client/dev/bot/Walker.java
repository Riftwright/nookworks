package dev.nookworks.client.dev.bot;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.dev.bot.path.Move;
import dev.nookworks.dev.bot.path.PathWorld;
import dev.nookworks.dev.bot.path.Pathfinder;
import dev.nookworks.dev.bot.path.Step;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Takes the walker along a path: plans it (a slice of the search each tick), then turns each step into keys and clicks -
 * walking and sprinting, jumping up and off, swimming, climbing, running jumps, blocks broken on the way with the right
 * tool, bridges set down from a sneak at the edge, pillars jumped up, doors opened. It watches itself as it goes: a path
 * the world has changed under, a fall off it, getting stuck - and plans again from where it is.
 */
final class Walker {
    enum State { IDLE, PLANNING, MOVING, DONE, FAILED }

    private static final long PLAN_NANOS = 12_000_000L;
    private static final int MAX_REPLANS = 12;
    /** Partial paths walked on one trip, each a leg nearer the goal: a long trip is many. */
    private static final int MAX_LEGS = 40;

    private State state = State.IDLE;
    private @Nullable Goal goal;
    private @Nullable Pathfinder search;
    private List<Step> path = List.of();
    private int index;
    /** The cell the current step starts from. */
    private BlockPos from = BlockPos.ZERO;
    private Acts.@Nullable Breaker breaker;
    private int breakIndex;
    private int phaseTicks;
    private boolean placed;
    private int stuck;
    private double bestDistance = Double.MAX_VALUE;
    private int replans;
    private int planTicks;
    private @Nullable String failure;
    private boolean mayBreak = true;
    /** A door or gate it opened, to close again once through (a person shuts the door behind them). */
    private @Nullable BlockPos openedDoor;
    private int closeTicks;
    /** Blocks it set down to get about (bridges, pillars), for tidying up after. */
    final List<BlockPos> scaffold = new ArrayList<>();

    State state() {
        return this.state;
    }

    @Nullable String failure() {
        return this.failure;
    }

    /** Steps left of the path (for the state file). */
    int remaining() {
        return Math.max(0, this.path.size() - this.index);
    }

    @Nullable Step current() {
        return this.index < this.path.size() ? this.path.get(this.index) : null;
    }

    List<Step> path() {
        return this.path;
    }

    /** Whether the walker is on the way to this very goal (not one another task gave it meanwhile). */
    boolean isFor(Goal goal) {
        return this.goal == goal;
    }

    void go(Goal goal) {
        this.go(goal, true);
    }

    /** {@code mayBreak}: whether blocks may be broken on the way at all (never, to pick up an item: no digging after
     *  every drop that rolls into a hole). */
    void go(Goal goal, boolean mayBreak) {
        this.mayBreak = mayBreak;
        this.goal = goal;
        this.replans = 0;
        this.legs = 0;
        this.failure = null;
        this.plan();
    }

    private int legs;

    void stop(Minecraft mc) {
        if (this.breaker != null && mc.gameMode != null) {
            mc.gameMode.stopDestroyBlock();
        }
        this.state = State.IDLE;
        this.search = null;
        this.path = List.of();
        this.breaker = null;
        this.goal = null;
    }

    private void plan() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        var ab = Bot.abilities(mc);
        ab.canBreak &= this.mayBreak;
        PathWorld world = new PathWorld(player.level(), ab);
        this.search = new Pathfinder(world, feetCell(player), this.goal);
        this.state = State.PLANNING;
        this.planTicks = 0;
    }

    private void fail(String why) {
        this.failure = why;
        this.state = State.FAILED;
        this.search = null;
    }

    static BlockPos feetCell(LocalPlayer player) {
        return BlockPos.containing(player.getX(), player.getY() + 0.01, player.getZ());
    }

    /** One tick of walking. */
    void tick(Minecraft mc, Controls c) {
        LocalPlayer player = mc.player;
        switch (this.state) {
            case IDLE, DONE, FAILED -> {
                return;
            }
            case PLANNING -> {
                c.stopMoving();
                this.planTicks++;
                Pathfinder.Status status = this.search.run(PLAN_NANOS);
                if (status == Pathfinder.Status.SEARCHING) {
                    if (this.planTicks <= 200) {
                        return;
                    }
                    // out of planning time: on along the best partial path, and plan the rest from its end
                    status = this.search.stop();
                    if (status != Pathfinder.Status.PARTIAL) {
                        this.fail("planning took too long");
                        return;
                    }
                    Bot.event("path", "long way: a leg of " + this.search.path().size() + " steps first");
                }
                if (status == Pathfinder.Status.FAILED) {
                    if (this.goal.reached(feetCell(player).getX(), feetCell(player).getY(), feetCell(player).getZ())) {
                        this.state = State.DONE;
                    } else {
                        this.fail("no way there (" + this.search.expanded() + " places tried)");
                    }
                    return;
                }
                this.path = new ArrayList<>(this.search.path());
                this.search = null;
                this.index = 0;
                this.from = feetCell(player);
                this.beginStep();
                if (this.path.isEmpty()) {
                    this.state = State.DONE;
                    return;
                }
                this.state = State.MOVING;
            }
            case MOVING -> this.move(mc, c);
        }
    }

    private void beginStep() {
        this.breaker = null;
        this.breakIndex = 0;
        this.phaseTicks = 0;
        this.placed = false;
        this.stuck = 0;
        this.bestDistance = Double.MAX_VALUE;
    }

    private void replan(String why) {
        if (++this.replans > MAX_REPLANS) {
            this.fail("kept losing the way: " + why);
            return;
        }
        Bot.event("replan", why);
        this.plan();
    }

    private void move(Minecraft mc, Controls c) {
        LocalPlayer player = mc.player;
        if (this.index >= this.path.size()) {
            BlockPos at = feetCell(player);
            if (this.goal.reached(at.getX(), at.getY(), at.getZ())) {
                c.stopMoving();
                this.state = State.DONE;
            } else if (++this.legs > MAX_LEGS) {
                this.fail("still not there after " + MAX_LEGS + " legs");
            } else {
                // a leg walked, nearer: not a way lost (those count to MAX_REPLANS)
                Bot.event("replan", "partial path walked; on toward the goal");
                this.plan();
            }
            return;
        }
        Step step = this.path.get(this.index);
        this.phaseTicks++;

        // fallen off the way, or carried off it (water, a push)
        BlockPos at = feetCell(player);
        if (at.distManhattan(step.to()) > 4 && at.distManhattan(this.from) > 4) {
            this.replan("off the path at " + at.toShortString());
            return;
        }
        // the world changed ahead: a block in the way that was not, a floor gone
        if (this.phaseTicks % 10 == 1 && !this.stillGood(player)) {
            this.replan("the way ahead changed");
            return;
        }

        // 1. blocks to break first
        while (this.breakIndex < step.breaks().size()) {
            if (this.breaker == null) {
                this.breaker = new Acts.Breaker(step.breaks().get(this.breakIndex));
            }
            if (!this.breaker.tick(mc, c)) {
                return;
            }
            if (this.breaker.failure() != null) {
                this.replan(this.breaker.failure());
                return;
            }
            this.breaker = null;
            this.breakIndex++;
        }

        Vec3 target = new Vec3(step.to().getX() + 0.5, step.standY(), step.to().getZ() + 0.5);
        double horizontal = Math.hypot(target.x - player.getX(), target.z - player.getZ());
        double distance = target.distanceTo(player.position());
        if (distance < this.bestDistance - 0.04) {
            this.bestDistance = distance;
            this.stuck = 0;
        } else if (++this.stuck > 70) {
            this.replan("stuck by " + step.to().toShortString());
            return;
        }

        c.stopMoving();
        if (this.closeDoor(mc, player, c)) {
            return;
        }
        switch (step.move()) {
            case PILLAR -> {
                if (this.pillar(mc, c, step)) {
                    this.advance(player);
                }
                return;
            }
            case CLIMB -> this.climb(player, c, step, target, horizontal);
            case SWIM -> this.swim(player, c, target, horizontal);
            case PARKOUR -> this.parkour(player, c, step, target);
            default -> {
                if (step.placesBlock() && !this.bridge(mc, c, step)) {
                    return;
                }
                this.walk(mc, player, c, step, target, horizontal);
            }
        }
        // a little nudge when it makes no headway: a jump frees it from most corners
        if (this.stuck > 25 && this.stuck % 15 == 0 && (player.onGround() || player.isInWater())) {
            c.jump = true;
        }
        if (this.arrived(player, step, target, horizontal)) {
            this.advance(player);
        }
    }

    private void advance(LocalPlayer player) {
        Step done = this.path.get(this.index);
        if (done.place() != null) {
            this.scaffold.add(done.place());
        }
        this.from = done.to();
        this.index++;
        this.beginStep();
    }

    /** Whether the next few steps can still be taken as planned. */
    private boolean stillGood(LocalPlayer player) {
        PathWorld world = new PathWorld(player.level(), Bot.abilities(Minecraft.getInstance()));
        for (int i = this.index; i < Math.min(this.path.size(), this.index + 4); i++) {
            Step s = this.path.get(i);
            if (!s.breaks().isEmpty() || s.placesBlock() || s.move() == Move.PILLAR) {
                break; // past here the plan counts on its own changes
            }
            BlockPos p = s.to();
            if (world.kind(p.getX(), p.getY(), p.getZ(), false) == PathWorld.Kind.NONE) {
                return false;
            }
        }
        return true;
    }

    private boolean arrived(LocalPlayer player, Step step, Vec3 target, double horizontal) {
        double dy = player.getY() - step.standY();
        boolean settled = player.onGround() || player.isInWater() || player.onClimbable();
        return switch (step.move()) {
            // a swimmer floats: a cell below the surface counts as reached from a little above it
            case SWIM -> horizontal < 0.45 && player.getY() - step.to().getY() < 1.3 && step.to().getY() - player.getY() < 0.7;
            case CLIMB -> horizontal < 0.7 && Math.abs(player.getY() - step.to().getY()) < 0.3;
            case DESCEND -> horizontal < 0.4 && dy > -0.5 && dy < 0.6 && settled;
            default -> {
                // on a straight walk, take the next cell before stopping at this one's middle
                boolean through = step.move() == Move.WALK && this.index + 1 < this.path.size() && this.flowsOn(step);
                yield horizontal < (through ? 0.62 : 0.33) && dy > -0.5 && dy < 0.6 && settled;
            }
        };
    }

    /** Whether the next step goes on from this one without anything to stop for. */
    private boolean flowsOn(Step step) {
        Step next = this.path.get(this.index + 1);
        return next.breaks().isEmpty() && !next.placesBlock() && (next.move() == Move.WALK || next.move() == Move.ASCEND
                || next.move() == Move.DESCEND) && Math.abs(next.to().getY() - step.to().getY()) <= 1;
    }

    private void walk(Minecraft mc, LocalPlayer player, Controls c, Step step, Vec3 target, double horizontal) {
        if (this.door(mc, player, c, step.to())) {
            return;
        }
        c.headFor(player, target.x, target.z);
        c.forward = horizontal > 0.12;
        // sprint along a straight run
        c.sprint = Bot.settings.sprint && player.getFoodData().getFoodLevel() > 6 && this.straightRun() >= 3 && !player.isInWater();
        if (step.move() == Move.ASCEND) {
            c.jump = (player.onGround() || player.isInWater() || player.onClimbable()) && horizontal < 1.35;
        }
        if (step.move() == Move.DESCEND) {
            c.forward = horizontal > 0.22;
            c.sprint = false;
        }
        if (player.isInWater() && step.standY() > player.getY() - 0.2) {
            c.jump = true; // out of the water onto the bank
        }
    }

    /** How many steps on from here the way runs straight and level enough to sprint. */
    private int straightRun() {
        if (this.index + 1 >= this.path.size()) {
            return 0;
        }
        BlockPos a = this.from;
        Step first = this.path.get(this.index);
        int dx = Integer.signum(first.to().getX() - a.getX());
        int dz = Integer.signum(first.to().getZ() - a.getZ());
        int run = 0;
        BlockPos prev = a;
        for (int i = this.index; i < this.path.size() && run < 6; i++) {
            Step s = this.path.get(i);
            if (s.move() != Move.WALK || !s.breaks().isEmpty() || s.placesBlock()
                    || Integer.signum(s.to().getX() - prev.getX()) != dx || Integer.signum(s.to().getZ() - prev.getZ()) != dz) {
                break;
            }
            prev = s.to();
            run++;
        }
        return run;
    }

    /** A closed door or gate in the cell ahead: turned to and opened (an empty hand, no sneak). True while busy with it. */
    private boolean door(Minecraft mc, LocalPlayer player, Controls c, BlockPos cell) {
        for (BlockPos p : new BlockPos[] {cell, cell.above()}) {
            BlockState state = player.level().getBlockState(p);
            boolean gate = state.getBlock() instanceof FenceGateBlock;
            if ((DoorBlock.isWoodenDoor(state) || gate) && !state.getValue(BlockStateProperties.OPEN)) {
                Vec3 middle = Vec3.atCenterOf(p);
                if (player.getEyePosition().distanceTo(middle) > 3.2) {
                    return false; // walk up to it first
                }
                c.lookAt(player, middle);
                if (Controls.looking(player, middle, 12.0F) && this.phaseTicks % 6 == 0) {
                    Inv.select(mc, Inv.emptyHandSlot(player));
                    Acts.Aim aim = Acts.aimAt(player, p);
                    Acts.useOn(mc, p, aim.face(), aim.point());
                    this.openedDoor = p;
                }
                return true;
            }
        }
        return false;
    }

    /**
     * Shuts the door it came through, once it is a step past it: a turn back, a click, and on. True while doing so.
     * Not if the next steps go back through it, nor if it has walked on out of reach.
     */
    private boolean closeDoor(Minecraft mc, LocalPlayer player, Controls c) {
        BlockPos door = this.openedDoor;
        if (door == null) {
            return false;
        }
        BlockState state = player.level().getBlockState(door);
        boolean openable = DoorBlock.isWoodenDoor(state) || state.getBlock() instanceof FenceGateBlock;
        Vec3 middle = Vec3.atCenterOf(door);
        double d = player.getEyePosition().distanceTo(middle);
        BlockPos feet = feetCell(player);
        boolean inIt = feet.equals(door) || feet.equals(door.below()) || feet.above().equals(door);
        if (!openable || !state.getValue(BlockStateProperties.OPEN) || d > 4.0 || ++this.closeTicks > 40) {
            this.openedDoor = null;
            this.closeTicks = 0;
            return false;
        }
        for (int i = this.index; i < Math.min(this.path.size(), this.index + 3); i++) {
            BlockPos s = this.path.get(i).to();
            if (s.equals(door) || s.equals(door.below())) {
                return false; // through it again shortly
            }
        }
        if (inIt || horizontalTo(player, door) < 1.3) {
            this.closeTicks = 0;
            return false; // not past it yet
        }
        c.lookAt(player, middle);
        if (Controls.looking(player, middle, 12.0F)) {
            Inv.select(mc, Inv.emptyHandSlot(player));
            Acts.Aim aim = Acts.aimAt(player, door);
            Acts.useOn(mc, door, aim.face(), aim.point());
            this.openedDoor = null;
            this.closeTicks = 0;
        }
        return true;
    }

    private static double horizontalTo(LocalPlayer player, BlockPos p) {
        return Math.hypot(p.getX() + 0.5 - player.getX(), p.getZ() + 0.5 - player.getZ());
    }

    private void swim(LocalPlayer player, Controls c, Vec3 target, double horizontal) {
        c.headFor(player, target.x, target.z);
        c.forward = horizontal > 0.25;
        double feet = player.getY();
        double want = target.y; // the cell's floor; a swimmer floats a little above
        if (want > feet - 0.15 || player.getAirSupply() < 60) {
            c.jump = true;
        } else if (want < feet - 0.6) {
            c.sneak = true;
        }
    }

    private void climb(LocalPlayer player, Controls c, Step step, Vec3 target, double horizontal) {
        c.headFor(player, target.x, target.z);
        c.forward = horizontal > 0.3;
        if (step.to().getY() > this.from.getY()) {
            c.jump = true;
        }
    }

    private void parkour(LocalPlayer player, Controls c, Step step, Vec3 target) {
        double fx = this.from.getX() + 0.5;
        double fz = this.from.getZ() + 0.5;
        double dx = Integer.signum(step.to().getX() - this.from.getX());
        double dz = Integer.signum(step.to().getZ() - this.from.getZ());
        double along = (player.getX() - fx) * dx + (player.getZ() - fz) * dz;
        c.headFor(player, target.x, target.z);
        c.forward = true;
        c.sprint = true;
        if (player.onGround() && along > 0.32 && along < 1.0) {
            c.jump = true;
        }
        // in the air: keep on; landing: the arrival test takes over
        double horizontal = Math.hypot(target.x - player.getX(), target.z - player.getZ());
        if (horizontal < 0.3 && player.onGround()) {
            c.forward = false;
            c.sprint = false;
        }
    }

    /** A bridge's block: sneak to the edge, turn to the side of the floor, set it down. True once it is there. */
    private boolean bridge(Minecraft mc, Controls c, Step step) {
        LocalPlayer player = mc.player;
        BlockPos place = step.place();
        if (!player.level().getBlockState(place).canBeReplaced()) {
            if (!this.placed) {
                this.placed = true;
            }
            return true;
        }
        if (!Inv.holdItem(mc, Inv::throwaway, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE))) {
            this.replan("nothing left to bridge with");
            return false;
        }
        Direction dir = step.face();
        double fx = this.from.getX() + 0.5;
        double fz = this.from.getZ() + 0.5;
        double along = (player.getX() - fx) * dir.getStepX() + (player.getZ() - fz) * dir.getStepZ();
        c.sneak = true;
        Vec3 hit = Vec3.atCenterOf(step.against()).add(dir.getStepX() * 0.5, 0.0, dir.getStepZ() * 0.5);
        if (along < 0.72 && this.phaseTicks < 60) {
            // edge forward, sneaking (sneak keeps it on the block), looking down at the edge
            c.headFor(player, fx + dir.getStepX() * 2.0, fz + dir.getStepZ() * 2.0);
            c.forward = true;
            if (along > 0.4) {
                c.lookAt(player, hit);
            }
            return false;
        }
        c.lookAt(player, hit);
        if (Controls.looking(player, hit, 25.0F) && this.phaseTicks % 3 == 0 && holdsThrowaway(player)) {
            Acts.useOn(mc, step.against(), dir, hit);
        }
        if (this.phaseTicks > 140) {
            this.replan("could not set the bridge's block down");
        }
        return false;
    }

    /** A pillar's block: look down, jump, set it down under the feet at the top of the jump. True once stood on it. */
    private boolean pillar(Minecraft mc, Controls c, Step step) {
        LocalPlayer player = mc.player;
        BlockPos place = step.place();
        BlockState placeState = player.level().getBlockState(place);
        boolean there = !placeState.canBeReplaced();
        if (there && placeState.getCollisionShape(player.level(), place).isEmpty()) {
            // something set down there that cannot be stood on (a torch): waiting to stand on it would never end
            this.replan("cannot stand on the " + Bot.id(placeState.getBlock()) + " at " + place.toShortString());
            return false;
        }
        if (there) {
            this.placed = true;
            if (player.onGround() && player.getY() >= place.getY() + 0.95) {
                return true;
            }
            c.face(player.getYRot(), 90.0F);
            return false;
        }
        if (!Inv.holdItem(mc, Inv::throwaway, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE))) {
            this.replan("nothing left to pillar with");
            return false;
        }
        // stand in the middle of the cell first
        double horizontal = Math.hypot(place.getX() + 0.5 - player.getX(), place.getZ() + 0.5 - player.getZ());
        c.face(player.getYRot(), 90.0F);
        if (horizontal > 0.25 && player.onGround()) {
            // shuffle in without turning: pick the key that moves toward the middle
            this.shuffleToward(player, c, place.getX() + 0.5, place.getZ() + 0.5);
            return false;
        }
        c.sneak = true; // the click sets a block down, never opens the one under the feet (a furnace, a chest)
        if (player.getXRot() < 80.0F) {
            return false; // still looking down
        }
        if (player.onGround()) {
            c.jump = true;
        } else if (player.getY() > place.getY() + 1.02 && holdsThrowaway(player)) {
            Acts.useOn(mc, step.against(), Direction.UP, new Vec3(place.getX() + 0.5, place.getY(), place.getZ() + 0.5));
        }
        if (this.phaseTicks > 120) {
            this.replan("could not pillar up");
        }
        return false;
    }

    /**
     * Whether the hand holds what bridges and pillars are made of yet. Taking it from the bag into the hand may take a
     * tick, and a click before then set down what was held: a torch under its own feet, in the hut beside the user
     * (2026-10-21), and the pillar waited on it for good.
     */
    private static boolean holdsThrowaway(LocalPlayer player) {
        return player.getAbilities().instabuild || Inv.throwaway(player.getMainHandItem());
    }

    /** Presses whichever of the movement keys moves the body toward a point, the view left where it is. */
    static void shuffleToward(LocalPlayer player, Controls c, double x, double z) {
        double yaw = Math.toRadians(player.getYRot());
        // the player's forward and right on the level
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        double dx = x - player.getX();
        double dz = z - player.getZ();
        double f = dx * fx + dz * fz;
        double r = dx * rx + dz * rz;
        c.forward = f > 0.08;
        c.back = f < -0.08;
        c.right = r > 0.08;
        c.left = r < -0.08;
        c.sneak = true;
    }
}
