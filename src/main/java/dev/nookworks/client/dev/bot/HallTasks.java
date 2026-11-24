package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.dev.bot.Hall;
import dev.nookworks.dev.bot.Stairs;
import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.item.Hammers;
import dev.nookworks.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * {@code bot excavate X1 Y1 Z1 X2 Y2 Z2 [FORWARD SIDE]}: a hall dug out of the box, from the top down, alone for hours
 * (the plan is {@link Hall}'s). Each move is walked to, looked at (lava by it: then a block at a time), struck with the
 * best hammer it carries, cleared of what the blow left, and looked round: water, lava and holes round what it opened
 * filled with a block ({@link Hall#toSeal}), a floor set where there is none, a torch on the grid. Each layer done is
 * looked over (what fell in, what was left, a dark spot) before the stair down to the next.
 * <p>
 * Between moves it keeps itself going: stone hammers made when the last wears out (three at a time; the crafting table
 * it sets down picked up again, so none is left floating in the hall), torches made from coal it digs, the dug-up stone
 * thrown away when the bag fills (ores, coal, food, tools and three stacks of stone to fill with are kept), a rest when
 * badly hurt. Where it got to is kept in {@code bot_hall/<world>.txt}, so {@code bot excavate resume} carries on after a
 * client start; events of type {@code hall} say how it goes (the numbers for the video: blocks dug, ores, water and lava
 * shut off, holes filled, torches).
 * <p>
 * While a hall is under way the walker breaks blocks only inside it and in the end wall its way in comes through
 * ({@link Hall#mayDig}), the dusk walk home and the night's call indoors leave it be, and a retreat stays in the hall.
 */
final class HallTasks {
    private HallTasks() {}

    /** The hall being dug, while its job runs or waits in the queue: the walker digs only in it. */
    static @Nullable Hall active;

    /** Stone hammers made at a time. */
    static final int HAMMER_BATCH = 3;
    /** Stacks of stone kept to fill water, lava and holes with. */
    static final int SEAL_STACKS = 3;

    /** Whether the job is a hall's dig (or one waiting in the queue to be made). */
    /** Whether the player is in the hall under way (the box, or the end wall its way in comes through). */
    static boolean inHall(Minecraft mc) {
        Hall h = active;
        if (h == null || mc.player == null) {
            return false;
        }
        BlockPos feet = Walker.feetCell(mc.player);
        return h.mayDig(feet) || h.mayDig(feet.above());
    }

    /** A Build that stands only on the hall's floor (or in the way in), never pillars, sets no helper blocks. */
    static WorkTasks.Build build(Hall h, List<WorkTasks.Target> targets, String what) {
        return new WorkTasks.Build(targets, what).only(p -> h.box.contains(p) || h.doorway(p));
    }

    static boolean digging(@Nullable Task t) {
        return t instanceof Excavate || t instanceof Bot.Later l && l.describe().startsWith("excavate");
    }

    /** {@code excavate X1 Y1 Z1 X2 Y2 Z2 [FORWARD SIDE] | resume | plan X1 Y1 Z1 X2 Y2 Z2 [FORWARD SIDE]}. */
    static @Nullable Task command(Minecraft mc, String[] a) {
        if (a.length < 2) {
            throw new IllegalArgumentException("excavate X1 Y1 Z1 X2 Y2 Z2 [FORWARD SIDE] | excavate resume | excavate plan ...");
        }
        if (a[1].equals("resume")) {
            Excavate e = load(mc);
            if (e == null) {
                throw new IllegalArgumentException("no hall to resume (" + file(mc) + ")");
            }
            return e;
        }
        boolean plan = a[1].equals("plan");
        int at = plan ? 2 : 1;
        int section = 0;
        boolean climbBack = false;
        for (int k = at; k < a.length; k++) {
            if (a[k].equals("climbback")) {
                climbBack = true;
                String[] rest = new String[a.length - 1];
                System.arraycopy(a, 0, rest, 0, k);
                System.arraycopy(a, k + 1, rest, k, a.length - k - 1);
                a = rest;
                break;
            }
        }
        for (int k = at; k + 1 < a.length; k++) {
            if (a[k].equals("section")) {
                section = Integer.parseInt(a[k + 1]);
                String[] rest = new String[a.length - 2];
                System.arraycopy(a, 0, rest, 0, k);
                System.arraycopy(a, k + 2, rest, k, a.length - k - 2);
                a = rest;
                break;
            }
        }
        Hall hall;
        Hall.Box box;
        if (a.length > at && a[at].equals("ahead")) {
            // from where it stands: its feet the way in, the hall ahead of it, its lanes to the right (or left)
            if (a.length < at + 4) {
                throw new IllegalArgumentException("excavate ahead WIDTH HEIGHT LENGTH [left|right] [DIR]");
            }
            int w = Integer.parseInt(a[at + 1]);
            int h = Integer.parseInt(a[at + 2]);
            int l = Integer.parseInt(a[at + 3]);
            boolean left = false;
            Direction forward = mc.player.getDirection();
            for (int k = at + 4; k < a.length; k++) {
                if (a[k].equals("left") || a[k].equals("right")) {
                    left = a[k].equals("left");
                } else if (Stairs.direction(a[k]) != null) {
                    forward = Stairs.direction(a[k]);
                } else {
                    throw new IllegalArgumentException("left, right or a direction, not " + a[k]);
                }
            }
            BlockPos feet = Walker.feetCell(mc.player);
            hall = Hall.ahead(feet, forward, left ? forward.getCounterClockWise() : forward.getClockWise(), w, h, l, section);
            box = hall.box;
        } else {
        if (a.length < at + 6) {
            throw new IllegalArgumentException("the box: X1 Y1 Z1 X2 Y2 Z2, or ahead WIDTH HEIGHT LENGTH");
        }
        BlockPos p1 = Bot.pos(a, at);
        BlockPos p2 = Bot.pos(a, at + 3);
        box = Hall.Box.of(p1.getX(), p1.getY(), p1.getZ(), p2.getX(), p2.getY(), p2.getZ());
        if (a.length >= at + 8) {
            Direction f = Stairs.direction(a[at + 6]);
            Direction s = Stairs.direction(a[at + 7]);
            if (f == null || s == null) {
                throw new IllegalArgumentException("FORWARD and SIDE: north, south, east or west");
            }
            hall = new Hall(box, f, s, section, climbBack);
        } else {
            hall = Hall.plan(box, Walker.feetCell(mc.player), section);
        }
        }
        String near = Protect.near(mc, box);
        if (near != null) {
            throw new IllegalArgumentException("the box comes within " + Protect.MARGIN + " of a protected place: " + near);
        }
        if (plan) {
            Bot.event("hall", "plan: " + box + ", " + hall.width + " across x " + hall.length + " along x " + box.sizeY() + " high, "
                    + hall.layers.size() + " layers, " + hall.lanes.length + " lanes, " + hall.sections.size() + " sections of "
                    + hall.sectionLength + ", " + hall.moves.size() + " moves; forward "
                    + hall.forward.getName() + ", side " + hall.side.getName() + "; the way in ends at " + hall.entry().toShortString()
                    + " (feet), first blow " + hall.moves.get(0).step().aim().toShortString() + "; excavate " + box + " "
                    + hall.forward.getName() + " " + hall.side.getName() + (hall.sections.size() > 1 ? " section " + hall.sectionLength : "")
                    + " digs it");
            return null;
        }
        Excavate saved = load(mc);
        if (saved != null && saved.hall.box.equals(box) && saved.hall.forward == hall.forward && saved.hall.side == hall.side
                && saved.hall.sectionLength == hall.sectionLength && saved.hall.climbBack == hall.climbBack) {
            Bot.event("hall", "the same hall as the one saved: carrying on from move " + saved.index);
            return saved;
        }
        if (saved != null) {
            try {
                Files.move(file(mc), file(mc).resolveSibling(file(mc).getFileName() + ".old"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Nookworks.LOGGER.warn("[bot] cannot put the old hall aside: {}", e.toString());
            }
        }
        return new Excavate(hall);
    }

    // ---- the file

    static Path file(Minecraft mc) {
        return Bot.dir().resolve("bot_hall").resolve(WayBook.key(mc) + ".txt");
    }

    static @Nullable Excavate load(Minecraft mc) {
        Path f = file(mc);
        if (!Files.exists(f)) {
            return null;
        }
        try {
            Map<String, String> kv = new HashMap<>();
            for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    kv.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
            }
            String[] b = kv.get("box").split(" ");
            Hall.Box box = Hall.Box.of(Integer.parseInt(b[0]), Integer.parseInt(b[1]), Integer.parseInt(b[2]), Integer.parseInt(b[3]),
                    Integer.parseInt(b[4]), Integer.parseInt(b[5]));
            Hall hall = new Hall(box, Stairs.direction(kv.get("forward")), Stairs.direction(kv.get("side")),
                    Integer.parseInt(kv.getOrDefault("section", "0")), Boolean.parseBoolean(kv.getOrDefault("climbback", "false")));
            Excavate e = new Excavate(hall);
            e.index = Math.min(hall.moves.size(), Integer.parseInt(kv.getOrDefault("index", "0")));
            e.dug = Long.parseLong(kv.getOrDefault("dug", "0"));
            e.water = Integer.parseInt(kv.getOrDefault("water", "0"));
            e.lava = Integer.parseInt(kv.getOrDefault("lava", "0"));
            e.holes = Integer.parseInt(kv.getOrDefault("holes", "0"));
            e.torches = Integer.parseInt(kv.getOrDefault("torches", "0"));
            e.hammers = Integer.parseInt(kv.getOrDefault("hammers", "0"));
            e.worked = Long.parseLong(kv.getOrDefault("worked", "0"));
            e.swept = Integer.parseInt(kv.getOrDefault("swept", "-1"));
            String ores = kv.getOrDefault("ores", "");
            for (String o : ores.split(",")) {
                int c = o.indexOf(':');
                if (c > 0) {
                    e.ores.put(o.substring(0, c), Integer.parseInt(o.substring(c + 1)));
                }
            }
            return e;
        } catch (IOException | RuntimeException ex) {
            Nookworks.LOGGER.warn("[bot] cannot read {}: {}", f, ex.toString());
            return null;
        }
    }

    // ---- what is kept

    /** Stone and ground the dig brings up in heaps: thrown away when the bag fills (but {@link #SEAL_STACKS} stacks of
     *  what fills a hole). */
    static boolean junk(ItemStack s) {
        if (s.is(Items.ROTTEN_FLESH) || s.is(net.minecraft.tags.ItemTags.SAPLINGS) || s.is(Items.FLINT)) {
            return true; // what the dig and the dark's mobs leave that it has no use for
        }
        if (s.isDamageableItem() && Inv.wornOut(s)) {
            return true; // worn-out hammers and pickaxes, kept and never used, filled the bag by midnight (2026-11-21)
        }
        if (!(s.getItem() instanceof BlockItem b)) {
            return false;
        }
        BlockState st = b.getBlock().defaultBlockState();
        if (st.is(Tags.Blocks.ORES) || st.hasBlockEntity()) {
            return false;
        }
        return Stairs.deepRock(st) || Bot.natural(st) || st.is(Tags.Blocks.COBBLESTONES) || st.is(Blocks.COBBLED_DEEPSLATE)
                || st.is(BlockTags.SAND) || st.is(Blocks.POINTED_DRIPSTONE) || st.is(Blocks.TUFF) || st.is(Blocks.CALCITE);
    }

    /** The inventory index of a stack to throw away, -1 if none: junk, but the fullest few stacks of what seals. */
    static int junkSlot(LocalPlayer player) {
        var inv = player.getInventory();
        List<Integer> keep = new ArrayList<>();
        List<Integer> seal = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && junk(s) && Inv.throwaway(s)) {
                seal.add(i);
            }
        }
        seal.sort((x, y) -> inv.getItem(y).getCount() - inv.getItem(x).getCount());
        keep.addAll(seal.subList(0, Math.min(SEAL_STACKS, seal.size())));
        // full stacks first, then the biggest: a stack just begun is left to fill (thrown as it came, it threw after every
        // blow - the user, 2026-11-21: 「一回掘るごとにアイテム捨てる動きがすごく無駄じゃない？」)
        int best = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !junk(s) || keep.contains(i) || i == inv.getSelectedSlot()) {
                continue;
            }
            if (best < 0) {
                best = i;
                continue;
            }
            ItemStack b = inv.getItem(best);
            boolean full = s.getCount() >= s.getMaxStackSize();
            boolean bestFull = b.getCount() >= b.getMaxStackSize();
            if (full && !bestFull || full == bestFull && s.getCount() > b.getCount()) {
                best = i;
            }
        }
        return best;
    }

    /** Slots free when it starts throwing junk away, and how many a round of throwing frees. */
    static final int THROW_AT = 1;
    static final int THROW_TO = 4;

    static int freeSlots(LocalPlayer player) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    /** Whether breaking the block with the best tool it carries gives what it drops (ores that want iron do not, to stone). */
    static boolean harvestable(LocalPlayer player, BlockState state) {
        // only ores are worth leaving standing: stone broken by hand for nothing (no hammer left) is still out of the way
        return !state.is(Tags.Blocks.ORES) || !state.requiresCorrectToolForDrops() || player.getAbilities().instabuild
                || Inv.bestTool(player, state) != -2;
    }

    /** The block to fill with: the fullest stack of plain stone or ground (not what falls). */
    static @Nullable Block sealBlock(LocalPlayer player) {
        int best = -1;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (Inv.throwaway(s) && s.getItem() instanceof BlockItem b && b.getBlock().defaultBlockState().isSolid() && s.getCount() > count) {
                best = i;
                count = s.getCount();
            }
        }
        return best < 0 ? null : ((BlockItem) player.getInventory().getItem(best).getItem()).getBlock();
    }

    /** Meals in the bag (food items, of anything that fills more than two points). */
    static int meals(LocalPlayer player) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (Inv.nutrition(s) > 2 && !Inv.harmful(s)) {
                n += s.getCount();
            }
        }
        return n;
    }

    /** Throws junk ({@link #junkSlot}) behind it. */
    static final class Throw implements Task {
        private final Direction back;
        private int ticks;
        private int thrown;

        Throw(Direction back) {
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
            if (slot < 0 || this.thrown > 0 && freeSlots(player) >= THROW_TO || ++this.ticks > 300) {
                return Result.DONE;
            }
            // dropped at its feet, looking down: not turned round behind it (its view is the video's camera)
            c.face(player.getYRot(), 75.0F);
            if (c.aimed(player, 8.0F) && this.ticks % 3 == 0) {
                var menu = player.inventoryMenu;
                Inv.click(mc, menu, Inv.menuSlot(menu, player.getInventory(), slot), 1, ContainerInput.THROW);
                this.thrown++;
            }
            return Result.RUNNING;
        }
    }

    private enum Phase { NEXT, GO, LOOK, BLOW, CLEAR, SEAL, FLOOR, TORCH, SWEEP }

    static final class Excavate implements Task, Bot.Resumable, Bot.ScreenTask {
        final Hall hall;
        /** The first move not yet done. */
        int index;
        long dug;
        final Map<String, Integer> ores = new TreeMap<>();
        int water;
        int lava;
        int holes;
        int torches;
        int hammers;
        /** Game ticks spent digging, over every run of this hall. */
        long worked;
        /** The last layer looked over when done. */
        int swept = -1;

        private Phase phase = Phase.NEXT;
        private Hall.@Nullable Move move;
        private @Nullable Task sub;
        private boolean subMayFail;
        private @Nullable Runnable afterSub;
        private Acts.@Nullable Breaker breaker;
        private boolean lavaMode;
        private boolean started;
        private long lastTick = -1;
        private int goFails;
        private int skippedInARow;
        private int sealRounds;
        private int floorTries;
        private int clears;
        private int phaseTicks;
        private int shuffle;
        private int still;
        private int settle;
        private @Nullable Block struck;
        private boolean toldOneBlock;
        private final Set<BlockPos> left = new HashSet<>();
        private final Set<BlockPos> unsealable = new HashSet<>();
        private final Map<Block, Integer> before = new LinkedHashMap<>();
        private final Map<BlockPos, String> oresBefore = new HashMap<>();
        private int solidBefore;
        private long hammerTriedAt = -100000;
        private long torchTriedAt = -100000;
        private long shovelTriedAt = -100000;
        private long pickaxeTriedAt = -100000;
        private long fullToldAt = -100000;
        private long stashTriedAt = -100000;
        private int wetSkipped;
        private long noTorchToldAt = -100000;
        private long lastProgress = -1;
        private int progressIndex = -1;
        private int progressSweep = -1;
        private @Nullable BlockPos progressPos;
        private long foodToldAt = -100000;
        private boolean toldNoHammer;
        private final Set<String> toldNoTool = new HashSet<>();
        private int movesThisRun;
        // the look over a finished layer
        private @Nullable List<BlockPos> sweepList;
        private int sweepPass;
        private int sweepLayer = -1;
        private int sweepTorches;

        Excavate(Hall hall) {
            this.hall = hall;
        }

        @Override
        public String describe() {
            Hall.Move m = this.hall.moves.get(Math.min(this.index, this.hall.moves.size() - 1));
            return "excavate " + this.hall.box + ": " + this.where(m.segment()) + ", move " + this.index + "/" + this.hall.moves.size() + ", "
                    + this.dug + " blocks dug";
        }

        /** "section 2/9, layer 3/14 (y 32..34)" (no section with one). */
        String where(int segment) {
            int n = this.hall.layers.size();
            Hall.Layer l = this.hall.layers.get(segment % n);
            return (this.hall.sections.size() > 1 ? "section " + (segment / n + 1) + "/" + this.hall.sections.size() + ", " : "") + "layer "
                    + (segment % n + 1) + "/" + n + " (y " + l.bottom() + ".." + l.top() + ")";
        }

        private String numbers() {
            StringBuilder ore = new StringBuilder();
            for (var e : this.ores.entrySet()) {
                ore.append(ore.isEmpty() ? "" : ", ").append(e.getKey()).append(' ').append(e.getValue());
            }
            return this.dug + " blocks dug; ores: " + (ore.isEmpty() ? "none" : ore) + "; sealed: water " + this.water + ", lava " + this.lava
                    + ", holes " + this.holes + "; torches " + this.torches + "; hammers made " + this.hammers + "; " + this.worked / 1200 + " min";
        }

        void save(Minecraft mc) {
            StringBuilder ore = new StringBuilder();
            for (var e : this.ores.entrySet()) {
                ore.append(ore.isEmpty() ? "" : ",").append(e.getKey()).append(':').append(e.getValue());
            }
            List<String> lines = List.of("box=" + this.hall.box, "forward=" + this.hall.forward.getName(), "side=" + this.hall.side.getName(),
                    "section=" + (this.hall.sections.size() > 1 ? this.hall.sectionLength : 0), "climbback=" + this.hall.climbBack,
                    "index=" + this.index, "moves=" + this.hall.moves.size(), "dug=" + this.dug, "ores=" + ore, "water=" + this.water,
                    "lava=" + this.lava, "holes=" + this.holes, "torches=" + this.torches, "hammers=" + this.hammers, "worked=" + this.worked,
                    "swept=" + this.swept, "entry=" + this.hall.entry().toShortString().replace(",", ""));
            Path f = file(mc);
            try {
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.write(tmp, lines, StandardCharsets.UTF_8);
                Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Nookworks.LOGGER.warn("[bot] cannot write {}: {}", f, e.toString());
            }
        }

        private void run(Task task, boolean mayFail, @Nullable Runnable after) {
            this.sub = task;
            this.subMayFail = mayFail;
            this.afterSub = after;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            active = this.hall;
            long now = mc.level.getGameTime();
            if (this.lastTick >= 0 && now > this.lastTick && now - this.lastTick < 40) {
                this.worked += now - this.lastTick;
            }
            this.lastTick = now;
            if (!this.started) {
                this.started = true;
                Bot.event("hall", (this.index == 0 ? "start: " : "carry on at move " + this.index + ": ") + this.hall.box + " ("
                        + this.hall.width + " across, " + this.hall.length + " along, " + this.hall.box.sizeY() + " high; " + this.hall.layers.size()
                        + " layers, " + this.hall.moves.size() + " moves), the way in at " + this.hall.entry().toShortString());
            }
            if (mc.gui.screen() != null && !(this.sub instanceof Bot.ScreenTask)) {
                player.closeContainer(); // a stray one: nothing of the dig's
                return Result.RUNNING;
            }
            // nothing done for three minutes (a loop no one foresaw): said, and the move passed over - the night goes on
            if (this.lastProgress < 0 || this.index != this.progressIndex || this.phase == Phase.SWEEP && this.sweepList != null
                    && this.sweepList.size() != this.progressSweep || this.progressPos == null
                    || this.progressPos.distSqr(player.blockPosition()) > 16) {
                this.lastProgress = this.worked;
                this.progressIndex = this.index;
                this.progressSweep = this.sweepList == null ? -1 : this.sweepList.size();
                this.progressPos = player.blockPosition(); // (a long walk back is headway too)
            } else if (this.worked - this.lastProgress > 3600) {
                this.lastProgress = this.worked;
                Bot.event("hall", "no headway for 3 minutes at move " + this.index + " (" + this.phase + ", at "
                        + player.blockPosition().toShortString() + "): passed over");
                if (this.breaker != null) {
                    mc.gameMode.stopDestroyBlock();
                    this.breaker = null;
                }
                if (this.phase == Phase.SWEEP) {
                    this.sweepPass = 3;
                    this.sweepList = new ArrayList<>();
                    this.sweepTorches = 99;
                } else if (this.move == null) {
                    this.index++; // stuck between moves (upkeep): on to the next all the same
                    this.hammerTriedAt = now;
                    this.torchTriedAt = now;
                    this.phase = Phase.NEXT;
                } else {
                    this.doneMove(mc, true);
                }
                if (this.sub != null) {
                    this.sub.cancel(mc);
                    this.sub = null;
                    this.afterSub = null;
                }
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
                Runnable after = this.afterSub;
                this.afterSub = null;
                if (r == Result.FAILED && !this.subMayFail) {
                    Bot.fail(done.describe() + " failed");
                    return this.stop(mc, Result.FAILED);
                }
                if (after != null) {
                    after.run();
                }
                return Result.RUNNING;
            }
            try {
                return switch (this.phase) {
                    case NEXT -> this.next(mc, player);
                    case GO -> this.go(mc, player, c);
                    case LOOK -> this.look(mc);
                    case BLOW -> this.blow(mc, player, c);
                    case CLEAR -> this.clear(mc, player, c);
                    case SEAL -> this.seal(mc, player);
                    case FLOOR -> this.floor(mc, player);
                    case TORCH -> this.torch(mc, player);
                    case SWEEP -> this.sweep(mc, player, c);
                };
            } catch (RuntimeException e) {
                Nookworks.LOGGER.warn("[bot] excavate", e);
                Bot.event("hall", "trouble at move " + this.index + " (" + e + "): skipped");
                this.doneMove(mc, true);
                return Result.RUNNING;
            }
        }

        private Result stop(Minecraft mc, Result r) {
            this.save(mc);
            if (active == this.hall) {
                active = null;
            }
            return r;
        }

        // ---- between moves

        private Result next(Minecraft mc, LocalPlayer player) {
            if (this.index >= this.hall.moves.size()) {
                int lastSeg = this.hall.moves.get(this.hall.moves.size() - 1).segment();
                if (this.swept < lastSeg) {
                    return this.startSweep(lastSeg);
                }
                Bot.event("hall", "done: " + this.hall.box + " dug out; " + this.numbers());
                return this.stop(mc, Result.DONE);
            }
            Hall.Move m = this.hall.moves.get(this.index);
            if (this.index > 0) {
                Hall.Move prev = this.hall.moves.get(this.index - 1);
                if (prev.segment() != m.segment() && this.swept < prev.segment()) {
                    return this.startSweep(prev.segment());
                }
            }
            if (this.upkeep(mc, player, m)) {
                return Result.RUNNING;
            }
            // moves already done (resumed, or a cave did them) are passed over, many a tick
            for (int k = 0; k < 64 && this.index < this.hall.moves.size(); k++) {
                m = this.hall.moves.get(this.index);
                if (!this.clearAlready(mc, m)) {
                    break;
                }
                if (this.index + 1 < this.hall.moves.size() && this.hall.moves.get(this.index + 1).segment() != m.segment()) {
                    this.index++;
                    return Result.RUNNING; // the next layer starts with the look over this one
                }
                this.index++;
            }
            if (this.index >= this.hall.moves.size()) {
                return Result.RUNNING;
            }
            m = this.hall.moves.get(this.index);
            if (m.index() == 0 || this.hall.moves.get(m.index() - 1).segment() != m.segment()) {
                Bot.event("hall", this.where(m.segment()) + " begins" + (m.part() == Hall.Part.IN ? " (in, and up the stair across it)" : "")
                        + "; " + this.numbers());
            }
            this.move = m;
            this.unsealable.clear();
            this.goFails = 0;
            this.sealRounds = 0;
            this.floorTries = 0;
            this.clears = 0;
            this.phase = Phase.GO;
            return Result.RUNNING;
        }

        /** Whether every cell the move opens is open already (and loaded: an unloaded one reads as air). */
        private boolean clearAlready(Minecraft mc, Hall.Move m) {
            for (BlockPos p : m.open()) {
                if (!mc.level.hasChunkAt(p)) {
                    return false;
                }
                if (clearable(mc, p) && !this.left.contains(p) || mc.level.getFluidState(p).isSource()) {
                    return false;
                }
            }
            return true;
        }

        /** A block to break: neither open to walk through nor a fluid (water is filled first, then dug). */
        private static boolean clearable(Minecraft mc, BlockPos p) {
            BlockState s = mc.level.getBlockState(p);
            return !Stairs.passable(mc.level, p, s) && s.getFluidState().isEmpty() && !s.is(Blocks.TORCH) && !s.is(Blocks.WALL_TORCH);
        }

        /** Keeping itself going between moves: true when a chore was started. */
        private boolean upkeep(Minecraft mc, LocalPlayer player, Hall.Move m) {
            long now = mc.level.getGameTime();
            if (freeSlots(player) <= THROW_AT && junkSlot(player) >= 0) {
                this.run(new Throw(m.step().dir().getOpposite()), true, null);
                return true;
            }
            // full of what it keeps (raw ores, lapis...): taken to the store chest (bot store X Y Z), if one is set; else they
            // lie where they fall
            if (freeSlots(player) <= THROW_AT && junkSlot(player) < 0 && Bot.settings.store != null && now - this.stashTriedAt > 6000) {
                this.stashTriedAt = now;
                Bot.event("hall", "bag full of what it keeps: taking it to the chest at " + Bot.settings.store.toShortString());
                this.run(new ItemTasks.Deposit(Bot.settings.store, "loot"), true, null);
                return true;
            }
            if (freeSlots(player) == 0 && now - this.fullToldAt > 12000) {
                this.fullToldAt = now;
                Bot.event("hall", "bag full of what it keeps: what it digs lies where it falls (put things in a chest for it)");
            }
            boolean creative = player.getAbilities().instabuild;
            // hammers made while one is still left (with none, it broke stone by hand for minutes: the pickaxe wore out
            // with them), three at a time, room made for them first
            int usable = Inv.count(player, s -> s.is(Hammers.TAG) && !Inv.wornOut(s));
            if (!creative && usable <= 1 && now - this.hammerTriedAt > (usable == 0 ? 300 : 1200)) {
                this.hammerTriedAt = now;
                int stone = Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS));
                int wood = Inv.count(player, s -> s.is(Items.STICK)) / 2 + Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.PLANKS))
                        + 4 * Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.LOGS));
                int n = Math.min(HAMMER_BATCH, Math.min(stone / 5, wood));
                // room for the table, the sticks and the hammers: as many made as there is room for (thrown at its
                // feet to make room, the junk was picked straight up again, and it threw and crafted in a loop for an hour,
                // 2026-11-22); none, and none left: what it keeps taken to the store chest
                if (n > 0 && freeSlots(player) < 3 && usable == 0) {
                    if (Bot.settings.store != null && now - this.stashTriedAt > 1200) {
                        this.stashTriedAt = now;
                        this.hammerTriedAt = now - 1200;
                        Bot.event("hall", "no room to make hammers: taking what it keeps to the chest at " + Bot.settings.store.toShortString());
                        this.run(new ItemTasks.Deposit(Bot.settings.store, "loot"), true, null);
                        return true;
                    }
                }
                n = Math.min(n, freeSlots(player) - 2);
                if (n > 0) {
                    int had = Inv.count(player, s -> s.is(ModItems.STONE_HAMMER.get()) && !Inv.wornOut(s));
                    Bot.event("hall", (usable == 0 ? "the hammer is worn out" : "one hammer left") + ": making " + n + " stone ones");
                    this.craftHere(mc, ModItems.STONE_HAMMER.get(), n, () -> this.hammers += Math.max(0,
                            Inv.count(mc.player, s -> s.is(ModItems.STONE_HAMMER.get()) && !Inv.wornOut(s)) - had));
                    return true;
                }
                if (usable == 0 && !this.toldNoHammer) {
                    this.toldNoHammer = true;
                    Bot.event("hall", freeSlots(player) <= 1 ? "no room in the bag to make a hammer: a block at a time"
                            : "no hammer, nor what makes one (5 stone, wood): a block at a time");
                }
            }
            // a shovel for the gravel and sand that fall out of the ceiling (the hammer takes them at a hand's pace)
            if (!creative && Inv.find(player, s -> s.is(net.minecraft.tags.ItemTags.SHOVELS) && !Inv.wornOut(s)) < 0 && now - this.shovelTriedAt > 6000
                    && Inv.count(player, s -> s.is(net.minecraft.tags.ItemTags.STONE_TOOL_MATERIALS)) >= 1
                    && Inv.count(player, s -> s.is(Items.STICK) || s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(net.minecraft.tags.ItemTags.LOGS)) > 0) {
                this.shovelTriedAt = now;
                this.craftHere(mc, Items.STONE_SHOVEL, 1, () -> {});
                return true;
            }
            int torchesHeld = Inv.count(player, s -> s.is(Items.TORCH));
            if (!creative && torchesHeld < 6 && now - this.torchTriedAt > 1200) {
                this.torchTriedAt = now;
                int coal = Inv.count(player, s -> s.is(Items.COAL) || s.is(Items.CHARCOAL));
                boolean wood = Inv.count(player, s -> s.is(Items.STICK) || s.is(net.minecraft.tags.ItemTags.PLANKS) || s.is(net.minecraft.tags.ItemTags.LOGS)) > 0;
                if (coal > 0 && wood) {
                    this.run(new Crafting.Craft(Items.TORCH, Math.min(32, 4 * coal)), true, null);
                    return true;
                }
                if (torchesHeld == 0 && now - this.noTorchToldAt > 12000) {
                    this.noTorchToldAt = now;
                    Bot.event("hall", "no torches left, nor coal and wood for more");
                }
            }
            if (!creative && meals(player) <= 2 && now - this.foodToldAt > 12000) {
                this.foodToldAt = now;
                Bot.event("hall", "food low: " + meals(player) + " left (food " + player.getFoodData().getFoodLevel() + ")");
            }
            // hurt, it eats up to the 18 that heals (left at 15 food, it stayed at half health for good, 2026-11-21)
            if (!creative && player.getHealth() < 16.0F && player.getFoodData().getFoodLevel() < 18 && Inv.bestFood(player, false) >= 0
                    && Bot.nearestHostile(player, 12.0) == null) {
                this.run(new ItemTasks.Eat(false), true, null);
                return true;
            }
            if (!creative && player.getHealth() < 10.0F && player.getFoodData().getFoodLevel() >= 18 && Bot.nearestHostile(player, 12.0) == null) {
                this.run(new MoveTasks.Wait(100), true, null); // healing (it eats by itself when hungry)
                return true;
            }
            return false;
        }

        /** An iron pickaxe made for an ore the stone tools would break for nothing: true when the craft was started. */
        private boolean mayMakePickaxe(Minecraft mc, LocalPlayer player) {
            long now = mc.level.getGameTime();
            if (player.getAbilities().instabuild || now - this.pickaxeTriedAt < 6000 || Inv.count(player, s -> s.is(Items.IRON_INGOT)) < 3
                    || freeSlots(player) < 1) {
                return false;
            }
            this.pickaxeTriedAt = now;
            Bot.event("hall", "an ore the stone tools would break for nothing: making an iron pickaxe");
            this.craftHere(mc, Items.IRON_PICKAXE, 1, () -> {});
            return true;
        }

        /** Crafts with a table set down by the dig, and picks the table up again after (none left floating in the hall). */
        private void craftHere(Minecraft mc, net.minecraft.world.item.Item item, int n, Runnable then) {
            Set<BlockPos> tables = tablesNear(mc);
            // its own table set down beside it on the hall's floor first, as the hall's other blocks are (the craft's own
            // way to a table walked it off a dug-out section's edge twice, 2026-11-22)
            BlockPos spot = this.tableSpot(mc);
            if (tables.isEmpty() && spot != null && Inv.find(mc.player, s -> s.is(Items.CRAFTING_TABLE)) >= 0) {
                this.run(build(this.hall, List.of(new WorkTasks.Target(spot, Blocks.CRAFTING_TABLE.defaultBlockState())), "a crafting table"), true,
                        () -> this.craftWith(mc, item, n, then, tables));
                return;
            }
            this.craftWith(mc, item, n, then, tables);
        }

        /** A cell beside the feet, in the hall, open, on its floor: where its crafting table goes. */
        private @Nullable BlockPos tableSpot(Minecraft mc) {
            BlockPos feet = Walker.feetCell(mc.player);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos p = feet.relative(d);
                if (this.hall.box.contains(p) && mc.level.getBlockState(p).isAir()
                        && mc.level.getBlockState(p.below()).isFaceSturdy(mc.level, p.below(), Direction.UP)) {
                    return p;
                }
            }
            return null;
        }

        private void craftWith(Minecraft mc, net.minecraft.world.item.Item item, int n, Runnable then, Set<BlockPos> tables) {
            this.run(new Crafting.Craft(item, n), true, () -> {
                then.run();
                List<BlockPos> ours = new ArrayList<>();
                for (BlockPos p : tablesNear(mc)) {
                    if (!tables.contains(p) && this.hall.box.contains(p)) {
                        ours.add(p);
                    }
                }
                if (!ours.isEmpty()) {
                    this.run(new PickUp(ours, Items.CRAFTING_TABLE), true, null);
                }
            });
        }

        private static Set<BlockPos> tablesNear(Minecraft mc) {
            Set<BlockPos> out = new HashSet<>();
            BlockPos at = mc.player.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-6, -3, -6), at.offset(6, 3, 6))) {
                if (mc.level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                    out.add(p.immutable());
                }
            }
            return out;
        }

        // ---- a move

        private Result go(Minecraft mc, LocalPlayer player, Controls c) {
            Hall.Move m = this.move;
            BlockPos feet = Walker.feetCell(player);
            if (feet.equals(m.stand()) && (player.onGround() || player.isInWater())) {
                this.floorTries = 0;
                this.phase = Phase.LOOK;
                return Result.RUNNING;
            }
            // (the way into a section - the stair up - is dug and its water shut even so: passed over, it left the
            // next section's top out of reach, and NookAI stood idle on the floor, 2026-11-21)
            boolean way = m.part() == Hall.Part.IN || m.part() == Hall.Part.UP || m.part() == Hall.Part.ENTRY;
            if (Bot.settings.dry && !way && (Hall.wet(mc.level, m.step()) || Hall.wetBy(mc.level, m.stand())
                    || Hall.wetBy(mc.level, m.stand().above()))) {
                this.left.addAll(m.open());
                this.wetSkipped++;
                if (this.wetSkipped % 20 == 1) {
                    Bot.event("hall", "water by move " + m.index() + " at " + m.step().to().toShortString() + ": left be (" + this.wetSkipped
                            + " moves left so far)");
                }
                this.doneMove(mc, true);
                return Result.RUNNING;
            }
            if (this.goFails >= 3) {
                Bot.event("hall", "could not get to " + m.stand().toShortString() + " for move " + m.index() + " (" + m.part() + "): passed over");
                this.doneMove(mc, true);
                return Result.RUNNING;
            }
            // nothing to stand on there (a lane over the stair up, a cave): a floor set down first, from the floor beside
            BlockPos under = m.stand().below();
            // (the way in over a section already dug out had none: it fell 36 blocks, 2026-11-21)
            if ((this.hall.box.contains(under) || under.getY() == this.hall.box.y1() - 1 || this.hall.mayDig(under)) && this.floorTries < 2
                    && mc.level.getBlockState(under).getCollisionShape(mc.level, under).isEmpty()) {
                this.floorTries++;
                Block block = sealBlock(player);
                if (block != null) {
                    this.run(build(this.hall, List.of(new WorkTasks.Target(under, block.defaultBlockState())), "a floor at " + under.toShortString()),
                            true, null);
                    return Result.RUNNING;
                }
            }
            this.run(new MoveTasks.Goto(Goal.at(m.stand()), "to move " + m.index()), true, () -> {
                LocalPlayer me = mc.player;
                if (!Walker.feetCell(me).equals(m.stand()) || !me.onGround() && !me.isInWater()) {
                    this.goFails++;
                }
            });
            return Result.RUNNING;
        }

        private Result look(Minecraft mc) {
            Hall.Move m = this.move;
            this.lavaMode = Hall.lavaBy(mc.level, m.step());
            this.before.clear();
            this.oresBefore.clear();
            this.solidBefore = 0;
            for (BlockPos p : m.open()) {
                if (clearable(mc, p)) {
                    this.solidBefore++;
                    String ore = Hall.oreName(mc.level.getBlockState(p));
                    if (ore != null) {
                        this.oresBefore.put(p, ore);
                    }
                }
            }
            if (this.lavaMode) {
                Bot.event("hall", "lava by move " + m.index() + " at " + m.step().to().toShortString() + ": a block at a time, the lava shut as it shows");
            }
            this.phaseTicks = 0;
            this.shuffle = 0;
            this.still = 0;
            this.settle = 0;
            this.struck = null;
            this.phase = this.lavaMode ? Phase.CLEAR : Phase.BLOW;
            return Result.RUNNING;
        }

        /** One blow of the hammer at the middle of the 3x3 ahead (as the stair dig strikes). */
        private Result blow(Minecraft mc, LocalPlayer player, Controls c) {
            Stairs.Step s = this.move.step();
            BlockState aim = mc.level.getBlockState(s.aim());
            int hammer = DigTasks.bestHammer(player);
            if (this.struck != null) {
                aim = this.struck.defaultBlockState();
            }
            if (Stairs.passable(mc.level, s.aim(), aim) && this.struck == null || hammer < 0 || player.getAbilities().instabuild || !Stairs.hammerTakes(aim)
                    || !player.getInventory().getItem(hammer).isCorrectToolForDrops(aim) || !this.hall.box.contains(s.aim())) {
                this.phase = Phase.CLEAR;
                return Result.RUNNING;
            }
            BlockPos stand = s.from();
            double off = Math.hypot(stand.getX() + 0.5 - player.getX(), stand.getZ() + 0.5 - player.getZ());
            if (off > 0.3 && this.shuffle++ < 40) {
                Walker.shuffleToward(player, c, stand.getX() + 0.5, stand.getZ() + 0.5);
                return Result.RUNNING;
            }
            if (!player.getMainHandItem().is(Hammers.TAG) || Inv.wornOut(player.getMainHandItem())) {
                Inv.hold(mc, hammer);
            }
            boolean sneak = Stairs.sneakForHammer(NookworksConfig.HAMMER_ACTIVATION.get());
            c.sneak = sneak;
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
            int leftOf = 0;
            for (BlockPos p : s.open()) {
                if (mc.level.getBlockState(p).is(this.struck)) {
                    leftOf++;
                }
            }
            if (leftOf > 0 && ++this.settle < 10) {
                return Result.RUNNING; // the rest of the 3x3 as the server's word of it comes
            }
            if (leftOf >= 4 && !this.toldOneBlock) {
                this.toldOneBlock = true;
                Bot.event("hall", "the hammer took one block only (" + leftOf + " of " + Bot.id(this.struck) + " left at " + s.aim().toShortString()
                        + "): is hammer.activation set otherwise?");
            }
            this.phase = Phase.CLEAR;
            return Result.RUNNING;
        }

        /** What the blow left, a block at a time (top first: what falls comes down into cells still to clear); by lava,
         *  each one's lava shut before the next. */
        private Result clear(Minecraft mc, LocalPlayer player, Controls c) {
            Hall.Move m = this.move;
            if (this.breaker != null) {
                if (!this.breaker.tick(mc, c)) {
                    return Result.RUNNING;
                }
                String why = this.breaker.failure();
                BlockPos p = this.breaker.pos;
                this.breaker = null;
                if (why != null) {
                    if (why.startsWith("out of reach")) {
                        this.goFails++;
                        this.phase = Phase.GO; // put off its stand meanwhile
                        return Result.RUNNING;
                    }
                    this.left.add(p);
                    Bot.event("hall", "left " + Bot.id(mc.level.getBlockState(p).getBlock()) + " at " + p.toShortString() + ": " + why);
                }
                if (this.lavaMode) {
                    this.sealRounds = 0;
                    this.phase = Phase.SEAL; // shut what this one let show, then the next
                    this.afterSealPhase = Phase.CLEAR;
                    return Result.RUNNING;
                }
            }
            for (BlockPos p : m.open()) {
                if (clearable(mc, p) && !this.left.contains(p)) {
                    if (++this.clears > 40) {
                        this.left.add(p);
                        Bot.event("hall", "could not clear " + p.toShortString() + " (it fills again): left");
                        continue;
                    }
                    BlockState st = mc.level.getBlockState(p);
                    if (!harvestable(player, st)) {
                        // never broken for nothing (gold ore broken with stone tools, the user saw, 2026-11-21): an iron
                        // pickaxe made if there is iron, else the block left standing
                        if (this.mayMakePickaxe(mc, player)) {
                            return Result.RUNNING;
                        }
                        this.left.add(p);
                        if (this.toldNoTool.add(Bot.id(st.getBlock()))) {
                            Bot.event("hall", "left " + Bot.id(st.getBlock()) + " at " + p.toShortString() + ": no tool that gets it (an iron pickaxe)");
                        }
                        continue;
                    }
                    this.breaker = new Acts.Breaker(p);
                    return Result.RUNNING;
                }
            }
            this.sealRounds = 0;
            this.afterSealPhase = Phase.FLOOR;
            this.phase = Phase.SEAL;
            return Result.RUNNING;
        }

        private Phase afterSealPhase = Phase.FLOOR;

        /** Water, lava and holes round what was opened, filled with a block. */
        private Result seal(Minecraft mc, LocalPlayer player) {
            Hall.Move m = this.move;
            // round the cells open now (by lava, the rest are still rock: what lies behind them is not to be got at yet)
            List<BlockPos> opened = new ArrayList<>();
            for (BlockPos p : m.open()) {
                if (!clearable(mc, p) || this.left.contains(p)) {
                    opened.add(p);
                }
            }
            List<BlockPos> want = new ArrayList<>();
            for (BlockPos p : this.hall.toSeal(mc.level, opened)) {
                if (!this.unsealable.contains(p) && mc.level.hasChunkAt(p)) {
                    want.add(p);
                }
            }
            if (want.isEmpty() || this.sealRounds >= 4) {
                this.unsealable.addAll(want);
                boolean refill = false;
                for (BlockPos p : m.open()) {
                    refill |= clearable(mc, p) && !this.left.contains(p);
                }
                // a source filled in the hall itself is dug again (the lava's own cell was the next to clear, by lava)
                this.phase = refill && this.afterSealPhase == Phase.FLOOR && this.clears < 40 ? Phase.CLEAR : this.afterSealPhase;
                return Result.RUNNING;
            }
            this.sealRounds++;
            Block block = sealBlock(player);
            if (block == null && !player.getAbilities().instabuild) {
                Bot.event("hall", "nothing left to fill " + want.size() + " cells with (first " + want.get(0).toShortString() + ")");
                this.unsealable.addAll(want);
                return Result.RUNNING;
            }
            if (block == null) {
                block = Blocks.COBBLESTONE;
            }
            BlockState state = block.defaultBlockState();
            Map<BlockPos, Hall.Seal> kinds = new HashMap<>();
            List<WorkTasks.Target> targets = new ArrayList<>();
            for (BlockPos p : want) {
                kinds.put(p, Hall.sealKind(mc.level, p));
                targets.add(new WorkTasks.Target(p, state));
            }
            // lava at its feet: a step back first
            BlockPos feet = Walker.feetCell(player);
            if (mc.level.getFluidState(feet).is(net.minecraft.tags.FluidTags.LAVA) || mc.level.getFluidState(feet.above()).is(net.minecraft.tags.FluidTags.LAVA)) {
                BlockPos back = m.stand().relative(m.step().dir().getOpposite(), 2);
                this.run(new MoveTasks.Goto(Goal.near(back, 1.0), "back from the lava"), true, null);
                return Result.RUNNING;
            }
            this.run(build(this.hall, targets, "shut " + this.describeSeal(kinds.values())), true, () -> {
                for (var e : kinds.entrySet()) {
                    BlockState now = mc.level.getBlockState(e.getKey());
                    if (now.getFluidState().isEmpty() && !Stairs.passable(mc.level, e.getKey(), now)) {
                        switch (e.getValue()) {
                            case LAVA -> this.lava++;
                            case WATER -> this.water++;
                            case HOLE -> this.holes++;
                        }
                    }
                }
                long lavaN = kinds.values().stream().filter(k -> k == Hall.Seal.LAVA).count();
                long waterN = kinds.values().stream().filter(k -> k == Hall.Seal.WATER).count();
                if (lavaN + waterN > 0) {
                    Bot.event("hall", "shut " + (lavaN > 0 ? lavaN + " lava " : "") + (waterN > 0 ? waterN + " water " : "") + "by "
                            + m.step().to().toShortString());
                }
            });
            return Result.RUNNING;
        }

        private String describeSeal(java.util.Collection<Hall.Seal> kinds) {
            long l = kinds.stream().filter(k -> k == Hall.Seal.LAVA).count();
            long w = kinds.stream().filter(k -> k == Hall.Seal.WATER).count();
            long h = kinds.stream().filter(k -> k == Hall.Seal.HOLE).count();
            return (l > 0 ? l + " lava " : "") + (w > 0 ? w + " water " : "") + (h > 0 ? h + " holes" : "");
        }

        private Result floor(Minecraft mc, LocalPlayer player) {
            Stairs.Step s = this.move.step();
            BlockPos under = s.floor();
            boolean ours = this.hall.box.contains(under) || under.getY() == this.hall.box.y1() - 1;
            if (!ours || !Stairs.needsFloor(mc.level, s) || player.getAbilities().instabuild || this.floorTries >= 2) {
                this.phase = Phase.TORCH;
                return Result.RUNNING;
            }
            this.floorTries++;
            Block block = sealBlock(player);
            if (block == null) {
                Bot.event("hall", "no block for a floor at " + under.toShortString());
                this.phase = Phase.TORCH;
                return Result.RUNNING;
            }
            this.run(build(this.hall, List.of(new WorkTasks.Target(under, block.defaultBlockState())), "a floor at " + under.toShortString()),
                    true, null);
            return Result.RUNNING;
        }

        private Result torch(Minecraft mc, LocalPlayer player) {
            BlockPos t = this.hall.torchAt(this.move);
            this.doneMove(mc, false);
            if (t != null && mc.level.getBlockState(t).isAir() && mc.level.getBlockState(t.below()).isFaceSturdy(mc.level, t.below(), Direction.UP)
                    && (Inv.find(player, st -> st.is(Items.TORCH)) >= 0 || player.getAbilities().instabuild)) {
                Bot.torchSetAt(t);
                this.run(build(this.hall, List.of(new WorkTasks.Target(t, Blocks.TORCH.defaultBlockState())), "a torch at " + t.toShortString()),
                        true, () -> {
                            if (mc.level.getBlockState(t).is(Blocks.TORCH)) {
                                this.torches++;
                            }
                        });
            }
            return Result.RUNNING;
        }

        /** The move done (or passed over): counted, and on to the next. */
        private void doneMove(Minecraft mc, boolean skipped) {
            Hall.Move m = this.move;
            if (m != null && !skipped) {
                int after = 0;
                for (BlockPos p : m.open()) {
                    if (clearable(mc, p)) {
                        after++;
                    }
                }
                this.dug += Math.max(0, this.solidBefore - after);
                for (var e : this.oresBefore.entrySet()) {
                    if (!clearable(mc, e.getKey())) {
                        this.ores.merge(e.getValue(), 1, Integer::sum);
                    }
                }
                this.skippedInARow = 0;
            } else {
                this.skippedInARow++;
            }
            this.oresBefore.clear();
            this.index = m == null ? this.index : m.index() + 1;
            this.move = null;
            this.phase = Phase.NEXT;
            this.movesThisRun++;
            if (this.movesThisRun % 5 == 0 || skipped) {
                this.save(mc);
            }
            if (this.movesThisRun % 60 == 0) {
                Bot.event("hall", "move " + this.index + "/" + this.hall.moves.size() + ": " + this.numbers());
            }
            if (this.skippedInARow == 30) {
                Bot.event("hall", "30 moves passed over in a row, at " + mc.player.blockPosition().toShortString() + ": the look over the layer"
                        + " sees to them later");
            }
        }

        // ---- a layer done: looked over

        private Result startSweep(int layer) {
            this.sweepLayer = layer;
            this.sweepList = null;
            this.sweepPass = 0;
            this.sweepTorches = 0;
            this.phase = Phase.SWEEP;
            return Result.RUNNING;
        }

        private Result sweep(Minecraft mc, LocalPlayer player, Controls c) {
            if (this.breaker != null) {
                if (!this.breaker.tick(mc, c)) {
                    return Result.RUNNING;
                }
                if (this.breaker.failure() != null) {
                    this.left.add(this.breaker.pos);
                }
                this.breaker = null;
                return Result.RUNNING;
            }
            if (this.sweepList == null) {
                this.sweepList = new ArrayList<>();
                for (BlockPos p : this.hall.layerCells(this.sweepLayer)) {
                    if (Bot.settings.dry && Hall.wetBy(mc.level, p)) {
                        continue;
                    }
                    if (mc.level.hasChunkAt(p) && (clearable(mc, p) && !this.left.contains(p) || mc.level.getFluidState(p).isSource())) {
                        this.sweepList.add(p);
                    }
                }
                if (!this.sweepList.isEmpty()) {
                    Bot.event("hall", this.where(this.sweepLayer) + " looked over: " + this.sweepList.size() + " cells to see to");
                }
            }
            if (!this.sweepList.isEmpty() && this.sweepPass < 3) {
                BlockPos here = player.blockPosition();
                this.sweepList.removeIf(p -> !(clearable(mc, p) && !this.left.contains(p) || mc.level.getFluidState(p).isSource()));
                if (this.sweepList.isEmpty()) {
                    this.sweepPass++;
                    this.sweepList = null;
                    return Result.RUNNING;
                }
                this.sweepList.sort(java.util.Comparator.comparingDouble(p -> p.distSqr(here)));
                BlockPos p = this.sweepList.remove(0);
                if (mc.level.getFluidState(p).isSource()) {
                    Block block = sealBlock(player);
                    if (block != null) {
                        Hall.Seal kind = Hall.sealKind(mc.level, p);
                        this.run(build(this.hall, List.of(new WorkTasks.Target(p, block.defaultBlockState())), "fill a source at " + p.toShortString()),
                                true, () -> {
                                    if (mc.level.getFluidState(p).isEmpty()) {
                                        if (kind == Hall.Seal.LAVA) {
                                            this.lava++;
                                        } else {
                                            this.water++;
                                        }
                                    }
                                });
                    }
                    return Result.RUNNING;
                }
                if (!harvestable(player, mc.level.getBlockState(p))) {
                    this.left.add(p); // nothing that gets it: left standing, not broken for nothing
                    return Result.RUNNING;
                }
                Acts.Aim aim = Acts.aimAt(player, p);
                if (Acts.inReach(player, aim.point())) {
                    this.breaker = new Acts.Breaker(p);
                } else {
                    this.run(new MoveTasks.Goto(new WorkTasks.ReachGoal(p), "to " + p.toShortString()), true, () -> {
                        if (!Acts.inReach(mc.player, Acts.aimAt(mc.player, p).point())) {
                            this.left.add(p);
                        } else {
                            this.breaker = new Acts.Breaker(p);
                        }
                    });
                }
                return Result.RUNNING;
            }
            if (this.sweepList != null && this.sweepList.isEmpty() && this.sweepPass < 3) {
                this.sweepPass++;
                this.sweepList = null;
                return Result.RUNNING;
            }
            // the floor's torches, and a dark spot left
            if (this.sweepTorches < 12 && (Inv.find(player, st -> st.is(Items.TORCH)) >= 0 || player.getAbilities().instabuild)) {
                int feet = this.hall.layers.get(this.sweepLayer % this.hall.layers.size()).bottom();
                for (BlockPos t : this.hall.torchPlaces(this.sweepLayer)) {
                    if (mc.level.hasChunkAt(t) && mc.level.getBlockState(t).isAir()
                            && mc.level.getBlockState(t.below()).isFaceSturdy(mc.level, t.below(), Direction.UP)
                            && mc.level.getBrightness(LightLayer.BLOCK, t) < 8 && t.getY() == feet) {
                        this.sweepTorches++;
                        this.run(build(this.hall, List.of(new WorkTasks.Target(t, Blocks.TORCH.defaultBlockState())), "a torch at " + t.toShortString()),
                                true, () -> {
                                    if (mc.level.getBlockState(t).is(Blocks.TORCH)) {
                                        this.torches++;
                                    }
                                });
                        return Result.RUNNING;
                    }
                }
            }
            this.swept = this.sweepLayer;
            Bot.event("hall", this.where(this.sweepLayer) + " done; " + this.numbers());
            this.save(mc);
            this.phase = Phase.NEXT;
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.breaker != null || this.phase == Phase.BLOW) {
                mc.gameMode.stopDestroyBlock();
            }
            if (this.sub != null) {
                this.sub.cancel(mc);
            }
            this.breaker = null;
            // a blow under way is begun again after (the swing's state is the game's)
            if (this.phase == Phase.BLOW) {
                this.phase = Phase.LOOK;
            }
            if (this.phase == Phase.GO) {
                this.sub = null;
            }
            this.save(mc);
        }
    }

    /** Breaks a few blocks (a table it set down) and picks up what they drop. */
    static final class PickUp implements Task {
        private final List<BlockPos> cells;
        private final net.minecraft.world.item.Item item;
        private Acts.@Nullable Breaker breaker;
        private @Nullable Task sub;
        private int ticks;

        PickUp(List<BlockPos> cells, net.minecraft.world.item.Item item) {
            this.cells = new ArrayList<>(cells);
            this.item = item;
        }

        @Override
        public String describe() {
            return "pick up " + Bot.name(this.item);
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (++this.ticks > 1200) {
                return Result.DONE;
            }
            if (this.sub != null) {
                if (this.sub.tick(mc, c) == Result.RUNNING) {
                    return Result.RUNNING;
                }
                this.sub.cancel(mc);
                this.sub = null;
                return this.cells.isEmpty() ? Result.DONE : Result.RUNNING;
            }
            if (this.breaker != null) {
                if (!this.breaker.tick(mc, c)) {
                    return Result.RUNNING;
                }
                this.breaker = null;
                if (this.cells.isEmpty()) {
                    this.sub = new MoveTasks.Collect(6.0, this.item);
                }
                return Result.RUNNING;
            }
            if (this.cells.isEmpty()) {
                return Result.DONE;
            }
            BlockPos p = this.cells.remove(0);
            if (!mc.level.getBlockState(p).is(Blocks.CRAFTING_TABLE)) {
                return Result.RUNNING;
            }
            this.breaker = new Acts.Breaker(p);
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            if (this.breaker != null) {
                mc.gameMode.stopDestroyBlock();
            }
            if (this.sub != null) {
                this.sub.cancel(mc);
            }
        }
    }

    /** Places the dig keeps away from (the base, a friend's cave): {@code bot protect X1 Z1 X2 Z2 | list | clear},
     *  kept in {@code bot_protect/<world>.txt}. A hall that comes within {@link #MARGIN} of one is refused. */
    static final class Protect {
        static final int MARGIN = 16;

        static Path file(Minecraft mc) {
            return Bot.dir().resolve("bot_protect").resolve(WayBook.key(mc) + ".txt");
        }

        static List<int[]> all(Minecraft mc) {
            List<int[]> out = new ArrayList<>();
            Path f = file(mc);
            if (!Files.exists(f)) {
                return out;
            }
            try {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    String[] p = line.trim().split("\\s+");
                    if (p.length >= 4) {
                        int x1 = Integer.parseInt(p[0]);
                        int z1 = Integer.parseInt(p[1]);
                        int x2 = Integer.parseInt(p[2]);
                        int z2 = Integer.parseInt(p[3]);
                        out.add(new int[] {Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)});
                    }
                }
            } catch (IOException | RuntimeException e) {
                Nookworks.LOGGER.warn("[bot] cannot read {}: {}", f, e.toString());
            }
            return out;
        }

        static @Nullable String near(Minecraft mc, Hall.Box box) {
            for (int[] r : all(mc)) {
                if (box.x1() <= r[2] + MARGIN && box.x2() >= r[0] - MARGIN && box.z1() <= r[3] + MARGIN && box.z2() >= r[1] - MARGIN) {
                    return "x " + r[0] + ".." + r[2] + ", z " + r[1] + ".." + r[3];
                }
            }
            return null;
        }

        static void command(Minecraft mc, String[] a) {
            Path f = file(mc);
            List<String> lines = new ArrayList<>();
            for (int[] r : all(mc)) {
                lines.add(r[0] + " " + r[1] + " " + r[2] + " " + r[3]);
            }
            if (a.length >= 2 && a[1].equals("clear")) {
                lines.clear();
            } else if (a.length >= 5) {
                lines.add(Integer.parseInt(a[1]) + " " + Integer.parseInt(a[2]) + " " + Integer.parseInt(a[3]) + " " + Integer.parseInt(a[4]));
            } else if (!(a.length >= 2 && a[1].equals("list"))) {
                throw new IllegalArgumentException("protect X1 Z1 X2 Z2 | list | clear");
            }
            try {
                Files.createDirectories(f.getParent());
                Files.write(f, lines, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("cannot write " + f + ": " + e);
            }
            Bot.event("protect", lines.isEmpty() ? "none" : String.join(" | ", lines));
        }
    }
}
