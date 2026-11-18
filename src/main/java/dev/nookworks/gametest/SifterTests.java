package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;

/** The Sifter, its Brass Mesh, and the tables its JEI page reads. */
final class SifterTests {
    static void register() {
        test("sifter_hand_sifts_one_per_use", SifterTests::sifterHandSifts);
        test("sifter_ignores_non_siftable", SifterTests::sifterIgnoresPlanks);
        test("end_gravel_sifts_into_the_ends_things", SifterTests::endGravelSifts);
        test("sifter_hopper_input_is_processed", SifterTests::sifterHopperInput);
        test("sifter_results_go_into_chest_below", SifterTests::sifterOutputToChest);
        test("sifter_waits_when_chest_is_full", 200, SifterTests::sifterWaitsWhenFull);
        test("sifter_gravel_drop_rates_are_sane", SifterTests::sifterRatesSane);
        test("jei_sifting_tables_are_readable", SifterTests::siftingTablesReadable);
        test("jei_sifting_page_shows_what_the_sifter_gives", SifterTests::siftingPageShowsWhatTheSifterGives);
        test("sifter_brass_mesh_keeps_the_small_things", SifterTests::sifterBrassMeshKeepsTheSmallThings);
        test("dirt_sifts_into_pitcher_pods", SifterTests::dirtSiftsIntoPitcherPods);
        test("sifter_takes_the_ticks_the_setting_says", 200, SifterTests::sifterTakesTheTicksTheSettingSays);
        test("sifter_takes_podzol_and_soul_soil", SifterTests::podzolAndSoulSoil);
        test("sifter_gives_more_kinds_at_the_old_odds", SifterTests::moreKindsAtTheOldOdds);
    }

    /** The chance a sift of {@code input} gives {@code item}, as the Sifter's page has it. */
    static double chance(String input, boolean fine, net.minecraft.world.item.Item item) {
        return LootSummaries.sifted(ResourceLocation.parse(input), fine).stream().filter(d -> d.stack().is(item))
                .mapToDouble(LootSummaries.Drop::chance).sum();
    }

    static boolean near(double chance, double expected) {
        return Math.abs(chance - expected) < 1e-9;
    }

    // ---- Sifter ----

    static final BlockPos SIFTER = new BlockPos(3, 2, 3);

    static void sifterHandSifts(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.GRAVEL, 64));

        for (int i = 0; i < 40; i++) {
            helper.useBlock(SIFTER, player);
        }

        check(helper, player.getMainHandItem().getCount() == 24, "40 uses should sift 40 gravel, left " + player.getMainHandItem().getCount());
        List<ItemEntity> drops = helper.getLevel().getEntities(EntityType.ITEM,
                new AABB(helper.absolutePos(SIFTER)).inflate(2.0), e -> true);
        check(helper, !drops.isEmpty(), "40 gravel should yield something (chance of nothing at all is ~1e-18)");
        helper.succeed();
    }

    /** A stocked Sifter sifts a block every sifterTicks ticks, as its setting's text says - not one tick more (r7 review). */
    static void sifterTakesTheTicksTheSettingSays(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(Items.GRAVEL, 16));
        List<Long> sifts = new java.util.ArrayList<>();
        int[] left = {16};
        helper.onEachTick(() -> {
            int now = sifter.getItem(SifterBlockEntity.INPUT).getCount();
            if (now < left[0]) {
                sifts.add(helper.getTick());
                left[0] = now;
            }
        });
        helper.succeedWhen(() -> {
            check(helper, sifts.size() >= 4, "four sifts seen: " + sifts);
            for (int i = 1; i < sifts.size(); i++) {
                check(helper, sifts.get(i) - sifts.get(i - 1) == sifter.ticksPerBlock(),
                        "a block every " + sifter.ticksPerBlock() + " ticks, sifted at " + sifts);
            }
        });
    }

    static void sifterIgnoresPlanks(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.OAK_PLANKS, 10));

        helper.useBlock(SIFTER, player);

        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        check(helper, !sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(Items.OAK_PLANKS)), "hoppers must not insert planks");
        check(helper, sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(Items.GRAVEL)), "hoppers may insert gravel");
        check(helper, !sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE)), "but not cobblestone: rock is crushed first");
        check(helper, !sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(Items.NETHERRACK)), "nor netherrack");
        for (var soil : List.of(Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.MUD, ModItems.DEEPSLATE_GRAVEL.get(), ModItems.NETHER_GRIT.get(),
                ModItems.END_GRAVEL.get())) {
            check(helper, sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(soil)), soil + " can be sifted");
        }
        var grit = CrushingRecipe.of(new ItemStack(Items.NETHERRACK));
        var deepGravel = CrushingRecipe.of(new ItemStack(Items.COBBLED_DEEPSLATE));
        check(helper, grit != null && grit.output().is(ModItems.NETHER_GRIT.get())
                && deepGravel != null && deepGravel.output().is(ModItems.DEEPSLATE_GRAVEL.get()), "the Crusher makes the grits");
        helper.assertItemEntityNotPresent(Items.FLINT, SIFTER, 3.0);
        helper.succeed();
    }

    /**
     * End Stone crushed is End Gravel, and End Gravel sifted gives the End's things (the user, 2026-10-10): four rolls in
     * five nothing, chorus, ender pearls, obsidian, chorus flowers, now and then a shulker shell, and once in a million a
     * dragon egg - which JEI's page writes as one in a million, not as 0.0%. Sulfur dust too, from 2026-11-15
     * (「エンドの砂利から、硫黄の粉とか？」): one roll in 25, taken from the nothing, and twice that through the mesh.
     */
    static void endGravelSifts(GameTestHelper helper) {
        var crushed = CrushingRecipe.of(new ItemStack(Items.END_STONE));
        check(helper, crushed != null && crushed.output().is(ModItems.END_GRAVEL.get()), "the Crusher makes End Gravel of End Stone");
        var drops = LootSummaries.sifted(BuiltInRegistries.ITEM.getKey(ModItems.END_GRAVEL.get()), false);
        Function<net.minecraft.world.item.Item, Double> chance = item -> drops.stream().filter(d -> d.stack().is(item))
                .mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, Math.abs(chance.apply(Items.CHORUS_FRUIT) - 0.09) < 1e-9 && Math.abs(chance.apply(Items.ENDER_PEARL) - 0.06) < 1e-9
                && Math.abs(chance.apply(Items.OBSIDIAN) - 0.027) < 1e-9 && Math.abs(chance.apply(Items.CHORUS_FLOWER) - 0.02) < 1e-9
                && Math.abs(chance.apply(Items.SHULKER_SHELL) - 0.003) < 1e-9, "the End's things at their chances: " + drops);
        check(helper, Math.abs(chance.apply(Items.DRAGON_EGG) - 1e-6) < 1e-12, "a dragon egg once in a million: " + chance.apply(Items.DRAGON_EGG));
        check(helper, near(chance.apply(ModItems.SULFUR_DUST.get()), 0.04)
                && near(chance("nookworks:end_gravel", true, ModItems.SULFUR_DUST.get()), 0.08)
                && near(chance("nookworks:end_gravel", true, Items.CHORUS_FRUIT), 0.09), "sulfur dust one roll in 25, twice "
                + "that through the mesh, which keeps the rest as they were: " + chance.apply(ModItems.SULFUR_DUST.get()));
        double something = drops.stream().mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, Math.abs(something - 0.242001) < 1e-9, "three rolls in four give nothing (an End Seed's one in five "
                + "hundred and the egg's one in a million aside): " + (1 - something));
        check(helper, LootSummaries.chanceText(1e-6).equals("1/1,000,000") && LootSummaries.chanceText(0.09).equals("9.0%")
                && LootSummaries.chanceText(1.0 / 990).equals("0.1%"), "JEI writes it as one in so many: " + LootSummaries.chanceText(1e-6));
        helper.succeed();
    }

    static void sifterHopperInput(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        helper.setBlock(SIFTER.below(), Blocks.CHEST);
        BlockPos hopper = SIFTER.above();
        helper.setBlock(hopper, Blocks.HOPPER);
        helper.getBlockEntity(hopper, HopperBlockEntity.class).setItem(0, new ItemStack(Items.GRAVEL, 3));

        helper.succeedWhen(() -> {
            check(helper, helper.getBlockEntity(hopper, HopperBlockEntity.class).isEmpty(), "hopper should be emptied into the sifter");
            check(helper, helper.getBlockEntity(SIFTER, SifterBlockEntity.class).getItem(SifterBlockEntity.INPUT).isEmpty(),
                    "all 3 gravel should be sifted");
            helper.assertItemEntityNotPresent(Items.FLINT, SIFTER, 3.0); // results went into the chest, not the floor
        });
    }

    static void sifterOutputToChest(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        BlockPos chest = SIFTER.below();
        helper.setBlock(chest, Blocks.CHEST);
        helper.getBlockEntity(SIFTER, SifterBlockEntity.class).setItem(1, new ItemStack(Items.DIAMOND, 2));

        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(chest, ChestBlockEntity.class), Items.DIAMOND) == 2, "results should move into the chest"));
    }

    static void sifterWaitsWhenFull(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        BlockPos chest = SIFTER.below();
        helper.setBlock(chest, Blocks.CHEST);
        ChestBlockEntity box = helper.getBlockEntity(chest, ChestBlockEntity.class);
        for (int i = 0; i < box.getContainerSize(); i++) {
            box.setItem(i, new ItemStack(Items.STONE, 64));
        }
        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        sifter.setItem(1, new ItemStack(Items.DIAMOND, 1)); // a result that cannot leave
        sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(Items.GRAVEL, 5));

        // One result waiting does not stop it: the others wait beside it, in the nine slots.
        helper.runAfterDelay(60, () -> {
            check(helper, sifter.getItem(SifterBlockEntity.INPUT).getCount() < 5, "it goes on with room left for results");
            check(helper, sifter.getItem(1).is(Items.DIAMOND), "stuck result must be kept, not dropped");
            helper.assertItemEntityNotPresent(Items.DIAMOND, SIFTER, 3.0);
            // Every slot full, and crude salt in, which always washes to salt: no room for what it gives. The block
            // is sifted, its salt waits behind the buffer, and nothing more goes in until the salt has.
            for (int slot = 1; slot <= 9; slot++) {
                sifter.setItem(slot, new ItemStack(Items.STONE, 64));
            }
            sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(ModItems.CRUDE_SALT.get(), 8));
            helper.runAfterDelay(60, () -> {
                check(helper, sifter.getItem(SifterBlockEntity.INPUT).getCount() == 7, "one block sifted, then it must pause: "
                        + sifter.getItem(SifterBlockEntity.INPUT).getCount());
                check(helper, sifter.guiStatus() == MachineStatus.OUTPUT_BLOCKED, "and say why");
                sifter.setItem(9, ItemStack.EMPTY); // room again: the waiting salt goes in first
                helper.runAfterDelay(2, () -> {
                    check(helper, sifter.getItem(9).is(ModItems.SALT.get()), "the salt that waited is in the freed slot: " + sifter.getItem(9));
                    helper.succeed();
                });
            });
        });
    }

    /** Statistical sanity check of the gravel table: 2000 rolls, generous bounds (> 6 sigma). */
    static void sifterRatesSane(GameTestHelper helper) {
        int flint = 0;
        int diamonds = 0;
        int nothing = 0;
        for (int i = 0; i < 2000; i++) {
            List<ItemStack> out = SifterBlockEntity.sift(helper.getLevel(), helper.absolutePos(SIFTER), new ItemStack(Items.GRAVEL));
            if (out.isEmpty()) {
                nothing++;
            }
            for (ItemStack s : out) {
                flint += s.is(Items.FLINT) ? 1 : 0;
                diamonds += s.is(Items.DIAMOND) ? s.getCount() : 0;
            }
        }
        check(helper, flint > 430 && flint < 680, "flint should be ~28% (556/2000), got " + flint);
        check(helper, nothing > 450 && nothing < 700, "nothing should be ~29% (574/2000), got " + nothing);
        check(helper, diamonds <= 12, "diamonds should be ~0.1% (2/2000), got " + diamonds);
        helper.succeed();
    }

    // ---- JEI support (plain code, no JEI classes) ----
    /**
     * The tables the recipe pages show, as the server reads them from its data packs and sends them to players: every
     * siftable item's, ordinary and fine, and the distiller's; their odds read right, and they cross the network whole.
     */
    static void siftingTablesReadable(GameTestHelper helper) {
        var tables = LootSummaries.collect(helper.getLevel().getServer());
        var gravel = ResourceLocation.parse("minecraft:gravel");
        var drops = tables.getOrDefault(LootSummaries.siftingTable(gravel, false), List.of());
        check(helper, drops.stream().anyMatch(d -> d.stack().is(Items.FLINT)), "gravel can give flint");
        double total = drops.stream().mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, total > 0.1 && total <= 1.0001, "chances add up to at most 100%, got " + total);
        check(helper, drops.stream().anyMatch(d -> d.stack().is(Items.IRON_NUGGET) && d.min() == 1 && d.max() == 3), "count ranges are read");
        check(helper, tables.containsKey(LootSummaries.siftingTable(ResourceLocation.parse("minecraft:dirt"), true)),
                "the Brass Mesh's tables go too");
        Function<List<LootSummaries.Drop>, List<String>> shape = list -> list.stream()
                .map(d -> BuiltInRegistries.ITEM.getKey(d.stack().getItem()) + " " + d.min() + "-" + d.max() + " " + d.chance()).toList();
        check(helper, shape.apply(drops).equals(shape.apply(LootSummaries.bundled(
                LootSummaries.siftingTable(gravel, false)))), "with no data pack over it, the server's table is the jar's");
        check(helper, LootSummaries.bundled(ResourceLocation.parse("nookworks:sifting/minecraft/nothing")).isEmpty(),
                "a missing table gives no drops");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        LootSummaries.STREAM_CODEC.encode(buffer, tables);
        var sent = LootSummaries.STREAM_CODEC.decode(buffer);
        check(helper, sent.keySet().equals(tables.keySet()) && sent.get(LootSummaries.siftingTable(gravel, false)).size() == drops.size(),
                "and they cross the network whole: " + sent.size() + " tables");
        helper.succeed();
    }

    /**
     * The Sifter page lists what the Sifter hands out, not the bare table: each thing as a roll of it comes out
     * ({@link SifterBlockEntity#given} - Farmer's Delight's rice and onion in place of ours, nothing of a part switched
     * off), for every siftable item, ordinary and fine. It listed our rice beside Farmer's Delight, which the Sifter
     * then never gave.
     */
    static void siftingPageShowsWhatTheSifterGives(GameTestHelper helper) {
        int pages = 0;
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(SifterBlockEntity.SIFTABLE)) {
            ResourceLocation id = holder.unwrapKey().orElseThrow().location();
            for (boolean fine : new boolean[] {false, true}) {
                List<ItemStack> table = LootSummaries.get(LootSummaries.siftingTable(id, fine)).stream()
                        .map(drop -> SifterBlockEntity.given(drop.stack())).filter(stack -> !stack.isEmpty()).toList();
                List<ItemStack> shown = LootSummaries.sifted(id, fine).stream().map(LootSummaries.Drop::stack).toList();
                check(helper, ItemStack.listMatches(shown, table), id + (fine ? " (fine)" : "") + ": the page shows " + shown
                        + ", the Sifter gives " + table);
                pages += shown.isEmpty() ? 0 : 1;
            }
        }
        check(helper, pages > 10, "every siftable item has its page, got " + pages);
        ResourceLocation dirt = ResourceLocation.parse("minecraft:dirt");
        check(helper, LootSummaries.sifted(dirt, false).stream().anyMatch(drop -> ItemStack.isSameItem(drop.stack(),
                ModCrops.preferred(new ItemStack(ModCrops.BROWN_RICE.get())))), "dirt's page has the rice the Sifter gives");
        // With the kitchen switched off, its crops leave the page as they leave the Sifter. The setting is put back before
        // anything else runs: nothing is written to disk, and no other test sees it.
        var kitchen = NookworksStartupConfig.feature("kitchen");
        boolean was = kitchen.getAsBoolean();
        List<LootSummaries.Drop> off;
        try {
            kitchen.set(false);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            kitchen.clearCache();
            off = LootSummaries.sifted(dirt, false);
        } finally {
            kitchen.set(was);
            dev.nookworks.feature.WorldFeatures.changed(); // what parts switched off take along is worked out again
            kitchen.clearCache();
        }
        check(helper, off.stream().noneMatch(drop -> drop.stack().is(ModCrops.BROWN_RICE.get()) || drop.stack().is(ModKitchen.CORN_KERNELS.get())
                || drop.stack().is(ModKitchen.ONION.get())), "the kitchen off, dirt's page lists no rice, corn or onion: " + off);
        check(helper, off.stream().anyMatch(drop -> drop.stack().is(Items.WHEAT_SEEDS)), "and still the rest of the table");
        helper.succeed();
    }

    // ---- Brass Mesh, pitcher pods, Return Scroll ----

    /**
     * The mesh sits in its own slot, which only it fits and which hoppers never touch. Fitted, the sifter takes twice
     * as long and turns up more of the small things (raw zinc) and less of the coarse (flint).
     */
    static void sifterBrassMeshKeepsTheSmallThings(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        ItemStack mesh = new ItemStack(ModItems.BRASS_MESH.get());
        check(helper, sifter.canPlaceItem(SifterBlockEntity.MESH, mesh), "the mesh fits its slot");
        check(helper, !sifter.canPlaceItem(SifterBlockEntity.MESH, new ItemStack(Items.GRAVEL)), "gravel does not");
        check(helper, !sifter.canPlaceItem(SifterBlockEntity.INPUT, mesh), "and the mesh is not something to sift");
        for (Direction side : Direction.values()) {
            check(helper, Arrays.stream(sifter.getSlotsForFace(side)).noneMatch(s -> s == SifterBlockEntity.MESH),
                    "no hopper reaches the mesh from " + side);
        }
        int plain = sifter.ticksPerBlock();
        sifter.setItem(SifterBlockEntity.MESH, mesh);
        check(helper, sifter.fine() && sifter.ticksPerBlock() == plain * 2, "fitted, a block takes twice as long");

        var level = helper.getLevel();
        BlockPos at = helper.absolutePos(SIFTER);
        ItemStack gravel = new ItemStack(Items.GRAVEL);
        int[] zinc = new int[2];
        int[] flint = new int[2];
        for (int fine = 0; fine < 2; fine++) {
            for (int i = 0; i < 10000; i++) {
                for (ItemStack result : SifterBlockEntity.sift(level, at, gravel, fine == 1)) {
                    zinc[fine] += result.is(ModItems.RAW_ZINC.get()) ? 1 : 0;
                    flint[fine] += result.is(Items.FLINT) ? 1 : 0;
                }
            }
        }
        check(helper, zinc[1] > zinc[0] * 3 / 2, "the mesh keeps far more raw zinc per block, " + zinc[1] + " against " + zinc[0]);
        // Flint is too big for either: the finer mesh holds it just as the plain net does (10000 rolls at ~28%: well
        // within 15% of each other).
        check(helper, Math.abs(flint[1] - flint[0]) < flint[0] * 0.15, "and keeps flint just the same, " + flint[1] + " against " + flint[0]);
        check(helper, SifterBlockEntity.sift(level, at, new ItemStack(ModItems.CRUDE_SALT.get()), true).stream()
                .anyMatch(s -> s.is(ModItems.SALT.get())), "an input with no fine table sifts as usual through the mesh");
        helper.succeed();
    }

    /**
     * Pitcher pods come out of sifted dirt, so cyan dye does not need a desert and a sniffer - rarely (one block in some
     * three hundred): enough to start a patch of pitcher plants, not to live on.
     */
    static void dirtSiftsIntoPitcherPods(GameTestHelper helper) {
        var level = helper.getLevel();
        int found = 0;
        for (int i = 0; i < 20_000; i++) {
            found += SifterBlockEntity.sift(level, helper.absolutePos(SIFTER), new ItemStack(Items.DIRT)).stream()
                    .filter(stack -> stack.is(Items.PITCHER_POD)).mapToInt(ItemStack::getCount).sum();
        }
        check(helper, found >= 20 && found <= 110, "20,000 blocks of dirt should give about 60 pitcher pods, gave " + found);
        for (int i = 0; i < 2000; i++) {
            check(helper, SifterBlockEntity.sift(level, helper.absolutePos(SIFTER), new ItemStack(Items.DIRT), i % 2 == 0).stream()
                    .noneMatch(stack -> stack.is(Items.TORCHFLOWER_SEEDS)), "but never torchflower seeds: those are the sniffer's");
        }
        helper.succeed();
    }

    /**
     * Podzol and soul soil are sifted (the user, 2026-11-15): they are grains, as moss, nylium and sculk are not. Podzol
     * gives the forest floor's things - torn moss as a carpet, bamboo now and then - and has nothing small for the mesh;
     * soul soil gives bones, a skeleton skull once in a thousand, more gold and quartz through the mesh, and no nether
     * wart (that grows on soul sand).
     */
    static void podzolAndSoulSoil(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        for (var soil : List.of(Items.PODZOL, Items.SOUL_SOIL)) {
            check(helper, sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(soil)), soil + " can be sifted");
        }
        for (var mat : List.of(Items.MOSS_BLOCK, Items.CRIMSON_NYLIUM, Items.WARPED_NYLIUM, Items.SCULK)) {
            check(helper, !sifter.canPlaceItem(SifterBlockEntity.INPUT, new ItemStack(mat)), mat + " is no grains to sift");
        }
        check(helper, near(chance("minecraft:podzol", false, Items.MOSS_CARPET), 0.04)
                && near(chance("minecraft:podzol", false, Items.BAMBOO), 0.02)
                && near(chance("minecraft:podzol", false, Items.SPRUCE_SAPLING), 0.06)
                && near(chance("minecraft:podzol", false, Items.SWEET_BERRIES), 0.05), "podzol gives the forest floor's things");
        check(helper, LootSummaries.bundled(LootSummaries.siftingTable(ResourceLocation.parse("minecraft:podzol"), true)).isEmpty(),
                "nothing in podzol is small: no table of its own through the mesh");
        check(helper, near(chance("minecraft:soul_soil", false, Items.BONE), 0.33)
                && near(chance("minecraft:soul_soil", false, Items.SKELETON_SKULL), 0.001)
                && near(chance("minecraft:soul_soil", false, Items.NETHER_WART), 0), "soul soil gives bones and, rarely, a skull");
        check(helper, near(chance("minecraft:soul_soil", true, Items.GOLD_NUGGET), 0.12)
                && near(chance("minecraft:soul_soil", true, Items.QUARTZ), 0.125)
                && near(chance("minecraft:soul_soil", true, Items.BONE), 0.33), "the mesh keeps its gold and quartz, its bones as before");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (var soil : List.of(Items.PODZOL, Items.SOUL_SOIL)) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(soil, 4));
            helper.useBlock(SIFTER, player);
            check(helper, player.getMainHandItem().getCount() == 3, "a click sifts one " + soil + " by hand");
        }
        helper.succeed();
    }

    /**
     * More kinds of things out of the old tables (the user, 2026-11-15), each taken from the roll of nothing, so every old
     * thing keeps its chance. No bone meal out of anything (「粉から粉が出てくるのは変」); the sea's treasures from sand,
     * rarely, and none from red sand; a wooden hoe out of dirt is worn.
     */
    static void moreKindsAtTheOldOdds(GameTestHelper helper) {
        check(helper, near(chance("minecraft:dirt", false, Items.WHEAT_SEEDS), 0.09)
                && near(chance("minecraft:gravel", false, Items.FLINT), 300 / 1080.0)
                && near(chance("minecraft:sand", false, Items.GOLD_NUGGET), 100 / 970.0)
                && near(chance("minecraft:mud", false, Items.CLAY_BALL), 360 / 838.0), "the old things keep their chances");
        check(helper, near(chance("minecraft:dirt", false, Items.BONE), 0.04) && near(chance("minecraft:dirt", false, Items.FEATHER), 0.01)
                && near(chance("minecraft:dirt", false, Items.WOODEN_HOE), 0.004), "dirt: more bones, a feather, an old hoe");
        check(helper, near(chance("minecraft:rooted_dirt", false, Items.GLOW_BERRIES), 0.015), "rooted dirt: glow berries");
        check(helper, chance("minecraft:mud", false, Items.LILY_PAD) > 0.01 && chance("minecraft:mud", false, Items.MANGROVE_PROPAGULE) > 0.015
                && chance("minecraft:mud", false, Items.STRING) > 0.005 && chance("minecraft:mud", false, Items.FISHING_ROD) > 0,
                "mud: the swamp's lily pad and propagule, string, an old rod");
        check(helper, chance("minecraft:gravel", false, Items.ARROW) > 0 && chance("minecraft:red_sand", false, Items.RAIL) > 0,
                "gravel an arrow, red sand a rail");
        for (var treasure : List.of(Items.PRISMARINE_SHARD, Items.PRISMARINE_CRYSTALS, Items.NAUTILUS_SHELL, Items.TURTLE_SCUTE)) {
            double sand = chance("minecraft:sand", false, treasure);
            check(helper, sand > 0 && sand < 0.006, "sand gives " + treasure + " rarely: " + sand);
            check(helper, chance("minecraft:red_sand", false, treasure) == 0 && chance("minecraft:red_sand", true, treasure) == 0,
                    "red sand has none of the sea's things: " + treasure);
        }
        check(helper, chance("minecraft:sand", false, Items.HEART_OF_THE_SEA) == 0, "no heart of the sea");
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(SifterBlockEntity.SIFTABLE)) {
            String id = holder.unwrapKey().orElseThrow().location().toString();
            for (boolean fine : new boolean[] {false, true}) {
                check(helper, chance(id, fine, Items.BONE_MEAL) == 0, id + " gives no bone meal: no powder out of powder");
            }
        }
        ItemStack hoe = ItemStack.EMPTY;
        for (int i = 0; i < 20_000 && hoe.isEmpty(); i++) {
            hoe = SifterBlockEntity.sift(helper.getLevel(), helper.absolutePos(SIFTER), new ItemStack(Items.DIRT)).stream()
                    .filter(stack -> stack.is(Items.WOODEN_HOE)).findFirst().orElse(ItemStack.EMPTY);
        }
        check(helper, !hoe.isEmpty() && hoe.isDamaged() && hoe.getDamageValue() < hoe.getMaxDamage(),
                "a hoe out of dirt is worn, not broken: " + hoe + " " + hoe.getDamageValue() + "/" + hoe.getMaxDamage());
        helper.succeed();
    }

    private SifterTests() {}
}
