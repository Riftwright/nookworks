package dev.nookworks.client.dev.bot;

import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.dev.bot.path.PathWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/** Getting about: going somewhere, following someone, running away, picking things up, looking, talking, waiting. */
final class MoveTasks {
    private MoveTasks() {}

    static @Nullable Player player(Minecraft mc, String name) {
        for (Player p : mc.level.players()) {
            if (p.getName().getString().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    /** {@code goto X Y Z [R]}, {@code goto player NAME [R]}, {@code goto block ID [RADIUS]}. */
    static Task goTo(Minecraft mc, String[] a) {
        if (a[1].equals("player")) {
            Player p = player(mc, a[2]);
            if (p == null) {
                throw new IllegalArgumentException("no player " + a[2]);
            }
            double r = a.length > 3 ? Double.parseDouble(a[3]) : 2.0;
            return travel(mc, Goal.near(p.blockPosition(), r), p.blockPosition(), "go to " + a[2]);
        }
        if (a[1].equals("block")) {
            int radius = a.length > 3 ? Integer.parseInt(a[3]) : 48;
            BlockPos found = Scan.nearest(mc, WorkTasks.blocks(a[2]), radius, Set.of());
            if (found == null) {
                throw new IllegalArgumentException("no " + a[2] + " within " + radius);
            }
            return travel(mc, Goal.near(found, 2.0), found, "go to the " + a[2] + " at " + found.toShortString());
        }
        if (a.length == 3 || a.length >= 4 && a[2].equals("~")) {
            // X Z, or X ~ Z [R]: any height there (within R across)
            int x = (int) Math.floor(Double.parseDouble(a[1]));
            int z = (int) Math.floor(Double.parseDouble(a[a.length == 3 ? 2 : 3]));
            double r = a.length > 4 ? Double.parseDouble(a[4]) : 0.0;
            var level = mc.level;
            Goal.Cells surface = (cx, cy, cz) -> dev.nookworks.dev.bot.BotRules.onSurface(level, cx, cy, cz,
                    level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, cx, cz), Bot::natural);
            BlockPos top = new BlockPos(x, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), z);
            return travel(mc, Goal.columnNear(x, z, r, surface), top, "go to " + x + ", " + z + (r > 0 ? " within " + r : ""));
        }
        BlockPos pos = Bot.pos(a, 1);
        var level = mc.level;
        int ground = level.hasChunkAt(pos)
                ? level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ())
                : Integer.MIN_VALUE;
        String wrong = dev.nookworks.dev.bot.BotRules.gotoHeightProblem(pos.getX(), pos.getY(), pos.getZ(), level.getMinY(),
                level.getMaxY(), ground);
        if (wrong != null) {
            throw new IllegalArgumentException(wrong);
        }
        double r = a.length > 4 ? Double.parseDouble(a[4]) : 0.0;
        return travel(mc, Goal.near(pos, r), pos, "go to " + pos.toShortString() + (r > 0 ? " within " + r : ""));
    }

    /**
     * A walk to a goal near {@code target}: by its own stairs and tunnels when the trip goes far up or down and starts
     * and ends near them ({@link dev.nookworks.dev.bot.Ways#route}) - the way it dug and knows, not the caves - else
     * straight there.
     */
    static Task travel(Minecraft mc, Goal goal, BlockPos target, String what) {
        var via = WayBook.ways(mc).route(Walker.feetCell(mc.player), target);
        if (via == null) {
            return new Goto(goal, what);
        }
        Bot.event("path", what + ": by its own way, " + via.size() + " waypoints from " + via.get(0).toShortString()
                + " to " + via.get(via.size() - 1).toShortString());
        return new Via(via, goal, what);
    }

    /** A walk by waypoints (along its own ways), then to the goal. A waypoint it cannot reach ends the waypoints: on to
     *  the goal as it can. */
    static final class Via implements Task, Bot.Resumable {
        private final java.util.List<BlockPos> points;
        private final Goal goal;
        private final String what;
        private int index;
        private @Nullable Goto leg;

        Via(java.util.List<BlockPos> points, Goal goal, String what) {
            this.points = points;
            this.goal = goal;
            this.what = what;
        }

        @Override
        public String describe() {
            return this.what + " (by its own way, " + Math.min(this.index, this.points.size()) + "/" + this.points.size() + ")";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            boolean last = this.index >= this.points.size();
            if (this.leg == null) {
                this.leg = last ? new Goto(this.goal, this.what) : new Goto(Goal.near(this.points.get(this.index), 1.5), "waypoint");
            }
            Result r = this.leg.tick(mc, c);
            if (r == Result.RUNNING) {
                return r;
            }
            this.leg.cancel(mc);
            this.leg = null;
            if (last) {
                return r;
            }
            if (r == Result.FAILED) {
                Bot.event("path", "its own way is blocked by " + this.points.get(this.index).toShortString() + ": on as it can");
                this.index = this.points.size();
            } else {
                this.index++;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            if (this.leg != null) {
                this.leg.cancel(mc);
                this.leg = null;
            }
        }
    }

    /** Walks until the goal is reached. Taken up again after an interruption by planning afresh from where it is. */
    static final class Goto implements Task, Bot.Resumable {
        private final Goal goal;
        private final String what;
        private boolean started;

        Goto(Goal goal, String what) {
            this.goal = goal;
            this.what = what;
        }

        @Override
        public String describe() {
            return this.what + " (" + Bot.walker.state() + ", " + Bot.walker.remaining() + " steps left)";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (!this.started || !Bot.walker.isFor(this.goal)) {
                this.started = true;
                Bot.walker.go(this.goal);
            }
            Bot.walker.tick(mc, c);
            return switch (Bot.walker.state()) {
                case DONE -> Result.DONE;
                case FAILED -> {
                    Bot.fail(Bot.walker.failure());
                    yield Result.FAILED;
                }
                default -> Result.RUNNING;
            };
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            this.started = false;
        }
    }

    /** Keeps near a player, looking at them when close, until stopped (or they are gone a while). */
    static final class Follow implements Task, Bot.Resumable {
        private final String name;
        private final double keep;
        private int missing;
        private @Nullable BlockPos heading;

        Follow(String name, double keep) {
            this.name = name;
            this.keep = keep;
        }

        @Override
        public String describe() {
            return "follow " + this.name;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            Player them = player(mc, this.name);
            if (them == null) {
                c.stopMoving();
                if (++this.missing > 400) {
                    Bot.fail(this.name + " is gone");
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            this.missing = 0;
            double d = me.distanceTo(them);
            BlockPos feet = Walker.feetCell(me);
            if (d <= this.keep && !idleSpot(mc, them).test(feet.getX(), feet.getY(), feet.getZ())) {
                // near enough, but in a doorway (or out in the open while they are in a room): a step to a better spot
                if (this.heading == null || Bot.walker.state() != Walker.State.MOVING && Bot.walker.state() != Walker.State.PLANNING) {
                    this.heading = them.blockPosition();
                    Bot.walker.go(Goal.near(this.heading, this.keep, idleSpot(mc, them)));
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() != Walker.State.FAILED) {
                    return Result.RUNNING;
                }
            }
            if (d <= this.keep) {
                if (this.heading != null) {
                    Bot.walker.stop(mc);
                    this.heading = null;
                }
                c.lookAt(me, them.getEyePosition());
                return Result.RUNNING;
            }
            BlockPos at = them.blockPosition();
            if (this.heading == null || this.heading.distManhattan(at) > 2 || Bot.walker.state() == Walker.State.FAILED
                    || Bot.walker.state() == Walker.State.DONE || Bot.walker.state() == Walker.State.IDLE) {
                this.heading = at;
                Bot.walker.go(Goal.near(at, Math.max(1.0, this.keep - 1.0), idleSpot(mc, them)));
            }
            Bot.walker.tick(mc, c);
            return Result.RUNNING;
        }

        /** Where to stand beside them: dry, not a doorway, and under a roof when they are under one. */
        private static Goal.Cells idleSpot(Minecraft mc, Player them) {
            var level = mc.level;
            var top = net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;
            BlockPos at = them.blockPosition();
            boolean inside = at.getY() + 1 < level.getHeight(top, at.getX(), at.getZ());
            return (x, y, z) -> dev.nookworks.dev.bot.BotRules.dry(level, x, y, z) && !dev.nookworks.dev.bot.BotRules.doorway(level, x, y, z)
                    && (!inside || y + 1 < level.getHeight(top, x, z));
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            this.heading = null;
        }
    }

    /** Away from a danger, at least {@code distance} blocks on the level. */
    static final class Flee implements Task {
        private final Vec3 from;
        private final double distance;
        private boolean started;

        Flee(Vec3 from, double distance) {
            this.from = from;
            this.distance = distance;
        }

        @Override
        public String describe() {
            return "run away from " + BlockPos.containing(this.from).toShortString();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (!this.started) {
                this.started = true;
                var level = mc.level;
                Bot.walker.go(Goal.awayFrom(this.from.x, this.from.z, this.distance,
                        (x, y, z) -> dev.nookworks.dev.bot.BotRules.dry(level, x, y, z)));
            }
            Bot.walker.tick(mc, c);
            c.sprint = true;
            return switch (Bot.walker.state()) {
                case DONE -> Result.DONE;
                case FAILED -> {
                    Bot.fail(Bot.walker.failure());
                    yield Result.FAILED;
                }
                default -> Result.RUNNING;
            };
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** A retreat, bare handed, to a safe place (home, the companion's side): a walk there, run as a reflex - and home,
     *  the door shut behind it (the zombie it ran from followed it in through the door left open). */
    static final class Shelter implements Task {
        private final Task walk;
        private final boolean shutDoor;
        private boolean arrived;
        private @org.jspecify.annotations.Nullable BlockPos door;
        private int tries;

        Shelter(Goal goal, String what, boolean shutDoor) {
            this(new Goto(goal, what), shutDoor);
        }

        Shelter(Task walk, boolean shutDoor) {
            this.walk = walk;
            this.shutDoor = shutDoor;
        }

        @Override
        public String describe() {
            return this.arrived ? "shut the door" : this.walk.describe();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (!this.arrived) {
                c.sprint = true;
                Result r = this.walk.tick(mc, c);
                if (r != Result.DONE || !this.shutDoor) {
                    return r;
                }
                this.arrived = true;
                this.door = openDoor(mc);
            }
            if (this.door == null || !mc.level.getBlockState(this.door).getOptionalValue(net.minecraft.world.level.block.DoorBlock.OPEN).orElse(false)) {
                return Result.DONE;
            }
            return ItemTasks.useBlock(mc, c, this.door, ++this.tries) ? Result.RUNNING : Result.DONE;
        }

        /** The nearest open door a hand can shut, within reach. */
        private static @org.jspecify.annotations.Nullable BlockPos openDoor(Minecraft mc) {
            BlockPos at = mc.player.blockPosition();
            BlockPos best = null;
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-4, -1, -4), at.offset(4, 2, 4))) {
                var s = mc.level.getBlockState(p);
                if (s.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && door.type().canOpenByHand()
                        && s.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)
                        && s.getValue(net.minecraft.world.level.block.DoorBlock.HALF) == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER
                        && Acts.inReach(mc.player, net.minecraft.world.phys.Vec3.atCenterOf(p))
                        && (best == null || p.distSqr(at) < best.distSqr(at))) {
                    best = p.immutable();
                }
            }
            return best;
        }

        @Override
        public void cancel(Minecraft mc) {
            this.walk.cancel(mc);
        }
    }

    /** Digs out the block its head is in (sand or gravel come down on it), and what comes down after. */
    static final class DigOut implements Task {
        private Acts.@Nullable Breaker breaker;
        private int ticks;

        @Override
        public String describe() {
            return "dig out of a block";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (!player.isInWall()) {
                return Result.DONE;
            }
            if (++this.ticks > 400) {
                Bot.fail("could not dig out");
                return Result.FAILED;
            }
            BlockPos head = BlockPos.containing(player.getEyePosition());
            if (this.breaker == null || !this.breaker.pos.equals(head)) {
                this.breaker = new Acts.Breaker(head);
            }
            if (this.breaker.tick(mc, c)) {
                this.breaker = null;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            mc.gameMode.stopDestroyBlock();
        }
    }

    /** A torch set at its feet: looking straight down, a click on the floor's top. */
    static final class SetTorch implements Task {
        private final BlockPos feet;
        private int ticks;

        SetTorch(BlockPos feet) {
            this.feet = feet;
        }

        @Override
        public String describe() {
            return "set a torch at " + this.feet.toShortString();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (mc.level.getBlockState(this.feet).is(net.minecraft.world.level.block.Blocks.TORCH)) {
                return Result.DONE;
            }
            if (++this.ticks > 40) {
                Bot.fail("the torch did not take");
                return Result.FAILED;
            }
            if (!FarmTasks.holdOf(mc, s -> s.is(net.minecraft.world.item.Items.TORCH), new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TORCH), "no torch")) {
                return Result.FAILED;
            }
            c.face(player.getYRot(), 90.0F);
            if (player.getXRot() > 80.0F && player.getMainHandItem().is(net.minecraft.world.item.Items.TORCH) && this.ticks % 5 == 0) {
                Acts.useOn(mc, this.feet.below(), net.minecraft.core.Direction.UP, new net.minecraft.world.phys.Vec3(this.feet.getX() + 0.5,
                        this.feet.getY(), this.feet.getZ() + 0.5));
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
        }
    }

    /** Swims up for air: jump held, looking up, until the head is out of the water - or, with rock over the water,
     *  to the nearest place the head is in air (back the way it came, likely). */
    static final class Surface implements Task {
        private int ticks;
        private @Nullable Goal goal;

        @Override
        public String describe() {
            return "swim up for air";
        }

        private @Nullable Goal shore;
        private java.util.@Nullable List<BlockPos> route;

        /** Through the water, cell by cell, to the nearest cell with air over it (a breath): the cells to swim, or null. */
        private static java.util.@Nullable List<BlockPos> swimRoute(Minecraft mc, BlockPos from) {
            var level = mc.level;
            java.util.Map<BlockPos, BlockPos> came = new java.util.HashMap<>();
            java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
            came.put(from, from);
            open.add(from);
            while (!open.isEmpty() && came.size() < 4000) {
                BlockPos p = open.poll();
                var above = level.getBlockState(p.above());
                if (!PathWorld.water(above) && above.getCollisionShape(level, p.above()).isEmpty() && !p.equals(from)) {
                    java.util.List<BlockPos> route = new java.util.ArrayList<>();
                    for (BlockPos q = p; !q.equals(from); q = came.get(q)) {
                        route.add(0, q);
                    }
                    route.add(p.above()); // and up into the air
                    return route;
                }
                for (net.minecraft.core.Direction d : net.minecraft.core.Direction.values()) {
                    BlockPos n = p.relative(d);
                    if (!came.containsKey(n) && n.distManhattan(from) <= 24 && PathWorld.water(level.getBlockState(n))) {
                        came.put(n, p);
                        open.add(n);
                    }
                }
            }
            return null;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            var level = mc.level;
            if (!player.isUnderWater() && player.getAirSupply() >= player.getMaxAirSupply() / 2) {
                // breathing again: out onto dry land, or the water takes it under again as soon as it stops
                if (!player.isInWater() || this.shore != null && Bot.walker.state() == Walker.State.FAILED) {
                    return Result.DONE;
                }
                if (this.shore == null) {
                    this.shore = Goal.near(player.blockPosition(), 16.0, (x, y, z) -> dev.nookworks.dev.bot.BotRules.dry(level, x, y, z));
                    Bot.walker.go(this.shore);
                }
                if (!Bot.walker.isFor(this.shore)) {
                    Bot.walker.go(this.shore);
                }
                Bot.walker.tick(mc, c);
                return Bot.walker.state() == Walker.State.DONE ? Result.DONE : Result.RUNNING;
            }
            if (++this.ticks > 400) {
                Bot.fail("could not get up to air");
                return Result.FAILED;
            }
            // rock over the water (or straight up has not worked in two seconds): the nearest air another way, swum to
            // cell by cell (not the walker's way: its path runs along the bottom while a body under water floats up to
            // the rock, and it was "stuck" until it drowned)
            boolean open = openAbove(mc, player);
            if (this.route == null && (this.ticks == 1 && !open || this.ticks == 40)) {
                this.route = swimRoute(mc, player.blockPosition());
                Bot.event("reflex", this.route == null ? "no air within reach under water" : "no air straight up: swims " + this.route.size() + " to air");
            }
            if (this.route != null && !open && !this.route.isEmpty()) {
                Vec3 next = Vec3.atCenterOf(this.route.get(0));
                if (player.position().add(0, 0.5, 0).distanceTo(next) < 0.7 && this.route.size() > 1) {
                    this.route.remove(0);
                    next = Vec3.atCenterOf(this.route.get(0));
                }
                c.lookAt(player, next);
                c.forward = true;
                c.sprint = true; // the swimming stroke
                c.jump = next.y > player.getY() + 0.6;
                return Result.RUNNING;
            }
            // straight up: no forward key (it swam away from the open water over it), the body in the middle of the
            // column (half over the rock beside a narrow opening, its head hit the rock and it drowned there)
            c.face(player.getYRot(), -90.0F);
            c.jump = true;
            BlockPos col = player.blockPosition();
            if (Math.hypot(player.getX() - (col.getX() + 0.5), player.getZ() - (col.getZ() + 0.5)) > 0.15) {
                Walker.shuffleToward(player, c, col.getX() + 0.5, col.getZ() + 0.5);
            }
            return Result.RUNNING;
        }

        /** Water straight up to air within eight blocks (not rock over it). */
        private static boolean openAbove(Minecraft mc, LocalPlayer player) {
            BlockPos.MutableBlockPos p = player.blockPosition().mutable();
            for (int i = 0; i < 9; i++) {
                p.move(0, 1, 0);
                var s = mc.level.getBlockState(p);
                if (!PathWorld.water(s)) {
                    return s.getCollisionShape(mc.level, p).isEmpty();
                }
            }
            return false;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** Walks over the items lying within reach of the radius (those of one kind, if asked), nearest first. */
    static final class Collect implements Task, Bot.Resumable {
        private final double radius;
        private final @Nullable Item only;
        private final Set<Integer> given = new HashSet<>();
        private @Nullable ItemEntity target;
        private @Nullable Goal goal;
        private int ticks;
        private int taken;

        Collect(double radius, @Nullable Item only) {
            this.radius = radius;
            this.only = only;
        }

        @Override
        public String describe() {
            return "pick up items (" + this.taken + " so far)";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            if (this.target == null || !this.target.isAlive()) {
                if (this.target != null) {
                    this.taken++;
                }
                this.target = this.next(me);
                this.ticks = 0;
                if (this.target == null) {
                    Bot.walker.stop(mc);
                    return Result.DONE;
                }
                this.goal = Goal.near(this.target.blockPosition(), 0.0);
                Bot.walker.go(this.goal);
            } else if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal); // taken up again after something else
            }
            if (++this.ticks > 300 || Bot.walker.state() == Walker.State.FAILED) {
                this.given.add(this.target.getId()); // out of reach: leave it
                this.target = null;
                return Result.RUNNING;
            }
            if (Bot.walker.state() == Walker.State.DONE) {
                // stood on its cell and it is still there: step about over it
                c.headFor(me, this.target.getX(), this.target.getZ());
                c.forward = me.distanceTo(this.target) > 0.4;
                return Result.RUNNING;
            }
            Bot.walker.tick(mc, c);
            return Result.RUNNING;
        }

        private @Nullable ItemEntity next(LocalPlayer me) {
            ItemEntity best = null;
            double bestD = this.radius * this.radius;
            for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
                // not one under water: it would have to dive for it
                if (e instanceof ItemEntity item && item.isAlive() && !this.given.contains(item.getId()) && !item.isUnderWater()
                        && (this.only == null || item.getItem().is(this.only))) {
                    double d = item.distanceToSqr(me);
                    if (d < bestD) {
                        bestD = d;
                        best = item;
                    }
                }
            }
            return best;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            this.target = null;
        }
    }

    /** Turns to look at a point, or at a player's face. */
    static final class Look implements Task {
        private final @Nullable Vec3 at;
        private final @Nullable String who;
        private int ticks;

        Look(@Nullable Vec3 at, @Nullable String who) {
            this.at = at;
            this.who = who;
        }

        @Override
        public String describe() {
            return "look at " + (this.who != null ? this.who : BlockPos.containing(this.at).toShortString());
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            Vec3 point = this.at;
            if (this.who != null) {
                Player p = player(mc, this.who);
                if (p == null) {
                    Bot.fail("no player " + this.who);
                    return Result.FAILED;
                }
                point = p.getEyePosition();
            }
            c.lookAt(mc.player, point);
            return Controls.looking(mc.player, point, 2.0F) || ++this.ticks > 60 ? Result.DONE : Result.RUNNING;
        }
    }

    /** A line in the chat (or a command, if it starts with a slash). */
    static final class Say implements Task {
        private final String text;

        Say(String text) {
            this.text = text;
        }

        @Override
        public String describe() {
            return "say " + this.text;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.text.startsWith("/")) {
                mc.player.connection.sendCommand(this.text.substring(1));
            } else {
                mc.player.connection.sendChat(this.text);
            }
            return Result.DONE;
        }
    }

    static final class Wait implements Task {
        private int left;

        Wait(int ticks) {
            this.left = ticks;
        }

        @Override
        public String describe() {
            return "wait " + this.left;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            return --this.left <= 0 ? Result.DONE : Result.RUNNING;
        }
    }
}
