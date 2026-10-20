package dev.nookworks.gametest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.feature.Features;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Headless in-game tests. Run all of them with {@code gradlew runGameTestServer}; the task fails if any test fails.
 * The tests live in classes by theme (blocks, mining, machines, mercury, storage, the kitchen, the reviews...), each
 * registering its own in {@code register()}, called below: a new feature adds its test to the class it belongs with,
 * or a new class registered here. {@link #check} is everyone's.
 * Arena: data/nookworks/structure/arena.nbt, 7 wide x 10 high x 7 deep, stone floor at y=0, open above.
 */
public final class NookworksGameTests {
    /** Per-test time limits in ticks (default 100); declared first because the registrations below use it. */
    private static final Map<String, Integer> TIMEOUTS = new HashMap<>();

    /**
     * What a test tries that a pack can switch off (a part of the mod, a server or start-up setting): run with it off -
     * a sweep of the settings, one at a time - the test passes untried instead of failing over what was switched off on
     * purpose, so whatever else fails is a real finding. Filled by {@link #switchable}, declared before the registrations.
     */
    private static final Map<String, BooleanSupplier> NEEDS = new HashMap<>();

    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, Nookworks.MODID);

    static final BlockPos CEILING = new BlockPos(3, 5, 3);

    /** The tests that set experience orbs loose, which run in a batch of their own (see onRegisterTests). */
    private static final Set<String> ORB_TESTS = Set.of("absorption_pad_collects_orbs", "absorption_pad_takes_every_merged_orb",
            "absorption_pad_takes_an_orb_lying_on_it");

    static {
        switchable();
        DataTests.register();
        CookingTests.register();
        KitchenTests.register();
        WorkshopTests.register();
        RopeTests.register();
        HungVineTests.register();
        BulbSwitchTests.register();
        RopeSignalTests.register();
        FixTests.register();
        R18FixTests.register();
        LineTests.register();
        FeatureTests.register();
        PlayTests.register();
        ClickTests.register();
        ReviewThreeTests.register();
        ReviewFourTests.register();
        CompatTests.register();
        BlockTests.register();
        MiningTests.register();
        TransportTests.register();
        SifterTests.register();
        MachineTests.register();
        RecyclingTests.register();
        StorageTests.register();
        MobTests.register();
        MercuryTests.register();
        InventoryTweakTests.register();
        EarlyReviewTests.register();
        TravelTests.register();
        IfTests.register();
        BarterTests.register();
        TradeTests.register();
        SoundTests.register();
        SaveCompat.register();
        PermissionTests.register();
        R7WrenchFluidsTests.register();
        R7WandItemsTests.register();
        R7StorageRopeTests.register();
        R7TravelIfTests.register();
        R7MobsTests.register();
        R7KitchenDataCoreTests.register();
        R7ServerPerfTests.register();
        R8VinesTests.register();
        R8CounterRerollTests.register();
        R8WorldFeaturesTests.register();
        R8DieselGeneratorTests.register();
        R8PebblesTests.register();
        R8RopeBannersTests.register();
        R8AutoBugsTests.register();
        R9SorterTests.register();
        R8NetherTreesTests.register();
        R8CreativeGeneratorTests.register();
        R9PipeTests.register();
        R9RopeTests.register();
        R10RequestTests.register();
        R10ItemLossTests.register();
        R11DriveTests.register();
        ChainTests.register();
        SquintTests.register();
        RecipeChoiceTests.register();
        CameraTests.register();
        BiodieselTests.register();
        EndSeedTests.register();
        QuenchingTests.register();
        DecorTests.register();
        R15Tests.register();
        R15ClaimTests.register();
        R15CheckFourTests.register();
        R15LateAuditTests.register();
        R15PlaythroughTests.register();
        WarpOwnerTests.register();
        R16FixTests.register();
        R16FixcheckTests.register();
        StructureCopyTests.register();
        TurnedShapeTests.register();
        EggsAndCampfiresTests.register();
    }

    public static void register(IEventBus modEventBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        DataTests.listen();
        R15LateAuditTests.listen(modEventBus);
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(NookworksGameTests::onRegisterTests);
    }

    static void test(String name, Consumer<GameTestHelper> body) {
        FUNCTIONS.register(name, () -> helper -> {
            BooleanSupplier on = NEEDS.get(name);
            if (on != null && !on.getAsBoolean()) {
                helper.succeed(); // what it tries is switched off
                return;
            }
            body.accept(helper);
        });
    }

    /** The tests that try a part of the mod or a setting a pack can switch off (r7's sweep of every switch). */
    private static void switchable() {
        needs(() -> Features.enabled("kitchen"), "bread_dough_rises_on_a_wooden_tray", "bread_is_made_of_flour",
                "corn_is_planted_from_its_kernels", "rice_bale_lies_on_its_side", "cream_stew_gives_the_milk_bucket_back",
                "an_iron_pot_cooks_between_two_hoppers", "iron_pot_fries_in_oil", "iron_pot_boils_rice_over_a_campfire",
                "pot_counts_its_bowls", "flour_coats_what_is_fried", "jei_sifting_page_shows_what_the_sifter_gives",
                "advancements_are_earned_as_a_player_would");
        needs(() -> Features.enabled("storage"), "storage_upgrades_turn_a_barrel_up_a_tier_where_it_stands",
                "velarium_barrel_holds_a_warehouse_of_warehouses");
        needs(() -> Features.enabled("sorting"), "sorter_gears_fit_and_old_sorters_keep_their_speed");
        needs(() -> Features.enabled("mob_bottle"), "advancements_are_earned_as_a_player_would");
        needs(() -> Features.enabled("liquid_experience"), "experience_bottles_fill_and_enchant",
                "retort_takes_hoppered_bottles_in_its_fill_box", "machine_screen_keeps_outputs_output_only");
        needs(() -> Features.enabled("liquid_experience"), R9PipeTests.NEED_LIQUID_EXPERIENCE);
        needs(() -> Features.enabled("liquid_experience"), BiodieselTests.NEED_LIQUID_EXPERIENCE);
        needs(() -> Features.enabled("kitchen"), R11DriveTests.NEED_KITCHEN);
        needs(() -> Features.enabled("kitchen"), "an_egg_fries_in_a_furnace_smoker_or_campfire",
                "the_iron_pot_boils_eggs_and_rolls_tamagoyaki", "egg_dishes_fill_as_the_table_says");
        needs(() -> Features.enabled("dark_matter"), "cooked_food_chars_into_dark_matter");
        needs(() -> Features.enabled("recycling"), RecyclingTests.NEED_RECYCLING);
        // the Crusher grinds wheat and bread into the kitchen's flour and breadcrumbs only with the kitchen on
        needs(() -> Features.enabled("kitchen"), "sorting_tags_match_the_machines",
                "compressor_takes_any_ninefold_recipe"); // brown rice packs into a Rice Bale, the kitchen's
        needs(() -> Features.enabled("lode_pearl"), "lode_pearls_bind_to_a_lodestone_and_stack");
        needs(() -> Features.enabled("lode_pearl"), "lode_pearl_lands_after_its_thrower_has_gone_far_off",
                "lode_pearl_comes_back_to_the_feet_of_a_full_inventory");
        needs(() -> Features.enabled("magnets"), "magnet_does_not_draw_drops_through_lava");
        needs(() -> Features.enabled("sorting"), "sorter_gives_nothing_back_into_a_trash_can",
                "sorter_leaves_a_shulker_box_a_shulker_box_refuses", "sorter_leaves_what_the_next_sorter_keeps_out");
        needs(NookworksStartupConfig::flourBakery, "bread_is_made_of_flour");
        needs(NookworksStartupConfig.EDIBLE_RAW_FOODS, "raw_kelp_sugar_and_mushrooms_can_be_eaten");
        needs(NookworksConfig.TIMBER_ENABLED, "timber_fells_tree", "timber_spares_a_cabin_against_the_tree",
                "timber_spares_a_village_house", "timber_fells_a_tree_by_the_road", "a_tree_grown_by_bone_meal_fells_from_the_bottom",
                "an_old_marked_stump_still_fells", "timber_leaves_decay_fast_but_spare_other_trees",
                "conflicting_mods_stand_our_features_down", "timber_fells_a_huge_fungus", "timber_spares_a_built_stem_wall");
        needs(NookworksConfig.FAST_LEAF_DECAY, "timber_leaves_decay_fast_but_spare_other_trees");
        needs(NookworksConfig.VEIN_ENABLED, "vein_mines_connected_ore", "vein_runs_on_into_deepslate", "vein_stops_before_tool_breaks");
        needs(NookworksConfig.PROTECT_TOOLS, "vein_stops_before_tool_breaks");
        needs(NookworksConfig.HAMMER_ENABLED, "hammer_mines_3x3_facing_player", "hammer_spares_unsuitable_blocks",
                "hammer_mines_floor_when_looking_down", "earthmover_sweeps_dirt_and_wood");
        needs(NookworksConfig.WAND_ENABLED, "wand_places_what_the_item_holds", "building_wand_stays_bounded",
                "building_wand_keeps_out_of_a_strict_claim");
        needs(NookworksConfig.WAND_ENABLED, "building_wand_sets_no_double_slab_in_water", "building_wand_places_against_the_row_it_builds_on");
        needs(NookworksConfig.RAW_FOOD_SICKNESS, "raw_chicken_spares_the_creative_and_milk_cures", "raw_chicken_makes_you_ill_later",
                "raw_meat_and_fish_risk_by_kind");
    }

    /** The tests {@code names} try what {@code on} says is on (with whatever else they were given). */
    private static void needs(BooleanSupplier on, String... names) {
        for (String name : names) {
            BooleanSupplier before = NEEDS.get(name);
            NEEDS.put(name, before == null ? on : () -> before.getAsBoolean() && on.getAsBoolean());
        }
    }

    /** Every test that tries something a pack can switch off. Public for tests (each name must be a test). */
    static Set<String> switchableTests() {
        return NEEDS.keySet();
    }

    /** A test that needs longer than the default 100 ticks (e.g. item pipelines at hopper speed). */
    static void test(String name, int maxTicks, Consumer<GameTestHelper> body) {
        TIMEOUTS.put(name, maxTicks);
        test(name, body);
    }

    private static void onRegisterTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(Nookworks.id("default"));
        // The spawner tests run in a batch of their own (before or after the rest: the order of batches changes from
        // run to run): a spawner will not spawn while enough of its mob stand within reach of it, and in the shared batch
        // another test's pigs in the next arena along could be counted, so now and then one of them never saw a pig.
        Holder<TestEnvironmentDefinition<?>> spawners = event.registerEnvironment(Nookworks.id("spawners"));
        // Dark Matter's disc reaches seven blocks, well past its own arena: it runs apart, where it can darken nobody else's.
        Holder<TestEnvironmentDefinition<?>> dark = event.registerEnvironment(Nookworks.id("dark"));
        // An experience orb flies to any player within eight blocks, and many tests stand a player in their arena: the
        // pad's orb tests run apart, with nobody near, or now and then the orb went to the next arena's player instead.
        Holder<TestEnvironmentDefinition<?>> orbs = event.registerEnvironment(Nookworks.id("orbs"));
        // -PonlyTests / -PrepeatTests (build.gradle): only some tests, each run again and again, each in a batch of its own
        String only = System.getProperty("nookworks.onlyTests", "");
        List<String> onlyPrefixes = only.isBlank() ? List.of() : List.of(only.split(","));
        int repeats = Math.max(1, Integer.getInteger("nookworks.repeatTests", 1));
        FUNCTIONS.getEntries().forEach(fn -> {
            ResourceKey<Consumer<GameTestHelper>> key = fn.key();
            String name = key.identifier().getPath();
            if (!onlyPrefixes.isEmpty() && onlyPrefixes.stream().noneMatch(name::startsWith)) {
                return;
            }
            if (repeats > 1) {
                for (int i = 0; i < repeats; i++) {
                    String copy = name + "_again_" + i;
                    event.registerTest(Nookworks.id(copy), new FunctionGameTestInstance(key,
                            new TestData<>(event.registerEnvironment(Nookworks.id(copy)), Nookworks.id("arena"), TIMEOUTS.getOrDefault(name, 100), 0, true)));
                }
                return;
            }
            // Where the Dock-a-Daemon Doors stand, and the If, are the whole server's, as are the recipes the recipe choice's
            // test adds for a while: each of their tests runs alone, in a batch of its own.
            Holder<TestEnvironmentDefinition<?>> own = name.startsWith("daemon_door_") || name.startsWith("if_")
                    || name.startsWith("recipe_choice_")
                    ? event.registerEnvironment(Nookworks.id(name)) : null;
            Holder<TestEnvironmentDefinition<?>> batch = own != null ? own
                    : name.startsWith("spawner_") ? spawners
                    : name.startsWith("dark_matter_") ? dark
                    : ORB_TESTS.contains(name) ? orbs : env;
            event.registerTest(key.identifier(), new FunctionGameTestInstance(key,
                    new TestData<>(batch, Nookworks.id("arena"), TIMEOUTS.getOrDefault(name, 100), 0, true)));
        });
    }

    static void check(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            throw helper.assertionException(Component.literal(message));
        }
    }

    /**
     * Holds up the server until the light engine has worked out every change made so far in the chunk of {@code pos}, and
     * the light those changes spread. Light is worked out on a thread of its own, started only when the server has time
     * to spare between ticks - which the test server, running its ticks back to back, seldom has: a test waiting for
     * light in ticks alone now and then ran out of them first (Dark Matter's, 2026-10-10: 600 ticks in a fifth of a
     * second, on a busy machine). Starts the work itself; gives up after ten seconds, leaving the test's check to fail.
     */
    static void settleLight(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        ThreadedLevelLightEngine light = helper.getLevel().getChunkSource().getLightEngine();
        CompletableFuture<?> done = light.waitForPendingTasks(SectionPos.blockToSectionCoord(at.getX()), SectionPos.blockToSectionCoord(at.getZ()));
        long until = System.nanoTime() + 10_000_000_000L;
        while (!done.isDone() && System.nanoTime() < until) {
            light.tryScheduleUpdate();
            LockSupport.parkNanos(200_000L);
        }
    }

    /**
     * Holds up the server until every chunk within {@code radius} chunks of chunk ({@code x}, {@code z}) is generated and
     * loaded in full. Ground never visited is generated on worker threads, in time, not in ticks - and the test server
     * runs its ticks back to back: a test waiting for far ground in ticks alone ran out of them first (the far Lode
     * Pearl's, 2026-10-17: 1200 ticks in under two seconds, while the run's first far chunks were still being made).
     * Only loads them: they stay loaded as long as some ticket of their own keeps them, no longer.
     */
    static void loadChunks(ServerLevel level, int x, int z, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                level.getChunk(x + dx, z + dz);
            }
        }
    }

    /**
     * Waits until {@code condition} holds, for something done off the server thread - entities read from disk, say - so
     * in time as well as ticks: each tick it does not hold yet, the server is held up a further {@code 20 ms}, so the
     * test's timeout gives it seconds of time, not the fraction of a second the test server's back-to-back ticks take
     * (a vine test's 120 ticks ran out before the entities round it had loaded, 2026-10-17).
     */
    static boolean inTime(BooleanSupplier condition) {
        if (condition.getAsBoolean()) {
            return true;
        }
        LockSupport.parkNanos(20_000_000L);
        return false;
    }

    private NookworksGameTests() {}
}
