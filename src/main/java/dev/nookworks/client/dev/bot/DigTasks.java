package dev.nookworks.client.dev.bot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.dev.bot.Stairs;
import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.item.Hammers;
import dev.nookworks.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Digging its own way down: {@code bot stairdown Y [DIR]}, a stair three wide and three high down to Y, and
 * {@code bot tunnel DIR [LEN]}, a level tunnel three by three - each step one blow of the best hammer it carries (a stone
 * one made first if it has none and can), the 3x3 of the next column at once; what the hammer leaves (dirt, sand, a
 * harder block) broken a block at a time. Every step is looked at first ({@link Stairs#hazard}): lava or water in it or
 * beside it, or a build, and it turns aside, or stops and says so. A torch every 8 steps; ores in the walls mined (the
 * vein with them), unless water or lava touches the vein. The cells its feet went through are kept ({@link WayBook}):
 * trips up and down go by them ({@link MoveTasks#travel}).
 */
final class DigTasks {
    private DigTasks() {}

    static final int TORCH_EVERY = 8;

    /** {@code stairdown Y [DIR]}. */
    static Task stairDown(String[] a) {
        return new Dig(Stairs.Kind.STAIR, a.length > 2 ? direction(a[2]) : null, Integer.parseInt(a[1]), Integer.MAX_VALUE);
    }

    /** {@code tunnel DIR [LEN]}. */
    static Task tunnel(String[] a) {
        return new Dig(Stairs.Kind.TUNNEL, direction(a[1]), Integer.MIN_VALUE, a.length > 2 ? Integer.parseInt(a[2]) : 32);
    }

    private static Direction direction(String word) {
        Direction d = Stairs.direction(word);
        if (d == null) {
            throw new IllegalArgumentException("which way? north, south, east or west, not " + word);
        }
        return d;
    }

    /** The inventory index of the best hammer it carries (not worn out), -1 if none. */
    static int bestHammer(LocalPlayer player) {
        int best = -1;
        float bestSpeed = 0.0F;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.is(Hammers.TAG) && !Inv.wornOut(s)) {
                float speed = s.getDestroySpeed(Blocks.STONE.defaultBlockState());
                if (speed > bestSpeed) {
                    bestSpeed = speed;
                    best = i;
                }
            }
        }
        return best;
    }

    /** Whether a stone hammer could be made from the bag: five of stone, and wood for two sticks. */
    private static boolean canMakeHammer(LocalPlayer player) {
        return Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS)) >= 5
                && (Inv.count(player, s -> s.is(Items.STICK)) >= 2 || Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.PLANKS)) >= 2
                        || Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.LOGS)) >= 1);
    }

    private static int freeSlots(LocalPlayer player) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** What a dig brings up in heaps and is not worth carrying: all but one stack each of cobblestone, cobbled
     *  deepslate (hammers, plugs, floors) and dirt. */
    private static final Set<Item> JUNK = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT, Items.GRAVEL, Items.TUFF,
            Items.GRANITE, Items.DIORITE, Items.ANDESITE, Items.CALCITE, Items.DEEPSLATE, Items.STONE, Items.NETHERRACK);
    private static final Set<Item> KEEP_ONE = Set.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.DIRT);

    /** The inventory index of a stack of junk to throw away, -1 if none. */
    static int junkSlot(LocalPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !JUNK.contains(s.getItem()) || i == inv.getSelectedSlot()) {
                continue;
            }
            if (!KEEP_ONE.contains(s.getItem())) {
                return i;
            }
            // the fullest stack of its kind is kept
            for (int j = 0; j < 36; j++) {
                ItemStack o = inv.getItem(j);
                if (j != i && o.is(s.getItem()) && (o.getCount() > s.getCount() || o.getCount() == s.getCount() && j < i)) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** Throws the junk ({@link #junkSlot}) back up the way it came (ahead, it would walk over it and pick it up). */
    static final class ThrowJunk implements Task {
        private final Direction back;
        private int ticks;
        private int thrown;

        ThrowJunk(Direction back) {
            this.back = back;
        }

        @Override
        public String describe() {
            return "throw away dug-up stone (" + this.thrown + " stacks)";
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            int slot = junkSlot(player);
            if (slot < 0 || ++this.ticks > 200) {
                return Result.DONE;
            }
            Vec3 at = player.getEyePosition().add(this.back.getStepX() * 3.0, 0.8, this.back.getStepZ() * 3.0);
            c.lookAt(player, at);
            if (Controls.looking(player, at, 12.0F) && this.ticks % 3 == 0) {
                var menu = player.inventoryMenu;
                Inv.click(mc, menu, Inv.menuSlot(menu, player.getInventory(), slot), 1, ContainerInput.THROW);
                this.thrown++;
            }
            return Result.RUNNING;
        }
    }

    private enum Phase { START, NEXT, HAMMER, CLEAR, FLOOR, STEP, TORCH, ORES, FAIL }

    /** A screen task too: the hammer it makes on the way is crafted in a crafting table's screen (any other screen open
     *  meanwhile is shut). */
    static final class Dig implements Task, Bot.Resumable, Bot.ScreenTask {
        private final Stairs.Kind kind;
        private @Nullable Direction dir;
        private final int targetY;
        private final int length;
        private Phase phase = Phase.START;
        private @Nullable BlockPos stand;
        private @Nullable String way;
        private Stairs.@Nullable Step step;
        private int steps;
        private long startTime;
        private @Nullable Task sub;
        private boolean subMayFail;
        private Acts.@Nullable Breaker breaker;
        private @Nullable Goal stepGoal;
        private int phaseTicks;
        private int shuffle;
        /** Ticks the hand has been held still, not sneaking, before a blow. */
        private int still;
        private int settle;
        private net.minecraft.world.level.block.@Nullable Block struck;
        private int clears;
        private int stepTries;
        private int floorTries;
        private int hammerTriedAt = -1000;
        private boolean toldOneBlock;
        private @Nullable String failWhy;
        private final Set<BlockPos> skipOres = new HashSet<>();
        private MoveTasks.@Nullable Collect collect;
        private int collectTicks;

        Dig(Stairs.Kind kind, @Nullable Direction dir, int targetY, int length) {
            this.kind = kind;
            this.dir = dir;
            this.targetY = targetY;
            this.length = length;
        }

        private String name() {
            return this.kind == Stairs.Kind.STAIR ? "stair down to y=" + this.targetY : "tunnel " + this.length + " " + (this.dir == null ? "" : this.dir.getName());
        }

        @Override
        public String describe() {
            String at = this.stand == null ? "" : ", at " + this.stand.toShortString();
            return this.name() + ": " + this.steps + " steps" + at + (this.way == null ? "" : " (" + this.way + ")");
        }

        private boolean finished() {
            return this.kind == Stairs.Kind.STAIR ? this.stand.getY() <= this.targetY : this.steps >= this.length;
        }

        private Result fail(String why) {
            Bot.fail(why + " - " + this.steps + " steps dug" + (this.stand == null ? "" : ", stopped at " + this.stand.toShortString())
                    + (this.way == null ? "" : "; the way so far is kept as " + this.way));
            return Result.FAILED;
        }

        private void run(Task task, boolean mayFail) {
            this.sub = task;
            this.subMayFail = mayFail;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (mc.gui.screen() != null && !(this.sub instanceof Bot.ScreenTask)) {
                player.closeContainer(); // a stray one (a chest clicked in passing): nothing of the dig's
                return Result.RUNNING;
            }
            if (this.sub != null) {
                Result r = this.sub.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                Task done = this.sub;
                this.sub = null;
                done.cancel(mc);
                if (r == Result.FAILED && !this.subMayFail) {
                    return this.fail(done.describe() + " failed");
                }
                if (this.phase == Phase.FAIL) {
                    return this.fail(this.failWhy);
                }
                return Result.RUNNING;
            }
            if (this.collect != null) {
                if (this.collect.tick(mc, c) == Result.RUNNING && ++this.collectTicks < 200) {
                    return Result.RUNNING;
                }
                this.collect.cancel(mc);
                this.collect = null;
            }
            return switch (this.phase) {
                case START -> this.start(mc, player);
                case NEXT -> this.next(mc, player);
                case HAMMER -> this.hammer(mc, player, c);
                case CLEAR -> this.clear(mc, player, c);
                case FLOOR -> this.floor(mc, player);
                case STEP -> this.stepDown(mc, c);
                case TORCH -> this.torch(mc, player);
                case ORES -> this.ores(mc, player, c);
                case FAIL -> this.fail(this.failWhy);
            };
        }

        private Result start(Minecraft mc, LocalPlayer player) {
            if (!player.onGround() && !player.getAbilities().flying) {
                return Result.RUNNING; // landed first
            }
            BlockPos feet = Walker.feetCell(player);
            if (this.dir == null) {
                this.dir = player.getDirection();
            }
            if (this.kind == Stairs.Kind.STAIR) {
                int lowest = mc.level.getMinY() + 6;
                if (this.targetY < lowest) {
                    Bot.fail("y=" + this.targetY + " is in or by the bedrock: y=" + lowest + " at the lowest");
                    return Result.FAILED;
                }
                if (feet.getY() <= this.targetY) {
                    Bot.fail("already at y=" + feet.getY() + ", not above y=" + this.targetY);
                    return Result.FAILED;
                }
            }
            this.stand = feet;
            this.startTime = mc.level.getGameTime();
            this.way = WayBook.ways(mc).begin(this.kind == Stairs.Kind.STAIR ? "stair" : "tunnel", feet);
            WayBook.save(mc);
            Bot.event("dig", this.name() + " from " + feet.toShortString() + " going " + this.dir.getName() + " (" + this.way + ")");
            this.phase = Phase.NEXT;
            if (bestHammer(player) < 0 && !player.getAbilities().instabuild) {
                if (canMakeHammer(player)) {
                    this.hammerTriedAt = 0;
                    Bot.event("dig", "no hammer: making a stone one");
                    this.run(new Crafting.Craft(ModItems.STONE_HAMMER.get(), 1), true);
                } else {
                    Bot.event("dig", "no hammer, nor what makes one (5 stone, wood): a block at a time");
                }
            }
            return Result.RUNNING;
        }

        private Result next(Minecraft mc, LocalPlayer player) {
            if (this.finished()) {
                long secs = (mc.level.getGameTime() - this.startTime) / 20;
                Bot.event("dig", this.name() + " done: " + this.steps + " steps in " + secs + " s, at " + this.stand.toShortString()
                        + " (" + this.way + ")");
                return Result.DONE;
            }
            BlockPos feet = Walker.feetCell(player);
            if (!feet.equals(this.stand)) {
                // put off its stand (a fight, a fall): back to it first
                this.run(new MoveTasks.Goto(Goal.at(this.stand), "back to the dig at " + this.stand.toShortString()), false);
                return Result.RUNNING;
            }
            if (freeSlots(player) <= 3 && junkSlot(player) >= 0) {
                this.run(new ThrowJunk(this.dir.getOpposite()), true);
                return Result.RUNNING;
            }
            if (bestHammer(player) < 0 && !player.getAbilities().instabuild && this.steps - this.hammerTriedAt >= 16 && canMakeHammer(player)) {
                this.hammerTriedAt = this.steps;
                Bot.event("dig", "the hammer is worn out: making a stone one");
                this.run(new Crafting.Craft(ModItems.STONE_HAMMER.get(), 1), true);
                return Result.RUNNING;
            }
            Stairs.Step s = Stairs.next(this.kind, this.stand, this.dir);
            Stairs.Hazard h = this.trouble(mc, s);
            if (h != null) {
                Stairs.Step turned = null;
                for (Direction alt : new Direction[] {this.dir.getClockWise(), this.dir.getCounterClockWise()}) {
                    Stairs.Step t = Stairs.next(this.kind, this.stand, alt);
                    if (this.trouble(mc, t) == null) {
                        turned = t;
                        break;
                    }
                }
                if (turned == null && h.what().equals("a drop")) {
                    turned = s; // nothing better to the sides: a floor set down over it
                } else if (turned == null) {
                    return this.fail(h.what() + " at " + h.pos().toShortString() + " ahead, and to both sides");
                } else {
                    Bot.event("dig", h.what() + " at " + h.pos().toShortString() + " ahead: turned " + turned.dir().getName());
                    this.dir = turned.dir();
                }
                s = turned;
            }
            this.step = s;
            this.phase = Phase.HAMMER;
            this.phaseTicks = 0;
            this.shuffle = 0;
            this.still = 0;
            this.settle = 0;
            this.struck = null;
            this.clears = 0;
            this.floorTries = 0;
            this.stepTries = 0;
            return Result.RUNNING;
        }

        /** What keeps a step from being dug ({@link Stairs#hazard}), or a deep drop under its floor. */
        private Stairs.@Nullable Hazard trouble(Minecraft mc, Stairs.Step s) {
            Stairs.Hazard h = Stairs.hazard(mc.level, s, (state, pos) -> mayDig(mc, state, pos));
            if (h == null && Stairs.needsFloor(mc.level, s) && Stairs.dropUnder(mc.level, s, 4) >= 4) {
                return new Stairs.Hazard("a drop", s.floor());
            }
            return h;
        }

        private Result hammer(Minecraft mc, LocalPlayer player, Controls c) {
            Stairs.Step s = this.step;
            BlockState aim = mc.level.getBlockState(s.aim());
            int hammer = bestHammer(player);
            if (this.struck != null) {
                aim = this.struck.defaultBlockState(); // struck already: waiting on the rest of the 3x3
            }
            if (Stairs.passable(mc.level, s.aim(), aim) && this.struck == null || hammer < 0 || player.getAbilities().instabuild || !Stairs.hammerTakes(aim)
                    || !player.getInventory().getItem(hammer).isCorrectToolForDrops(aim)) {
                this.phase = Phase.CLEAR; // nothing for a blow to take: a block at a time
                return Result.RUNNING;
            }
            // in the middle of its cell, so the eyes see the face straight on
            double off = Math.hypot(this.stand.getX() + 0.5 - player.getX(), this.stand.getZ() + 0.5 - player.getZ());
            if (off > 0.3 && this.shuffle++ < 40) {
                Walker.shuffleToward(player, c, this.stand.getX() + 0.5, this.stand.getZ() + 0.5);
                return Result.RUNNING;
            }
            if (!player.getMainHandItem().is(Hammers.TAG) || Inv.wornOut(player.getMainHandItem())) {
                Inv.hold(mc, hammer);
            }
            boolean sneak = Stairs.sneakForHammer(NookworksConfig.HAMMER_ACTIVATION.get());
            c.sneak = sneak; // standing, by default: sneaking, the hammer takes one block
            if (this.struck == null) {
                this.struck = aim.getBlock();
            }
            Vec3 point = Stairs.aimPoint(s);
            c.lookAt(player, point);
            if (++this.phaseTicks > 600) {
                mc.gameMode.stopDestroyBlock();
                this.phase = Phase.CLEAR;
                return Result.RUNNING;
            }
            // the sneak let go of (or held) a few ticks before the blow, so the server has it too (the shuffle into the
            // cell's middle sneaks)
            this.still = player.isShiftKeyDown() == sneak ? this.still + 1 : 0;
            boolean standing = !Stairs.passable(mc.level, s.aim(), mc.level.getBlockState(s.aim()));
            if (standing && this.still >= 4 && player.getMainHandItem().is(Hammers.TAG) && Controls.looking(player, point, 2.5F)
                    && Acts.inReach(player, point)) {
                mc.gameMode.continueDestroyBlock(s.aim(), s.face());
                Acts.swing(mc, true);
            }
            if (standing) {
                return Result.RUNNING;
            }
            mc.gameMode.stopDestroyBlock();
            int left = 0;
            for (BlockPos p : s.open()) {
                if (mc.level.getBlockState(p).is(this.struck)) {
                    left++; // the same rock as the one struck: the 3x3 takes it
                }
            }
            // the middle goes at once on the client, the rest as the server's word of them comes: a few ticks (judged at
            // once, "the hammer took one block" was said of blows that took all nine)
            if (left > 0 && ++this.settle < 10) {
                return Result.RUNNING;
            }
            if (left >= 4 && !this.toldOneBlock) {
                this.toldOneBlock = true;
                Bot.event("dig", "the hammer took one block only (" + left + " of rock left round " + Bot.id(this.struck) + " at "
                        + s.aim().toShortString() + ", sneaking " + player.isShiftKeyDown() + "): is hammer.activation set otherwise?");
            }
            this.phase = Phase.CLEAR;
            return Result.RUNNING;
        }

        private Result clear(Minecraft mc, LocalPlayer player, Controls c) {
            Stairs.Step s = this.step;
            if (this.breaker != null) {
                if (!this.breaker.tick(mc, c)) {
                    return Result.RUNNING;
                }
                String why = this.breaker.failure();
                this.breaker = null;
                if (why != null) {
                    if (why.startsWith("out of reach")) {
                        this.phase = Phase.NEXT; // put off its stand meanwhile
                        return Result.RUNNING;
                    }
                    return this.fail(why);
                }
            }
            // what was dug let something in after all (a source the look missed, gravel with water over it): back up
            // the way it came, and stop
            Stairs.Hazard h = Stairs.hazard(mc.level, s, (state, pos) -> true);
            if (h != null && h.fluid()) {
                this.failWhy = h.what() + " came in at " + h.pos().toShortString();
                this.phase = Phase.FAIL;
                List<net.minecraft.core.BlockPos> cells = WayBook.ways(mc).all().get(this.way);
                BlockPos back = cells != null && cells.size() >= 3 ? cells.get(cells.size() - 3) : this.stand;
                this.run(new MoveTasks.Goto(Goal.near(back, 0.5), "back up from the " + h.what()), true);
                return Result.RUNNING;
            }
            for (BlockPos p : s.open()) {
                if (!Stairs.passable(mc.level, p, mc.level.getBlockState(p))) {
                    if (++this.clears > 60) {
                        return this.fail("could not clear the step at " + p.toShortString());
                    }
                    this.breaker = new Acts.Breaker(p);
                    return Result.RUNNING;
                }
            }
            this.phase = Phase.FLOOR;
            return Result.RUNNING;
        }

        private Result floor(Minecraft mc, LocalPlayer player) {
            Stairs.Step s = this.step;
            if (!Stairs.needsFloor(mc.level, s) || player.getAbilities().instabuild) {
                this.phase = Phase.STEP;
                return Result.RUNNING;
            }
            int block = Inv.find(player, Inv::throwaway);
            if (block < 0 || !(player.getInventory().getItem(block).getItem() instanceof BlockItem item)) {
                return this.fail("no block to set down for a floor at " + s.floor().toShortString());
            }
            if (++this.floorTries > 3) {
                return this.fail("could not set a floor down at " + s.floor().toShortString());
            }
            this.run(new WorkTasks.Build(List.of(new WorkTasks.Target(s.floor(), item.getBlock().defaultBlockState())),
                    "a floor at " + s.floor().toShortString()), true);
            return Result.RUNNING;
        }

        private Result stepDown(Minecraft mc, Controls c) {
            Stairs.Step s = this.step;
            if (this.stepGoal == null || !Bot.walker.isFor(this.stepGoal)) {
                this.stepGoal = Goal.at(s.to());
                Bot.walker.go(this.stepGoal);
            }
            Bot.walker.tick(mc, c);
            switch (Bot.walker.state()) {
                case DONE -> {
                    this.stepGoal = null;
                    this.stand = s.to();
                    this.steps++;
                    WayBook.ways(mc).append(this.way, this.stand);
                    WayBook.save(mc);
                    if (this.steps % 8 == 0) {
                        Bot.event("dig", this.name() + ": " + this.steps + " steps, at " + this.stand.toShortString() + ", "
                                + (mc.level.getGameTime() - this.startTime) / 20 + " s");
                    }
                    this.phase = Phase.TORCH;
                }
                case FAILED -> {
                    this.stepGoal = null;
                    if (++this.stepTries > 2) {
                        return this.fail("could not step to " + s.to().toShortString() + ": " + Bot.walker.failure());
                    }
                    this.phase = Phase.NEXT;
                }
                default -> {
                }
            }
            return Result.RUNNING;
        }

        private Result torch(Minecraft mc, LocalPlayer player) {
            this.phase = Phase.ORES;
            if (this.steps % TORCH_EVERY != 0 || Inv.find(player, st -> st.is(Items.TORCH)) < 0) {
                return Result.RUNNING;
            }
            // on the floor to one side, out of the way
            for (Direction side : new Direction[] {this.dir.getClockWise(), this.dir.getCounterClockWise()}) {
                BlockPos t = this.stand.relative(side);
                if (mc.level.getBlockState(t).isAir() && mc.level.getBlockState(t.below()).isFaceSturdy(mc.level, t.below(), Direction.UP)) {
                    Bot.torchSetAt(t);
                    this.run(new WorkTasks.Build(List.of(new WorkTasks.Target(t, Blocks.TORCH.defaultBlockState())), "a torch at " + t.toShortString()), true);
                    return Result.RUNNING;
                }
            }
            return Result.RUNNING;
        }

        /** Ores showing round the step just dug: mined, vein and all (Nookworks' vein mining, a pickaxe in hand), unless
         *  the vein touches water or lava, or holds up the floor. */
        private Result ores(Minecraft mc, LocalPlayer player, Controls c) {
            if (this.breaker != null) {
                if (!this.breaker.tick(mc, c)) {
                    return Result.RUNNING;
                }
                if (this.breaker.failure() == null) {
                    this.collect = new MoveTasks.Collect(5.0, null);
                    this.collectTicks = 0;
                } else {
                    this.skipOres.add(this.breaker.pos);
                }
                this.breaker = null;
                return Result.RUNNING;
            }
            for (BlockPos ore : Stairs.ores(mc.level, this.step)) {
                if (this.skipOres.contains(ore)) {
                    continue;
                }
                List<BlockPos> vein = Stairs.vein(mc.level, ore, 64);
                BlockPos under = this.stand.below();
                if (!Stairs.veinSafe(mc.level, vein) || vein.contains(under) || vein.contains(this.step.floor())) {
                    this.skipOres.addAll(vein);
                    Bot.event("dig", "left the " + Bot.id(mc.level.getBlockState(ore).getBlock()) + " at " + ore.toShortString()
                            + (vein.contains(under) || vein.contains(this.step.floor()) ? ": it holds the floor up" : ": water or lava by it"));
                    continue;
                }
                if (Inv.bestTool(player, mc.level.getBlockState(ore)) == -2) {
                    this.skipOres.addAll(vein);
                    Bot.event("dig", "left the " + Bot.id(mc.level.getBlockState(ore).getBlock()) + " at " + ore.toShortString() + ": no pickaxe good enough");
                    continue;
                }
                this.skipOres.add(ore);
                Bot.event("dig", "ore in the wall: " + Bot.id(mc.level.getBlockState(ore).getBlock()) + " at " + ore.toShortString()
                        + (vein.size() > 1 ? " (a vein of " + vein.size() + ")" : ""));
                this.breaker = new Acts.Breaker(ore, true);
                return Result.RUNNING;
            }
            this.phase = Phase.NEXT;
            return Result.RUNNING;
        }

        /** Whether a block in the way may be dug: what the walker may break on its way, ores, and the deep's rock. */
        private static boolean mayDig(Minecraft mc, BlockState state, BlockPos pos) {
            if (state.hasBlockEntity() || state.getDestroySpeed(mc.level, pos) < 0 || state.is(net.minecraft.tags.BlockTags.BEDS)) {
                return false;
            }
            if (Bot.mayBreak(mc, state, pos)) {
                return true;
            }
            if (Stairs.deepRock(state) && !dev.nookworks.dev.bot.BotRules.underBuild(mc.level, pos, Bot::natural)) {
                return true;
            }
            // well under the surface, all that a pickaxe or shovel digs: the deep has rock of kinds the walker does not
            // know (26.3's sulfur stopped the first stair at y=8, 2026-10-22), and cobblestone of lava meeting water
            int top = mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
            return pos.getY() < top - 8 && !state.is(dev.nookworks.feature.HammerMining.SPARES)
                    && (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) || state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL));
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.breaker != null || this.phase == Phase.HAMMER) {
                mc.gameMode.stopDestroyBlock();
            }
            if (this.sub != null) {
                this.sub.cancel(mc);
                this.sub = null;
            }
            this.breaker = null;
            this.stepGoal = null;
            if (this.collect != null) {
                this.collect.cancel(mc);
                this.collect = null;
            }
        }
    }
}
