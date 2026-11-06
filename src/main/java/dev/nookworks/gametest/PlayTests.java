package dev.nookworks.gametest;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import dev.nookworks.pearl.LodestoneNames;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.settleLight;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.IntSupplier;

import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.block.ElectricFurnaceBlock;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.darkmatter.DarkMatterBlock;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.feature.Features;
import dev.nookworks.feature.PlacedLogs;
import dev.nookworks.feature.TrimmedVines;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.pearl.ThrownLodePearl;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.storage.SortOrder;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageInventory;
import dev.nookworks.storage.StorageItem;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageSpill;
import dev.nookworks.storage.StorageTier;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/** Things the user ran into playing the 0.2 build, each held to the right behaviour. */
final class PlayTests {
    static void register() {
        NookworksGameTests.test("barrel_scroll_shows_the_servers_rows", PlayTests::barrelScrollShowsTheServersRows);
        NookworksGameTests.test("a_tree_grown_by_bone_meal_fells_from_the_bottom", PlayTests::aTreeGrownByBoneMealFellsFromTheBottom);
        NookworksGameTests.test("an_old_marked_stump_still_fells", PlayTests::anOldMarkedStumpStillFells);
        NookworksGameTests.test("a_chest_opens_under_the_sifter", PlayTests::aChestOpensUnderTheSifter);
        NookworksGameTests.test("sifted_dirt_gives_few_kinds_of_thing", PlayTests::siftedDirtGivesFewKindsOfThing);
        NookworksGameTests.test("hanging_planters_hang_on_thin_rope", PlayTests::hangingPlantersHangOnThinRope);
        NookworksGameTests.test("barrel_sorts_in_the_order_chosen", PlayTests::barrelSortsInTheOrderChosen);
        NookworksGameTests.test("barrel_keeps_its_sort_order", PlayTests::barrelKeepsItsSortOrder);
        NookworksGameTests.test("lode_pearls_bind_to_a_lodestone_and_stack", PlayTests::lodePearlsBindAndStack);
        NookworksGameTests.test("a_lode_pearl_takes_you_to_its_lodestone", PlayTests::aLodePearlTakesYouHome);
        NookworksGameTests.test("a_lode_pearl_in_the_void_still_takes_you", PlayTests::aLodePearlInTheVoid);
        NookworksGameTests.test("a_lode_pearl_without_its_lodestone_stays_a_pearl", PlayTests::aLodePearlWithoutItsLodestone);
        NookworksGameTests.test("a_lode_pearl_without_its_lodestone_comes_back_from_the_void", PlayTests::aLodePearlWithoutItsLodestoneInTheVoid);
        NookworksGameTests.test("a_lode_pearl_sets_you_down_clear_of_harm", PlayTests::aLodePearlSetsYouDownClearOfHarm);
        NookworksGameTests.test("a_claim_keeps_a_lode_pearl_out", PlayTests::aClaimKeepsALodePearlOut);
        NookworksGameTests.test("lodestones_are_named_as_they_are_set_down", PlayTests::lodestonesNamedAsSetDown);
        NookworksGameTests.test("a_lodestone_renamed_renames_its_pearls", PlayTests::lodestoneRenamed);
        NookworksGameTests.test("a_lodestone_taken_away_forgets_its_name", PlayTests::lodestoneForgotten);
        NookworksGameTests.test("soap_washes_a_lode_pearl_back", PlayTests::soapWashesALodePearlBack);
        // light is worked out off the main thread: on a busy machine a torch takes more than ten seconds to spread
        NookworksGameTests.test("dark_matter_puts_out_the_light", 600, PlayTests::darkMatterPutsOutTheLight);
        NookworksGameTests.test("cooked_food_chars_into_dark_matter", PlayTests::cookedFoodCharsIntoDarkMatter);
        NookworksGameTests.test("a_label_pressed_on_sneaking_goes_in_the_middle", PlayTests::aLabelSneakingGoesInTheMiddle);
        NookworksGameTests.test("the_press_takes_a_sample_on_its_labels_page", PlayTests::thePressTakesASampleOnItsLabelsPage);
        NookworksGameTests.test("a_vine_cut_with_fine_shears_stops_spreading", PlayTests::aVineCutWithFineShearsStopsSpreading);
        NookworksGameTests.test("velarium_barrel_holds_a_warehouse_of_warehouses", PlayTests::velariumBarrelHoldsAWarehouseOfWarehouses);
        NookworksGameTests.test("the_electric_furnace_smelts_on_power", PlayTests::theElectricFurnaceSmeltsOnPower);
        NookworksGameTests.test("a_barrel_too_full_to_break_leaves_whole", PlayTests::aBarrelTooFullToBreakLeavesWhole);
        NookworksGameTests.test("a_made_up_mob_bottle_lets_nothing_out", PlayTests::aMadeUpMobBottleLetsNothingOut);
        NookworksGameTests.test("the_oil_press_starts_over_for_other_seeds", PlayTests::theOilPressStartsOverForOtherSeeds);
        NookworksGameTests.test("workbenches_are_crafting_tables", PlayTests::workbenchesAreCraftingTables);
        NookworksGameTests.test("worn_gear_grinds_to_less", PlayTests::wornGearGrindsToLess);
        NookworksGameTests.test("the_mercury_chain_does_not_multiply_fortune", PlayTests::theMercuryChainDoesNotMultiplyFortune);
        NookworksGameTests.test("experience_bottles_fill_and_enchant", PlayTests::experienceBottlesFillAndEnchant);
        NookworksGameTests.test("the_electrolytic_cell_makes_caustic_soda_zinc_and_bright_copper", PlayTests::theElectrolyticCell);
    }

    /**
     * What a server menu sends its player, handed straight to a client menu in the order the network carries it: a
     * tick's slot changes first, then its data (the scroll row among them).
     */
    static ContainerSynchronizer relay(AbstractContainerMenu client) {
        return new ContainerSynchronizer() {
            @Override
            public void sendInitialData(AbstractContainerMenu menu, List<ItemStack> items, ItemStack carried, int[] data) {
                client.initializeContents(0, items.stream().map(ItemStack::copy).toList(), carried.copy());
                for (int i = 0; i < data.length; i++) {
                    client.setData(i, (short) data[i]);
                }
            }

            @Override
            public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) {
                client.setItem(slot, 0, stack.copy());
            }

            @Override
            public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) {
                client.setCarried(stack.copy());
            }

            @Override
            public void sendDataChange(AbstractContainerMenu menu, int id, int value) {
                client.setData(id, (short) value);
            }

            @Override
            public RemoteSlot createSlot() {
                return new RemoteSlot.Synchronized(component -> 0);
            }
        };
    }

    /**
     * A Diamond Storage Barrel scrolled with the wheel shows on the client exactly what the server has in each visible
     * slot - scrolled several rows in one tick, one row at a time, and after a sort has made the rows look alike (the
     * server does not send a slot again that looks the same as before, and the client used to file what it did send
     * under the row it had been on).
     */
    private static void barrelScrollShowsTheServersRows(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.DIAMOND_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        var inventory = barrel.inventory();
        // Every slot something different, so a row out of place cannot pass for the right one.
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (items.size() < inventory.size() && item != Items.AIR && item.getDefaultMaxStackSize() == 64
                    && inventory.isValid(0, ItemResource.of(item))) {
                items.add(item);
            }
        }
        for (int i = 0; i < inventory.size(); i++) {
            inventory.set(i, ItemResource.of(items.get(i)), 1 + i);
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var server = (StorageMenu) barrel.createMenu(1, player.getInventory(), player);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        buf.writeEnum(StorageTier.DIAMOND);
        var client = StorageMenu.client(2, player.getInventory(), buf);
        server.setSynchronizer(relay(client));
        same(helper, server, client, "when opened");

        // A quick flick of the wheel: three rows asked for before the server next sends anything.
        server.clickMenuButton(player, 1);
        server.clickMenuButton(player, 2);
        server.clickMenuButton(player, 3);
        server.broadcastChanges();
        same(helper, server, client, "after a fast scroll");
        for (int row = 2; row >= 0; row--) {
            server.clickMenuButton(player, row);
            server.broadcastChanges();
            same(helper, server, client, "scrolled back to row " + row);
        }

        // Sixty full slots of one thing: after a sort the first rows are all alike.
        for (int i = 0; i < 60; i++) {
            inventory.set(i, ItemResource.of(Items.COBBLESTONE), inventory.tier().slotCapacity(64));
        }
        server.clickMenuButton(player, StorageMenu.BUTTON_SORT);
        server.broadcastChanges();
        same(helper, server, client, "after a sort");
        for (int row : new int[] {1, 3, 2, 3, 0}) {
            server.clickMenuButton(player, row);
            server.broadcastChanges();
            same(helper, server, client, "after a sort, at row " + row);
        }
        server.removed(player);
        helper.succeed();
    }

    private static ServerPlayer holding(GameTestHelper helper, ItemStack stack) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return player;
    }

    /**
     * A birch sapling grown on the spot with bone meal - as a player does it, through the item's use - is a tree, not
     * something built: its bottom log is not marked as placed, and an axe there fells the whole trunk.
     */
    private static void aTreeGrownByBoneMealFellsFromTheBottom(GameTestHelper helper) {
        BlockPos sapling = new BlockPos(3, 2, 3);
        helper.setBlock(sapling.below(), Blocks.DIRT);
        helper.setBlock(sapling, Blocks.BIRCH_SAPLING);
        ServerPlayer player = holding(helper, new ItemStack(Items.BONE_MEAL, 64));
        BlockPos absolute = helper.absolutePos(sapling);
        for (int i = 0; i < 64 && !helper.getBlockState(sapling).is(Blocks.BIRCH_LOG); i++) {
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        }
        check(helper, helper.getBlockState(sapling).is(Blocks.BIRCH_LOG), "the sapling grew");
        check(helper, !PlacedLogs.isPlaced(helper.getLevel(), absolute), "the bottom log is marked as placed");
        List<BlockPos> trunk = new ArrayList<>();
        for (BlockPos p = sapling; helper.getBlockState(p).is(Blocks.BIRCH_LOG); p = p.above()) {
            trunk.add(p);
        }
        check(helper, trunk.size() >= 4, "a trunk of " + trunk.size());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
        player.gameMode.destroyBlock(absolute);
        for (BlockPos p : trunk) {
            check(helper, !helper.getBlockState(p).is(Blocks.BIRCH_LOG), "log at " + p + " still standing");
        }
        helper.succeed();
    }

    /**
     * A tree bone meal grew before the fix has its bottom log marked as placed, and keeps the mark in its chunk. Felling
     * from that stump still takes the tree; a wall of placed logs (every log marked) still stands.
     */
    private static void anOldMarkedStumpStillFells(GameTestHelper helper) {
        List<BlockPos> trunk = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            trunk.add(new BlockPos(2, y, 2));
        }
        trunk.forEach(p -> helper.setBlock(p, Blocks.SPRUCE_LOG));
        helper.setBlock(new BlockPos(2, 6, 2), Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        PlacedLogs.mark(helper.getLevel(), helper.absolutePos(trunk.get(0)), true);
        List<BlockPos> wall = new ArrayList<>();
        for (int y = 1; y <= 5; y++) {
            wall.add(new BlockPos(5, y, 2));
        }
        wall.forEach(p -> {
            helper.setBlock(p, Blocks.SPRUCE_LOG);
            PlacedLogs.mark(helper.getLevel(), helper.absolutePos(p), true);
        });
        helper.setBlock(new BlockPos(5, 6, 2), Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false));
        ServerPlayer player = holding(helper, new ItemStack(Items.DIAMOND_AXE));
        player.gameMode.destroyBlock(helper.absolutePos(trunk.get(0)));
        for (BlockPos p : trunk) {
            check(helper, !helper.getBlockState(p).is(Blocks.SPRUCE_LOG), "stump felling left the log at " + p);
        }
        player.gameMode.destroyBlock(helper.absolutePos(wall.get(0)));
        check(helper, helper.getBlockState(wall.get(1)).is(Blocks.SPRUCE_LOG), "the placed wall came down with its bottom log");
        helper.succeed();
    }

    /**
     * The Sifter stands on legs and drops what it finds into whatever is under it, so a chest there opens, and a signal
     * into the Sifter still reaches it. A cabinet machine like the Crusher is as solid as it looks: a chest under it stays
     * shut, as under any full block (a barrel or a hopper goes there instead).
     */
    private static void aChestOpensUnderTheSifter(GameTestHelper helper) {
        BlockPos chest = new BlockPos(1, 1, 1);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(chest.above(), ModBlocks.SIFTER.get());
        check(helper, !ChestBlock.isChestBlockedAt(helper.getLevel(), helper.absolutePos(chest)),
                "a chest under the Sifter will not open");
        BlockPos other = new BlockPos(4, 1, 1);
        helper.setBlock(other, Blocks.CHEST);
        helper.setBlock(other.above(), ModBlocks.CRUSHER.get());
        check(helper, ChestBlock.isChestBlockedAt(helper.getLevel(), helper.absolutePos(other)),
                "a chest under the Crusher opens, but the Crusher is a solid cabinet");
        BlockPos powered = new BlockPos(1, 1, 4);
        helper.setBlock(powered, ModBlocks.SIFTER.get());
        helper.setBlock(powered.east(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getLevel().hasNeighborSignal(helper.absolutePos(powered)), "a signal beside the Sifter reaches it");
        helper.succeed();
    }

    /**
     * Sifted dirt gives seeds, sticks, flint, clay, bones, the odd potato (green, now and then), carrot or bit of rotten
     * flesh, fallen leaves, now and then a spider eye and a little rice, corn kernels or an onion, with the rare Rare
     * Seed and pitcher pod - no boots, bowls or string - and about two blocks in three fall through as nothing.
     */
    private static void siftedDirtGivesFewKindsOfThing(GameTestHelper helper) {
        var allowed = Set.of(Items.WHEAT_SEEDS, Items.BEETROOT_SEEDS, Items.PUMPKIN_SEEDS, Items.MELON_SEEDS, Items.STICK,
                Items.FLINT, Items.CLAY_BALL, Items.BONE, Items.PITCHER_POD, ModCrops.BROWN_RICE.get(),
                Items.POTATO, Items.POISONOUS_POTATO, Items.CARROT, Items.ROTTEN_FLESH, Items.LEAF_LITTER, Items.SPIDER_EYE,
                ModWoods.RARE_SEED.get(), ModKitchen.CORN_KERNELS.get(),
                ModKitchen.ONION.get(), ModOil.SUNFLOWER_SEEDS.get());
        int empty = 0;
        int sticks = 0;
        Set<Item> odd = new HashSet<>();
        for (int i = 0; i < 4000; i++) {
            var got = SifterBlockEntity.sift(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), new ItemStack(Items.DIRT));
            if (got.isEmpty()) {
                empty++;
            }
            for (ItemStack stack : got) {
                if (!allowed.contains(stack.getItem())) {
                    odd.add(stack.getItem());
                }
                if (stack.is(Items.STICK)) {
                    sticks++;
                }
            }
        }
        check(helper, odd.isEmpty(), "dirt sifted into " + odd);
        check(helper, sticks > 150, "sticks turn up often (" + sticks + " in 4000)");
        check(helper, empty > 2350 && empty < 2800, "about two blocks in three give nothing, " + empty + " of 4000 did");
        helper.succeed();
    }

    /** A Hanging Planter is a glazed pot on Thin Rope, as it hangs, not on a bare string. */
    private static void hangingPlantersHangOnThinRope(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        for (String name : List.of("hanging_planter_green", "hanging_planter_pink")) {
            var key = ResourceKey.create(Registries.RECIPE, Nookworks.id(name));
            var recipe = recipes.byKey(key).orElseThrow().value();
            var ingredients = recipe.placementInfo().ingredients();
            check(helper, ingredients.stream().anyMatch(i -> i.test(new ItemStack(ModItems.THIN_ROPE.get()))),
                    name + " takes thin rope");
            check(helper, ingredients.stream().noneMatch(i -> i.test(new ItemStack(Items.STRING))), name + " still takes string");
        }
        helper.succeed();
    }

    /**
     * Three lots put in one after another - a lot of stone, then a little dirt, then some salt (ours) - come out of each
     * sort order where it says: most first; the game's item order (vanilla before mods); mod by mod and by name within;
     * last added first. Each kind of item is still one run of full slots.
     */
    private static void barrelSortsInTheOrderChosen(GameTestHelper helper) {
        long[] now = {100};
        var inventory = new StorageInventory(StorageTier.GOLD, () -> {}, () -> now[0]);
        Item salt = ModItems.SALT.get();
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(Items.STONE, 300), false, null);
        now[0] = 200;
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(Items.DIRT, 10), false, null);
        now[0] = 300;
        ItemUtil.insertItemReturnRemaining(inventory, new ItemStack(salt, 50), false, null);
        var expected = Map.of(
                SortOrder.COUNT, List.of(Items.STONE, salt, Items.DIRT),
                SortOrder.ID, List.of(Items.STONE, Items.DIRT, salt),
                SortOrder.MOD, List.of(Items.DIRT, Items.STONE, salt),
                SortOrder.RECENT, List.of(salt, Items.DIRT, Items.STONE));
        for (var order : SortOrder.values()) {
            inventory.sort(order);
            List<Item> runs = new ArrayList<>();
            int filled = 0;
            for (int i = 0; i < inventory.size(); i++) {
                if (inventory.getAmountAsInt(i) == 0) {
                    continue;
                }
                check(helper, i == filled++, order + ": a gap before slot " + i);
                Item item = inventory.getResource(i).getItem();
                if (runs.isEmpty() || runs.getLast() != item) {
                    runs.add(item);
                }
            }
            check(helper, runs.equals(expected.get(order)), order + " put them " + runs);
            check(helper, filled == 4, order + ": 300 stone take two slots, dirt and salt one each, " + filled + " used");
        }
        // Sorting keeps when each lot went in: RECENT again after the others still puts the salt first.
        inventory.sort(SortOrder.RECENT);
        check(helper, inventory.getResource(0).is(salt) && inventory.lastAdded(0) == 300, "the salt keeps its time through the sorts");
        helper.succeed();
    }

    /** The order button moves a barrel through the orders both ways, Sort uses it, and the barrel saves it. */
    private static void barrelKeepsItsSortOrder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = (StorageMenu) barrel.createMenu(1, player.getInventory(), player);
        check(helper, menu.sortOrder() == SortOrder.ID, "a new barrel sorts by item ID");
        menu.clickMenuButton(player, StorageMenu.BUTTON_ORDER_NEXT);
        check(helper, barrel.sortOrder() == SortOrder.MOD, "a click moves on to by mod");
        menu.clickMenuButton(player, StorageMenu.BUTTON_ORDER_BACK);
        menu.clickMenuButton(player, StorageMenu.BUTTON_ORDER_BACK);
        check(helper, barrel.sortOrder() == SortOrder.COUNT, "two right-clicks go back to by count");
        barrel.inventory().set(0, ItemResource.of(Items.DIRT), 5);
        barrel.inventory().set(1, ItemResource.of(Items.STONE), 60);
        menu.clickMenuButton(player, StorageMenu.BUTTON_SORT);
        check(helper, barrel.inventory().getResource(0).is(Items.STONE), "Sort sorted by count: the stone first");
        menu.removed(player);

        var level = helper.getLevel();
        var saved = barrel.saveWithoutMetadata(level.registryAccess());
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        StorageBlockEntity again = helper.getBlockEntity(pos, StorageBlockEntity.class);
        try (var reporter = new ProblemReporter.ScopedCollector(again.problemPath(), LogUtils.getLogger())) {
            again.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
        }
        check(helper, again.sortOrder() == SortOrder.COUNT, "the order is saved with the barrel");
        check(helper, again.inventory().lastAdded(1) == barrel.inventory().lastAdded(1), "and when each slot was last added to");
        helper.succeed();
    }

    private static ServerPlayer survivor(GameTestHelper helper, Vec3 at) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL); // creative players keep what they use
        player.snapTo(helper.absoluteVec(at));
        return player;
    }

    private static GlobalPos global(GameTestHelper helper, BlockPos pos) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    /**
     * An ender pearl used on a lodestone - through the game's own right-click - becomes a Lode Pearl bound to it, one a
     * click, and is not thrown; two bound to the same lodestone stack, and one bound elsewhere does not.
     */
    private static void lodePearlsBindAndStack(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(2, 1, 2);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(3.5, 1, 3.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL, 3));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(lodestone)), Direction.UP, helper.absolutePos(lodestone), false);
        for (int i = 0; i < 2; i++) {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        }
        check(helper, player.getMainHandItem().is(Items.ENDER_PEARL) && player.getMainHandItem().getCount() == 1, "two pearls went, one a click");
        int bound = 0;
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ModItems.LODE_PEARL.get())) {
                check(helper, stack.getCount() == 2, "the two Lode Pearls share a stack, " + stack.getCount());
                check(helper, LodePearlItem.lodestone(stack).equals(Optional.of(global(helper, lodestone))),
                        "bound to this lodestone");
                bound++;
            }
        }
        check(helper, bound == 1, "one stack of Lode Pearls, found " + bound);
        check(helper, helper.getLevel().getEntitiesOfClass(ThrownLodePearl.class,
                new AABB(helper.absolutePos(lodestone)).inflate(8)).isEmpty()
                && helper.getLevel().getEntitiesOfClass(ThrownEnderpearl.class,
                new AABB(helper.absolutePos(lodestone)).inflate(8)).isEmpty(), "nothing was thrown");
        ItemStack elsewhere = LodePearlItem.bound(global(helper, new BlockPos(5, 1, 5)));
        check(helper, !ItemStack.isSameItemSameComponents(elsewhere, LodePearlItem.bound(global(helper, lodestone))),
                "a pearl bound to another lodestone does not stack with them");
        helper.succeed();
    }

    /** {@code player} setting {@code stack} - a lodestone - down on top of {@code floor}, as a click with it does. */
    private static void setDown(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos floor) {
        helper.setBlock(floor, Blocks.STONE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(floor);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false));
    }

    /**
     * Set down, a lodestone is named at once: after where it stands (a second there numbered on), or by the name its
     * item was given at an anvil.
     */
    private static void lodestonesNamedAsSetDown(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(3.5, 2, 3.5));
        setDown(helper, player, new ItemStack(Items.LODESTONE), new BlockPos(1, 1, 1));
        setDown(helper, player, new ItemStack(Items.LODESTONE), new BlockPos(1, 1, 5));
        var first = LodestoneNames.of(global(helper, new BlockPos(1, 2, 1)));
        var second = LodestoneNames.of(global(helper, new BlockPos(1, 2, 5)));
        check(helper, first.isPresent() && second.isPresent(), "both named as they were set down: " + first + " / " + second);
        check(helper, !first.get().equals(second.get()), "and not alike: " + first.get() + " / " + second.get());
        ItemStack named = new ItemStack(Items.LODESTONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Home"));
        setDown(helper, player, named, new BlockPos(5, 1, 1));
        check(helper, LodestoneNames.of(global(helper, new BlockPos(5, 2, 1))).map(Component::getString).equals(Optional.of("Home")),
                "one named at an anvil keeps that name");
        helper.succeed();
    }

    /**
     * Renamed, a lodestone's pearls are called after the new name; cleared, it has a name of its own again - the same
     * one each time it is cleared, not counted on (it was "Plains 2", then "Plains 3"). Only a lodestone, only
     * within reach, and not by a spectator. A lodestone with no name yet (from before names) gets one at an
     * empty-handed click, as the box to rename it opens; but with a compass in the other hand, the click is the
     * compass's, as in vanilla: it points at the lodestone, and no box opens.
     */
    private static void lodestoneRenamed(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(2, 1, 2);
        helper.setBlock(lodestone, Blocks.LODESTONE); // as from before names: none
        GlobalPos at = global(helper, lodestone);
        check(helper, LodestoneNames.of(at).isEmpty(), "set by a command: no name yet");
        ServerPlayer player = survivor(helper, new Vec3(3.5, 1, 3.5));
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(lodestone)), Direction.UP, helper.absolutePos(lodestone), false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        check(helper, LodestoneNames.of(at).isPresent(), "an empty-handed click names it, to show in the box");
        ItemStack pearl = LodePearlItem.bound(at);
        check(helper, LodestoneNames.rename(player, helper.absolutePos(lodestone), "  Kitchen "), "renamed");
        check(helper, LodestoneNames.of(at).map(Component::getString).equals(Optional.of("Kitchen")), "to what was typed, trimmed");
        check(helper, pearl.getHoverName().getContents() instanceof TranslatableContents name
                && name.getKey().equals("item.nookworks.lode_pearl.named") && name.getArgs().length == 1
                && name.getArgs()[0] instanceof Component arg && arg.getString().equals("Kitchen"), "its pearl is called after it: " + pearl.getHoverName());
        check(helper, LodestoneNames.rename(player, helper.absolutePos(lodestone), "x".repeat(80))
                && LodestoneNames.of(at).get().getString().length() == LodestoneNames.MAX_LENGTH, "a long name is cut to an anvil's length");
        check(helper, LodestoneNames.rename(player, helper.absolutePos(lodestone), "")
                && LodestoneNames.of(at).get().getContents() instanceof TranslatableContents, "cleared: a name of its own again");
        Component own = LodestoneNames.of(at).get();
        check(helper, LodestoneNames.rename(player, helper.absolutePos(lodestone), "") && LodestoneNames.of(at).get().equals(own),
                "cleared again, the same name, not counted on: " + LodestoneNames.of(at).get());
        player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
        check(helper, !LodestoneNames.rename(player, helper.absolutePos(lodestone), "Spectated"), "a spectator may not rename it");
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.COMPASS));
        var result = player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit);
        check(helper, !result.consumesAction(), "with a compass in the other hand, the empty hand's click does nothing: " + result);
        player.gameMode.useItemOn(player, helper.getLevel(), player.getOffhandItem(), InteractionHand.OFF_HAND, hit);
        check(helper, player.getOffhandItem().has(DataComponents.LODESTONE_TRACKER), "and the compass in the other hand points at the lodestone");
        // a block in the other hand is set against it, as vanilla does; a shield there, held for good, does not stop a rename
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TORCH));
        check(helper, !player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit).consumesAction(),
                "with a torch in the other hand, the empty hand's click leaves it to the torch");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.WATER_BUCKET));
        check(helper, !player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit).consumesAction(),
                "with a bucket in the other hand, the click is left to it");
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        check(helper, player.gameMode.useItemOn(player, helper.getLevel(), ItemStack.EMPTY, InteractionHand.MAIN_HAND, hit).consumesAction(),
                "with a shield in the other hand, the empty hand's click still opens the box");
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        check(helper, !LodestoneNames.rename(player, helper.absolutePos(new BlockPos(4, 1, 4)), "Nowhere"), "not where no lodestone is");
        player.teleportTo(player.getX() + 40, player.getY(), player.getZ());
        check(helper, !LodestoneNames.rename(player, helper.absolutePos(lodestone), "Far"), "not from far off");
        helper.succeed();
    }

    /** Broken by a player, a lodestone's name is forgotten. */
    private static void lodestoneForgotten(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(3.5, 2, 3.5));
        setDown(helper, player, new ItemStack(Items.LODESTONE), new BlockPos(2, 1, 2));
        GlobalPos at = global(helper, new BlockPos(2, 2, 2));
        check(helper, LodestoneNames.of(at).isPresent(), "named as it was set down");
        player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(2, 2, 2)));
        check(helper, LodestoneNames.of(at).isEmpty(), "taken away, forgotten");
        helper.succeed();
    }

    /** Thrown across the room at one heart, it brings its thrower to stand on the lodestone, unhurt. */
    private static void aLodePearlTakesYouHome(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(1, 1, 1);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        player.setHealth(2.0F);
        throwAt(helper, player, LodePearlItem.bound(global(helper, lodestone)), new Vec3(6.5, 3, 6.5));
        helper.succeedWhen(() -> {
            check(helper, player.blockPosition().equals(helper.absolutePos(lodestone.above())), "the thrower is at " + player.blockPosition());
            check(helper, player.getHealth() == 2.0F && player.isAlive(), "and unhurt, at " + player.getHealth());
        });
    }

    /** Thrown into the void, it takes its thrower home once it has fallen out of the world. */
    private static void aLodePearlInTheVoid(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(1, 1, 1);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        Vec3 deep = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
        throwAt(helper, player, LodePearlItem.bound(global(helper, lodestone)),
                helper.relativeVec(new Vec3(deep.x, helper.getLevel().getMinY() - 70, deep.z)));
        helper.succeedWhen(() -> check(helper, player.blockPosition().equals(helper.absolutePos(lodestone.above())),
                "the thrower is at " + player.blockPosition()));
    }

    /**
     * Bound to a lodestone that is no longer there: nobody moves, and the pearl comes back to its thrower as a plain
     * ender pearl - into their hands, not left where it landed (in lava, it was lost).
     */
    private static void aLodePearlWithoutItsLodestone(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        BlockPos was = player.blockPosition();
        throwAt(helper, player, LodePearlItem.bound(global(helper, new BlockPos(1, 1, 1))), new Vec3(3.5, 3, 3.5));
        helper.succeedWhen(() -> {
            check(helper, player.getInventory().countItem(Items.ENDER_PEARL) == 1, "an ender pearl back in the thrower's hands");
            helper.assertItemEntityNotPresent(Items.ENDER_PEARL, new BlockPos(3, 1, 3), 2.0);
            check(helper, player.blockPosition().equals(was), "the thrower stayed put");
        });
    }

    /** Thrown into the void, bound to a lodestone no longer there: the plain ender pearl comes back all the same. */
    private static void aLodePearlWithoutItsLodestoneInTheVoid(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        Vec3 deep = helper.absoluteVec(new Vec3(6.5, 1, 6.5));
        throwAt(helper, player, LodePearlItem.bound(global(helper, new BlockPos(1, 1, 1))),
                helper.relativeVec(new Vec3(deep.x, helper.getLevel().getMinY() - 70, deep.z)));
        helper.succeedWhen(() -> check(helper, player.getInventory().countItem(Items.ENDER_PEARL) == 1,
                "an ender pearl back in the thrower's hands"));
    }

    /**
     * A cobweb over the lodestone (fire, lava or a portal alike: nothing solid, all harmful) is not where the pearl
     * sets its thrower down: they arrive beside the lodestone instead, as a respawn anchor would put them.
     */
    private static void aLodePearlSetsYouDownClearOfHarm(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(3, 1, 3);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        helper.setBlock(lodestone.above(), Blocks.COBWEB);
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        throwAt(helper, player, LodePearlItem.bound(global(helper, lodestone)), new Vec3(6.5, 3, 6.5));
        BlockPos top = helper.absolutePos(lodestone.above());
        helper.succeedWhen(() -> {
            check(helper, player.blockPosition().distManhattan(top) <= 3, "the thrower came home, to " + player.blockPosition());
            check(helper, !player.blockPosition().equals(top), "and not into the cobweb on the lodestone");
        });
    }

    /**
     * A Lode Pearl asks as any teleport asks: land claimed by someone else keeps its thrower out, as it keeps out an ender
     * pearl; the land's owner goes home (compat.md promises the claim mods this).
     */
    private static void aClaimKeepsALodePearlOut(GameTestHelper helper) {
        BlockPos lodestone = new BlockPos(3, 1, 3);
        helper.setBlock(lodestone, Blocks.LODESTONE);
        ServerPlayer player = survivor(helper, new Vec3(6.5, 1, 6.5));
        BlockPos was = player.blockPosition();
        ItemStack pearl = LodePearlItem.bound(global(helper, lodestone));
        ThrownLodePearl.Trip[] trip = new ThrownLodePearl.Trip[1];
        BlockTests.underClaim(UUID.randomUUID(), () -> trip[0] = ThrownLodePearl.send(player, pearl));
        check(helper, trip[0] == ThrownLodePearl.Trip.REFUSED && player.blockPosition().equals(was),
                "someone else's claim keeps the thrower out: " + trip[0] + ", at " + player.blockPosition());
        BlockTests.underClaim(player.getUUID(), () -> trip[0] = ThrownLodePearl.send(player, pearl));
        check(helper, trip[0] == ThrownLodePearl.Trip.HOME && player.blockPosition().distManhattan(helper.absolutePos(lodestone)) <= 3,
                "their own lets them home: " + trip[0]);
        helper.succeed();
    }

    private static void throwAt(GameTestHelper helper, ServerPlayer player, ItemStack pearl, Vec3 at) {
        var thrown = new ThrownLodePearl(helper.getLevel(), player, pearl);
        Vec3 where = helper.absoluteVec(at);
        thrown.setPos(where.x, where.y, where.z);
        thrown.setDeltaMovement(0, -0.5, 0);
        helper.getLevel().addFreshEntity(thrown);
    }

    /** Soap in the crafting grid washes a Lode Pearl back to an ender pearl, and the bar comes back one scrub worn. */
    private static void soapWashesALodePearlBack(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE,
                Nookworks.id("ender_pearl_from_lode_pearl"));
        var recipe = (CraftingRecipe) helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow().value();
        var input = CraftingInput.of(2, 1, List.of(
                LodePearlItem.bound(global(helper, new BlockPos(1, 1, 1))), new ItemStack(ModOil.SOAP.get())));
        check(helper, recipe.matches(input, helper.getLevel()), "a bound pearl and soap make the recipe");
        check(helper, recipe.assemble(input).is(Items.ENDER_PEARL), "and it gives an ender pearl");
        var left = recipe.getRemainingItems(input);
        check(helper, left.get(1).is(ModOil.SOAP.get()) && left.get(1).getDamageValue() == 1,
                "the soap comes back a scrub worn, got " + left.get(1));
        helper.succeed();
    }

    /**
     * Dark Matter in a corner, a torch in the far corner, outside its disc: from the layer under the disc to three above
     * it the torch's light is gone (light 0, dark enough for a monster), just outside it and four layers up it shines. A torch set down inside
     * the disc lifts all of the dark, and taking it away brings the dark back; taking the Dark Matter away gives the
     * light back at once.
     */
    private static void darkMatterPutsOutTheLight(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos matter = new BlockPos(0, 1, 0);
        BlockPos inside = new BlockPos(3, 1, 3);   // six steps from the Dark Matter
        BlockPos outside = new BlockPos(4, 1, 4);  // eight
        BlockPos lamp = new BlockPos(2, 1, 2);
        BlockPos torch = new BlockPos(6, 1, 6);
        helper.setBlock(torch, Blocks.TORCH);
        helper.setBlock(matter, ModBlocks.DARK_MATTER.get());
        IntSupplier blockLightInside = () -> level.getBrightness(LightLayer.BLOCK, helper.absolutePos(inside));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    // the light worked out first, in time and not in ticks (see settleLight); then the torch's own light
                    // just outside (14 less 4 steps: an arena nearby, left as its test ended, can light it a little)
                    settleLight(helper, torch);
                    check(helper, level.getBrightness(LightLayer.BLOCK, helper.absolutePos(outside)) >= 10,
                            "the torch's light has reached just outside the disc");
                    // and the disc in place, as soon as the block is (its block entity starts it on the tick after)
                    check(helper, blockLightInside.getAsInt() == 0, "inside the disc the torch's light is gone, " + blockLightInside.getAsInt());
                })
                .thenExecute(() -> {
                    check(helper, level.getBrightness(LightLayer.SKY, helper.absolutePos(inside)) == 0, "and the sky's");
                    check(helper, level.getRawBrightness(helper.absolutePos(inside.above(3)), 0) == 0, "and three layers up, an enderman's eyes, too");
                    check(helper, level.getBrightness(LightLayer.BLOCK, helper.absolutePos(inside.above(4))) > 0,
                            "but four layers up the torch shines");
                    check(helper, Monster.isDarkEnoughToSpawn(level, helper.absolutePos(inside), level.getRandom()),
                            "dark enough for a monster to spawn");
                    helper.setBlock(lamp, Blocks.TORCH);
                })
                .thenWaitUntil(() -> check(helper, blockLightInside.getAsInt() > 0, "a torch inside the disc lifts the dark"))
                .thenExecute(() -> {
                    check(helper, !helper.getBlockState(matter).getValue(DarkMatterBlock.DARKENING), "and dulls the block");
                    helper.setBlock(lamp, Blocks.AIR);
                })
                .thenWaitUntil(() -> check(helper, blockLightInside.getAsInt() == 0, "with that torch gone the dark falls again"))
                .thenExecute(() -> helper.setBlock(matter, Blocks.AIR))
                .thenWaitUntil(() -> check(helper, blockLightInside.getAsInt() > 0, "without the Dark Matter the light is back"))
                .thenExecute(() -> check(helper, !Monster.isDarkEnoughToSpawn(level, helper.absolutePos(inside), level.getRandom()),
                        "too light for a monster again"))
                .thenSucceed();
    }

    /**
     * Cooked food cooked again - in a furnace, a smoker, over a campfire - comes out a Charred Lump; raw food still just
     * cooks. Four lumps round an amethyst block make Dark Matter.
     */
    private static void cookedFoodCharsIntoDarkMatter(GameTestHelper helper) {
        var level = helper.getLevel();
        var lump = ModItems.CHARRED_LUMP.get();
        for (var type : List.of(RecipeType.SMELTING, RecipeType.SMOKING,
                RecipeType.CAMPFIRE_COOKING)) {
            for (Item cooked : List.of(Items.COOKED_BEEF, Items.COOKED_SALMON, Items.BAKED_POTATO)) {
                var input = new SingleRecipeInput(new ItemStack(cooked));
                var recipe = level.recipeAccess().getRecipeFor(type, input, level);
                check(helper, recipe.isPresent() && recipe.get().value().assemble(input).is(lump), cooked + " in " + type + " chars");
            }
            var raw = new SingleRecipeInput(new ItemStack(Items.BEEF));
            check(helper, level.recipeAccess().getRecipeFor(type, raw, level).map(r -> r.value().assemble(raw).is(Items.COOKED_BEEF)).orElse(false),
                    "raw beef still just cooks in " + type);
        }
        ItemStack c = new ItemStack(lump);
        ItemStack a = new ItemStack(Items.AMETHYST_BLOCK);
        var grid = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, c, ItemStack.EMPTY, c, a, c, ItemStack.EMPTY, c, ItemStack.EMPTY));
        var made = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, grid, level);
        check(helper, made.isPresent() && made.get().value().assemble(grid).is(ModItems.DARK_MATTER.get()),
                "four lumps round an amethyst block make Dark Matter");
        helper.succeed();
    }

    /**
     * Pressed near a barrel's corner a label sits there; pressed near the corner while sneaking it goes in the very
     * middle of the face, so a wall of barrels labels in rows.
     */
    private static void aLabelSneakingGoesInTheMiddle(GameTestHelper helper) {
        ServerPlayer player = survivor(helper, new Vec3(2.5, 1, 0.5));
        BlockPos[] barrels = {new BlockPos(2, 1, 2), new BlockPos(4, 1, 2)};
        for (BlockPos barrel : barrels) {
            helper.setBlock(barrel, Blocks.BARREL);
        }
        Vec3[] centres = new Vec3[2];
        for (int i = 0; i < 2; i++) {
            player.setShiftKeyDown(i == 1);
            BlockPos at = helper.absolutePos(barrels[i]);
            Vec3 corner = Vec3.atCenterOf(at).add(0.35, 0.35, -0.5); // near the top corner of its north face
            ItemStack label = LabelItem.of(new ItemStack(Items.STONE));
            player.setItemInHand(InteractionHand.MAIN_HAND, label);
            var result = label.getItem().onItemUseFirst(label, new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(corner, Direction.NORTH, at, false)));
            check(helper, result.consumesAction(), "the label went on barrel " + i);
            var stuck = helper.getLevel().getEntitiesOfClass(LabelEntity.class, new AABB(at).inflate(0.5));
            check(helper, stuck.size() == 1, "one label on barrel " + i + ", found " + stuck.size());
            centres[i] = stuck.getFirst().getBoundingBox().getCenter().subtract(Vec3.atCenterOf(at));
        }
        check(helper, Math.abs(centres[0].x) > 0.1 && centres[0].y > 0.1, "pressed near the corner, it sits by the corner: " + centres[0]);
        check(helper, Math.abs(centres[1].x) < 1.0E-4 && Math.abs(centres[1].y) < 1.0E-4, "sneaking, it goes in the middle: " + centres[1]);
        helper.succeed();
    }

    /**
     * A shift-clicked cobblestone goes into the Printing Press's sample slot only while labels are what it prints (the
     * labels page); set to a poster, or to nothing, the press does not take it, so it moves from the hotbar to the
     * inventory above, as on a furnace's screen.
     */
    private static void thePressTakesASampleOnItsLabelsPage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pos, PrintingPressBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        var menu = (PrintingPressMenu) press.createMenu(1, player.getInventory(), player);
        int hotbar0 = PrintingPressBlockEntity.SLOTS + 27; // not the menu's end: an add-on's fitting may follow
        List<Identifier> designs = menu.designs();
        int poster = designs.indexOf(designs.get(2)) + 1; // labels and a photo come first
        menu.clickMenuButton(player, poster);
        menu.quickMoveStack(player, hotbar0);
        check(helper, press.getItem(PrintingPressBlockEntity.SAMPLE).isEmpty(), "set to a poster, it takes no sample");
        check(helper, player.getInventory().getItem(9).getCount() == 5, "but moves the cobblestone up from the hotbar");
        menu.clickMenuButton(player, designs.indexOf(PrintingPressBlockEntity.LABEL) + 1);
        check(helper, menu.printingLabels(), "labels chosen");
        menu.quickMoveStack(player, PrintingPressBlockEntity.SLOTS); // the inventory's slot 9
        check(helper, press.getItem(PrintingPressBlockEntity.SAMPLE).is(Items.COBBLESTONE)
                && press.getItem(PrintingPressBlockEntity.SAMPLE).getCount() == 1, "on the labels page one goes in as the sample");
        check(helper, player.getInventory().getItem(9).getCount() == 4, "and the rest stays where it was");
        helper.succeed();
    }

    /**
     * Two vines on a wall, one cut with the Fine Shears - through the game's own right-click, which wears the shears - and
     * both given the same many random ticks: the cut one spreads nowhere, the other does. Breaking the vine forgets the cut.
     * (Vanilla's shears leave a vine growing: R8VinesTests.)
     */
    private static void aVineCutWithFineShearsStopsSpreading(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos[] vines = {new BlockPos(1, 5, 2), new BlockPos(5, 5, 2)};
        for (BlockPos vine : vines) {
            for (int x = -1; x <= 1; x++) {
                for (int y = -3; y <= 1; y++) {
                    helper.setBlock(vine.offset(x, y, 1), Blocks.STONE); // a wall behind, to the south
                }
            }
            helper.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
        }
        ServerPlayer player = survivor(helper, new Vec3(1.5, 1, 0.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WOOL_SHEARS.get()));
        BlockPos cut = helper.absolutePos(vines[0]);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cut), Direction.NORTH, cut, false));
        check(helper, TrimmedVines.isTrimmed(level, cut), "the vine is cut");
        check(helper, player.getMainHandItem().getDamageValue() == 1, "and the shears wear");
        int[] grown = new int[2];
        for (int i = 0; i < 2; i++) {
            BlockPos at = helper.absolutePos(vines[i]);
            for (int tick = 0; tick < 3000; tick++) {
                BlockState state = level.getBlockState(at);
                if (state.is(Blocks.VINE)) {
                    state.randomTick(level, at, level.getRandom());
                }
            }
            for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -4, -1), at.offset(2, 2, 1))) {
                if (!p.equals(at) && level.getBlockState(p).is(Blocks.VINE)) {
                    grown[i]++;
                }
            }
        }
        check(helper, grown[0] == 0, "the cut vine spread to " + grown[0] + " blocks");
        check(helper, grown[1] > 0, "the uncut one should have spread");
        player.gameMode.destroyBlock(cut);
        check(helper, !TrimmedVines.isTrimmed(level, cut), "broken, the cut is forgotten");

        // cut again, then gone with no word of it (burned, washed away): a vine growing down into the place is a new one
        helper.setBlock(vines[0], Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cut), Direction.NORTH, cut, false));
        check(helper, TrimmedVines.isTrimmed(level, cut), "cut again");
        level.setBlock(cut, Blocks.AIR.defaultBlockState(), 2);
        helper.setBlock(vines[0].above(), Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
        BlockPos above = cut.above();
        for (int tick = 0; tick < 6000 && !level.getBlockState(cut).is(Blocks.VINE); tick++) {
            level.getBlockState(above).randomTick(level, above, level.getRandom());
        }
        check(helper, level.getBlockState(cut).is(Blocks.VINE), "the vine above should have grown down");
        check(helper, !TrimmedVines.isTrimmed(level, cut), "the vine grown into the place of the cut one is not cut");

        // in adventure mode shears say nothing about vines: no cut, no wear
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        BlockPos other = helper.absolutePos(vines[1]);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(other), Direction.NORTH, other, false));
        check(helper, !TrimmedVines.isTrimmed(level, other), "not cut in adventure mode");
        helper.succeed();
    }

    /**
     * The Velarium Storage Barrel, a Netherite one and a Block of Velarium: 15 rows of slots holding 1,024 stacks each,
     * scrolling like the rest, and how full it is reaches the screen whole though it is far more than a data slot's short.
     */
    private static void velariumBarrelHoldsAWarehouseOfWarehouses(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.VELARIUM_STORAGE.get());
        StorageBlockEntity barrel = helper.getBlockEntity(pos, StorageBlockEntity.class);
        var inventory = barrel.inventory();
        check(helper, inventory.size() == 135 && inventory.tier().rows() == 15, "15 rows of 9, got " + inventory.size());
        check(helper, inventory.tier().slotCapacity(64) == 65_536, "a slot holds 1,024 stacks");
        for (int i = 0; i < 100; i++) {
            inventory.set(i, ItemResource.of(Items.COBBLESTONE), 65_536);
        }
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var server = (StorageMenu) barrel.createMenu(1, player.getInventory(), player);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        buf.writeEnum(StorageTier.VELARIUM);
        var client = StorageMenu.client(2, player.getInventory(), buf);
        server.setSynchronizer(relay(client));
        check(helper, client.storedStacks() == 102_400, "the screen shows 102,400 stacks, got " + client.storedStacks());
        server.clickMenuButton(player, server.maxScroll());
        server.broadcastChanges();
        same(helper, server, client, "scrolled to the bottom");
        server.removed(player);
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(
                Registries.RECIPE, Nookworks.id("velarium_storage_barrel")));
        check(helper, recipe.isPresent(), "it has a recipe");
        helper.succeed();
    }

    /**
     * The Electric Furnace smelts by the furnace's recipes in half their time on 40 FE/t: raw iron (200 ticks in a
     * furnace) is an iron ingot after 100 ticks and 4,000 FE, it goes down into the barrel under it, and the experience
     * is kept as liquid. Without power it stands, and says so; what it cannot smelt it does not take.
     */
    private static void theElectricFurnaceSmeltsOnPower(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.BARREL);
        helper.setBlock(pos, ModBlocks.ELECTRIC_FURNACE.get());
        var furnace = helper.getBlockEntity(pos, ElectricFurnaceBlockEntity.class);
        check(helper, !furnace.canPlaceItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.STICK)),
                "it will not take what a furnace cannot smelt");
        furnace.setItem(ElectricFurnaceBlockEntity.INPUT, new ItemStack(Items.RAW_IRON, 2));
        BlockPos at = helper.absolutePos(pos);
        ElectricFurnaceBlockEntity.serverTick(level, at, helper.getBlockState(pos), furnace);
        check(helper, furnace.guiStatus() == MachineStatus.NO_POWER, "without power it waits, " + furnace.guiStatus());
        furnace.energy().set(ElectricFurnaceBlockEntity.CAPACITY);
        for (int tick = 0; tick < 100; tick++) {
            ElectricFurnaceBlockEntity.serverTick(level, at, helper.getBlockState(pos), furnace);
        }
        var below = (BarrelBlockEntity) level.getBlockEntity(at.below());
        check(helper, below.countItem(Items.IRON_INGOT) == 1, "one ingot in the barrel after 100 ticks, " + below.countItem(Items.IRON_INGOT));
        check(helper, furnace.energyStored() == ElectricFurnaceBlockEntity.CAPACITY - 4_000,
                "it used 4,000 FE, " + (ElectricFurnaceBlockEntity.CAPACITY - furnace.energyStored()));
        check(helper, furnace.experienceStored() > 0, "and kept the experience as liquid");
        check(helper, helper.getBlockState(pos).getValue(ElectricFurnaceBlock.LIT), "lit while it works");
        helper.succeed();
    }

    /**
     * A Velarium barrel of a thousand stacks and more taken away by something other than a hand leaves as one barrel
     * item holding all of it - no flood of item entities, and set down again it holds them as before - however it goes:
     * a block breaker with its drops, one without, its contents read for something else first in the same tick (a
     * creative pick: that once lost everything), or its drops worked out after the block has gone, as a player's
     * break does (that would have dropped a second barrel). The item never despawns, and fire, lava, explosions and
     * cactus spare it.
     */
    private static void aBarrelTooFullToBreakLeavesWhole(GameTestHelper helper) {
        var level = helper.getLevel();
        String[] ways = {"with drops", "without drops", "read first, then without drops", "drops worked out after"};
        for (int i = 0; i < ways.length; i++) {
            BlockPos spot = new BlockPos(1 + i % 2 * 3, 2, 1 + i / 2 * 3);
            helper.setBlock(spot, ModBlocks.VELARIUM_STORAGE.get());
            var barrel = helper.getBlockEntity(spot, StorageBlockEntity.class);
            barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 1024 * 64);
            barrel.inventory().set(1, ItemResource.of(Items.DIAMOND), 5_000);
            check(helper, barrel.tooFullToBreak(), "a hand could not break it");
            BlockPos at = helper.absolutePos(spot);
            switch (i) {
                case 0 -> level.destroyBlock(at, true);
                case 1 -> level.destroyBlock(at, false);
                case 2 -> {
                    barrel.collectComponents();
                    level.destroyBlock(at, false);
                }
                default -> {
                    BlockState state = level.getBlockState(at);
                    level.removeBlock(at, false);
                    Block.dropResources(state, level, at, barrel, null, ItemStack.EMPTY);
                }
            }
            var items = helper.getEntities(EntityTypes.ITEM, spot, 1.5);
            check(helper, items.size() == 1, ways[i] + ": one item comes out, not " + items.size());
            ItemStack whole = items.get(0).getItem();
            check(helper, whole.is(ModBlocks.VELARIUM_STORAGE.get().asItem()) && StorageItem.holdsSomething(whole),
                    ways[i] + ": the barrel itself, holding its contents: " + whole);
            check(helper, items.get(0).getAge() < 0, ways[i] + ": it never despawns");
            var sources = level.damageSources();
            check(helper, !whole.canBeHurtBy(sources.inFire()) && !whole.canBeHurtBy(sources.lava())
                    && !whole.canBeHurtBy(sources.explosion(null, null)) && !whole.canBeHurtBy(sources.cactus()),
                    ways[i] + ": fire, lava, explosions and cactus spare it");
            check(helper, StorageSpill.pending(level, at) == 0, ways[i] + ": and nothing waits to spill");
            if (i == 0) {
                BlockPos again = new BlockPos(1, 2, 4);
                helper.setBlock(again, ModBlocks.VELARIUM_STORAGE.get());
                var placed = helper.getBlockEntity(again, StorageBlockEntity.class);
                placed.applyComponentsFromItemStack(whole);
                check(helper, placed.inventory().getAmountAsInt(0) == 1024 * 64 && placed.inventory().getAmountAsInt(1) == 5_000,
                        "set down again it holds them all: " + placed.inventory().getAmountAsInt(0) + ", " + placed.inventory().getAmountAsInt(1));
            }
            items.forEach(Entity::discard);
        }
        helper.succeed();
    }

    /**
     * A creative player can hand the server any item: a Mob Bottle made up to hold a command block minecart, or a
     * wither, lets nothing out, and data only an operator may set is left out for anyone else.
     */
    private static void aMadeUpMobBottleLetsNothingOut(GameTestHelper helper) {
        var level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        for (var type : List.of(EntityTypes.COMMAND_BLOCK_MINECART, EntityTypes.WITHER)) {
            ItemStack bottle = MobBottleItem.filled(type);
            CompoundTag data = new CompoundTag();
            data.putString("Command", "say hello");
            bottle.set(ModDataComponents.CAPTURED_DATA.get(), CustomData.of(data));
            player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
            var result = bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false)));
            check(helper, !result.consumesAction(), type + ": the bottle refused, " + result);
            check(helper, level.getEntitiesOfClass(Entity.class, new AABB(floor).inflate(3),
                    e -> e.getType() == type).isEmpty(), "and no " + type + " came out");
        }
        helper.succeed();
    }

    /**
     * A turn on a sunflower seed, then it taken off and pumpkin seeds put on (two turns to press): the pumpkin seeds
     * start from nothing, and one turn does not finish them on the sunflower seed's work.
     */
    private static void theOilPressStartsOverForOtherSeeds(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModOil.OIL_PRESS.get());
        var press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get()));
        press.turn(helper.getLevel());
        press.removeItemNoUpdate(OilPressBlockEntity.INPUT); // taken off: the slot stands empty
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(Items.PUMPKIN_SEEDS));
        press.turn(helper.getLevel());
        check(helper, press.getItem(OilPressBlockEntity.INPUT).is(Items.PUMPKIN_SEEDS) && press.stored() == 0,
                "one turn on the seeds pressed nothing yet, " + press.stored() + " mB");
        press.turn(helper.getLevel());
        check(helper, press.stored() == 50, "the second did: 50 mB, " + press.stored());
        helper.succeed();
    }

    /**
     * Both workbenches are crafting tables: the crafting table's own menu (so the crafting screen, JEI and the recipe
     * book treat them as one), crafting in it, kept open at the bench and closed once the bench is gone, faced toward
     * whoever set it down, and listed under the common crafting-table tags other mods look for.
     */
    private static void workbenchesAreCraftingTables(GameTestHelper helper) {
        var level = helper.getLevel();
        int x = 1;
        for (var bench : List.of(ModBlocks.IRON_WORKBENCH.get(), ModBlocks.BRASS_WORKBENCH.get())) {
            BlockPos pos = new BlockPos(x, 2, 2);
            x += 3;
            helper.setBlock(pos, bench);
            ServerPlayer player = MockPlayers.inLevel(helper);
            Vec3 near = helper.absoluteVec(Vec3.atCenterOf(pos).add(0, 0, 2));
            player.setPos(near.x, near.y, near.z);
            var provider = helper.getBlockState(pos).getMenuProvider(level, helper.absolutePos(pos));
            check(helper, provider != null, bench + " offers a menu");
            var menu = provider.createMenu(1, player.getInventory(), player);
            check(helper, menu instanceof CraftingMenu && menu.getType() == MenuType.CRAFTING,
                    "the crafting table's own menu, " + menu);
            check(helper, menu.stillValid(player), "kept open at the bench");
            // three planks in a row across the top make... nothing; two planks stacked make sticks
            var craft = (CraftingMenu) menu;
            craft.getSlot(1).set(new ItemStack(Items.OAK_PLANKS));
            craft.getSlot(4).set(new ItemStack(Items.OAK_PLANKS));
            craft.slotsChanged(craft.getSlot(1).container);
            check(helper, craft.getSlot(0).getItem().is(Items.STICK), "two planks make sticks: " + craft.getSlot(0).getItem());
            helper.setBlock(pos, Blocks.AIR);
            check(helper, !menu.stillValid(player), "and it closes once the bench is gone");
            check(helper, bench.defaultBlockState().is(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("c", "player_workstations/crafting_tables"))), "in #c:player_workstations/crafting_tables");
            check(helper, new ItemStack(bench).is(TagKey.create(Registries.ITEM,
                    Identifier.fromNamespaceAndPath("c", "player_workstations/crafting_tables"))), "and so is its item");
        }
        helper.succeed();
    }

    /**
     * The Crusher gives back less than gear cost: a new diamond chestplate 3 or 4 diamonds, a new pickaxe 1 or 2, a
     * chestplate worn to half about half that, one worn through nothing; what is not gear still comes out whole.
     */
    private static void wornGearGrindsToLess(GameTestHelper helper) {
        var random = RandomSource.create(7);
        ItemStack chest = new ItemStack(Items.DIAMOND_CHESTPLATE);
        var recipe = CrushingRecipe.of(chest);
        check(helper, recipe != null && recipe.output().is(Items.DIAMOND), "a chestplate grinds to diamonds");
        int total = 0;
        for (int i = 0; i < 1000; i++) {
            int n = recipe.outputFor(chest, random).getCount();
            check(helper, n == 3 || n == 4, "a new chestplate gives 3 or 4, not " + n);
            total += n;
        }
        check(helper, total > 3450 && total < 3750, "3.6 on average, " + total / 1000.0);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        var pickRecipe = CrushingRecipe.of(pick);
        for (int i = 0; i < 200; i++) {
            int n = pickRecipe.outputFor(pick, random).getCount();
            check(helper, n == 1 || n == 2, "a new pickaxe gives 1 or 2, not " + n);
        }
        ItemStack half = chest.copy();
        half.setDamageValue(half.getMaxDamage() / 2);
        int halfTotal = 0;
        for (int i = 0; i < 1000; i++) {
            halfTotal += recipe.outputFor(half, random).getCount();
        }
        check(helper, halfTotal > 1650 && halfTotal < 1950, "worn to half, 1.8 on average, " + halfTotal / 1000.0);
        ItemStack spent = chest.copy();
        spent.setDamageValue(spent.getMaxDamage() - 1);
        check(helper, recipe.outputFor(spent, random).isEmpty(), "worn through, nothing");
        ItemStack bread = new ItemStack(Items.BREAD);
        check(helper, !Features.enabled("kitchen") || CrushingRecipe.of(bread).outputFor(bread, random).getCount() == 2,
                "bread still crumbles to two (breadcrumbs: with the kitchen)");
        helper.succeed();
    }

    /**
     * What a raw ore and an ore block come to through the Stirring Tank and the Distiller, in ingots (nine nuggets):
     * gold best, copper less, iron barely more than the furnace; a silk-touched ore block about what Fortune III makes
     * of it in raw ore, never that again on top. Rock gives no iron and no ore blocks.
     */
    private static void theMercuryChainDoesNotMultiplyFortune(GameTestHelper helper) {
        BiFunction<Item, Float, Float> ingots = (in, unused) -> {
            var amalgam = MercuryRecipes.amalgamation(new ItemStack(in));
            var distil = MercuryRecipes.distillation(amalgam.output());
            float nuggets = distil.count() + distil.bonusChance() * distil.bonus();
            check(helper, distil.result().get().value().getDescriptionId().endsWith("_nugget"), in + " comes out as nuggets");
            return amalgam.count() * nuggets / 9F;
        };
        float gold = ingots.apply(Items.RAW_GOLD, 0F), copper = ingots.apply(Items.RAW_COPPER, 0F), iron = ingots.apply(Items.RAW_IRON, 0F);
        check(helper, gold > 2.2F && gold < 2.6F, "raw gold, about 2.4 ingots: " + gold);
        check(helper, copper > 1.4F && copper < gold, "raw copper less than gold: " + copper);
        check(helper, iron > 1.0F && iron < copper, "raw iron, barely more than the furnace: " + iron);
        // Fortune III gives 2.2 raw gold a gold ore on average, 3.5 x 2.2 raw copper a copper ore
        float goldOre = ingots.apply(Items.GOLD_ORE, 0F), copperOre = ingots.apply(Items.COPPER_ORE, 0F);
        check(helper, goldOre <= 2.2F * gold * 1.1F && goldOre >= 2.2F * gold * 0.8F, "a gold ore block about Fortune III's raw: " + goldOre);
        check(helper, copperOre <= 7.7F * copper * 1.1F, "a copper ore block no more than Fortune III's raw: " + copperOre);
        var rock = helper.getLevel().getServer().reloadableRegistries().getLootTable(ResourceKey.create(
                Registries.LOOT_TABLE, Nookworks.id("distillation/mixed_amalgam")));
        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
                .create(LootContextParamSets.CHEST);
        for (int i = 0; i < 2000; i++) {
            for (ItemStack out : rock.getRandomItems(params)) {
                check(helper, !out.is(Items.IRON_NUGGET) && !out.is(Items.RAW_IRON) && !out.is(TagKey.create(
                        Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ores"))), "rock gives no iron and no ore blocks: " + out);
            }
        }
        helper.succeed();
    }

    /**
     * The way to a Bottle o' Enchanting without a cleric: the Retort's fill box fills a glass bottle from the
     * experience its charges left (a bucket too); an Experience Tank fills and empties a bottle by hand; and on an
     * Enchanting Table the bottle becomes a Bottle o' Enchanting for one lapis and no levels.
     */
    private static void experienceBottlesFillAndEnchant(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        var xp = FluidResource.of(ExperienceFluids.SOURCE.get());
        ((FluidStacksResourceHandler) retort.experienceHandler()).set(0, xp, 1_000);
        check(helper, retort.canPlaceItem(RetortBlockEntity.FILL, new ItemStack(Items.GLASS_BOTTLE))
                && !retort.canPlaceItem(RetortBlockEntity.FILL, new ItemStack(Items.STICK)), "the fill box takes bottles and buckets only");
        retort.setItem(RetortBlockEntity.FILL, new ItemStack(Items.GLASS_BOTTLE, 3));
        for (int i = 0; i < 5; i++) {
            RetortBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        ItemStack filled = retort.getItem(RetortBlockEntity.FILLED);
        check(helper, filled.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()) && filled.getCount() == 3, "three bottles filled: " + filled);
        check(helper, retort.experienceStored() == 250, "750 mB taken, " + retort.experienceStored() + " left");

        BlockPos tankPos = new BlockPos(5, 2, 2);
        helper.setBlock(tankPos, ModBlocks.EXPERIENCE_TANK.get());
        var tank = helper.getBlockEntity(tankPos, ExperienceTankBlockEntity.class);
        tank.fill(500);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
        BlockPos tankAt = helper.absolutePos(tankPos);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(tankAt), Direction.UP, tankAt, false));
        check(helper, player.getInventory().contains(new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get())) && tank.amount() == 250, // (a creative player keeps the empty one too)
                "a bottle filled at the tank: " + player.getMainHandItem() + ", " + tank.amount());

        helper.getLevel().getServer().getPlayerList().remove(player);
        player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL); // survival: the lapis is paid
        player.experienceLevel = 5;
        var menu = new EnchantmentMenu(1, player.getInventory(),
                ContainerLevelAccess.create(level, helper.absolutePos(new BlockPos(2, 2, 5))));
        menu.getSlot(0).set(new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()));
        menu.getSlot(1).set(new ItemStack(Items.LAPIS_LAZULI, 3));
        menu.slotsChanged(menu.getSlot(0).container);
        check(helper, menu.costs[0] == 1 && menu.costs[1] == 0 && menu.costs[2] == 0, "one choice offered: " + Arrays.toString(menu.costs));
        check(helper, menu.clickMenuButton(player, 0), "taken");
        check(helper, menu.getSlot(0).getItem().is(Items.EXPERIENCE_BOTTLE), "a Bottle o' Enchanting: " + menu.getSlot(0).getItem());
        check(helper, menu.getSlot(1).getItem().getCount() == 2, "one lapis used");
        check(helper, player.experienceLevel == 5, "and no levels");
        helper.succeed();
    }

    /**
     * The Electrolytic Cell: salt and water make caustic soda only with a pool of mercury standing in it, which loses a
     * few millibuckets a run; raw zinc wins zinc; oxidized copper comes back bright. Without water it waits and says so.
     */
    private static void theElectrolyticCell(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.BARREL);
        helper.setBlock(pos, ModBlocks.ELECTROLYTIC_CELL.get());
        var cell = helper.getBlockEntity(pos, ElectrolyticCellBlockEntity.class);
        BlockPos at = helper.absolutePos(pos);
        Runnable run = () -> {
            for (int i = 0; i < ElectrolyticCellBlockEntity.TICKS + 2; i++) {
                ElectrolyticCellBlockEntity.serverTick(level, at, helper.getBlockState(pos), cell);
            }
        };
        cell.energy().set(20_000);
        cell.setItem(0, new ItemStack(ModItems.SALT.get(), 4));
        run.run();
        check(helper, cell.guiStatus() == MachineStatus.LOW_WATER,
                "no water in its tank: it waits, and says so - not that there is no source below, as a pump would: " + cell.guiStatus());
        check(helper, cell.pourBucket() && cell.pourBucket(), "two buckets of water poured in");
        run.run();
        check(helper, cell.guiStatus() == MachineStatus.NO_MERCURY, "no mercury pool: it waits, " + cell.guiStatus());
        cell.fillMercury(1_000, false);
        cell.energy().set(20_000);
        run.run();
        var below = (BarrelBlockEntity) level.getBlockEntity(at.below());
        check(helper, below.countItem(ModItems.CAUSTIC_SODA.get()) == 1, "one caustic soda below");
        check(helper, cell.mercury() == 995 && cell.waterStored() == 1_750, "5 mB of mercury lost and 250 mB of water used: "
                + cell.mercury() + ", " + cell.waterStored());

        cell.setItem(0, new ItemStack(ModItems.RAW_ZINC.get()));
        cell.energy().set(20_000);
        run.run();
        int zinc = below.countItem(ModItems.ZINC_INGOT.get());
        check(helper, zinc == 1 || zinc == 2, "raw zinc won as one or two ingots: " + zinc);

        cell.setItem(0, new ItemStack(Items.CUT_COPPER_STAIRS.weathering().oxidized()));
        cell.energy().set(20_000);
        run.run();
        check(helper, below.countItem(Items.CUT_COPPER_STAIRS.weathering().unaffected()) == 1, "oxidized stairs come back bright");
        helper.succeed();
    }

    private static void same(GameTestHelper helper, StorageMenu server, StorageMenu client, String when) {
        check(helper, client.scrollRow() == server.scrollRow(), when + ": client at row " + client.scrollRow() + ", server " + server.scrollRow());
        for (int i = 0; i < server.visibleRows() * 9; i++) {
            ItemStack there = server.getSlot(i).getItem();
            ItemStack shown = client.getSlot(i).getItem();
            check(helper, ItemStack.matches(there, shown), when + ": slot " + i + " shows " + shown + " but holds " + there);
        }
    }

    private PlayTests() {}
}
