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
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
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
        List<ItemEntity> drops = helper.getLevel().getEntities(EntityTypes.ITEM,
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
     * dragon egg - which JEI's page writes as one in a million, not as 0.0%.
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
        double something = drops.stream().mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, Math.abs(something - 0.202001) < 1e-9, "four rolls in five give nothing (less a End Seed's one in five "
                + "hundred and the egg's one in a million): " + (1 - something));
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
        check(helper, flint > 470 && flint < 730, "flint should be ~30% (600/2000), got " + flint);
        check(helper, nothing > 570 && nothing < 840, "nothing should be ~35% (704/2000), got " + nothing);
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
        var gravel = Identifier.parse("minecraft:gravel");
        var drops = tables.getOrDefault(LootSummaries.siftingTable(gravel, false), List.of());
        check(helper, drops.stream().anyMatch(d -> d.stack().is(Items.FLINT)), "gravel can give flint");
        double total = drops.stream().mapToDouble(LootSummaries.Drop::chance).sum();
        check(helper, total > 0.1 && total <= 1.0001, "chances add up to at most 100%, got " + total);
        check(helper, drops.stream().anyMatch(d -> d.stack().is(Items.IRON_NUGGET) && d.min() == 1 && d.max() == 3), "count ranges are read");
        check(helper, tables.containsKey(LootSummaries.siftingTable(Identifier.parse("minecraft:dirt"), true)),
                "the Brass Mesh's tables go too");
        Function<List<LootSummaries.Drop>, List<String>> shape = list -> list.stream()
                .map(d -> BuiltInRegistries.ITEM.getKey(d.stack().getItem()) + " " + d.min() + "-" + d.max() + " " + d.chance()).toList();
        check(helper, shape.apply(drops).equals(shape.apply(LootSummaries.bundled(
                LootSummaries.siftingTable(gravel, false)))), "with no data pack over it, the server's table is the jar's");
        check(helper, LootSummaries.bundled(Identifier.parse("nookworks:sifting/minecraft/nothing")).isEmpty(),
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
            Identifier id = holder.unwrapKey().orElseThrow().identifier();
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
        Identifier dirt = Identifier.parse("minecraft:dirt");
        check(helper, LootSummaries.sifted(dirt, false).stream().anyMatch(drop -> ItemStack.isSameItem(drop.stack(),
                ModCrops.preferred(new ItemStack(ModCrops.BROWN_RICE.get())))), "dirt's page has the rice the Sifter gives");
        // With the kitchen switched off, its crops leave the page as they leave the Sifter. The setting is put back before
        // anything else runs: nothing is written to disk, and no other test sees it.
        var kitchen = NookworksStartupConfig.feature("kitchen");
        boolean was = kitchen.getAsBoolean();
        List<LootSummaries.Drop> off;
        try {
            kitchen.set(false);
            kitchen.clearCache();
            off = LootSummaries.sifted(dirt, false);
        } finally {
            kitchen.set(was);
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

    private SifterTests() {}
}
