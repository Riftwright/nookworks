package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.NookworksConfig.Activation;
import dev.nookworks.dev.bot.Stairs;
import dev.nookworks.dev.bot.Ways;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The agent bot's own way down (dev tooling, dev.nookworks.dev.bot.Stairs and Ways): the user's idea of 2026-10-22, after
 * every trip down a cave ended in a death - a stair three wide and three high dug with a Nookworks hammer, one blow a
 * step, lava and water looked for before each, and trips up and down by the ways it dug. The stair and the tunnel are
 * dug here with a real hammer, by a player stood and aimed as the bot stands and aims.
 */
final class BotDigTests {
    private BotDigTests() {}

    static void register() {
        test("bot_dig_stair_one_blow_a_step", BotDigTests::stair);
        test("bot_dig_tunnel_one_blow_a_step", BotDigTests::tunnel);
        test("bot_dig_hammer_swung_standing", BotDigTests::standing);
        test("bot_dig_looks_for_lava_and_water", BotDigTests::hazards);
        test("bot_dig_ores_in_the_walls", BotDigTests::ores);
        test("bot_dig_trips_go_by_its_own_ways", BotDigTests::ways);
    }

    private static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        for (BlockPos p : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
            helper.setBlock(p, state);
        }
    }

    /** A survival player with a stone hammer, its feet in the middle of the cell, looking at the step's aim point, holding
     *  sneak as the bot does for a hammer blow by default. */
    private static ServerPlayer stand(GameTestHelper helper, ServerPlayer player, Stairs.Step s) {
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

    private static ServerPlayer hammerPlayer(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STONE_HAMMER.get()));
        return player;
    }

    /** Four steps of a stair down through solid stone, each one hammer blow; then the stair's shape, block by block. */
    private static void stair(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 9, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 8, 3, 1, 9, 3, Blocks.AIR.defaultBlockState()); // the hole it stands in at the start
        var level = helper.getLevel();
        ServerPlayer player = hammerPlayer(helper);
        BlockPos feet = helper.absolutePos(new BlockPos(1, 8, 3));
        Direction east = Direction.EAST;
        for (int k = 1; k <= 4; k++) {
            Stairs.Step s = Stairs.next(Stairs.Kind.STAIR, feet, east);
            check(helper, Stairs.hazard(level, s, (st, p) -> true) == null, "solid stone, nothing to fear at step " + k);
            check(helper, Stairs.hammerTakes(level.getBlockState(s.aim())), "the hammer strikes stone at step " + k);
            stand(helper, player, s).gameMode.destroyBlock(s.aim());
            check(helper, Stairs.cleared(level, s), "one blow opened the whole step " + k + ": " + left(level, s));
            check(helper, !Stairs.needsFloor(level, s), "a floor under step " + k);
            check(helper, s.to().equals(feet.east().below()), "a step is one along and one down");
            feet = s.to();
        }
        for (int k = 1; k <= 4; k++) {
            int x = 1 + k;
            int floor = 7 - k; // the feet of step k at 8 - k
            for (int z = 2; z <= 4; z++) {
                for (int y = floor + 1; y <= floor + 3; y++) {
                    check(helper, helper.getBlockState(new BlockPos(x, y, z)).isAir(), "open: " + x + " " + y + " " + z);
                }
                check(helper, helper.getBlockState(new BlockPos(x, floor, z)).is(Blocks.STONE), "the step's floor: " + x + " " + floor + " " + z);
                check(helper, helper.getBlockState(new BlockPos(x, floor + 4, z)).is(Blocks.STONE) || floor + 4 > 9, "the roof: " + x + " " + (floor + 4));
            }
            for (int z : new int[] {1, 5}) {
                check(helper, helper.getBlockState(new BlockPos(x, floor + 2, z)).is(Blocks.STONE), "the walls stand: " + x + " " + z);
            }
        }
        check(helper, helper.getBlockState(new BlockPos(6, 4, 3)).is(Blocks.STONE), "nothing dug past the last step");
        helper.succeed();
    }

    /** The cells still standing in a step (for a failure's message). */
    private static String left(net.minecraft.world.level.Level level, Stairs.Step s) {
        List<String> out = new ArrayList<>();
        for (BlockPos p : s.open()) {
            if (!Stairs.passable(level, p, level.getBlockState(p))) {
                out.add(p.toShortString());
            }
        }
        return out.toString();
    }

    /** A level tunnel three by three, four blows long, its floor left. */
    private static void tunnel(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 6, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 2, 3, 1, 4, 3, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        ServerPlayer player = hammerPlayer(helper);
        BlockPos feet = helper.absolutePos(new BlockPos(1, 2, 3));
        for (int k = 1; k <= 4; k++) {
            Stairs.Step s = Stairs.next(Stairs.Kind.TUNNEL, feet, Direction.EAST);
            stand(helper, player, s).gameMode.destroyBlock(s.aim());
            check(helper, Stairs.cleared(level, s), "one blow opened tunnel step " + k + ": " + left(level, s));
            check(helper, s.to().equals(feet.east()), "a tunnel step is level");
            feet = s.to();
        }
        for (int x = 2; x <= 5; x++) {
            for (int z = 2; z <= 4; z++) {
                check(helper, helper.getBlockState(new BlockPos(x, 1, z)).is(Blocks.STONE), "the floor stays: " + x + " " + z);
                check(helper, helper.getBlockState(new BlockPos(x, 5, z)).is(Blocks.STONE), "the roof stays: " + x + " " + z);
                for (int y = 2; y <= 4; y++) {
                    check(helper, helper.getBlockState(new BlockPos(x, y, z)).isAir(), "open: " + x + " " + y + " " + z);
                }
            }
        }
        helper.succeed();
    }

    /**
     * The blow is struck standing: by default (hammer.activation NOT_SNEAKING) a sneaking player's hammer takes one block,
     * and a stair dug so would be one block a blow. A block on its way, broken alone with a hammer, it takes sneaking.
     */
    private static void standing(GameTestHelper helper) {
        check(helper, !Stairs.sneakForHammer(Activation.NOT_SNEAKING), "default: the 3x3 standing");
        check(helper, Stairs.sneakForHammer(Activation.SNEAKING), "set to sneaking: the 3x3 sneaking");
        check(helper, !Stairs.sneakForHammer(Activation.ALWAYS), "always: standing will do");
        check(helper, Stairs.sneakForOne(Activation.NOT_SNEAKING), "default: one block sneaking");
        check(helper, !Stairs.sneakForOne(Activation.SNEAKING), "set to sneaking: one block standing");
        // the same blow as a stair's first, struck sneaking: one block, eight left
        fill(helper, 0, 1, 0, 6, 9, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 8, 3, 1, 9, 3, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        Stairs.Step s = Stairs.next(Stairs.Kind.STAIR, helper.absolutePos(new BlockPos(1, 8, 3)), Direction.EAST);
        ServerPlayer player = stand(helper, hammerPlayer(helper), s);
        player.setShiftKeyDown(true);
        player.gameMode.destroyBlock(s.aim());
        int standing = 0;
        for (BlockPos p : s.open()) {
            if (!level.getBlockState(p).isAir()) {
                standing++;
            }
        }
        check(helper, standing == 8, "sneaking, the blow took one block (8 left), took " + (9 - standing));
        helper.succeed();
    }

    /** Lava or water in a step or touching it stops it; further off, or below the rock under the floor, does not. A build
     *  in the way stops it; a cave under the step wants a floor. */
    private static void hazards(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 9, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 8, 3, 1, 9, 3, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        Stairs.Step s = Stairs.next(Stairs.Kind.STAIR, helper.absolutePos(new BlockPos(1, 8, 3)), Direction.EAST);
        check(helper, Stairs.hazard(level, s, (st, p) -> true) == null, "solid stone: none");
        // the step opens x=2, y 7-9, z 2-4: lava beside its wall (z=5)
        helper.setBlock(new BlockPos(2, 8, 5), Blocks.LAVA);
        Stairs.Hazard h = Stairs.hazard(level, s, (st, p) -> true);
        check(helper, h != null && h.what().equals("lava") && h.pos().equals(helper.absolutePos(new BlockPos(2, 8, 5))), "lava by the wall: " + h);
        helper.setBlock(new BlockPos(2, 8, 5), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 8, 6), Blocks.LAVA); // a block further: behind the wall
        check(helper, Stairs.hazard(level, s, (st, p) -> true) == null, "lava behind a wall a block thick: none");
        helper.setBlock(new BlockPos(3, 8, 3), Blocks.WATER); // in the next column, the face beyond the step
        h = Stairs.hazard(level, s, (st, p) -> true);
        check(helper, h != null && h.what().equals("water"), "water beyond the step's face: " + h);
        helper.setBlock(new BlockPos(3, 8, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 6, 3), Blocks.LAVA); // the floor itself
        h = Stairs.hazard(level, s, (st, p) -> true);
        check(helper, h != null && h.what().equals("lava"), "lava for a floor: " + h);
        helper.setBlock(new BlockPos(2, 6, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 9, 2), Blocks.OAK_PLANKS);
        h = Stairs.hazard(level, s, (st, p) -> st.is(Blocks.STONE));
        check(helper, h != null && h.what().equals("minecraft:oak_planks"), "a build in the way: " + h);
        helper.setBlock(new BlockPos(2, 9, 2), Blocks.STONE);
        // a cave under the step: a floor wanted, and how deep the drop is
        fill(helper, 2, 3, 3, 2, 6, 3, Blocks.AIR.defaultBlockState());
        check(helper, Stairs.needsFloor(level, s), "a hole for a floor");
        check(helper, Stairs.dropUnder(level, s, 8) == 4, "four deep: " + Stairs.dropUnder(level, s, 8));
        helper.succeed();
    }

    /** Ores showing round a tunnel's step are found; their vein is safe to take unless water touches it. */
    private static void ores(GameTestHelper helper) {
        fill(helper, 0, 1, 0, 6, 6, 6, Blocks.STONE.defaultBlockState());
        fill(helper, 1, 2, 3, 1, 4, 3, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        Stairs.Step s = Stairs.next(Stairs.Kind.TUNNEL, helper.absolutePos(new BlockPos(1, 2, 3)), Direction.EAST);
        BlockPos ore = new BlockPos(2, 3, 5); // in the wall the step opens beside
        helper.setBlock(ore, Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(2, 3, 6), Blocks.DEEPSLATE_IRON_ORE); // the same vein, deeper in
        helper.setBlock(new BlockPos(4, 3, 6), Blocks.COAL_ORE); // not touching the step
        List<BlockPos> found = Stairs.ores(level, s);
        check(helper, found.equals(List.of(helper.absolutePos(ore))), "the iron in the wall, not the coal: " + found);
        List<BlockPos> vein = Stairs.vein(level, helper.absolutePos(ore), 64);
        check(helper, vein.size() == 2, "iron in stone and in deepslate are one vein: " + vein);
        check(helper, Stairs.veinSafe(level, vein), "a dry vein is safe");
        helper.setBlock(new BlockPos(1, 3, 6), Blocks.WATER);
        check(helper, !Stairs.veinSafe(level, vein), "water touching the vein: left");
        helper.succeed();
    }

    /** A stair down and a tunnel from its foot: a trip from the tunnel's end home goes back along them; a walk on the level
     *  does not; nor one far from them. */
    private static void ways(GameTestHelper helper) {
        Ways ways = new Ways();
        String stair = ways.begin("stair", new BlockPos(0, 64, 0));
        for (int k = 1; k <= 30; k++) {
            ways.append(stair, new BlockPos(k, 64 - k, 0));
        }
        String tunnel = ways.begin("tunnel", new BlockPos(30, 34, 0));
        for (int k = 1; k <= 20; k++) {
            ways.append(tunnel, new BlockPos(30, 34, k));
        }
        check(helper, stair.equals("stair1") && tunnel.equals("tunnel1"), "names: " + stair + " " + tunnel);
        BlockPos home = new BlockPos(-4, 64, 3);
        List<BlockPos> back = ways.route(new BlockPos(30, 34, 20), home);
        check(helper, back != null && back.get(0).equals(new BlockPos(30, 34, 20)) && back.get(back.size() - 1).equals(new BlockPos(0, 64, 0)),
                "home from the tunnel's end: along it and up the stair: " + back);
        check(helper, back.contains(new BlockPos(30, 34, 2)) || back.contains(new BlockPos(30, 34, 0)), "by the tunnel's start");
        List<BlockPos> down = ways.route(home, new BlockPos(30, 34, 18));
        check(helper, down != null && down.get(0).equals(new BlockPos(0, 64, 0)), "down the same way: " + down);
        check(helper, ways.route(new BlockPos(5, 64, 5), new BlockPos(10, 64, 8)) == null, "a walk on the level: as it would go");
        check(helper, ways.route(new BlockPos(200, 30, 200), home) == null, "far from any way: as it would go");
        List<BlockPos> up = ways.wayUp(new BlockPos(29, 35, 1));
        check(helper, up != null && up.get(up.size() - 1).getY() == 64, "out underground: up its own stair: " + up);
        Ways again = Ways.read(ways.lines());
        check(helper, again.lines().equals(ways.lines()) && again.all().get("tunnel1").size() == 21, "kept in a file and read back");
        helper.succeed();
    }
}
