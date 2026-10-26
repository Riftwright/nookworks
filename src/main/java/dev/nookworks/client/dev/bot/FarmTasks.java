package dev.nookworks.client.dev.bot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Farming, and an item used on a block: {@code till}, {@code plant}, {@code harvest}, {@code useon}. The user's first
 * wish for NookAI's days on its own (2026-10-22): a farm for food. Each walks within reach of a block, takes the item
 * into the hand and right-clicks the block's face, as a person farms.
 */
final class FarmTasks {
    private FarmTasks() {}

    /** Within reach of a block, never standing on it (farmland is trampled; a crop is in the way), and where the eyes
     *  see its top face - a portal's bottom block seen from beside the frame is behind a pillar, and the click went to a
     *  side. */
    static Goal reach(BlockPos block) {
        Goal near = new WorkTasks.ReachGoal(block);
        var level = Minecraft.getInstance().level;
        var shape = level.getBlockState(block).getShape(level, block);
        double height = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
        Vec3 top = new Vec3(block.getX() + 0.5, block.getY() + height - 0.05, block.getZ() + 0.5);
        return new Goal() {
            @Override
            public boolean reached(int x, int y, int z) {
                if (!near.reached(x, y, z) || x == block.getX() && z == block.getZ()) {
                    return false;
                }
                Vec3 eye = new Vec3(x + 0.5, y + 1.62, z + 0.5);
                if (eye.y <= top.y + 0.05) {
                    return true; // from below the top: whatever face it sees will do
                }
                BlockHitResult hit = level.clip(new ClipContext(eye, top, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        net.minecraft.world.phys.shapes.CollisionContext.empty()));
                return hit.getType() != HitResult.Type.BLOCK || hit.getBlockPos().equals(block);
            }

            @Override
            public double estimate(int x, int y, int z) {
                return near.estimate(x, y, z);
            }
        };
    }

    /** The top face's middle if the eyes see it, else the face they see best. The top as the block's shape has it:
     *  farmland's is a sixteenth low, and a look at a full block's top went past it onto a side. */
    static Acts.Aim aimTop(LocalPlayer player, BlockPos pos) {
        var shape = player.level().getBlockState(pos).getShape(player.level(), pos);
        double height = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
        Vec3 top = new Vec3(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
        Vec3 eye = player.getEyePosition();
        if (eye.y > top.y) {
            // solid blocks only: the seedlings already set in the rows in front are no wall (their outlines took the look)
            BlockHitResult hit = player.level().clip(new ClipContext(eye, top.add(0, -0.05, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, player));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos) && hit.getDirection() == Direction.UP) {
                return new Acts.Aim(hit.getLocation(), Direction.UP);
            }
        }
        return Acts.aimAt(player, pos);
    }

    /**
     * Blocks one after another, nearest first: walk within reach, take the item, click. A block is done when
     * {@link #done} says so; one it cannot get to, or that does not take, is left.
     */
    abstract static class OnBlocks implements Task, Bot.Resumable {
        final String what;
        final List<BlockPos> left;
        final Set<BlockPos> failed = new HashSet<>();
        @Nullable BlockPos target;
        @Nullable Goal goal;
        int tries;
        int count;

        OnBlocks(String what, List<BlockPos> targets) {
            this.what = what;
            this.left = new ArrayList<>(targets);
        }

        @Override
        public String describe() {
            return this.what + " (" + this.count + " done, " + this.left.size() + " left"
                    + (this.failed.isEmpty() ? "" : ", " + this.failed.size() + " could not be") + ")";
        }

        /** Already as wanted. */
        abstract boolean done(Minecraft mc, BlockPos p);

        /** Still worth doing (the world may have changed). */
        abstract boolean still(Minecraft mc, BlockPos p);

        /** Takes the item into the hand; false if there is none (the failure said). */
        abstract boolean hold(Minecraft mc);

        /** What the hand must hold for the click (it may take a tick to come from the bag). */
        abstract Predicate<ItemStack> tool();

        /** Before the click: clear the way (true while at it). */
        boolean prepare(Minecraft mc, Controls c, BlockPos p) {
            return false;
        }

        /** The click itself. */
        void click(Minecraft mc, BlockPos p, Acts.Aim aim) {
            Acts.useOn(mc, p, aim.face(), aim.point());
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.target == null) {
                this.left.removeIf(p -> {
                    if (this.done(mc, p)) {
                        return true;
                    }
                    return !this.still(mc, p);
                });
                BlockPos best = null;
                for (BlockPos p : this.left) {
                    if (!this.failed.contains(p) && (best == null || p.distSqr(player.blockPosition()) < best.distSqr(player.blockPosition()))) {
                        best = p;
                    }
                }
                if (best == null) {
                    if (this.count == 0 && !this.failed.isEmpty()) {
                        Bot.fail(this.failed.size() + " could not be done, first at " + this.failed.iterator().next().toShortString());
                        return Result.FAILED;
                    }
                    return Result.DONE;
                }
                this.target = best;
                this.goal = reach(best);
                this.tries = 0;
                Bot.walker.go(this.goal);
            }
            BlockPos t = this.target;
            if (this.done(mc, t)) {
                this.count++;
                this.left.remove(t);
                this.target = null;
                return Result.RUNNING;
            }
            if (!this.still(mc, t)) {
                this.left.remove(t);
                this.target = null;
                return Result.RUNNING;
            }
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    this.failed.add(t);
                    this.target = null;
                }
                return Result.RUNNING;
            }
            if (++this.tries > 80) {
                Bot.event("skip", this.what + ": did not take at " + t.toShortString());
                this.failed.add(t);
                this.target = null;
                return Result.RUNNING;
            }
            if (this.prepare(mc, c, t)) {
                return Result.RUNNING;
            }
            if (!this.hold(mc)) {
                if (this.count > 0) {
                    Bot.event("skip", this.what + ": ran out after " + this.count);
                    return Result.DONE; // as far as it went: not a failure that drops the jobs after it
                }
                return Result.FAILED;
            }
            if (!this.tool().test(player.getMainHandItem()) && !player.getAbilities().instabuild) {
                return Result.RUNNING;
            }
            Acts.Aim aim = aimTop(player, t);
            if (!Acts.inReach(player, aim.point())) {
                Bot.walker.go(this.goal); // stood off a little: closer
                return Result.RUNNING;
            }
            c.sneak = true; // never opens what it clicks on
            c.lookAt(player, aim.point());
            if (Controls.looking(player, aim.point(), 3.0F) && this.tries % 5 == 0) {
                this.click(mc, t, aim);
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            this.target = null;
        }
    }

    /** Takes an item of the kind into the hand; false (the failure said) if it has none. */
    static boolean holdOf(Minecraft mc, Predicate<ItemStack> kind, ItemStack creative, String none) {
        if (Inv.holdItem(mc, kind, creative) && kind.test(mc.player.getMainHandItem())) {
            return true;
        }
        if (Inv.find(mc.player, kind) >= 0) {
            return true; // being taken into the hand: next tick (the click waits for it)
        }
        Bot.fail(none);
        return false;
    }

    /** {@code till X Y Z [R] [dry]}: the grass and dirt within R of the spot made farmland - only where water is within
     *  4, so it stays wet (unless {@code dry}). */
    static Task till(Minecraft mc, String[] a) {
        BlockPos at = Bot.pos(a, 1);
        int r = a.length > 4 ? Integer.parseInt(a[4]) : 4;
        boolean dry = a.length > 5 && a[5].equals("dry");
        List<BlockPos> targets = BotRules.tillTargets(mc.level, at, r, dry);
        if (targets.isEmpty()) {
            boolean anyDry = !BotRules.tillTargets(mc.level, at, r, true).isEmpty();
            if (anyDry && BotRules.tillable(mc.level, at) && Inv.find(mc.player, s -> s.is(net.minecraft.world.item.Items.WATER_BUCKET)) >= 0) {
                // no water near: a hole dug in the middle and the bucket poured into it, then the ground round it
                Bot.event("farm", "no water within 4: a water hole at " + at.toShortString() + " first");
                return new Chain("till round a water hole at " + at.toShortString(), List.of(
                        () -> new BreakAt(at),
                        () -> new UseOn(net.minecraft.world.item.Items.WATER_BUCKET, at.below()),
                        () -> tillAll(mc, BotRules.tillTargets(mc.level, at, r, false))));
            }
            throw new IllegalArgumentException(anyDry
                    ? "no ground within " + r + " of " + at.toShortString() + " has water within 4: carry a water bucket"
                            + " (it digs a water hole in the middle), or till it dry (bot till X Y Z R dry)"
                    : "no grass or dirt with room above within " + r + " of " + at.toShortString());
        }
        return tillAll(mc, targets);
    }

    private static Task tillAll(Minecraft mc, List<BlockPos> targets) {
        return new OnBlocks("till " + targets.size(), targets) {
            @Override
            boolean done(Minecraft mc, BlockPos p) {
                return mc.level.getBlockState(p).getBlock() instanceof FarmlandBlock;
            }

            @Override
            boolean still(Minecraft mc, BlockPos p) {
                return BotRules.tillable(mc.level, p);
            }

            @Override
            boolean prepare(Minecraft mc, Controls c, BlockPos p) {
                // short grass on it: broken first (a hand does)
                BlockPos above = p.above();
                BlockState s = mc.level.getBlockState(above);
                if (s.isAir()) {
                    return false;
                }
                Acts.Aim aim = Acts.aimAt(mc.player, above);
                c.lookAt(mc.player, aim.point());
                if (Controls.looking(mc.player, aim.point(), 5.0F) && this.tries % 5 == 0) {
                    mc.gameMode.startDestroyBlock(above, aim.face());
                    Acts.swing(mc, true);
                }
                return true;
            }

            @Override
            boolean hold(Minecraft mc) {
                return holdOf(mc, this.tool(), new ItemStack(net.minecraft.world.item.Items.IRON_HOE),
                        "no hoe (bot craft minecraft:wooden_hoe)");
            }

            @Override
            Predicate<ItemStack> tool() {
                return s -> s.is(ItemTags.HOES);
            }
        };
    }

    /** Tasks one after another, each made when its turn comes (so it sees the world the earlier ones left); fails with
     *  the first that fails. */
    static final class Chain implements Task, Bot.Resumable, Bot.Wrapper {
        private final String what;
        private final List<java.util.function.Supplier<Task>> steps;
        private int at;
        private @Nullable Task now;

        Chain(String what, List<java.util.function.Supplier<Task>> steps) {
            this.what = what;
            this.steps = steps;
        }

        @Override
        public @Nullable Task inner() {
            return this.now;
        }

        @Override
        public String describe() {
            // one step: its own words once it is made (the line it came from until then)
            if (this.steps.size() == 1 && this.now != null) {
                return this.now.describe();
            }
            return this.what + (this.now == null ? "" : ": " + this.now.describe());
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.now == null) {
                if (this.at >= this.steps.size()) {
                    return Result.DONE;
                }
                try {
                    this.now = this.steps.get(this.at++).get();
                } catch (IllegalArgumentException e) {
                    Bot.fail(e.getMessage());
                    return Result.FAILED;
                }
            }
            Result r = this.now.tick(mc, c);
            if (r == Result.RUNNING) {
                return r;
            }
            this.now.cancel(mc);
            this.now = null;
            return r == Result.FAILED ? Result.FAILED : Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            if (this.now != null) {
                this.now.cancel(mc);
            }
        }
    }

    /** One block broken, from within reach (any block: asked for by name and place). */
    static final class BreakAt implements Task {
        private final BlockPos pos;
        private final Goal goal;
        private Acts.@Nullable Breaker breaker;

        BreakAt(BlockPos pos) {
            this.pos = pos;
            this.goal = reach(pos);
        }

        @Override
        public String describe() {
            return "break " + this.pos.toShortString();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.breaker == null) {
                if (!Bot.walker.isFor(this.goal)) {
                    Bot.walker.go(this.goal);
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    Bot.walker.tick(mc, c);
                    if (Bot.walker.state() == Walker.State.FAILED) {
                        Bot.fail("cannot get within reach: " + Bot.walker.failure());
                        return Result.FAILED;
                    }
                    return Result.RUNNING;
                }
                this.breaker = new Acts.Breaker(this.pos);
            }
            if (!this.breaker.tick(mc, c)) {
                return Result.RUNNING;
            }
            if (this.breaker.failure() != null) {
                Bot.fail(this.breaker.failure());
                return Result.FAILED;
            }
            return Result.DONE;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.breaker != null) {
                mc.gameMode.stopDestroyBlock();
            }
        }
    }

    /** {@code plant ID X Y Z [R]}: seeds (carrots, potatoes...) set on the bare farmland within R of the spot. */
    static Task plant(Minecraft mc, String[] a) {
        Item seed = Bot.item(a[1]);
        BlockPos at = Bot.pos(a, 2);
        int r = a.length > 5 ? Integer.parseInt(a[5]) : 4;
        List<BlockPos> targets = BotRules.plantTargets(mc.level, at, r);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("no bare farmland within " + r + " of " + at.toShortString() + " (bot till first)");
        }
        return new OnBlocks("plant " + Bot.name(seed), targets) {
            @Override
            boolean done(Minecraft mc, BlockPos p) {
                return !mc.level.getBlockState(p.above()).isAir();
            }

            @Override
            boolean still(Minecraft mc, BlockPos p) {
                return mc.level.getBlockState(p).getBlock() instanceof FarmlandBlock;
            }

            @Override
            boolean hold(Minecraft mc) {
                return holdOf(mc, this.tool(), new ItemStack(seed), "no " + Bot.name(seed) + " left");
            }

            @Override
            Predicate<ItemStack> tool() {
                return s -> s.is(seed);
            }
        };
    }

    /**
     * {@code harvest [R]}: every ripe crop within R picked - broken, its seed set again on the farmland - and what
     * dropped picked up.
     */
    static final class Harvest implements Task, Bot.Resumable {
        private final int radius;
        private final Set<BlockPos> failed = new HashSet<>();
        private @Nullable BlockPos target;
        private @Nullable Block crop;
        private @Nullable Goal goal;
        private int phase;
        private int tries;
        private int picked;
        private int replanted;
        private MoveTasks.@Nullable Collect collect;

        Harvest(int radius) {
            this.radius = radius;
        }

        @Override
        public String describe() {
            return "harvest (" + this.picked + " picked, " + this.replanted + " set again)";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.collect != null) {
                return this.collect.tick(mc, c);
            }
            if (this.target == null) {
                BlockPos next = null;
                for (BlockPos p : BotRules.ripeCrops(mc.level, player.blockPosition(), this.radius)) {
                    if (!this.failed.contains(p)) {
                        next = p;
                        break;
                    }
                }
                if (next == null) {
                    if (this.picked == 0) {
                        Bot.fail("nothing ripe within " + this.radius);
                        return Result.FAILED;
                    }
                    this.collect = new MoveTasks.Collect(this.radius + 3.0, null);
                    return Result.RUNNING;
                }
                this.target = next;
                this.crop = mc.level.getBlockState(next).getBlock();
                this.goal = reach(next.below()); // in sight of the soil's top: where the seed goes again
                this.phase = 0;
                this.tries = 0;
                Bot.walker.go(this.goal);
            }
            BlockPos t = this.target;
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    this.failed.add(t);
                    this.target = null;
                }
                return Result.RUNNING;
            }
            if (++this.tries > 100) {
                this.failed.add(t);
                this.target = null;
                return Result.RUNNING;
            }
            if (this.phase == 0) {
                // pick it: a crop breaks at a touch
                if (!BotRules.ripe(mc.level.getBlockState(t))) {
                    this.picked++;
                    this.phase = 1;
                    this.tries = 0;
                    return Result.RUNNING;
                }
                Acts.Aim aim = Acts.aimAt(player, t);
                c.lookAt(player, aim.point());
                if (Controls.looking(player, aim.point(), 5.0F) && this.tries % 4 == 0) {
                    Inv.select(mc, Inv.emptyHandSlot(player));
                    mc.gameMode.startDestroyBlock(t, aim.face());
                    Acts.swing(mc, true);
                }
                return Result.RUNNING;
            }
            // set its seed again on the farmland
            BlockPos soil = t.below();
            Item seed = this.crop == null ? net.minecraft.world.item.Items.AIR : this.crop.asItem();
            if (!mc.level.getBlockState(t).isAir() || !(mc.level.getBlockState(soil).getBlock() instanceof FarmlandBlock)
                    || seed == net.minecraft.world.item.Items.AIR || Inv.find(player, s -> s.is(seed)) < 0 && !player.getAbilities().instabuild) {
                if (!mc.level.getBlockState(t).isAir() && this.phase == 2) {
                    this.replanted++;
                }
                this.target = null;
                return Result.RUNNING;
            }
            if (!holdOf(mc, s -> s.is(seed), new ItemStack(seed), "no seed") || !player.getMainHandItem().is(seed)) {
                return Result.RUNNING;
            }
            Acts.Aim aim = aimTop(player, soil);
            c.sneak = true;
            c.lookAt(player, aim.point());
            if (Controls.looking(player, aim.point(), 3.0F) && this.tries % 5 == 0) {
                Acts.useOn(mc, soil, aim.face(), aim.point());
                this.phase = 2;
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.collect != null) {
                this.collect.cancel(mc);
            }
            this.target = null;
        }
    }

    /**
     * {@code useon ID X Y Z}: the item used on the block once, from where it can reach it - flint and steel on a portal's
     * obsidian, bone meal on a crop, a bucket on water or on a face to pour. Done when something there changes (the block,
     * the one by the face, or what is in the hand).
     */
    static final class UseOn implements Task {
        private final Item item;
        private final BlockPos pos;
        private final Goal goal;
        private @Nullable String before;
        private int tries;

        UseOn(Item item, BlockPos pos) {
            this.item = item;
            this.pos = pos;
            this.goal = reach(pos);
        }

        @Override
        public String describe() {
            return "use " + Bot.name(this.item) + " on " + this.pos.toShortString();
        }

        private String snapshot(Minecraft mc) {
            ItemStack hand = mc.player.getMainHandItem();
            StringBuilder s = new StringBuilder();
            for (BlockPos p : new BlockPos[] {this.pos, this.pos.above(), this.pos.north(), this.pos.south(), this.pos.east(), this.pos.west()}) {
                s.append(mc.level.getBlockState(p)).append(';');
            }
            return s.append(Bot.name(hand.getItem())).append('x').append(hand.getCount()).append('d').append(hand.getDamageValue()).toString();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (!Bot.walker.isFor(this.goal)) {
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get within reach: " + Bot.walker.failure());
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            if (this.before != null && !this.before.equals(this.snapshot(mc))) {
                return Result.DONE; // (a bucket poured is a bucket in the hand: checked before taking another)
            }
            if (!holdOf(mc, s -> s.is(this.item), new ItemStack(this.item), "no " + Bot.name(this.item))) {
                return Result.FAILED;
            }
            if (!player.getMainHandItem().is(this.item)) {
                return Result.RUNNING;
            }
            if (this.before == null) {
                this.before = this.snapshot(mc);
            }
            if (++this.tries > 100) {
                Bot.fail("nothing came of it");
                return Result.FAILED;
            }
            Acts.Aim aim = aimTop(player, this.pos);
            c.sneak = true;
            c.lookAt(player, aim.point());
            if (Controls.looking(player, aim.point(), 3.0F) && this.tries % 10 == 5) {
                if (this.item instanceof BucketItem || !mc.level.getFluidState(this.pos).isEmpty()) {
                    Acts.useItem(mc); // a bucket acts along the look (a fluid has no face to click)
                    Acts.swing(mc, false);
                } else {
                    Acts.useOn(mc, this.pos, aim.face(), aim.point());
                }
            }
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }
}
