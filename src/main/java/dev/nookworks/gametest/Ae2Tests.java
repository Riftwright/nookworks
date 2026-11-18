package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.CrateBlockEntity;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.ItemResource;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * Applied Energistics 2's buses on our blocks, with the real mod: a storage bus on a Storage Barrel and on a Crate reads
 * the true count and puts in and takes out exactly; an import bus on a Crusher takes only what it made. The network is a
 * cable with the buses on it and a Creative Energy Cell, set as a player sets them (AE2's PartHelper). Registered only
 * with AE2.
 */
final class Ae2Tests {
    private Ae2Tests() {}

    static void register() {
        if (!Foreign.loaded("ae2")) {
            return;
        }
        NookworksGameTests.test("xmod_ae2_control_storage_bus_on_a_chest", 100, h -> storageBus(h, false));
        NookworksGameTests.test("xmod_ae2_storage_bus_on_a_storage_barrel", 100, h -> storageBus(h, true));
        NookworksGameTests.test("xmod_ae2_storage_bus_on_a_crate", 100, Ae2Tests::storageBusOnCrate);
        NookworksGameTests.test("xmod_ae2_import_bus_takes_only_what_a_crusher_made", 400, Ae2Tests::importBus);
    }

    static final BlockPos BUS = new BlockPos(3, 1, 3);

    /** A cable at {@code pos} powered by a Creative Energy Cell above it, with the given parts on its faces. */
    private static void network(GameTestHelper helper, BlockPos pos, Object... facesAndParts) {
        helper.setBlock(pos.above(), Foreign.block("ae2:creative_energy_cell"));
        part(helper, pos, null, "ae2:fluix_glass_cable");
        for (int i = 0; i < facesAndParts.length; i += 2) {
            part(helper, pos, (Direction) facesAndParts[i], (String) facesAndParts[i + 1]);
        }
    }

    private static void part(GameTestHelper helper, BlockPos pos, @Nullable Direction side, String item) {
        Object placed = Foreign.callStatic("appeng.api.parts.PartHelper", "setPart", helper.getLevel(), helper.absolutePos(pos), side,
                Foreign.fakePlayer(helper), Foreign.item(item));
        check(helper, placed != null, "the " + item + " went on at " + side);
    }

    /** The network's whole store, as its terminals see it. */
    private static Object storage(GameTestHelper helper, BlockPos pos, Direction side) {
        Object part = Foreign.callStatic("appeng.api.parts.PartHelper", "getPart", helper.getLevel(), helper.absolutePos(pos), side);
        Object node = Foreign.call(part, "getGridNode");
        check(helper, node != null, "the bus is on a grid");
        Object grid = Foreign.call(node, "getGrid");
        return Foreign.call(Foreign.call(grid, "getStorageService"), "getInventory");
    }

    private static long count(Object storage, Item item) {
        Object stacks = Foreign.call(storage, "getAvailableStacks");
        return (long) Foreign.call(stacks, "get", key(item));
    }

    private static Object key(Item item) {
        return Foreign.callStatic("appeng.api.stacks.AEItemKey", "of", new ItemStack(item));
    }

    private static long move(Object storage, String how, Item item, long amount) {
        return (long) Foreign.call(storage, how, key(item), amount, Foreign.constant("appeng.api.config.Actionable", "MODULATE"),
                Foreign.callStatic("appeng.api.networking.security.IActionSource", "empty"));
    }

    /**
     * A storage bus on a Netherite Storage Barrel holding 16,384 + 9,000 cobblestone (a vanilla chest of 27 x 64 in the
     * control): the network reads every one, takes 500 in and 1,234 out, and the barrel holds exactly that.
     */
    private static void storageBus(GameTestHelper helper, boolean barrel) {
        BlockPos at = BUS.east();
        long start;
        if (barrel) {
            helper.setBlock(at, ModBlocks.NETHERITE_STORAGE.get());
            StorageBlockEntity storage = helper.getBlockEntity(at, StorageBlockEntity.class);
            int perSlot = (int) storage.inventory().getCapacityAsLong(0, ItemResource.of(Items.COBBLESTONE));
            storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), perSlot);
            storage.inventory().set(1, ItemResource.of(Items.COBBLESTONE), 9_000);
            start = perSlot + 9_000L;
        } else {
            helper.setBlock(at, Blocks.CHEST);
            var chest = helper.getBlockEntity(at, ChestBlockEntity.class);
            for (int i = 0; i < 20; i++) {
                chest.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
            }
            start = 20 * 64L;
        }
        network(helper, BUS, Direction.EAST, "ae2:storage_bus");
        long put = barrel ? 500 : 64;
        long take = barrel ? 1_234 : 100;
        helper.startSequence().thenWaitUntil(() -> {
            Object storage = storage(helper, BUS, Direction.EAST);
            check(helper, count(storage, Items.COBBLESTONE) == start, "the network reads " + count(storage, Items.COBBLESTONE) + " of " + start);
        }).thenExecute(() -> {
            Object storage = storage(helper, BUS, Direction.EAST);
            long in = move(storage, "insert", Items.COBBLESTONE, put);
            long out = move(storage, "extract", Items.COBBLESTONE, take);
            long held = OtherModPipesTests.held(helper, at, Items.COBBLESTONE);
            check(helper, in == put && out == take && held == start + put - take,
                    "put " + in + " of " + put + " in, took " + out + " of " + take + " out: the block holds " + held + ", expected " + (start + put - take));
        }).thenWaitUntil(() -> {
            Object storage = storage(helper, BUS, Direction.EAST);
            check(helper, count(storage, Items.COBBLESTONE) == start + put - take, "and the network reads it: " + count(storage, Items.COBBLESTONE));
        }).thenSucceed();
    }

    private static void storageBusOnCrate(GameTestHelper helper) {
        BlockPos at = BUS.east();
        helper.setBlock(at, ModBlocks.CRATE.get());
        CrateBlockEntity crate = helper.getBlockEntity(at, CrateBlockEntity.class);
        crate.setItem(0, new ItemStack(Items.DIAMOND, 40));
        crate.setItem(3, new ItemStack(Items.DIAMOND, 30));
        network(helper, BUS, Direction.EAST, "ae2:storage_bus");
        helper.startSequence().thenWaitUntil(() -> check(helper, count(storage(helper, BUS, Direction.EAST), Items.DIAMOND) == 70,
                "the network reads the crate's 70 diamonds: " + count(storage(helper, BUS, Direction.EAST), Items.DIAMOND))).thenExecute(() -> {
            Object storage = storage(helper, BUS, Direction.EAST);
            long in = move(storage, "insert", Items.DIAMOND, 20);
            long out = move(storage, "extract", Items.DIAMOND, 55);
            long held = OtherModPipesTests.held(helper, at, Items.DIAMOND);
            check(helper, in == 20 && out == 55 && held == 35, "20 in, 55 out: " + in + ", " + out + "; the crate holds " + held);
        }).thenSucceed();
    }

    /** A storage bus on a chest (west) and an import bus on a Crusher (east): the gravel it made comes in, the cobblestone stays. */
    private static void importBus(GameTestHelper helper) {
        helper.setBlock(BUS.west(), Blocks.CHEST);
        helper.setBlock(BUS.east(), ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(BUS.east(), CrusherBlockEntity.class);
        // walled in, so the Crusher cannot drop what it made out of itself (it does, where there is room)
        for (BlockPos wall : java.util.List.of(BUS.east().above(), BUS.east().east(), BUS.east().north(), BUS.east().south())) {
            helper.setBlock(wall, Blocks.STONE);
        }
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 10));
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL, 7));
        network(helper, BUS, Direction.WEST, "ae2:storage_bus", Direction.EAST, "ae2:import_bus");
        var chest = helper.getBlockEntity(BUS.west(), ChestBlockEntity.class);
        helper.startSequence().thenWaitUntil(() -> check(helper, OtherModPipesTests.countIn(chest, Items.GRAVEL) == 7,
                "the import bus brought the gravel in: " + OtherModPipesTests.countIn(chest, Items.GRAVEL))).thenExecuteAfter(60, () ->
                check(helper, OtherModPipesTests.countIn(chest, Items.COBBLESTONE) == 0 && crusher.getItem(CrusherBlockEntity.INPUT).getCount() == 10,
                        "the cobblestone stays to be crushed: " + crusher.getItem(CrusherBlockEntity.INPUT))).thenSucceed();
    }
}
