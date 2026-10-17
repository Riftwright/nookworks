package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.TransportTests.countIn;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterSettings;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The user's two Sorter requests of 2026-10-06: a sorter draws from the container above it, as a hopper does, so a
 * sorting store can be a chest set on a sorter ("せめてホッパーみたいに、上にあるチェストや樽から自動で引くようにできない？");
 * and the Precision Sorter's picker offers a whole mod beside the sample's tags ("バニラのアイテムとnookworksのアイテムを
 * 仕分ける、とか").
 */
final class R9SorterTests {
    private R9SorterTests() {}

    static void register() {
        NookworksGameTests.test("sorter_draws_from_the_chest_above_and_sorts_both_ways", R9SorterTests::drawsFromChestAbove);
        NookworksGameTests.test("sorter_stops_drawing_while_its_outlet_is_full", 200, R9SorterTests::stopsWhileOutletFull);
        NookworksGameTests.test("sorter_draws_from_a_storage_barrel_above", R9SorterTests::drawsFromStorageBarrel);
        NookworksGameTests.test("sorter_leaves_a_sorter_above_alone", R9SorterTests::leavesSorterAboveAlone);
        NookworksGameTests.test("precision_sorter_sorts_by_mod", R9SorterTests::sortsByMod);
        NookworksGameTests.test("old_sorter_filters_load_unchanged", R9SorterTests::oldFiltersLoad);
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

    /**
     * A chest on an Item Sorter with no inventory feeding it: the sorter draws everything out, a hopper's pace, and
     * sends the diamonds it has registered to its right and the dirt to its left.
     */
    private static void drawsFromChestAbove(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.DIAMOND, 2));
        above.setItem(5, new ItemStack(Items.DIRT, 3));

        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.west()), Items.DIAMOND) == 2, "the diamonds go right: "
                    + countIn(chest(helper, pos.west()), Items.DIAMOND));
            check(helper, countIn(chest(helper, pos.east()), Items.DIRT) == 3, "the dirt goes left: "
                    + countIn(chest(helper, pos.east()), Items.DIRT));
            check(helper, above.isEmpty(), "and the chest above is emptied");
        });
    }

    /**
     * The outlet registered items go to is full: the sorter draws one batch (a Netherite gear's four) and stops with it,
     * the rest left in the chest above - the dirt behind the diamonds too, though its own outlet has room (the user,
     * 2026-10-06: 排出先が満杯なら、ソーター自体が止まる挙動が自然かな。在庫管理大事). Room made, it carries on and empties the chest.
     */
    private static void stopsWhileOutletFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        sorter.gearSlot().setItem(0, new ItemStack(ModItems.NETHERITE_SORTER_GEAR.get()));
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container full = chest(helper, pos.west());
        for (int i = 0; i < full.getContainerSize(); i++) {
            full.setItem(i, new ItemStack(Items.STONE, 64));
        }
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(Items.DIAMOND, 10));
        above.setItem(1, new ItemStack(Items.DIRT, 2));

        helper.startSequence()
                .thenExecuteAfter(20, () -> {
                    check(helper, sorter.getItem(0).is(Items.DIAMOND) && sorter.getItem(0).getCount() == 4,
                            "it holds one batch it cannot send: " + sorter.getItem(0));
                    check(helper, countIn(above, Items.DIAMOND) == 6 && countIn(above, Items.DIRT) == 2,
                            "the rest stays in the chest above: " + countIn(above, Items.DIAMOND) + " diamonds");
                    check(helper, chest(helper, pos.east()).isEmpty(), "the dirt waits behind the diamonds");
                    full.setItem(0, ItemStack.EMPTY); // room for a stack
                })
                .thenWaitUntil(() -> {
                    check(helper, countIn(full, Items.DIAMOND) == 10, "with room, the diamonds go: " + countIn(full, Items.DIAMOND));
                    check(helper, countIn(chest(helper, pos.east()), Items.DIRT) == 2, "and the dirt after them");
                    check(helper, above.isEmpty() && sorter.isEmpty(), "nothing is left above or in the sorter");
                })
                .thenSucceed();
    }

    /** A Storage Barrel on a sorter, a slot of it more than a stack: drawn out a gear's batch at a time, and sorted. */
    private static void drawsFromStorageBarrel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        sorter.gearSlot().setItem(0, new ItemStack(ModItems.NETHERITE_SORTER_GEAR.get()));
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), ModBlocks.IRON_STORAGE.get());
        var barrel = helper.getBlockEntity(pos.above(), StorageBlockEntity.class);
        barrel.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 100);
        barrel.inventory().set(1, ItemResource.of(Items.DIAMOND), 5);

        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.east()), Items.COBBLESTONE) == 100, "the cobblestone goes left: "
                    + countIn(chest(helper, pos.east()), Items.COBBLESTONE));
            check(helper, countIn(chest(helper, pos.west()), Items.DIAMOND) == 5, "the diamonds right");
            check(helper, StorageTests.storageTotal(barrel, Items.COBBLESTONE) == 0
                    && StorageTests.storageTotal(barrel, Items.DIAMOND) == 0, "the barrel is emptied");
        });
    }

    /**
     * A sorter on a sorter takes nothing out of it: what the one above holds is for its own outlets (both walled up
     * here, so it keeps it).
     */
    private static void leavesSorterAboveAlone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        sorter(helper, pos, ModBlocks.ITEM_SORTER.get());
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        var upper = sorter(helper, pos.above(), ModBlocks.PRECISION_SORTER.get());
        helper.setBlock(pos.above().west(), Blocks.STONE);
        helper.setBlock(pos.above().east(), Blocks.STONE);
        upper.setItem(0, new ItemStack(Items.DIAMOND, 3));

        helper.runAfterDelay(40, () -> {
            check(helper, upper.getItem(0).getCount() == 3, "the upper sorter keeps its diamonds: " + upper.getItem(0));
            check(helper, chest(helper, pos.west()).isEmpty() && chest(helper, pos.east()).isEmpty(), "the lower one sent nothing");
            helper.succeed();
        });
    }

    /**
     * A Precision Sorter's picker offers the sample's mod above its tags. Ticked and registered, it is one entry, marked
     * by the mod it stands for, that catches every vanilla item (Minecraft's, for an iron ingot) and none of ours: the
     * cobblestone in the chest above goes right and the Brass Ingots left. Exclude turns that round; Exact keeps to
     * items in their sample's condition, as it does for a tag entry. The same mod twice is one entry; a Nookworks
     * sample offers Nookworks; an Item Sorter has no picker.
     */
    private static void sortsByMod(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var sorter = sorter(helper, pos, ModBlocks.PRECISION_SORTER.get());
        sorter.filter().setItem(0, ItemStack.EMPTY);
        ServerPlayer player = MockPlayers.inLevel(helper);
        var menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        check(helper, "minecraft".equals(menu.sampleMod()), "an iron ingot's mod is Minecraft: " + menu.sampleMod());
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE_MOD);
        check(helper, menu.modChosen() && menu.anyChosen(), "the mod's line is ticked");
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_TAGS);
        ItemStack entry = sorter.filter().getItem(0);
        check(helper, "minecraft".equals(SorterTags.modOf(entry)) && SorterTags.of(entry) == null && entry.is(Items.IRON_INGOT),
                "Register puts in a mod entry, drawn as its sample: " + entry);
        check(helper, sorter.isRegistered(new ItemStack(Items.COBBLESTONE)) && sorter.isRegistered(new ItemStack(Items.DIAMOND_SWORD))
                && !sorter.isRegistered(new ItemStack(ModItems.BRASS_INGOT.get())), "it catches vanilla items, not ours");

        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        check(helper, menu.choices().contains(Tags.Items.GEMS_DIAMOND), "the sample's tags are listed still");
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE_MOD);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_TAGS);
        check(helper, sorter.filter().getItem(1).isEmpty(), "the same mod twice is one entry");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(ModItems.BRASS_INGOT.get()));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        check(helper, "nookworks".equals(menu.sampleMod()), "a Brass Ingot's mod is Nookworks: " + menu.sampleMod());
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_CLOSE);
        menu.setCarried(ItemStack.EMPTY);

        check(helper, SorterTags.modName("minecraft").equals("Minecraft") && SorterTags.modName("nookworks").equals("Nookworks"),
                "named as the mod list names them: " + SorterTags.modName("minecraft"));
        check(helper, SorterTags.modName("a_mod_taken_out").equals("a_mod_taken_out"), "a mod not installed by its id");
        check(helper, SorterTags.modSize("minecraft") > 1000 && !SorterTags.modMembers("minecraft", 1).getFirst().is(Items.AIR),
                "vanilla's count leaves air out: " + SorterTags.modSize("minecraft"));

        BlockPos itemSorterPos = new BlockPos(1, 1, 6);
        var itemSorter = sorter(helper, itemSorterPos, ModBlocks.ITEM_SORTER.get());
        var plainMenu = (SorterMenu) itemSorter.createMenu(2, player.getInventory(), player);
        check(helper, !plainMenu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE_MOD), "an Item Sorter has no picker");

        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = chest(helper, pos.above());
        above.setItem(0, new ItemStack(ModItems.BRASS_INGOT.get(), 2));
        above.setItem(1, new ItemStack(Items.COBBLESTONE, 2));
        helper.succeedWhen(() -> {
            check(helper, countIn(chest(helper, pos.west()), Items.COBBLESTONE) == 2, "vanilla cobblestone goes right");
            check(helper, countIn(chest(helper, pos.east()), ModItems.BRASS_INGOT.get()) == 2, "the Brass Ingots left");

            sorter.settings().toggle(SorterSettings.BLACKLIST);
            boolean flipped = !sorter.isRegistered(new ItemStack(Items.COBBLESTONE))
                    && sorter.isRegistered(new ItemStack(ModItems.BRASS_INGOT.get()));
            sorter.settings().toggle(SorterSettings.BLACKLIST);
            check(helper, flipped, "Exclude sends vanilla items left, ours right");
            sorter.settings().toggle(SorterSettings.MATCH_COMPONENTS);
            ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
            worn.set(DataComponents.DAMAGE, 10);
            boolean exact = sorter.isRegistered(new ItemStack(Items.IRON_PICKAXE)) && !sorter.isRegistered(worn);
            sorter.settings().toggle(SorterSettings.MATCH_COMPONENTS);
            check(helper, exact, "Exact catches a fresh vanilla pickaxe and not a worn one");
        });
    }

    /**
     * A Precision Sorter's filter as 0.6.0 saved it - a tag entry, an item, a worn item, its switches - loads as it
     * was and saves the same again; a mod entry keeps its mod through a save and a load.
     */
    private static void oldFiltersLoad(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.PRECISION_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        helper.setBlock(pos.west(), Blocks.STONE); // nothing moves while it is looked at
        helper.setBlock(pos.east(), Blocks.STONE);
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved;
        try {
            saved = TagParser.parseCompoundFully("{Items:[{Slot:0b,id:\"minecraft:cobblestone\",count:3}],"
                    + "Filter:{Items:[{Slot:0b,id:\"minecraft:iron_ingot\",count:1,components:{\"nookworks:sorter_tag\":\"c:ingots/iron\"}},"
                    + "{Slot:1b,id:\"minecraft:diamond\",count:1},"
                    + "{Slot:2b,id:\"minecraft:iron_pickaxe\",count:1,components:{\"minecraft:damage\":5}}]},"
                    + "Gear:{Items:[]},Settings:{Flag0:0b,Flag1:0b,Flag2:1b}}");
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(e);
        }
        try (var reporter = new ProblemReporter.ScopedCollector(sorter.problemPath(), LogUtils.getLogger())) {
            sorter.loadWithComponents(TagValueInput.create(reporter, registries, saved));
        }
        ItemStack tagEntry = sorter.filter().getItem(0);
        check(helper, Tags.Items.INGOTS_IRON.equals(SorterTags.of(tagEntry)) && SorterTags.modOf(tagEntry) == null,
                "the tag entry is as it was: " + tagEntry);
        check(helper, sorter.filter().getItem(1).is(Items.DIAMOND) && SorterTags.of(sorter.filter().getItem(1)) == null
                && SorterTags.modOf(sorter.filter().getItem(1)) == null, "the diamond is the diamond");
        check(helper, sorter.filter().getItem(2).getDamageValue() == 5, "the worn pickaxe is worn");
        check(helper, sorter.settings().get(SorterSettings.MATCH_COMPONENTS) && !sorter.settings().get(SorterSettings.BLACKLIST)
                && sorter.getItem(0).getCount() == 3, "the switches and the load are kept");
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(5);
        check(helper, sorter.isRegistered(new ItemStack(Items.IRON_INGOT)) && sorter.isRegistered(new ItemStack(Items.DIAMOND))
                && sorter.isRegistered(worn) && !sorter.isRegistered(new ItemStack(Items.IRON_PICKAXE))
                && !sorter.isRegistered(new ItemStack(Items.GOLD_INGOT)), "and it sorts as it did");
        CompoundTag again = sorter.saveWithoutMetadata(registries);
        again.remove("components"); // the block entity's own components (it has none), written beside its data
        check(helper, again.equals(saved), "saved again, the same: " + again);

        sorter.filter().setItem(3, SorterTags.modEntry(new ItemStack(Items.STONE)));
        CompoundTag withMod = sorter.saveWithoutMetadata(registries);
        BlockPos other = new BlockPos(1, 1, 6);
        helper.setBlock(other, ModBlocks.PRECISION_SORTER.get());
        var copy = helper.getBlockEntity(other, SorterBlockEntity.class);
        try (var reporter = new ProblemReporter.ScopedCollector(copy.problemPath(), LogUtils.getLogger())) {
            copy.loadWithComponents(TagValueInput.create(reporter, registries, withMod));
        }
        ItemStack modEntry = copy.filter().getItem(3);
        check(helper, "minecraft".equals(modEntry.get(ModDataComponents.SORTER_MOD.get())) && modEntry.is(Items.STONE),
                "a mod entry keeps its mod: " + modEntry);
        helper.succeed();
    }
}
