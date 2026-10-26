package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.dev.bot.path.PathWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Work with blocks: mining, setting blocks down (a single block, or a whole build from a file), tidying up after. */
final class WorkTasks {
    private WorkTasks() {}

    /** Block kinds from a command: ids and #tags, comma separated ({@code minecraft:oak_log,#minecraft:logs}). */
    static Predicate<BlockState> blocks(String spec) {
        Predicate<BlockState> any = s -> false;
        for (String part : spec.split(",")) {
            Predicate<BlockState> one;
            if (part.startsWith("#")) {
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, Identifier.parse(part.substring(1)));
                one = s -> s.is(tag);
            } else {
                Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(part));
                if (block == net.minecraft.world.level.block.Blocks.AIR && !part.endsWith("air")) {
                    throw new IllegalArgumentException("no block " + part);
                }
                one = s -> s.is(block);
            }
            any = any.or(one);
        }
        return any;
    }

    /** A block to set down: where, and its state ({@code minecraft:oak_stairs[facing=east,half=top]}). */
    record Target(BlockPos pos, BlockState state) {}

    static Target target(String spec, BlockPos pos) {
        try {
            return new Target(pos, BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, spec, false).blockState());
        } catch (CommandSyntaxException e) {
            throw new IllegalArgumentException("bad block state " + spec + ": " + e.getMessage());
        }
    }

    /** {@code build FILE [OX OY OZ]}: a file of {@code x y z state} lines ('#' starts a comment), offset by O. */
    static Task buildFile(Minecraft mc, String[] a) {
        List<Target> targets = readFile(a[1], a.length > 4 ? Bot.pos(a, 2) : BlockPos.ZERO);
        return new Build(targets, "build " + Path.of(a[1]).getFileName() + " (" + targets.size() + " blocks)");
    }

    /** A build file's blocks ({@code x y z state} lines, '#' a comment; relative to run-agent/ unless absolute), offset. */
    static List<Target> readFile(String name, BlockPos origin) {
        Path file = Path.of(name);
        if (!file.isAbsolute()) {
            file = Bot.dir().resolve(name);
        }
        List<Target> targets = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String t = line.strip();
                if (t.isEmpty() || t.startsWith("#")) {
                    continue;
                }
                String[] p = t.split("\\s+", 4);
                BlockPos at = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2])).offset(origin);
                targets.add(target(p[3], at));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("cannot read " + file + ": " + e.getMessage());
        }
        return targets;
    }

    // ---- mining

    /** Mines blocks of a kind, nearest first, walking (and if need be digging) to each, picking up what drops. */
    static final class Mine implements Task, Bot.Resumable {
        private final Predicate<BlockState> what;
        private final int want;
        private final int radius;
        private final Set<BlockPos> skip = new HashSet<>();
        private @Nullable BlockPos target;
        private @Nullable Goal goal;
        private Acts.@Nullable Breaker breaker;
        private MoveTasks.@Nullable Collect collect;
        private int collectTicks;
        private int mined;
        private int underWater;
        private int underBuild;
        private List<BlockPos> vein = List.of();

        Mine(Predicate<BlockState> what, int want, int radius) {
            this.what = what;
            this.want = want;
            this.radius = radius;
        }

        @Override
        public String describe() {
            return "mine " + this.mined + "/" + this.want + (this.target == null ? "" : ", at " + this.target.toShortString());
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.collect != null) {
                if (this.collect.tick(mc, c) == Result.RUNNING && ++this.collectTicks < 400) {
                    return Result.RUNNING;
                }
                this.collect.cancel(mc);
                this.collect = null;
                if (this.mined >= this.want) {
                    return Result.DONE;
                }
            }
            LocalPlayer player = mc.player;
            if (this.mined == 0 && this.target == null && this.skip.isEmpty() && !player.getAbilities().instabuild
                    && player.getHealth() <= dev.nookworks.dev.bot.BotRules.LOW_HEALTH) {
                // it set off mining at two health, and died down there (2026-10-22)
                Bot.fail("too hurt to go mining (" + (int) player.getHealth() + " health): eat or rest first");
                return Result.FAILED;
            }
            if (this.target == null) {
                this.target = Scan.nearest(mc, this.what, this.radius, this.skip);
                if (this.target == null) {
                    if (this.mined > 0) {
                        return Result.DONE;
                    }
                    Bot.fail("none found within " + this.radius + (this.underWater > 0 ? " (" + this.underWater + " left: only under water)" : "")
                            + (this.underBuild > 0 ? " (" + this.underBuild + " left: under a build)" : ""));
                    return Result.FAILED;
                }
                BlockState aim = mc.level.getBlockState(this.target);
                if (!player.getAbilities().instabuild && Inv.bestTool(player, aim) == -2) {
                    // nothing carried mines it (the pickaxes worn out): it said "could not break" for ten minutes
                    // and never failed, so no new pickaxe was made (2026-10-22)
                    Bot.fail("no tool that can mine " + Bot.id(aim.getBlock()) + " (a pickaxe, or a better one; worn-out ones are kept, not used)"
                            + (this.mined > 0 ? " - " + this.mined + " mined before" : ""));
                    return Result.FAILED;
                }
                if (dev.nookworks.dev.bot.BotRules.underBuild(mc.level, this.target, Bot::natural)) {
                    this.skip.add(this.target); // it holds up someone's floor or wall
                    this.underBuild++;
                    this.target = null;
                    return Result.RUNNING;
                }
                if (dev.nookworks.dev.bot.BotRules.onlyUnderWater(mc.level, this.target)) {
                    this.skip.add(this.target); // a lake bed's: not worth drowning for
                    this.underWater++;
                    this.target = null;
                    return Result.RUNNING;
                }
                this.goal = new ReachGoal(this.target);
                Bot.walker.go(this.goal);
                this.breaker = null;
            }
            if (this.breaker == null && !this.what.test(player.level().getBlockState(this.target))) {
                this.target = null; // gone meanwhile (someone else mined it, it fell)
                return Result.RUNNING;
            }
            if (this.breaker == null) {
                if (!Bot.walker.isFor(this.goal)) {
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                switch (Bot.walker.state()) {
                    case DONE -> {
                        this.breaker = new Acts.Breaker(this.target, true);
                        this.vein = vein(mc, this.target, this.what);
                    }
                    case FAILED -> {
                        this.skip.add(this.target);
                        this.target = null;
                    }
                    default -> {
                    }
                }
                return Result.RUNNING;
            }
            if (!this.breaker.tick(mc, c)) {
                return Result.RUNNING;
            }
            if (this.breaker.failure() != null) {
                this.skip.add(this.target);
            } else {
                // Nookworks' vein mining (and its tree felling) may take the whole vein with it: all of it counts
                int gone = 0;
                for (BlockPos v : this.vein) {
                    if (!this.what.test(player.level().getBlockState(v))) {
                        gone++;
                    }
                }
                this.mined += Math.max(1, gone);
                this.collect = new MoveTasks.Collect(7.0, null);
                this.collectTicks = 0;
            }
            this.target = null;
            this.breaker = null;
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.breaker != null) {
                mc.gameMode.stopDestroyBlock();
            }
            this.breaker = null;
            this.target = null;
        }
    }

    /** The blocks of the kind joined to {@code start} (faces and edges), up to 128: a vein, a tree. */
    static List<BlockPos> vein(Minecraft mc, BlockPos start, Predicate<BlockState> what) {
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        java.util.ArrayDeque<BlockPos> open = new java.util.ArrayDeque<>();
        open.add(start);
        seen.add(start);
        while (!open.isEmpty() && out.size() < 128) {
            BlockPos p = open.poll();
            if (!what.test(mc.level.getBlockState(p))) {
                continue;
            }
            out.add(p);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = p.offset(dx, dy, dz);
                        if (seen.add(n)) {
                            open.add(n);
                        }
                    }
                }
            }
        }
        return out;
    }

    /** A cell from which the eyes reach the middle of a block, and where the body does not stand in it. */
    static final class ReachGoal implements Goal {
        private final BlockPos block;

        ReachGoal(BlockPos block) {
            this.block = block;
        }

        @Override
        public boolean reached(int x, int y, int z) {
            if (x == this.block.getX() && z == this.block.getZ() && (y == this.block.getY() || y + 1 == this.block.getY())) {
                return false;
            }
            double dx = x + 0.5 - (this.block.getX() + 0.5);
            double dy = y + 1.62 - (this.block.getY() + 0.5);
            double dz = z + 0.5 - (this.block.getZ() + 0.5);
            return dx * dx + dy * dy + dz * dz <= 3.9 * 3.9;
        }

        @Override
        public double estimate(int x, int y, int z) {
            return Math.max(0.0, Goal.distance(x - this.block.getX(), y - this.block.getY(), z - this.block.getZ()) - 3.5) * PER_BLOCK;
        }
    }

    // ---- setting blocks down

    /** How a block is set down: from where the walker stands, clicking which face, where on it. */
    record Plan(BlockPos stand, BlockPos clicked, Direction face, Vec3 hit) {}

    /** The properties that say which way a block is set (the rest - a stair's shape, a fence's links - follow from its
     *  neighbours). */
    private static final List<Property<?>> ORIENTATION = List.of(BlockStateProperties.FACING, BlockStateProperties.HORIZONTAL_FACING,
            BlockStateProperties.FACING_HOPPER, BlockStateProperties.AXIS, BlockStateProperties.HORIZONTAL_AXIS,
            BlockStateProperties.HALF, BlockStateProperties.SLAB_TYPE, BlockStateProperties.DOOR_HINGE,
            BlockStateProperties.ATTACH_FACE, BlockStateProperties.ROTATION_16, BlockStateProperties.ORIENTATION,
            BlockStateProperties.VERTICAL_DIRECTION);

    static boolean sameWay(BlockState want, @Nullable BlockState got) {
        if (got == null || got.getBlock() != want.getBlock()) {
            return false;
        }
        for (Property<?> p : ORIENTATION) {
            if (want.hasProperty(p) && got.hasProperty(p) && !want.getValue(p).equals(got.getValue(p))) {
                return false;
            }
        }
        return true;
    }

    /** Whether the world already has the block there as asked. */
    static boolean done(Minecraft mc, Target t) {
        return sameWay(t.state(), mc.level.getBlockState(t.pos()));
    }

    /**
     * Every way to set the block down as asked from cells round it: the faces of solid neighbours, a few points on each,
     * each tried from each cell with the game's own placement (the player's look turned to the point, for a moment, and
     * turned back), and kept if the block comes out facing the way it should. Cells to stand in may be in mid-air: the
     * walker pillars up to them.
     */
    static List<Plan> plans(Minecraft mc, Target t, ItemStack stack, int limit, @Nullable BlockPos assume) {
        // ways that still come out right with the look a few degrees off, first; any that come out right, failing those
        List<Plan> steady = plans(mc, t, stack, limit, assume, true);
        return steady.isEmpty() ? plans(mc, t, stack, limit, assume, false) : steady;
    }

    private static List<Plan> plans(Minecraft mc, Target t, ItemStack stack, int limit, @Nullable BlockPos assume, boolean steadyOnly) {
        LocalPlayer player = mc.player;
        List<Plan> plans = new ArrayList<>();
        if (!(stack.getItem() instanceof BlockItem item)) {
            return plans;
        }
        PathWorld world = new PathWorld(mc.level, Bot.abilities(mc));
        double reach = player.blockInteractionRange() - 0.25;
        BlockPos at = t.pos();
        for (Direction d : Direction.values()) {
            BlockPos clicked = at.relative(d);
            if (assume != null && !clicked.equals(assume)) {
                continue; // asked about one face only: a helper block that would be there
            }
            BlockState against = mc.level.getBlockState(clicked);
            if (assume == null && (against.isAir() || against.canBeReplaced()
                    || !against.getFluidState().isEmpty() && against.getCollisionShape(mc.level, clicked).isEmpty())) {
                continue;
            }
            Direction face = d.getOpposite();
            Vec3 faceMiddle = Vec3.atCenterOf(clicked).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
            List<Vec3> points = new ArrayList<>();
            points.add(faceMiddle);
            if (face.getAxis().isHorizontal()) {
                points.add(faceMiddle.add(0, 0.3, 0));
                points.add(faceMiddle.add(0, -0.3, 0));
            }
            for (int x = -4; x <= 4; x++) {
                for (int y = -3; y <= 4; y++) {
                    for (int z = -4; z <= 4; z++) {
                        BlockPos cell = at.offset(x, y, z);
                        if (cell.equals(at) || cell.above().equals(at)) {
                            continue;
                        }
                        PathWorld.Kind kind = world.kind(cell.getX(), cell.getY(), cell.getZ(), false);
                        double feet;
                        if (kind == PathWorld.Kind.GROUND) {
                            feet = world.standY(cell.getX(), cell.getY(), cell.getZ(), false);
                        } else if (kind == PathWorld.Kind.NONE && world.bodyFits(cell.getX(), cell.getZ(), cell.getY())
                                && pillarable(world, cell)) {
                            feet = cell.getY();
                        } else {
                            continue;
                        }
                        Vec3 eye = new Vec3(cell.getX() + 0.5, feet + player.getEyeHeight(), cell.getZ() + 0.5);
                        for (Vec3 hit : points) {
                            if (eye.distanceToSqr(hit) > reach * reach) {
                                continue;
                            }
                            // the eyes see that face
                            Vec3 lookFrom = eye;
                            BlockHitResult seen = mc.level.clip(new ClipContext(lookFrom, hit.add(hit.subtract(lookFrom).normalize().scale(0.05)),
                                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
                            if (assume == null && (seen.getType() != HitResult.Type.BLOCK || !seen.getBlockPos().equals(clicked)
                                    || seen.getDirection() != face)) {
                                continue;
                            }
                            BlockState got = simulate(mc, item, stack, eye, hit, clicked, face);
                            if (sameWay(t.state(), got) && (!steadyOnly || steady(mc, item, stack, eye, hit, clicked, face, t.state()))) {
                                plans.add(new Plan(cell, clicked, face, hit));
                                if (plans.size() >= limit) {
                                    return plans;
                                }
                                break;
                            }
                        }
                    }
                }
            }
        }
        return plans;
    }

    /** Whether the block still comes out right with the look a few degrees either way. */
    private static boolean steady(Minecraft mc, BlockItem item, ItemStack stack, Vec3 eye, Vec3 hit, BlockPos clicked, Direction face,
            BlockState want) {
        float[] look = lookFrom(eye, hit);
        for (float dy : new float[] {-7.0F, 7.0F}) {
            for (float dp : new float[] {-5.0F, 5.0F}) {
                if (!sameWay(want, simulate(mc, item, stack, look[0] + dy, look[1] + dp, hit, clicked, face))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the walker can pillar up to the cell: open air down to a floor, a few blocks below. */
    private static boolean pillarable(PathWorld world, BlockPos cell) {
        for (int d = 1; d <= 5; d++) {
            BlockPos below = cell.below(d);
            if (world.kind(below.getX(), below.getY(), below.getZ(), false) == PathWorld.Kind.GROUND) {
                return true;
            }
            if (!world.bodyFits(below.getX(), below.getZ(), below.getY(), 1.0)) {
                return false;
            }
        }
        return false;
    }

    /** The state the game would set down for a click at {@code hit} from eyes at {@code eye} (the look turned there for
     *  the question, and back). */
    static @Nullable BlockState simulate(Minecraft mc, BlockItem item, ItemStack stack, Vec3 eye, Vec3 hit, BlockPos clicked, Direction face) {
        float[] look = lookFrom(eye, hit);
        return simulate(mc, item, stack, look[0], look[1], hit, clicked, face);
    }

    /** Yaw and pitch of a look from the eyes to a point. */
    static float[] lookFrom(Vec3 eye, Vec3 hit) {
        double dx = hit.x - eye.x;
        double dy = hit.y - eye.y;
        double dz = hit.z - eye.z;
        return new float[] {(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    static @Nullable BlockState simulate(Minecraft mc, BlockItem item, ItemStack stack, float lookYaw, float lookPitch, Vec3 hit,
            BlockPos clicked, Direction face) {
        LocalPlayer player = mc.player;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float yawO = player.yRotO;
        float pitchO = player.xRotO;
        try {
            player.setYRot(lookYaw);
            player.setXRot(lookPitch);
            player.yRotO = lookYaw;
            player.xRotO = lookPitch;
            BlockPlaceContext ctx = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, face, clicked, false));
            BlockPlaceContext updated = item.updatePlacementContext(ctx);
            if (updated == null || !updated.canPlace()) {
                return null;
            }
            if (item instanceof StandingAndWallBlockItem) {
                // a torch, a sign, a head: the item picks the floor or the wall block by the look and the face, and only
                // one that holds there (the torch played beside the user, 2026-10-21, was planned as standing on any
                // face it clicked - a wall's side set a wall torch - and a wall torch was never planned at all)
                return placementState(item, updated);
            }
            BlockState state = item.getBlock().getStateForPlacement(updated);
            return state != null && state.canSurvive(mc.level, updated.getClickedPos()) ? state : null;
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.yRotO = yawO;
            player.xRotO = pitchO;
        }
    }

    private static java.lang.reflect.@Nullable Method placementState;

    /** The item's own choice of state for a click ({@code BlockItem.getPlacementState}, protected). */
    private static @Nullable BlockState placementState(BlockItem item, BlockPlaceContext ctx) {
        try {
            if (placementState == null) {
                placementState = BlockItem.class.getDeclaredMethod("getPlacementState", BlockPlaceContext.class);
                placementState.setAccessible(true);
            }
            return (BlockState) placementState.invoke(item, ctx);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("BlockItem.getPlacementState", e);
        }
    }

    /**
     * Sets blocks down, lowest first and nearest first within a layer, each from a cell it walks (or pillars) to, facing
     * the way it should. A block of something else in the way is broken first if it may be. A block that cannot be set
     * down yet - nothing beside it to click on, or nothing the right way round - waits until others are down; if all that
     * are left wait, a helper block is set beside one (a face to click), and taken away again once the build is done.
     * Blocks come from the inventory (in creative, from the creative inventory, as a person picks them).
     */
    static final class Build implements Task, Bot.Resumable {
        private final List<Target> left;
        private final String what;
        private final Set<Target> failed = new HashSet<>();
        /** Waiting for a neighbour: no way to set them down as things stand. */
        private final Set<Target> waiting = new HashSet<>();
        /** Helper blocks set down to click on, taken away at the end (one list for a build and its helpers' own). */
        private final List<BlockPos> helpers;
        private final Set<Target> helped = new HashSet<>();
        private @Nullable Task sub;
        private @Nullable Target target;
        private @Nullable Plan plan;
        private @Nullable Goal goal;
        private Acts.@Nullable Breaker breaker;
        private int phaseTicks;
        /** Ticks spent turning to the block and clicking (not walking there). */
        private int clickTicks;
        private int placedCount;
        private int tries;
        /** How deep in helper blocks this build is: a helper with nothing to stand on gets one of its own under it, down
         *  to the ground - a column - but no deeper than {@link #MAX_HELPERS}. */
        private final int depth;
        private boolean dugForScaffold;
        /** Every block of the build, and the box round them: its own pillar blocks left in it are taken away. */
        private final Set<BlockPos> all = new HashSet<>();
        private net.minecraft.world.level.levelgen.structure.@Nullable BoundingBox box;
        private boolean tidied;
        private static final int MAX_HELPERS = 6;

        Build(List<Target> targets, String what) {
            this(targets, what, 0, new ArrayList<>());
        }

        private Build(List<Target> targets, String what, int depth, List<BlockPos> helpers) {
            for (Target t : targets) {
                this.all.add(t.pos());
                this.box = this.box == null ? new net.minecraft.world.level.levelgen.structure.BoundingBox(t.pos())
                        : this.box.encapsulate(t.pos());
            }
            this.left = new ArrayList<>(targets);
            this.what = what;
            this.depth = depth;
            this.helpers = helpers;
        }

        @Override
        public String describe() {
            return this.what + ": " + this.placedCount + " set down, " + this.left.size() + " left"
                    + (this.failed.isEmpty() ? "" : ", " + this.failed.size() + " could not be");
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.sub != null) {
                Result r = this.sub.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                this.sub.cancel(mc);
                this.sub = null;
                this.waiting.clear(); // a helper is down (or not): everything may be tried again
                return Result.RUNNING;
            }
            if (this.target == null && this.depth == 0 && !player.getAbilities().instabuild && !this.dugForScaffold
                    && Inv.throwawayCount(player) < 12 && this.left.stream().anyMatch(t -> t.pos().getY() > player.getY() + 2)) {
                this.dugForScaffold = true;
                Bot.event("scaffold", "digging dirt to climb on");
                this.sub = new Mine(s -> s.is(Blocks.DIRT) || s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.COARSE_DIRT), 24, 24);
                return Result.RUNNING;
            }
            if (this.target == null) {
                this.left.removeIf(t -> done(mc, t));
                if (this.left.isEmpty()) {
                    if (!this.helpers.isEmpty() && this.depth == 0) {
                        this.sub = new Cleanup(this.helpers, "take the helper blocks away");
                        return Result.RUNNING;
                    }
                    if (this.depth == 0 && !this.tidied) {
                        // its own pillar and bridge blocks in or by what it built (a doorway blocked by them shut it in) -
                        // whether all of it went down or not
                        this.tidied = true;
                        List<BlockPos> own = new ArrayList<>();
                        for (BlockPos p : Bot.walker.scaffold) {
                            if (this.box != null && this.box.inflatedBy(2).isInside(p) && !this.all.contains(p)) {
                                own.add(p);
                            }
                        }
                        if (!own.isEmpty()) {
                            Bot.walker.scaffold.removeAll(own);
                            this.sub = new Cleanup(own, "take away its own pillar blocks by the build");
                            return Result.RUNNING;
                        }
                    }
                    if (!this.failed.isEmpty()) {
                        Bot.fail(this.failed.size() + " blocks could not be set down, first at " + this.failed.iterator().next().pos().toShortString());
                        return Result.FAILED;
                    }
                    if (Bot.settings.cleanup && !Bot.walker.scaffold.isEmpty() && this.depth == 0) {
                        this.sub = new Cleanup(Bot.walker.scaffold, "tidy up");
                        return Result.RUNNING;
                    }
                    return Result.DONE;
                }
                return this.choose(mc) ? Result.RUNNING : Result.FAILED;
            }
            Target t = this.target;
            this.phaseTicks++;
            if (this.phaseTicks > 1600 && !done(mc, t)) {
                this.giveUp("took too long at " + t.pos().toShortString());
                return Result.RUNNING;
            }
            if (done(mc, t)) {
                this.placedCount++;
                this.waiting.clear(); // a new block down: a face to click for those waiting
                this.next();
                return Result.RUNNING;
            }
            BlockState there = mc.level.getBlockState(t.pos());
            // something else in the way: break it first (if it may be broken)
            if (!there.canBeReplaced()) {
                if (this.breaker == null) {
                    if (!Bot.mayBreak(mc, there, t.pos()) && !Bot.walker.scaffold.contains(t.pos()) && !this.helpers.contains(t.pos())) {
                        this.giveUp("something of someone's is in the way at " + t.pos().toShortString());
                        return Result.RUNNING;
                    }
                    if (!Bot.walker.isFor(this.goal)) {
                        this.goal = new ReachGoal(t.pos());
                        Bot.walker.go(this.goal);
                    }
                    Bot.walker.tick(mc, c);
                    if (Bot.walker.state() == Walker.State.DONE) {
                        this.breaker = new Acts.Breaker(t.pos());
                    } else if (Bot.walker.state() == Walker.State.FAILED) {
                        this.giveUp("cannot get to " + t.pos().toShortString());
                    }
                    return Result.RUNNING;
                }
                if (this.breaker.tick(mc, c)) {
                    if (this.breaker.failure() != null) {
                        this.giveUp(this.breaker.failure());
                    }
                    this.breaker = null;
                    this.plan = null;
                    this.goal = null;
                }
                return Result.RUNNING;
            }
            ItemStack wanted = new ItemStack(t.state().getBlock().asItem());
            if (wanted.isEmpty()) {
                this.giveUp("no item sets down " + Bot.id(t.state().getBlock()));
                return Result.RUNNING;
            }
            if (Inv.find(player, s -> s.is(wanted.getItem())) < 0 && !player.getAbilities().instabuild) {
                Bot.fail("no " + Bot.id(t.state().getBlock()) + " left");
                return Result.FAILED;
            }
            if (this.plan == null) {
                List<Plan> plans = plans(mc, t, wanted, 40, null);
                if (plans.isEmpty()) {
                    this.waiting.add(t); // later, with more of the build down
                    this.target = null;
                    return Result.RUNNING;
                }
                BlockPos here = Walker.feetCell(player);
                plans.sort(Comparator.comparingDouble(p -> p.stand().distSqr(here)));
                this.plan = plans.get(0);
                List<BlockPos> cells = new ArrayList<>();
                for (Plan p : plans) {
                    cells.add(p.stand());
                }
                this.goal = Goal.any(cells);
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.isFor(this.goal) && Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    this.giveUp("cannot get to a place to set it down from: " + Bot.walker.failure());
                }
                return Result.RUNNING;
            }
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
                return Result.RUNNING;
            }
            // stood where it can be set down: the body out of the block's cell first (the game refuses a block set down
            // into someone), then the plan again from the eyes as they really are
            BlockPos here = Walker.feetCell(player);
            if (player.getBoundingBox().intersects(new net.minecraft.world.phys.AABB(t.pos()))) {
                Walker.shuffleToward(player, c, here.getX() + 0.5, here.getZ() + 0.5);
                return Result.RUNNING;
            }
            List<Plan> now = plans(mc, t, wanted, 60, null);
            Plan p = this.robust(mc, t, wanted, now, here);
            if (p == null) {
                if (++this.tries > 3) {
                    this.giveUp("the place to stand would not do at " + t.pos().toShortString());
                } else {
                    this.plan = null;
                    this.goal = null;
                }
                return Result.RUNNING;
            }
            Inv.holdItem(mc, s -> s.is(wanted.getItem()), wanted);
            this.clickTicks++;
            c.sneak = true; // never opens what it clicks on
            c.lookAt(player, p.hit());
            if (Controls.looking(player, p.hit(), 2.0F) && player.getMainHandItem().is(wanted.getItem()) && this.phaseTicks % 2 == 0
                    && wanted.getItem() instanceof BlockItem bi
                    && sameWay(t.state(), simulate(mc, bi, wanted, player.getYRot(), player.getXRot(), p.hit(), p.clicked(), p.face()))) {
                Acts.useOn(mc, p.clicked(), p.face(), p.hit());
            }
            if (this.clickTicks > 160) {
                this.giveUp("did not take at " + t.pos().toShortString());
            }
            return Result.RUNNING;
        }

        /**
         * Of the ways to set the block down from where it stands, one that comes out right from the eyes where they really
         * are, and still does with the look a few degrees either way (a stair's facing flips at 45 degrees). Null if none.
         */
        private @Nullable Plan robust(Minecraft mc, Target t, ItemStack wanted, List<Plan> plans, BlockPos here) {
            if (!(wanted.getItem() instanceof BlockItem item)) {
                return null;
            }
            Vec3 eye = mc.player.getEyePosition();
            Plan exact = null;
            for (Plan q : plans) {
                if (!q.stand().equals(here) || !Acts.inReach(mc.player, q.hit())) {
                    continue;
                }
                float[] look = lookFrom(eye, q.hit());
                if (!sameWay(t.state(), simulate(mc, item, wanted, look[0], look[1], q.hit(), q.clicked(), q.face()))) {
                    continue;
                }
                if (exact == null) {
                    exact = q;
                }
                boolean steady = true;
                for (float dy : new float[] {-6.0F, 6.0F}) {
                    for (float dp : new float[] {-4.0F, 4.0F}) {
                        if (!sameWay(t.state(), simulate(mc, item, wanted, look[0] + dy, look[1] + dp, q.hit(), q.clicked(), q.face()))) {
                            steady = false;
                        }
                    }
                }
                if (steady) {
                    return q;
                }
            }
            return exact;
        }

        /** The block helpers are made of: plain stuff it carries (dirt, cobblestone), cobblestone in creative. */
        private static BlockState helperBlock(Minecraft mc) {
            int index = Inv.find(mc.player, Inv::throwaway);
            if (index >= 0 && mc.player.getInventory().getItem(index).getItem() instanceof BlockItem block) {
                return block.getBlock().defaultBlockState();
            }
            return Blocks.COBBLESTONE.defaultBlockState();
        }

        /** The next block: the lowest layer first, nearest first in it, one not waiting for a neighbour. */
        private boolean choose(Minecraft mc) {
            BlockPos here = mc.player.blockPosition();
            this.left.sort(Comparator.<Target>comparingInt(t -> t.pos().getY()).thenComparingDouble(t -> t.pos().distSqr(here)));
            for (Target t : this.left) {
                if (this.failed.contains(t) || this.waiting.contains(t)) {
                    continue;
                }
                this.start(t);
                return true;
            }
            // everything left is waiting: a helper block beside one of them, a face to click - on a short column of
            // helpers from the ground when it would float - the one needing the fewest helpers first
            if (this.depth == 0) {
                Target best = null;
                List<BlockPos> bestColumn = null;
                for (Target t : this.left) {
                    if (this.failed.contains(t) || this.helped.contains(t)) {
                        continue;
                    }
                    List<BlockPos> column = this.helperFor(mc, t);
                    if (column != null && (bestColumn == null || column.size() < bestColumn.size())) {
                        best = t;
                        bestColumn = column;
                    }
                }
                if (best != null) {
                    this.helped.add(best);
                    Bot.event("helper", bestColumn.size() + " helper block(s) up to " + bestColumn.get(bestColumn.size() - 1).toShortString()
                            + " for " + best.pos().toShortString());
                    List<Target> column = new ArrayList<>();
                    BlockState helperBlock = helperBlock(mc);
                    for (BlockPos h : bestColumn) {
                        this.helpers.add(h);
                        column.add(new Target(h, helperBlock));
                    }
                    this.sub = new Build(column, "helper blocks", this.depth + 1, this.helpers);
                    return true;
                }
            }
            // nothing more can be done: those still waiting cannot be set down
            for (Target t : List.copyOf(this.left)) {
                if (!this.failed.contains(t)) {
                    this.target = t;
                    this.giveUp("no way to set it down facing right at " + t.pos().toShortString());
                }
            }
            return true;
        }

        /**
         * Where helper blocks would give the block a face to click that sets it down right: a free cell beside it (never
         * one of the build's own), and under that, down to something solid, the column that holds it up - bottom first.
         * Null if there is none short enough.
         */
        private @Nullable List<BlockPos> helperFor(Minecraft mc, Target t) {
            ItemStack wanted = new ItemStack(t.state().getBlock().asItem());
            Set<BlockPos> targets = new HashSet<>();
            for (Target o : this.left) {
                targets.add(o.pos());
            }
            List<BlockPos> best = null;
            for (Direction d : Direction.values()) {
                BlockPos n = t.pos().relative(d);
                if (targets.contains(n) || !free(mc, n) || plans(mc, t, wanted, 1, n).isEmpty()) {
                    continue;
                }
                List<BlockPos> column = new ArrayList<>();
                BlockPos at = n;
                boolean held = false;
                for (int k = 0; k <= MAX_HELPERS; k++) {
                    if (targets.contains(at) || !free(mc, at) || at.equals(t.pos())) {
                        break;
                    }
                    column.add(0, at);
                    if (supported(mc, at, t.pos(), targets)) {
                        held = true;
                        break;
                    }
                    at = at.below();
                }
                if (held && (best == null || column.size() < best.size())) {
                    best = column;
                }
            }
            return best;
        }

        private static boolean free(Minecraft mc, BlockPos p) {
            BlockState s = mc.level.getBlockState(p);
            return s.canBeReplaced() && s.getFluidState().isEmpty();
        }

        /** Whether a block set down at {@code p} has something solid beside it to be clicked on (not the cell it serves). */
        private static boolean supported(Minecraft mc, BlockPos p, BlockPos serves, Set<BlockPos> targets) {
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (n.equals(serves) || targets.contains(n) && free(mc, n)) {
                    continue;
                }
                BlockState s = mc.level.getBlockState(n);
                if (!s.canBeReplaced() && s.getFluidState().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private void start(Target t) {
            this.target = t;
            this.plan = null;
            this.goal = null;
            this.breaker = null;
            this.phaseTicks = 0;
            this.clickTicks = 0;
            this.tries = 0;
        }

        private void next() {
            if (this.target != null) {
                this.left.remove(this.target);
            }
            this.target = null;
        }

        private void giveUp(String why) {
            Bot.event("skip", why);
            this.failed.add(this.target);
            this.left.remove(this.target);
            this.target = null;
            Bot.walker.stop(Minecraft.getInstance());
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.breaker != null) {
                mc.gameMode.stopDestroyBlock();
            }
            if (this.sub != null) {
                this.sub.cancel(mc);
            }
            this.breaker = null;
            this.plan = null;
            this.goal = null;
        }
    }

    /** Breaks blocks the walker set down for its own use (bridges, pillars, helper blocks), last first. */
    static final class Cleanup implements Task {
        private final List<BlockPos> list;
        private final String what;
        private @Nullable BlockPos target;
        private @Nullable Goal goal;
        private Acts.@Nullable Breaker breaker;

        Cleanup() {
            this(Bot.walker.scaffold, "tidy up");
        }

        Cleanup(List<BlockPos> list, String what) {
            this.list = list;
            this.what = what;
        }

        @Override
        public String describe() {
            return this.what + ": " + this.list.size() + " blocks";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            List<BlockPos> list = this.list;
            if (this.target == null) {
                while (!list.isEmpty() && !Inv.throwaway(new ItemStack(mc.level.getBlockState(list.get(list.size() - 1)).getBlock().asItem()))) {
                    list.remove(list.size() - 1); // gone already, or no longer its block
                }
                if (list.isEmpty()) {
                    return Result.DONE;
                }
                this.target = list.get(list.size() - 1);
                this.goal = new ReachGoal(this.target);
                Bot.walker.go(this.goal);
                this.breaker = null;
            }
            if (this.breaker == null) {
                if (!Bot.walker.isFor(this.goal)) {
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.DONE) {
                    this.breaker = new Acts.Breaker(this.target);
                } else if (Bot.walker.state() == Walker.State.FAILED) {
                    list.remove(this.target);
                    this.target = null;
                }
                return Result.RUNNING;
            }
            if (this.breaker.tick(mc, c)) {
                list.remove(this.target);
                this.target = null;
                this.breaker = null;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }
}
