package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.List;
import java.util.Optional;

import com.mojang.logging.LogUtils;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.dowsing.DowsingSearch;
import dev.nookworks.dowsing.NookDowsingRodItem;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * The user's requests of 2026-10-08: a Keep Out row on the Sorters, whose items stay in the chest above ("設定したアイテム
 * は、ソーターで引かなくなる"); bundles that stopped a sorter now and then (one bound for a Storage Barrel, which holds
 * none: it was held for good); the Horizontal Item Warp made with an ender pearl, not a chorus fruit; and the dowsing
 * rod's unsaid knack of finding a Nether fortress in the Nether and an End city in the End.
 */
final class R10RequestTests {
    private R10RequestTests() {}

    static void register() {
        NookworksGameTests.test("horizontal_item_warp_is_made_with_an_ender_pearl", R10RequestTests::warpRecipes);
        NookworksGameTests.test("sorter_leaves_kept_out_items_in_the_chest_above", 200, R10RequestTests::keepOut);
        NookworksGameTests.test("precision_sorter_keeps_out_by_its_exact_switch", 200, R10RequestTests::keepOutExact);
        NookworksGameTests.test("sorter_leaves_a_bundle_for_a_storage_barrel_above", 200, R10RequestTests::bundleForBarrel);
        NookworksGameTests.test("sorter_hands_back_a_bundle_it_was_stuck_on", 200, R10RequestTests::stuckBundleGoesBack);
        NookworksGameTests.test("hopper_cannot_feed_a_sorter_what_it_keeps_out", R10RequestTests::hopperKeptOut);
        NookworksGameTests.test("sorter_keep_out_row_is_set_by_clicks_and_saved", R10RequestTests::keepOutRow);
        NookworksGameTests.test("precision_sorter_keeps_out_a_whole_tag", 200, R10RequestTests::keepOutTag);
        NookworksGameTests.test("dowsing_rod_seeks_fortresses_and_end_cities", R10RequestTests::dowsingStructures);
    }

    /** An Item Sorter facing north at {@code pos}, diamonds registered: they leave west (its right), the rest east. */
    private static SorterBlockEntity sorter(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block.defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        sorter.filter().setItem(0, new ItemStack(Items.DIAMOND));
        return sorter;
    }

    private static Container chest(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, ChestBlockEntity.class);
    }

    private static ItemStack crafted(GameTestHelper helper, ItemStack a, ItemStack b) {
        ServerLevel level = helper.getLevel();
        CraftingInput grid = CraftingInput.of(2, 1, List.of(a, b));
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(r -> r.value().assemble(grid)).orElse(ItemStack.EMPTY);
    }

    /** A sender or receiver and an ender pearl make the horizontal one; a chorus fruit no longer does. */
    private static void warpRecipes(GameTestHelper helper) {
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        check(helper, crafted(helper, new ItemStack(ModItems.ITEM_WARP_SENDER.get()), pearl).is(ModItems.LINE_WARP_SENDER.get()),
                "a sender and an ender pearl make the horizontal sender");
        check(helper, crafted(helper, new ItemStack(ModItems.ITEM_WARP_RECEIVER.get()), pearl).is(ModItems.LINE_WARP_RECEIVER.get()),
                "a receiver and an ender pearl make the horizontal receiver");
        check(helper, crafted(helper, new ItemStack(ModItems.ITEM_WARP_SENDER.get()), new ItemStack(Items.CHORUS_FRUIT)).isEmpty(),
                "a chorus fruit no longer makes one");
        helper.succeed();
    }

    /**
     * Cobblestone in the Keep Out row of an Item Sorter under a chest of diamonds, dirt and cobblestone: the diamonds go
     * right, the dirt left, and every piece of cobblestone stays in the chest - nothing stops behind it.
     */
    private static void keepOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        sorter.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.COBBLESTONE, 20));
        above.setItem(1, new ItemStack(Items.DIAMOND, 2));
        above.setItem(4, new ItemStack(Items.COBBLESTONE, 7));
        above.setItem(9, new ItemStack(Items.DIRT, 3));
        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.west()), Items.DIAMOND) == 2, "the diamonds go right");
            check(helper, countIn(chest(helper, pos.east()), Items.DIRT) == 3, "the dirt goes left, past the cobblestone");
            check(helper, countIn(above, Items.COBBLESTONE) == 27 && countIn(chest(helper, pos.east()), Items.COBBLESTONE) == 0,
                    "every piece of cobblestone stays above: " + countIn(above, Items.COBBLESTONE));
            check(helper, sorter.isEmpty(), "and the sorter holds none of it");
        });
    }

    /**
     * A Precision Sorter keeps out as its filter matches: a worn pickaxe in the row keeps out every iron pickaxe with
     * Exact off, and with it on, only those worn the same.
     */
    private static void keepOutExact(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.PRECISION_SORTER.get());
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(30);
        sorter.deny().setItem(0, worn.copy());
        check(helper, sorter.denies(new ItemStack(Items.IRON_PICKAXE)), "Exact off: a fresh pickaxe is kept out too");
        sorter.settings().toggle(dev.nookworks.sorter.SorterSettings.MATCH_COMPONENTS);
        check(helper, !sorter.denies(new ItemStack(Items.IRON_PICKAXE)) && sorter.denies(worn.copy()),
                "Exact on: only the one worn the same is");
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, worn.copy());
        above.setItem(1, new ItemStack(Items.IRON_PICKAXE));
        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.east()), Items.IRON_PICKAXE) == 1, "the fresh one is sorted");
            check(helper, above.getItem(0).getDamageValue() == 30 && above.getItem(1).isEmpty(), "the worn one stays above");
        });
    }

    /**
     * The other outlet a Storage Barrel, which holds no bundle: the bundle stays in the chest above, and the dirt behind
     * it goes into the barrel all the same - the sorter is not stopped by it.
     */
    private static void bundleForBarrel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), ModBlocks.IRON_STORAGE.get());
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.BUNDLE));
        above.setItem(1, new ItemStack(Items.DIRT, 5));
        above.setItem(2, new ItemStack(Items.DIAMOND, 1));
        var barrel = helper.getBlockEntity(pos.east(), StorageBlockEntity.class);
        helper.succeedWhen(() -> {
            check(helper, StorageTests.storageTotal(barrel, Items.DIRT) == 5, "the dirt reaches the barrel");
            check(helper, countIn(chest(helper, pos.west()), Items.DIAMOND) == 1, "the diamond goes right");
            check(helper, countIn(above, Items.BUNDLE) == 1, "the bundle stays above");
        });
    }

    /** A sorter of before, stuck on a bundle bound for a Storage Barrel: it puts the bundle back up and sorts on. */
    private static void stuckBundleGoesBack(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), ModBlocks.IRON_STORAGE.get());
        helper.setBlock(pos.above(), Blocks.CHEST);
        sorter.setItem(0, new ItemStack(Items.BUNDLE));
        Container above = chest(helper, pos.above());
        above.setItem(5, new ItemStack(Items.DIRT, 2));
        var barrel = helper.getBlockEntity(pos.east(), StorageBlockEntity.class);
        helper.succeedWhen(() -> {
            check(helper, countIn(above, Items.BUNDLE) == 1 && sorter.isEmpty(), "the bundle is back in the chest above");
            check(helper, StorageTests.storageTotal(barrel, Items.DIRT) == 2, "and the dirt sorted after it");
        });
    }

    /** A hopper is not let put in what the sorter keeps out, nor a bundle for a barrel; anything else, as before. */
    private static void hopperKeptOut(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        sorter.deny().setItem(3, new ItemStack(Items.COBBLESTONE));
        helper.setBlock(pos.east(), ModBlocks.IRON_STORAGE.get());
        check(helper, !sorter.canPlaceItemThroughFace(0, new ItemStack(Items.COBBLESTONE), Direction.UP), "kept out: refused");
        check(helper, !sorter.canPlaceItemThroughFace(0, new ItemStack(Items.BUNDLE), Direction.UP), "a bundle for the barrel: refused");
        check(helper, sorter.canPlaceItemThroughFace(0, new ItemStack(Items.DIRT), Direction.UP), "dirt for the barrel: taken");
        check(helper, sorter.canPlaceItemThroughFace(0, new ItemStack(Items.DIAMOND), Direction.UP), "a diamond for the right: taken");
        helper.succeed();
    }

    /**
     * A whole tag in the Keep Out row (the user, 2026-10-10: "タグ単位で禁止項目を追加できるようにして"): an oak log as the
     * picker's sample, minecraft:logs ticked, Keep Out pressed - one tag entry in the row, none in the filter, the picker
     * shut. The birch and spruce logs in the chest above stay there, and the cobblestone beside them is sorted. The same
     * tag twice is one entry; a mod goes in the row the same way; with the row full, nothing is put over what is there;
     * an Item Sorter, with no picker, takes no such button.
     */
    private static void keepOutTag(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.PRECISION_SORTER.get());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.OAK_LOG));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        menu.setCarried(ItemStack.EMPTY);
        int logs = menu.choices().indexOf(ItemTags.LOGS);
        check(helper, logs >= 0, "an oak log's tags offer minecraft:logs: " + menu.choices());
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE + logs);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_DENY);
        ItemStack entry = sorter.deny().getItem(0);
        check(helper, ItemTags.LOGS.equals(SorterTags.of(entry)) && entry.is(Items.OAK_LOG), "Keep Out puts the tag entry in the row: " + entry);
        check(helper, sorter.filter().getItem(1).isEmpty() && !menu.picking(), "not in the filter, and the picker is shut");
        check(helper, menu.hasDenyEntryFor(ItemTags.LOGS) && !menu.hasEntryFor(ItemTags.LOGS), "the menu knows it is kept out, not filtered");
        check(helper, sorter.denies(new ItemStack(Items.BIRCH_LOG)) && !sorter.denies(new ItemStack(Items.COBBLESTONE))
                && !sorter.denies(new ItemStack(Items.OAK_PLANKS)), "every log is kept out, nothing else");
        check(helper, !sorter.canPlaceItemThroughFace(0, new ItemStack(Items.SPRUCE_LOG), Direction.UP), "nor can a hopper put one in");

        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.BIRCH_LOG));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE + menu.choices().indexOf(ItemTags.LOGS));
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_DENY);
        check(helper, sorter.deny().getItem(1).isEmpty(), "the same tag twice is one entry");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(ModItems.BRASS_INGOT.get()));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE_MOD);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_DENY);
        check(helper, "nookworks".equals(SorterTags.modOf(sorter.deny().getItem(1))) && sorter.denies(new ItemStack(ModItems.BRASS_INGOT.get())),
                "a mod goes in the row the same way");
        for (int i = 2; i < SorterBlockEntity.DENY_SLOTS; i++) {
            sorter.deny().setItem(i, new ItemStack(Items.FLINT));
        }
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE_MOD);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_DENY);
        check(helper, menu.freeDenySlots() == 0 && sorter.deny().getItem(7).is(Items.FLINT) && SorterTags.modOf(sorter.deny().getItem(7)) == null,
                "a full row: nothing is put over what is there");
        menu.setCarried(ItemStack.EMPTY);
        for (int i = 1; i < SorterBlockEntity.DENY_SLOTS; i++) {
            sorter.deny().setItem(i, ItemStack.EMPTY);
        }

        BlockPos itemSorterPos = new BlockPos(1, 1, 6);
        var itemSorter = sorter(helper, itemSorterPos, ModBlocks.ITEM_SORTER.get());
        var itemMenu = (SorterMenu) itemSorter.createMenu(2, player.getInventory(), player);
        check(helper, !itemMenu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_DENY), "an Item Sorter has no picker to keep out from");

        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.BIRCH_LOG, 6));
        above.setItem(1, new ItemStack(Items.COBBLESTONE, 4));
        above.setItem(2, new ItemStack(Items.SPRUCE_LOG, 3));
        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.east()), Items.COBBLESTONE) == 4, "the cobblestone is sorted, past the logs");
            check(helper, countIn(above, Items.BIRCH_LOG) == 6 && countIn(above, Items.SPRUCE_LOG) == 3 && sorter.isEmpty(),
                    "every log stays above");
        });
    }

    /** The row in the screen: a click with an item registers a copy, an empty hand clears, a shift-click never fills it; saved and loaded. */
    private static void keepOutRow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        menu.setCarried(new ItemStack(Items.COBBLESTONE, 12));
        menu.clicked(SorterMenu.DENY_SLOT + 2, 0, ContainerInput.PICKUP, player);
        check(helper, sorter.deny().getItem(2).is(Items.COBBLESTONE) && sorter.deny().getItem(2).getCount() == 1
                && menu.getCarried().getCount() == 12, "a click registers a copy and leaves the hand as it was");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(SorterMenu.DENY_SLOT + 2, 0, ContainerInput.PICKUP, player);
        check(helper, sorter.deny().getItem(2).isEmpty(), "an empty hand clears it");
        player.getInventory().setItem(0, new ItemStack(Items.GRAVEL, 4));
        int hotbar = menu.slots.size() - 9;
        menu.quickMoveStack(player, hotbar);
        boolean rowEmpty = true;
        for (int i = 0; i < SorterBlockEntity.DENY_SLOTS; i++) {
            rowEmpty &= sorter.deny().getItem(i).isEmpty();
        }
        check(helper, rowEmpty && sorter.filter().getItem(1).is(Items.GRAVEL), "a shift-click registers in the filter, not the row");

        sorter.deny().setItem(5, new ItemStack(Items.FLINT));
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        sorter.saveWithoutMetadata(output);
        helper.setBlock(pos.north(), ModBlocks.ITEM_SORTER.get().defaultBlockState());
        var copy = helper.getBlockEntity(pos.north(), SorterBlockEntity.class);
        copy.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()));
        check(helper, copy.deny().getItem(5).is(Items.FLINT), "the row is saved and loaded");
        helper.succeed();
    }

    /**
     * The rod's hidden knack: in the Nether it seeks #nookworks:dowsing/nether (fortresses), in the End
     * #nookworks:dowsing/end (End cities), and nothing else anywhere else. The search answers as /locate structure's own
     * check does: whatever vanilla finds within ten cells, it finds too, as near or nearer. Its find sets the needle,
     * without a depth.
     */
    private static void dowsingStructures(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel nether = server.getLevel(Level.NETHER);
        ServerLevel end = server.getLevel(Level.END);
        check(helper, "fortress".equals(NookDowsingRodItem.sought(nether)) && "end_city".equals(NookDowsingRodItem.sought(end))
                && NookDowsingRodItem.sought(helper.getLevel()) == null, "the Nether seeks fortresses, the End cities, the rest cinnabar");
        compare(helper, nether, new BlockPos(0, 64, 0), NookDowsingRodItem.NETHER_FIND);
        compare(helper, end, new BlockPos(1200, 64, 300), NookDowsingRodItem.END_FIND);

        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)).above(64));
        player.snapTo(stand.x, stand.y, stand.z);
        ItemStack rod = new ItemStack(ModItems.NOOK_DOWSING_ROD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        long search = NookDowsingRodItem.mark(rod, 99L);
        BlockPos from = player.blockPosition();
        BlockPos fortress = from.offset(400, 0, -120);
        NookDowsingRodItem.found(nether, player, search, from, new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, fortress), "fortress");
        LodestoneTracker needle = player.getMainHandItem().get(DataComponents.LODESTONE_TRACKER);
        check(helper, needle != null && needle.target().equals(Optional.of(GlobalPos.of(Level.NETHER, fortress))),
                "the needle points at the fortress, in the Nether: " + needle);
        helper.succeed();
    }

    private static void compare(GameTestHelper helper, ServerLevel level, BlockPos from, net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.structure.Structure> tag) {
        BlockPos vanilla = level.findNearestMapStructure(tag, from, 10, false);
        DowsingSearch.Result ours = DowsingSearch.findStructureNow(level, from, tag, DowsingSearch.RADIUS);
        LogUtils.getLogger().info("[dowsing] {} from {}: vanilla {}, ours {}", level.dimension(), from, vanilla, ours);
        if (vanilla != null) {
            check(helper, ours.outcome() == DowsingSearch.Outcome.FOUND && ours.at() != null
                    && flat(from, ours.at()) <= flat(from, vanilla) + 1, "found as near as /locate: vanilla " + vanilla + ", ours " + ours);
        } else {
            check(helper, ours.outcome() != DowsingSearch.Outcome.FOUND || flat(from, ours.at()) > 16 * 16 * 10,
                    "nothing within ten cells for /locate, nothing nearer for the rod: " + ours);
        }
    }

    private static double flat(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
