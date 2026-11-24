package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.nookworks.NookworksConfig.Activation;
import dev.nookworks.dev.bot.Hall;
import dev.nookworks.dev.bot.Stairs;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The agent bot's big dig (dev tooling, dev.nookworks.dev.bot.Hall): a hall dug out of a box from the top down, lane by
 * lane, layer by layer, for 「AIと一緒にサバイバル #2」 (2026-11-21: 41 wide, 41 high, at about y=0). Its plan is walked
 * through cell by cell for boxes of many shapes and every way round (each blow's 3x3 inside the box, struck from where
 * it can stand and see the face, the whole box open at the end, torches lighting every floor); a small hall is dug with a
 * real stone hammer, by a player stood and aimed as the bot stands and aims; and what it fills round an opening is
 * checked.
 */
final class BotHallTests {
    private BotHallTests() {}

    static void register() {
        test("bot_hall_plan_digs_the_box_and_nothing_else", BotHallTests::plan);
        test("bot_hall_dug_with_a_stone_hammer", BotHallTests::hammer);
        test("bot_hall_fills_water_lava_and_holes", BotHallTests::seal);
        test("bot_hall_starts_from_the_near_end", BotHallTests::ends);
    }

    private static Hall.Box box(Direction forward, int across, int high, int along) {
        boolean alongX = forward.getAxis() == Direction.Axis.X;
        int sx = alongX ? along : across;
        int sz = alongX ? across : along;
        return Hall.Box.of(100, 0, 200, 100 + sx - 1, high - 1, 200 + sz - 1);
    }

    /** The plan, cell by cell, for halls of many shapes and every way round. */
    private static void plan(GameTestHelper helper) {
        int[][] sizes = {{3, 3, 4}, {5, 5, 5}, {7, 8, 6}, {8, 7, 9}, {4, 6, 10}, {6, 4, 7}, {9, 10, 12}};
        for (int[] sz : sizes) {
            for (Direction f : Direction.Plane.HORIZONTAL) {
                for (Direction side : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
                    walk(helper, new Hall(box(f, sz[0], sz[1], sz[2]), f, side), true);
                }
            }
        }
        // the episode's own: 41 across, 41 high (air y=0..40), 48 along
        Hall big = new Hall(Hall.Box.of(0, 0, 0, 40, 40, 47), Direction.SOUTH, Direction.EAST);
        check(helper, big.layers.size() == 14, "41 high: 13 layers of 3 and one of 2: " + big.layers.size());
        check(helper, big.layers.get(0).bottom() == 38 && big.layers.get(0).top() == 40, "the top layer y 38..40: " + big.layers.get(0));
        check(helper, big.layers.get(13).bottom() == 0 && big.layers.get(13).top() == 1, "the bottom layer y 0..1: " + big.layers.get(13));
        check(helper, big.lanes.length == 14 && big.lanes[13] == 39, "41 across: 14 lanes, the last against the far wall: "
                + java.util.Arrays.toString(big.lanes));
        check(helper, big.entry().equals(new BlockPos(1, 38, -1)), "the way in ends before the first lane, on the top floor: " + big.entry());
        walk(helper, big, false);
        Hall west = new Hall(Hall.Box.of(-60, 0, 10, -13, 40, 50), Direction.WEST, Direction.NORTH);
        walk(helper, west, false);
        // in sections: each dug from its top to its floor, the next climbed into by a stair across its first columns
        int[][] parts = {{8, 7, 20, 5}, {9, 9, 30, 8}, {7, 7, 13, 4}, {12, 10, 26, 6}};
        for (int[] sz : parts) {
            for (Direction f : Direction.Plane.HORIZONTAL) {
                for (Direction side : new Direction[] {f.getClockWise(), f.getCounterClockWise()}) {
                    Hall h = new Hall(box(f, sz[0], sz[1], sz[2]), f, side, sz[3]);
                    check(helper, h.sections.size() > 1, "sections: " + h.sections.size());
                    walk(helper, h, true);
                    walk(helper, new Hall(box(f, sz[0], sz[1], sz[2]), f, side, sz[3], true), true);
                }
            }
        }
        Hall night = Hall.ahead(new BlockPos(-30, 38, -234), Direction.EAST, Direction.SOUTH, 41, 41, 48, 8);
        check(helper, night.sections.size() == 6, "48 along in sections of 8: " + night.sections.size());
        walk(helper, night, false);
        boolean narrow = false;
        try {
            new Hall(Hall.Box.of(0, 0, 0, 5, 9, 30), Direction.SOUTH, Direction.EAST, 8);
        } catch (IllegalArgumentException e) {
            narrow = true;
        }
        check(helper, narrow, "sections in a box narrower than it is high are refused");
        helper.succeed();
    }

    /** Every move of a hall, in order, against the cells dug so far. */
    private static void walk(GameTestHelper helper, Hall h, boolean paths) {
        String name = h.box + " " + h.forward.getName() + "/" + h.side.getName();
        Set<BlockPos> dug = new HashSet<>();
        for (BlockPos p : BlockPos.betweenClosed(h.entry().offset(-2, 0, -2), h.entry().offset(2, 2, 2))) {
            if (h.doorway(p)) {
                dug.add(p.immutable());
            }
        }
        check(helper, dug.size() == 9 && dug.contains(h.entry()) && dug.contains(h.entry().above()), name + ": the doorway is 3x3 round the way in");
        BlockPos at = h.entry();
        for (Hall.Move m : h.moves) {
            String what = name + " move " + m.index() + " (" + m.part() + ")";
            Stairs.Step raw = Stairs.next(m.step().kind(), m.stand(), m.step().dir());
            for (BlockPos p : raw.open()) {
                check(helper, h.box.contains(p), what + ": its blow would take " + p.toShortString() + ", outside the box");
            }
            check(helper, !m.open().isEmpty(), what + ": opens nothing");
            if (dug.contains(m.stand().below()) && h.box.contains(m.stand().below())) {
                dug.remove(m.stand().below()); // a floor set down first (a lane over the stair up)
            }
            check(helper, dug.contains(m.stand()) && dug.contains(m.stand().above()), what + ": stood in rock at " + m.stand().toShortString());
            check(helper, !dug.contains(m.stand().below()), what + ": nothing to stand on at " + m.stand().toShortString());
            check(helper, dug.contains(m.step().aim().relative(m.step().face())), what + ": the face struck is not to be seen");
            check(helper, m.layer() == h.layerAt(m.step().to().getY()), what + ": counted in layer " + m.layer() + ", its feet at y "
                    + m.step().to().getY());
            if (paths && !m.stand().equals(at)) {
                check(helper, connected(dug, at, m.stand()), what + ": no way from " + at.toShortString() + " to " + m.stand().toShortString());
            }
            dug.addAll(m.open());
            BlockPos under = m.step().to().below();
            if (dug.contains(under) && h.box.contains(under)) {
                dug.remove(under); // a floor set down there (the stair up's first step, over the way in)
            }
            at = m.stand();
        }
        for (BlockPos p : BlockPos.betweenClosed(h.box.x1(), h.box.y1(), h.box.z1(), h.box.x2(), h.box.y2(), h.box.z2())) {
            check(helper, dug.contains(p), name + ": left undug at " + p.toShortString());
        }
        // a torch place on each layer's floor, each opened by a move of that layer; every floor cell within 12 of one
        int n = h.layers.size();
        for (int seg = 0; seg < h.sections.size() * n; seg++) {
            int layer = seg % n;
            Set<BlockPos> reached = new HashSet<>();
            for (Hall.Move m : h.moves) {
                BlockPos t = m.segment() == seg && m.layer() == layer ? h.torchAt(m) : null;
                if (t != null) {
                    reached.add(t);
                }
            }
            List<BlockPos> places = h.torchPlaces(seg);
            check(helper, reached.equals(new HashSet<>(places)), name + ": segment " + seg + "'s torch places reached " + reached.size() + " of "
                    + places.size());
            int feet = h.layers.get(layer).bottom();
            int[] sec = h.sections.get(seg / n);
            for (int i = 0; i < h.width; i++) {
                for (int j = sec[0]; j <= sec[1]; j++) {
                    BlockPos c = h.cell(i, feet, j);
                    int best = Integer.MAX_VALUE;
                    for (BlockPos t : places) {
                        best = Math.min(best, Math.abs(t.getX() - c.getX()) + Math.abs(t.getZ() - c.getZ()));
                    }
                    check(helper, best <= 12, name + ": dark floor at " + c.toShortString() + " (" + best + " from a torch)");
                }
            }
        }
    }

    /** Whether the walker could go from one cell to another through the cells dug (faces, a step up or down). */
    private static boolean connected(Set<BlockPos> dug, BlockPos from, BlockPos to) {
        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        open.add(from);
        seen.add(from);
        while (!open.isEmpty() && seen.size() < 20000) {
            BlockPos p = open.poll();
            if (p.equals(to)) {
                return true;
            }
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (dug.contains(n) && seen.add(n)) {
                    open.add(n);
                }
            }
        }
        return false;
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, state);
        }
    }

    private static ServerPlayer stand(ServerPlayer player, Stairs.Step s) {
        BlockPos feet = s.from();
        Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + player.getEyeHeight(), feet.getZ() + 0.5);
        Vec3 at = Stairs.aimPoint(s);
        double dx = at.x - eye.x;
        double dy = at.y - eye.y;
        double dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, pitch);
        player.setYHeadRot(yaw);
        player.setShiftKeyDown(Stairs.sneakForHammer(Activation.NOT_SNEAKING));
        return player;
    }

    /**
     * A hall 5 across, 5 high and 5 along dug in solid stone with a stone hammer, every move struck as the bot strikes it;
     * what a blow cannot take (a column a stair opened half of) broken a block at a time. The box ends empty, every wall,
     * the ceiling and the floor whole, but for the doorway the way in came through.
     */
    private static void hammer(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 7, 6, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        BlockPos lo = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos hi = helper.absolutePos(new BlockPos(5, 6, 5));
        Hall h = new Hall(Hall.Box.of(lo.getX(), lo.getY(), lo.getZ(), hi.getX(), hi.getY(), hi.getZ()), Direction.SOUTH, Direction.EAST);
        // the way in: helper positions turn with the test's rotation, so the doorway is found by the hall's own cells
        for (BlockPos p : BlockPos.betweenClosed(h.entry().offset(-2, -1, -2), h.entry().offset(2, 3, 2))) {
            if (h.doorway(p)) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STONE_HAMMER.get()));
        int byHand = 0;
        int blows = 0;
        for (Hall.Move m : h.moves) {
            Stairs.Step s = m.step();
            if (!Stairs.passable(level, s.aim(), level.getBlockState(s.aim()))) {
                stand(player, s).gameMode.destroyBlock(s.aim());
                blows++;
                if (m.part() != Hall.Part.BACK) {
                    check(helper, Stairs.cleared(level, s), "move " + m.index() + " (" + m.part() + "): one blow opened it all");
                }
            }
            for (BlockPos p : m.open()) {
                if (!level.getBlockState(p).isAir()) {
                    level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    byHand++;
                }
            }
        }
        for (BlockPos p : BlockPos.betweenClosed(lo, hi)) {
            check(helper, level.getBlockState(p).isAir(), "left in the hall: " + p.toShortString());
        }
        for (BlockPos p : BlockPos.betweenClosed(lo.offset(-1, -1, -1), hi.offset(1, 1, 1))) {
            if (!h.box.contains(p) && !h.doorway(p)) {
                check(helper, level.getBlockState(p).is(Blocks.STONE), "the shell broken at " + p.toShortString());
            }
        }
        check(helper, byHand <= 12, "a block at a time only where a stair opened half a column: " + byHand + " (" + blows + " blows)");
        helper.succeed();
    }

    /** Round an opened column: water and lava outside it, a source in it or beside it in the box, and holes in the walls
     *  are filled, lava first; the doorway, a source below in the box, flowing water in the box are left. */
    private static void seal(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 8, 6, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        BlockPos lo = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos hi = helper.absolutePos(new BlockPos(5, 6, 5));
        Hall h = new Hall(Hall.Box.of(lo.getX(), lo.getY(), lo.getZ(), hi.getX(), hi.getY(), hi.getZ()), Direction.SOUTH, Direction.EAST);
        // the first column opened: across 0..2 of the top layer, along 0
        List<BlockPos> opened = h.moves.get(0).open();
        for (BlockPos p : opened) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState flowing = water.setValue(LiquidBlock.LEVEL, 3);
        int top = hi.getY();
        BlockPos wall = h.cell(-1, top - 1, 0); // in the side wall
        BlockPos ceiling = h.cell(1, top + 1, 0); // over the top layer
        BlockPos wallHole = h.cell(-1, top - 2, 0);
        BlockPos doorway = h.cell(2, top - 1, -1);
        BlockPos below = h.cell(1, top - 3, 0); // in the box, under the opened cells
        BlockPos flowingIn = h.cell(3, top - 1, 0); // in the box, beside
        BlockPos sourceIn = h.cell(3, top, 0); // in the box, beside
        BlockPos inOpen = h.cell(1, top - 1, 0); // in the column itself
        level.setBlock(wall, water, 3);
        level.setBlock(ceiling, Blocks.LAVA.defaultBlockState(), 3);
        level.setBlock(wallHole, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(doorway, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(below, water, 3);
        level.setBlock(flowingIn, flowing, 3);
        level.setBlock(sourceIn, water, 3);
        level.setBlock(inOpen, water, 3);
        check(helper, h.doorway(doorway), "the doorway cell");
        List<BlockPos> seal = h.toSeal(level, opened);
        check(helper, !seal.isEmpty() && seal.get(0).equals(ceiling), "the lava over it first: " + seal);
        for (BlockPos p : List.of(wall, wallHole, sourceIn, inOpen)) {
            check(helper, seal.contains(p), "filled: " + p.toShortString() + " in " + seal);
        }
        for (BlockPos p : List.of(doorway, below, flowingIn)) {
            check(helper, !seal.contains(p), "left: " + p.toShortString() + " in " + seal);
        }
        check(helper, seal.indexOf(wallHole) > seal.indexOf(wall), "water before holes: " + seal);
        check(helper, Hall.sealKind(level, ceiling) == Hall.Seal.LAVA && Hall.sealKind(level, wall) == Hall.Seal.WATER
                && Hall.sealKind(level, wallHole) == Hall.Seal.HOLE, "what each is");
        // lava by a step: dug a block at a time
        check(helper, Hall.lavaBy(level, h.moves.get(0).step()), "lava over the first column");
        check(helper, !Hall.lavaBy(level, h.moves.get(h.moves.size() - 1).step()), "none by the last");
        helper.succeed();
    }

    /** It starts from the end and the side nearer where it stands; lanes along the longer side. */
    private static void ends(GameTestHelper helper) {
        Hall.Box box = Hall.Box.of(0, 0, 0, 40, 40, 59);
        Hall a = Hall.plan(box, new BlockPos(-5, 60, -20));
        check(helper, a.forward == Direction.SOUTH && a.side == Direction.EAST, "from the north-west: south, then east: " + a.forward + " " + a.side);
        Hall b = Hall.plan(box, new BlockPos(45, 60, 70));
        check(helper, b.forward == Direction.NORTH && b.side == Direction.WEST, "from the south-east: north, then west: " + b.forward + " " + b.side);
        check(helper, b.entry().getZ() == 60 && b.entry().getX() == 39, "the way in at the south end, east lane: " + b.entry());
        Hall c = Hall.plan(Hall.Box.of(0, 0, 0, 59, 40, 40), new BlockPos(70, 50, 2));
        check(helper, c.forward == Direction.WEST, "longer along x, from the east: west: " + c.forward);
        boolean refused = false;
        try {
            new Hall(Hall.Box.of(0, 0, 0, 1, 5, 9), Direction.SOUTH, Direction.EAST);
        } catch (IllegalArgumentException e) {
            refused = true;
        }
        check(helper, refused, "a box two across is refused");
        // ahead of where it stands: its feet the way in, the top layer's floor under them
        BlockPos feet = new BlockPos(-30, 38, -234);
        Hall d = Hall.ahead(feet, Direction.EAST, Direction.SOUTH, 41, 41, 64);
        check(helper, d.entry().equals(feet) && d.box.equals(Hall.Box.of(-29, 0, -235, 34, 40, -195)), "ahead, east, lanes to the south: " + d.box);
        Hall e = Hall.ahead(feet, Direction.EAST, Direction.NORTH, 41, 41, 64);
        check(helper, e.entry().equals(feet) && e.box.equals(Hall.Box.of(-29, 0, -273, 34, 40, -233)), "lanes to the north: " + e.box);
        helper.succeed();
    }
}
