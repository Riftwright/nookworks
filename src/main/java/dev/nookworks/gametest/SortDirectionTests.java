package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.logging.LogUtils;

import dev.nookworks.inventory.ChestSorting;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.storage.SortOrder;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageInventory;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageTier;
import dev.nookworks.storage.StorageUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/**
 * Sort turned round (the user, 2026-10-31: 「昇順」「降順」のボタンもあったほうが親切だよね): every order runs the other way
 * too - fewest first, the game's order from its end, the mods Z-A, first added first - a plain item still before the
 * same item named. A barrel keeps which way round with its order, and choosing another order puts it back; a chest's
 * Sort takes it from the client.
 */
final class SortDirectionTests {
    private SortDirectionTests() {}

    static void register() {
        test("every_sort_order_turns_round", SortDirectionTests::turned);
        test("a_barrel_keeps_its_order_turned_round", SortDirectionTests::barrel);
    }

    private static void turned(GameTestHelper helper) {
        long[] now = {100};
        var inventory = new StorageInventory(StorageTier.GOLD, () -> {}, () -> now[0]);
        Item salt = ModItems.SALT.get();
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(Items.STONE, 300), false, null);
        now[0] = 200;
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(Items.DIRT, 10), false, null);
        now[0] = 300;
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(salt, 50), false, null);
        // the barrel orders of PlayTests, turned round
        Map<SortOrder, List<Item>> turned = Map.of(
                SortOrder.COUNT, List.of(Items.DIRT, salt, Items.STONE),
                SortOrder.ID, List.of(salt, Items.DIRT, Items.STONE),
                SortOrder.MOD, List.of(salt, Items.STONE, Items.DIRT),
                SortOrder.RECENT, List.of(Items.STONE, Items.DIRT, salt));
        for (SortOrder order : SortOrder.values()) {
            inventory.sort(order, true);
            check(helper, items(inventory).equals(turned.get(order)), order + " turned round put them " + items(inventory));
        }
        check(helper, SortOrder.COUNT.descending() && SortOrder.RECENT.descending() && !SortOrder.ID.descending() && !SortOrder.MOD.descending(),
                "count and last added run descending by themselves, the game's order and the mods' ascending");

        // a plain stick still before a named one, either way round
        var sticks = new StorageInventory(StorageTier.GOLD, () -> {}, () -> 0L);
        ItemStack named = new ItemStack(Items.STICK, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Wand"));
        ItemUtil.insertItemReturnRemaining(sticks, named, false, null);
        ItemUtil.insertItemReturnRemaining(sticks, new ItemStack(Items.STICK, 5), false, null);
        ItemUtil.insertItemReturnRemaining(sticks, new ItemStack(Items.STONE, 1), false, null);
        sticks.sort(SortOrder.ID, true);
        check(helper, sticks.getResource(0).is(Items.STICK) && !sticks.getResource(0).toStack(1).has(DataComponents.CUSTOM_NAME)
                && sticks.getResource(1).toStack(1).has(DataComponents.CUSTOM_NAME) && sticks.getResource(2).is(Items.STONE),
                "the game's order from its end: the sticks before the stone, the plain one first");

        // a chest's Sort turned round: fewest first
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos at = new BlockPos(1, 1, 1);
        helper.setBlock(at, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(at, ChestBlockEntity.class);
        chest.setItem(0, new ItemStack(ModCooking.FLOUR.get(), 40));
        chest.setItem(1, new ItemStack(Items.DIRT, 5));
        chest.setItem(2, new ItemStack(Items.STONE, 20));
        ChestMenu menu = ChestMenu.threeRows(1, player.getInventory(), chest);
        check(helper, ChestSorting.sort(menu, player, SortOrder.COUNT, true) && chest.getItem(0).is(Items.DIRT)
                && chest.getItem(1).is(Items.STONE) && chest.getItem(2).is(ModCooking.FLOUR.get()),
                "a chest by count turned round: fewest first: " + chest.getItem(0) + ", " + chest.getItem(1) + ", " + chest.getItem(2));
        helper.succeed();
    }

    private static void barrel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = (StorageMenu) barrel.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, StorageMenu.BUTTON_ORDER_BACK); // from the game's order back to count
        check(helper, barrel.sortOrder() == SortOrder.COUNT && !menu.sortReversed(), "by count, as it runs by itself");
        menu.clickMenuButton(player, StorageMenu.BUTTON_REVERSE);
        check(helper, barrel.sortReversed() && menu.sortReversed(), "the button turns it round");
        barrel.inventory().set(0, ItemResource.of(Items.STONE), 60);
        barrel.inventory().set(1, ItemResource.of(Items.DIRT), 5);
        menu.clickMenuButton(player, StorageMenu.BUTTON_SORT);
        check(helper, barrel.inventory().getResource(0).is(Items.DIRT), "Sort sorted by count turned round: the dirt first");
        menu.removed(player);

        var level = helper.getLevel();
        var saved = barrel.saveWithoutMetadata(level.registryAccess());
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity again = helper.getBlockEntity(pos, StorageBlockEntity.class);
        try (var reporter = new ProblemReporter.ScopedCollector(again.problemPath(), LogUtils.getLogger())) {
            again.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
        }
        check(helper, again.sortOrder() == SortOrder.COUNT && again.sortReversed(), "which way round is saved with the barrel");
        var menuAgain = (StorageMenu) again.createMenu(2, player.getInventory(), player);
        menuAgain.clickMenuButton(player, StorageMenu.BUTTON_ORDER_NEXT);
        check(helper, again.sortOrder() == SortOrder.ID && !again.sortReversed(), "another order runs its own way again");
        menuAgain.removed(player);
        again.setSortReversed(true);
        check(helper, StorageUpgradeItem.upgrade(level, helper.absolutePos(pos), ModBlocks.GOLD_STORAGE.get(), player),
                "the barrel is upgraded");
        StorageBlockEntity gold = helper.getBlockEntity(pos, StorageBlockEntity.class);
        check(helper, gold.sortOrder() == SortOrder.ID && gold.sortReversed(), "and keeps its order, turned round as it was (r19)");
        helper.succeed();
    }

    /** The kinds of item in the inventory, in its order, one entry a run of slots. */
    private static List<Item> items(StorageInventory inventory) {
        List<Item> runs = new ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getAmountAsInt(i) > 0) {
                Item item = inventory.getResource(i).getItem();
                if (runs.isEmpty() || runs.getLast() != item) {
                    runs.add(item);
                }
            }
        }
        return runs;
    }
}
