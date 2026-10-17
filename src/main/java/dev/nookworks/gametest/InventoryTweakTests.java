package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import dev.nookworks.inventory.ChestSorting;
import dev.nookworks.inventory.InventoryTweaks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.storage.SortOrder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;

/** The inventory tweaks, their clicks replayed as the server would receive them. */
final class InventoryTweakTests {
    static void register() {
        test("wheel_pushes_one_into_existing_stack", InventoryTweakTests::wheelPushesOne);
        test("wheel_pushes_last_item", InventoryTweakTests::wheelPushesLastItem);
        test("wheel_pulls_one_from_other_side", InventoryTweakTests::wheelPullsOne);
        test("wheel_moves_hotbar_to_main_in_inventory", InventoryTweakTests::wheelHotbarToMain);
        test("wheel_does_nothing_when_full", InventoryTweakTests::wheelNothingWhenFull);
        test("chest_sort_gathers_and_orders_a_large_chest", InventoryTweakTests::chestSortLargeChest);
        test("chest_sort_orders_by_count_and_by_mod", InventoryTweakTests::chestSortOrders);
        test("chest_sort_keeps_to_vanilla_menus", InventoryTweakTests::chestSortOnlyVanilla);
        test("chest_sort_sorts_a_placed_chest_from_its_screen", InventoryTweakTests::chestSortFromScreen);
    }

    // ---- Inventory tweaks (clicks are replayed exactly as the server would receive them) ----

    static void apply(AbstractContainerMenu menu, Player player, List<InventoryTweaks.Click> clicks) {
        for (InventoryTweaks.Click c : clicks) {
            menu.clicked(c.slot(), c.button(), c.input(), player);
        }
    }

    /** Chest menu: slots 0-26 chest, 27-53 main inventory, 54-62 hotbar. */
    /** A chest set down at {@code pos}: real storage, as the Sort button sorts. */
    static ChestBlockEntity chestAt(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, Blocks.CHEST);
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    static ChestMenu chestMenu(Player player, SimpleContainer chest) {
        return ChestMenu.threeRows(1, player.getInventory(), chest);
    }

    static void wheelPushesOne(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 10));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        ChestMenu menu = chestMenu(player, chest);

        apply(menu, player, InventoryTweaks.pushOne(menu, player, menu.getSlot(0)));

        check(helper, chest.getItem(0).getCount() == 4, "chest should keep 4, has " + chest.getItem(0).getCount());
        check(helper, player.getInventory().getItem(20).getCount() == 11, "existing stack should grow to 11");
        check(helper, menu.getCarried().isEmpty(), "cursor must end empty");
        helper.succeed();
    }

    static void wheelPushesLastItem(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(3, new ItemStack(Items.APPLE, 1));
        ChestMenu menu = chestMenu(player, chest);

        apply(menu, player, InventoryTweaks.pushOne(menu, player, menu.getSlot(3)));

        check(helper, chest.getItem(3).isEmpty(), "chest slot should be empty");
        check(helper, player.getInventory().countItem(Items.APPLE) == 1, "player should have the apple");
        check(helper, menu.getCarried().isEmpty(), "cursor must end empty");
        helper.succeed();
    }

    static void wheelPullsOne(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(8, new ItemStack(Items.IRON_INGOT, 3));
        ChestMenu menu = chestMenu(player, chest);

        apply(menu, player, InventoryTweaks.pullOne(menu, player, menu.getSlot(8)));

        check(helper, chest.getItem(8).getCount() == 4, "chest slot should grow to 4");
        check(helper, player.getInventory().getItem(0).getCount() == 9, "player stack should shrink to 9");
        check(helper, menu.getCarried().isEmpty(), "cursor must end empty");
        helper.succeed();
    }

    static void wheelHotbarToMain(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(2, new ItemStack(Items.STICK, 6));
        InventoryMenu menu = player.inventoryMenu;
        Slot hotbar = menu.slots.stream().filter(s -> s.container == player.getInventory() && s.getContainerSlot() == 2)
                .findFirst().orElseThrow();

        apply(menu, player, InventoryTweaks.pushOne(menu, player, hotbar));

        check(helper, player.getInventory().getItem(2).getCount() == 5, "hotbar stack should shrink to 5");
        int inMain = 0;
        for (int i = 9; i < 36; i++) {
            inMain += player.getInventory().getItem(i).is(Items.STICK) ? player.getInventory().getItem(i).getCount() : 0;
        }
        check(helper, inMain == 1, "one stick should land in the main inventory, found " + inMain);
        check(helper, menu.getCarried().isEmpty(), "cursor must end empty");
        helper.succeed();
    }

    static void wheelNothingWhenFull(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 36; i++) {
            player.getInventory().setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        SimpleContainer chest = new SimpleContainer(27);
        chest.setItem(0, new ItemStack(Items.DIAMOND, 5));
        ChestMenu menu = chestMenu(player, chest);

        check(helper, InventoryTweaks.pushOne(menu, player, menu.getSlot(0)).isEmpty(), "no room: no clicks should be planned");
        helper.succeed();
    }

    // ---- the Sort button on vanilla's chests ----

    private static int count(Container container, Item item) {
        int n = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            n += container.getItem(i).is(item) ? container.getItem(i).getCount() : 0;
        }
        return n;
    }

    /** A large chest's scattered stacks: each kind gathered into as few stacks as it fits, in the game's order, nothing lost. */
    static void chestSortLargeChest(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(5, new ItemStack(Items.STONE, 3));
        Container chest = new CompoundContainer(chestAt(helper, new BlockPos(1, 1, 1)), chestAt(helper, new BlockPos(3, 1, 1)));
        chest.setItem(40, new ItemStack(Items.DIRT, 10));
        chest.setItem(2, new ItemStack(Items.STONE, 50));
        chest.setItem(51, new ItemStack(Items.DIRT, 20));
        chest.setItem(7, new ItemStack(Items.DIAMOND_SWORD));
        chest.setItem(30, new ItemStack(Items.STONE, 64));
        ItemStack named = new ItemStack(Items.STONE, 5);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        chest.setItem(53, named);
        chest.setItem(12, new ItemStack(Items.DIAMOND_SWORD));
        ChestMenu menu = ChestMenu.sixRows(1, player.getInventory(), chest);
        check(helper, ChestSorting.sort(menu, player, SortOrder.ID), "it sorts");
        check(helper, chest.getItem(0).is(Items.STONE) && chest.getItem(0).getCount() == 64 && chest.getItem(1).getCount() == 50
                && !chest.getItem(1).has(DataComponents.CUSTOM_NAME), "stone first, in full stacks: " + chest.getItem(0) + ", " + chest.getItem(1));
        check(helper, chest.getItem(2).is(Items.STONE) && chest.getItem(2).has(DataComponents.CUSTOM_NAME) && chest.getItem(2).getCount() == 5,
                "the named stone kept apart, after the plain");
        check(helper, chest.getItem(3).is(Items.DIRT) && chest.getItem(3).getCount() == 30, "dirt gathered into one stack");
        check(helper, chest.getItem(4).is(Items.DIAMOND_SWORD) && chest.getItem(5).is(Items.DIAMOND_SWORD), "swords one to a slot");
        for (int i = 6; i < 54; i++) {
            check(helper, chest.getItem(i).isEmpty(), "the rest empty, slot " + i);
        }
        check(helper, count(chest, Items.STONE) == 119 && count(chest, Items.DIRT) == 30, "nothing lost or made");
        check(helper, player.getInventory().getItem(5).getCount() == 3, "the player's own inventory untouched");
        helper.succeed();
    }

    /** By count: most first; by mod: Minecraft first, then the mods by name. */
    static void chestSortOrders(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestBlockEntity chest = chestAt(helper, new BlockPos(1, 1, 1));
        chest.setItem(0, new ItemStack(ModCooking.FLOUR.get(), 40));
        chest.setItem(1, new ItemStack(Items.DIRT, 5));
        chest.setItem(2, new ItemStack(Items.STONE, 20));
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        ChestSorting.sort(menu, player, SortOrder.COUNT);
        check(helper, chest.getItem(0).is(ModCooking.FLOUR.get()) && chest.getItem(1).is(Items.STONE) && chest.getItem(2).is(Items.DIRT),
                "most first: " + chest.getItem(0) + ", " + chest.getItem(1) + ", " + chest.getItem(2));
        ChestSorting.sort(menu, player, SortOrder.MOD);
        check(helper, chest.getItem(0).is(Items.DIRT) && chest.getItem(1).is(Items.STONE) && chest.getItem(2).is(ModCooking.FLOUR.get()),
                "Minecraft's first, by name, then ours: " + chest.getItem(0) + ", " + chest.getItem(1) + ", " + chest.getItem(2));
        check(helper, ChestSorting.orderOf(SortOrder.RECENT.ordinal()) == SortOrder.ID, "a chest keeps no last-added order: the default");
        helper.succeed();
    }

    /**
     * Another mod's menu built on a chest's is left alone, and so is a chest's own menu over a container made up for a
     * screen (a mod's shop, its items buttons); a shulker box's is sorted.
     */
    static void chestSortOnlyVanilla(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ChestBlockEntity chest = chestAt(helper, new BlockPos(1, 1, 1));
        chest.setItem(5, new ItemStack(Items.DIRT, 5));
        ChestMenu theirs = new ChestMenu(MenuType.GENERIC_9x3, 1, player.getInventory(), chest, 3) {};
        check(helper, !ChestSorting.sort(theirs, player, SortOrder.ID) && chest.getItem(5).is(Items.DIRT), "a subclass is not sorted");
        SimpleContainer shop = new SimpleContainer(27);
        shop.setItem(5, new ItemStack(Items.EMERALD));
        check(helper, !ChestSorting.sort(chestMenu(player, shop), player, SortOrder.ID) && shop.getItem(5).is(Items.EMERALD),
                "a screen's made-up container is not sorted");
        helper.setBlock(new BlockPos(3, 1, 1), Blocks.SHULKER_BOX);
        var box = helper.getBlockEntity(new BlockPos(3, 1, 1), net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.class);
        box.setItem(9, new ItemStack(Items.DIRT, 5));
        box.setItem(3, new ItemStack(Items.DIRT, 7));
        ShulkerBoxMenu shulker = new ShulkerBoxMenu(2, player.getInventory(), box);
        check(helper, ChestSorting.sort(shulker, player, SortOrder.ID) && box.getItem(0).getCount() == 12 && box.getItem(9).isEmpty(),
                "a shulker box is: " + box.getItem(0));
        helper.succeed();
    }

    /**
     * As the button does it: a chest set down and opened by a player, the sort asked for by the menu's id - only the
     * open one, and not by a spectator.
     */
    static void chestSortFromScreen(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos, ChestBlockEntity.class);
        chest.setItem(20, new ItemStack(Items.DIRT, 4));
        chest.setItem(10, new ItemStack(Items.DIRT, 6));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 1, 4.5));
        player.snapTo(near.x, near.y, near.z);
        player.openMenu(chest);
        int id = player.containerMenu.containerId;
        check(helper, !ChestSorting.sortOpen(player, id + 1, SortOrder.ID.ordinal()) && chest.getItem(20).getCount() == 4,
                "another menu's id: nothing");
        player.setGameMode(GameType.SPECTATOR);
        check(helper, !ChestSorting.sortOpen(player, id, SortOrder.ID.ordinal()) && chest.getItem(20).getCount() == 4, "a spectator: nothing");
        player.setGameMode(GameType.SURVIVAL);
        check(helper, ChestSorting.sortOpen(player, id, SortOrder.ID.ordinal()) && chest.getItem(0).getCount() == 10 && chest.getItem(20).isEmpty(),
                "the open chest, sorted: " + chest.getItem(0));
        player.closeContainer();
        helper.succeed();
    }

    private InventoryTweakTests() {}
}
