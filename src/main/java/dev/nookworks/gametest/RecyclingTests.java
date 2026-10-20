package dev.nookworks.gametest;

import static dev.nookworks.gametest.MachineTests.placeByHand;
import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.nookworks.Nookworks;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.recycling.CompressorBlockEntity;
import dev.nookworks.recycling.CorrugatedSheetBlock;
import dev.nookworks.recycling.RecyclerBlock;
import dev.nookworks.recycling.RecyclerBlockEntity;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModRecycling;
import dev.nookworks.sorter.SorterTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Recycling (DECISIONS, "Recycling", 2026-10-18): the Compressor, the Recycler, scrap and what is built of it. */
final class RecyclingTests {
    static final TagKey<Item> CRUSHABLE = TagKey.create(Registries.ITEM, Nookworks.id("crushable"));
    static final TagKey<Item> RECYCLABLE = TagKey.create(Registries.ITEM, Nookworks.id("recyclable"));
    static final TagKey<Item> COMPRESSIBLE = TagKey.create(Registries.ITEM, Nookworks.id("compressible"));
    static final TagKey<Item> SIFTABLE = TagKey.create(Registries.ITEM, Nookworks.id("siftable"));

    /** The tests that try the recycling part, passed over when a pack switches it off. */
    static final String[] NEED_RECYCLING = {"compressor_packs_nine_into_one", "compressor_waits_for_nine",
            "compressor_takes_any_ninefold_recipe", "recycler_gives_back_half_the_metal", "recycler_worn_and_gold",
            "recycler_takes_metal_only", "recycler_halves_stand_together", "recycler_fed_through_its_intake",
            "scrap_rusts_waxes_and_scrapes", "corrugated_sheet_lies_on_the_floor", "scrap_recipes", "sorting_tags_match_the_machines",
            "recycler_broken_by_hand_drops_nothing", "recycler_drops_its_output_on_top", "recycler_refuses_what_holds_things",
            "recycler_drops_beside_under_a_hopper", "wrench_moves_a_sheet_to_the_middle_and_back", "recycler_intake_is_the_body",
            "a_lone_intake_is_not_the_machine_under_it", "recycler_broken_with_a_pickaxe_drops_one",
            "recycler_keeps_its_work_through_a_save"};

    static void register() {
        test("compressor_packs_nine_into_one", RecyclingTests::compressorPacksNine);
        test("compressor_waits_for_nine", 400, RecyclingTests::compressorWaitsForNine);
        test("compressor_takes_other_kinds_beside_a_few_waiting", 400, RecyclingTests::compressorFourKinds);
        test("recycler_takes_nookworks_metal_things", RecyclingTests::recyclerTakesOurs);
        test("compressor_takes_any_ninefold_recipe", RecyclingTests::compressorTakesNinefold);
        test("recycler_gives_back_half_the_metal", 300, RecyclingTests::recyclerGivesBackHalf);
        test("recycler_worn_and_gold", RecyclingTests::recyclerWornAndGold);
        test("recycler_takes_metal_only", RecyclingTests::recyclerTakesMetalOnly);
        test("recycler_halves_stand_together", 200, RecyclingTests::recyclerHalvesStandTogether);
        test("recycler_fed_through_its_intake", 300, RecyclingTests::recyclerFedThroughIntake);
        test("recycler_broken_by_hand_drops_nothing", 100, RecyclingTests::recyclerBrokenByHand);
        test("recycler_drops_its_output_on_top", 300, RecyclingTests::recyclerDropsOnTop);
        test("recycler_refuses_what_holds_things", RecyclingTests::recyclerRefusesWhatHoldsThings);
        test("recycler_drops_beside_under_a_hopper", 300, RecyclingTests::recyclerDropsBesideUnderAHopper);
        test("wrench_moves_a_sheet_to_the_middle_and_back", RecyclingTests::wrenchMovesASheet);
        test("recycler_intake_is_the_body", RecyclingTests::recyclerIntakeIsTheBody);
        test("a_lone_intake_is_not_the_machine_under_it", RecyclingTests::loneIntake);
        test("recycler_broken_with_a_pickaxe_drops_one", 100, RecyclingTests::recyclerBrokenWithAPickaxe);
        test("recycler_keeps_its_work_through_a_save", 200, RecyclingTests::recyclerKeepsItsWork);
        test("recycler_waits_with_both_outputs_full", 300, RecyclingTests::recyclerWaitsWhenFull);
        test("scrap_rusts_waxes_and_scrapes", RecyclingTests::scrapRusts);
        test("corrugated_sheet_lies_on_the_floor", RecyclingTests::sheetLiesFlat);
        test("scrap_recipes", RecyclingTests::scrapRecipes);
        test("sorting_tags_match_the_machines", RecyclingTests::sortingTagsMatch);
    }

    private static <T extends MachineBlockEntity> T powered(GameTestHelper helper, BlockPos pos, Class<T> type) {
        T machine = helper.getBlockEntity(pos, type);
        var energy = machine.energy();
        energy.set((int) energy.getCapacityAsLong());
        return machine;
    }

    /** Eighteen iron nuggets in: two ingots out, below. */
    static void compressorPacksNine(GameTestHelper helper) {
        BlockPos press = new BlockPos(2, 2, 2);
        helper.setBlock(press, ModRecycling.COMPRESSOR.get());
        helper.setBlock(press.below(), Blocks.CHEST);
        powered(helper, press, CompressorBlockEntity.class).setItem(CompressorBlockEntity.INPUT, new ItemStack(Items.IRON_NUGGET, 18));
        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(press.below(), ChestBlockEntity.class), Items.IRON_INGOT) == 2,
                "eighteen nuggets should come out as two ingots"));
    }

    /** Eight is not nine: it waits, keeps them, and makes nothing. */
    static void compressorWaitsForNine(GameTestHelper helper) {
        BlockPos press = new BlockPos(2, 2, 2);
        helper.setBlock(press, ModRecycling.COMPRESSOR.get());
        helper.setBlock(press.below(), Blocks.CHEST);
        var machine = powered(helper, press, CompressorBlockEntity.class);
        machine.setItem(CompressorBlockEntity.INPUT, new ItemStack(ModRecycling.SCRAP.get(), 8));
        helper.runAfterDelay(120, () -> {
            check(helper, machine.getItem(CompressorBlockEntity.INPUT).getCount() == 8, "the eight should still be waiting");
            check(helper, machine.guiStatus() == dev.nookworks.machine.MachineStatus.NEEDS_NINE, "and say so: " + machine.guiStatus());
            check(helper, countIn(helper.getBlockEntity(press.below(), ChestBlockEntity.class), ModRecycling.BUILDING_ITEMS.get("scrap_bale").get()) == 0,
                    "and nothing made of them");
            machine.setItem(CompressorBlockEntity.INPUT, new ItemStack(ModRecycling.SCRAP.get(), 9));
            helper.succeedWhen(() -> check(helper,
                    countIn(helper.getBlockEntity(press.below(), ChestBlockEntity.class), ModRecycling.BUILDING_ITEMS.get("scrap_bale").get()) == 1,
                    "nine scrap should make a Scrap Bale"));
        });
    }

    /**
     * Eight iron nuggets waiting for a ninth do not stop gold nuggets coming in beside them (the user, 2026-10-20): a
     * hopper's gold goes into another input slot and is pressed; one kind is not spread over two slots.
     */
    static void compressorFourKinds(GameTestHelper helper) {
        BlockPos press = new BlockPos(2, 2, 2);
        helper.setBlock(press, ModRecycling.COMPRESSOR.get());
        helper.setBlock(press.below(), Blocks.CHEST);
        var machine = powered(helper, press, CompressorBlockEntity.class);
        machine.setItem(CompressorBlockEntity.INPUT, new ItemStack(Items.IRON_NUGGET, 8));
        int[] sides = machine.getSlotsForFace(Direction.UP);
        int goldSlot = -1;
        for (int slot : sides) {
            if (machine.canPlaceItem(slot, new ItemStack(Items.GOLD_NUGGET))) {
                goldSlot = slot;
                break;
            }
        }
        check(helper, goldSlot > 0 && goldSlot != CompressorBlockEntity.OUTPUT, "gold has a slot of its own beside the iron: " + goldSlot);
        int ironElsewhere = 0;
        for (int slot : sides) {
            ironElsewhere += slot != CompressorBlockEntity.INPUT && machine.canPlaceItem(slot, new ItemStack(Items.IRON_NUGGET)) ? 1 : 0;
        }
        check(helper, ironElsewhere == 0, "iron goes only where iron is");
        machine.setItem(goldSlot, new ItemStack(Items.GOLD_NUGGET, 9));
        helper.succeedWhen(() -> {
            check(helper, countIn(helper.getBlockEntity(press.below(), ChestBlockEntity.class), Items.GOLD_INGOT) == 1, "the gold pressed");
            check(helper, machine.getItem(CompressorBlockEntity.INPUT).getCount() == 8, "the iron still waiting");
        });
    }

    /** Nookworks' own metal things go into the Recycler too: a Bookend, a Hook, a gear, a Generator; a wall shelf does not. */
    static void recyclerTakesOurs(GameTestHelper helper) {
        for (String id : List.of("bookend", "hook", "gold_sorter_gear", "generator", "machine_chassis")) {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Nookworks.MODID, id));
            check(helper, RecyclingRecipe.of(new ItemStack(item)) != null, id + " is recycled");
        }
        Item shelf = BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath(Nookworks.MODID, "oak_wall_shelf"));
        check(helper, RecyclingRecipe.of(new ItemStack(shelf)) == null, "a wall shelf, mostly wood, is not");
        helper.succeed();
    }

    /**
     * Whatever the crafting grid packs nine of into one, the Compressor does too (and a hopper can feed it), and
     * nothing else: #nookworks:compressible is exactly what it takes, vanilla's and ours.
     */
    static void compressorTakesNinefold(GameTestHelper helper) {
        var level = helper.getLevel();
        check(helper, CompressorBlockEntity.resultOf(level, new ItemStack(Items.DIAMOND)).is(Items.DIAMOND_BLOCK), "diamonds to a block");
        check(helper, CompressorBlockEntity.resultOf(level, new ItemStack(Items.WHEAT)).is(Items.HAY_BLOCK), "wheat to hay");
        check(helper, CompressorBlockEntity.resultOf(level, new ItemStack(Items.QUARTZ)).isEmpty(), "quartz packs by four, not nine");
        check(helper, CompressorBlockEntity.resultOf(level, new ItemStack(Items.IRON_BLOCK)).isEmpty(), "a block unpacks, it does not pack");
        BlockPos press = new BlockPos(2, 2, 2);
        helper.setBlock(press, ModRecycling.COMPRESSOR.get());
        var machine = helper.getBlockEntity(press, CompressorBlockEntity.class);
        check(helper, machine.canPlaceItem(CompressorBlockEntity.INPUT, new ItemStack(Items.GOLD_NUGGET)), "a hopper may feed nuggets");
        check(helper, !machine.canPlaceItem(CompressorBlockEntity.INPUT, new ItemStack(Items.DIRT)), "but not dirt");
        List<String> wrong = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String ns = BuiltInRegistries.ITEM.getKey(item).getNamespace();
            if (!ns.equals("minecraft") && !ns.equals(Nookworks.MODID)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.is(COMPRESSIBLE) == CompressorBlockEntity.resultOf(level, stack).isEmpty()) {
                wrong.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
        }
        check(helper, wrong.isEmpty(), "#nookworks:compressible should be just what the Compressor takes; wrong for " + wrong);
        helper.succeed();
    }

    /** A Recycler set down by hand on the floor (y 0) at {@code pos}, its intake over it. */
    private static BlockPos recycler(GameTestHelper helper, BlockPos pos) {
        placeByHand(helper, new ItemStack(ModRecycling.RECYCLER_ITEM.get()), pos);
        BlockState lower = helper.getBlockState(pos);
        BlockState upper = helper.getBlockState(pos.above());
        check(helper, lower.is(ModRecycling.RECYCLER.get()) && lower.getValue(RecyclerBlock.HALF) == DoubleBlockHalf.LOWER,
                "the Recycler's body should stand where it was set, found " + lower);
        check(helper, upper.is(ModRecycling.RECYCLER.get()) && upper.getValue(RecyclerBlock.HALF) == DoubleBlockHalf.UPPER,
                "and its intake on it, found " + upper);
        return pos;
    }

    /** A new iron chestplate (eight ingots) gives back four scrap, into the chest below. */
    static void recyclerGivesBackHalf(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        helper.setBlock(body.below(), Blocks.CHEST);
        powered(helper, body, RecyclerBlockEntity.class).setItem(RecyclerBlockEntity.INPUT, new ItemStack(Items.IRON_CHESTPLATE));
        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(body.below(), ChestBlockEntity.class), ModRecycling.SCRAP.get()) == 4,
                "an iron chestplate should come back as four scrap"));
    }

    /** Half worn, half as much; gold comes back as nuggets, a golden helmet's five ingots as 22 or 23. */
    static void recyclerWornAndGold(GameTestHelper helper) {
        var random = helper.getLevel().getRandom();
        ItemStack worn = new ItemStack(Items.IRON_CHESTPLATE);
        worn.setDamageValue(worn.getMaxDamage() / 2);
        ItemStack out = RecyclingRecipe.of(worn).outputFor(worn, random);
        check(helper, out.is(ModRecycling.SCRAP.get()) && out.getCount() == 2, "a half-worn chestplate should give two scrap, gave " + out);
        for (int i = 0; i < 20; i++) {
            ItemStack gold = RecyclingRecipe.of(new ItemStack(Items.GOLDEN_HELMET)).outputFor(new ItemStack(Items.GOLDEN_HELMET), random);
            check(helper, gold.is(Items.GOLD_NUGGET) && (gold.getCount() == 22 || gold.getCount() == 23),
                    "a golden helmet should give 22 or 23 gold nuggets, gave " + gold);
        }
        ItemStack rod = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("waxed_weathered_lightning_rod")));
        check(helper, RecyclingRecipe.of(rod) != null, "copper junk goes in at any age, waxed or not: " + rod);
        ItemStack copper = RecyclingRecipe.of(rod).outputFor(rod, random);
        check(helper, copper.is(ModRecycling.SCRAP.get()), "copper comes back as scrap too, gave " + copper);
        helper.succeed();
    }

    /** Metal only: no gravel, dirt or wood, and no netherite (the user, 2026-10-18). */
    static void recyclerTakesMetalOnly(GameTestHelper helper) {
        for (Item no : List.of(Items.GRAVEL, Items.DIRT, Items.OAK_PLANKS, Items.NETHERITE_SWORD, Items.NETHERITE_CHESTPLATE,
                Items.DIAMOND_SWORD, Items.STONE_PICKAXE, Items.IRON_INGOT)) {
            check(helper, RecyclingRecipe.of(new ItemStack(no)) == null, no + " must not go in the Recycler");
        }
        for (Item yes : List.of(Items.IRON_SWORD, Items.BUCKET, Items.MINECART, Items.ANVIL, Items.RAIL, Items.CHAINMAIL_HELMET,
                Items.COPPER_PICKAXE, Items.GOLDEN_HOE, Items.POWERED_RAIL)) {
            check(helper, RecyclingRecipe.of(new ItemStack(yes)) != null, yes + " should go in the Recycler");
        }
        helper.succeed();
    }

    /** Either half broken, the other goes; the body drops the Recycler, once. */
    static void recyclerHalvesStandTogether(GameTestHelper helper) {
        BlockPos a = recycler(helper, new BlockPos(1, 1, 2));
        BlockPos b = recycler(helper, new BlockPos(4, 1, 2));
        helper.getLevel().destroyBlock(helper.absolutePos(a.above()), true);
        helper.getLevel().destroyBlock(helper.absolutePos(b), true);
        check(helper, helper.getBlockState(a).isAir(), "breaking the intake should take the body with it");
        check(helper, helper.getBlockState(b.above()).isAir(), "breaking the body should take the intake with it");
        helper.succeedWhen(() -> {
            int dropped = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                    e -> e.getItem().is(ModRecycling.RECYCLER_ITEM.get())).stream().mapToInt(e -> e.getItem().getCount()).sum();
            check(helper, dropped == 2, "each Recycler should drop itself once, dropped " + dropped);
        });
    }

    private static int droppedRecyclers(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                new net.minecraft.world.phys.AABB(helper.absolutePos(BlockPos.ZERO)).inflate(8),
                e -> e.getItem().is(ModRecycling.RECYCLER_ITEM.get())).stream().mapToInt(e -> e.getItem().getCount()).sum();
    }

    /**
     * Its intake broken by a bare hand, the Recycler goes and drops nothing, as a door broken without the tool it
     * needs (the self-review, 2026-10-18: the body's loot dropped through its neighbour update).
     */
    static void recyclerBrokenByHand(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.gameMode.destroyBlock(helper.absolutePos(body.above()));
        check(helper, helper.getBlockState(body).isAir() && helper.getBlockState(body.above()).isAir(), "both halves should be gone");
        helper.runAfterDelay(5, () -> {
            check(helper, droppedRecyclers(helper) == 0, "and no Recycler dropped, found " + droppedRecyclers(helper));
            helper.succeed();
        });
    }

    /** Its intake broken with a pickaxe, the Recycler drops itself, once. */
    static void recyclerBrokenWithAPickaxe(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        player.gameMode.destroyBlock(helper.absolutePos(body.above()));
        check(helper, helper.getBlockState(body).isAir() && helper.getBlockState(body.above()).isAir(), "both halves should be gone");
        helper.runAfterDelay(5, () -> {
            check(helper, droppedRecyclers(helper) == 1, "and one Recycler dropped, found " + droppedRecyclers(helper));
            helper.succeed();
        });
    }

    /** Saved halfway through a piece and loaded again, it holds the piece, its energy and how far it had got. */
    static void recyclerKeepsItsWork(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        helper.setBlock(body.below(), Blocks.CHEST);
        var machine = powered(helper, body, RecyclerBlockEntity.class);
        machine.setItem(RecyclerBlockEntity.INPUT, new ItemStack(Items.IRON_CHESTPLATE, 1));
        helper.runAfterDelay(40, () -> {
            int progress = machine.guiProgress();
            check(helper, progress > 0, "it should be working");
            var registries = helper.getLevel().registryAccess();
            var saved = machine.saveWithoutMetadata(registries);
            BlockPos other = new BlockPos(5, 1, 2);
            recycler(helper, other);
            var copy = helper.getBlockEntity(other, RecyclerBlockEntity.class);
            copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                    net.minecraft.util.ProblemReporter.DISCARDING, registries, saved));
            check(helper, copy.getItem(RecyclerBlockEntity.INPUT).is(Items.IRON_CHESTPLATE), "the piece comes back");
            check(helper, copy.guiProgress() == progress, "and so does how far it had got: " + copy.guiProgress() + " / " + progress);
            check(helper, copy.energy().getAmountAsLong() == machine.energy().getAmountAsLong(), "and its energy");
            helper.succeed();
        });
    }

    /**
     * Both output slots holding something else and the chest below full: it waits, says so and keeps the piece whole;
     * with room below again, the slots empty and it goes on.
     */
    static void recyclerWaitsWhenFull(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        helper.setBlock(body.below(), Blocks.CHEST);
        var chest = helper.getBlockEntity(body.below(), net.minecraft.world.level.block.entity.ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.STONE, 64));
        }
        var machine = powered(helper, body, RecyclerBlockEntity.class);
        machine.setItem(RecyclerBlockEntity.OUT_A, new ItemStack(Items.DIRT, 64));
        machine.setItem(RecyclerBlockEntity.OUT_B, new ItemStack(Items.SAND, 64));
        machine.setItem(RecyclerBlockEntity.INPUT, new ItemStack(Items.IRON_SWORD));
        helper.runAfterDelay(60, () -> {
            check(helper, machine.guiStatus() == dev.nookworks.machine.MachineStatus.OUTPUT_BLOCKED, "it should say its outputs are full, said " + machine.guiStatus());
            check(helper, machine.getItem(RecyclerBlockEntity.INPUT).is(Items.IRON_SWORD) && machine.guiProgress() == 0,
                    "and keep the sword whole, not work on it");
            chest.clearContent();
            helper.succeedWhen(() -> {
                check(helper, machine.getItem(RecyclerBlockEntity.INPUT).isEmpty(), "with room below again it should go on");
                check(helper, countIn(chest, ModRecycling.SCRAP.get()) > 0, "and its scrap reach the chest");
            });
        });
    }

    /** With no container below and a block under it, what it makes drops on top of the intake, not inside it. */
    static void recyclerDropsOnTop(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        powered(helper, body, RecyclerBlockEntity.class).setItem(RecyclerBlockEntity.INPUT, new ItemStack(Items.IRON_SWORD));
        double top = helper.absolutePos(body).getY() + 2;
        helper.succeedWhen(() -> {
            var scrap = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(body)).inflate(3), e -> e.getItem().is(ModRecycling.SCRAP.get()));
            check(helper, !scrap.isEmpty(), "the scrap should have dropped");
            check(helper, scrap.get(0).getY() >= top - 0.05, "on top of the intake, not inside it: y " + scrap.get(0).getY() + ", top " + top);
        });
    }

    /** Fed by a hopper on the intake, nothing below: what it makes drops beside the body, not into the hopper's block. */
    static void recyclerDropsBesideUnderAHopper(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        helper.setBlock(body.above(2), Blocks.HOPPER);
        powered(helper, body, RecyclerBlockEntity.class);
        helper.getBlockEntity(body.above(2), HopperBlockEntity.class).setItem(0, new ItemStack(Items.IRON_SWORD));
        helper.succeedWhen(() -> {
            var scrap = helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(body)).inflate(3), e -> e.getItem().is(ModRecycling.SCRAP.get()));
            check(helper, !scrap.isEmpty(), "the scrap should have dropped");
            BlockPos where = scrap.get(0).blockPosition();
            check(helper, where.getY() == helper.absolutePos(body).getY() && !where.equals(helper.absolutePos(body)),
                    "beside the body, at its level: at " + where + ", the body at " + helper.absolutePos(body));
        });
    }

    /**
     * A wrench, sneaking, moves a sheet between the face it is set on and the middle of its block, where a glass pane
     * stands (the user, 2026-10-18), and the sheet's outline goes with it; it keeps its facing.
     */
    static void wrenchMovesASheet(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModRecycling.PAINTED_SHEETS.get(net.minecraft.world.item.DyeColor.RED).get().defaultBlockState()
                .setValue(CorrugatedSheetBlock.FACING, Direction.EAST));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(dev.nookworks.registry.ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        BlockPos at = helper.absolutePos(pos);
        UseOnContext click = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.EAST, at, false));
        wrench.getItem().onItemUseFirst(wrench, click);
        BlockState middle = helper.getBlockState(pos);
        check(helper, middle.getValue(CorrugatedSheetBlock.CENTERED) && middle.getValue(CorrugatedSheetBlock.FACING) == Direction.EAST,
                "the sheet should move to the middle, facing as it did: " + middle);
        var box = middle.getShape(helper.getLevel(), at).bounds();
        check(helper, Math.abs(box.minX - 7 / 16.0) < 1e-6 && Math.abs(box.maxX - 9 / 16.0) < 1e-6, "its outline in the middle: " + box);
        wrench.getItem().onItemUseFirst(wrench, click);
        check(helper, !helper.getBlockState(pos).getValue(CorrugatedSheetBlock.CENTERED), "and back against its face");
        check(helper, wrench.getDamageValue() == 2, "each switch wears the wrench a little: " + wrench.getDamageValue());
        // waxed and rusted, it switches the same and keeps what it is
        BlockState waxed = ModRecycling.CORRUGATED_SHEETS.get("waxed_weathered_corrugated_sheet").get().defaultBlockState()
                .setValue(CorrugatedSheetBlock.FACING, Direction.EAST);
        helper.setBlock(pos, waxed);
        wrench.getItem().onItemUseFirst(wrench, click);
        check(helper, helper.getBlockState(pos).is(waxed.getBlock()) && helper.getBlockState(pos).getValue(CorrugatedSheetBlock.CENTERED),
                "a waxed, weathered sheet moves and stays as it was: " + helper.getBlockState(pos));
        // not sneaking, the wrench turns it as it turns anything
        player.setShiftKeyDown(false);
        wrench.getItem().onItemUseFirst(wrench, click);
        check(helper, helper.getBlockState(pos).getValue(CorrugatedSheetBlock.FACING) != Direction.EAST
                && helper.getBlockState(pos).getValue(CorrugatedSheetBlock.CENTERED), "a plain click turns it, still in the middle");
        helper.succeed();
    }

    /**
     * The intake is the body's to every mod's pipe and cable (the capabilities on the upper block are the body's) and to
     * a comparator beside it, which reads what the body holds.
     */
    static void recyclerIntakeIsTheBody(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        BlockPos intake = helper.absolutePos(body.above());
        var level = helper.getLevel();
        var machine = helper.getBlockEntity(body, RecyclerBlockEntity.class);
        var items = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK, intake, Direction.UP);
        check(helper, items != null, "a pipe on the intake should find an inventory");
        var energy = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.Energy.BLOCK, intake, Direction.NORTH);
        check(helper, energy == machine.energy(), "and a cable on it the body's own energy store");
        ItemStack rest = net.neoforged.neoforge.transfer.item.ItemUtil.insertItemReturnRemaining(items, new ItemStack(Items.IRON_SWORD), false, null);
        check(helper, rest.isEmpty() && machine.getItem(RecyclerBlockEntity.INPUT).is(Items.IRON_SWORD),
                "what goes in at the intake lands in the body's input");
        BlockState upper = level.getBlockState(intake);
        int fromIntake = upper.getAnalogOutputSignal(level, intake, Direction.NORTH);
        int fromBody = level.getBlockState(helper.absolutePos(body)).getAnalogOutputSignal(level, helper.absolutePos(body), Direction.NORTH);
        check(helper, fromIntake > 0 && fromIntake == fromBody, "a comparator by the intake reads the body: " + fromIntake + " / " + fromBody);
        helper.succeed();
    }

    /**
     * An intake set alone by command on another machine stands for no body: a click on it opens nothing, a hopper finds
     * no inventory in it and a comparator reads nothing (it opened the Crusher under it).
     */
    static void loneIntake(GameTestHelper helper) {
        BlockPos crusher = new BlockPos(2, 1, 2);
        helper.setBlock(crusher, dev.nookworks.registry.ModBlocks.CRUSHER.get());
        helper.setBlock(crusher.above(), ModRecycling.RECYCLER.get().defaultBlockState().setValue(RecyclerBlock.HALF, DoubleBlockHalf.UPPER));
        BlockPos at = helper.absolutePos(crusher.above());
        BlockState intake = helper.getLevel().getBlockState(at);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var result = intake.useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        check(helper, result == InteractionResult.PASS && player.containerMenu == player.inventoryMenu,
                "a lone intake should open nothing, got " + result + " and " + player.containerMenu);
        check(helper, ((RecyclerBlock) intake.getBlock()).getContainer(intake, helper.getLevel(), at) == null, "nor be a container");
        check(helper, intake.getAnalogOutputSignal(helper.getLevel(), at, Direction.NORTH) == 0, "nor give a comparator anything");
        helper.succeed();
    }

    /** A thing holding other things (a copper chest picked up full) is refused: they would be shredded with it. */
    static void recyclerRefusesWhatHoldsThings(GameTestHelper helper) {
        ItemStack chest = new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("copper_chest")));
        check(helper, RecyclingRecipe.of(chest) != null, "an empty copper chest is junk like any");
        chest.set(net.minecraft.core.component.DataComponents.CONTAINER,
                net.minecraft.world.item.component.ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND))));
        check(helper, RecyclingRecipe.of(chest) == null, "but not one with a diamond in it");
        ItemStack crossbow = new ItemStack(Items.CROSSBOW);
        check(helper, RecyclingRecipe.of(crossbow) != null, "an unloaded crossbow goes in");
        crossbow.set(net.minecraft.core.component.DataComponents.CHARGED_PROJECTILES,
                net.minecraft.world.item.component.ChargedProjectiles.ofNonEmpty(List.of(new ItemStack(Items.ARROW))));
        check(helper, RecyclingRecipe.of(crossbow) == null, "a loaded one keeps its arrow");
        helper.succeed();
    }

    /** A hopper over the intake feeds the body; what it makes comes out below. */
    static void recyclerFedThroughIntake(GameTestHelper helper) {
        BlockPos body = recycler(helper, new BlockPos(2, 1, 2));
        helper.setBlock(body.below(), Blocks.CHEST);
        helper.setBlock(body.above(2), Blocks.HOPPER);
        powered(helper, body, RecyclerBlockEntity.class);
        helper.getBlockEntity(body.above(2), HopperBlockEntity.class).setItem(0, new ItemStack(Items.BUCKET));
        helper.succeedWhen(() -> {
            int scrap = countIn(helper.getBlockEntity(body.below(), ChestBlockEntity.class), ModRecycling.SCRAP.get());
            check(helper, scrap >= 1 && scrap <= 2, "a bucket fed through the intake should come out as one or two scrap, got " + scrap);
        });
    }

    /** The bare bale and sheet rust in four ages as copper does; honeycomb waxes them, an axe scrapes a stage back. */
    static void scrapRusts(GameTestHelper helper) {
        for (Map<String, ?> family : List.of(ModRecycling.SCRAP_BALES, ModRecycling.CORRUGATED_SHEETS)) {
            List<Block> ages = family.values().stream().limit(4).map(b -> ((net.neoforged.neoforge.registries.DeferredBlock<?>) b).get())
                    .map(Block.class::cast).toList();
            for (int i = 0; i < 3; i++) {
                Block next = WeatheringCopper.getNext(ages.get(i)).orElse(null);
                check(helper, next == ages.get(i + 1), ages.get(i) + " should rust into " + ages.get(i + 1) + ", not " + next);
                check(helper, ages.get(i).defaultBlockState().isRandomlyTicking(), ages.get(i) + " should tick to rust");
            }
            check(helper, WeatheringCopper.getNext(ages.get(3)).isEmpty(), "the last age rusts no further");
        }
        for (var painted : ModRecycling.PAINTED_SHEETS.values()) {
            check(helper, WeatheringCopper.getNext(painted.get()).isEmpty() && !painted.get().defaultBlockState().isRandomlyTicking(),
                    painted.getId() + ": painted sheets do not rust");
        }
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModRecycling.SCRAP_BALES.get("exposed_scrap_bale").get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, new ItemStack(Items.IRON_AXE), pos);
        check(helper, helper.getBlockState(pos).is(ModRecycling.SCRAP_BALES.get("scrap_bale").get()),
                "an axe should scrape it back a stage, found " + helper.getBlockState(pos));
        use(helper, player, new ItemStack(Items.HONEYCOMB), pos);
        check(helper, helper.getBlockState(pos).is(ModRecycling.SCRAP_BALES.get("waxed_scrap_bale").get()),
                "honeycomb should wax it, found " + helper.getBlockState(pos));
        check(helper, !helper.getBlockState(pos).isRandomlyTicking(), "and waxed it rusts no more");
        // a sheet keeps how it lies through the axe: scraped back and its wax taken off, it still lies flat
        BlockPos sheet = new BlockPos(4, 1, 2);
        helper.setBlock(sheet, ModRecycling.CORRUGATED_SHEETS.get("weathered_corrugated_sheet").get().defaultBlockState()
                .setValue(CorrugatedSheetBlock.FACING, Direction.UP).setValue(CorrugatedSheetBlock.AXIS, Direction.Axis.X)
                .setValue(CorrugatedSheetBlock.CENTERED, true));
        use(helper, player, new ItemStack(Items.IRON_AXE), sheet);
        BlockState scraped = helper.getBlockState(sheet);
        check(helper, scraped.is(ModRecycling.CORRUGATED_SHEETS.get("exposed_corrugated_sheet").get())
                && scraped.getValue(CorrugatedSheetBlock.FACING) == Direction.UP && scraped.getValue(CorrugatedSheetBlock.AXIS) == Direction.Axis.X
                && scraped.getValue(CorrugatedSheetBlock.CENTERED), "an axe scrapes a sheet back a stage, lying as it lay: " + scraped);
        helper.setBlock(sheet, ModRecycling.CORRUGATED_SHEETS.get("waxed_corrugated_sheet").get().defaultBlockState()
                .setValue(CorrugatedSheetBlock.FACING, Direction.WEST));
        use(helper, player, new ItemStack(Items.IRON_AXE), sheet);
        BlockState unwaxed = helper.getBlockState(sheet);
        check(helper, unwaxed.is(ModRecycling.CORRUGATED_SHEETS.get("corrugated_sheet").get())
                && unwaxed.getValue(CorrugatedSheetBlock.FACING) == Direction.WEST, "and takes its wax off, facing as it faced: " + unwaxed);
        helper.succeed();
    }

    private static void use(GameTestHelper helper, Player player, ItemStack stack, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
    }

    /** Set on the floor, a sheet lies flat on it: two pixels high, facing up. */
    static void sheetLiesFlat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        placeByHand(helper, new ItemStack(ModRecycling.BUILDING_ITEMS.get("red_corrugated_sheet").get()), pos);
        BlockState sheet = helper.getBlockState(pos);
        check(helper, sheet.getBlock() instanceof CorrugatedSheetBlock, "the sheet should be placed, found " + sheet);
        check(helper, sheet.getValue(CorrugatedSheetBlock.FACING) == Direction.UP, "facing up, found " + sheet);
        double top = sheet.getShape(helper.getLevel(), helper.absolutePos(pos)).max(Direction.Axis.Y);
        check(helper, top <= 2.0 / 16 + 1e-6, "and lying low on the floor, its top at " + top);
        var LAND = net.minecraft.world.level.pathfinder.PathComputationType.LAND;
        check(helper, sheet.isPathfindable(LAND), "mobs walk over a sheet on the floor");
        BlockState wall = sheet.setValue(CorrugatedSheetBlock.FACING, Direction.NORTH);
        check(helper, !wall.isPathfindable(LAND) && !wall.setValue(CorrugatedSheetBlock.CENTERED, true).isPathfindable(LAND),
                "but not through one standing up, against a wall or in the middle");
        // nor, found by the same survey (2026-10-18), through a Copper Tank or a Laser
        check(helper, !dev.nookworks.registry.ModBlocks.COPPER_TANK.get().defaultBlockState().isPathfindable(LAND)
                && !dev.nookworks.registry.ModBlocks.LASER.get().defaultBlockState().isPathfindable(LAND),
                "a Copper Tank and a Laser are no way through either");
        helper.succeed();
    }

    /** A bale stands in for the chassis's iron block; scrap smelts to an iron nugget. */
    static void scrapRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getServer().getRecipeManager();
        check(helper, recipes.byKey(ResourceKey.create(Registries.RECIPE, Nookworks.id("machine_chassis_from_scrap_bale"))).isPresent(),
                "the Machine Chassis should have a recipe with a Scrap Bale");
        ItemStack smelted = recipes.getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(new ItemStack(ModRecycling.SCRAP.get())), helper.getLevel())
                .map(holder -> holder.value().assemble(new SingleRecipeInput(new ItemStack(ModRecycling.SCRAP.get())))).orElse(ItemStack.EMPTY);
        check(helper, smelted.is(Items.IRON_NUGGET) && smelted.getCount() == 1, "one scrap should smelt to one iron nugget, gave " + smelted);
        helper.succeed();
    }

    /**
     * The tags to sort by match the machines: #nookworks:crushable is what the Crusher takes, #nookworks:recyclable
     * what the Recycler takes - and a Precision Sorter offers them, however big.
     */
    static void sortingTagsMatch(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (stack.is(CRUSHABLE) != (CrushingRecipe.of(stack) != null)) {
                wrong.add("crushable " + id);
            }
            if (stack.is(RECYCLABLE) != (RecyclingRecipe.of(stack) != null)) {
                wrong.add("recyclable " + id);
            }
        }
        check(helper, wrong.isEmpty(), "the sorting tags should match the machines; wrong for " + wrong);
        for (TagKey<Item> tag : List.of(CRUSHABLE, RECYCLABLE, COMPRESSIBLE, SIFTABLE)) {
            check(helper, SorterTags.sortable(tag), tag.location() + " should be offered to sort by (it holds " + SorterTags.size(tag) + ")");
            check(helper, SorterTags.choices(new ItemStack(BuiltInRegistries.ITEM.get(tag).orElseThrow().get(0).value())).contains(tag),
                    "and among its first item's tags");
        }
        helper.succeed();
    }

    private RecyclingTests() {}
}
