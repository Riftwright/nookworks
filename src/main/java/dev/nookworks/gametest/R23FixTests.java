package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.block.HubStoneBlock;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.cooking.HandMachineBlockEntity;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.drive.BeltKind;
import dev.nookworks.drive.Belts;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.drive.DriveWheelBlockEntity;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilPressBlock;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.sorter.SorterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.vehicle.minecart.MinecartChest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.AABB;

/** What the self-review of 2026-11-26 (r23) found in what came after 1.8.0, each mended and kept so. */
final class R23FixTests {
    private R23FixTests() {}

    static void register() {
        test("comparator_through_a_block_goes_dark_when_a_sorter_is_broken", 60, R23FixTests::comparatorAfterBreak);
        test("iron_pot_reads_full_while_a_dish_waits", 900, R23FixTests::potReading);
        test("dispenser_keeps_a_mob_bottle_facing_a_wall", 40, R23FixTests::bottleIntoAWall);
        test("sorter_puts_nothing_kept_out_back_into_a_crafter", 60, R23FixTests::keptOutNotIntoCrafter);
        test("sorter_line_keeps_out_what_its_last_sorter_does", R23FixTests::sorterLine);
        test("hub_stone_fed_by_dust_reads_its_own_power", R23FixTests::hubStoneOnDust);
        test("item_warp_receiver_fills_a_chest_minecart", 200, R23FixTests::warpIntoCart);
        test("machines_wait_at_a_rail_for_the_next_cart", 300, R23FixTests::waitAtRail);
        test("machines_wait_at_a_rail_further_down", 300, R23FixTests::waitAtRailBelow);
        test("item_warp_receiver_waits_at_an_empty_rail", 300, R23FixTests::warpWaitsAtRail);
        test("motor_stopped_by_redstone_holds_what_it_turns_out", 300, R23FixTests::motorHolds);
        test("motor_stopped_by_redstone_holds_a_belted_press", 300, R23FixTests::motorHoldsBelted);
        test("motor_running_beside_a_stopped_one_lets_a_press_hand_out", 200, R23FixTests::motorRunningBesideStopped);
        test("motor_stopped_holds_from_the_tick_its_signal_comes", 200, R23FixTests::motorHoldsAtOnce);
        test("dispenser_facing_sideways_lets_a_small_slime_out", 60, R23FixTests::smallSlimeSideways);
        test("retort_matches_a_tag_and_an_item_both_ways", R23FixTests::retortTagAndItem);
    }

    /**
     * A comparator reading a Sorter through a stone goes dark once the sorter is broken: a removal tells comparators only
     * when what takes its place gives a reading, and it kept its last one.
     */
    private static void comparatorAfterBreak(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(1, 2, 1);
        BlockPos stone = sorterPos.east();
        BlockPos comparator = stone.east();
        helper.setBlock(sorterPos.below(), Blocks.STONE);
        helper.setBlock(stone.below(), Blocks.STONE);
        helper.setBlock(comparator.below(), Blocks.STONE);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        helper.setBlock(stone, Blocks.STONE);
        // its back to the stone: it reads what is behind it, through the stone
        helper.setBlock(comparator, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        var sorter = helper.getBlockEntity(sorterPos, SorterBlockEntity.class);
        for (int slot = 0; slot < SorterBlockEntity.INPUT_SLOTS; slot++) {
            sorter.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.runAfterDelay(10, () -> {
            var read = helper.getBlockEntity(comparator, ComparatorBlockEntity.class);
            check(helper, read.getOutputSignal() > 0, "a full sorter reads through the stone: " + read.getOutputSignal());
            helper.setBlock(sorterPos, Blocks.AIR);
            helper.runAfterDelay(10, () -> {
                check(helper, read.getOutputSignal() == 0, "broken, it reads nothing: " + read.getOutputSignal());
                helper.succeed();
            });
        });
    }

    /**
     * The Iron Pot reads by its ingredients against its room, at most 14, and 15 while a dish waits: read as a hopper
     * sees it, a dish done with no bowls in read 0, the moment the rice was ready.
     */
    private static void potReading(GameTestHelper helper) {
        ServerPlayer player = R21FixTests.clicker(helper);
        BlockPos grill = new BlockPos(2, 3, 2);
        IronPotBlockEntity pot = R21FixTests.heatedPot(helper, player, grill);
        check(helper, pot.comparatorReading() == 0, "empty, 0");
        pot.addLiquid(PotRecipe.Liquid.WATER, IronPotBlockEntity.CAPACITY);
        pot.addIngredient(new ItemStack(ModCrops.WHITE_RICE.get()));
        int one = pot.comparatorReading();
        check(helper, one > 0 && one < 14, "one thing in of eight: a low reading, " + one);
        helper.startSequence().thenWaitUntil(() -> check(helper, !pot.dish().isEmpty(), "the rice cooks")).thenExecute(() -> {
            check(helper, pot.count() == 0, "its rice gone into the dish");
            check(helper, pot.comparatorReading() == 15, "a dish waiting, no bowls in: 15, " + pot.comparatorReading());
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(grill);
            check(helper, level.getBlockState(at).getAnalogOutputSignal(level, at, Direction.SOUTH) == 15,
                    "and the block gives it a comparator");
        }).thenExecute(() -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }).thenSucceed();
    }

    /** A dispenser facing a wall keeps a Mob Bottle's mob in it: let out into the stone, it suffocated. */
    private static void bottleIntoAWall(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.setBlock(pos.east(), Blocks.STONE);
        helper.getBlockEntity(pos, DispenserBlockEntity.class).setItem(0, MobBottleItem.filled(EntityTypes.COW));
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            var cows = helper.getLevel().getEntities(EntityTypes.COW, new AABB(helper.absolutePos(pos)).inflate(3), e -> true);
            check(helper, cows.isEmpty(), "no cow let out into the stone: " + cows);
            ItemStack left = helper.getBlockEntity(pos, DispenserBlockEntity.class).getItem(0);
            check(helper, MobBottleItem.captured(left) == EntityTypes.COW, "the cow stays in its bottle: " + left);
            helper.succeed();
        });
    }

    /** What a sorter keeps out does not go back up into a crafter over it, whose grid is its recipe. */
    private static void keptOutNotIntoCrafter(GameTestHelper helper) {
        BlockPos sorterPos = new BlockPos(1, 2, 1);
        helper.setBlock(sorterPos.below(), Blocks.STONE);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        helper.setBlock(sorterPos.above(), Blocks.CRAFTER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ORIENTATION,
                net.minecraft.core.FrontAndTop.DOWN_NORTH));
        var sorter = helper.getBlockEntity(sorterPos, SorterBlockEntity.class);
        sorter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 3));
        sorter.deny().setItem(0, new ItemStack(Items.WHEAT_SEEDS));
        helper.runAfterDelay(40, () -> {
            var crafter = helper.getBlockEntity(sorterPos.above(), CrafterBlockEntity.class);
            int up = 0;
            for (int slot = 0; slot < crafter.getContainerSize(); slot++) {
                up += crafter.getItem(slot).is(Items.WHEAT_SEEDS) ? crafter.getItem(slot).getCount() : 0;
            }
            check(helper, up == 0, "no seeds put into the crafter's grid: " + up);
            check(helper, sorter.getItem(0).is(Items.WHEAT_SEEDS), "they wait in the sorter");
            helper.succeed();
        });
    }

    /**
     * A line of sorters, each feeding the next: the first keeps out what the last does, and takes the rest. Each sorter
     * down the line is asked once - it was asked once for each of its five slots, five to the power of the sorters after.
     */
    private static void sorterLine(GameTestHelper helper) {
        var state = ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH);
        Direction along = SorterBlock.otherSide(state);
        BlockPos first = new BlockPos(0, 2, 1); // along is east: five sorters and a chest across the arena
        BlockPos pos = first;
        SorterBlockEntity last = null;
        for (int i = 0; i < 5; i++) {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, state);
            last = helper.getBlockEntity(pos, SorterBlockEntity.class);
            pos = pos.relative(along);
        }
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, Blocks.CHEST);
        last.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        var head = helper.getBlockEntity(first, SorterBlockEntity.class);
        long start = System.nanoTime();
        boolean cobble = head.canPlaceItemThroughFace(0, new ItemStack(Items.COBBLESTONE), Direction.UP);
        boolean dirt = head.canPlaceItemThroughFace(0, new ItemStack(Items.DIRT), Direction.UP);
        long took = (System.nanoTime() - start) / 1_000_000;
        check(helper, !cobble, "the first keeps out the cobblestone the last keeps out");
        check(helper, dirt, "and takes the dirt");
        check(helper, took < 200, "asked at once, not five times a sorter: " + took + " ms");
        helper.succeed();
    }

    /** A Hub Stone fed by dust reads, to a reader beside it, its own power - the dust's less one - not the dust's. */
    private static void hubStoneOnDust(GameTestHelper helper) {
        BlockPos power = new BlockPos(1, 2, 1);
        BlockPos dust = power.east();
        BlockPos stone = dust.east();
        for (BlockPos at : new BlockPos[] {power, dust, stone, stone.east()}) {
            helper.setBlock(at.below(), Blocks.STONE);
        }
        helper.setBlock(stone, ModBlocks.HUB_STONE.get());
        helper.setBlock(dust, Blocks.REDSTONE_WIRE);
        helper.setBlock(power, Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(4, () -> {
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(stone);
            int own = level.getBlockState(at).getValue(HubStoneBlock.POWER);
            int read = level.getSignal(at, Direction.WEST);
            check(helper, own == 14, "the stone takes the dust's 15 less one: " + own);
            check(helper, read == own, "a reader beside it reads the stone's own " + own + ", not the dust's: " + read);
            helper.succeed();
        });
    }

    /** An Item Warp Receiver over a chest minecart fills it, as a hopper would; it threw the items onto the rail. */
    private static void warpIntoCart(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, 2, 3);
        helper.setBlock(rail.below(), Blocks.STONE);
        helper.setBlock(rail, Blocks.RAIL);
        MinecartChest cart = helper.spawn(EntityTypes.CHEST_MINECART, rail);
        helper.setBlock(rail.above(), ModBlocks.ITEM_WARP_RECEIVER.get());
        BlockPos sender = new BlockPos(3, 8, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        TransportTests.dropOnto(helper, sender, new ItemStack(Items.BONE, 3));
        helper.succeedWhen(() -> {
            check(helper, TransportTests.countIn(cart, Items.BONE) == 3, "the three bones go into the cart: " + TransportTests.countIn(cart, Items.BONE));
            check(helper, helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(rail)).inflate(2), e -> true).isEmpty(),
                    "none on the rail");
        });
    }

    /**
     * Nothing a machine puts out goes onto a rail with no cart on it: a Sifter over one, an Oil Press facing one and a
     * Sorter's outlet keep it - not on their top or beside instead - and fill the next cart (the user, 2026-11-26:
     * 「レール側だけ制御しとけばいいよね」). They dropped it on the rail, and a cart line needed a detector rail to stop the
     * machine, which left a tail on the rail after each cart.
     */
    private static void waitAtRail(GameTestHelper helper) {
        BlockPos sifterPos = new BlockPos(1, 3, 1);
        helper.setBlock(sifterPos.below(2), Blocks.STONE);
        helper.setBlock(sifterPos.below(), Blocks.RAIL);
        helper.setBlock(sifterPos, ModBlocks.SIFTER.get());
        var sifter = helper.getBlockEntity(sifterPos, SifterBlockEntity.class);
        sifter.setItem(1, new ItemStack(Items.FLINT, 5));
        BlockPos pressPos = new BlockPos(4, 2, 5);
        BlockPos front = pressPos.north();
        helper.setBlock(pressPos.below(), Blocks.STONE);
        helper.setBlock(front.below(), Blocks.STONE);
        helper.setBlock(front, Blocks.RAIL);
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.NORTH));
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2));
        BlockPos sorterPos = new BlockPos(5, 2, 1);
        helper.setBlock(sorterPos.below(), Blocks.STONE);
        helper.setBlock(sorterPos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        BlockPos outlet = sorterPos.relative(SorterBlock.otherSide(helper.getBlockState(sorterPos)));
        helper.setBlock(outlet.below(), Blocks.STONE);
        helper.setBlock(outlet, Blocks.RAIL);
        var sorter = helper.getBlockEntity(sorterPos, SorterBlockEntity.class);
        sorter.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        MinecartChest[] carts = new MinecartChest[3];
        helper.startSequence().thenExecuteAfter(60, () -> {
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing goes out with no cart on the rail: "
                    + helper.getEntities(EntityTypes.ITEM));
            check(helper, sifter.getItem(1).getCount() == 5, "the sifter keeps its flint: " + sifter.getItem(1));
            check(helper, press.getItem(OilPressBlockEntity.CAKE).getCount() == 2, "the press keeps its cakes: "
                    + press.getItem(OilPressBlockEntity.CAKE));
            check(helper, TransportTests.countIn(sorter, Items.COBBLESTONE) == 4, "the sorter keeps its cobblestone: "
                    + TransportTests.countIn(sorter, Items.COBBLESTONE));
            carts[0] = helper.spawn(EntityTypes.CHEST_MINECART, sifterPos.below());
            carts[1] = helper.spawn(EntityTypes.CHEST_MINECART, front);
            carts[2] = helper.spawn(EntityTypes.CHEST_MINECART, outlet);
        }).thenExecuteAfter(100, () -> {
            check(helper, TransportTests.countIn(carts[0], Items.FLINT) == 5, "the flint goes into the cart under the sifter: "
                    + TransportTests.countIn(carts[0], Items.FLINT));
            check(helper, TransportTests.countIn(carts[1], ModOil.OIL_CAKE.get()) == 2, "the cakes into the cart in front of the press: "
                    + TransportTests.countIn(carts[1], ModOil.OIL_CAKE.get()));
            check(helper, TransportTests.countIn(carts[2], Items.COBBLESTONE) == 4, "the cobblestone into the cart at the outlet: "
                    + TransportTests.countIn(carts[2], Items.COBBLESTONE));
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "and none on the ground: " + helper.getEntities(EntityTypes.ITEM));
        }).thenSucceed();
    }

    /**
     * As at a rail beside a machine, so at one a fall below it: a Sifter two blocks over a rail and an Oil Press on a
     * ledge over one keep what they have made, and fill the cart that comes (nookTechnology's find for its furnace ports).
     */
    private static void waitAtRailBelow(GameTestHelper helper) {
        BlockPos sifterPos = new BlockPos(1, 4, 1);
        BlockPos rail = new BlockPos(1, 2, 1);
        helper.setBlock(rail.below(), Blocks.STONE);
        helper.setBlock(rail, Blocks.RAIL);
        helper.setBlock(sifterPos, ModBlocks.SIFTER.get());
        var sifter = helper.getBlockEntity(sifterPos, SifterBlockEntity.class);
        sifter.setItem(1, new ItemStack(Items.FLINT, 5));
        BlockPos pressPos = new BlockPos(4, 3, 5);
        BlockPos railFront = new BlockPos(4, 2, 4);
        helper.setBlock(pressPos.below(), Blocks.STONE);
        helper.setBlock(railFront.below(), Blocks.STONE);
        helper.setBlock(railFront, Blocks.RAIL);
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.NORTH));
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2));
        MinecartChest[] carts = new MinecartChest[2];
        helper.startSequence().thenExecuteAfter(60, () -> {
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing falls onto a rail with no cart: "
                    + helper.getEntities(EntityTypes.ITEM));
            check(helper, sifter.getItem(1).getCount() == 5 && press.getItem(OilPressBlockEntity.CAKE).getCount() == 2,
                    "both keep what they have made");
            carts[0] = helper.spawn(EntityTypes.CHEST_MINECART, rail);
            carts[1] = helper.spawn(EntityTypes.CHEST_MINECART, railFront);
        }).thenExecuteAfter(100, () -> {
            check(helper, TransportTests.countIn(carts[0], Items.FLINT) == 5, "the flint into the cart two blocks under the sifter: "
                    + TransportTests.countIn(carts[0], Items.FLINT));
            check(helper, TransportTests.countIn(carts[1], ModOil.OIL_CAKE.get()) == 2, "the cakes into the cart under the ledge: "
                    + TransportTests.countIn(carts[1], ModOil.OIL_CAKE.get()));
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "and none on the ground");
        }).thenSucceed();
    }

    /** An Item Warp Receiver over a rail with no cart keeps what it is sent for the next cart; it dropped it onto the rail. */
    private static void warpWaitsAtRail(GameTestHelper helper) {
        BlockPos rail = new BlockPos(3, 2, 3);
        helper.setBlock(rail.below(), Blocks.STONE);
        helper.setBlock(rail, Blocks.RAIL);
        helper.setBlock(rail.above(), ModBlocks.ITEM_WARP_RECEIVER.get());
        BlockPos sender = new BlockPos(3, 8, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        TransportTests.dropOnto(helper, sender, new ItemStack(Items.BONE, 3));
        MinecartChest[] cart = new MinecartChest[1];
        helper.startSequence().thenExecuteAfter(80, () -> {
            check(helper, helper.getEntities(EntityTypes.ITEM).stream().noneMatch(item -> item.getY() < helper.absolutePos(sender).getY()),
                    "nothing dropped at the receiver: " + helper.getEntities(EntityTypes.ITEM));
            cart[0] = helper.spawn(EntityTypes.CHEST_MINECART, rail);
        }).thenExecuteAfter(100, () -> {
            check(helper, TransportTests.countIn(cart[0], Items.BONE) == 3, "the three bones go into the cart once it comes: "
                    + TransportTests.countIn(cart[0], Items.BONE));
            check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "none on the rail");
        }).thenSucceed();
    }

    /**
     * A Motor stopped by redstone holds what the machine it turns has made, as a machine stopped by redstone does (the
     * user, 2026-11-26: 「おすすめで作っていいよ」): an Oil Press's cakes and a Hand Quern's flour wait in them, and come out
     * once the motor may run. A press with no motor hands its out all the while.
     */
    private static void motorHolds(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(2, 2, 2);
        BlockPos motorPos = pressPos.west();
        BlockPos freePos = new BlockPos(5, 2, 2);
        BlockPos quernPos = new BlockPos(3, 2, 5);
        for (BlockPos at : new BlockPos[] {pressPos, motorPos, freePos, quernPos, pressPos.north(), freePos.north(), quernPos.north()}) {
            helper.setBlock(at.below(), Blocks.STONE);
        }
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.NORTH));
        helper.setBlock(freePos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.NORTH));
        helper.setBlock(motorPos, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.EAST));
        helper.setBlock(motorPos.above(), Blocks.REDSTONE_BLOCK);
        helper.setBlock(quernPos, ModKitchen.HAND_QUERN.get());
        helper.setBlock(quernPos.above(), ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.DOWN));
        helper.setBlock(quernPos.above(2), Blocks.REDSTONE_BLOCK);
        for (BlockPos at : new BlockPos[] {motorPos, quernPos.above()}) {
            var motor = helper.getBlockEntity(at, MotorBlockEntity.class);
            motor.energy().set((int) motor.energy().getCapacityAsLong());
        }
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        var free = helper.getBlockEntity(freePos, OilPressBlockEntity.class);
        var quern = helper.getBlockEntity(quernPos, HandMachineBlockEntity.class);
        press.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2));
        free.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2));
        quern.setItem(HandMachineBlockEntity.OUTPUT, new ItemStack(Items.SUGAR, 3));
        // before either motor has ticked (a chunk just in, a press ticking before its motor): held all the same
        check(helper, MotorBlockEntity.holdsHandOut(press) && MotorBlockEntity.holdsHandOut(quern),
                "held from the first tick, the motors looked at as they stand");
        check(helper, !MotorBlockEntity.holdsHandOut(free), "a press with no motor is not held");
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, press.getItem(OilPressBlockEntity.CAKE).getCount() == 2, "its motor stopped, the press keeps its cakes: "
                    + press.getItem(OilPressBlockEntity.CAKE));
            check(helper, quern.getItem(HandMachineBlockEntity.OUTPUT).getCount() == 3, "and the quern what it has ground: "
                    + quern.getItem(HandMachineBlockEntity.OUTPUT));
            check(helper, free.getItem(OilPressBlockEntity.CAKE).isEmpty() && lying(helper, ModOil.OIL_CAKE.get()) == 2,
                    "a press with no motor hands its cakes out: " + lying(helper, ModOil.OIL_CAKE.get()));
            check(helper, lying(helper, Items.SUGAR) == 0, "nothing out of the quern");
            helper.setBlock(motorPos.above(), Blocks.AIR);
            helper.setBlock(quernPos.above(2), Blocks.AIR);
        }).thenExecuteAfter(40, () -> {
            check(helper, press.getItem(OilPressBlockEntity.CAKE).isEmpty() && lying(helper, ModOil.OIL_CAKE.get()) == 4,
                    "the motor running again, the press's cakes come out: " + press.getItem(OilPressBlockEntity.CAKE));
            check(helper, quern.getItem(HandMachineBlockEntity.OUTPUT).isEmpty() && lying(helper, Items.SUGAR) == 3,
                    "and the quern's: " + quern.getItem(HandMachineBlockEntity.OUTPUT));
        }).thenSucceed();
    }

    /**
     * A motor stopped by redstone holds a press it turns through belted Drive Wheels, from the first tick (before it has
     * ticked) on, and lets it go with the signal.
     */
    private static void motorHoldsBelted(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(1, 2, 3);
        BlockPos motorPos = new BlockPos(4, 2, 1);
        for (BlockPos at : new BlockPos[] {pressPos, pressPos.south(), new BlockPos(1, 2, 2), new BlockPos(4, 2, 2), motorPos}) {
            helper.setBlock(at.below(), Blocks.STONE);
        }
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(1, 2, 2), ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.SOUTH));
        helper.setBlock(new BlockPos(4, 2, 2), ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.SOUTH));
        helper.setBlock(motorPos, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.SOUTH));
        helper.setBlock(motorPos.above(), Blocks.REDSTONE_BLOCK);
        var motor = helper.getBlockEntity(motorPos, MotorBlockEntity.class);
        motor.energy().set((int) motor.energy().getCapacityAsLong());
        check(helper, Belts.tie(helper.getLevel(), helper.getBlockEntity(new BlockPos(1, 2, 2), DriveWheelBlockEntity.class),
                helper.getBlockEntity(new BlockPos(4, 2, 2), DriveWheelBlockEntity.class), BeltKind.ROPE, null,
                new ItemStack(ModItems.ROPE.get())) == null, "the wheels are belted");
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2));
        check(helper, MotorBlockEntity.holdsHandOut(press), "held before the motor has ticked, through the belt");
        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, press.getItem(OilPressBlockEntity.CAKE).getCount() == 2 && lying(helper, ModOil.OIL_CAKE.get()) == 0,
                    "its belted motor stopped, the press keeps its cakes: " + press.getItem(OilPressBlockEntity.CAKE));
            helper.setBlock(motorPos.above(), Blocks.AIR);
        }).thenExecuteAfter(40, () -> {
            check(helper, press.getItem(OilPressBlockEntity.CAKE).isEmpty() && lying(helper, ModOil.OIL_CAKE.get()) == 2,
                    "the motor running again, they come out: " + press.getItem(OilPressBlockEntity.CAKE));
        }).thenSucceed();
    }

    /**
     * Two motors on one belt, one stopped by redstone and one free to run: a press there with only cakes left (no work,
     * so the running motor does not turn it) hands them out - the running one stands against it (r24 GM-01: it noted
     * only the machines it turned, the stopped one all, and the cakes stayed in for good).
     */
    private static void motorRunningBesideStopped(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(1, 2, 3);
        BlockPos stopped = new BlockPos(4, 2, 1);
        BlockPos running = new BlockPos(1, 2, 1);
        for (BlockPos at : new BlockPos[] {pressPos, pressPos.south(), new BlockPos(1, 2, 2), new BlockPos(4, 2, 2), stopped, running}) {
            helper.setBlock(at.below(), Blocks.STONE);
        }
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.SOUTH));
        for (BlockPos wheel : new BlockPos[] {new BlockPos(1, 2, 2), new BlockPos(4, 2, 2)}) {
            helper.setBlock(wheel, ModOil.DRIVE_WHEEL.get().defaultBlockState().setValue(DriveWheelBlock.FACING, Direction.SOUTH));
        }
        for (BlockPos at : new BlockPos[] {stopped, running}) {
            helper.setBlock(at, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.SOUTH));
            var motor = helper.getBlockEntity(at, MotorBlockEntity.class);
            motor.energy().set((int) motor.energy().getCapacityAsLong());
        }
        helper.setBlock(stopped.above(), Blocks.REDSTONE_BLOCK);
        check(helper, Belts.tie(helper.getLevel(), helper.getBlockEntity(new BlockPos(1, 2, 2), DriveWheelBlockEntity.class),
                helper.getBlockEntity(new BlockPos(4, 2, 2), DriveWheelBlockEntity.class), BeltKind.ROPE, null,
                new ItemStack(ModItems.ROPE.get())) == null, "the wheels are belted");
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        helper.startSequence().thenExecuteAfter(10, () -> press.setItem(OilPressBlockEntity.CAKE, new ItemStack(ModOil.OIL_CAKE.get(), 2)))
                .thenExecuteAfter(60, () -> check(helper, press.getItem(OilPressBlockEntity.CAKE).isEmpty()
                        && lying(helper, ModOil.OIL_CAKE.get()) == 2, "one motor free to run, the cakes come out: "
                        + press.getItem(OilPressBlockEntity.CAKE)))
                .thenSucceed();
    }

    /**
     * A motor running a press is stopped by redstone: the press holds from that very tick, its motor read as it stands,
     * not as it was when it last ticked (r24 CX-02: a cake went out the tick the signal came).
     */
    private static void motorHoldsAtOnce(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(2, 2, 2);
        BlockPos motorPos = pressPos.west();
        for (BlockPos at : new BlockPos[] {pressPos, motorPos, pressPos.north()}) {
            helper.setBlock(at.below(), Blocks.STONE);
        }
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get().defaultBlockState().setValue(OilPressBlock.FACING, Direction.NORTH));
        helper.setBlock(motorPos, ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.EAST));
        var motor = helper.getBlockEntity(motorPos, MotorBlockEntity.class);
        motor.energy().set((int) motor.energy().getCapacityAsLong());
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        helper.startSequence().thenExecuteAfter(20, () -> {
            check(helper, !MotorBlockEntity.holdsHandOut(press), "running, the motor holds nothing");
            helper.setBlock(motorPos.above(), Blocks.REDSTONE_BLOCK);
            check(helper, MotorBlockEntity.holdsHandOut(press), "stopped, it holds at once, before it has ticked again");
        }).thenSucceed();
    }

    /**
     * A bottled small slime is let out of a dispenser facing sideways, into the open block in front: its type is sized for
     * spawning (four times a small slime's width), and that box met the dispenser itself (r24 CX-03).
     */
    private static void smallSlimeSideways(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos.below().east(), Blocks.STONE);
        helper.setBlock(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        var slime = helper.spawn(EntityTypes.SLIME, new BlockPos(4, 2, 4));
        slime.setSize(1, true);
        ItemStack bottle = MobBottleItem.filled(slime);
        slime.discard();
        helper.getBlockEntity(pos, DispenserBlockEntity.class).setItem(0, bottle);
        helper.setBlock(pos.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(20, () -> {
            var slimes = helper.getLevel().getEntities(EntityTypes.SLIME, new AABB(helper.absolutePos(pos.east())).inflate(1), e -> true);
            check(helper, slimes.size() == 1, "the small slime is let out in front: " + slimes);
            ItemStack left = helper.getBlockEntity(pos, DispenserBlockEntity.class).getItem(0);
            check(helper, MobBottleItem.captured(left) == null, "and its bottle is empty: " + left);
            helper.succeed();
        });
    }

    /**
     * A retort entry asking for any ingot and an iron ingot is complete with iron and gold in the slots, whichever slot
     * holds which: the iron was taken for "any ingot" first and the iron then found short (r24 CX-05).
     */
    private static void retortTagAndItem(GameTestHelper helper) {
        var items = helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM);
        var anyIngot = items.getOrThrow(net.neoforged.neoforge.common.Tags.Items.INGOTS);
        var recipe = new dev.nookworks.mercury.MercuryRecipes.Retorting(0, Items.STONE.builtInRegistryHolder(), 1,
                java.util.Optional.empty(), java.util.List.of(anyIngot, net.minecraft.core.HolderSet.direct(Items.IRON_INGOT.builtInRegistryHolder())),
                0.2F, java.util.Optional.empty());
        var key = Items.REDSTONE.builtInRegistryHolder();
        for (var held : java.util.List.of(
                java.util.List.of(new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT)),
                java.util.List.of(new ItemStack(Items.REDSTONE), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.IRON_INGOT)))) {
            var charge = dev.nookworks.mercury.RetortBlockEntity.charge(recipe, key, held);
            check(helper, charge.complete(), "complete with " + held + ", short of " + charge.missing());
        }
        var short_ = dev.nookworks.mercury.RetortBlockEntity.charge(recipe, key,
                java.util.List.of(new ItemStack(Items.REDSTONE), new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY));
        check(helper, short_.missing().size() == 1, "one iron alone serves one of the two: " + short_.missing());
        helper.succeed();
    }

    /** How many of {@code item} lie on the ground in the test's area. */
    private static int lying(GameTestHelper helper, Item item) {
        return helper.getEntities(EntityTypes.ITEM).stream().filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
