package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.BotRules.FoodStep;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The home's upkeep and the larder: what ten hours alone on 2026-10-22 asked for. That day it died about twenty times,
 * most of them at night by the hut - dark all round, a wall a creeper had blown open, no bed, the door open, and out of
 * the door to fight - and went hungry all day (no animals within about 100 blocks). So, with a home set
 * ({@code bot home X Y Z [FILE OX OY OZ]...}): the house mended to its plan, a bed kept in it, the yard round it lit,
 * doors shut at night and the night spent inside; and food kept in stock - cooked, baked, a field tended, animals bred
 * in a pen by the house ({@code bot pen X Y Z}) and fetched to it from where an explore saw them, hunted, or fished for.
 */
final class HomeTasks {
    private HomeTasks() {}

    /** The house's plans (one a file given with the home), and its doors: those in the plans and those found in them. */
    static final List<BotRules.Plan> plans = new ArrayList<>();
    static final Map<BlockPos, BlockState> doors = new LinkedHashMap<>();
    /** Parts of the upkeep that failed lately, with when: left alone for five minutes, the rest go on. */
    private static final Map<String, Long> tried = new HashMap<>();
    private static long bredAt = -BotRules.BREED_EVERY;
    private static final long RETRY = 6000;

    // ---- the home and its plans

    /** {@code home X Y Z [FILE OX OY OZ]...}: the plans read (each offset by its O), the doors in them noted. */
    static void setPlans(Minecraft mc, String[] a, int from) {
        plans.clear();
        doors.clear();
        tried.clear();
        for (int i = from; i + 3 < a.length; i += 4) {
            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            for (WorkTasks.Target t : WorkTasks.readFile(a[i], Bot.pos(a, i + 1))) {
                blocks.put(t.pos(), t.state());
            }
            plans.add(BotRules.Plan.of(blocks));
        }
        for (BotRules.Plan plan : plans) {
            plan.blocks().forEach((p, s) -> {
                if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    doors.put(p, s.setValue(DoorBlock.OPEN, false));
                }
            });
            for (BlockPos p : BlockPos.betweenClosed(plan.box().minX() - 1, plan.box().minY(), plan.box().minZ() - 1, plan.box().maxX() + 1,
                    plan.box().maxY(), plan.box().maxZ() + 1)) {
                BlockState s = mc.level.getBlockState(p);
                if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                    doors.putIfAbsent(p.immutable(), s.setValue(DoorBlock.OPEN, false));
                }
            }
            // a doorway the plan leaves open, with no door in it: an oak door goes in (the hut had none on the day alone,
            // 2026-10-22, and the zombies walked in)
            BotRules.Doorway way = BotRules.doorway(plan);
            if (way != null && doors.keySet().stream().noneMatch(d -> d.distManhattan(way.pos()) <= 1)) {
                doors.put(way.pos(), Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, way.out())
                        .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER).setValue(DoorBlock.OPEN, false));
            }
        }
    }

    /** Feet in this cell are in the house: in a plan's room, or with no plan, within 4 of home under a roof. */
    static boolean inside(Minecraft mc, BlockPos feet) {
        BlockPos home = Bot.settings.home;
        if (home == null) {
            return false;
        }
        for (BotRules.Plan p : plans) {
            if (p.inside(feet)) {
                return true;
            }
        }
        return plans.isEmpty() && feet.closerThan(home, 4.5) && roofed(mc, feet);
    }

    private static boolean roofed(Minecraft mc, BlockPos feet) {
        for (int up = 2; up <= 6; up++) {
            BlockPos p = feet.above(up);
            if (!mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** The house's rooms, cell by cell (feet cells, whatever is in them). */
    static List<BlockPos> roomCells(Minecraft mc) {
        List<BlockPos> out = new ArrayList<>();
        BlockPos home = Bot.settings.home;
        if (plans.isEmpty()) {
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-4, -1, -4), home.offset(4, 1, 4))) {
                if (inside(mc, p)) {
                    out.add(p.immutable());
                }
            }
            return out;
        }
        for (BotRules.Plan plan : plans) {
            for (BlockPos p : BlockPos.betweenClosed(plan.box().minX(), plan.box().minY(), plan.box().minZ(), plan.box().maxX(),
                    plan.box().maxY(), plan.box().maxZ())) {
                if (plan.inside(p)) {
                    out.add(p.immutable());
                }
            }
        }
        return out;
    }

    /** Feet can stand here and nothing is in the way: a floor under, two free cells, no water, no door. */
    static boolean freeFloor(Minecraft mc, BlockPos p) {
        var level = mc.level;
        BlockState s = level.getBlockState(p);
        return BotRules.standable(level, p) && !(s.getBlock() instanceof DoorBlock) && (s.isAir() || s.canBeReplaced());
    }

    /** At night, staying in: in the house, with keepIn. */
    static boolean leashed(Minecraft mc) {
        return BotRules.keepIn(mc.level.getDefaultClockTime(), Bot.settings.home != null, Bot.settings.nightOut)
                && inside(mc, Walker.feetCell(mc.player));
    }

    /** One of the house's doors standing open (nearest first), or null. */
    static @Nullable BlockPos openDoor(Minecraft mc) {
        BlockPos best = null;
        BlockPos at = mc.player.blockPosition();
        for (BlockPos p : doors.keySet()) {
            BlockState s = mc.level.getBlockState(p);
            if (s.getBlock() instanceof DoorBlock && s.getValue(DoorBlock.OPEN) && (best == null || p.distSqr(at) < best.distSqr(at))) {
                best = p;
            }
        }
        return best;
    }

    /** A bed in (or by) the house. */
    static @Nullable BlockPos bed(Minecraft mc) {
        for (BlockPos p : roomCells(mc)) {
            if (mc.level.getBlockState(p).is(BlockTags.BEDS)) {
                return p;
            }
        }
        return null;
    }

    // ---- upkeep: the guard chore

    /** The house's blocks that are missing (air, a plant, water where the plan has a block), its doors with them. */
    static List<WorkTasks.Target> missing(Minecraft mc) {
        List<WorkTasks.Target> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (BotRules.Plan plan : plans) {
            List<WorkTasks.Target> gone = new ArrayList<>();
            plan.blocks().forEach((p, want) -> {
                if (mc.level.hasChunkAt(p) && !seen.contains(p) && BotRules.needsRepair(want, mc.level.getBlockState(p))) {
                    gone.add(new WorkTasks.Target(p, want));
                }
            });
            if (BotRules.mendable(gone.size(), plan.blocks().size())) {
                gone.forEach(t -> seen.add(t.pos()));
                out.addAll(gone);
            }
        }
        doors.forEach((p, want) -> {
            if (mc.level.hasChunkAt(p) && seen.add(p) && BotRules.needsRepair(want, mc.level.getBlockState(p))) {
                out.add(new WorkTasks.Target(p, want));
            }
        });
        return out;
    }

    /** What upkeep the house needs now, as one job: mended first, then a bed, then the yard lit; null if none (or none
     *  it can do). */
    static @Nullable Task guard(Minecraft mc) {
        if (Bot.settings.home == null) {
            return null;
        }
        Task t = mend(mc);
        if (t == null) {
            t = makeBed(mc);
        }
        if (t == null) {
            t = light(mc);
        }
        return t;
    }

    private static boolean fresh(String part) {
        Long at = tried.get(part);
        return at == null || Bot.tick() - at > RETRY;
    }

    /** A job that notes, if it fails, which part of the upkeep it was: that part is left a while, the others go on. */
    static Task part(String name, Task task) {
        return new Part(name, task);
    }

    /** A step that may fail without failing the job it is in (a craft of what may already be enough). */
    static Task tryIt(Task task) {
        return new Try(task);
    }

    private record Try(Task task) implements Task, Bot.Wrapper {
        @Override
        public Task inner() {
            return this.task;
        }

        @Override
        public String describe() {
            return this.task.describe();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            Result r = this.task.tick(mc, c);
            return r == Result.FAILED ? Result.DONE : r;
        }

        @Override
        public void cancel(Minecraft mc) {
            this.task.cancel(mc);
        }
    }

    /** A job of the upkeep, named for the part it is. */
    private static final class Part implements Task, Bot.Wrapper {
        private final String name;
        private final Task task;

        Part(String name, Task task) {
            this.name = name;
            this.task = task;
        }

        @Override
        public Task inner() {
            return this.task;
        }

        @Override
        public String describe() {
            return this.task.describe();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            Result r = this.task.tick(mc, c);
            if (r == Result.FAILED) {
                tried.put(this.name, Bot.tick());
            }
            return r;
        }

        @Override
        public void cancel(Minecraft mc) {
            this.task.cancel(mc);
        }
    }

    private static @Nullable Task mend(Minecraft mc) {
        if (!fresh("mend")) {
            return null;
        }
        List<WorkTasks.Target> missing = missing(mc);
        if (missing.isEmpty()) {
            return null;
        }
        LocalPlayer player = mc.player;
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (WorkTasks.Target t : missing) {
            need.merge(t.state().getBlock().asItem(), 1, Integer::sum);
        }
        List<Supplier<Task>> steps = new ArrayList<>();
        need.forEach((item, n) -> {
            int short_ = n - Inv.count(player, s -> s.is(item));
            if (short_ <= 0 || player.getAbilities().instabuild) {
                return;
            }
            if (Bot.settings.store != null) {
                steps.add(() -> new ItemTasks.Withdraw(Bot.settings.store, item, short_));
            }
            // what the store did not have, made if it can be (planks from logs, a door from planks)
            steps.add(() -> {
                int left = n - Inv.count(mc.player, s -> s.is(item));
                return left <= 0 || !Crafting.canCraft(mc, item) ? new MoveTasks.Wait(1) : tryIt(new Crafting.Craft(item, left));
            });
        });
        if (Bot.settings.store == null && !player.getAbilities().instabuild
                && need.keySet().stream().noneMatch(item -> Inv.count(player, s -> s.is(item)) > 0 || Crafting.canCraft(mc, item))) {
            return null; // nothing to mend it with, and nowhere to get it
        }
        steps.add(() -> {
            List<WorkTasks.Target> now = new ArrayList<>();
            for (WorkTasks.Target t : missing(mc)) {
                if (mc.player.getAbilities().instabuild || Inv.count(mc.player, s -> s.is(t.state().getBlock().asItem())) > 0) {
                    now.add(t);
                }
            }
            if (now.isEmpty()) {
                throw new IllegalArgumentException("nothing to mend the house with (" + missing.size() + " blocks missing)");
            }
            return new WorkTasks.Build(now, "mend the house");
        });
        Bot.event("guard", missing.size() + " blocks of the house missing, first " + missing.get(0).pos().toShortString() + " "
                + Bot.id(missing.get(0).state().getBlock()));
        return part("mend", new FarmTasks.Chain("mend the house (" + missing.size() + " missing)", steps));
    }

    private static @Nullable Task makeBed(Minecraft mc) {
        if (!fresh("bed") || bed(mc) != null) {
            return null;
        }
        LocalPlayer player = mc.player;
        Set<BlockPos> free = new HashSet<>();
        for (BlockPos p : roomCells(mc)) {
            if (freeFloor(mc, p)) {
                free.add(p);
            }
        }
        BotRules.BedSpot spot = BotRules.bedSpot(free, doors.keySet());
        if (spot == null) {
            return null;
        }
        int bedItem = Inv.find(player, s -> s.is(ItemTags.BEDS));
        Item bed;
        Task craft = null;
        if (bedItem >= 0) {
            bed = player.getInventory().getItem(bedItem).getItem();
        } else {
            // three wool of a colour and planks: a bed of that colour
            Item wool = null;
            for (int i = 0; i < 36; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (s.is(ItemTags.WOOL) && Inv.count(player, x -> x.is(s.getItem())) >= 3) {
                    wool = s.getItem();
                    break;
                }
            }
            if (wool == null) {
                return null;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(wool);
            bed = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(id.getNamespace(), id.getPath().replace("_wool", "_bed")));
            if (bed == Items.AIR) {
                return null;
            }
            craft = new Crafting.Craft(bed, 1);
        }
        if (!(bed instanceof net.minecraft.world.item.BlockItem block)) {
            return null;
        }
        BlockState state = block.getBlock().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, spot.facing());
        Task place = new WorkTasks.Build(List.of(new WorkTasks.Target(spot.foot(), state)), "set a bed in the house");
        Bot.event("guard", "no bed in the house: one goes at " + spot.foot().toShortString() + " facing " + spot.facing());
        if (craft == null) {
            return part("bed", place);
        }
        Task made = craft;
        return part("bed", new FarmTasks.Chain("make and set a bed", List.of(() -> made, () -> place)));
    }

    /** Ground cells round home a mob could stand in, with their block light. */
    static Map<BlockPos, Integer> yard(Minecraft mc) {
        BlockPos home = Bot.settings.home;
        var level = mc.level;
        Map<BlockPos, Integer> out = new LinkedHashMap<>();
        Set<BlockPos> planned = new HashSet<>();
        for (BotRules.Plan p : plans) {
            planned.addAll(p.blocks().keySet());
        }
        int r = BotRules.YARD;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                for (int dy = -3; dy <= 1; dy++) { // not up on the roof (a build that high digs dirt to climb on)
                    BlockPos p = home.offset(dx, dy, dz);
                    if (!level.hasChunkAt(p) || planned.contains(p)) {
                        continue;
                    }
                    BlockState s = level.getBlockState(p);
                    BlockState below = level.getBlockState(p.below());
                    if (!(s.isAir() || s.canBeReplaced() && s.getFluidState().isEmpty() && !s.is(BlockTags.CROPS))
                            || !BotRules.standable(level, p) || !below.isFaceSturdy(level, p.below(), Direction.UP)
                            || below.is(BlockTags.LEAVES) || below.is(net.neoforged.neoforge.common.Tags.Blocks.GLASS_BLOCKS)) {
                        continue;
                    }
                    out.put(p.immutable(), level.getBrightness(LightLayer.BLOCK, p));
                }
            }
        }
        return out;
    }

    private static @Nullable Task light(Minecraft mc) {
        if (!fresh("light")) {
            return null;
        }
        LocalPlayer player = mc.player;
        int torches = player.getAbilities().instabuild ? 64 : Inv.count(player, s -> s.is(Items.TORCH));
        if (torches == 0) {
            return null;
        }
        List<BlockPos> spots = BotRules.torchSpots(yard(mc), BotRules.YARD_LIGHT, Math.min(torches, 8));
        if (spots.isEmpty()) {
            return null;
        }
        List<WorkTasks.Target> targets = new ArrayList<>();
        for (BlockPos p : spots) {
            targets.add(new WorkTasks.Target(p, Blocks.TORCH.defaultBlockState()));
        }
        Bot.event("guard", "the yard is dark: " + spots.size() + " torches, first at " + spots.get(0).toShortString());
        return part("light", new WorkTasks.Build(targets, "light the yard"));
    }

    /** Walks within reach of a door, from inside if it can, and shuts it. */
    static final class ShutDoor implements Task {
        private final BlockPos door;
        private @Nullable Goal goal;
        private int tries;

        ShutDoor(BlockPos door) {
            this.door = door;
        }

        @Override
        public String describe() {
            return "shut the door at " + this.door.toShortString();
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            BlockState s = mc.level.getBlockState(this.door);
            if (!(s.getBlock() instanceof DoorBlock) || !s.getValue(DoorBlock.OPEN)) {
                return Result.DONE;
            }
            if (!Acts.inReach(mc.player, Vec3.atCenterOf(this.door))) {
                if (this.goal == null) {
                    List<BlockPos> in = new ArrayList<>();
                    for (BlockPos p : roomCells(mc)) {
                        if (p.distSqr(this.door) <= 6 && !p.equals(this.door) && freeFloor(mc, p)) {
                            in.add(p);
                        }
                    }
                    this.goal = in.isEmpty() ? new WorkTasks.ReachGoal(this.door) : Goal.any(in);
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get to the door: " + Bot.walker.failure());
                    return Result.FAILED;
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    return Result.RUNNING;
                }
            }
            Bot.walker.stop(mc);
            return ItemTasks.useBlock(mc, c, this.door, ++this.tries) ? Result.RUNNING : Result.FAILED;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** Staying in, a creeper outside the wall: to the room's far side from it. */
    static @Nullable Task awayInside(Minecraft mc, Vec3 from) {
        List<BlockPos> cells = new ArrayList<>();
        for (BlockPos p : roomCells(mc)) {
            if (freeFloor(mc, p) && Vec3.atBottomCenterOf(p).distanceTo(from) >= 4.0) {
                cells.add(p);
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        cells.sort(Comparator.comparingDouble(p -> -Vec3.atBottomCenterOf(p).distanceToSqr(from)));
        return new MoveTasks.Goto(Goal.any(cells.subList(0, Math.min(3, cells.size()))), "to the far side of the room");
    }

    // ---- the field: harvested, planted, bone-mealed

    /** Seeds it plants, and what each comes from. */
    private static final Item[] SEEDS = {Items.WHEAT_SEEDS, Items.CARROT, Items.POTATO, Items.BEETROOT_SEEDS};
    /** How far round home the field may be. */
    static final int FIELD = 16;

    /** The field's upkeep, as a job, or null: ripe crops picked, bare farmland planted, bone meal on what grows, the
     *  pen's animals bred. */
    static @Nullable Task farm(Minecraft mc) {
        BlockPos home = Bot.settings.home;
        if (home == null) {
            return null;
        }
        LocalPlayer player = mc.player;
        var level = mc.level;
        List<BlockPos> ripe = BotRules.ripeCrops(level, home, FIELD);
        if (!ripe.isEmpty() && fresh("harvest")) {
            BlockPos first = ripe.get(0);
            return part("harvest", new FarmTasks.Chain("harvest the field", List.of(
                    () -> new MoveTasks.Goto(FarmTasks.reach(first), "to the field"), () -> new FarmTasks.Harvest(10))));
        }
        List<BlockPos> bare = BotRules.plantTargets(level, home, FIELD);
        if (!bare.isEmpty() && fresh("plant")) {
            for (Item seed : SEEDS) {
                if (Inv.count(player, s -> s.is(seed)) > (seed == Items.WHEAT_SEEDS ? 0 : 1)) {
                    BlockPos first = bare.get(0);
                    String[] a = {"plant", Bot.name(seed), "" + first.getX(), "" + first.getY(), "" + first.getZ(), "8"};
                    return part("plant", FarmTasks.plant(mc, a));
                }
            }
            if (fresh("seeds")) {
                return part("seeds", new WorkTasks.Mine(WorkTasks.blocks("minecraft:short_grass,minecraft:tall_grass"), 24, 16));
            }
        }
        Task breed = penWork(mc, true);
        if (breed != null) {
            return breed;
        }
        if (fresh("bonemeal") && (Inv.count(player, s -> s.is(Items.BONE_MEAL)) > 0 || Inv.count(player, s -> s.is(Items.BONE)) > 0)) {
            List<BlockPos> growing = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(home.offset(-FIELD, -2, -FIELD), home.offset(FIELD, 2, FIELD))) {
                BlockState s = level.getBlockState(p);
                if (s.getBlock() instanceof CropBlock crop && !crop.isMaxAge(s)) {
                    growing.add(p.immutable());
                }
            }
            if (!growing.isEmpty()) {
                growing.sort(Comparator.comparingDouble(p -> p.distSqr(player.blockPosition())));
                List<Supplier<Task>> steps = new ArrayList<>();
                if (Inv.count(player, s -> s.is(Items.BONE_MEAL)) == 0) {
                    steps.add(() -> new Crafting.Craft(Items.BONE_MEAL, 3));
                }
                for (BlockPos p : growing.subList(0, Math.min(6, growing.size()))) {
                    steps.add(() -> Inv.count(mc.player, s -> s.is(Items.BONE_MEAL)) > 0 ? new FarmTasks.UseOn(Items.BONE_MEAL, p) : new MoveTasks.Wait(1));
                }
                return part("bonemeal", new FarmTasks.Chain("bone meal on the field", steps));
            }
        }
        return null;
    }

    // ---- the pen

    /** The kinds it keeps, with what leads and breeds them. */
    record Kind(EntityType<?> type, Predicate<ItemStack> food) {}

    static final List<Kind> KINDS = List.of(
            new Kind(EntityTypes.CHICKEN, s -> s.is(ItemTags.CHICKEN_FOOD)),
            new Kind(EntityTypes.COW, s -> s.is(ItemTags.COW_FOOD)),
            new Kind(EntityTypes.SHEEP, s -> s.is(ItemTags.SHEEP_FOOD)),
            new Kind(EntityTypes.PIG, s -> s.is(ItemTags.PIG_FOOD)));

    static BotRules.@Nullable Pen pen() {
        return Bot.settings.pen == null || Bot.settings.home == null ? null : BotRules.pen(Bot.settings.pen, Bot.settings.home);
    }

    static boolean penBuilt(Minecraft mc, BotRules.Pen pen) {
        for (BlockPos p : pen.fence()) {
            if (!mc.level.getBlockState(p).is(BlockTags.FENCES)) {
                return false;
            }
        }
        return mc.level.getBlockState(pen.gate()).getBlock() instanceof FenceGateBlock;
    }

    /** The animals in the pen, by kind. */
    static Map<Kind, List<Animal>> inPen(Minecraft mc, BotRules.Pen pen) {
        Map<Kind, List<Animal>> out = new LinkedHashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Animal a && a.isAlive() && pen.inside(a.blockPosition())) {
                for (Kind k : KINDS) {
                    if (k.type() == a.getType()) {
                        out.computeIfAbsent(k, x -> new ArrayList<>()).add(a);
                    }
                }
            }
        }
        return out;
    }

    /** The pen's kind: the one in it most. */
    static @Nullable Kind penKind(Map<Kind, List<Animal>> in) {
        Kind best = null;
        for (var e : in.entrySet()) {
            if (best == null || e.getValue().size() > in.get(best).size()) {
                best = e.getKey();
            }
        }
        return best;
    }

    static int adults(@Nullable List<Animal> list) {
        return list == null ? 0 : (int) list.stream().filter(a -> !a.isBaby()).count();
    }

    /** Breeding (or, {@code breedOnly} false, an animal more than the pen keeps eaten) as a job, or null. */
    static @Nullable Task penWork(Minecraft mc, boolean breedOnly) {
        BotRules.Pen pen = pen();
        if (pen == null || !penBuilt(mc, pen)) {
            return null;
        }
        var in = inPen(mc, pen);
        Kind kind = penKind(in);
        if (kind == null) {
            return null;
        }
        int adults = adults(in.get(kind));
        int babies = in.get(kind).size() - adults;
        boolean food = Inv.find(mc.player, kind.food()) >= 0;
        if (fresh("breed") && BotRules.penBreeds(adults, babies, food, Bot.tick() - bredAt < BotRules.BREED_EVERY)) {
            return part("breed", new PenAct(pen, kind, true));
        }
        if (!breedOnly && fresh("cull") && adults > BotRules.PEN_KEEP) {
            return part("cull", new PenAct(pen, kind, false));
        }
        return null;
    }

    /** At the pen, over the fence: two adults fed (to breed), or one struck down (for meat). */
    static final class PenAct implements Task {
        private final BotRules.Pen pen;
        private final Kind kind;
        private final boolean feed;
        private final Set<UUID> done = new HashSet<>();
        private @Nullable UUID target;
        private @Nullable Goal goal;
        private int ticks;
        private int hits;

        PenAct(BotRules.Pen pen, Kind kind, boolean feed) {
            this.pen = pen;
            this.kind = kind;
            this.feed = feed;
        }

        @Override
        public String describe() {
            return (this.feed ? "breed the " : "take one of the ") + this.kind.type().toShortString() + "s in the pen (" + this.done.size() + " done)";
        }

        private @Nullable Animal pick(Minecraft mc) {
            Animal best = null;
            for (Animal a : inPen(mc, this.pen).getOrDefault(this.kind, List.of())) {
                if (a.isBaby() || this.done.contains(a.getUUID())) {
                    continue;
                }
                if (this.target != null && a.getUUID().equals(this.target)) {
                    return a;
                }
                if (best == null || a.distanceToSqr(mc.player) < best.distanceToSqr(mc.player)) {
                    best = a;
                }
            }
            return best;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            if (++this.ticks > 2400) {
                Bot.fail("the pen took too long");
                return Result.FAILED;
            }
            if (this.done.size() >= (this.feed ? 2 : 1)) {
                if (this.feed) {
                    bredAt = Bot.tick();
                }
                Bot.walker.stop(mc);
                return Result.DONE;
            }
            if (!this.feed && adults(inPen(mc, this.pen).get(this.kind)) <= BotRules.PEN_KEEP) {
                return this.done.isEmpty() ? fail("only " + BotRules.PEN_KEEP + " left in the pen") : Result.DONE;
            }
            Animal a = this.pick(mc);
            if (a == null) {
                return fail("no " + this.kind.type().toShortString() + " to " + (this.feed ? "feed" : "take") + " in the pen");
            }
            if (!a.getUUID().equals(this.target)) {
                this.target = a.getUUID();
                this.goal = null;
            }
            double reach = me.entityInteractionRange() - 0.3;
            Vec3 aim = a.getBoundingBox().getCenter();
            if (me.getEyePosition().distanceTo(aim) > reach) {
                if (this.goal == null || Bot.walker.state() == Walker.State.DONE || Bot.walker.state() == Walker.State.FAILED) {
                    List<BlockPos> ring = new ArrayList<>(this.pen.ring());
                    ring.removeIf(p -> !freeFloor(mc, p));
                    ring.sort(Comparator.comparingDouble(p -> Vec3.atBottomCenterOf(p).distanceToSqr(a.position())));
                    if (ring.isEmpty()) {
                        return fail("nowhere to stand by the pen");
                    }
                    this.goal = Goal.any(ring.subList(0, Math.min(2, ring.size())));
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    return fail("cannot get round the pen: " + Bot.walker.failure());
                }
                return Result.RUNNING;
            }
            Bot.walker.stop(mc);
            c.lookAt(me, aim);
            if (!Controls.looking(me, aim, 10.0F)) {
                return Result.RUNNING;
            }
            if (this.feed) {
                if (!this.kind.food().test(me.getMainHandItem())) {
                    Inv.hold(mc, Inv.find(me, this.kind.food()));
                    return Result.RUNNING;
                }
                if (this.ticks % 10 == 0) {
                    mc.gameMode.interact(me, a, new EntityHitResult(a, aim), InteractionHand.MAIN_HAND);
                    Acts.swing(mc, false);
                    this.done.add(a.getUUID());
                    this.target = null;
                }
                return Result.RUNNING;
            }
            int weapon = Inv.bestWeapon(me);
            if (weapon >= 0) {
                Inv.hold(mc, weapon);
            }
            if (me.getAttackStrengthScale(0.5F) >= 1.0F) {
                Acts.attack(mc, a);
                this.hits++;
            }
            if (!a.isAlive() || a.isDeadOrDying()) {
                this.done.add(a.getUUID());
                this.target = null;
            }
            return Result.RUNNING;
        }

        private static Result fail(String why) {
            Bot.fail(why);
            return Result.FAILED;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
        }
    }

    /** The pen's fence and gate set down, the fence and gate made first from the wood it has. */
    static @Nullable Task buildPen(Minecraft mc, BotRules.Pen pen) {
        LocalPlayer player = mc.player;
        String wood = wood(player);
        if (wood == null && !player.getAbilities().instabuild) {
            return null;
        }
        if (wood == null) {
            wood = "oak";
        }
        Item fence = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(wood + "_fence"));
        Item gate = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(wood + "_fence_gate"));
        if (fence == Items.AIR || gate == Items.AIR) {
            return null;
        }
        List<WorkTasks.Target> targets = new ArrayList<>();
        int fences = 0;
        for (BlockPos p : pen.fence()) {
            if (!mc.level.getBlockState(p).is(BlockTags.FENCES)) {
                targets.add(new WorkTasks.Target(p, ((net.minecraft.world.item.BlockItem) fence).getBlock().defaultBlockState()));
                fences++;
            }
        }
        if (!(mc.level.getBlockState(pen.gate()).getBlock() instanceof FenceGateBlock)) {
            targets.add(new WorkTasks.Target(pen.gate(), ((net.minecraft.world.item.BlockItem) gate).getBlock().defaultBlockState()
                    .setValue(FenceGateBlock.FACING, pen.gateSide().getOpposite())));
        }
        List<Supplier<Task>> steps = new ArrayList<>();
        int needFences = fences;
        if (!player.getAbilities().instabuild) {
            steps.add(() -> {
                int have = Inv.count(mc.player, s -> s.is(fence));
                return have >= needFences ? new MoveTasks.Wait(1) : new Crafting.Craft(fence, needFences - have);
            });
            steps.add(() -> Inv.count(mc.player, s -> s.is(gate)) > 0 ? new MoveTasks.Wait(1) : new Crafting.Craft(gate, 1));
        }
        steps.add(() -> new WorkTasks.Build(targets, "build the pen at " + pen.centre().toShortString()));
        return part("pen", new FarmTasks.Chain("build the pen", steps));
    }

    /** Planks (or logs) enough for the pen's fence and gate: about 36 planks. */
    static boolean penMaterials(LocalPlayer player) {
        return player.getAbilities().instabuild || wood(player) != null
                && Inv.count(player, s -> s.is(ItemTags.PLANKS)) + 4 * Inv.count(player, s -> s.is(ItemTags.LOGS)) >= 36;
    }

    /** The kind of wood it has most of, as a name ("oak", "spruce"), or null. */
    static @Nullable String wood(LocalPlayer player) {
        Map<String, Integer> by = new HashMap<>();
        for (int i = 0; i < 36; i++) {
            ItemStack s = player.getInventory().getItem(i);
            String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (s.is(ItemTags.PLANKS) && path.endsWith("_planks")) {
                by.merge(path.substring(0, path.length() - "_planks".length()), s.getCount(), Integer::sum);
            } else if (s.is(ItemTags.LOGS) && path.endsWith("_log") && !path.startsWith("stripped_")) {
                by.merge(path.substring(0, path.length() - "_log".length()), 4 * s.getCount(), Integer::sum);
            }
        }
        return by.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    /**
     * Two animals of a kind led into the pen: to where they were seen (if none is about), the food that leads them in
     * the hand, walking slowly and waiting for them; into the pen with the gate held open; then out, the hand emptied,
     * and the gate shut.
     */
    static final class Lead implements Task {
        private final Kind kind;
        private final BotRules.Pen pen;
        private final @Nullable BlockPos place;
        private final List<UUID> led = new ArrayList<>();
        private int phase;
        private int ticks;
        private int waited;
        private int tries;
        private @Nullable Goal goal;
        private @Nullable Boolean sprintWas;

        Lead(Kind kind, BotRules.Pen pen, @Nullable BlockPos place) {
            this.kind = kind;
            this.pen = pen;
            this.place = place;
        }

        @Override
        public String describe() {
            return "fetch " + this.kind.type().toShortString() + "s to the pen (" + this.led.size() + " led, phase " + this.phase + ")";
        }

        private List<Animal> nearby(Minecraft mc, double r) {
            List<Animal> out = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Animal a && a.getType() == this.kind.type() && a.isAlive() && !a.isBaby() && a.distanceTo(mc.player) < r
                        && !this.pen.inside(a.blockPosition())) {
                    out.add(a);
                }
            }
            out.sort(Comparator.comparingDouble(a -> a.distanceToSqr(mc.player)));
            return out;
        }

        private List<Animal> ledAnimals(Minecraft mc) {
            List<Animal> out = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Animal a && a.isAlive() && this.led.contains(a.getUUID())) {
                    out.add(a);
                }
            }
            return out;
        }

        private Result fail(String why) {
            Bot.fail(why);
            return Result.FAILED;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer me = mc.player;
            if (++this.ticks > 12000) {
                return this.fail("the fetch took too long");
            }
            if (this.phase == 0) {
                if (this.nearby(mc, 16).isEmpty() && this.place != null) {
                    if (this.goal == null) {
                        this.goal = Goal.near(this.place, 6.0);
                        Bot.walker.go(this.goal);
                    }
                    Bot.walker.tick(mc, c);
                    if (Bot.walker.state() == Walker.State.FAILED) {
                        return this.fail("cannot get to where they were seen: " + Bot.walker.failure());
                    }
                    if (Bot.walker.state() != Walker.State.DONE) {
                        return Result.RUNNING;
                    }
                }
                List<Animal> them = this.nearby(mc, 16);
                if (them.isEmpty()) {
                    if (this.place != null) {
                        gone.add(this.place); // not looked for there again
                    }
                    return this.fail("no " + this.kind.type().toShortString() + " about here any more");
                }
                for (Animal a : them.subList(0, Math.min(2, them.size()))) {
                    this.led.add(a.getUUID());
                }
                this.phase = 1;
                this.goal = null;
                this.sprintWas = Bot.settings.sprint; // led at a walk: put back at the end
                Bot.settings.sprint = false;
                Bot.event("fetch", "leading " + this.led.size() + " " + this.kind.type().toShortString() + " to the pen");
            }
            List<Animal> them = this.ledAnimals(mc);
            Inv.holdItem(mc, this.kind.food(), ItemStack.EMPTY);
            if (this.phase == 1) {
                // to the pen, slowly, waiting for them
                them.removeIf(a -> a.distanceTo(me) > 16);
                if (them.isEmpty()) {
                    return this.fail("they would not follow");
                }
                Animal last = them.stream().max(Comparator.comparingDouble(a -> a.distanceToSqr(me))).get();
                BlockPos feet = Walker.feetCell(me);
                if (this.pen.inside(feet)) {
                    Bot.walker.stop(mc);
                    this.phase = 2;
                    this.waited = 0;
                    return Result.RUNNING;
                }
                if (last.distanceTo(me) > 6.0) {
                    Bot.walker.stop(mc);
                    this.goal = null;
                    c.lookAt(me, last.getEyePosition());
                    if (++this.waited > 600) {
                        return this.fail("they would not follow");
                    }
                    return Result.RUNNING;
                }
                this.waited = 0;
                if (this.goal == null || !Bot.walker.isFor(this.goal)) {
                    this.goal = Goal.near(this.pen.centre(), 0.5);
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                c.sprint = false;
                if (Bot.walker.state() == Walker.State.FAILED) {
                    return this.fail("cannot get into the pen: " + Bot.walker.failure());
                }
                return Result.RUNNING;
            }
            BlockState gate = mc.level.getBlockState(this.pen.gate());
            boolean open = gate.getBlock() instanceof FenceGateBlock && gate.getValue(FenceGateBlock.OPEN);
            if (this.phase == 2) {
                // in the pen: the gate held open until they are in too
                if (!open) {
                    if (!ItemTasks.useBlock(mc, c, this.pen.gate(), ++this.tries)) {
                        return Result.FAILED;
                    }
                    Inv.holdItem(mc, this.kind.food(), ItemStack.EMPTY);
                    return Result.RUNNING;
                }
                this.tries = 0;
                boolean in = !them.isEmpty() && them.stream().allMatch(a -> this.pen.inside(a.blockPosition()));
                if (!them.isEmpty()) {
                    c.lookAt(me, them.get(0).getEyePosition());
                }
                if (in || ++this.waited > 400) {
                    if (them.stream().noneMatch(a -> this.pen.inside(a.blockPosition()))) {
                        return this.fail("they would not come into the pen");
                    }
                    Inv.select(mc, Inv.emptyHandSlot(me)); // nothing tempting in the hand: they stay
                    this.phase = 3;
                    this.goal = null;
                }
                return Result.RUNNING;
            }
            if (this.phase == 3) {
                // out of the gate, and shut it
                Inv.select(mc, Inv.emptyHandSlot(me));
                if (this.goal == null) {
                    this.goal = Goal.at(this.pen.gate().relative(this.pen.gateSide(), 1));
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    return this.fail("cannot get out of the pen: " + Bot.walker.failure());
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    return Result.RUNNING;
                }
                this.phase = 4;
                this.tries = 0;
            }
            if (open) {
                return ItemTasks.useBlock(mc, c, this.pen.gate(), ++this.tries) ? Result.RUNNING : Result.FAILED;
            }
            int n = (int) them.stream().filter(a -> this.pen.inside(a.blockPosition())).count();
            Bot.event("fetch", n + " " + this.kind.type().toShortString() + " in the pen");
            return Result.DONE;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.sprintWas != null) {
                Bot.settings.sprint = this.sprintWas;
                this.sprintWas = null;
            }
        }
    }

    /** Hunting: to a place animals were seen (if none is about), then animals of the kind struck down while more than two
     *  are about (some kept for later), at most two; what drops picked up. */
    static final class Hunt implements Task {
        private final EntityType<?> kind;
        private final @Nullable BlockPos place;
        private @Nullable Goal goal;
        private FightTasks.@Nullable Attack fight;
        private MoveTasks.@Nullable Collect collect;
        private int kills;
        private boolean there;

        Hunt(EntityType<?> kind, @Nullable BlockPos place) {
            this.kind = kind;
            this.place = place;
        }

        @Override
        public String describe() {
            return "hunt " + this.kind.toShortString() + " (" + this.kills + " taken)";
        }

        private List<Animal> about(Minecraft mc) {
            List<Animal> out = new ArrayList<>();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof Animal a && a.getType() == this.kind && a.isAlive() && !a.isBaby() && a.distanceTo(mc.player) < 32) {
                    BotRules.Pen pen = pen();
                    if (pen == null || !pen.inside(a.blockPosition())) {
                        out.add(a);
                    }
                }
            }
            out.sort(Comparator.comparingDouble(a -> a.distanceToSqr(mc.player)));
            return out;
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            if (this.collect != null) {
                Result r = this.collect.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                this.collect = null;
            }
            if (this.fight != null) {
                Result r = this.fight.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                this.fight.cancel(mc);
                this.fight = null;
                if (r == Result.DONE) {
                    this.kills++;
                    this.collect = new MoveTasks.Collect(8.0, null);
                }
                return Result.RUNNING;
            }
            if (!this.there && this.about(mc).size() <= BotRules.PEN_KEEP && this.place != null) {
                if (this.goal == null) {
                    this.goal = Goal.near(this.place, 8.0);
                    Bot.walker.go(this.goal);
                }
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get to where they were seen: " + Bot.walker.failure());
                    return Result.FAILED;
                }
                if (Bot.walker.state() != Walker.State.DONE) {
                    return Result.RUNNING;
                }
            }
            this.there = true;
            List<Animal> them = this.about(mc);
            if (this.kills >= 2 || them.size() <= BotRules.PEN_KEEP) {
                if (this.kills == 0) {
                    if (this.place != null) {
                        gone.add(this.place);
                    }
                    Bot.fail("only " + them.size() + " " + this.kind.toShortString() + " about: left for later");
                    return Result.FAILED;
                }
                return Result.DONE;
            }
            this.fight = new FightTasks.Attack(them.get(0).getUUID(), 32.0);
            return Result.RUNNING;
        }

        @Override
        public void cancel(Minecraft mc) {
            Bot.walker.stop(mc);
            if (this.fight != null) {
                this.fight.cancel(mc);
            }
        }
    }

    // ---- the larder: food kept in stock

    /** Raw food it cooks. */
    static final Item[] RAW = {Items.BEEF, Items.PORKCHOP, Items.MUTTON, Items.CHICKEN, Items.COD, Items.SALMON, Items.POTATO, Items.RABBIT};

    /** Food in the bag worth a meal: cooked meat and fish, bread, baked potatoes, apples - not raw, not harmful. */
    static int cooked(LocalPlayer player) {
        return Inv.count(player, s -> Inv.nutrition(s) >= 4 && !Inv.harmful(s) && !Inv.alwaysEdible(s) && !isRaw(s));
    }

    static boolean isRaw(ItemStack s) {
        for (Item raw : RAW) {
            if (s.is(raw)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> notes = List.of();
    private static long notesRead = -1;

    /** The explore notes (run-agent/explore_notes.txt), read again when they change. */
    static List<String> notes() {
        Path file = Bot.dir().resolve("explore_notes.txt");
        try {
            long at = Files.exists(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
            if (at != notesRead) {
                notesRead = at;
                notes = at == 0 ? List.of() : Files.readAllLines(file, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // as they were
        }
        return notes;
    }

    /** The nearest place an explore saw animals of a kind, within 200 of home, or null. */
    /** Places seen with animals that were not there (or too few) when it went: not gone to again. */
    static final Set<BlockPos> gone = new HashSet<>();

    static @Nullable BlockPos known(EntityType<?> type, BlockPos home) {
        for (BlockPos p : BotRules.animalPlaces(notes(), type.toShortString(), home)) {
            if (p.closerThan(home, 200) && gone.stream().noneMatch(g -> g.closerThan(p, 24))) {
                return p;
            }
        }
        return null;
    }

    /** The kinds about within 48 of it, with how many adults (the pen's aside). */
    static Map<EntityType<?>, Integer> about(Minecraft mc) {
        Map<EntityType<?>, Integer> out = new HashMap<>();
        BotRules.Pen pen = pen();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Animal a && a.isAlive() && !a.isBaby() && a.distanceTo(mc.player) < 48 && (pen == null || !pen.inside(a.blockPosition()))) {
                for (Kind k : KINDS) {
                    if (k.type() == a.getType()) {
                        out.merge(a.getType(), 1, Integer::sum);
                    }
                }
            }
        }
        return out;
    }

    /** The next step to keep food in stock, as a job, or null (stocked, or nothing to do it with). */
    static @Nullable Task stock(Minecraft mc) {
        LocalPlayer player = mc.player;
        BlockPos home = Bot.settings.home != null ? Bot.settings.home : player.blockPosition();
        int cooked = cooked(player);
        if (cooked >= Bot.settings.foodStock) {
            return null;
        }
        Item raw = Items.AIR;
        int rawCount = 0;
        for (Item r : RAW) {
            int n = Inv.count(player, s -> s.is(r));
            if (n > 0) {
                raw = r;
                rawCount = n;
                break;
            }
        }
        BotRules.Pen pen = pen();
        boolean built = pen != null && penBuilt(mc, pen);
        var in = built ? inPen(mc, pen) : Map.<Kind, List<Animal>>of();
        Kind penKind = penKind(in);
        Map<EntityType<?>, Integer> near = about(mc);
        // the kind to fetch to an empty pen: one whose food is in the bag, seen nearest
        Kind fetch = penKind;
        BlockPos fetchAt = null;
        if (fetch == null) {
            double best = Double.MAX_VALUE;
            for (Kind k : KINDS) {
                if (Inv.find(player, k.food()) < 0) {
                    continue;
                }
                BlockPos at = near.getOrDefault(k.type(), 0) >= 1 ? player.blockPosition() : known(k.type(), home);
                if (at != null && at.distSqr(home) < best) {
                    best = at.distSqr(home);
                    fetch = k;
                    fetchAt = at.equals(player.blockPosition()) ? null : at;
                }
            }
        } else {
            fetchAt = near.getOrDefault(fetch.type(), 0) >= 1 ? null : known(fetch.type(), home);
        }
        // the kind to hunt: two or more about, else the nearest place seen
        EntityType<?> hunt = null;
        BlockPos huntAt = null;
        for (var e : near.entrySet()) {
            if (e.getValue() > BotRules.PEN_KEEP) {
                hunt = e.getKey();
            }
        }
        if (hunt == null) {
            double best = Double.MAX_VALUE;
            for (Kind k : KINDS) {
                BlockPos at = known(k.type(), home);
                if (at != null && at.distSqr(home) < best) {
                    best = at.distSqr(home);
                    hunt = k.type();
                    huntAt = at;
                }
            }
        }
        Set<FoodStep> failed = EnumSet.noneOf(FoodStep.class);
        for (FoodStep s : FoodStep.values()) {
            if (!fresh(s.name())) {
                failed.add(s);
            }
        }
        int adults = penKind == null ? 0 : adults(in.get(penKind));
        BotRules.Larder l = new BotRules.Larder(cooked, Bot.settings.foodStock, rawCount,
                Crafting.canCraft(mc, Items.BREAD) ? Inv.count(player, s -> s.is(Items.WHEAT)) : 0, ItemTasks.bestFuel(player) >= 0 || player.getAbilities().instabuild, pen != null, built,
                penMaterials(player), adults, penKind == null ? 0 : in.get(penKind).size() - adults,
                penKind != null && Inv.find(player, penKind.food()) >= 0, Bot.tick() - bredAt < BotRules.BREED_EVERY,
                fetch != null && Inv.find(player, fetch.food()) >= 0, hunt != null && huntAt == null,
                fetchAt != null || huntAt != null, BotRules.farTrip(mc.level.getDefaultClockTime(), player.getHealth()),
                (Inv.find(player, s -> s.is(Items.FISHING_ROD)) >= 0 || Inv.count(player, s -> s.is(Items.STRING)) >= 2
                        || player.getAbilities().instabuild) && BotRules.fishingSpot(mc.level, home, 16) != null, failed);
        FoodStep step = BotRules.foodStep(l);
        Task task = switch (step) {
            case NONE -> null;
            case COOK -> new ItemTasks.Smelt(raw, Math.min(rawCount, 8), null);
            case BREAD -> new Crafting.Craft(Items.BREAD, Inv.count(player, s -> s.is(Items.WHEAT)) / 3);
            case CULL -> new PenAct(pen, penKind, false);
            case BREED -> new PenAct(pen, penKind, true);
            case BUILD_PEN -> buildPen(mc, pen);
            case FETCH -> new Lead(fetch, pen, fetchAt);
            case HUNT -> new Hunt(hunt, huntAt);
            case FISH -> Inv.find(player, s -> s.is(Items.FISHING_ROD)) >= 0 || player.getAbilities().instabuild ? new FishTasks.Fish(5)
                    : new FarmTasks.Chain("fish for food", List.of(() -> new Crafting.Craft(Items.FISHING_ROD, 1), () -> new FishTasks.Fish(5)));
        };
        if (task == null) {
            return null;
        }
        Bot.event("larder", cooked + " of " + Bot.settings.foodStock + " meals in the bag: " + step.name().toLowerCase());
        return part(step.name(), task);
    }
}
