package dev.nookworks.gametest;

import static dev.nookworks.gametest.MachineTests.placeByHand;
import static dev.nookworks.gametest.MiningTests.playerWith;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.IntSupplier;
import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.Codec;

import dev.nookworks.Nookworks;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterGear;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterSettings;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.sorter.SorterTier;
import dev.nookworks.spawner.ControlledSpawner;
import dev.nookworks.spawner.SpawnerControllerBlock;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.spawner.SpawnerLinkReceiverBlock;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import dev.nookworks.spawner.SpawnerLinkSenderBlock;
import dev.nookworks.spawner.SpawnerSettings;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageSpill;
import dev.nookworks.storage.StorageTier;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/** Sorters and Storage Barrels, and what a dedicated server does to their data on the way to a player. */
final class StorageTests {
    static void register() {
        test("sorter_sends_registered_right_others_left", StorageTests::sorterRoutes);
        test("storage_clicks_take_as_vanilla_does", StorageTests::storageClicksTakeAsVanilla);
        test("storage_gather_only_at_the_double_click", StorageTests::storageGatherOnlyAtTheDoubleClick);
        test("sorter_swap_mirrors_the_outlets", StorageTests::sorterSwaps);
        test("sorter_drops_items_without_inventory", StorageTests::sorterDrops);
        test("sorter_waits_when_outlet_is_blocked", StorageTests::sorterWaitsWhenBlocked);
        test("sorter_gears_differ_in_speed", StorageTests::sorterSpeeds);
        test("sorter_gears_fit_and_old_sorters_keep_their_speed", StorageTests::sorterGears);
        test("sorter_filter_slots_are_ghosts", StorageTests::sorterGhostSlots);
        test("storage_slots_hold_more_than_a_stack", StorageTests::storageCapacity);
        test("storage_accepts_hopper_past_64", StorageTests::storageHopper);
        test("storage_spills_when_broken", StorageTests::storageSpillsWhenBroken);
        test("storage_spill_is_spread_out", StorageTests::storageSpillIsSpreadOut);
        test("storage_too_full_cannot_be_broken", StorageTests::storageTooFull);
        test("storage_contents_codec_round_trip", StorageTests::storageCodec);
        test("storage_click_takes_one_stack", StorageTests::storageClickClamp);
        test("storage_scroll_shows_later_rows", StorageTests::storageScroll);
        test("storage_shift_click_both_ways", StorageTests::storageShiftClick);
        test("storage_screen_shows_how_full", StorageTests::storageScreenFill);
        test("storage_too_full_says_so", StorageTests::storageTooFullSaysSo);
        test("storage_counts_fit_a_slot", StorageTests::storageCountsFit);
        test("storage_count_font_covers_every_count", StorageTests::countFontCoversEveryCount);
        test("machine_screens_survive_the_network", StorageTests::machineScreensSurviveTheNetwork);
        test("other_screens_survive_the_network", StorageTests::otherScreensSurviveTheNetwork);
        test("item_data_survives_the_network", StorageTests::itemDataSurvivesTheNetwork);
        test("renamed_ids_still_load", StorageTests::renamedIdsStillLoad);
        test("spawner_silk_hammer_keeps_mob", StorageTests::spawnerSilkHammer);
        test("spawner_silk_pickaxe_drops_nothing", StorageTests::spawnerSilkPickaxe);
        test("spawner_item_restores_mob", StorageTests::spawnerItemRestoresMob);
        test("spawner_controller_pairs_and_pays", StorageTests::controllerPairsAndPays);
        // Spawning rolls against the level's own random, so give it room rather than let it time out at random.
        test("spawner_controller_spawns_without_player", 1200, StorageTests::controllerSpawnsWithoutPlayer);
        test("spawner_controller_off_stops_spawner", StorageTests::controllerOffStopsSpawner);
        test("spawner_controller_menu_buttons", StorageTests::controllerMenuButtons);
        test("spawner_controller_faces_the_player", StorageTests::controllerFacesThePlayer);
        test("spawner_controller_works_on_its_receiver_and_lights_up", StorageTests::controllerWorksOnItsReceiver);
        test("furniture_takes_and_gives_water", StorageTests::furnitureTakesAndGivesWater);
        test("storage_screen_closes_when_barrel_breaks", StorageTests::storageScreenClosesWhenBroken);
        test("storage_takes_only_empty_barrels", StorageTests::storageTakesOnlyEmptyBarrels);
    }

    // ---- Item sorter ----
    // A sorter facing north (front towards -Z) has its right-hand outlet on the west (-X) side.

    static final BlockPos SORTER_POS = new BlockPos(3, 1, 3);

    static SorterBlockEntity placeSorter(GameTestHelper helper, Block block, boolean swapped) {
        helper.setBlock(SORTER_POS, block.defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH).setValue(SorterBlock.SWAPPED, swapped));
        SorterBlockEntity sorter = helper.getBlockEntity(SORTER_POS, SorterBlockEntity.class);
        sorter.filter().setItem(0, new ItemStack(Items.DIAMOND));
        return sorter;
    }

    static void sorterRoutes(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        BlockPos right = SORTER_POS.west();
        BlockPos left = SORTER_POS.east();
        helper.setBlock(right, Blocks.CHEST);
        helper.setBlock(left, Blocks.CHEST);
        sorter.setItem(0, new ItemStack(Items.DIAMOND, 2));

        helper.succeedWhen(() -> {
            check(helper, countIn(helper.getBlockEntity(right, ChestBlockEntity.class), Items.DIAMOND) == 2, "registered diamonds should go right");
            check(helper, countIn(helper.getBlockEntity(left, ChestBlockEntity.class), Items.DIAMOND) == 0, "nothing registered goes left");
        });
    }

    static void sorterSwaps(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), true);
        BlockPos right = SORTER_POS.west();
        BlockPos left = SORTER_POS.east();
        helper.setBlock(right, Blocks.CHEST);
        helper.setBlock(left, Blocks.CHEST);
        sorter.setItem(0, new ItemStack(Items.DIRT, 2));

        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(right, ChestBlockEntity.class), Items.DIRT) == 2,
                "swapped: unregistered dirt should now leave on the right"));
    }

    static void sorterDrops(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        sorter.setItem(0, new ItemStack(Items.DIAMOND, 1));

        helper.succeedWhen(() -> helper.assertItemEntityPresent(Items.DIAMOND, SORTER_POS.west(), 1.5));
    }

    static void sorterWaitsWhenBlocked(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        helper.setBlock(SORTER_POS.west(), Blocks.STONE);
        sorter.setItem(0, new ItemStack(Items.DIAMOND, 3));

        helper.runAfterDelay(40, () -> {
            check(helper, sorter.getItem(0).getCount() == 3, "a blocked outlet must hold the items back");
            helper.assertItemEntityNotPresent(Items.DIAMOND, SORTER_POS, 3.0);
            helper.succeed();
        });
    }

    /** Without a gear a sorter keeps a hopper's pace; a Netherite Sorter Gear moves four a tick. */
    static void sorterSpeeds(GameTestHelper helper) {
        BlockPos plainPos = new BlockPos(1, 1, 1);
        BlockPos gearedPos = new BlockPos(1, 1, 5);
        for (BlockPos p : List.of(plainPos, gearedPos)) {
            helper.setBlock(p.west(), Blocks.CHEST);
            helper.setBlock(p, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
            SorterBlockEntity s = helper.getBlockEntity(p, SorterBlockEntity.class);
            s.filter().setItem(0, new ItemStack(Items.COBBLESTONE));
            s.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.getBlockEntity(gearedPos, SorterBlockEntity.class).gearSlot().setItem(0, new ItemStack(ModItems.NETHERITE_SORTER_GEAR.get()));
        helper.runAfterDelay(24, () -> {
            int plain = countIn(helper.getBlockEntity(plainPos.west(), ChestBlockEntity.class), Items.COBBLESTONE);
            int geared = countIn(helper.getBlockEntity(gearedPos.west(), ChestBlockEntity.class), Items.COBBLESTONE);
            check(helper, plain >= 2 && plain <= 4, "without a gear, ~1 item per 8 ticks (3 in 24), moved " + plain);
            check(helper, geared == 64, "a netherite gear moves 4 items a tick, moved " + geared);
            check(helper, helper.getBlockState(gearedPos).getValue(SorterBlock.GEAR) == SorterGear.NETHERITE
                    && helper.getBlockState(plainPos).getValue(SorterBlock.GEAR) == SorterGear.NONE, "the front shows the gear");
            helper.succeed();
        });
    }

    /**
     * A gear goes into the gear slot by shift-click (anything else is registered as before) and out again; broken, the
     * sorter drops it. A Golden Item Sorter from 0.2.0 turns into an Item Sorter with a gold gear, its filter and load
     * kept, and a Precision Sorter saved by 0.2.0 - which moved a stack a tick - gets a Velarium gear. Neither sorter's
     * recipe asks for Velarium now.
     */
    static void sorterGears(GameTestHelper helper) {
        var level = helper.getLevel();
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(ModItems.DIAMOND_SORTER_GEAR.get(), 2));
        SorterMenu menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        int hotbar = menu.slots.size() - 9;
        menu.quickMoveStack(player, hotbar);
        check(helper, sorter.speed() == SorterGear.DIAMOND && player.getInventory().getItem(0).getCount() == 1,
                "shift-click fits one gear");
        menu.quickMoveStack(player, hotbar);
        check(helper, player.getInventory().getItem(0).getCount() == 1 && sorter.isRegistered(new ItemStack(ModItems.DIAMOND_SORTER_GEAR.get())),
                "a second one, with the slot taken, is only registered as a filter");
        menu.quickMoveStack(player, SorterMenu.GEAR_SLOT);
        check(helper, sorter.speed() == SorterGear.NONE && player.getInventory().countItem(ModItems.DIAMOND_SORTER_GEAR.get()) == 2,
                "shift-click on the gear takes it back");
        check(helper, !menu.getSlot(SorterMenu.GEAR_SLOT).mayPlace(new ItemStack(Items.IRON_INGOT)), "the slot takes only gears");

        BlockPos old = new BlockPos(5, 1, 5);
        helper.setBlock(old, ModBlocks.GOLD_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.EAST));
        var legacy = helper.getBlockEntity(old, SorterBlockEntity.class);
        legacy.filter().setItem(0, new ItemStack(Items.APPLE));
        legacy.setItem(0, new ItemStack(Items.APPLE, 5));
        SorterBlockEntity.serverTick(level, helper.absolutePos(old), helper.getBlockState(old), legacy);
        var turned = helper.getBlockEntity(old, SorterBlockEntity.class);
        check(helper, helper.getBlockState(old).is(ModBlocks.ITEM_SORTER.get()) && helper.getBlockState(old).getValue(SorterBlock.FACING) == Direction.EAST,
                "an old golden sorter is an Item Sorter now, facing as it did");
        check(helper, turned.speed() == SorterGear.GOLD && turned.filter().getItem(0).is(Items.APPLE)
                && turned.getItem(0).getCount() == 5, "with a gold gear, its filter and its load");
        List<Component> said = new ArrayList<>();
        ItemStack oldItem = new ItemStack(ModItems.GOLD_SORTER.get());
        oldItem.getItem().appendHoverText(oldItem, Item.TooltipContext.EMPTY, TooltipDisplay.DEFAULT, said::add,
                TooltipFlag.NORMAL);
        check(helper, said.size() == 1 && said.get(0).getContents() instanceof TranslatableContents words
                && words.getKey().equals("tooltip.nookworks.legacy_sorter") && words.getArgs()[0] instanceof Component gear
                && gear.getContents() instanceof TranslatableContents name
                && name.getKey().equals("item.nookworks.gold_sorter_gear"), "one still in a chest says it becomes an Item Sorter with a gold gear");

        BlockPos precise = new BlockPos(5, 1, 1);
        helper.setBlock(precise, ModBlocks.PRECISION_SORTER.get());
        var precision = helper.getBlockEntity(precise, SorterBlockEntity.class);
        var saved = new CompoundTag();
        saved.put("Filter", new CompoundTag()); // as 0.2.0 saved it: a filter, and no gear
        precision.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING,
                level.registryAccess(), saved));
        check(helper, precision.speed() == SorterGear.VELARIUM, "a Precision Sorter from 0.2.0 keeps its stack a tick");

        sorter.gearSlot().setItem(0, new ItemStack(ModItems.DIAMOND_SORTER_GEAR.get()));
        helper.setBlock(SORTER_POS, Blocks.AIR);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(SORTER_POS)).inflate(1.5));
        check(helper, drops.stream().anyMatch(e -> e.getItem().is(ModItems.DIAMOND_SORTER_GEAR.get())), "broken, a sorter drops its gear");

        var recipes = level.getServer().getRecipeManager();
        for (String id : new String[] {"nookworks:item_sorter", "nookworks:precision_sorter"}) {
            var recipe = recipes.byKey(ResourceKey.create(Registries.RECIPE,
                    Identifier.parse(id)));
            check(helper, recipe.isPresent(), id + " is loaded");
            var needs = recipe.get().value().placementInfo().ingredients();
            check(helper, needs.stream().noneMatch(i -> i.test(new ItemStack(ModItems.VELARIUM_INGOT.get()))), id + " asks for no Velarium");
        }
        helper.succeed();
    }

    static void sorterGhostSlots(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.IRON_INGOT, 20));
        SorterMenu menu = new SorterMenu(1, player.getInventory(), sorter.filter(), sorter.gearSlot(), helper.absolutePos(SORTER_POS),
                sorter.sorterTier(), sorter.settings());
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 5));

        menu.clicked(3, 0, ContainerInput.PICKUP, player);
        check(helper, sorter.filter().getItem(3).is(Items.GOLD_INGOT) && sorter.filter().getItem(3).getCount() == 1,
                "clicking a filter slot registers a single ghost copy");
        check(helper, menu.getCarried().getCount() == 5, "the carried stack must not be consumed");

        int hotbarSlot = menu.slots.size() - 9;
        menu.quickMoveStack(player, hotbarSlot);
        check(helper, sorter.isRegistered(new ItemStack(Items.IRON_INGOT)), "shift-click registers the item");
        check(helper, player.getInventory().getItem(0).getCount() == 20, "shift-click must not move the real items");

        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(3, 0, ContainerInput.PICKUP, player);
        check(helper, sorter.filter().getItem(3).isEmpty(), "clicking with an empty hand clears the slot");

        checkSlotsApart(helper, menu, "the Item Sorter's screen");
        check(helper, !menu.getSlot(SorterTier.ITEM.filterSlots).isActive(), "an Item Sorter has no second filter row");
        checkSlotsApart(helper, new SorterMenu(2, player.getInventory(), new SimpleContainer(SorterBlockEntity.FILTER_SLOTS),
                new SimpleContainer(1), helper.absolutePos(SORTER_POS), SorterTier.PRECISION, null), "the Precision Sorter's screen");
        helper.succeed();
    }

    /** No two slots a screen shows may overlap: the one listed first would take the other's hover and clicks. */
    static void checkSlotsApart(GameTestHelper helper, AbstractContainerMenu menu, String what) {
        List<Slot> shown = menu.slots.stream().filter(Slot::isActive).toList();
        for (int i = 0; i < shown.size(); i++) {
            for (int j = i + 1; j < shown.size(); j++) {
                Slot a = shown.get(i), b = shown.get(j);
                check(helper, Math.abs(a.x - b.x) >= 16 || Math.abs(a.y - b.y) >= 16, what + ": slots " + a.index + " and " + b.index + " overlap");
            }
        }
    }

    // ---- Storage chest ----
    static final BlockPos STORAGE_POS = new BlockPos(3, 1, 3);

    static StorageBlockEntity placeStorage(GameTestHelper helper, Block block) {
        helper.setBlock(STORAGE_POS, block);
        return helper.getBlockEntity(STORAGE_POS, StorageBlockEntity.class);
    }

    static int storageTotal(StorageBlockEntity storage, Item item) {
        int total = 0;
        for (int i = 0; i < storage.inventory().size(); i++) {
            if (storage.inventory().getResource(i).is(item)) {
                total += storage.inventory().getAmountAsInt(i);
            }
        }
        return total;
    }

    static void storageCapacity(GameTestHelper helper) {
        check(helper, StorageTier.IRON.slots == 27 && StorageTier.IRON.slotCapacity(64) == 128 && StorageTier.IRON.slotCapacity(16) == 32,
                "iron: a double chest's worth, 27 slots of 2 stacks");
        check(helper, StorageTier.DIAMOND.slotCapacity(64) == 1024, "diamond holds 16 stacks per slot");
        check(helper, StorageTier.NETHERITE.slots == 108 && StorageTier.NETHERITE.slotCapacity(64) == 16_384
                && StorageTier.NETHERITE.slotCapacity(1) == 256, "netherite holds 256 stacks per slot");
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.GOLD_STORAGE.get());
        check(helper, storage.inventory().size() == 54, "gold storage has 54 slots");
        ItemStack rest = ItemUtil.insertItemReturnRemaining(storage.inventory(),
                new ItemStack(Items.COBBLESTONE, 64 * 4 + 10), false, null);
        check(helper, rest.isEmpty(), "everything fits");
        check(helper, storage.inventory().getAmountAsInt(0) == 256, "slot 0 fills to 256, has " + storage.inventory().getAmountAsInt(0));
        check(helper, storageTotal(storage, Items.COBBLESTONE) == 266, "total is 266");
        helper.succeed();
    }

    static void storageHopper(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 64);
        BlockPos hopper = STORAGE_POS.above();
        helper.setBlock(hopper, Blocks.HOPPER);
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 5));
        helper.succeedWhen(() -> check(helper, storage.inventory().getAmountAsInt(0) == 69,
                "slot 0 should grow past 64 to 69, has " + storage.inventory().getAmountAsInt(0)));
    }

    /** Only the Crate carries its contents; a barrel spills like a chest. */
    static void storageSpillsWhenBroken(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        storage.inventory().set(7, ItemResource.of(Items.DIAMOND), 100);
        helper.getLevel().destroyBlock(helper.absolutePos(STORAGE_POS), true);
        helper.succeedWhen(() -> {
            List<ItemEntity> barrels = helper.getLevel().getEntities(EntityTypes.ITEM,
                    new AABB(helper.absolutePos(STORAGE_POS)).inflate(2.0), e -> e.getItem().is(ModItems.IRON_STORAGE.get()));
            check(helper, barrels.size() == 1 && !barrels.get(0).getItem().has(ModDataComponents.STORAGE.get()),
                    "the barrel drops empty");
            int diamonds = helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(STORAGE_POS)).inflate(3.0),
                    e -> e.getItem().is(Items.DIAMOND)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, diamonds == 100, "and its 100 diamonds spill, found " + diamonds);
        });
    }

    /** More than one tick's worth comes out a tick's worth at a time, all of it in the end. */
    static void storageSpillIsSpreadOut(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        // 200 stacks: under the limit a hand may break (256), so it spills; over it, the barrel leaves whole
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 100 * 64);
        storage.inventory().set(1, ItemResource.of(Items.COBBLESTONE), 100 * 64);
        helper.getLevel().destroyBlock(helper.absolutePos(STORAGE_POS), false); // no player, so nothing guards it
        BlockPos at = helper.absolutePos(STORAGE_POS);
        int waiting = StorageSpill.pending(helper.getLevel(), at);
        // At most a tick's worth: the budget is the level's, so another barrel breaking this tick leaves less.
        check(helper, waiting >= 200 - StorageSpill.PER_TICK && waiting < 200 + 1,
                "no more than one tick's worth comes out at once, " + waiting + " waiting");
        helper.succeedWhen(() -> {
            check(helper, StorageSpill.pending(helper.getLevel(), at) == 0, "the rest follows");
            int cobble = helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(STORAGE_POS)).inflate(4.0),
                    e -> e.getItem().is(Items.COBBLESTONE)).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, cobble == 200 * 64, "nothing is lost, found " + cobble);
        });
    }

    static void storageTooFull(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.DIAMOND), 200 * 64);
        storage.inventory().set(1, ItemResource.of(Items.DIAMOND), 57 * 64); // 257 stacks, one over the default
        check(helper, helper.getBlockState(STORAGE_POS).getValue(StorageBlock.LOCKED), "over the limit: LOCKED");
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_PICKAXE));
        check(helper, helper.getBlockState(STORAGE_POS).getDestroyProgress(player, helper.getLevel(), helper.absolutePos(STORAGE_POS)) == 0.0F,
                "a locked chest cannot be mined at all");
        player.gameMode.destroyBlock(helper.absolutePos(STORAGE_POS));
        helper.runAfterDelay(3, () -> {
            check(helper, helper.getBlockState(STORAGE_POS).is(ModBlocks.NETHERITE_STORAGE.get()), "the chest must still stand");
            check(helper, storage.inventory().getAmountAsInt(0) == 200 * 64, "contents untouched");
            helper.assertItemEntityNotPresent(ModItems.NETHERITE_STORAGE.get(), STORAGE_POS, 3.0);
            storage.inventory().set(1, ItemResource.of(Items.DIAMOND), 56 * 64);
            check(helper, !helper.getBlockState(STORAGE_POS).getValue(StorageBlock.LOCKED), "emptied below the limit: unlocked");
            helper.succeed();
        });
    }

    static void storageCodec(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), StorageTier.NETHERITE.slotCapacity(64));
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Edge"));
        storage.inventory().set(100, ItemResource.of(named), 3);
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag tag = StorageContents.CODEC.encodeStart(ops, storage.inventory().toContents()).getOrThrow();
        StorageContents back = StorageContents.CODEC.parse(ops, tag).getOrThrow();
        storage.inventory().load(StorageContents.EMPTY);
        check(helper, storage.inventory().getAmountAsInt(0) == 0, "load(EMPTY) clears");
        storage.inventory().load(back);
        check(helper, storage.inventory().getAmountAsInt(0) == StorageTier.NETHERITE.slotCapacity(64), "a full slot (far past 99) survives, got " + storage.inventory().getAmountAsInt(0));
        check(helper, storage.inventory().getAmountAsInt(100) == 3
                && storage.inventory().getResource(100).toStack(1).get(DataComponents.CUSTOM_NAME) != null, "components survive");
        helper.succeed();
    }

    /** The barrel's screen says how full it is, counting each item's own stacks. */
    static void storageScreenFill(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 64 * 200);
        storage.inventory().set(1, ItemResource.of(Items.ENDER_PEARL), 16 * 100);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = (StorageMenu) storage.createMenu(1, player.getInventory(), player);
        check(helper, menu.storedStacks() == 300, "300 stacks stored, the screen says " + menu.storedStacks());
        check(helper, StorageTier.NETHERITE.stackCapacity() == 108 * 256, "a full netherite barrel is 27,648 stacks");
        storage.inventory().set(1, ItemResource.EMPTY, 0);
        check(helper, menu.storedStacks() == 200, "taking some out shows at once");
        menu.removed(player);
        helper.succeed();
    }

    /** Hitting a barrel too full to break says so, in a word; one that can be broken says nothing. */
    static void storageTooFullSaysSo(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        List<Component> said = new ArrayList<>();
        Player player = new Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }

            @Override
            public boolean isClientAuthoritative() {
                return false;
            }

            @Override
            public void sendOverlayMessage(Component message) {
                said.add(message);
            }
        };
        Runnable hit = () -> NeoForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickBlock(
                player, helper.absolutePos(STORAGE_POS), Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START));
        storage.inventory().set(0, ItemResource.of(Items.DIAMOND), 64 * 100);
        hit.run();
        check(helper, said.isEmpty(), "a barrel that can be broken says nothing");
        storage.inventory().set(1, ItemResource.of(Items.DIAMOND), 64 * 200);
        hit.run();
        check(helper, said.size() == 1 && said.get(0).getContents() instanceof TranslatableContents t
                && t.getKey().equals("message.nookworks.storage.too_full"), "hitting a too-full barrel says it is too full");
        helper.succeed();
    }

    /** Counts shortened to at most four characters, and never rounded up past what is there. */
    static void storageCountsFit(GameTestHelper helper) {
        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(999, "999");
        expected.put(1_000, "1.0K");
        expected.put(6_499, "6.4K");
        expected.put(12_800, "12K");
        expected.put(999_999, "999K");
        expected.put(1_250_000, "1.2M");
        expected.put(34_900_000, "34M");
        expected.put(Integer.MAX_VALUE, "2.1B");
        expected.forEach((count, text) -> {
            String shown = StorageMenu.shortCount(count);
            check(helper, shown.equals(text), count + " shows as " + shown + ", not " + text);
            check(helper, shown.length() <= 4, count + " takes " + shown.length() + " characters");
        });
        helper.succeed();
    }

    /**
     * A long count is drawn in the count font (font/count.json, tools/textures/gen_count_font.py): every character a
     * count can show has a glyph in it, inked, and a glyph's pixel is one font unit, so the screen can draw each a
     * whole number of screen pixels and keep them crisp. A count of thousands at its widest fits a slot at the size
     * the others are drawn at, up to GUI scale 4, where that is three screen pixels a unit in 17 GUI pixels of room.
     */
    static void countFontCoversEveryCount(GameTestHelper helper) {
        try (InputStream json = StorageTests.class.getResourceAsStream("/assets/nookworks/font/count.json");
                InputStream png = StorageTests.class.getResourceAsStream("/assets/nookworks/textures/font/count.png")) {
            check(helper, json != null && png != null, "the count font and its glyphs are in the jar");
            JsonObject provider = JsonParser.parseString(new String(json.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                    .getAsJsonArray("providers").get(0).getAsJsonObject();
            String chars = provider.getAsJsonArray("chars").get(0).getAsString();
            BufferedImage glyphs = ImageIO.read(png);
            int cell = glyphs.getWidth() / chars.length();
            check(helper, cell * chars.length() == glyphs.getWidth(), "one row of whole cells");
            check(helper, glyphs.getHeight() == provider.get("height").getAsInt(), "a glyph's pixel is one font unit");
            int[] advance = new int[chars.length()];
            for (int i = 0; i < chars.length(); i++) {
                for (int x = i * cell; x < (i + 1) * cell; x++) {
                    for (int y = 0; y < glyphs.getHeight(); y++) {
                        if (glyphs.getRGB(x, y) >>> 24 != 0) {
                            advance[i] = x - i * cell + 2; // as the game measures a glyph: to its last inked column, and a space
                        }
                    }
                }
                check(helper, advance[i] > 0, "'" + chars.charAt(i) + "' is drawn");
            }
            int digit = 0;
            for (char c = '0'; c <= '9'; c++) {
                digit = Math.max(digit, advance[chars.indexOf(c)]);
            }
            int widest = 2 * digit + advance[chars.indexOf('.')] + advance[chars.indexOf('K')];
            check(helper, widest <= 17 * 4 / 3, "the widest count of thousands, " + widest + " units, fits a slot at GUI scale 4");
            for (int count : new int[] {100, 999, 1_000, 6_499, 12_800, 65_536, 101_376, 999_999, 1_250_000, Integer.MAX_VALUE}) {
                String shown = StorageMenu.shortCount(count);
                for (char c : shown.toCharArray()) {
                    check(helper, chars.indexOf(c) >= 0, count + " shows as " + shown + ", and the font has no '" + c + "'");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.succeed();
    }

    // ---- what a dedicated server does to data on the way to the client ----
    //
    // In singleplayer the server hands its packets to the client as they are, so nothing below is ever encoded
    // there: a number too big for a menu's data slot, or an item component that does not survive its network
    // codec, only goes wrong on a real server. These tests put the data through what the network does to it.

    /** Feeds the server menu's data slots to the client menu the way the data packet does: as shorts. */
    static void throughTheNetwork(AbstractContainerMenu server,
            AbstractContainerMenu client) {
        server.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {}

            @Override
            public void dataChanged(AbstractContainerMenu menu, int id, int value) {
                client.setData(id, (short) value);
            }
        });
    }

    /** Every machine, full of power, reads the same on the client as on the server. */
    static void machineScreensSurviveTheNetwork(GameTestHelper helper) {
        List<Block> machines = List.of(ModBlocks.GENERATOR.get(), ModBlocks.CRUSHER.get(),
                ModBlocks.STONE_MAKER.get(), ModBlocks.LASER.get(), ModBlocks.PLANTER.get(), ModBlocks.SIFTER.get(),
                ModBlocks.RETORT.get(), ModBlocks.STIRRING_TANK.get(), ModBlocks.DISTILLER.get(),
                ModBlocks.ELECTRIC_FURNACE.get(), ModBlocks.ELECTROLYTIC_CELL.get(), ModBarter.BARTER_TERMINAL.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < machines.size(); i++) {
            BlockPos pos = new BlockPos(1 + (i % 5), 2, 1 + (i / 5) * 2); // all twelve inside the arena
            helper.setBlock(pos, machines.get(i));
            var entity = helper.getBlockEntity(pos, BlockEntity.class);
            var info = (MachineInfo) entity;
            // An odd amount just under full, so both halves of the split number are exercised.
            SimpleEnergyHandler energy = switch (entity) {
                case MachineBlockEntity machine -> machine.energy();
                case LaserBlockEntity laser -> laser.energy();
                case PlanterBlockEntity planter -> planter.energy();
                default -> null;
            };
            if (energy != null) {
                energy.set((int) energy.getCapacityAsLong() - 3);
            }
            info.setRedstoneMode(RedstoneMode.NEVER);
            var layout = info.layout();
            var server = new MachineMenu(1, player.getInventory(), layout, info.slots(),
                    MachineMenu.data(info), entity);
            var client = new MachineMenu(2, player.getInventory(), layout,
                    new SimpleContainer(layout.slotCount()),
                    new SimpleContainerData(MachineMenu.DATA_COUNT), null);
            throughTheNetwork(server, client);
            check(helper, client.energy() == server.energy() && client.energyCapacity() == server.energyCapacity()
                    && client.energyRate() == server.energyRate(), layout + ": energy arrived as " + client.energy() + " of " + server.energy());
            check(helper, client.progress() == server.progress() && client.progressMax() == server.progressMax(), layout + ": progress");
            for (int t = 0; t < layout.tanks().size(); t++) {
                check(helper, client.tankAmount(t) == server.tankAmount(t) && client.tankCapacity(t) == server.tankCapacity(t)
                        && client.tankKind(t) == server.tankKind(t), layout + ": tank " + t);
            }
            check(helper, client.status() == server.status() && client.redstoneMode() == server.redstoneMode(), layout + ": status and mode");
        }
        helper.succeed();
    }

    /** The screens with menus of their own, each loaded with the biggest numbers it can show. */
    static void otherScreensSurviveTheNetwork(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var registries = helper.getLevel().registryAccess();

        // Repair Box pair: 40,000 FE between them.
        BlockPos left = new BlockPos(1, 2, 1);
        BlockPos right = new BlockPos(2, 2, 1);
        var type = RepairBoxBlock.TYPE;
        BlockState single = ModBlocks.REPAIR_BOX.get().defaultBlockState().setValue(RepairBoxBlock.FACING, Direction.NORTH);
        helper.setBlock(right, single);
        helper.setBlock(left, single.setValue(type, ChestType.LEFT));
        var box = helper.getBlockEntity(left, RepairBoxBlockEntity.class);
        var other = helper.getBlockEntity(right, RepairBoxBlockEntity.class);
        box.energy().set(RepairBoxBlockEntity.CAPACITY - 1);
        other.energy().set(RepairBoxBlockEntity.CAPACITY - 2);
        var halves = new RepairBoxBlockEntity[] {box, other};
        var repairServer = new RepairBoxMenu(1, player.getInventory(),
                new CompoundContainer(box, other), RepairBoxBlockEntity.dataFor(halves), halves);
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        buf.writeVarInt(halves.length);
        var repairClient = RepairBoxMenu.client(2, player.getInventory(), buf);
        throughTheNetwork(repairServer, repairClient);
        check(helper, repairClient.energy() == repairServer.energy() && repairClient.experience() == repairServer.experience()
                && repairClient.boxes() == 2 && repairClient.redstoneMode() == repairServer.redstoneMode(),
                "repair box pair: energy arrived as " + repairClient.energy() + " of " + repairServer.energy());
        repairServer.removed(player);

        // Printing Press.
        BlockPos pressPos = new BlockPos(4, 2, 1);
        helper.setBlock(pressPos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pressPos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        press.select(Nookworks.id("safety_cross"));
        var pressServer = (PrintingPressMenu) press.createMenu(1, player.getInventory(), player);
        var pressClient = new PrintingPressMenu(2, player.getInventory());
        throughTheNetwork(pressServer, pressClient);
        check(helper, pressClient.energy() == pressServer.energy() && Objects.equals(pressClient.selected(), pressServer.selected())
                && pressClient.redstoneMode() == pressServer.redstoneMode(), "printing press");

        // Spawner Controller: a full buffer (in the receiver it stands on) and the longest interval.
        BlockPos controllerPos = new BlockPos(1, 3, 4);
        helper.setBlock(controllerPos.below(), ModBlocks.SPAWNER_LINK_RECEIVER.get());
        helper.setBlock(controllerPos, ModBlocks.SPAWNER_CONTROLLER.get());
        var controller = helper.getBlockEntity(controllerPos, SpawnerControllerBlockEntity.class);
        controller.energy().set(SpawnerControllerBlockEntity.CAPACITY);
        controller.setSettings(SpawnerSettings.DEFAULT.withInterval(SpawnerSettings.MAX_INTERVAL).withCount(SpawnerSettings.MAX_COUNT));
        var controllerServer = (SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player);
        var controllerClient = new SpawnerControllerMenu(2);
        throughTheNetwork(controllerServer, controllerClient);
        check(helper, controllerClient.energy() == controllerServer.energy()
                && controllerClient.intervalSeconds() == controllerServer.intervalSeconds()
                && controllerClient.count() == controllerServer.count(), "spawner controller");

        // Spawn Egg Brewer.
        BlockPos brewerPos = new BlockPos(3, 2, 4);
        helper.setBlock(brewerPos, ModBlocks.SPAWN_EGG_BREWER.get());
        var brewer = helper.getBlockEntity(brewerPos, SpawnEggBrewerBlockEntity.class);
        brewer.setRedstoneMode(RedstoneMode.NEVER);
        var brewerServer = (SpawnEggBrewerMenu) brewer.createMenu(1, player.getInventory(), player);
        var brewerClient = new SpawnEggBrewerMenu(2, player.getInventory());
        throughTheNetwork(brewerServer, brewerClient);
        check(helper, brewerClient.redstoneMode() == RedstoneMode.NEVER, "spawn egg brewer");

        // Precision Sorter with every switch on.
        BlockPos sorterPos = new BlockPos(5, 2, 4);
        helper.setBlock(sorterPos, ModBlocks.PRECISION_SORTER.get());
        var sorter = helper.getBlockEntity(sorterPos, SorterBlockEntity.class);
        for (int i = 0; i < SorterSettings.COUNT; i++) {
            sorter.settings().toggle(i);
        }
        var sorterServer = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        var sorterBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        sorterBuf.writeEnum(SorterTier.PRECISION);
        sorterBuf.writeBlockPos(helper.absolutePos(sorterPos));
        var sorterClient = SorterMenu.client(2, player.getInventory(), sorterBuf);
        throughTheNetwork(sorterServer, sorterClient);
        for (int i = 0; i < SorterSettings.COUNT; i++) {
            check(helper, sorterClient.flag(i), "precision sorter switch " + i);
        }

        // A netherite barrel nearly full: 27,000 stacks, scrolled to the bottom.
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        for (int slot = 0; slot < 105; slot++) {
            storage.inventory().set(slot, ItemResource.of(Items.COBBLESTONE), 64 * 256);
        }
        storage.inventory().set(105, ItemResource.of(Items.COBBLESTONE), 64 * 120);
        var storageServer = (StorageMenu) storage.createMenu(1, player.getInventory(), player);
        storageServer.clickMenuButton(player, storageServer.maxScroll());
        var storageBuf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        storageBuf.writeEnum(StorageTier.NETHERITE);
        var storageClient = StorageMenu.client(2, player.getInventory(), storageBuf);
        throughTheNetwork(storageServer, storageClient);
        check(helper, storageClient.storedStacks() == 27_000, "barrel fill arrived as " + storageClient.storedStacks());
        check(helper, storageClient.scrollRow() == storageServer.maxScroll(), "barrel scroll position");
        storageServer.removed(player);
        helper.succeed();
    }

    /**
     * Every Nookworks item component survives the item network codec. The list is checked against the
     * registered components, so a new component without a sample here fails the test instead of going unchecked.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void itemDataSurvivesTheNetwork(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Edge"));
        Map<DataComponentType<?>, ItemStack> samples = new LinkedHashMap<>();
        BiConsumer<DataComponentType, ItemStack> sample = (type, stack) -> samples.put(type, stack);
        ItemStack barrel = new ItemStack(ModItems.NETHERITE_STORAGE.get());
        barrel.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(
                new StorageContents.Entry(0, new ItemStack(Items.COBBLESTONE), 1_000_000), new StorageContents.Entry(7, named, 1))));
        sample.accept(ModDataComponents.STORAGE.get(), barrel);
        ItemStack spawner = new ItemStack(Items.SPAWNER);
        spawner.set(ModDataComponents.SPAWNER_ENTITY.get(), EntityTypes.PIG);
        sample.accept(ModDataComponents.SPAWNER_ENTITY.get(), spawner);
        ItemStack tank = new ItemStack(ModItems.MERCURY_TANK.get());
        tank.set(ModDataComponents.MERCURY.get(), 12_345);
        sample.accept(ModDataComponents.MERCURY.get(), tank);
        ItemStack copperTank = new ItemStack(ModItems.COPPER_TANK.get());
        copperTank.set(ModDataComponents.FLUID_CONTENTS.get(),
                SimpleFluidContent.copyOf(new FluidStack(
                        OilFluids.BIODIESEL.get(), 7_250)));
        sample.accept(ModDataComponents.FLUID_CONTENTS.get(), copperTank);
        ItemStack retort = new ItemStack(ModItems.RETORT.get());
        retort.set(ModDataComponents.HELD_FLUIDS.get(), List.of(
                SimpleFluidContent.copyOf(new FluidStack(
                        MercuryFluids.SOURCE.get(), 3_210)),
                SimpleFluidContent.copyOf(new FluidStack(
                        Fluids.WATER, 2_000))));
        sample.accept(ModDataComponents.HELD_FLUIDS.get(), retort);
        sample.accept(ModDataComponents.SORTER_TAG.get(), SorterTags.entry(
                new ItemStack(Items.IRON_INGOT), Tags.Items.INGOTS_IRON));
        sample.accept(ModDataComponents.SORTER_MOD.get(), SorterTags.modEntry(new ItemStack(Items.IRON_INGOT)));
        ItemStack poster = new ItemStack(ModItems.POSTER.get());
        poster.set(ModDataComponents.POSTER.get(), Nookworks.id("safety_cross"));
        sample.accept(ModDataComponents.POSTER.get(), poster);
        sample.accept(ModDataComponents.LABEL.get(), LabelItem.of(new ItemStack(Items.COBBLESTONE)));
        sample.accept(ModDataComponents.LABEL_TEXT.get(), LabelItem.ofText("Ores 42"));
        ItemStack poweredCrusher = new ItemStack(ModBlocks.CRUSHER.get());
        poweredCrusher.set(ModDataComponents.HELD_ENERGY.get(), 12_345);
        sample.accept(ModDataComponents.HELD_ENERGY.get(), poweredCrusher);
        ItemStack stirringTank = new ItemStack(ModItems.STIRRING_TANK.get());
        stirringTank.set(ModDataComponents.CATALYST_LEFT.get(), 3);
        sample.accept(ModDataComponents.CATALYST_LEFT.get(), stirringTank);
        ItemStack brewer = new ItemStack(ModItems.SPAWN_EGG_BREWER.get());
        brewer.set(ModDataComponents.BREWS_LEFT.get(), 7);
        sample.accept(ModDataComponents.BREWS_LEFT.get(), brewer);
        ItemStack stampedReceiver = new ItemStack(ModItems.SPAWNER_LINK_RECEIVER.get());
        stampedReceiver.set(ModDataComponents.SPAWNER_LINK_STAMP.get(), 0x0123_4567_89AB_CDEFL);
        sample.accept(ModDataComponents.SPAWNER_LINK_STAMP.get(), stampedReceiver);
        ItemStack redHot = new ItemStack(ModItems.RED_HOT_VELARIUM_INGOT.get());
        redHot.set(ModDataComponents.HEATED_AT.get(), 9_876_543_210L);
        sample.accept(ModDataComponents.HEATED_AT.get(), redHot);
        ItemStack magnet = new ItemStack(ModItems.MAGNET.get());
        magnet.set(ModDataComponents.MAGNET_ACTIVE.get(), true);
        sample.accept(ModDataComponents.MAGNET_ACTIVE.get(), magnet);
        ItemStack leafless = new ItemStack(net.minecraft.world.item.Items.DIAMOND_AXE);
        leafless.set(ModDataComponents.GATHERS_LEAVES.get(), false);
        sample.accept(ModDataComponents.GATHERS_LEAVES.get(), leafless);
        ItemStack bottle = new ItemStack(ModItems.MOB_BOTTLE.get());
        bottle.set(ModDataComponents.CAPTURED_ENTITY.get(), EntityTypes.COW);
        sample.accept(ModDataComponents.CAPTURED_ENTITY.get(), bottle);
        ItemStack whole = MobBottleItem.filled(EntityTypes.COW);
        var cowData = new CompoundTag();
        cowData.putFloat("Health", 5.0F);
        cowData.putString("CustomName", "Moo");
        whole.set(ModDataComponents.CAPTURED_DATA.get(), CustomData.of(cowData));
        sample.accept(ModDataComponents.CAPTURED_DATA.get(), whole);
        ItemStack receiver = new ItemStack(ModItems.SPAWNER_LINK_RECEIVER.get());
        receiver.set(ModDataComponents.SPAWNER_LINK.get(),
                GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(1, 2, 3))));
        sample.accept(ModDataComponents.SPAWNER_LINK.get(), receiver);
        ItemStack warpReceiver = new ItemStack(ModItems.HORIZONTAL_ITEM_WARP_RECEIVER.get());
        warpReceiver.set(ModDataComponents.WARP_LINK.get(), GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(3, 2, 1))));
        sample.accept(ModDataComponents.WARP_LINK.get(), warpReceiver);
        ItemStack stampedWarp = new ItemStack(ModItems.HORIZONTAL_ITEM_WARP_RECEIVER.get());
        stampedWarp.set(ModDataComponents.WARP_LINK_STAMP.get(), -0x0123_4567_89AB_CDEFL);
        sample.accept(ModDataComponents.WARP_LINK_STAMP.get(), stampedWarp);
        ItemStack camera = new ItemStack(ModItems.CAMERA.get());
        camera.set(ModDataComponents.CAMERA_PHOTOS.get(), List.of(3, 40_000, 1_234_567));
        sample.accept(ModDataComponents.CAMERA_PHOTOS.get(), camera);

        for (var entry : ModDataComponents.COMPONENTS.getEntries()) {
            check(helper, samples.containsKey(entry.get()), entry.getId() + " has no sample in this test");
        }
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        samples.forEach((type, stack) -> {
            var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            ItemStack.STREAM_CODEC.encode(buf, stack);
            ItemStack back = ItemStack.STREAM_CODEC.decode(buf);
            var codec = (Codec) type.codecOrThrow();
            Tag sent = (Tag) codec.encodeStart(ops, stack.get(type)).getOrThrow();
            Tag arrived = back.has(type) ? (Tag) codec.encodeStart(ops, back.get(type)).getOrThrow() : null;
            check(helper, back.is(stack.getItem()) && sent.equals(arrived), type + " came back as " + arrived + ", not " + sent);
        });
        helper.succeed();
    }

    /** A world saved before the ids were renamed still finds its blocks, items and block entities. */
    static void renamedIdsStillLoad(GameTestHelper helper) {
        check(helper, BuiltInRegistries.BLOCK.getValue(Nookworks.id("fertile_soil")) == ModBlocks.FERTILE_SOIL.get(),
                "fertile_soil loads as Nourished Soil");
        check(helper, BuiltInRegistries.BLOCK.getValue(Nookworks.id("netherite_storage")) == ModBlocks.NETHERITE_STORAGE.get(),
                "netherite_storage loads as the Netherite Storage Barrel");
        check(helper, BuiltInRegistries.ITEM.getValue(Nookworks.id("trace_amalgam")) == ModItems.TRACE_AMALGAM.get(),
                "trace_amalgam loads as Mixed Amalgam");
        check(helper, BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Nookworks.id("line_warp_sender"))
                == ModBlockEntities.HORIZONTAL_ITEM_WARP_SENDER.get(), "the horizontal warp sender's block entity loads");
        check(helper, BuiltInRegistries.BLOCK.getValue(Nookworks.id("chair")) == ModWoods.CHAIRS.get("oak").get()
                && BuiltInRegistries.ITEM.getValue(Nookworks.id("desk")) == ModWoods.DESK_ITEMS.get("oak").get(),
                "the Chair and the Desk load as the oak ones");
        check(helper, BuiltInRegistries.BLOCK.getKey(ModBlocks.FERTILE_SOIL.get()).equals(Nookworks.id("nourished_soil")),
                "and it is saved under the new id");
        helper.succeed();
    }

    static StorageMenu storageMenu(Player player, StorageBlockEntity storage) {
        return new StorageMenu(1, player.getInventory(), storage.inventory(), storage.getBlockPos());
    }

    static void storageClickClamp(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 120);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = storageMenu(player, storage);
        check(helper, menu.getSlot(0).getItem().getCount() == 120, "the slot shows the full count");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().getCount() == 64, "a click lifts one stack, got " + menu.getCarried().getCount());
        check(helper, storage.inventory().getAmountAsInt(0) == 56, "56 stay behind, has " + storage.inventory().getAmountAsInt(0));
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().isEmpty() && storage.inventory().getAmountAsInt(0) == 120,
                "clicking again puts it back, slot has " + storage.inventory().getAmountAsInt(0));
        helper.succeed();
    }

    /**
     * Taking out as vanilla's chests do (the user, 2026-10-11): a right-click lifts half, half a stack at most; a
     * left-click a stack; and a shift + double-click moves every stack of the kind into the inventory - slots of
     * thousands and rows scrolled away too - as far as there is room, losing nothing.
     */
    static void storageClicksTakeAsVanilla(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        var inv = storage.inventory();
        inv.set(0, ItemResource.of(Items.COBBLESTONE), 1000);
        inv.set(1, ItemResource.of(Items.DIRT), 40);
        inv.set(2, ItemResource.of(Items.ENDER_PEARL), 100);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = storageMenu(player, storage);
        menu.clicked(0, 1, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().getCount() == 32 && inv.getAmountAsInt(0) == 968,
                "a right-click on a thousand lifts half a stack, got " + menu.getCarried().getCount());
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        menu.clicked(1, 1, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().getCount() == 20, "on forty, half of them, got " + menu.getCarried().getCount());
        menu.clicked(1, 0, ContainerInput.PICKUP, player);
        menu.clicked(2, 1, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().getCount() == 8, "on ender pearls, half their stack of 16, got " + menu.getCarried().getCount());
        menu.clicked(2, 0, ContainerInput.PICKUP, player);
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().getCount() == 64, "a left-click lifts one stack");
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().isEmpty() && inv.getAmountAsInt(0) == 1000, "and puts it back");

        menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER);
        check(helper, inv.getAmountAsInt(0) == 1000, "a gather without the shift-click before it moves nothing");

        int far = StorageMenu.MAX_VISIBLE_ROWS * 9 + 5; // in a row off the screen
        inv.set(5, ItemResource.of(Items.COBBLESTONE), 300);
        inv.set(far, ItemResource.of(Items.COBBLESTONE), 200);
        menu.clicked(0, 1, ContainerInput.PICKUP, player); // half a stack in hand, as the user does it
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player); // the double-click's first, shifted
        menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER); // and its second
        int moved = player.getInventory().countItem(Items.COBBLESTONE);
        check(helper, moved == 968 + 300 + 200, "every cobblestone in the barrel is in the inventory, got " + moved);
        check(helper, inv.getAmountAsInt(0) == 0 && inv.getAmountAsInt(5) == 0 && inv.getAmountAsInt(far) == 0,
                "the slots of it emptied, the far row's too");
        check(helper, inv.getAmountAsInt(1) == 40 && inv.getAmountAsInt(2) == 100, "and nothing else moved");
        check(helper, menu.getCarried().getCount() == 32, "the half in hand stays in hand");

        // a fuller barrel than the inventory takes: what does not fit stays, nothing lost
        player.getInventory().clearContent();
        menu.setCarried(ItemStack.EMPTY);
        inv.set(0, ItemResource.of(Items.COBBLESTONE), 2000);
        inv.set(5, ItemResource.of(Items.COBBLESTONE), 2000);
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
        menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER);
        int inInventory = player.getInventory().countItem(Items.COBBLESTONE);
        check(helper, inInventory == 36 * 64, "the inventory filled, got " + inInventory);
        check(helper, inInventory + inv.getAmountAsInt(0) + inv.getAmountAsInt(5) == 4000, "and the rest kept in the barrel");
        helper.succeed();
    }

    /**
     * The gather is the double-click's second half: a second after the shift-click it does nothing; it leaves a stack of
     * the kind that is named behind, as vanilla's does; and a player who has just died gathers nothing.
     */
    static void storageGatherOnlyAtTheDoubleClick(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        var inv = storage.inventory();
        ItemResource cobble = ItemResource.of(Items.COBBLESTONE);
        inv.set(0, cobble, 300);
        inv.set(2, cobble, 200);
        ItemStack named = new ItemStack(Items.COBBLESTONE);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keystone"));
        inv.set(1, ItemResource.of(named), 5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = storageMenu(player, storage);
        menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player); // the first slot's stacks, as a shift-click moves them
        helper.runAfterDelay(StorageMenu.GATHER_TICKS + 2, () -> {
            menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER);
            check(helper, inv.getAmountAsInt(2) == 200, "a gather long after the shift-click moves nothing, left " + inv.getAmountAsInt(2));
            inv.set(0, cobble, 10);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER);
            check(helper, inv.getAmountAsInt(2) == 0 && inv.getAmountAsInt(1) == 5, "at once, the plain stacks go and the named one stays");
            player.getInventory().clearContent();
            inv.set(0, cobble, 10);
            inv.set(2, cobble, 200);
            menu.clicked(0, 0, ContainerInput.QUICK_MOVE, player);
            player.setHealth(0.0F);
            menu.clickMenuButton(player, StorageMenu.BUTTON_GATHER);
            check(helper, inv.getAmountAsInt(2) == 200, "a player just dead gathers nothing, left " + inv.getAmountAsInt(2));
            helper.succeed();
        });
    }

    static void storageScroll(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.DIAMOND_STORAGE.get());
        storage.inventory().set(30, ItemResource.of(Items.EMERALD), 5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = storageMenu(player, storage);
        check(helper, menu.maxScroll() == 3, "diamond has 9 rows, 3 hidden");
        check(helper, menu.getSlot(3).getItem().isEmpty(), "row 0 slot 3 is empty");
        menu.clickMenuButton(player, 99);
        check(helper, menu.scrollRow() == 3, "scroll clamps to 3");
        check(helper, menu.getSlot(3).getItem().is(Items.EMERALD), "scrolled slot 3 shows storage slot 30");
        helper.succeed();
    }

    static void storageShiftClick(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        StorageMenu menu = storageMenu(player, storage);
        int hotbar0 = menu.slots.size() - 9;
        menu.quickMoveStack(player, hotbar0);
        check(helper, player.getInventory().getItem(0).isEmpty() && storageTotal(storage, Items.COBBLESTONE) == 64, "player stack moves in");
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 256);
        menu.quickMoveStack(player, 0);
        check(helper, player.getInventory().countItem(Items.COBBLESTONE) == 64 && storage.inventory().getAmountAsInt(0) == 192,
                "a shift-click moves one stack out, as a chest's slot gives (the user, 2026-10-12), player has "
                        + player.getInventory().countItem(Items.COBBLESTONE));
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 30);
        menu.quickMoveStack(player, 0);
        check(helper, player.getInventory().countItem(Items.COBBLESTONE) == 94 && storage.inventory().getAmountAsInt(0) == 0,
                "a slot of less than a stack empties");
        player.getInventory().clearContent();
        check(helper, menu.getSlot(1).mayPlace(new ItemStack(ModItems.IRON_STORAGE.get())), "an empty barrel goes inside");
        ItemStack full = new ItemStack(ModItems.IRON_STORAGE.get());
        full.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(new StorageContents.Entry(0, new ItemStack(Items.DIAMOND), 1))));
        check(helper, !menu.getSlot(1).mayPlace(full), "a barrel carrying things does not");
        storage.inventory().set(5, ItemResource.of(Items.COBBLESTONE), 64);
        player.getInventory().setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        menu.quickMoveStack(player, hotbar0);
        check(helper, storage.inventory().getAmountAsInt(5) == 128 && storage.inventory().getResource(0).isEmpty(),
                "shift-clicked in, it joins the slot already holding it, not the first empty one: slot 5 has "
                        + storage.inventory().getAmountAsInt(5));
        helper.succeed();
    }

    static void furnitureTakesAndGivesWater(GameTestHelper helper) {
        for (var block : List.<Block>of(ModWoods.CHAIRS.get("oak").get(), ModWoods.DESKS.get("oak").get())) {
            BlockPos pos = new BlockPos(3, 2, 3);
            helper.setBlock(pos, block);
            var waterlogged = (SimpleWaterloggedBlock) block;
            check(helper, waterlogged.placeLiquid(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos),
                    Fluids.WATER.getSource(false)), block + " takes a bucket of water");
            check(helper, helper.getBlockState(pos).getValue(BlockStateProperties.WATERLOGGED), block + " is waterlogged");
            ItemStack bucket = waterlogged.pickupBlock(null, helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
            check(helper, bucket.is(Items.WATER_BUCKET) && !helper.getBlockState(pos).getValue(BlockStateProperties.WATERLOGGED),
                    block + " gives the water back");
        }
        helper.succeed();
    }

    static void storageScreenClosesWhenBroken(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.DIAMOND), 100);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.snapTo(helper.absoluteVec(Vec3.atCenterOf(STORAGE_POS).add(1.5, 0, 0)));
        var menu = storage.createMenu(1, player.getInventory(), player);
        check(helper, menu.stillValid(player), "open next to the barrel: valid");
        // Blown up, say: the diamonds leave inside the dropped barrel, so the open screen must let go of them.
        helper.getLevel().destroyBlock(helper.absolutePos(STORAGE_POS), true);
        check(helper, !menu.stillValid(player), "the screen must close once the barrel is gone");
        helper.succeed();
    }

    /**
     * An empty barrel item (what breaking a barrel leaves) packs into a barrel like any block; one carrying contents,
     * which only a command makes, does not - by hopper, pipe or shift-click.
     */
    static void storageTakesOnlyEmptyBarrels(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        ItemStack empty = new ItemStack(ModItems.GOLD_STORAGE.get());
        ItemStack rest = ItemUtil.insertItemReturnRemaining(storage.inventory(), empty.copy(), false, null);
        check(helper, rest.isEmpty(), "an empty barrel goes into a barrel");
        check(helper, empty.getItem().canFitInsideContainerItems(empty), "and into a shulker box");
        ItemStack full = new ItemStack(ModItems.GOLD_STORAGE.get());
        full.set(ModDataComponents.STORAGE.get(), new StorageContents(List.of(
                new StorageContents.Entry(0, new ItemStack(Items.DIAMOND), 100))));
        rest = ItemUtil.insertItemReturnRemaining(storage.inventory(), full.copy(), false, null);
        check(helper, rest.getCount() == 1, "a hopper or pipe cannot put a barrel of things inside a barrel");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, full.copy());
        StorageMenu menu = storageMenu(player, storage);
        menu.quickMoveStack(player, menu.slots.size() - 9);
        check(helper, player.getInventory().getItem(0).has(ModDataComponents.STORAGE.get()), "nor can a shift-click");
        check(helper, storageTotal(storage, ModItems.GOLD_STORAGE.get()) == 1, "the barrel holds just the empty one");
        helper.succeed();
    }

    static final BlockPos SPAWNER_POS = new BlockPos(3, 2, 3);

    static SpawnerBlockEntity placeSpawner(GameTestHelper helper, EntityType<?> type) {
        helper.setBlock(SPAWNER_POS, Blocks.SPAWNER);
        SpawnerBlockEntity spawner = helper.getBlockEntity(SPAWNER_POS, SpawnerBlockEntity.class);
        spawner.setEntityId(type, helper.getLevel().getRandom());
        return spawner;
    }

    static ItemStack silkTouched(GameTestHelper helper, Item item) {
        ItemStack stack = new ItemStack(item);
        stack.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH), 1);
        return stack;
    }

    static void spawnerSilkHammer(GameTestHelper helper) {
        placeSpawner(helper, EntityTypes.ZOMBIE);
        ServerPlayer player = playerWith(helper, silkTouched(helper, ModItems.DIAMOND_HAMMER.get()));
        player.gameMode.destroyBlock(helper.absolutePos(SPAWNER_POS));
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getLevel().getEntities(EntityTypes.ITEM,
                    new AABB(helper.absolutePos(SPAWNER_POS)).inflate(3.0), e -> e.getItem().is(Items.SPAWNER));
            check(helper, drops.size() == 1, "one spawner item should drop, got " + drops.size());
            check(helper, drops.get(0).getItem().get(ModDataComponents.SPAWNER_ENTITY.get()) == EntityTypes.ZOMBIE,
                    "the item remembers the zombie");
            check(helper, helper.getLevel().getEntities(EntityTypes.EXPERIENCE_ORB,
                    new AABB(helper.absolutePos(SPAWNER_POS)).inflate(3.0), e -> true).isEmpty(), "no XP when picked up");
        });
    }

    static void spawnerSilkPickaxe(GameTestHelper helper) {
        placeSpawner(helper, EntityTypes.ZOMBIE);
        ServerPlayer player = playerWith(helper, silkTouched(helper, Items.DIAMOND_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(SPAWNER_POS));
        helper.runAfterDelay(5, () -> {
            check(helper, helper.getBlockState(SPAWNER_POS).isAir(), "the spawner is mined");
            helper.assertItemEntityNotPresent(Items.SPAWNER, SPAWNER_POS, 3.0);
            helper.succeed();
        });
    }

    static void spawnerItemRestoresMob(GameTestHelper helper) {
        ItemStack stack = new ItemStack(Items.SPAWNER);
        stack.set(ModDataComponents.SPAWNER_ENTITY.get(), EntityTypes.SKELETON);
        BlockPos floor = new BlockPos(3, 1, 3);
        placeByHand(helper, stack, floor);
        SpawnerBlockEntity spawner = helper.getBlockEntity(floor, SpawnerBlockEntity.class);
        check(helper, ControlledSpawner.of(spawner.getSpawner()).nookworks$entityType() == EntityTypes.SKELETON,
                "the placed spawner makes skeletons");
        helper.succeed();
    }

    /** Spawner with a sender on top; in the corner a receiver paired to it, filled, and the controller on top of it. */
    static SpawnerControllerBlockEntity buildControl(GameTestHelper helper, SpawnerSettings settings, int energy) {
        placeSpawner(helper, EntityTypes.PIG);
        BlockPos sender = SPAWNER_POS.above();
        helper.setBlock(sender, ModBlocks.SPAWNER_LINK_SENDER.get());
        BlockPos receiverPos = new BlockPos(0, 1, 0);
        BlockPos controllerPos = receiverPos.above();
        helper.setBlock(receiverPos, ModBlocks.SPAWNER_LINK_RECEIVER.get());
        helper.setBlock(controllerPos, ModBlocks.SPAWNER_CONTROLLER.get());
        helper.getBlockEntity(receiverPos, SpawnerLinkReceiverBlockEntity.class)
                .setLink(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(sender)), 0L); // a sender set by a command: stamp 0
        SpawnerControllerBlockEntity controller = helper.getBlockEntity(controllerPos, SpawnerControllerBlockEntity.class);
        controller.setSettings(settings);
        controller.energy().set(energy);
        return controller;
    }

    /**
     * Pigs inside this test's own arena. Counting by distance instead went wrong both ways: a small radius
     * missed mobs the spawner scattered to the edge, and a wide one picked up the pigs from the test running
     * in the next arena along.
     */
    static int pigsAround(GameTestHelper helper) {
        return helper.getEntities(EntityTypes.PIG).size();
    }

    static void controllerPairsAndPays(GameTestHelper helper) {
        SpawnerControllerBlockEntity controller = buildControl(helper, SpawnerSettings.DEFAULT, 1_000);
        SpawnerBlockEntity spawner = helper.getBlockEntity(SPAWNER_POS, SpawnerBlockEntity.class);
        controller.update(helper.getLevel());
        check(helper, controller.linked() == 1 && controller.powered(), "one spawner linked and powered");
        check(helper, controller.energy().getAmountAsLong() == 1_000 - SpawnerControllerBlockEntity.PERIOD * SpawnerSettings.BASE_FE_PER_TICK,
                "one period of power is used, left " + controller.energy().getAmountAsLong());
        check(helper, ControlledSpawner.of(spawner.getSpawner()).nookworks$activeControl(helper.getLevel()) != null, "the spawner is controlled");
        controller.energy().set(0);
        controller.update(helper.getLevel());
        check(helper, !controller.powered(), "no power: not powered");
        helper.succeed();
    }

    static void controllerSpawnsWithoutPlayer(GameTestHelper helper) {
        // at midnight: a pig will not stand on the arena's stone in the dark, and "any light" must see to that too
        // (this is what made the test fail now and then, whenever the batch ran at night)
        var world = helper.getLevel();
        world.dimensionTypeRegistration().value().defaultClock().ifPresent(clock -> {
            long was = world.getServer().clockManager().getInstance(clock).totalTicks();
            world.getServer().clockManager().setTotalTicks(clock, 18_000);
            // put back when it ends: left at midnight, the tests after it ran in the dark
            AfterTest.undo(helper, () -> world.getServer().clockManager().setTotalTicks(clock, was));
        });
        var controller = buildControl(helper,
                SpawnerSettings.DEFAULT.withInterval(20).withCount(16).withAnyLight(true).withNeedsPlayer(false), 20_000);
        helper.succeedWhen(() -> {
            // Keep it fed. A full buffer runs the spawner for about a thousand ticks, and every spawn is a
            // roll for somewhere to put the mob, so running dry part way through was what made this fail
            // now and then. Whether it spawns with nobody nearby is the question, not how long it lasts.
            controller.energy().set((int) controller.energy().getCapacityAsLong());
            if (pigsAround(helper) == 0) {
                // what the spawner saw, so a failure says why and not only that it failed
                var level = helper.getLevel();
                BlockPos spawner = helper.absolutePos(SPAWNER_POS);
                int nearby = level.getEntities(EntityTypes.PIG, new AABB(spawner).inflate(16), e -> true).size();
                var be = helper.getBlockEntity(SPAWNER_POS, SpawnerBlockEntity.class);
                check(helper, false, "pigs should spawn with no player around (pigs within 16 of the spawner: " + nearby
                        + ", controller powered " + controller.powered() + ", linked " + controller.linked()
                        + ", spawner " + (be == null ? "gone" : "there") + ")");
            }
        });
    }

    static void controllerOffStopsSpawner(GameTestHelper helper) {
        buildControl(helper, SpawnerSettings.DEFAULT.withEnabled(false).withInterval(20).withAnyLight(true).withNeedsPlayer(false), 20_000);
        IntSupplier countdown = spawnerCountdown(helper);
        int[] before = new int[1];
        // counted from once the controller has looked (it takes the spawner in hand at its first update; a world loaded
        // with one finds the hold saved in the spawner)
        helper.startSequence().thenExecuteAfter(30, () -> before[0] = countdown.getAsInt()).thenExecuteAfter(60, () -> {
            check(helper, pigsAround(helper) == 0, "an OFF spawner must not spawn, found " + pigsAround(helper));
            check(helper, countdown.getAsInt() == before[0], "nor count down to a spawn with a player beside it: "
                    + before[0] + " -> " + countdown.getAsInt());
        }).thenSucceed();
    }

    /**
     * A player beside the test spawner, and its countdown to a spawn to read. A spawner left to vanilla counts down with
     * a player near; one held off does not. No pig alone proves nothing: vanilla's own rules want a player near, and
     * grass under a pig, and the arena has neither.
     */
    static IntSupplier spawnerCountdown(GameTestHelper helper) {
        var spawner = helper.getBlockEntity(SPAWNER_POS, SpawnerBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 near = Vec3.atBottomCenterOf(spawner.getBlockPos().east(2));
        player.snapTo(near.x, near.y, near.z);
        return () -> spawner.saveWithoutMetadata(helper.getLevel().registryAccess()).getShortOr("Delay", (short) -1);
    }

    static void controllerMenuButtons(GameTestHelper helper) {
        SpawnerControllerBlockEntity controller = buildControl(helper, SpawnerSettings.DEFAULT, 0);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SpawnerControllerMenu menu = (SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, SpawnerControllerMenu.TOGGLE_ENABLED);
        menu.clickMenuButton(player, SpawnerControllerMenu.INTERVAL_UP);
        for (int i = 0; i < 30; i++) {
            menu.clickMenuButton(player, SpawnerControllerMenu.COUNT_UP);
        }
        menu.clickMenuButton(player, SpawnerControllerMenu.TOGGLE_PLAYER);
        SpawnerSettings s = controller.settings();
        check(helper, !s.enabled(), "toggled off");
        check(helper, s.interval() == 15 * 20, "10 s + one step = 15 s, got " + s.interval() / 20);
        check(helper, s.count() == SpawnerSettings.MAX_COUNT, "count clamps at the maximum");
        check(helper, !s.needsPlayer(), "player no longer needed");
        check(helper, SpawnerSettings.DEFAULT.withInterval(20).stepInterval(-1).interval() == SpawnerSettings.MIN_INTERVAL, "interval clamps at 1 s");
        helper.succeed();
    }

    /**
     * The console works set on top of its receiver, which takes the FE - not beside it (a cable's arm would stand off
     * the console's sloped side) - and only there does a click open its screen. Its screen lights while it runs, with
     * a glow short of a torch's, and goes dark off power.
     */
    static void controllerWorksOnItsReceiver(GameTestHelper helper) {
        var level = helper.getLevel();
        SpawnerControllerBlockEntity onTop = buildControl(helper, SpawnerSettings.DEFAULT, 1_000);
        BlockPos receiverPos = new BlockPos(0, 1, 0);
        BlockPos controllerPos = receiverPos.above();
        check(helper, level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(receiverPos), Direction.NORTH) != null,
                "the receiver takes FE from a cable");
        check(helper, level.getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(controllerPos), Direction.NORTH) == null,
                "the console itself takes no cable");
        check(helper, !helper.getBlockState(controllerPos).getValue(SpawnerControllerBlock.LIT), "a new console is dark");
        onTop.update(level);
        check(helper, onTop.linked() == 1 && onTop.powered(), "on its receiver it controls the spawner");
        BlockState lit = helper.getBlockState(controllerPos);
        check(helper, lit.getValue(SpawnerControllerBlock.LIT), "and its screen is on");
        check(helper, lit.getLightEmission() > 0 && lit.getLightEmission() < 14, "a glow short of a torch's: " + lit.getLightEmission());
        check(helper, helper.getBlockState(receiverPos).getValue(SpawnerLinkReceiverBlock.LIT),
                "the receiver's lines light with it");
        BlockPos senderPos = SPAWNER_POS.above();
        check(helper, helper.getBlockState(senderPos).getValue(SpawnerLinkSenderBlock.LIT)
                && helper.getBlockState(senderPos).getLightEmission() > 0, "and the sender's, far off as it may be");
        check(helper, onTop.energy().getAmountAsLong() == 1_000 - SpawnerControllerBlockEntity.PERIOD * SpawnerSettings.BASE_FE_PER_TICK,
                "it pays from the receiver's FE, left " + onTop.energy().getAmountAsLong());

        onTop.energy().set(0);
        onTop.update(level);
        check(helper, !onTop.powered() && !helper.getBlockState(controllerPos).getValue(SpawnerControllerBlock.LIT), "off power, dark");
        check(helper, !helper.getBlockState(receiverPos).getValue(SpawnerLinkReceiverBlock.LIT), "the receiver too");

        ServerPlayer player = MockPlayers.inLevel(helper);
        helper.useBlock(controllerPos, player);
        check(helper, player.containerMenu instanceof SpawnerControllerMenu, "a click on the console on its receiver opens it");
        player.closeContainer();

        // Beside the receiver instead: nothing linked, nothing paid, dark, and no screen.
        helper.setBlock(controllerPos, Blocks.AIR);
        BlockPos beside = receiverPos.east();
        helper.setBlock(beside, ModBlocks.SPAWNER_CONTROLLER.get());
        var aside = helper.getBlockEntity(beside, SpawnerControllerBlockEntity.class);
        helper.getBlockEntity(receiverPos, SpawnerLinkReceiverBlockEntity.class).energy().set(1_000);
        aside.update(level);
        check(helper, aside.receiver() == null && aside.linked() == 0 && !aside.powered(), "beside the receiver it controls nothing");
        check(helper, helper.getBlockEntity(receiverPos, SpawnerLinkReceiverBlockEntity.class).energy().getAmountAsLong() == 1_000,
                "and takes nothing from it");
        check(helper, !helper.getBlockState(beside).getValue(SpawnerControllerBlock.LIT), "and stays dark");
        helper.useBlock(beside, player);
        check(helper, player.containerMenu == player.inventoryMenu, "a click on it opens nothing");
        level.getServer().getPlayerList().remove(player);
        // nothing runs through the sender any more: once its last lease is up, its lines go dark of themselves
        helper.runAfterDelay(40, () -> {
            check(helper, !helper.getBlockState(senderPos).getValue(SpawnerLinkSenderBlock.LIT), "the sender goes dark");
            helper.succeed();
        });
    }

    /** The console is a desk; it must face the player when placed, and the wrench must turn it. */
    static void controllerFacesThePlayer(GameTestHelper helper) {
        // y=1 sits on the arena floor, which is what placeByHand clicks.
        BlockPos pos = new BlockPos(3, 1, 3);
        var facing = SpawnerControllerBlock.FACING;
        // Yaw 0 is south, 90 west, 180 north, 270 east. The screen faces the player, so the opposite.
        float[] yaws = {0, 90, 180, 270};
        Direction[] expected = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < yaws.length; i++) {
            helper.setBlock(pos, Blocks.AIR);
            placeControllerLooking(helper, pos, yaws[i]);
            BlockState state = helper.getBlockState(pos);
            check(helper, state.is(ModBlocks.SPAWNER_CONTROLLER.get()),
                    "looking " + Direction.fromYRot(yaws[i]).getName() + ", the controller should have been placed");
            check(helper, state.getValue(facing) == expected[i],
                    "looking " + Direction.fromYRot(yaws[i]).getName() + ", the screen should face "
                            + expected[i].getName() + ", got " + state.getValue(facing).getName());
        }
        BlockState turned = WrenchItem.turn(helper.getBlockState(pos), false);
        check(helper, turned.getValue(facing) == Direction.NORTH,
                "the wrench turns the console clockwise, got " + turned.getValue(facing).getName());
        helper.succeed();
    }

    static void placeControllerLooking(GameTestHelper helper, BlockPos pos, float yaw) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setYRot(yaw);
        ItemStack stack = new ItemStack(ModItems.SPAWNER_CONTROLLER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = helper.absolutePos(pos.below());
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
    }

    private StorageTests() {}
}
