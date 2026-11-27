package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * A machine stopped by redstone puts nothing out, as a locked hopper (the user, 2026-11-25): what it has made waits in it,
 * neither into the chest below nor onto the ground, and goes out once the signal is gone.
 */
final class RedstoneHoldTests {
    private RedstoneHoldTests() {}

    static void register() {
        test("stopped_crusher_holds_its_output", 120, RedstoneHoldTests::crusher);
        test("stopped_electric_furnace_holds_its_output", 120, RedstoneHoldTests::furnace);
        test("stopped_sifter_drops_nothing", 120, RedstoneHoldTests::sifter);
        test("stopped_electrolytic_cell_holds_its_output", 120, RedstoneHoldTests::cell);
        test("stopped_retort_holds_its_output", 120, RedstoneHoldTests::retort);
    }

    private static int count(Container container, Item item) {
        int in = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            in += container.getItem(i).is(item) ? container.getItem(i).getCount() : 0;
        }
        return in;
    }

    /** A machine at {@code at} over a chest, a redstone block beside it (the default mode stops with a signal). */
    private static void held(GameTestHelper helper, BlockPos at, Container machine, java.util.function.Supplier<MachineStatus> status, Item made, int count) {
        BlockPos power = at.east();
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(40, () -> {
            var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
            check(helper, count(chest, made) == 0, "nothing went into the chest while stopped: " + count(chest, made));
            check(helper, count(machine, made) == count, "it waits in the machine: " + count(machine, made));
            check(helper, status.get() == MachineStatus.REDSTONE, "it says redstone holds it: " + status.get());
            helper.setBlock(power, Blocks.AIR);
            helper.runAfterDelay(40, () -> {
                check(helper, count(chest, made) == count, "it went down once the signal was gone: " + count(chest, made));
                helper.succeed();
            });
        });
    }

    private static void crusher(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(at, CrusherBlockEntity.class);
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL, 3));
        held(helper, at, crusher, crusher::guiStatus, Items.GRAVEL, 3);
    }

    private static void furnace(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.ELECTRIC_FURNACE.get());
        var furnace = helper.getBlockEntity(at, ElectricFurnaceBlockEntity.class);
        furnace.setItem(ElectricFurnaceBlockEntity.OUTPUT, new ItemStack(Items.IRON_INGOT, 4));
        held(helper, at, furnace, furnace::guiStatus, Items.IRON_INGOT, 4);
    }

    /**
     * The Electrolytic Cell holds as the other mercury machines do, and says so: it said its output was blocked, a
     * warning that rang a Machine Bell beside it while a lever had only stopped it (r23 self-review).
     */
    private static void cell(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.ELECTROLYTIC_CELL.get());
        var cell = helper.getBlockEntity(at, ElectrolyticCellBlockEntity.class);
        cell.setItem(1, new ItemStack(Items.IRON_INGOT, 2));
        held(helper, at, cell, cell::guiStatus, Items.IRON_INGOT, 2);
    }

    private static void retort(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(at, RetortBlockEntity.class);
        retort.setItem(RetortBlockEntity.INPUT_SLOTS, new ItemStack(Items.CHARCOAL, 3));
        held(helper, at, retort, retort::guiStatus, Items.CHARCOAL, 3);
    }

    /** Over open ground, a stopped Sifter drops nothing; let go, it drops what it held. */
    private static void sifter(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 3, 1);
        helper.setBlock(at.below(2), Blocks.STONE);
        helper.setBlock(at, ModBlocks.SIFTER.get());
        helper.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        var sifter = helper.getBlockEntity(at, SifterBlockEntity.class);
        sifter.setItem(1, new ItemStack(Items.FLINT, 5));
        AABB around = new AABB(helper.absolutePos(at)).inflate(3);
        helper.runAfterDelay(40, () -> {
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "nothing dropped while stopped");
            check(helper, count(sifter, Items.FLINT) == 5, "the flint waits in it");
            check(helper, sifter.guiStatus() == MachineStatus.REDSTONE, "it says redstone holds it: " + sifter.guiStatus());
            helper.setBlock(at.east(), Blocks.AIR);
            helper.runAfterDelay(40, () -> {
                check(helper, !helper.getLevel().getEntitiesOfClass(ItemEntity.class, around).isEmpty(), "dropped once the signal was gone");
                helper.succeed();
            });
        });
    }
}
