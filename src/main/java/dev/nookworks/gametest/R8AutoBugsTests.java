package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.block.PlanterBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * Bugs the r8 automation run found (AI_review/r8_automation): a hopper under a Planter drained its seeds; a Stirring
 * Tank short of oil said it was idle, as if nothing were in it; Oil Cake and Rice Bran were thrown out of a dispenser
 * rather than used as bone meal is.
 */
final class R8AutoBugsTests {
    private R8AutoBugsTests() {}

    static void register() {
        NookworksGameTests.test("planter_takes_seeds_in_and_gives_none_out", R8AutoBugsTests::planterTakesInOnly);
        NookworksGameTests.test("stirring_tank_short_of_oil_says_so", R8AutoBugsTests::stirringTankShortOfOil);
        NookworksGameTests.test("dispenser_uses_oil_cake_and_rice_bran_as_bone_meal", R8AutoBugsTests::dispenserFertilizes);
    }

    /**
     * A Planter is fed from any side and gives nothing out, as a machine's input slots: a hopper under it takes none of
     * its seeds (it took 8 of 10 in 3 s), one over it still fills it, and no pipe draws seeds out through any face, while
     * one at its side puts them in (plants only). Its stock still comes out by hand, in its screen.
     */
    private static void planterTakesInOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        helper.setBlock(pos.below(), Blocks.HOPPER); // draws from the planter above it
        helper.setBlock(pos.above(), Blocks.HOPPER); // feeds the planter below it
        var planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 10)); // no power: it plants none meanwhile
        helper.getBlockEntity(pos.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.WHEAT_SEEDS, 4));

        BlockPos at = helper.absolutePos(pos);
        for (Direction side : Direction.values()) {
            var handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, side);
            check(helper, handler != null && Move.extract(handler, ItemResource.of(Items.WHEAT_SEEDS), 1) == 0,
                    "no pipe draws seeds out through its " + side + " face");
        }
        var pipe = helper.getLevel().getCapability(Capabilities.Item.BLOCK, at, Direction.NORTH);
        check(helper, Move.insert(pipe, ItemResource.of(Items.BEETROOT_SEEDS), 2) == 2, "a pipe at its side puts seeds in");
        check(helper, Move.insert(pipe, ItemResource.of(Items.COBBLESTONE), 1) == 0, "and only plants");

        helper.runAfterDelay(60, () -> {
            var below = helper.getBlockEntity(pos.below(), HopperBlockEntity.class);
            check(helper, below.isEmpty(), "the hopper under it took nothing: " + below.getItem(0));
            check(helper, helper.getBlockEntity(pos.above(), HopperBlockEntity.class).isEmpty(), "the hopper over it emptied into it");
            check(helper, planter.countItem(Items.WHEAT_SEEDS) == 14 && planter.countItem(Items.BEETROOT_SEEDS) == 2,
                    "it holds all it was given: " + planter.countItem(Items.WHEAT_SEEDS) + " wheat seeds");

            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            var menu = new MachineMenu(1, player.getInventory(), MachineLayout.PLANTER, planter.slots(), MachineMenu.data(planter), planter);
            int slot = planter.getItem(0).is(Items.WHEAT_SEEDS) ? 0 : 1;
            menu.quickMoveStack(player, slot);
            check(helper, player.getInventory().countItem(Items.WHEAT_SEEDS) == 14 && planter.countItem(Items.WHEAT_SEEDS) == 0,
                    "a player takes them back out in its screen");
            helper.succeed();
        });
    }

    /**
     * Making biodiesel with its methanol and caustic soda in, a Stirring Tank with less oil than a batch takes says the
     * oil is short, not that it is idle; topped up to a batch it runs. With nothing in it, it is idle as before.
     */
    private static void stirringTankShortOfOil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(pos, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        Runnable tick = () -> StirringTankBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), tank);

        tick.run();
        check(helper, tank.guiStatus() == MachineStatus.IDLE, "empty, it is idle: " + tank.guiStatus());

        tank.setItem(0, new ItemStack(ModOil.METHANOL.get()));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        int some = StirringTankBlockEntity.OIL_PER_BATCH - 50;
        check(helper, Move.insert(tank.fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), some) == some, "oil is piped in");
        tick.run();
        check(helper, tank.guiStatus() == MachineStatus.LOW_OIL, "200 mB of oil: it says the oil is short, " + tank.guiStatus());
        check(helper, MachineStatus.LOW_OIL.tone() == MachineStatus.Tone.WARN, "in the screen's warning colour");

        Move.insert(tank.fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), 50);
        tick.run();
        check(helper, tank.guiStatus() == MachineStatus.RUNNING, "a batch's worth: it stirs, " + tank.guiStatus());
        helper.succeed();
    }

    /**
     * Oil Cake and Rice Bran in a dispenser are used on the crop in front of it as bone meal is - vanilla's own
     * behaviour for bone meal - one at a time, the wheat grown, nothing thrown out.
     */
    private static void dispenserFertilizes(GameTestHelper helper) {
        List<Item> fertilizers = List.of(ModOil.OIL_CAKE.get(), ModCrops.RICE_BRAN.get());
        for (Item item : fertilizers) {
            check(helper, DispenserBlock.DISPENSER_REGISTRY.get(item) == DispenserBlock.DISPENSER_REGISTRY.get(Items.BONE_MEAL),
                    item + " has bone meal's dispenser behaviour");
        }
        for (int i = 0; i < fertilizers.size(); i++) {
            BlockPos dispenser = new BlockPos(1, 2, 1 + 3 * i);
            BlockPos crop = dispenser.east();
            helper.setBlock(crop.below(), Blocks.FARMLAND);
            helper.setBlock(crop, Blocks.WHEAT.defaultBlockState()); // age 0
            helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
            helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(fertilizers.get(i), 4));
            helper.setBlock(dispenser.above(), Blocks.REDSTONE_BLOCK);
        }
        helper.succeedWhen(() -> {
            for (int i = 0; i < fertilizers.size(); i++) {
                Item item = fertilizers.get(i);
                BlockPos dispenser = new BlockPos(1, 2, 1 + 3 * i);
                var wheat = helper.getBlockState(dispenser.east());
                check(helper, wheat.is(Blocks.WHEAT) && wheat.getValue(CropBlock.AGE) > 0, item + " grew the wheat");
                check(helper, helper.getBlockEntity(dispenser, DispenserBlockEntity.class).countItem(item) == 3, "one " + item + " used");
            }
            check(helper, helper.getEntities(EntityTypes.ITEM).stream().map(ItemEntity::getItem)
                    .noneMatch(stack -> fertilizers.contains(stack.getItem())), "nothing thrown out");
        });
    }
}
