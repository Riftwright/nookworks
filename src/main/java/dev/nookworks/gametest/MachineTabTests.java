package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.menu.ShiftClick;
import dev.nookworks.menu.tab.LiquidTab;
import dev.nookworks.menu.tab.MachineTab;
import dev.nookworks.menu.tab.MenuTabs;
import dev.nookworks.menu.tab.TabSlot;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * The machine screens' tabs (the user's design, 2026-11-08/10): the Liquid tab's by-hand slots fill and empty a tank in
 * about a second, take only what the machine takes, draw the liquid chosen, are out of a hopper's reach and give back
 * what is left as the screen closes; a shift-click reaches a tab only while it is open; an add-on's slot registered by
 * the API stands in the Add-on tab alone; and what an old world kept in the fill boxes that are gone is put out below.
 */
final class MachineTabTests {
    private MachineTabTests() {}

    static void register() {
        test("liquid_tab_fills_and_empties_a_tank_over_a_second", MachineTabTests::fillsAndEmpties);
        test("liquid_tab_refuses_a_liquid_the_machine_does_not_take", MachineTabTests::refuses);
        test("liquid_tab_draws_the_liquid_chosen", MachineTabTests::chooses);
        test("liquid_tab_is_out_of_a_hoppers_reach", 100, MachineTabTests::outOfHoppersReach);
        test("liquid_tab_gives_back_what_is_left_on_close", MachineTabTests::givesBack);
        test("machine_tabs_take_shift_clicks_only_while_open", MachineTabTests::shiftClicks);
        test("an_addon_slot_stands_in_the_addon_tab_and_parts_never_go_into_the_machine", MachineTabTests::addonSlot);
        test("machine_tabs_must_be_turned_on_top_down", MachineTabTests::tabOrder);
        test("old_fill_boxes_are_put_out_below_not_lost", 100, MachineTabTests::oldFillBoxes);
        test("retort_output_shows_and_gives_the_liquid_it_holds", MachineTabTests::retortOtherLiquid);
    }

    /**
     * A Retort's output is what its recipe gives off, which a data pack may make other than wood vinegar: its gauge says
     * what it holds, and the Liquid tab bottles that (r22 CXC-006, CXP-004: methanol showed as wood vinegar and stayed in).
     */
    private static void retortOtherLiquid(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        check(helper, retort.guiTanks().get(2).kind() == dev.nookworks.machine.GuiTank.Kind.WOOD_VINEGAR, "empty, it is the wood vinegar gauge");
        Tanks.set(retort.liquidTank(), OilFluids.METHANOL.get(), 500);
        check(helper, retort.guiTanks().get(2).kind() == dev.nookworks.machine.GuiTank.Kind.METHANOL,
                "holding methanol, the methanol gauge: " + retort.guiTanks().get(2).kind());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = menu(player, retort);
        LiquidTab tab = menu.tabs().liquid();
        tab.choose(2);
        tab.slot(LiquidTab.DRAW).set(new ItemStack(Items.GLASS_BOTTLE));
        ticks(menu, player, 20);
        check(helper, tab.slot(LiquidTab.DRAW).getItem().is(ModOil.METHANOL.get()) && retort.liquidStored() == 250,
                "a bottle of it drawn: " + tab.slot(LiquidTab.DRAW).getItem() + ", " + retort.liquidStored() + " mB left");
        menu.removed(player);
        helper.succeed();
    }

    private static <T extends MachineBlockEntity & MachineInfo> MachineMenu menu(ServerPlayer player, T machine) {
        return new MachineMenu(1, player.getInventory(), machine.layout(), machine.slots(), MachineMenu.data(machine), machine);
    }

    private static StirringTankBlockEntity stirringTank(GameTestHelper helper, BlockPos at) {
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        return helper.getBlockEntity(at, StirringTankBlockEntity.class);
    }

    private static void ticks(MachineMenu menu, ServerPlayer player, int n) {
        for (int i = 0; i < n; i++) {
            menu.tabs().tick(player);
        }
    }

    /** A water bucket in the bottom slot: nothing at half a second, the tank filled and an empty bucket at one; and back. */
    private static void fillsAndEmpties(GameTestHelper helper) {
        StirringTankBlockEntity tank = stirringTank(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = menu(player, tank);
        LiquidTab liquid = menu.tabs().liquid();
        // (an add-on may add an Add-on tab and slots after these: nookTechnology's gear on the Stirring Tank)
        check(helper, liquid != null && menu.tabs().has(MachineTab.LIQUID) && menu.tabs().has(MachineTab.SETTINGS),
                "a machine with tanks has Settings and Liquid");
        int afterInventory = tank.layout().slotCount() + ShiftClick.INVENTORY_SLOTS;
        check(helper, liquid.slot(LiquidTab.DRAW).index == afterInventory && liquid.slot(LiquidTab.POUR).index == afterInventory + 1,
                "its two slots come right after the player's inventory: " + liquid.slot(LiquidTab.DRAW).index);
        liquid.slot(LiquidTab.POUR).set(new ItemStack(Items.WATER_BUCKET));
        ticks(menu, player, 10);
        check(helper, tank.waterStored() == 0 && liquid.progress(LiquidTab.POUR) == 10,
                "half a second in, nothing poured yet, the dial half round: " + tank.waterStored() + ", " + liquid.progress(LiquidTab.POUR));
        ticks(menu, player, LiquidTab.TICKS - 10);
        check(helper, tank.waterStored() == 1000 && liquid.slot(LiquidTab.POUR).getItem().is(Items.BUCKET),
                "after a second the water is in and the bucket left empty in its slot: " + tank.waterStored() + ", "
                        + liquid.slot(LiquidTab.POUR).getItem());
        ticks(menu, player, LiquidTab.TICKS);
        check(helper, tank.waterStored() == 1000, "an empty bucket in the bottom slot pours nothing");

        liquid.slot(LiquidTab.DRAW).set(new ItemStack(Items.GLASS_BOTTLE));
        ticks(menu, player, LiquidTab.TICKS - 1);
        check(helper, tank.waterStored() == 1000 && liquid.slot(LiquidTab.DRAW).getItem().is(Items.GLASS_BOTTLE), "not yet filled");
        ticks(menu, player, 1);
        check(helper, tank.waterStored() == 750 && liquid.slot(LiquidTab.DRAW).getItem().is(Items.POTION),
                "a glass bottle in the top slot fills with water, which no pipe may draw from: " + tank.waterStored());
        liquid.slot(LiquidTab.DRAW).set(new ItemStack(Items.BUCKET));
        ticks(menu, player, LiquidTab.TICKS);
        check(helper, tank.waterStored() == 750 && liquid.slot(LiquidTab.DRAW).getItem().is(Items.BUCKET),
                "a bucket waits while under a bucket's worth is in: " + tank.waterStored());
        Tanks.set((FluidStacksResourceHandler) tank.gaugeTank(0), Fluids.WATER, 1250);
        ticks(menu, player, LiquidTab.TICKS);
        check(helper, tank.waterStored() == 250 && liquid.slot(LiquidTab.DRAW).getItem().is(Items.WATER_BUCKET), "and with one in, takes it");
        ticks(menu, player, LiquidTab.TICKS);
        check(helper, liquid.slot(LiquidTab.DRAW).getItem().is(Items.WATER_BUCKET), "a filled bucket in the top slot stays as it is");
        menu.removed(player);
        helper.succeed();
    }

    /** Only what the machine takes in: not lava, not the Furnace's experience it makes, not mercury into a Distiller. */
    private static void refuses(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        StirringTankBlockEntity tank = stirringTank(helper, new BlockPos(1, 2, 1));
        MachineMenu stirring = menu(player, tank);
        var pour = stirring.tabs().liquid().slot(LiquidTab.POUR);
        check(helper, !pour.mayPlace(new ItemStack(Items.LAVA_BUCKET)) && !pour.mayPlace(new ItemStack(Items.BUCKET))
                && !pour.mayPlace(new ItemStack(Items.STONE)), "the Stirring Tank's bottom slot turns down lava, an empty bucket, a stone");
        check(helper, pour.mayPlace(new ItemStack(Items.WATER_BUCKET)) && pour.mayPlace(new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()))
                && pour.mayPlace(new ItemStack(ModItems.MERCURY_BUCKET.get())) && pour.mayPlace(new ItemStack(ModOil.METHANOL.get())),
                "and takes water, oil, mercury and methanol, which it uses");
        check(helper, !pour.mayPlace(new ItemStack(ModOil.BIODIESEL_BUCKET.get())), "not biodiesel, which it makes");
        check(helper, !stirring.tabs().liquid().slot(LiquidTab.DRAW).mayPlace(new ItemStack(Items.WATER_BUCKET)),
                "the top slot takes only an empty bucket or bottle");

        BlockPos furnaceAt = new BlockPos(3, 2, 1);
        helper.setBlock(furnaceAt, ModBlocks.ELECTRIC_FURNACE.get());
        MachineMenu furnace = menu(player, helper.getBlockEntity(furnaceAt, ElectricFurnaceBlockEntity.class));
        check(helper, furnace.tabs().liquid() != null
                && !furnace.tabs().liquid().slot(LiquidTab.POUR).mayPlace(new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get())),
                "the Electric Furnace makes experience: none goes back in");
        check(helper, furnace.clickMenuButton(player, MenuTabs.button(MachineTab.LIQUID)) && furnace.tabs().liquid().slot(LiquidTab.DRAW).isActive()
                && !furnace.tabs().liquid().slot(LiquidTab.POUR).isActive(), "and its Liquid tab shows no bottom slot, only the top one");
        BlockPos distillerAt = new BlockPos(5, 2, 1);
        helper.setBlock(distillerAt, ModBlocks.DISTILLER.get());
        MachineMenu distiller = menu(player, helper.getBlockEntity(distillerAt, DistillerBlockEntity.class));
        check(helper, !distiller.tabs().liquid().slot(LiquidTab.POUR).mayPlace(new ItemStack(ModItems.MERCURY_BUCKET.get()))
                && distiller.tabs().liquid().slot(LiquidTab.POUR).mayPlace(new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get())),
                "the Distiller gives mercury, and takes wood vinegar");
        BlockPos crusherAt = new BlockPos(7, 2, 1);
        helper.setBlock(crusherAt, ModBlocks.CRUSHER.get());
        MachineMenu crusher = menu(player, helper.getBlockEntity(crusherAt, CrusherBlockEntity.class));
        check(helper, crusher.tabs().liquid() == null && !crusher.tabs().has(MachineTab.LIQUID)
                && crusher.tabs().slots(MachineTab.LIQUID).isEmpty(), "a machine with no tank has no Liquid tab, nor its slots");
        check(helper, !MachineMenuTabs.liquid(MachineLayout.BARTER_TERMINAL), "nor the Barter Terminal, whose air is no liquid");
        check(helper, !crusher.clickMenuButton(player, MenuTabs.button(MachineTab.LIQUID)) && crusher.tabs().open() == null,
                "a tab the screen does not have does not open");
        check(helper, !crusher.tabs().clickMenuButton(player, 100) && !crusher.tabs().clickMenuButton(player, 119),
                "ids 100-119 stay the add-on's: the tabs take none of them");
        helper.succeed();
    }

    /** Two liquids in the Stirring Tank: the first drawn by default, the other when chosen; a pick gone dry falls back. */
    private static void chooses(GameTestHelper helper) {
        StirringTankBlockEntity tank = stirringTank(helper, new BlockPos(1, 2, 1));
        Tanks.set((FluidStacksResourceHandler) tank.gaugeTank(0), Fluids.WATER, 1000);
        Tanks.set((FluidStacksResourceHandler) tank.gaugeTank(2), OilFluids.SOURCE.get(), 2000);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = menu(player, tank);
        LiquidTab liquid = menu.tabs().liquid();
        ticks(menu, player, 1);
        check(helper, liquid.drawableCount() == 2 && liquid.drawable(0) && liquid.drawable(2) && liquid.chosen() == 0,
                "water and oil to draw, water first: " + liquid.drawableCount() + ", " + liquid.chosen());
        check(helper, menu.clickMenuButton(player, MenuTabs.BUTTON_LIQUID_CHOICE + 2) && liquid.chosen() == 2, "the oil chosen");
        liquid.slot(LiquidTab.DRAW).set(new ItemStack(Items.BUCKET));
        ticks(menu, player, LiquidTab.TICKS);
        check(helper, liquid.slot(LiquidTab.DRAW).getItem().is(ModOil.VEGETABLE_OIL_BUCKET.get()) && tank.oilStored() == 1000
                && tank.waterStored() == 1000, "an empty bucket drew the oil, the water left: " + liquid.slot(LiquidTab.DRAW).getItem());
        Tanks.set((FluidStacksResourceHandler) tank.gaugeTank(2), OilFluids.SOURCE.get(), 0);
        ticks(menu, player, 1);
        check(helper, liquid.chosen() == 0 && liquid.drawableCount() == 1, "with the oil gone, the water is drawn again: " + liquid.chosen());
        menu.removed(player);
        helper.succeed();
    }

    /**
     * A hopper over a machine never reaches the Liquid tab: the machine's own container is as it was (no slot of the
     * tab's), and a hopper of empty buckets over a Stirring Tank with a menu open leaves the tab's slots empty.
     */
    private static void outOfHoppersReach(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        StirringTankBlockEntity tank = stirringTank(helper, at);
        helper.setBlock(at.above(), Blocks.HOPPER);
        helper.getBlockEntity(at.above(), HopperBlockEntity.class).setItem(0, new ItemStack(Items.BUCKET, 4));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = menu(player, tank);
        check(helper, tank.getContainerSize() == tank.layout().slotCount(), "the machine's own slots are its layout's alone");
        helper.runAfterDelay(60, () -> {
            ticks(menu, player, 1);
            LiquidTab liquid = menu.tabs().liquid();
            check(helper, !liquid.slot(LiquidTab.DRAW).hasItem() && !liquid.slot(LiquidTab.POUR).hasItem(),
                    "no hopper put anything in the Liquid tab");
            menu.removed(player);
            helper.succeed();
        });
    }

    /** What is left in the Liquid tab goes into the inventory as the screen closes; nothing is lost. */
    private static void givesBack(GameTestHelper helper) {
        StirringTankBlockEntity tank = stirringTank(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        MachineMenu menu = menu(player, tank);
        LiquidTab liquid = menu.tabs().liquid();
        liquid.slot(LiquidTab.DRAW).set(new ItemStack(Items.BUCKET));
        liquid.slot(LiquidTab.POUR).set(new ItemStack(ModOil.METHANOL_BUCKET.get()));
        menu.removed(player);
        check(helper, player.getInventory().countItem(Items.BUCKET) == 1 && player.getInventory().countItem(ModOil.METHANOL_BUCKET.get()) == 1,
                "both back in the inventory");
        check(helper, !liquid.slot(LiquidTab.DRAW).hasItem() && !liquid.slot(LiquidTab.POUR).hasItem(), "and the slots empty");
        helper.succeed();
    }

    /**
     * The furnace's shift-click rule with the tabs: with the Liquid tab shut a stack of empty buckets crosses to the hotbar
     * (the Stirring Tank takes none); open, one goes into its top slot and the rest stays put; a water bucket into its
     * bottom slot; shift-clicked out of the tab, they go back to the inventory, hotbar first.
     */
    private static void shiftClicks(GameTestHelper helper) {
        StirringTankBlockEntity tank = stirringTank(helper, new BlockPos(1, 2, 1));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var inventory = player.getInventory();
        MachineMenu menu = menu(player, tank);
        int first = tank.layout().slotCount(); // the menu's slot of the inventory's slot 9
        LiquidTab liquid = menu.tabs().liquid();
        inventory.setItem(9, new ItemStack(Items.BUCKET, 4));
        menu.quickMoveStack(player, first);
        check(helper, !liquid.slot(LiquidTab.DRAW).hasItem() && inventory.getItem(0).is(Items.BUCKET) && inventory.getItem(0).getCount() == 4,
                "the tab shut: the buckets cross to the hotbar: " + inventory.getItem(0));
        check(helper, menu.clickMenuButton(player, MenuTabs.button(MachineTab.LIQUID)) && menu.tabs().isOpen(MachineTab.LIQUID),
                "the Liquid tab opened");
        inventory.setItem(9, inventory.getItem(0).copy());
        inventory.setItem(0, ItemStack.EMPTY);
        menu.clicked(first, 0, net.minecraft.world.inventory.ContainerInput.QUICK_MOVE, player);
        check(helper, liquid.slot(LiquidTab.DRAW).getItem().getCount() == 1 && inventory.getItem(9).getCount() == 3,
                "open: one bucket in, three left where they were: " + liquid.slot(LiquidTab.DRAW).getItem() + ", " + inventory.getItem(9));
        inventory.setItem(10, new ItemStack(Items.WATER_BUCKET));
        menu.quickMoveStack(player, first + 1);
        check(helper, liquid.slot(LiquidTab.POUR).getItem().is(Items.WATER_BUCKET), "a water bucket into the bottom slot");
        menu.quickMoveStack(player, liquid.slot(LiquidTab.POUR).index);
        boolean onHotbar = false;
        for (int i = 0; i < 9; i++) {
            onHotbar |= inventory.getItem(i).is(Items.WATER_BUCKET);
        }
        check(helper, !liquid.slot(LiquidTab.POUR).hasItem() && onHotbar, "out of the tab to the hotbar first");
        check(helper, menu.clickMenuButton(player, MenuTabs.button(null)) && menu.tabs().open() == null, "shut again");
        inventory.setItem(11, new ItemStack(Items.WATER_BUCKET));
        menu.quickMoveStack(player, first + 2);
        check(helper, !liquid.slot(LiquidTab.POUR).hasItem(), "shut, nothing goes into the tab");
        menu.removed(player);
        helper.succeed();
    }

    /**
     * An add-on's slot added through the API: in the Add-on tab, active only while it is open. What it takes is a part,
     * which a shift-click never puts into the machine (five gold gears at a Recycler: one fitted, four scrapped, the
     * user's complaint): shut, it crosses to the hotbar; open, one goes in and the rest stays; full, it stays put. The
     * part here is cobblestone, which the Crusher would crush. (With nookTechnology installed its gear slot comes first.)
     */
    private static void addonSlot(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(at, CrusherBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var inventory = player.getInventory();
        SimpleContainer part = new SimpleContainer(1) {
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        };
        TabSlot[] added = new TabSlot[1];
        // what an add-on's mixin does at the end of the menu's constructor
        MachineMenu menu = new MachineMenu(1, inventory, crusher.layout(), crusher.slots(), MachineMenu.data(crusher), crusher) {
            {
                MenuTabs tabs = this.tabs();
                int[] spot = tabs.nextAddonSpot();
                added[0] = tabs.add(new TabSlot(tabs, MachineTab.ADDON, part, 0, spot[0], spot[1]) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.is(Items.COBBLESTONE);
                    }
                });
                this.addSlot(added[0]);
            }
        };
        MenuTabs tabs = menu.tabs();
        TabSlot slot = added[0];
        List<TabSlot> addon = tabs.slots(MachineTab.ADDON);
        check(helper, tabs.has(MachineTab.ADDON) && addon.get(addon.size() - 1) == slot, "the Add-on tab is there, holding it");
        check(helper, slot.x >= MachineLayout.WIDTH && slot.y >= tabs.contentY(MachineTab.ADDON) && !slot.isActive(),
                "it stands on the tab, right of the panel, and is shut away: " + slot.x + ", " + slot.y);
        check(helper, tabs.isPart(new ItemStack(Items.COBBLESTONE)) && !tabs.isPart(new ItemStack(Items.GRAVEL)), "what it takes is a part");
        int first = crusher.layout().slotCount();
        inventory.setItem(9, new ItemStack(Items.COBBLESTONE, 5));
        menu.quickMoveStack(player, first);
        check(helper, !slot.hasItem() && !menu.slots.get(0).hasItem() && inventory.getItem(0).is(Items.COBBLESTONE)
                && inventory.getItem(0).getCount() == 5, "shut: the part crosses to the hotbar, not into the slot or the machine: "
                        + menu.slots.get(0).getItem() + ", " + inventory.getItem(0));
        check(helper, menu.clickMenuButton(player, MenuTabs.button(MachineTab.ADDON)) && slot.isActive() && tabs.liquid() == null,
                "open, it is active");
        inventory.setItem(10, new ItemStack(Items.COBBLESTONE, 5));
        ItemStack back = menu.quickMoveStack(player, first + 1);
        check(helper, slot.getItem().getCount() == 1 && inventory.getItem(10).getCount() == 4 && back.isEmpty(),
                "open: one fitted, the rest kept, and the shift-click loop told to stop");
        menu.quickMoveStack(player, first + 1);
        check(helper, inventory.getItem(10).getCount() == 4 && !menu.slots.get(0).hasItem(),
                "its slot full, the rest stays put - none into the machine: " + menu.slots.get(0).getItem());
        check(helper, menu.clickMenuButton(player, MenuTabs.button(MachineTab.SETTINGS)) && !slot.isActive(), "another tab open: shut away");
        menu.quickMoveStack(player, slot.index);
        check(helper, !slot.hasItem() && inventory.countItem(Items.COBBLESTONE) == 10, "shift-clicked out, back to the inventory");
        helper.succeed();
    }

    /** A tab turned on above one that already has slots is refused: those slots would stand a tab too high. */
    private static void tabOrder(GameTestHelper helper) {
        MenuTabs tabs = new MenuTabs(MachineLayout.WIDTH);
        tabs.nextAddonSpot();
        boolean refused = false;
        try {
            tabs.enable(MachineTab.LIQUID);
        } catch (IllegalStateException expected) {
            refused = true;
        }
        check(helper, refused && !tabs.has(MachineTab.LIQUID), "the Liquid tab after an Add-on slot is refused");
        MenuTabs right = new MenuTabs(MachineLayout.WIDTH);
        right.enable(MachineTab.SETTINGS);
        right.enable(MachineTab.LIQUID);
        right.nextAddonSpot();
        right.enable(MachineTab.SETTINGS); // already on: nothing moves
        check(helper, right.has(MachineTab.ADDON) && right.tabTop(MachineTab.ADDON) == MenuTabs.TOP + 2 * (MenuTabs.TAB + MenuTabs.GAP),
                "Settings, Liquid, then Add-on slots: in order");
        helper.succeed();
    }

    /**
     * A Retort and an Electric Furnace saved with things in their fill box (slots 5-6 and 2-3, as every fixture of
     * SaveCompat has them): the slots kept load where they were, the box's things are kept meanwhile (and saved), and at
     * the first tick they go out below - into the chest under the Retort, dropped beside the Furnace with none.
     */
    private static void oldFillBoxes(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        BlockPos retortAt = new BlockPos(1, 2, 1);
        helper.setBlock(retortAt.below(), Blocks.CHEST);
        helper.setBlock(retortAt, ModBlocks.RETORT.get());
        RetortBlockEntity retort = helper.getBlockEntity(retortAt, RetortBlockEntity.class);
        CompoundTag old = new CompoundTag();
        ListTag items = new ListTag();
        items.add(slot(0, "minecraft:oak_log", 3));
        items.add(slot(4, "minecraft:charcoal", 2));
        items.add(slot(5, "minecraft:glass_bottle", 8));
        items.add(slot(6, "nookworks:liquid_experience_bottle", 2));
        old.put("Items", items);
        old.putString("Bottles", "EXPERIENCE");
        retort.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, old));
        check(helper, retort.getItem(0).is(Items.OAK_LOG) && retort.getItem(0).getCount() == 3 && retort.getItem(4).is(Items.CHARCOAL),
                "the slots kept load where they were: " + retort.getItem(0) + ", " + retort.getItem(4));
        check(helper, retort.hasLeftovers(), "the fill box's things are kept");
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        retort.saveCustomOnly(saved);
        var copy = new RetortBlockEntity(helper.absolutePos(retortAt), helper.getBlockState(retortAt));
        copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved.buildResult()));
        check(helper, copy.hasLeftovers(), "and saved meanwhile (leftovers)");

        BlockPos furnaceAt = new BlockPos(5, 2, 1);
        helper.setBlock(furnaceAt, ModBlocks.ELECTRIC_FURNACE.get());
        ElectricFurnaceBlockEntity furnace = helper.getBlockEntity(furnaceAt, ElectricFurnaceBlockEntity.class);
        CompoundTag oldFurnace = new CompoundTag();
        ListTag furnaceItems = new ListTag();
        furnaceItems.add(slot(0, "minecraft:cobblestone", 3));
        furnaceItems.add(slot(2, "minecraft:glass_bottle", 5));
        furnaceItems.add(slot(3, "minecraft:bucket", 1));
        oldFurnace.put("Items", furnaceItems);
        furnace.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, oldFurnace));
        check(helper, furnace.getContainerSize() == 2 && furnace.getItem(0).is(Items.COBBLESTONE), "the Furnace keeps its input: " + furnace.getItem(0));
        helper.runAfterDelay(5, () -> {
            ChestBlockEntity chest = helper.getBlockEntity(retortAt.below(), ChestBlockEntity.class);
            int bottles = 0;
            int experience = 0;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack in = chest.getItem(i);
                bottles += in.is(Items.GLASS_BOTTLE) ? in.getCount() : 0;
                experience += in.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()) ? in.getCount() : 0;
            }
            check(helper, bottles == 8 && experience == 2 && !retort.hasLeftovers(),
                    "the Retort's box went into the chest under it: " + bottles + " bottles, " + experience + " filled");
            var around = new AABB(helper.absolutePos(furnaceAt)).inflate(2);
            int dropped = 0;
            int buckets = 0;
            for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, around)) {
                dropped += item.getItem().is(Items.GLASS_BOTTLE) ? item.getItem().getCount() : 0;
                buckets += item.getItem().is(Items.BUCKET) ? item.getItem().getCount() : 0;
            }
            check(helper, dropped == 5 && buckets == 1 && !furnace.hasLeftovers(), "the Furnace's, with no chest, dropped by it: " + dropped + ", " + buckets);
            helper.succeed();
        });
    }

    private static CompoundTag slot(int slot, String id, int count) {
        CompoundTag tag = new CompoundTag();
        tag.putByte("Slot", (byte) slot);
        tag.putString("id", id);
        tag.putInt("count", count);
        return tag;
    }

    /** The menu's rule for a Liquid tab, as a layout has it. */
    private static final class MachineMenuTabs {
        static boolean liquid(MachineLayout layout) {
            return dev.nookworks.machine.MachineLiquids.any(layout);
        }
    }
}
