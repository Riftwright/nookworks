package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.crop.EndSeedPlant;
import dev.nookworks.decor.Lanterns;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.Earthmover;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.vehicle.boat.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The compatibility and modpack sweep of 2026-10-23 (the user: 「他のmodとの連携」「modパックへの入れやすさの配慮」): a
 * machine asked from no side gives out only what it gives out from below; Dawnwood's chest boat reaches other mods'
 * automation; nothing of a part switched off comes out of an End Seed or a librarian; our things in the common tags.
 */
final class CompatSweepTests {
    private CompatSweepTests() {}

    static void register() {
        test("compat_a_machine_asked_from_no_side_gives_only_its_output", CompatSweepTests::sideless);
        test("compat_dawnwood_chest_boat_reaches_other_mods_automation", CompatSweepTests::chestBoat);
        test("compat_earthmover_is_offered_only_with_the_hammers", CompatSweepTests::earthmover);
        test("compat_end_seed_grows_no_corn_with_the_kitchen_off", CompatSweepTests::endSeed);
        test("compat_our_things_are_in_the_common_tags", CompatSweepTests::tags);
        test("mixin_hooks_are_counted_in_test_runs", CompatSweepTests::hooks);
        test("compat_hammers_do_not_stand_down_for_ftb_ultimine", CompatSweepTests::ultimine);
        test("parts_of_1_2_go_with_what_needs_them", CompatSweepTests::newParts);
    }

    /** A Planter's seeds and a Crusher's ore stay in from no side; the Crusher's output still comes out. */
    private static void sideless(GameTestHelper helper) {
        BlockPos planterAt = new BlockPos(1, 1, 1);
        BlockPos crusherAt = new BlockPos(3, 1, 1);
        helper.setBlock(planterAt, ModBlocks.PLANTER.get());
        helper.setBlock(crusherAt, ModBlocks.CRUSHER.get());
        PlanterBlockEntity planter = (PlanterBlockEntity) helper.getBlockEntity(planterAt, PlanterBlockEntity.class);
        CrusherBlockEntity crusher = (CrusherBlockEntity) helper.getBlockEntity(crusherAt, CrusherBlockEntity.class);
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 8));
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 8));
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL, 8));
        var planterItems = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(planterAt), null);
        var crusherItems = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(crusherAt), null);
        check(helper, planterItems != null && crusherItems != null, "both have item handlers from no side");
        check(helper, Move.extract(planterItems, ItemResource.of(Items.WHEAT_SEEDS), 8) == 0, "no seeds out of the Planter");
        check(helper, Move.extract(crusherItems, ItemResource.of(Items.COBBLESTONE), 8) == 0, "no ore out of the Crusher");
        check(helper, Move.extract(crusherItems, ItemResource.of(Items.GRAVEL), 8) == 8, "its output comes out, as below");
        check(helper, Move.insert(crusherItems, ItemResource.of(Items.COBBLESTONE), 1) == 1, "and it still takes ore in");
        helper.succeed();
    }

    /** Asked as NeoForge asks a vanilla chest boat, Dawnwood's gives its chest. */
    private static void chestBoat(GameTestHelper helper) {
        ChestBoat boat = ModEntities.DAWNWOOD_CHEST_BOAT.get().create(helper.getLevel(), EntitySpawnReason.COMMAND);
        check(helper, boat != null, "a Dawnwood chest boat");
        boat.setItem(0, new ItemStack(Items.APPLE, 3));
        var items = boat.getCapability(Capabilities.Item.ENTITY, null);
        var automation = boat.getCapability(Capabilities.Item.ENTITY_AUTOMATION, Direction.UP);
        check(helper, items != null && automation != null, "its chest is a capability, for players' and machines' handlers");
        check(helper, Move.extract(automation, ItemResource.of(Items.APPLE), 3) == 3, "an unloader takes what is in it");
        boat.discard();
        helper.succeed();
    }

    /** With the hammers switched off, the enchanting table offers no Earthmover book; on, it does (in one tick). */
    private static void earthmover(GameTestHelper helper) {
        Holder<Enchantment> earthmover = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Earthmover.KEY);
        ModConfigSpec.BooleanValue hammers = NookworksStartupConfig.feature("hammers");
        boolean was = hammers.get();
        Set<Holder<Enchantment>> on;
        Set<Holder<Enchantment>> off;
        try {
            hammers.set(true);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            on = EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(Items.BOOK), Stream.of(earthmover)).stream()
                    .map(e -> e.enchantment()).collect(Collectors.toSet());
            hammers.set(false);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            off = EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(Items.BOOK), Stream.of(earthmover)).stream()
                    .map(e -> e.enchantment()).collect(Collectors.toSet());
        } finally {
            hammers.set(was);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
        }
        check(helper, on.contains(earthmover), "with the hammers on, a book can get Earthmover");
        check(helper, off.isEmpty(), "with the hammers off, it is not offered");
        helper.succeed();
    }

    /** The End Seed's draw has the corn while the kitchen is on, and none of it with the kitchen off. */
    private static void endSeed(GameTestHelper helper) {
        BlockPos at = helper.absolutePos(new BlockPos(1, 1, 1));
        ModConfigSpec.BooleanValue kitchen = NookworksStartupConfig.feature("kitchen");
        boolean was = kitchen.get();
        boolean cornOn;
        boolean cornOff;
        try {
            kitchen.set(true);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            cornOn = EndSeedPlant.fitting(helper.getLevel(), at).stream().anyMatch(e -> e.state().is(ModKitchen.CORN_CROP.get()));
            kitchen.set(false);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            cornOff = EndSeedPlant.fitting(helper.getLevel(), at).stream().anyMatch(e -> e.state().is(ModKitchen.CORN_CROP.get()));
        } finally {
            kitchen.set(was);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
        }
        check(helper, cornOn, "with the kitchen on, an End Seed may grow corn");
        check(helper, !cornOff, "with it off, never");
        helper.succeed();
    }

    private static void tags(GameTestHelper helper) {
        check(helper, new ItemStack(ModWoods.DAWNWOOD_FENCE_GATE.get()).is(Tags.Items.FENCE_GATES_WOODEN)
                && Blocks.OAK_FENCE_GATE.defaultBlockState().is(Tags.Blocks.FENCE_GATES_WOODEN)
                && ModWoods.DAWNWOOD_FENCE_GATE.get().defaultBlockState().is(Tags.Blocks.FENCE_GATES_WOODEN), "Dawnwood's fence gate");
        check(helper, new ItemStack(ModWoods.STRIPPED_DAWNWOOD_LOG.get()).is(Tags.Items.STRIPPED_LOGS)
                && new ItemStack(ModWoods.STRIPPED_DAWNWOOD_WOOD.get()).is(Tags.Items.STRIPPED_WOODS), "its stripped log and wood");
        check(helper, ModEntities.DAWNWOOD_CHEST_BOAT.get().builtInRegistryHolder().is(Tags.EntityTypes.BOATS)
                && ModEntities.DAWNWOOD_BOAT.get().builtInRegistryHolder().is(Tags.EntityTypes.BOATS),
                "its boats");
        check(helper, new ItemStack(ModBlocks.ROPE.get()).is(Tags.Items.ROPES), "the Rope");
        try (var reader = helper.getLevel().getServer().getResourceManager()
                .getResourceOrThrow(net.minecraft.resources.Identifier.parse("nookworks:tags/item/drive_belt_ropes.json")).openAsReader()) {
            check(helper, com.google.gson.JsonParser.parseReader(reader).toString().contains("supplementaries:rope"),
                    "Supplementaries' Rope makes a belt, where that mod is");
        } catch (java.io.IOException unreadable) {
            check(helper, false, "the belt ropes' tag reads: " + unreadable);
        }
        check(helper, new ItemStack(BuiltInItems.of("nookworks:iron_hammer")).is(Tags.Items.MINING_TOOL_TOOLS), "a hammer is a mining tool");
        check(helper, new ItemStack(BuiltInItems.of("nookworks:gold_sorter")).is(Tags.Items.HIDDEN_FROM_RECIPE_VIEWERS),
                "0.2.0's sorters are hidden from every recipe viewer");
        ItemStack sapling = new ItemStack(ModWoods.DAWNWOOD_SAPLING.get().asItem()); // the Rare Seed, the sapling's item
        check(helper, sapling.has(DataComponents.COOKING_FUEL) && sapling.has(DataComponents.COMPOSTABLE), "the sapling burns and composts");
        check(helper, Lanterns.lighter(new ItemStack(Items.FLINT_AND_STEEL)) && Lanterns.lighter(new ItemStack(Items.FIRE_CHARGE))
                && !Lanterns.lighter(new ItemStack(Items.STICK)), "a lantern's lighters");
        helper.succeed();
    }

    /**
     * The GameTest run counts every hook's injections ({@code mixin.debug.countInjections}, build.gradle), so one that
     * finds no place in the game's code stops the run at load. Players' games leave such a hook out instead (since
     * 2026-10-23); without the count, a Minecraft update that broke one would go unseen.
     */
    private static void hooks(GameTestHelper helper) {
        check(helper, Boolean.getBoolean("mixin.debug.countInjections"), "the test run counts every hook's injections");
        helper.succeed();
    }

    /** The hammers' old default list (with FTB Ultimine) reads as today's; a list a pack wrote itself reads as written. */
    private static void ultimine(GameTestHelper helper) {
        List<? extends String> was = NookworksConfig.HAMMER_CONFLICTS.get();
        List<? extends String> old;
        List<? extends String> own;
        try {
            NookworksConfig.HAMMER_CONFLICTS.set(List.of("justhammers", "hammers", "vanilla_hammers", "ftbultimine"));
            old = FeatureGate.hammerConflicts();
            NookworksConfig.HAMMER_CONFLICTS.set(List.of("ftbultimine", "somehammermod"));
            own = FeatureGate.hammerConflicts();
        } finally {
            NookworksConfig.HAMMER_CONFLICTS.set(was);
        }
        check(helper, !old.contains("ftbultimine") && old.contains("justhammers"), "the old default no longer names FTB Ultimine: " + old);
        check(helper, own.contains("ftbultimine"), "a pack's own list is kept: " + own);
        check(helper, !NookworksConfig.HAMMER_CONFLICTS.getDefault().contains("ftbultimine"), "nor does today's default");
        helper.succeed();
    }

    /**
     * The parts added 2026-10-23 (the user: なるべくオンオフの選択肢は増やして): each has its switch, and what needs one goes
     * with it - the Velarium Hammer, Sorter Gear, Storage Barrel and Upgrade with velarium; the rest stays
     * (tools/check_parts.py walks it all).
     */
    private static void newParts(GameTestHelper helper) {
        for (String part : List.of("generators", "electric_furnace", "planter", "water_pump", "motor", "electrolytic_cell", "stone_maker", "velarium")) {
            check(helper, NookworksStartupConfig.feature(part) != null, "a switch for " + part);
        }
        // biodiesel is stirred in the Stirring Tank as well as cooked in the pot: the kitchen off leaves it, and the Diesel Generator
        check(helper, dev.nookworks.feature.Features.enabled(BuiltInItems.of("nookworks:diesel_generator"), part -> !part.equals("kitchen"))
                && dev.nookworks.feature.Features.enabled(BuiltInItems.of("nookworks:biodiesel_bucket"), part -> !part.equals("kitchen"))
                && !dev.nookworks.feature.Features.enabled(BuiltInItems.of("nookworks:diesel_generator"), part -> !part.equals("generators")),
                "the Diesel Generator stays with the kitchen off, and goes with the generators");
        for (String id : List.of("nookworks:velarium_hammer", "nookworks:velarium_sorter_gear", "nookworks:velarium_storage_barrel",
                "nookworks:velarium_storage_upgrade", "nookworks:velarium_pickaxe")) {
            check(helper, !dev.nookworks.feature.Features.enabled(BuiltInItems.of(id), part -> !part.equals("velarium")), id + " goes with velarium");
        }
        check(helper, dev.nookworks.feature.Features.enabled(BuiltInItems.of("nookworks:iron_hammer"), part -> !part.equals("velarium")),
                "an iron hammer stays");
        check(helper, NookworksConfig.CHEST_SORT_ALLOWED.getDefault(), "a server lets its players sort chests unless it says not");
        helper.succeed();
    }

    /** An item by its id, for those the registries name only through a holder kept elsewhere. */
    private static final class BuiltInItems {
        static net.minecraft.world.item.Item of(String id) {
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(id));
        }
    }
}
