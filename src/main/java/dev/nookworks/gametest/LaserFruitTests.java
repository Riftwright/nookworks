package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.UUID;
import java.util.function.Consumer;

import dev.nookworks.block.LaserBlock;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * The Laser harvests pumpkins and melons (1.5; the user, 2026-11-10: 「レーザーでカボチャやスイカも収穫できるようにしてほしいな」):
 * a fruit grown on its stem is cut as by hand - a pumpkin, melon slices - the stem left, for its surge; a pumpkin, melon,
 * carved pumpkin or jack o'lantern set down as a block is cut too (the user, 2026-11-10: 「くり抜いたカボチャやランタンが
 * 壊せないのは不自然」), stone and a plain stem are not; a claim that keeps the fruit from the Laser's owner keeps it.
 */
final class LaserFruitTests {
    private LaserFruitTests() {}

    static void register() {
        test("the_laser_cuts_grown_pumpkins_and_melons_and_leaves_the_stem", LaserFruitTests::cuts);
        test("the_laser_cuts_carved_pumpkins_jack_o_lanterns_and_placed_gourds", LaserFruitTests::placed);
        test("the_laser_cuts_no_fruit_a_claim_keeps_from_its_owner", LaserFruitTests::claimed);
    }

    /** A Laser at {@code pos} firing east, full of power. */
    private static LaserBlockEntity laser(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.EAST));
        var laser = helper.getBlockEntity(pos, LaserBlockEntity.class);
        laser.energy().set(LaserBlockEntity.CAPACITY);
        return laser;
    }

    /** A fruit at {@code fruit}, its stem south of it on farmland, attached and facing it. */
    private static void grown(GameTestHelper helper, BlockPos fruit, Block block, Block attached) {
        helper.setBlock(fruit.below(), Blocks.STONE);
        helper.setBlock(fruit.south().below(), Blocks.FARMLAND);
        helper.setBlock(fruit, block);
        helper.setBlock(fruit.south(), attached.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.NORTH));
    }

    private static void tick(GameTestHelper helper, BlockPos pos, LaserBlockEntity laser, int ticks) {
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < ticks; i++) {
            LaserBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), laser);
        }
    }

    private static int dropped(GameTestHelper helper, BlockPos around, Item item) {
        BlockPos at = helper.absolutePos(around);
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2)).stream()
                .filter(e -> e.getItem().is(item)).mapToInt(e -> e.getItem().getCount()).sum();
    }

    private static void cuts(GameTestHelper helper) {
        BlockPos pumpkinLaser = new BlockPos(1, 2, 1);
        BlockPos pumpkin = pumpkinLaser.east(3);
        grown(helper, pumpkin, Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM);
        var first = laser(helper, pumpkinLaser);
        check(helper, LaserBlockEntity.grownFruit(helper.getLevel(), helper.absolutePos(pumpkin)), "a pumpkin on its stem is grown fruit");
        tick(helper, pumpkinLaser, first, 3);
        check(helper, helper.getBlockState(pumpkin).isAir(), "the pumpkin is cut: " + helper.getBlockState(pumpkin));
        check(helper, dropped(helper, pumpkin, Items.PUMPKIN) == 1, "and drops a pumpkin, as cut by hand");
        BlockState stem = helper.getBlockState(pumpkin.south());
        check(helper, stem.is(Blocks.PUMPKIN_STEM), "the stem left, to grow another: " + stem);
        long spent = LaserBlockEntity.CAPACITY - first.energy().getAmountAsLong();
        check(helper, spent >= MachineTuning.energy(LaserBlockEntity.FE_PER_CUT) + MachineTuning.energy(LaserBlockEntity.FE_PER_TICK),
                "the cut is paid for: " + spent + " FE");

        BlockPos melonLaser = new BlockPos(1, 2, 3);
        BlockPos melon = melonLaser.east(3);
        grown(helper, melon, Blocks.MELON, Blocks.ATTACHED_MELON_STEM);
        var second = laser(helper, melonLaser);
        tick(helper, melonLaser, second, 3);
        int slices = dropped(helper, melon, Items.MELON_SLICE);
        check(helper, helper.getBlockState(melon).isAir() && slices >= 3 && slices <= 7, "a melon cut into 3-7 slices: " + slices);
        check(helper, helper.getBlockState(melon.south()).is(Blocks.MELON_STEM), "its stem left");
        check(helper, second.guiStatus() == MachineStatus.STANDBY || second.guiStatus() == MachineStatus.RUNNING,
                "nothing more to do: " + second.guiStatus());

        // with too little stored for the cut it waits, the beam off, and says so
        BlockPos lowLaser = new BlockPos(1, 2, 5);
        BlockPos low = lowLaser.east(3);
        grown(helper, low, Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM);
        var third = laser(helper, lowLaser);
        third.energy().set(MachineTuning.energy(LaserBlockEntity.FE_PER_CUT));
        tick(helper, lowLaser, third, 2);
        check(helper, helper.getBlockState(low).is(Blocks.PUMPKIN) && third.guiStatus() == MachineStatus.CHARGING_CUT,
                "short of the surge it waits, charging: " + third.guiStatus());
        helper.succeed();
    }

    private static void placed(GameTestHelper helper) {
        int z = 0; // the arena is 7 x 7
        Item[] drops = {Items.MELON_SLICE, Items.CARVED_PUMPKIN, Items.JACK_O_LANTERN, Items.PUMPKIN};
        Block[] blocks = {Blocks.MELON, Blocks.CARVED_PUMPKIN, Blocks.JACK_O_LANTERN, Blocks.PUMPKIN};
        for (int i = 0; i < blocks.length; i++) {
            Block block = blocks[i];
            BlockPos at = new BlockPos(1, 2, z);
            BlockPos fruit = at.east(3);
            helper.setBlock(fruit.below(), Blocks.STONE);
            helper.setBlock(fruit, block);
            if (block == Blocks.MELON) {
                // a melon beside an unripe stem, not attached to it: set down by hand
                helper.setBlock(fruit.south().below(), Blocks.FARMLAND);
                helper.setBlock(fruit.south(), Blocks.MELON_STEM);
            }
            var laser = laser(helper, at);
            check(helper, LaserBlockEntity.cuttable(helper.getLevel(), helper.absolutePos(fruit)), block + " is cut");
            tick(helper, at, laser, 3);
            check(helper, helper.getBlockState(fruit).isAir(), block + " set down as a block is cut: " + helper.getBlockState(fruit));
            check(helper, dropped(helper, fruit, drops[i]) >= 1, block + " drops as broken by hand");
            if (block == Blocks.MELON) {
                check(helper, helper.getBlockState(fruit.south()).is(Blocks.MELON_STEM), "the stem beside it left alone");
            }
            z += 2;
        }
        // stone, and a stem with nothing on it, are not
        BlockPos stoneAt = new BlockPos(5, 2, 0);
        helper.setBlock(stoneAt, Blocks.STONE);
        check(helper, !LaserBlockEntity.cuttable(helper.getLevel(), helper.absolutePos(stoneAt)), "stone is not cut");
        helper.succeed();
    }

    private static void claimed(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        BlockPos fruit = at.east(3);
        grown(helper, fruit, Blocks.PUMPKIN, Blocks.ATTACHED_PUMPKIN_STEM);
        var laser = laser(helper, at);
        laser.setOwner(UUID.randomUUID());
        BlockPos kept = helper.absolutePos(fruit);
        UUID claimant = UUID.randomUUID();
        Consumer<BreakBlockEvent> stand = event -> {
            if (event.getPos().equals(kept) && (event.getPlayer() == null || !claimant.equals(event.getPlayer().getUUID()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BreakBlockEvent.class, stand);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(stand));
        tick(helper, at, laser, 20);
        check(helper, helper.getBlockState(fruit).is(Blocks.PUMPKIN), "a claimed pumpkin is not cut");
        check(helper, laser.guiStatus() == MachineStatus.STANDBY, "and the laser stands by, beam off: " + laser.guiStatus());
        helper.succeed();
    }
}
