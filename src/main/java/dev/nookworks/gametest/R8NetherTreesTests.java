package dev.nookworks.gametest;

import static dev.nookworks.gametest.MachineTests.placeByHand;
import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.block.LaserBlock;
import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The user's asks of 2026-10-06 about felling: the Nether's huge fungi fell as trees ("ネザーの木材（真紅、ゆがんだ）が
 * 木こり発動しないんだけど" - their crown is wart blocks and shroomlight, not leaves, so they never did), a wall of stems
 * someone built still does not; and the Laser's standby, cut to 2 FE/t, nothing at all between a Nested Timer's pulses.
 */
final class R8NetherTreesTests {
    private R8NetherTreesTests() {}

    static void register() {
        NookworksGameTests.test("timber_fells_a_huge_fungus", R8NetherTreesTests::timberFellsAHugeFungus);
        NookworksGameTests.test("timber_spares_a_built_stem_wall", R8NetherTreesTests::timberSparesABuiltStemWall);
        NookworksGameTests.test("laser_fells_a_huge_fungus", R8NetherTreesTests::laserFellsAHugeFungus);
        NookworksGameTests.test("laser_stands_by_at_a_built_stem_wall", R8NetherTreesTests::laserStandsByAtABuiltStemWall);
        NookworksGameTests.test("laser_draws_nothing_between_a_timers_pulses", R8NetherTreesTests::laserDrawsNothingBetweenPulses);
    }

    /**
     * A huge fungus as the Nether grows one: a trunk of {@code stem} at column {@code x} (y 1..5), and round its top a
     * cap of {@code wart} three across (y 4..6) with a shroomlight in it at the west. Returns the stems, bottom first.
     */
    static List<BlockPos> buildHugeFungus(GameTestHelper helper, int x, Block stem, Block wart) {
        List<BlockPos> stems = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            stems.add(new BlockPos(x, y, 3));
        }
        stems.forEach(p -> helper.setBlock(p, stem));
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(x - 1, 4, 2), new BlockPos(x + 1, 6, 4))) {
            if (!stems.contains(p)) {
                helper.setBlock(p.immutable(), wart);
            }
        }
        helper.setBlock(new BlockPos(x - 1, 5, 3), Blocks.SHROOMLIGHT);
        return stems;
    }

    /**
     * An axe on the foot of a crimson and of a warped huge fungus fells each stem, a point of wear a stem, as an
     * oak's logs; their wart blocks and shroomlight stay hanging, as when vanilla's stems are cut: they never decay, and
     * the leaves' fast decay only hurries what vanilla does.
     */
    private static void timberFellsAHugeFungus(GameTestHelper helper) {
        List<BlockPos> crimson = buildHugeFungus(helper, 1, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK);
        List<BlockPos> warped = buildHugeFungus(helper, 5, Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK);
        ServerPlayer player = MiningTests.playerWith(helper, new ItemStack(Items.DIAMOND_AXE));

        player.gameMode.destroyBlock(helper.absolutePos(crimson.get(0)));
        player.gameMode.destroyBlock(helper.absolutePos(warped.get(0)));

        crimson.forEach(p -> helper.assertBlockNotPresent(Blocks.CRIMSON_STEM, p));
        warped.forEach(p -> helper.assertBlockNotPresent(Blocks.WARPED_STEM, p));
        int damage = player.getMainHandItem().getDamageValue();
        check(helper, damage == crimson.size() + warped.size(), "a point of wear a stem, " + (crimson.size() + warped.size())
                + ": " + damage);
        helper.runAfterDelay(20, () -> {
            helper.assertBlockPresent(Blocks.NETHER_WART_BLOCK, new BlockPos(1, 6, 3));
            helper.assertBlockPresent(Blocks.NETHER_WART_BLOCK, new BlockPos(2, 5, 3));
            helper.assertBlockPresent(Blocks.SHROOMLIGHT, new BlockPos(0, 5, 3));
            helper.assertBlockPresent(Blocks.WARPED_WART_BLOCK, new BlockPos(5, 6, 3));
            helper.succeed();
        });
    }

    /**
     * Crimson stems set down by hand into a wall against a huge fungus's trunk and crown, wart blocks set down on its
     * top - as a log cabin against an oak, under leaves set down (persistent): the stems are remembered as placed, so the
     * fungus fells and the wall stands, and cutting the wall takes one stem. Wart blocks have no mark of their own; the
     * stems are what is known to be built.
     */
    private static void timberSparesABuiltStemWall(GameTestHelper helper) {
        List<BlockPos> tree = buildHugeFungus(helper, 1, Blocks.CRIMSON_STEM, Blocks.NETHER_WART_BLOCK);
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 1; y <= 3; y++) {
            for (int x = 2; x <= 5; x++) {
                wall.add(new BlockPos(x, y, 3));
            }
        }
        for (int x = 3; x <= 5; x++) {
            wall.add(new BlockPos(x, 4, 3)); // up beside the crown
        }
        wall.forEach(p -> placeByHand(helper, new ItemStack(Items.CRIMSON_STEM), p));
        for (int x = 3; x <= 5; x++) {
            placeByHand(helper, new ItemStack(Items.NETHER_WART_BLOCK), new BlockPos(x, 5, 3)); // a crown of the builder's
        }
        wall.forEach(p -> helper.assertBlockPresent(Blocks.CRIMSON_STEM, p));
        check(helper, PlacedLogs.isPlaced(helper.getLevel(), helper.absolutePos(wall.get(0))), "a stem put down by hand is remembered");
        ServerPlayer player = MiningTests.playerWith(helper, new ItemStack(Items.DIAMOND_AXE));

        player.gameMode.destroyBlock(helper.absolutePos(tree.get(0)));
        tree.forEach(p -> helper.assertBlockNotPresent(Blocks.CRIMSON_STEM, p));
        wall.forEach(p -> helper.assertBlockPresent(Blocks.CRIMSON_STEM, p));

        BlockPos cut = new BlockPos(4, 1, 3);
        player.gameMode.destroyBlock(helper.absolutePos(cut));
        helper.assertBlockNotPresent(Blocks.CRIMSON_STEM, cut);
        wall.stream().filter(p -> !p.equals(cut)).forEach(p -> helper.assertBlockPresent(Blocks.CRIMSON_STEM, p));
        helper.succeed();
    }

    /**
     * The Laser fells a huge fungus as a tree, by cutting it as it cuts logs (stems do not burn), and leaves its crown;
     * with the trunk gone its beam goes out.
     */
    private static void laserFellsAHugeFungus(GameTestHelper helper) {
        BlockPos laserPos = new BlockPos(0, 2, 3);
        MachineTests.placeLaser(helper, laserPos, Direction.EAST);
        List<BlockPos> stems = buildHugeFungus(helper, 3, Blocks.WARPED_STEM, Blocks.WARPED_WART_BLOCK);
        helper.setBlock(new BlockPos(6, 2, 3), Blocks.STONE); // behind it, so the beam ends in the arena
        helper.succeedWhen(() -> {
            stems.forEach(p -> check(helper, !helper.getBlockState(p).is(Blocks.WARPED_STEM), "the whole stem is felled, " + p));
            helper.assertBlockPresent(Blocks.WARPED_WART_BLOCK, new BlockPos(3, 6, 3));
            helper.assertBlockPresent(Blocks.SHROOMLIGHT, new BlockPos(2, 5, 3));
            check(helper, helper.getBlockState(laserPos).getValue(LaserBlock.BEAM) == 0, "and the beam is out: stone is nothing to it");
        });
    }

    /**
     * Stems set down by hand in the beam, wart blocks beside them: no tree, and stems do not burn, so after its first
     * look the Laser stands by, its beam out, drawing only its standby - not the full beam at a wall for ever.
     */
    private static void laserStandsByAtABuiltStemWall(GameTestHelper helper) {
        BlockPos laserPos = new BlockPos(0, 1, 3);
        LaserBlockEntity laser = MachineTests.placeLaser(helper, laserPos, Direction.EAST);
        for (int y = 1; y <= 3; y++) {
            placeByHand(helper, new ItemStack(Items.CRIMSON_STEM), new BlockPos(3, y, 3));
            placeByHand(helper, new ItemStack(Items.NETHER_WART_BLOCK), new BlockPos(4, y, 3));
        }
        long[] before = {0};
        helper.runAfterDelay(30, () -> before[0] = laser.energy().getAmountAsLong());
        helper.runAfterDelay(40, () -> {
            for (int y = 1; y <= 3; y++) {
                helper.assertBlockPresent(Blocks.CRIMSON_STEM, new BlockPos(3, y, 3));
            }
            check(helper, helper.getBlockState(laserPos).getValue(LaserBlock.BEAM) == 0 && laser.guiStatus() == MachineStatus.STANDBY,
                    "it stands by at a built wall, the beam out: " + laser.guiStatus());
            long spent = before[0] - laser.energy().getAmountAsLong();
            check(helper, spent == 10L * LaserBlockEntity.FE_STANDBY, "ten ticks on standby cost " + 10 * LaserBlockEntity.FE_STANDBY
                    + " FE, not the beam's: " + spent);
            helper.succeed();
        });
    }

    /**
     * Standing by, the Laser draws 2 FE/t: its 120,000 FE last fifty minutes, past the quarter hour or so a sapling takes
     * to grow on average (at 10 FE/t they lasted ten, and a log farm started on a few logs' power starved before its
     * first tree - the r8 automation test). Set to run with a signal and pulsed by a Nested Timer, it draws nothing at all
     * between pulses, and a pulse fells a grown tree.
     */
    private static void laserDrawsNothingBetweenPulses(GameTestHelper helper) {
        // a sapling's two stages, one random tick in seven growing it a stage, a random tick each 4096/3 game ticks
        int saplingTicks = 2 * 7 * 4096 / 3;
        check(helper, LaserBlockEntity.FE_STANDBY == 2, "standing by on 2 FE/t: " + LaserBlockEntity.FE_STANDBY);
        check(helper, LaserBlockEntity.CAPACITY / LaserBlockEntity.FE_STANDBY > 2 * saplingTicks,
                "a full buffer stands by through two saplings' growing: " + LaserBlockEntity.CAPACITY / LaserBlockEntity.FE_STANDBY + " ticks");

        BlockPos timerPos = new BlockPos(0, 1, 3);
        BlockPos laserPos = new BlockPos(1, 1, 3);
        helper.setBlock(timerPos, ModBlocks.NESTED_TIMER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST)); // its front, to the east, on the laser
        helper.getBlockEntity(timerPos, NestedTimerBlockEntity.class).setInterval(40);
        LaserBlockEntity laser = MachineTests.placeLaser(helper, laserPos, Direction.EAST);
        laser.setRedstoneMode(RedstoneMode.WITH_SIGNAL);
        laser.energy().set(50_000);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(3, y, 3), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1);
        helper.setBlock(new BlockPos(3, 5, 3), leaves);
        helper.setBlock(new BlockPos(4, 4, 3), leaves);
        helper.setBlock(new BlockPos(5, 1, 3), Blocks.STONE); // the beam ends here once the tree is down

        helper.runAfterDelay(30, () -> {
            check(helper, laser.energy().getAmountAsLong() == 50_000, "before the first pulse it draws nothing: "
                    + laser.energy().getAmountAsLong());
            check(helper, laser.guiStatus() == MachineStatus.REDSTONE, "waiting for a signal: " + laser.guiStatus());
            helper.assertBlockPresent(Blocks.OAK_LOG, new BlockPos(3, 1, 3));
        });
        long[] afterPulse = {0};
        helper.runAfterDelay(55, () -> {
            for (int y = 1; y <= 4; y++) {
                helper.assertBlockNotPresent(Blocks.OAK_LOG, new BlockPos(3, y, 3));
            }
            afterPulse[0] = laser.energy().getAmountAsLong();
            check(helper, afterPulse[0] < 50_000 - LaserBlockEntity.FE_PER_FELL, "the pulse fired it and paid the fell: " + afterPulse[0]);
        });
        helper.runAfterDelay(75, () -> {
            check(helper, laser.energy().getAmountAsLong() == afterPulse[0], "and between pulses it draws nothing again: "
                    + afterPulse[0] + " -> " + laser.energy().getAmountAsLong());
            check(helper, helper.getBlockState(laserPos).getValue(LaserBlock.BEAM) == 0, "the beam out");
            helper.succeed();
        });
    }
}
