package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.dev.bot.path.Abilities;
import dev.nookworks.dev.bot.path.Goal;
import dev.nookworks.dev.bot.path.Move;
import dev.nookworks.dev.bot.path.PathWorld;
import dev.nookworks.dev.bot.path.Pathfinder;
import dev.nookworks.dev.bot.path.Step;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The agent walker's planner (dev tooling: the agent client's player that walks, climbs, swims, bridges and digs its
 * own way about), on small courses built in the arena: each way of getting about found where it is needed, and the
 * walker kept from what it must not do (a long fall, lava, a build it may not break, a door it cannot open).
 */
final class BotPathTests {
    private BotPathTests() {}

    static void register() {
        test("bot_path_walks_round_a_wall", BotPathTests::aroundWall);
        test("bot_path_climbs_a_ladder", BotPathTests::ladder);
        test("bot_path_swims_across_deep_water", BotPathTests::swim);
        test("bot_path_jumps_up_block_steps", BotPathTests::steps);
        test("bot_path_walks_slabs_without_jumping", BotPathTests::slabs);
        test("bot_path_never_falls_too_far", BotPathTests::longFall);
        test("bot_path_falls_into_water_from_any_height", BotPathTests::fallIntoWater);
        test("bot_path_pillars_up_with_blocks_only", BotPathTests::pillar);
        test("bot_path_bridges_a_gap", BotPathTests::bridge);
        test("bot_path_jumps_a_gap", BotPathTests::parkour);
        test("bot_path_tunnels_natural_stone_not_builds", BotPathTests::tunnel);
        test("bot_path_keeps_out_of_lava", BotPathTests::lava);
        test("bot_path_goes_through_doors_a_hand_opens", BotPathTests::doors);
        test("bot_path_digs_down_safely", BotPathTests::digDown);
        test("bot_path_any_height_means_the_surface", BotPathTests::surface);
        test("bot_path_stopped_search_gives_a_leg", BotPathTests::stoppedLeg);
        test("bot_path_no_long_swim_without_air", BotPathTests::floodedTunnel);
    }

    // ---- courses

    /** {@code goto X Z}: a hill with a cave under it, the column over both - the hill's top, not the cave's floor. */
    private static void surface(GameTestHelper helper) {
        fill(helper, 2, 1, 3, 6, 3, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 4, 1, 3, 4, 2, 5, Blocks.AIR.defaultBlockState()); // the cave, its mouth facing the start
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.STONE);
        fill(helper, 2, 1, 2, 2, 2, 2, Blocks.STONE.defaultBlockState()); // steps up the hill's side
        var level = helper.getLevel();
        BlockPos column = helper.absolutePos(new BlockPos(4, 0, 5));
        Goal.Cells open = (x, y, z) -> dev.nookworks.dev.bot.BotRules.onSurface(level, x, y, z,
                level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z), st -> st.is(Blocks.STONE));
        Abilities ab = Abilities.walkOnly();
        ab.bounds = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 9, 6)));
        BlockPos from = helper.absolutePos(new BlockPos(2, 1, 0));
        Pathfinder anyCell = new Pathfinder(new PathWorld(level, ab), from, Goal.columnNear(column.getX(), column.getZ(), 0.0));
        check(helper, anyCell.runToEnd() == Pathfinder.Status.FOUND && last(helper, anyCell).getY() == 1,
                "the plain column goal ends in the cave (the course is as meant): " + show(helper, anyCell.path()));
        Pathfinder onTop = new Pathfinder(new PathWorld(level, ab), from, Goal.columnNear(column.getX(), column.getZ(), 0.0, open));
        check(helper, onTop.runToEnd() == Pathfinder.Status.FOUND && last(helper, onTop).getY() == 4,
                "on the hill's top: " + show(helper, onTop.path()));
        helper.succeed();
    }

    /** A tunnel round two sides of the arena, flooded: longer than a breath, it is not swum; dry, it is walked. */
    private static void floodedTunnel(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 3, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 0, 1, 0, 6, 2, 0, Blocks.WATER.defaultBlockState());
        fill(helper, 6, 1, 0, 6, 2, 6, Blocks.WATER.defaultBlockState());
        fill(helper, 0, 1, 0, 0, 2, 0, Blocks.AIR.defaultBlockState());
        fill(helper, 6, 1, 6, 6, 2, 6, Blocks.AIR.defaultBlockState());
        List<Step> wet = expect(helper, Abilities.walkOnly(), new BlockPos(0, 1, 0), new BlockPos(6, 1, 6), false);
        fill(helper, 1, 1, 0, 6, 2, 0, Blocks.AIR.defaultBlockState());
        fill(helper, 6, 1, 1, 6, 2, 5, Blocks.AIR.defaultBlockState());
        expect(helper, Abilities.walkOnly(), new BlockPos(0, 1, 0), new BlockPos(6, 1, 6), true);
        helper.succeed();
    }

    /** A search out of time (a long trip) ends with the best leg so far, not nothing. */
    private static void stoppedLeg(GameTestHelper helper) {
        fill(helper, 0, 1, 3, 5, 2, 3, Blocks.STONE.defaultBlockState());
        Abilities ab = Abilities.walkOnly();
        ab.bounds = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 9, 6)));
        BlockPos from = helper.absolutePos(new BlockPos(1, 1, 1));
        // a goal far beyond the arena: no search finds it, the nearest cell is the arena's far side
        Pathfinder search = new Pathfinder(new PathWorld(helper.getLevel(), ab), from, Goal.at(helper.absolutePos(new BlockPos(3, 1, 60))));
        search.run(1L);
        Pathfinder.Status status = search.stop();
        check(helper, status == Pathfinder.Status.PARTIAL && !search.path().isEmpty() && last(helper, search).getZ() > 3,
                "a leg past the wall toward the goal: " + status + " " + show(helper, search.path()));
        check(helper, search.stop() == status, "stopping again changes nothing");
        helper.succeed();
    }

    private static BlockPos last(GameTestHelper helper, Pathfinder search) {
        List<Step> path = search.path();
        return path.isEmpty() ? new BlockPos(-1, -1, -1) : rel(helper, path.get(path.size() - 1).to());
    }

    private static void aroundWall(GameTestHelper helper) {
        fill(helper, 0, 1, 3, 5, 3, 3, Blocks.STONE.defaultBlockState());
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(1, 1, 1), new BlockPos(1, 1, 5), true);
        check(helper, path.stream().allMatch(s -> s.breaks().isEmpty() && !s.placesBlock()), "walked round, nothing broken or set down");
        check(helper, path.stream().anyMatch(s -> rel(helper, s.to()).getX() == 6), "through the gap at the wall's end");
        helper.succeed();
    }

    private static void ladder(GameTestHelper helper) {
        fill(helper, 0, 1, 4, 6, 4, 6, Blocks.STONE.defaultBlockState());
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, helper.getTestRotation().rotate(Direction.NORTH)));
        }
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(3, 1, 1), new BlockPos(3, 5, 5), true);
        check(helper, path.stream().filter(s -> s.move() == Move.CLIMB).count() >= 2, "up the ladder: " + show(helper, path));
        helper.succeed();
    }

    private static void swim(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 2, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 0, 1, 3, 6, 2, 3, Blocks.WATER.defaultBlockState());
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(3, 3, 1), new BlockPos(3, 3, 5), true);
        check(helper, path.stream().anyMatch(s -> rel(helper, s.to()).getZ() == 3
                && helper.getBlockState(rel(helper, s.to())).is(Blocks.WATER)), "through the water: " + path);
        helper.succeed();
    }

    private static void steps(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        fill(helper, 3, 1, 3, 3, 2, 3, Blocks.STONE.defaultBlockState());
        fill(helper, 3, 1, 4, 3, 3, 4, Blocks.STONE.defaultBlockState());
        fill(helper, 0, 1, 5, 6, 3, 6, Blocks.STONE.defaultBlockState());
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(3, 1, 1), new BlockPos(3, 4, 5), true);
        check(helper, path.stream().filter(s -> s.move() == Move.ASCEND).count() == 3, "three jumps up: " + path);
        helper.succeed();
    }

    private static void slabs(GameTestHelper helper) {
        BlockState slab = Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        fill(helper, 0, 1, 0, 6, 9, 0, Blocks.GLASS.defaultBlockState()); // nothing round the side
        helper.setBlock(new BlockPos(3, 1, 2), slab);
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 4), slab);
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);
        fill(helper, 0, 1, 5, 6, 2, 6, Blocks.STONE.defaultBlockState());
        Abilities ab = Abilities.walkOnly();
        List<Step> path = expect(helper, ab, new BlockPos(3, 1, 1), new BlockPos(3, 3, 5), true);
        check(helper, path.stream().noneMatch(s -> s.move() == Move.ASCEND), "half a block at a time, no jump: " + path);
        helper.succeed();
    }

    private static void longFall(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 4, 2, Blocks.STONE.defaultBlockState());
        // four blocks down off the platform, and no other way
        Pathfinder search = search(helper, Abilities.walkOnly(), new BlockPos(1, 5, 1), new BlockPos(1, 1, 5));
        check(helper, search.runToEnd() != Pathfinder.Status.FOUND, "no four-block fall: " + search.path());
        helper.succeed();
    }

    private static void fallIntoWater(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 6, 2, Blocks.STONE.defaultBlockState());
        helper.setBlock(new BlockPos(1, 1, 3), Blocks.WATER);
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(1, 7, 1), new BlockPos(1, 1, 5), true);
        check(helper, path.stream().anyMatch(s -> s.move() == Move.DESCEND && rel(helper, s.to()).equals(new BlockPos(1, 1, 3))),
                "down into the pool: " + path);
        helper.succeed();
    }

    private static void pillar(GameTestHelper helper) {
        fill(helper, 0, 1, 4, 6, 3, 6, Blocks.STONE.defaultBlockState());
        Pathfinder without = search(helper, Abilities.walkOnly(), new BlockPos(3, 1, 1), new BlockPos(3, 4, 5));
        check(helper, without.runToEnd() != Pathfinder.Status.FOUND, "no way up with nothing to build with");
        Abilities ab = Abilities.walkOnly();
        ab.placeBlocks = 8;
        List<Step> path = expect(helper, ab, new BlockPos(3, 1, 1), new BlockPos(3, 4, 5), true);
        check(helper, path.stream().filter(s -> s.move() == Move.PILLAR).count() >= 2, "pillared up: " + show(helper, path));
        helper.succeed();
    }

    private static void bridge(GameTestHelper helper) {
        // too deep a gap to drop into and climb out of
        fill(helper, 0, 1, 0, 6, 5, 1, Blocks.STONE.defaultBlockState());
        fill(helper, 0, 1, 5, 6, 5, 6, Blocks.STONE.defaultBlockState());
        Abilities ab = Abilities.walkOnly();
        ab.placeBlocks = 8;
        List<Step> path = expect(helper, ab, new BlockPos(3, 6, 1), new BlockPos(3, 6, 5), true);
        check(helper, path.stream().filter(s -> s.placesBlock() && s.move() == Move.WALK).count() == 3, "bridged: " + show(helper, path));
        check(helper, path.stream().allMatch(s -> rel(helper, s.to()).getY() == 6), "never down in the gap: " + show(helper, path));
        helper.succeed();
    }

    private static void parkour(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 3, 2, Blocks.STONE.defaultBlockState());
        fill(helper, 0, 1, 4, 6, 3, 6, Blocks.STONE.defaultBlockState());
        Abilities ab = new Abilities(); // nothing to build with, nothing to break: jump
        List<Step> path = expect(helper, ab, new BlockPos(3, 4, 1), new BlockPos(3, 4, 5), true);
        check(helper, path.stream().anyMatch(s -> s.move() == Move.PARKOUR), "jumped the gap: " + path);
        helper.succeed();
    }

    private static void tunnel(GameTestHelper helper) {
        fill(helper, 0, 1, 3, 6, 9, 3, Blocks.STONE.defaultBlockState());
        Abilities ab = naturalDigger();
        List<Step> path = expect(helper, ab, new BlockPos(3, 1, 1), new BlockPos(3, 1, 5), true);
        check(helper, path.stream().anyMatch(s -> !s.breaks().isEmpty()), "dug through the stone: " + path);
        fill(helper, 0, 1, 3, 6, 9, 3, Blocks.OAK_PLANKS.defaultBlockState());
        Pathfinder build = search(helper, naturalDigger(), new BlockPos(3, 1, 1), new BlockPos(3, 1, 5));
        check(helper, build.runToEnd() != Pathfinder.Status.FOUND, "a wall of planks is somebody's: left whole");
        helper.succeed();
    }

    private static void lava(GameTestHelper helper) {
        fill(helper, 0, 0, 3, 5, 0, 3, Blocks.LAVA.defaultBlockState());
        List<Step> path = expect(helper, new Abilities(), new BlockPos(1, 1, 1), new BlockPos(1, 1, 5), true);
        check(helper, path.stream().allMatch(s -> rel(helper, s.to()).getZ() != 3 || rel(helper, s.to()).getX() == 6),
                "round the lava, not over it: " + path);
        helper.succeed();
    }

    private static void doors(GameTestHelper helper) {
        fill(helper, 0, 1, 3, 6, 9, 3, Blocks.STONE.defaultBlockState());
        door(helper, Blocks.OAK_DOOR.defaultBlockState());
        List<Step> path = expect(helper, Abilities.walkOnly(), new BlockPos(3, 1, 1), new BlockPos(3, 1, 5), true);
        check(helper, path.stream().anyMatch(s -> rel(helper, s.to()).equals(new BlockPos(3, 1, 3))), "through the door: " + path);
        door(helper, Blocks.IRON_DOOR.defaultBlockState());
        Pathfinder iron = search(helper, Abilities.walkOnly(), new BlockPos(3, 1, 1), new BlockPos(3, 1, 5));
        check(helper, iron.runToEnd() != Pathfinder.Status.FOUND, "an iron door wants redstone, not a hand");
        helper.succeed();
    }

    private static void digDown(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 3, 6, Blocks.STONE.defaultBlockState());
        Abilities ab = naturalDigger();
        List<Step> path = expect(helper, ab, new BlockPos(3, 4, 3), new BlockPos(3, 1, 3), true);
        check(helper, path.stream().filter(s -> s.move() == Move.DESCEND && !s.breaks().isEmpty()).count() == 3,
                "three blocks dug straight down: " + path);
        // never down into lava
        helper.setBlock(new BlockPos(3, 0, 3), Blocks.LAVA);
        Pathfinder over = search(helper, naturalDigger(), new BlockPos(3, 4, 3), new BlockPos(3, 1, 3));
        check(helper, over.runToEnd() != Pathfinder.Status.FOUND, "no digging down onto lava: " + over.path());
        helper.succeed();
    }

    // ---- helpers

    private static Abilities naturalDigger() {
        Abilities ab = Abilities.walkOnly();
        ab.canBreak = true;
        ab.mayBreak = (state, pos) -> state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT);
        ab.breakTicks = (state, pos) -> 30.0;
        return ab;
    }

    private static void door(GameTestHelper helper, BlockState door) {
        Direction facing = helper.getTestRotation().rotate(Direction.SOUTH);
        helper.setBlock(new BlockPos(3, 1, 3), door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        helper.setBlock(new BlockPos(3, 2, 3), door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    helper.setBlock(new BlockPos(x, y, z), state);
                }
            }
        }
    }

    private static BlockPos rel(GameTestHelper helper, BlockPos absolute) {
        return helper.relativePos(absolute);
    }

    /** A path in the arena's own coordinates, for the failure messages. */
    private static String show(GameTestHelper helper, List<Step> path) {
        StringBuilder out = new StringBuilder();
        for (Step s : path) {
            out.append(s.move()).append(' ').append(rel(helper, s.to()).toShortString());
            if (!s.breaks().isEmpty()) {
                out.append(" break");
            }
            if (s.placesBlock()) {
                out.append(" place");
            }
            out.append("; ");
        }
        return out.toString();
    }

    private static Pathfinder search(GameTestHelper helper, Abilities ab, BlockPos from, BlockPos to) {
        ab.bounds = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(6, 9, 6)));
        return new Pathfinder(new PathWorld(helper.getLevel(), ab), helper.absolutePos(from), Goal.at(helper.absolutePos(to)));
    }

    private static List<Step> expect(GameTestHelper helper, Abilities ab, BlockPos from, BlockPos to, boolean found) {
        Pathfinder search = search(helper, ab, from, to);
        Pathfinder.Status status = search.runToEnd();
        check(helper, (status == Pathfinder.Status.FOUND) == found, "status " + status + " after " + search.expanded()
                + " nodes, " + from.toShortString() + " to " + to.toShortString() + ": " + show(helper, search.path()));
        return search.path();
    }
}
