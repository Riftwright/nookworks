package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.sorter.SorterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * Vanilla's item blocks with ours, as with vanilla's own (the 2026-11-24 audit, after the user's crafter circuit): a
 * dispenser sets a Dawnwood boat on water and lights an unlit lantern with flint and steel; a comparator reads a Sorter
 * and a Sifter; a Sorter's outlet fills a chest minecart standing there, as a hopper's would; and so does a Sifter over a
 * chest minecart, and a machine's front over a chest boat (2026-11-25).
 */
final class VanillaBlockTests {
    private VanillaBlockTests() {}

    static void register() {
        test("dispenser_sets_a_dawnwood_boat_on_water", 60, VanillaBlockTests::boat);
        test("dispenser_flint_and_steel_lights_an_unlit_lantern", 60, VanillaBlockTests::lantern);
        test("comparator_reads_a_sorter_and_a_sifter", VanillaBlockTests::comparator);
        test("sorter_outlet_fills_a_chest_minecart", 100, VanillaBlockTests::cart);
        test("comparator_reads_the_cooking_blocks", VanillaBlockTests::cooking);
        test("dispenser_lets_a_mob_bottles_mob_out", 60, VanillaBlockTests::mobBottle);
        test("sifter_fills_a_chest_minecart_under_it", 100, VanillaBlockTests::sifterCart);
        test("machine_front_fills_a_chest_boat", VanillaBlockTests::boatInFront);
    }

    private static BlockPos dispenser(GameTestHelper helper, ItemStack stack) {
        BlockPos pos = new BlockPos(1, 2, 2);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(pos, DispenserBlockEntity.class).setItem(0, stack);
        return pos;
    }

    private static void boat(GameTestHelper helper) {
        BlockPos pos = dispenser(helper, new ItemStack(ModWoods.DAWNWOOD_BOAT.get()));
        helper.setBlock(pos.east().below(), Blocks.STONE);
        helper.setBlock(pos.east(), Blocks.WATER);
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            var boats = helper.getLevel().getEntities(ModEntities.DAWNWOOD_BOAT.get(), new AABB(helper.absolutePos(pos)).inflate(3), e -> true);
            check(helper, boats.size() == 1, "one Dawnwood boat set on the water: " + boats);
            check(helper, helper.getBlockEntity(pos, DispenserBlockEntity.class).isEmpty(), "the boat item used");
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3)).isEmpty(),
                    "not thrown out as an item");
            helper.succeed();
        });
    }

    private static void lantern(GameTestHelper helper) {
        BlockPos pos = dispenser(helper, new ItemStack(Items.FLINT_AND_STEEL));
        helper.setBlock(pos.east().below(), Blocks.STONE);
        helper.setBlock(pos.east(), ModBlocks.UNLIT_LANTERN.get());
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            check(helper, helper.getBlockState(pos.east()).is(Blocks.LANTERN) && !helper.getBlockState(pos.east()).getValue(LanternBlock.HANGING),
                    "the lantern is lit, still standing: " + helper.getBlockState(pos.east()));
            check(helper, helper.getBlockEntity(pos, DispenserBlockEntity.class).getItem(0).getDamageValue() == 1, "the flint and steel worn once");
            helper.succeed();
        });
    }

    private static void comparator(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(1, 2, 1);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        var sorter = helper.getBlockEntity(sorterPos, SorterBlockEntity.class);
        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(sorterPos);
        check(helper, helper.getBlockState(sorterPos).getAnalogOutputSignal(level, at, Direction.SOUTH) == 0, "an empty sorter reads 0");
        sorter.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        int read = helper.getBlockState(sorterPos).getAnalogOutputSignal(level, at, Direction.SOUTH);
        check(helper, read > 0, "a sorter holding a stack reads as a hopper holding one: " + read);
        BlockPos sifterPos = new BlockPos(3, 2, 1);
        helper.setBlock(sifterPos, ModBlocks.SIFTER.get());
        var sifter = helper.getBlockEntity(sifterPos, SifterBlockEntity.class);
        BlockPos sifterAt = helper.absolutePos(sifterPos);
        check(helper, helper.getBlockState(sifterPos).getAnalogOutputSignal(level, sifterAt, Direction.SOUTH) == 0, "an empty sifter reads 0");
        sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(Items.GRAVEL, 64));
        check(helper, helper.getBlockState(sifterPos).getAnalogOutputSignal(level, sifterAt, Direction.SOUTH) > 0, "a sifter with gravel reads more");
        helper.succeed();
    }

    private static void cart(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(2, 2, 2);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        BlockPos outlet = sorterPos.relative(SorterBlock.otherSide(helper.getBlockState(sorterPos)));
        helper.setBlock(outlet.below(), Blocks.STONE);
        helper.setBlock(outlet, Blocks.RAIL);
        MinecartChest cart = helper.spawn(EntityTypes.CHEST_MINECART, outlet);
        helper.getBlockEntity(sorterPos, SorterBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        helper.runAfterDelay(80, () -> {
            int in = 0;
            for (int i = 0; i < cart.getContainerSize(); i++) {
                in += cart.getItem(i).is(Items.COBBLESTONE) ? cart.getItem(i).getCount() : 0;
            }
            check(helper, in == 4, "the four cobblestone go into the cart: " + in);
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(sorterPos)).inflate(3)).isEmpty(),
                    "none thrown onto the rail");
            helper.succeed();
        });
    }

    private static int count(net.minecraft.world.Container container, net.minecraft.world.item.Item item) {
        int in = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            in += container.getItem(i).is(item) ? container.getItem(i).getCount() : 0;
        }
        return in;
    }

    /** What a Sifter has made goes into a chest minecart on the rail under it, not onto the rail. */
    private static void sifterCart(GameTestHelper helper) {
        BlockPos sifterPos = new BlockPos(1, 3, 1);
        helper.setBlock(sifterPos.below(2), Blocks.STONE);
        helper.setBlock(sifterPos.below(), Blocks.RAIL);
        helper.setBlock(sifterPos, ModBlocks.SIFTER.get());
        MinecartChest cart = helper.spawn(EntityTypes.CHEST_MINECART, sifterPos.below());
        helper.getBlockEntity(sifterPos, SifterBlockEntity.class).setItem(1, new ItemStack(Items.FLINT, 5));
        helper.runAfterDelay(60, () -> {
            check(helper, count(cart, Items.FLINT) == 5, "the five flint go into the cart: " + count(cart, Items.FLINT));
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(sifterPos)).inflate(3)).isEmpty(),
                    "none dropped onto the rail");
            helper.succeed();
        });
    }

    /** A machine handing out of its front (an Oil Press, a Hand Quern) puts it into a chest boat on the water there. */
    private static void boatInFront(GameTestHelper helper) {
        BlockPos machine = new BlockPos(1, 2, 1);
        BlockPos water = machine.east();
        helper.setBlock(machine.below(), Blocks.STONE);
        helper.setBlock(water.below(), Blocks.STONE);
        helper.setBlock(water, Blocks.WATER);
        var boat = helper.spawn(EntityTypes.OAK_CHEST_BOAT, water);
        ItemStack stack = new ItemStack(Items.WHEAT_SEEDS, 7);
        dev.nookworks.machine.MachineIO.handOut(helper.getLevel(), helper.absolutePos(machine), Direction.EAST, stack);
        check(helper, stack.isEmpty(), "all of it went: " + stack);
        check(helper, count((net.minecraft.world.Container) boat, Items.WHEAT_SEEDS) == 7, "the seven seeds are in the boat");
        check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(machine)).inflate(3)).isEmpty(),
                "none thrown onto the water");
        helper.succeed();
    }

    /** Each cooking block reads 0 empty and more with something in it, as a chest or a smoker does. */
    private static void cooking(GameTestHelper helper) {
        var blocks = java.util.List.of(dev.nookworks.registry.ModCooking.IRON_POT.get(), dev.nookworks.registry.ModCooking.FRYING_PAN.get(),
                dev.nookworks.registry.ModCooking.GRILL.get(), dev.nookworks.registry.ModCooking.NUKADOKO.get(),
                dev.nookworks.registry.ModKitchen.WOODEN_TRAY.get());
        var candidates = java.util.List.of(Items.POTATO, Items.CARROT, Items.BEEF, Items.CHICKEN, Items.WHEAT, Items.EGG,
                Items.BREAD, dev.nookworks.registry.ModKitchen.BREAD_DOUGH.get());
        int x = 0;
        for (var block : blocks) {
            BlockPos pos = new BlockPos(x, 2, 1);
            x += 2;
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, block);
            BlockPos at = helper.absolutePos(pos);
            var level = helper.getLevel();
            var name = block.getName().getString();
            check(helper, helper.getBlockState(pos).hasAnalogOutputSignal(), name + " gives a comparator reading");
            check(helper, helper.getBlockState(pos).getAnalogOutputSignal(level, at, Direction.SOUTH) == 0, name + " reads 0 empty");
            var handler = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, at, Direction.UP);
            check(helper, handler != null, name + " takes items");
            int put = 0;
            for (var item : candidates) {
                try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
                    put = handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(item), 1, tx);
                    if (put > 0) {
                        tx.commit();
                        break;
                    }
                }
            }
            check(helper, put > 0, name + " took one of the test foods");
            int read = helper.getBlockState(pos).getAnalogOutputSignal(level, at, Direction.SOUTH);
            check(helper, read > 0, name + " with something in reads more: " + read);
        }
        helper.succeed();
    }

    /** A filled Mob Bottle in a dispenser lets its cow out in front and leaves the empty bottle in the dispenser. */
    private static void mobBottle(GameTestHelper helper) {
        BlockPos pos = dispenser(helper, dev.nookworks.mobcapture.MobBottleItem.filled(EntityTypes.COW));
        helper.setBlock(pos.east().below(), Blocks.STONE);
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            var cows = helper.getLevel().getEntities(EntityTypes.COW, new AABB(helper.absolutePos(pos.east())).inflate(1), e -> true);
            check(helper, cows.size() == 1, "the cow comes out in front: " + cows);
            ItemStack left = helper.getBlockEntity(pos, DispenserBlockEntity.class).getItem(0);
            check(helper, left.is(dev.nookworks.registry.ModItems.MOB_BOTTLE.get()) && dev.nookworks.mobcapture.MobBottleItem.captured(left) == null,
                    "the empty bottle stays in the dispenser: " + left);
            check(helper, helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(3)).isEmpty(),
                    "nothing thrown out");
            helper.succeed();
        });
    }
}
