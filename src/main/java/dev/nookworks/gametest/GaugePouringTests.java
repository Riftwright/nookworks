package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.GaugePouring;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;

/**
 * A bucket or bottle on the cursor clicked on a machine screen's tank gauge (1.3; the user, 2026-10-26): a full one
 * pours into that tank and comes back empty, an empty one draws out of it - only what the gauge holds, into the tank
 * the gauge is - and a bucket of mercury goes into a mercury tank by its gauge.
 */
final class GaugePouringTests {
    private GaugePouringTests() {}

    static void register() {
        test("a_bucket_or_bottle_clicked_on_a_gauge_pours_into_that_tank", GaugePouringTests::pours);
        test("a_bucket_of_mercury_clicked_on_its_gauge_fills_the_mercury_tank", GaugePouringTests::mercury);
    }

    private static void pours(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        StirringTankBlockEntity tank = helper.getBlockEntity(at, StirringTankBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = player.inventoryMenu;
        menu.setCarried(new ItemStack(Items.WATER_BUCKET));
        check(helper, !GaugePouring.pour(player, menu, tank.gaugeTank(2), GuiTank.Kind.VEGETABLE_OIL), "water does not go in by the oil's gauge");
        check(helper, GaugePouring.pour(player, menu, tank.gaugeTank(0), GuiTank.Kind.WATER) && tank.waterStored() == 1000,
                "a water bucket on the water gauge pours in: " + tank.waterStored());
        check(helper, menu.getCarried().is(Items.BUCKET), "and the bucket comes back empty: " + menu.getCarried());
        check(helper, !GaugePouring.pour(player, menu, tank.gaugeTank(1), GuiTank.Kind.BIODIESEL), "an empty bucket draws nothing from an empty tank");
        menu.setCarried(new ItemStack(Items.GLASS_BOTTLE, 2));
        check(helper, GaugePouring.pour(player, menu, tank.gaugeTank(0), GuiTank.Kind.WATER) && tank.waterStored() == 750,
                "a glass bottle draws a bottle's worth out of the water tank, which no pipe may draw from: " + tank.waterStored());
        check(helper, menu.getCarried().is(Items.GLASS_BOTTLE) && menu.getCarried().getCount() == 1
                && player.getInventory().countItem(Items.POTION) == 1, "one bottle of the two is filled, into the inventory");
        menu.setCarried(PotionContents.createItemStack(Items.POTION, Potions.WATER));
        check(helper, GaugePouring.pour(player, menu, tank.gaugeTank(0), GuiTank.Kind.WATER) && tank.waterStored() == 1000
                && menu.getCarried().is(Items.GLASS_BOTTLE), "a water bottle pours back in: " + tank.waterStored());
        helper.succeed();
    }

    private static void mercury(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, ModBlocks.ELECTROLYTIC_CELL.get());
        ElectrolyticCellBlockEntity cell = helper.getBlockEntity(at, ElectrolyticCellBlockEntity.class);
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        var pos = helper.absolutePos(at.south());
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var menu = new MachineMenu(1, player.getInventory(), cell.layout(), cell.slots(), MachineMenu.data(cell), cell);
        menu.setCarried(new ItemStack(ModItems.MERCURY_BUCKET.get()));
        int mercuryGauge = cell.guiTanks().size() - 1;
        int before = cell.mercuryStore().amount();
        check(helper, GaugePouring.clicked(player, menu, mercuryGauge) && cell.mercuryStore().amount() == before + 1000,
                "a bucket of mercury on the mercury gauge goes into the cell: " + cell.mercuryStore().amount());
        check(helper, menu.getCarried().is(Items.BUCKET), "the bucket comes back empty");
        check(helper, GaugePouring.clicked(player, menu, mercuryGauge) && menu.getCarried().is(ModItems.MERCURY_BUCKET.get())
                && cell.mercuryStore().amount() == before, "and an empty one draws it out again");
        menu.setCarried(new ItemStack(ModItems.MERCURY_BUCKET.get()));
        check(helper, !GaugePouring.clicked(player, menu, 0), "mercury does not go in by the water gauge");
        // a spectator with the screen open (one made so after opening it, r19)
        ServerPlayer watching = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
        watching.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        var watched = new MachineMenu(2, watching.getInventory(), cell.layout(), cell.slots(), MachineMenu.data(cell), cell);
        watched.setCarried(new ItemStack(ModItems.MERCURY_BUCKET.get()));
        check(helper, !GaugePouring.clicked(watching, watched, mercuryGauge) && cell.mercuryStore().amount() == before
                && watched.getCarried().is(ModItems.MERCURY_BUCKET.get()), "a spectator pours nothing");
        helper.succeed();
    }
}
