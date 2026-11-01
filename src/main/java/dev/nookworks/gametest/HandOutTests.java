package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.cooking.HandMachineBlock;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.crop.RicePolisherBlock;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * The machines worked by a handle send on what they make much as a dropper does (the user, 2026-10-31: 「ドロッパー
 * みたいに、よこにアイテムポイする方式に」): into a container under them, as before, else one in front, else out of the
 * front onto the ground just there - so an Oil Press with a pipe under it for its oil still has its cakes gathered. A
 * carpet in front is no wall; a slab is, and then what it makes waits inside.
 */
final class HandOutTests {
    private HandOutTests() {}

    static void register() {
        test("a_hand_worked_machine_throws_what_it_makes_out_of_its_front", HandOutTests::thrown);
        test("an_oil_press_over_a_pipe_throws_its_cakes_out_and_runs_its_oil_down", HandOutTests::pressOverPipe);
    }

    private static void thrown(GameTestHelper helper) {
        var level = helper.getLevel();
        // a Noodle Maker with nothing round it: its noodles fly out in front (north, as it faces here)
        BlockPos makerAt = new BlockPos(2, 2, 2);
        helper.setBlock(makerAt, ModKitchen.NOODLE_MAKER.get());
        var maker = helper.getBlockEntity(makerAt, HandMachineBlockEntity.class);
        maker.setItem(HandMachineBlockEntity.INPUT, new ItemStack(ModKitchen.UDON_DOUGH.get()));
        for (int i = 0; i < 4; i++) {
            maker.turn(level);
        }
        check(helper, maker.getItem(HandMachineBlockEntity.OUTPUT).isEmpty(), "the noodles do not stay in it");
        List<ItemEntity> noodles = lying(helper, ModKitchen.RAW_UDON.get());
        check(helper, noodles.size() == 1 && noodles.get(0).getItem().getCount() == 2
                && noodles.get(0).getZ() < helper.absolutePos(makerAt).getZ(), "they come out of its front: " + noodles);

        // a Rice Polisher facing east, a carpet in front of its chute: the rice comes out over the carpet
        BlockPos polisherAt = new BlockPos(2, 2, 6);
        helper.setBlock(polisherAt.east(), Blocks.MOSS_CARPET);
        helper.setBlock(polisherAt, ModCrops.RICE_POLISHER.get().defaultBlockState().setValue(RicePolisherBlock.FACING, Direction.EAST));
        var polisher = helper.getBlockEntity(polisherAt, RicePolisherBlockEntity.class);
        polisher.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 4));
        for (int i = 0; i < RicePolisherBlockEntity.TURNS; i++) {
            polisher.turn(level);
        }
        List<ItemEntity> rice = lying(helper, ModCrops.WHITE_RICE.get());
        check(helper, rice.size() == 1 && rice.get(0).getX() > helper.absolutePos(polisherAt).getX() + 1,
                "a carpet in front is no wall: the rice comes out to the east: " + rice);

        // a Hand Quern with a slab in front of its spout: the flour waits inside, nothing on the floor
        BlockPos quernAt = new BlockPos(6, 2, 2);
        helper.setBlock(quernAt.north(), Blocks.STONE_SLAB);
        helper.setBlock(quernAt, ModKitchen.HAND_QUERN.get().defaultBlockState().setValue(HandMachineBlock.FACING, Direction.NORTH));
        var quern = helper.getBlockEntity(quernAt, HandMachineBlockEntity.class);
        quern.setItem(HandMachineBlockEntity.INPUT, new ItemStack(Items.WHEAT, 2));
        for (int i = 0; i < 3; i++) {
            quern.turn(level);
        }
        check(helper, quern.getItem(HandMachineBlockEntity.OUTPUT).is(ModCooking.FLOUR.get()) && lying(helper, ModCooking.FLOUR.get()).isEmpty(),
                "a slab in front keeps the flour in");
        // the slab taken away, it goes out at the next hand-out
        helper.setBlock(quernAt.north(), Blocks.AIR);
        helper.runAfterDelay(10, () -> {
            check(helper, quern.getItem(HandMachineBlockEntity.OUTPUT).isEmpty() && lying(helper, ModCooking.FLOUR.get()).size() == 1,
                    "with the slab gone, the flour comes out by itself");
            helper.succeed();
        });
    }

    private static void pressOverPipe(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pressAt = new BlockPos(2, 3, 2);
        BlockPos pipeAt = pressAt.below();
        helper.setBlock(pipeAt, ModBlocks.COPPER_PIPE.get());
        helper.setBlock(pipeAt.below(), ModBlocks.COPPER_TANK.get());
        helper.setBlock(pressAt, ModOil.OIL_PRESS.get());
        // a press beside it with a hopper under it, as in worlds played before: its cakes go into the hopper
        BlockPos oldAt = new BlockPos(6, 3, 2);
        helper.setBlock(oldAt.below(), Blocks.HOPPER);
        helper.setBlock(oldAt, ModOil.OIL_PRESS.get());
        helper.runAfterDelay(2, () -> { // the press and the tank are in by now: the pipe joins them
            BlockPos at = helper.absolutePos(pipeAt);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
            var press = helper.getBlockEntity(pressAt, OilPressBlockEntity.class);
            var old = helper.getBlockEntity(oldAt, OilPressBlockEntity.class);
            var hopper = (HopperBlockEntity) level.getBlockEntity(helper.absolutePos(oldAt.below()));
            for (OilPressBlockEntity each : new OilPressBlockEntity[] {press, old}) {
                each.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
            }
            // a cake one seed in four: sixty-four seeds without one would be one press in some ten million
            while (lying(helper, ModOil.OIL_CAKE.get()).isEmpty() && !press.getItem(OilPressBlockEntity.INPUT).isEmpty()) {
                press.turn(level);
            }
            while (hopper.countItem(ModOil.OIL_CAKE.get()) == 0 && !old.getItem(OilPressBlockEntity.INPUT).isEmpty()) {
                old.turn(level);
            }
            List<ItemEntity> cakes = lying(helper, ModOil.OIL_CAKE.get());
            check(helper, cakes.size() == 1 && cakes.get(0).getZ() < helper.absolutePos(pressAt).getZ() && press.getItem(OilPressBlockEntity.CAKE).isEmpty(),
                    "with a pipe under it, its cake comes out of its front: " + cakes);
            check(helper, hopper.countItem(ModOil.OIL_CAKE.get()) == 1 && old.getItem(OilPressBlockEntity.CAKE).isEmpty(),
                    "with a hopper under it, the cake goes into the hopper, as before");
            int pressed = press.stored();
            helper.runAfterDelay(40, () -> {
                check(helper, press.stored() < pressed, "and the oil runs down into the pipe: " + pressed + " -> " + press.stored());
                helper.succeed();
            });
        });
    }

    /** The items of {@code item} lying in the test's area. */
    private static List<ItemEntity> lying(GameTestHelper helper, Item item) {
        return helper.getEntities(EntityTypes.ITEM).stream().filter(entity -> entity.getItem().is(item)).toList();
    }
}
