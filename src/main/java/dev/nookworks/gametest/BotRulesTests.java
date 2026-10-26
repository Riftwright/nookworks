package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.dev.bot.BotRules;
import dev.nookworks.dev.bot.BotRules.Survive;
import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The agent bot's judgements that need no game (dev tooling, dev.nookworks.dev.bot.BotRules), from what went wrong
 * when it played beside the user on 2026-10-21: it fought on nearly dead, a goto's three numbers were read as X Y Z
 * when X Z R was meant, and a "now" waited behind the jobs it was to replace.
 */
final class BotRulesTests {
    private BotRulesTests() {}

    static void register() {
        test("bot_rules_hurt_backs_off_or_eats", BotRulesTests::survive);
        test("bot_rules_goto_height_checked", BotRulesTests::gotoHeight);
        test("bot_rules_now_jumps_the_input", BotRulesTests::jumpTo);
        test("bot_rules_column_near_any_height", BotRulesTests::columnNear);
        test("bot_rules_farm_ground_and_ripe_crops", BotRulesTests::farm);
        test("bot_rules_fishing_spot_and_explore", BotRulesTests::fishingAndExplore);
        test("bot_rules_chores_in_order", BotRulesTests::chores);
        test("bot_rules_never_under_a_build", BotRulesTests::underBuild);
        test("bot_rules_fights_only_armed", h -> {
            retreat(h);
            mayFight(h);
        });
        test("bot_rules_home_plan_mended_bed_set", BotRulesTests::homePlan);
        test("bot_rules_yard_lit_with_torches", BotRulesTests::torches);
        test("bot_rules_nights_spent_in", BotRulesTests::nights);
        test("bot_rules_larder_kept", BotRulesTests::larderSteps);
        test("bot_rules_dusk_calls_it_home", h -> {
            dry(h);
            lakeBed(h);
            dusk(h);
        });
    }

    private static void survive(GameTestHelper helper) {
        double none = Double.POSITIVE_INFINITY;
        check(helper, BotRules.survive(20.0F, 2.0, true, false, Survive.CARRY_ON) == Survive.CARRY_ON, "healthy: fights on");
        check(helper, BotRules.survive(7.0F, 2.0, true, false, Survive.CARRY_ON) == Survive.CARRY_ON, "three and a half hearts: still fights");
        // the skeleton fight of the first evening: 1.5 health, apples in the bag, the skeleton near
        check(helper, BotRules.survive(1.5F, 6.0, true, false, Survive.CARRY_ON) == Survive.BACK_OFF, "nearly dead, a mob near: backs off");
        check(helper, BotRules.survive(6.0F, 3.0, false, false, Survive.CARRY_ON) == Survive.BACK_OFF, "three hearts, no food: backs off");
        check(helper, BotRules.survive(1.5F, none, true, false, Survive.CARRY_ON) == Survive.EAT, "nearly dead, nothing near: eats");
        check(helper, BotRules.survive(4.0F, 15.0, true, false, Survive.CARRY_ON) == Survive.EAT, "an archer far off: eats");
        check(helper, BotRules.survive(4.0F, none, false, false, Survive.CARRY_ON) == Survive.CARRY_ON, "nothing to eat, nothing near: as it was");
        check(helper, BotRules.survive(4.0F, 2.0, true, true, Survive.CARRY_ON) == Survive.FIGHT, "cornered: fights after all");
        // a zombie hovering about ten blocks off turned it from eating to running and back every few ticks
        check(helper, BotRules.survive(3.0F, 8.0, true, false, Survive.EAT) == Survive.EAT, "eating: a mob eight off does not stop it");
        check(helper, BotRules.survive(3.0F, 4.0, true, false, Survive.EAT) == Survive.BACK_OFF, "eating: a mob four off does");
        check(helper, BotRules.survive(3.0F, 30.0, true, false, Survive.BACK_OFF) == Survive.BACK_OFF, "running: runs on until done");
        check(helper, BotRules.survive(12.0F, 3.0, true, false, Survive.BACK_OFF) == Survive.CARRY_ON, "healed: as it was");
        helper.succeed();
    }

    private static void gotoHeight(GameTestHelper helper) {
        // "goto -22 252 4" meant x=-22 z=252 within 4, and sent it toward y=252, far above ground at 64
        String wrong = BotRules.gotoHeightProblem(-22, 252, 4, -64, 319, 64);
        check(helper, wrong != null && wrong.contains("188 blocks above") && wrong.contains("goto -22 ~ 4"), "far above the ground: " + wrong);
        check(helper, BotRules.gotoHeightProblem(0, 400, 0, -64, 319, Integer.MIN_VALUE) != null, "above the world");
        check(helper, BotRules.gotoHeightProblem(0, -70, 0, -64, 319, Integer.MIN_VALUE) != null, "below the world");
        check(helper, BotRules.gotoHeightProblem(0, 80, 0, -64, 319, 64) == null, "on a hill top or a roof: fine");
        check(helper, BotRules.gotoHeightProblem(0, 12, 0, -64, 319, 64) == null, "down in a mine: fine");
        check(helper, BotRules.gotoHeightProblem(0, 252, 0, -64, 319, Integer.MIN_VALUE) == null, "ground not known: let it try");
        helper.succeed();
    }

    private static void jumpTo(GameTestHelper helper) {
        check(helper, BotRules.jumpTo(List.of("bot smelt minecraft:cod 3", "botwait", "bot say done")) == -1, "no now: nothing skipped");
        check(helper, BotRules.jumpTo(List.of("bot smelt minecraft:cod 3", "botwait", "bot say done", "bot now goto 1 64 2")) == 3,
                "a now jumps over those before it");
        check(helper, BotRules.jumpTo(List.of("bot now goto 1 64 2", "bot say a", "bot stop", "bot say b")) == 2, "the last now or stop");
        check(helper, BotRules.jumpTo(List.of("bot say now", "say nowhere", "bot nowhere")) == -1, "only bot now / bot stop");
        helper.succeed();
    }

    private static void mayFight(GameTestHelper helper) {
        check(helper, !BotRules.mayFight(1.0, 20.0F, 0.0), "bare handed, just respawned: keeps its distance");
        check(helper, BotRules.mayFight(7.0, 20.0F, 0.0), "an iron sword: fights");
        check(helper, BotRules.mayFight(4.0, 12.0F, 0.0), "a wooden sword, hurt a little: fights");
        check(helper, !BotRules.mayFight(7.0, 5.0F, 20.0), "nearly dead: not even armed");
        check(helper, BotRules.mayFight(1.0, 20.0F, 15.0), "bare handed in iron armour at full health: fights");
        check(helper, !BotRules.mayFight(1.0, 14.0F, 15.0), "bare handed in armour, hurt: keeps its distance");
        helper.succeed();
    }

    private static void retreat(GameTestHelper helper) {
        double none = Double.POSITIVE_INFINITY;
        // by the hut at night, bare handed: in through the door, not off north leg by leg into the sea
        check(helper, BotRules.retreat(6.0, 20.0) == BotRules.Retreat.HOME, "near home: in");
        check(helper, BotRules.retreat(2.0, 20.0) == BotRules.Retreat.HOLD, "home already: stays");
        check(helper, BotRules.retreat(100.0, 10.0) == BotRules.Retreat.COMPANION, "far from home, the companion near: to them");
        check(helper, BotRules.retreat(none, 2.0) == BotRules.Retreat.HOLD, "beside the companion: stays");
        check(helper, BotRules.retreat(none, none) == BotRules.Retreat.FLEE, "alone, no home: away");
        check(helper, BotRules.retreatEndsJobs(BotRules.Retreat.HOME, 18000, 20.0F), "home at night: the jobs end");
        check(helper, BotRules.retreatEndsJobs(BotRules.Retreat.HOME, 6000, 4.0F), "home badly hurt by day: they end");
        check(helper, !BotRules.retreatEndsJobs(BotRules.Retreat.HOME, 6000, 18.0F), "home from one zombie by day: put aside");
        check(helper, !BotRules.retreatEndsJobs(BotRules.Retreat.FLEE, 18000, 4.0F), "no home to stay in: put aside");
        check(helper, BotRules.night(24000L * 5 + 13000) && !BotRules.night(1000) && !BotRules.night(23600), "night");
        check(helper, BotRules.retreat(none, none, true) == BotRules.Retreat.SURFACE, "alone in a cave: up, not deeper in");
        check(helper, BotRules.retreat(30.0, none, true) == BotRules.Retreat.HOME, "in a cave under home: home");
        check(helper, BotRules.underground(20, 64, 63) && !BotRules.underground(62, 64, 63) && BotRules.underground(-18, -18, 63)
                && !BotRules.underground(120, 121, 63), "underground: under rock, or at a ravine's floor; not on a mountain");
        check(helper, BotRules.wantsTorch(true, 0, 9.0, true) && !BotRules.wantsTorch(true, 0, 5.0, true)
                && !BotRules.wantsTorch(false, 0, 99.0, true) && !BotRules.wantsTorch(true, 12, 99.0, true)
                && !BotRules.wantsTorch(true, 0, 99.0, false), "a torch in the dark underground, every eight blocks");
        check(helper, BotRules.chore(10, true, true, true, true, 1, true, 20, true, java.util.Set.of()) == BotRules.Chore.TORCHES
                && BotRules.chore(10, true, true, true, true, 1, false, 20, true, java.util.Set.of()) == BotRules.Chore.NONE,
                "few torches: more made, if there is coal and wood");
        check(helper, BotRules.needsAir(80, true) && !BotRules.needsAir(80, false) && !BotRules.needsAir(250, true), "air");
    }

    /** The run's end: dry land, never in the sea (the shore, a shallow pool's edge are fine). */
    private static void dry(GameTestHelper helper) {
        helper.setBlock(new net.minecraft.core.BlockPos(1, 1, 1), net.minecraft.world.level.block.Blocks.WATER);
        helper.setBlock(new net.minecraft.core.BlockPos(1, 2, 1), net.minecraft.world.level.block.Blocks.WATER);
        var level = helper.getLevel();
        var swim = helper.absolutePos(new net.minecraft.core.BlockPos(1, 2, 1));
        var over = helper.absolutePos(new net.minecraft.core.BlockPos(1, 3, 1));
        var shore = helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 1));
        check(helper, !BotRules.dry(level, swim.getX(), swim.getY(), swim.getZ()), "in the water: no");
        check(helper, !BotRules.dry(level, over.getX(), over.getY(), over.getZ()), "floating on it: no");
        check(helper, BotRules.dry(level, shore.getX(), shore.getY(), shore.getZ()), "on the shore: yes");
    }

    /** A lake bed's stone, a shore's, stone in the rock; a doorway. */
    private static void lakeBed(GameTestHelper helper) {

        for (int x = 0; x <= 4; x++) {
            for (int z = 0; z <= 4; z++) {
                helper.setBlock(new net.minecraft.core.BlockPos(x, 1, z), net.minecraft.world.level.block.Blocks.STONE);
                helper.setBlock(new net.minecraft.core.BlockPos(x, 2, z), net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        // a pool two deep over (2, 2, 2)
        helper.setBlock(new net.minecraft.core.BlockPos(2, 2, 2), net.minecraft.world.level.block.Blocks.WATER);
        helper.setBlock(new net.minecraft.core.BlockPos(2, 3, 2), net.minecraft.world.level.block.Blocks.WATER);
        var level = helper.getLevel();
        check(helper, BotRules.onlyUnderWater(level, helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2))), "the pool's bed: under water only");
        check(helper, !BotRules.onlyUnderWater(level, helper.absolutePos(new net.minecraft.core.BlockPos(0, 2, 0))), "the bank, open to the air: fine");
        check(helper, !BotRules.onlyUnderWater(level, helper.absolutePos(new net.minecraft.core.BlockPos(0, 1, 0))), "deep in the rock: fine");
        var door = helper.absolutePos(new net.minecraft.core.BlockPos(4, 3, 4));
        helper.setBlock(new net.minecraft.core.BlockPos(4, 3, 4), net.minecraft.world.level.block.Blocks.OAK_DOOR);
        check(helper, BotRules.doorway(level, door.getX(), door.getY(), door.getZ()), "in the door: a doorway");
        check(helper, !BotRules.doorway(level, door.getX() - 2, door.getY(), door.getZ()), "beside it: not");
    }

    private static void dusk(GameTestHelper helper) {
        check(helper, BotRules.duskCall(12100, 80.0), "sunset, out in the forest: home");
        check(helper, BotRules.duskCall(24000L * 3 + 12500, 80.0), "any day's dusk");
        check(helper, !BotRules.duskCall(12100, 10.0), "sunset, near home: carries on");
        check(helper, !BotRules.duskCall(6000, 200.0), "noon, far off: carries on");
        check(helper, !BotRules.duskCall(20000, 200.0), "deep in the night: too late to call (it stays as told)");
        helper.succeed();
    }

    /** A strip of grass by a water block, a grass block under stone, wheat ripe and carrots not. */
    private static void farm(GameTestHelper helper) {
        var B = new Object() {
            void set(int x, int y, int z, net.minecraft.world.level.block.state.BlockState st) {
                helper.setBlock(new net.minecraft.core.BlockPos(x, y, z), st);
            }
        };
        var grass = net.minecraft.world.level.block.Blocks.GRASS_BLOCK.defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 2; z++) {
                B.set(x, 1, z, grass);
            }
        }
        B.set(0, 1, 1, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState());
        B.set(2, 2, 2, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()); // no room on (2, 1, 2)
        var level = helper.getLevel();
        var center = helper.absolutePos(new net.minecraft.core.BlockPos(3, 1, 1));
        var wet = BotRules.tillTargets(level, center, 4, false);
        var all = BotRules.tillTargets(level, center, 4, true);
        check(helper, wet.contains(helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 1))), "four from the water: tilled " + wet.size());
        check(helper, !wet.contains(helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 1)))
                && all.contains(helper.absolutePos(new net.minecraft.core.BlockPos(6, 1, 1))), "six from the water: only when dry is asked");
        check(helper, !all.contains(helper.absolutePos(new net.minecraft.core.BlockPos(2, 1, 2))), "stone on it: left");
        check(helper, wet.get(0).distSqr(center) <= wet.get(wet.size() - 1).distSqr(center), "nearest first");
        var farmland = net.minecraft.world.level.block.Blocks.FARMLAND.defaultBlockState();
        B.set(3, 1, 4, farmland);
        B.set(4, 1, 4, farmland);
        B.set(5, 1, 4, farmland);
        var wheat = net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState();
        B.set(3, 2, 4, wheat.setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));
        B.set(4, 2, 4, net.minecraft.world.level.block.Blocks.CARROTS.defaultBlockState());
        check(helper, BotRules.ripe(helper.getBlockState(new net.minecraft.core.BlockPos(3, 2, 4))), "wheat at age 7: ripe");
        check(helper, !BotRules.ripe(helper.getBlockState(new net.minecraft.core.BlockPos(4, 2, 4))), "carrots just set: not");
        var ripe = BotRules.ripeCrops(level, helper.absolutePos(new net.minecraft.core.BlockPos(4, 2, 4)), 3);
        check(helper, ripe.size() == 1 && ripe.get(0).equals(helper.absolutePos(new net.minecraft.core.BlockPos(3, 2, 4))), "the one ripe crop: " + ripe);
        var bare = BotRules.plantTargets(level, helper.absolutePos(new net.minecraft.core.BlockPos(4, 1, 4)), 2);
        check(helper, bare.size() == 1 && bare.get(0).equals(helper.absolutePos(new net.minecraft.core.BlockPos(5, 1, 4))), "the bare farmland only: " + bare);
        helper.succeed();
    }

    /** A pool in a stone floor: a place to fish from on its bank; the explorer's spiral and its landmarks. */
    private static void fishingAndExplore(GameTestHelper helper) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                helper.setBlock(new net.minecraft.core.BlockPos(x, 1, z), x <= 2 && z <= 2
                        ? net.minecraft.world.level.block.Blocks.WATER : net.minecraft.world.level.block.Blocks.STONE);
            }
        }
        var level = helper.getLevel();
        var spot = BotRules.fishingSpot(level, helper.absolutePos(new net.minecraft.core.BlockPos(6, 2, 6)), 8);
        check(helper, spot != null, "a place to fish from");
        var stand = helper.relativePos(spot.stand());
        var water = helper.relativePos(spot.water());
        double across = Math.sqrt(Math.pow(stand.getX() - water.getX(), 2) + Math.pow(stand.getZ() - water.getZ(), 2));
        check(helper, stand.getY() == 2 && (stand.getX() > 2 || stand.getZ() > 2) && across >= 2.0 && across <= 5.0,
                "on the bank, two to five from open water: " + stand.toShortString() + " to " + water.toShortString());
        check(helper, BotRules.spiralCorner(1, 10)[0] == 10 && BotRules.spiralCorner(2, 10)[1] == 10
                && BotRules.spiralCorner(3, 10)[0] == -10 && BotRules.spiralCorner(4, 10)[1] == -10
                && BotRules.spiralCorner(5, 10)[0] == 20, "the spiral widens: east, south, west two, north two, east three");
        check(helper, "bell".equals(BotRules.landmark(net.minecraft.world.level.block.Blocks.BELL.defaultBlockState()))
                && "workstation".equals(BotRules.landmark(net.minecraft.world.level.block.Blocks.COMPOSTER.defaultBlockState()))
                && "lava".equals(BotRules.landmark(net.minecraft.world.level.block.Blocks.LAVA.defaultBlockState()))
                && BotRules.landmark(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()) == null, "landmarks");
        helper.succeed();
    }

    /** The dirt under a plank floor is left; under grass, a tree, a plant or the open sky it is not. */
    private static void underBuild(GameTestHelper helper) {
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> natural = st -> st.is(net.minecraft.world.level.block.Blocks.DIRT)
                || st.is(net.minecraft.world.level.block.Blocks.STONE) || st.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        var dirt = net.minecraft.world.level.block.Blocks.DIRT;
        for (int x = 0; x <= 5; x++) {
            helper.setBlock(new net.minecraft.core.BlockPos(x, 1, 1), dirt);
        }
        helper.setBlock(new net.minecraft.core.BlockPos(0, 2, 1), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        helper.setBlock(new net.minecraft.core.BlockPos(1, 2, 1), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
        helper.setBlock(new net.minecraft.core.BlockPos(2, 2, 1), net.minecraft.world.level.block.Blocks.OAK_LOG);
        helper.setBlock(new net.minecraft.core.BlockPos(3, 2, 1), net.minecraft.world.level.block.Blocks.SHORT_GRASS);
        helper.setBlock(new net.minecraft.core.BlockPos(4, 2, 1), net.minecraft.world.level.block.Blocks.STONE);
        helper.setBlock(new net.minecraft.core.BlockPos(4, 3, 1), net.minecraft.world.level.block.Blocks.BRICKS);
        var level = helper.getLevel();
        java.util.function.IntPredicate under = x -> BotRules.underBuild(level, helper.absolutePos(new net.minecraft.core.BlockPos(x, 1, 1)), natural);
        check(helper, under.test(0), "under a plank floor: left");
        check(helper, !under.test(1) && !under.test(2) && !under.test(3) && !under.test(5), "under grass, a log, a plant, the sky: dug");
        check(helper, under.test(4), "a brick wall two up on a stone footing: left");
        helper.succeed();
    }

    private static void chores(GameTestHelper helper) {
        var none = java.util.Set.<BotRules.Chore>of();
        check(helper, BotRules.chore(1, true, false, false, false, 5, false, none) == BotRules.Chore.DEPOSIT, "a full bag first");
        check(helper, BotRules.chore(1, false, false, true, true, 20, true, none) == BotRules.Chore.PICKAXE, "no store: tools");
        check(helper, BotRules.chore(10, true, true, false, false, 20, true, none) == BotRules.Chore.SWORD, "a sword before an axe");
        check(helper, BotRules.chore(10, true, true, true, true, 10, false, none) == BotRules.Chore.FOOD, "hungry, no food");
        check(helper, BotRules.chore(10, true, true, true, true, 10, true, none) == BotRules.Chore.NONE, "hungry with food: it eats on its own");
        check(helper, BotRules.chore(10, true, false, true, true, 20, true, java.util.Set.of(BotRules.Chore.PICKAXE)) == BotRules.Chore.NONE,
                "a chore that just failed: left alone");
        check(helper, "iron".equals(BotRules.toolTier(3, 9, 9, 3)) && "stone".equals(BotRules.toolTier(2, 3, 9, 3))
                && "wooden".equals(BotRules.toolTier(0, 0, 4, 3)) && BotRules.toolTier(0, 0, 3, 3) == null, "tool tiers");
        helper.succeed();
    }

    private static void columnNear(GameTestHelper helper) {
        Goal g = Goal.columnNear(-22, 252, 4.0);
        check(helper, g.reached(-22, 64, 252) && g.reached(-22, 12, 252) && g.reached(-19, 70, 254), "any height within 4");
        check(helper, !g.reached(-22, 64, 257), "not 5 off");
        helper.succeed();
    }

    // ---- the home, nights in it, the larder (the day alone, 2026-10-22)

    /** The hut of base1.txt (7x7, walls three high, a flat roof): the room, and what counts as a block to set back. */
    private static void homePlan(GameTestHelper helper) {
        var blocks = new java.util.HashMap<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var planks = net.minecraft.world.level.block.Blocks.OAK_PLANKS.defaultBlockState();
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                for (int y = 0; y <= 4; y++) {
                    boolean wall = x == 0 || x == 6 || z == 0 || z == 6;
                    if ((y == 0 || y == 4 || wall) && !(x == 0 && z == 3 && (y == 1 || y == 2))) {
                        blocks.put(new net.minecraft.core.BlockPos(-24 + x, 63 + y, 229 + z), planks);
                    }
                }
            }
        }
        var plan = BotRules.Plan.of(blocks);
        check(helper, plan.inside(new net.minecraft.core.BlockPos(-21, 64, 232)) && plan.inside(new net.minecraft.core.BlockPos(-23, 66, 230)),
                "the hut's floor and up to its ceiling: in");
        check(helper, !plan.inside(new net.minecraft.core.BlockPos(-24, 64, 232)), "the doorway: not in");
        check(helper, !plan.inside(new net.minecraft.core.BlockPos(-25, 64, 232)) && !plan.inside(new net.minecraft.core.BlockPos(-21, 68, 232))
                && !plan.inside(new net.minecraft.core.BlockPos(-21, 63, 232)), "outside the door, on the roof, in the floor: not");
        var way = BotRules.doorway(plan);
        check(helper, way != null && way.pos().equals(new net.minecraft.core.BlockPos(-24, 64, 232)) && way.out() == net.minecraft.core.Direction.WEST,
                "the doorway left in the west wall, for a door: " + way);
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var door = net.minecraft.world.level.block.Blocks.OAK_DOOR.defaultBlockState();
        var upper = door.setValue(net.minecraft.world.level.block.DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER);
        check(helper, BotRules.needsRepair(planks, air), "a hole a creeper blew: set back");
        check(helper, BotRules.needsRepair(planks, net.minecraft.world.level.block.Blocks.SHORT_GRASS.defaultBlockState())
                && BotRules.needsRepair(planks, net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()), "grass or water in it: set back");
        check(helper, !BotRules.needsRepair(planks, planks) && !BotRules.needsRepair(planks, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState()),
                "there, or someone's chest in its place: left");
        check(helper, BotRules.needsRepair(door, air) && !BotRules.needsRepair(upper, air), "a door from its lower half");
        check(helper, !BotRules.needsRepair(air, planks), "air in the plan: nothing to do");
        check(helper, BotRules.mendable(26, 166) && !BotRules.mendable(166, 166) && !BotRules.mendable(100, 166),
                "a blown wall mended; a house that is mostly not there (not loaded, not built) left");
        // a bed in the room's free floor, the door on the west at (-24, 64, 232)
        var free = new java.util.HashSet<net.minecraft.core.BlockPos>();
        for (int x = -23; x <= -19; x++) {
            for (int z = 230; z <= 234; z++) {
                free.add(new net.minecraft.core.BlockPos(x, 64, z));
            }
        }
        var doors = java.util.Set.of(new net.minecraft.core.BlockPos(-24, 64, 232));
        var bed = BotRules.bedSpot(free, doors);
        check(helper, bed != null, "room for a bed");
        var head = bed.foot().relative(bed.facing());
        check(helper, free.contains(head) && bed.foot().distManhattan(doors.iterator().next()) > 1 && head.distManhattan(doors.iterator().next()) > 1,
                "the bed's two cells free, not by the door: " + bed);
        check(helper, !free.contains(head.relative(bed.facing())), "its head against a wall: " + bed);
        check(helper, BotRules.bedSpot(java.util.Set.of(new net.minecraft.core.BlockPos(0, 64, 0)), java.util.Set.of()) == null, "one cell: no room");
        helper.succeed();
    }

    /** The yard lit to block light 8 everywhere, the darkest first; a lit yard needs none. */
    private static void torches(GameTestHelper helper) {
        var dark = new java.util.HashMap<net.minecraft.core.BlockPos, Integer>();
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                if (x * x + z * z <= 100) {
                    dark.put(new net.minecraft.core.BlockPos(x, 64, z), 0);
                }
            }
        }
        var spots = BotRules.torchSpots(dark, BotRules.YARD_LIGHT, 64);
        for (var c : dark.keySet()) {
            int lit = 0;
            for (var t : spots) {
                lit = Math.max(lit, BotRules.TORCH_LIGHT - c.distManhattan(t));
            }
            if (lit < BotRules.YARD_LIGHT) {
                check(helper, false, "left dark: " + c.toShortString());
            }
        }
        check(helper, spots.size() >= 4 && spots.size() <= 14, "a torch every few blocks: " + spots.size());
        check(helper, BotRules.torchSpots(dark, BotRules.YARD_LIGHT, 3).size() == 3, "no more than it has");
        var lit = new java.util.HashMap<net.minecraft.core.BlockPos, Integer>();
        dark.keySet().forEach(c -> lit.put(c, 9));
        check(helper, BotRules.torchSpots(lit, BotRules.YARD_LIGHT, 64).isEmpty(), "lit already: none");
        lit.put(new net.minecraft.core.BlockPos(7, 64, 7), 2);
        var one = BotRules.torchSpots(lit, BotRules.YARD_LIGHT, 64);
        check(helper, one.size() == 1 && one.get(0).equals(new net.minecraft.core.BlockPos(7, 64, 7)), "one dark corner: one torch there " + one);
        helper.succeed();
    }

    /** Nights: in, jobs wait, fights from inside; the companion told when left out. */
    private static void nights(GameTestHelper helper) {
        check(helper, BotRules.keepIn(18000, true, false) && BotRules.keepIn(24000L * 2 + 13000, true, false), "night with a home: stays in");
        check(helper, !BotRules.keepIn(6000, true, false) && !BotRules.keepIn(18000, false, false) && !BotRules.keepIn(18000, true, true),
                "by day, with no home, or told it may go out: not");
        check(helper, BotRules.strikeFromInside(true, 4.0, 3.0), "a zombie in the room: struck");
        check(helper, BotRules.strikeFromInside(false, 2.5, 3.0), "one at the window, within reach: struck from inside");
        check(helper, !BotRules.strikeFromInside(false, 6.0, 3.0), "one out in the yard: left (going after it was going out)");
        int away = BotRules.AWAY;
        check(helper, BotRules.warnCompanion(18000, true, away, false, 99999) == BotRules.Warn.OUT_AT_NIGHT, "left out at night: told");
        check(helper, BotRules.warnCompanion(18000, true, away, false, 100) == BotRules.Warn.NONE, "told a moment ago: not again yet");
        check(helper, BotRules.warnCompanion(18000, true, away, true, BotRules.WARN_EVERY / 3) == BotRules.Warn.MOB_CLOSE, "a mob by them: told sooner");
        check(helper, BotRules.warnCompanion(18000, true, 200, true, 99999) == BotRules.Warn.NONE, "moving about, playing: not");
        check(helper, BotRules.warnCompanion(18000, false, away, true, 99999) == BotRules.Warn.NONE
                && BotRules.warnCompanion(6000, true, away, true, 99999) == BotRules.Warn.NONE, "indoors, or by day: not");
        var none = java.util.Set.<BotRules.Chore>of();
        check(helper, BotRules.chore(10, true, true, true, true, 8, false, 20, true, true, true, true, none) == BotRules.Chore.GUARD,
                "the house before the field and the larder");
        check(helper, BotRules.chore(10, true, true, true, true, 8, false, 20, true, false, true, true, none) == BotRules.Chore.FARM
                && BotRules.chore(10, true, true, true, true, 8, false, 20, true, false, false, true, none) == BotRules.Chore.STOCK, "then the field, the larder");
        check(helper, BotRules.chore(1, true, true, true, true, 8, false, 20, true, true, true, true, none) == BotRules.Chore.DEPOSIT, "a full bag still first");
        helper.succeed();
    }

    private static BotRules.Larder larder(int cooked, int raw, int wheat, boolean penSet, boolean built, int adults, int babies, boolean food,
            boolean near, boolean known, boolean longDay, boolean water, java.util.Set<BotRules.FoodStep> tried) {
        return new BotRules.Larder(cooked, 6, raw, wheat, true, penSet, built, true, adults, babies, food, false, food, near, known, longDay, water, tried);
    }

    /** Food kept in stock: nearest at hand first, the pen bred and kept at two, a long walk only in the morning. */
    private static void larderSteps(GameTestHelper helper) {
        var none = java.util.Set.<BotRules.FoodStep>of();
        check(helper, BotRules.foodStep(larder(6, 3, 9, true, true, 4, 0, true, true, true, true, true, none)) == BotRules.FoodStep.NONE, "stocked: nothing");
        check(helper, BotRules.foodStep(larder(1, 3, 9, true, true, 4, 0, true, true, true, true, true, none)) == BotRules.FoodStep.COOK, "raw meat: cooked first");
        check(helper, BotRules.foodStep(larder(1, 0, 9, true, true, 4, 0, true, true, true, true, true, none)) == BotRules.FoodStep.BREAD, "wheat: bread");
        check(helper, BotRules.foodStep(larder(1, 0, 0, true, true, 4, 0, true, true, true, true, true, none)) == BotRules.FoodStep.CULL, "four in the pen: one eaten");
        check(helper, BotRules.foodStep(larder(1, 0, 0, true, true, 2, 0, true, true, true, true, true, none)) == BotRules.FoodStep.BREED, "two: bred");
        check(helper, BotRules.foodStep(larder(1, 0, 0, true, false, 0, 0, true, false, true, true, true, none)) == BotRules.FoodStep.BUILD_PEN, "no pen yet: built");
        check(helper, BotRules.foodStep(larder(1, 0, 0, true, true, 0, 0, true, false, true, true, true, none)) == BotRules.FoodStep.FETCH,
                "an empty pen, animals seen: two fetched");
        check(helper, BotRules.foodStep(larder(1, 0, 0, true, true, 0, 0, true, false, true, false, true, none)) == BotRules.FoodStep.FISH,
                "seen far off, the afternoon: no long walk - fish");
        check(helper, BotRules.foodStep(larder(1, 0, 0, false, false, 0, 0, false, true, false, false, false, none)) == BotRules.FoodStep.HUNT,
                "animals about, no pen: hunted");
        check(helper, BotRules.foodStep(larder(1, 0, 0, false, false, 0, 0, false, false, false, true, false, none)) == BotRules.FoodStep.NONE,
                "nothing at all: nothing");
        check(helper, BotRules.foodStep(larder(1, 3, 0, false, false, 0, 0, false, true, false, true, true, java.util.Set.of(BotRules.FoodStep.COOK)))
                == BotRules.FoodStep.HUNT, "cooking just failed: the next thing");
        check(helper, !BotRules.penBreeds(2, 6, true, false) && !BotRules.penBreeds(2, 0, true, true) && !BotRules.penBreeds(1, 0, true, false)
                && BotRules.penBreeds(3, 2, true, false), "breeding: a pair, room, not lately");
        check(helper, BotRules.longDay(1000) && BotRules.longDay(6000) && !BotRules.longDay(23800) && !BotRules.longDay(9000),
                "a long day ahead: the morning, the sun well up");
        check(helper, BotRules.farTrip(3000, 20.0F) && !BotRules.farTrip(3000, 0.5F), "badly hurt: no long walk (an arrow on the way killed it)");
        // the pen west of the hut, its gate toward it
        var pen = BotRules.pen(new net.minecraft.core.BlockPos(-34, 64, 232), new net.minecraft.core.BlockPos(-21, 64, 232));
        check(helper, pen.gate().equals(new net.minecraft.core.BlockPos(-32, 64, 232)) && pen.gateSide() == net.minecraft.core.Direction.EAST,
                "the gate on the home side: " + pen.gate().toShortString());
        check(helper, pen.fence().size() == 15 && !pen.fence().contains(pen.gate()), "a 5x5 ring of fence less the gate");
        check(helper, pen.inside(new net.minecraft.core.BlockPos(-33, 64, 233)) && !pen.inside(new net.minecraft.core.BlockPos(-32, 64, 232)),
                "the yard in, the fence not");
        check(helper, pen.ring().size() == 20 && pen.ring().stream().noneMatch(p -> pen.inside(p) || pen.fence().contains(p)),
                "places to stand outside the fence");
        var notes = java.util.List.of("tick 1  at -134, 75, 349  4 sheep at -152 74 374",
                "tick 2  at -58, 74, 307  1 chicken at -71 77 302; 2 chicken at -65 73 309",
                "tick 3  at -210, 76, 435  3 cow at -196 73 432; 4 sheep at -224 79 448; 1 horse at -228 80 410");
        var home = new net.minecraft.core.BlockPos(-21, 64, 232);
        var chickens = BotRules.animalPlaces(notes, "chicken", home);
        check(helper, chickens.size() == 2 && chickens.get(0).equals(new net.minecraft.core.BlockPos(-65, 73, 309)), "two chickens before one: " + chickens);
        var sheep = BotRules.animalPlaces(notes, "sheep", home);
        check(helper, sheep.size() == 2 && sheep.get(0).equals(new net.minecraft.core.BlockPos(-152, 74, 374)), "the nearer sheep first: " + sheep);
        check(helper, BotRules.animalPlaces(notes, "pig", home).isEmpty(), "no pigs seen");
        helper.succeed();
    }
}
