package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.StorageTests.placeSorter;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.imageio.ImageIO;

import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.StorageBlock;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.block.entity.TrashCanBlockEntity;
import dev.nookworks.dowsing.DowsingSearch;
import dev.nookworks.dowsing.NookDowsingRodItem;
import dev.nookworks.experience.AbsorptionPadBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.LiquidExperienceBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.item.Hammers;
import dev.nookworks.item.VelariumGear;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.Electrolysis;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.MercuryBlock;
import dev.nookworks.mercury.MercuryMachineBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mixin.ExperienceOrbAccessor;
import dev.nookworks.poster.PosterEntity;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.Posters;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterSettings;
import dev.nookworks.sorter.SorterTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The mercury line: Retort, Stirring Tank, Distiller, Electrolytic Cell, pipes and tanks, Velarium. */
final class MercuryTests {
    static void register() {
        test("nook_dowsing_rod_finds_the_nearest_and_says_when_there_is_none", MercuryTests::nookDowsingRod);
        test("nook_dowsing_rod_find_goes_to_the_rod_moved_meanwhile", MercuryTests::nookDowsingRodMovedMeanwhile);
        test("dowsing_search_runs_a_little_a_tick_one_a_player", MercuryTests::dowsingSearchRunsOverTicks);
        test("mercury_retort_makes_mercury_and_sulfur", MercuryTests::retortMakesMercury);
        test("mercury_stirring_tank_amalgamates_through_pipes", MercuryTests::stirringTankAmalgamates);
        test("stirring_tank_makes_mud_of_dirt_and_water", MercuryTests::stirringTankMakesMud);
        test("mercury_distiller_needs_tower_and_recovers", MercuryTests::distillerNeedsTower);
        test("mercury_poisons_on_touch", MercuryTests::mercuryPoisons);
        test("mercury_poison_skips_creative", MercuryTests::mercuryPoisonSkipsCreative);
        test("mercury_tank_buckets_and_no_foreign_capability", MercuryTests::mercuryTankBuckets);
        test("velarium_only_from_distilling", MercuryTests::velariumOnlyFromDistilling);
        test("velarium_gear_beats_netherite", MercuryTests::velariumGearBeatsNetherite);
        test("sulfur_makes_gunpowder", MercuryTests::sulfurMakesGunpowder);
        test("brine_makes_caustic_soda", MercuryTests::brineMakesCausticSoda);
        test("logs_become_fine_paper", MercuryTests::logsBecomeFinePaper);
        test("poster_hangs_thin", MercuryTests::posterHangsThin);
        test("laser_costs_real_power", MercuryTests::laserCostsRealPower);
        test("retort_pulps_wood_chips", MercuryTests::retortPulpsWoodChips);
        test("retort_alloys_brass", MercuryTests::retortAlloysBrass);
        test("sifter_finds_zinc", MercuryTests::sifterFindsZinc);
        test("storage_is_a_barrel", MercuryTests::storageIsABarrel);
        test("precision_sorter_filters", MercuryTests::precisionSorterFilters);
        test("repair_box_mends_without_mending", MercuryTests::repairBoxMendsWithoutMending);
        test("repair_boxes_join_into_one", MercuryTests::repairBoxesJoin);
        test("repair_box_pair_pools_power_and_experience", MercuryTests::repairBoxPairPools);
        test("repair_box_tells_clients_how_full_it_is", MercuryTests::repairBoxTellsHowFull);
        test("wrench_turns_blocks", MercuryTests::wrenchTurnsBlocks);
        test("trash_can_needs_power_and_a_hopper", MercuryTests::trashCanNeedsPowerAndAHopper);
        test("building_wand_fills_a_surface", MercuryTests::buildingWandFillsASurface);
        test("absorption_pad_collects_orbs", MercuryTests::absorptionPadCollectsOrbs);
        test("absorption_pad_drains_a_level_a_second", 200, MercuryTests::absorptionPadDrainsALevelASecond);
        test("absorption_pad_stores_what_it_takes_at_any_level", MercuryTests::absorptionPadAtACommandsLevel);
        test("pad_pipes_into_tank", 400, MercuryTests::padPipesIntoTank);
        test("experience_tank_talks_to_other_mods", MercuryTests::experienceTankTalksToOtherMods);
        test("liquid_experience_credits_the_kill", MercuryTests::liquidExperienceCreditsTheKill);
        test("every_model_resolves_to_real_textures", MercuryTests::everyModelResolves);
        test("finished_block_art_is_present", MercuryTests::finishedBlockArtIsPresent);
        test("poster_artwork_matches", MercuryTests::posterArtworkMatches);
        test("printing_press_prints_posters", MercuryTests::printingPressPrintsPosters);
        test("absorption_pad_takes_every_merged_orb", MercuryTests::absorptionPadTakesMergedOrbs);
        test("absorption_pad_takes_an_orb_lying_on_it", MercuryTests::absorptionPadTakesAnOrbLyingOnIt);
    }

    // ---- Mercury ----
    /**
     * The Nook Dowsing Rod's search finds the nearest of what it looks for from where it starts - here the biome the test
     * stands in, in the first column it looks at. The test world is flat and has no sulfur caves: the rod says so at once,
     * searching nothing, clears the needle it had and is not worn; it cools down as any use does.
     */
    private static void nookDowsingRod(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        Holder<Biome> here = level.getBiome(origin);
        var found = DowsingSearch.findNow(level, origin, here::equals, 256);
        check(helper, found.outcome() == DowsingSearch.Outcome.FOUND && found.at() != null
                && found.at().getX() == origin.getX() && found.at().getZ() == origin.getZ(),
                "the biome it stands in is found where it stands: " + found);
        boolean hasCaves = level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes().stream()
                .anyMatch(biome -> biome.is(NookDowsingRodItem.HAS_CINNABAR));
        check(helper, !hasCaves, "the test world has no sulfur caves (it is flat)");
        check(helper, DowsingSearch.findNow(level, origin, biome -> biome.is(NookDowsingRodItem.HAS_CINNABAR), 256).outcome()
                == DowsingSearch.Outcome.NOT_HERE, "and a search for them says so");

        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        // well above the arena: a player within 8 blocks of the next test's arena draws its experience orbs away
        Vec3 at = Vec3.atBottomCenterOf(origin.above(64));
        player.snapTo(at.x, at.y, at.z);
        ItemStack rod = new ItemStack(ModItems.NOOK_DOWSING_ROD.get());
        rod.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(level.dimension(), origin.east(100))), false));
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        rod.use(level, player, InteractionHand.MAIN_HAND);
        check(helper, !rod.has(DataComponents.LODESTONE_TRACKER), "with nothing to find, the needle it had is cleared");
        check(helper, rod.getDamageValue() == 0, "and the rod not worn");
        check(helper, player.getCooldowns().isOnCooldown(rod), "it cools down after a use");
        check(helper, !NookDowsingRodItem.marked(rod), "and carries no mark of a search once it has answered");
        helper.succeed();
    }

    /**
     * A search takes a while, and the rod may be moved meanwhile: to the other hand, as an inventory click moves it, which
     * splits it off into a new stack and leaves the old one empty. The find goes to the rod that made the search - its
     * needle set on what was found, in the dimension searched, a step of wear taken off it - not to the empty husk.
     */
    private static void nookDowsingRodMovedMeanwhile(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(3, 2, 3)).above(64)); // clear of the arenas' orbs
        player.snapTo(stand.x, stand.y, stand.z);
        ItemStack rod = new ItemStack(ModItems.NOOK_DOWSING_ROD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rod);
        long search = NookDowsingRodItem.mark(rod, 42L);
        player.setItemInHand(InteractionHand.OFF_HAND, rod.split(1));
        BlockPos from = player.blockPosition();
        BlockPos cinnabar = from.offset(320, -50, -96);
        NookDowsingRodItem.found(level, player, search, from, new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, cinnabar));
        ItemStack moved = player.getOffhandItem();
        LodestoneTracker needle = moved.get(DataComponents.LODESTONE_TRACKER);
        check(helper, needle != null && needle.target().equals(Optional.of(GlobalPos.of(level.dimension(), cinnabar))) && !needle.tracked(),
                "the rod in the other hand points at what was found: " + needle);
        check(helper, moved.getDamageValue() == 1, "a find wears it a step, got " + moved.getDamageValue());
        check(helper, !NookDowsingRodItem.marked(moved), "and it no longer carries the search's mark");
        check(helper, rod.isEmpty() && !rod.has(DataComponents.LODESTONE_TRACKER), "the empty stack left behind is given nothing");

        // found straight below where it was used: still pointed there, and worn
        BlockPos below = from.below(40);
        long again = NookDowsingRodItem.mark(moved, 7L);
        NookDowsingRodItem.found(level, player, again, from, new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, below));
        LodestoneTracker down = moved.get(DataComponents.LODESTONE_TRACKER);
        check(helper, down != null && down.target().equals(Optional.of(GlobalPos.of(level.dimension(), below))) && moved.getDamageValue() == 2,
                "a find straight below is pointed at and worn too: " + down + ", damage " + moved.getDamageValue());

        // and a search whose rod has left the player's inventory sets no needle on anything they hold
        long gone = NookDowsingRodItem.mark(new ItemStack(ModItems.NOOK_DOWSING_ROD.get()), 99L);
        NookDowsingRodItem.found(level, player, gone, from, new DowsingSearch.Result(DowsingSearch.Outcome.FOUND, from.east(500)));
        check(helper, moved.get(DataComponents.LODESTONE_TRACKER).equals(down) && moved.getDamageValue() == 2,
                "a find for another rod leaves this one as it was");
        helper.succeed();
    }

    /**
     * The search proper, as a rod makes it: begun, it answers in a later tick (here, at once in the column it starts
     * from, looking for the biome it starts in); while it runs, the same player cannot begin another.
     */
    private static void dowsingSearchRunsOverTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
        Holder<Biome> here = level.getBiome(origin);
        Object owner = new Object();
        DowsingSearch.Result[] answer = new DowsingSearch.Result[1];
        check(helper, DowsingSearch.start(level, origin, here::equals, 256, owner, result -> answer[0] = result),
                "a search begins");
        check(helper, answer[0] == null, "and answers in a later tick, not at once");
        check(helper, !DowsingSearch.start(level, origin, here::equals, 256, owner, result -> {}),
                "while it runs, its owner cannot begin another");
        helper.succeedWhen(() -> check(helper, answer[0] != null && answer[0].outcome() == DowsingSearch.Outcome.FOUND
                && answer[0].at() != null && answer[0].at().getX() == origin.getX() && answer[0].at().getZ() == origin.getZ(),
                "it found the biome where it began: " + answer[0]));
    }

    static <T extends MercuryMachineBlockEntity> T placeMercuryMachine(GameTestHelper helper,
            Block block, BlockPos pos, Class<T> type) {
        helper.setBlock(pos, block);
        T machine = helper.getBlockEntity(pos, type);
        machine.energy().set((int) machine.energy().getCapacityAsLong());
        return machine;
    }

    static MercuryTankBlockEntity placeTank(GameTestHelper helper, BlockPos pos, int mercury) {
        helper.setBlock(pos, ModBlocks.MERCURY_TANK.get());
        var tank = helper.getBlockEntity(pos, MercuryTankBlockEntity.class);
        tank.store().set(mercury);
        return tank;
    }

    static void retortMakesMercury(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var retort = placeMercuryMachine(helper, ModBlocks.RETORT.get(), pos, RetortBlockEntity.class);
        var tank = placeTank(helper, pos.east(), 0);
        retort.setItem(0, new ItemStack(Items.CINNABAR, 2));
        for (int i = 0; i < RetortBlockEntity.TICKS + 5; i++) {
            RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        check(helper, tank.mercury() == 250, "one cinnabar gives 250 mB into the adjacent tank, has " + tank.mercury());
        check(helper, countIn(helper.getBlockEntity(pos.below(), ChestBlockEntity.class), ModItems.SULFUR_DUST.get()) == 1,
                "the sulfur dust goes into the chest below");
        check(helper, retort.getItem(0).getCount() == 1, "one cinnabar used");
        helper.succeed();
    }

    static void stirringTankAmalgamates(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var stirrer = placeMercuryMachine(helper, ModBlocks.STIRRING_TANK.get(), pos, StirringTankBlockEntity.class);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), ModBlocks.MERCURY_PIPE.get());
        }
        var tank = placeTank(helper, new BlockPos(5, 2, 3), 1_000);
        stirrer.setItem(0, new ItemStack(Items.GOLD_ORE));
        check(helper, !stirrer.canPlaceItem(0, new ItemStack(Items.DIAMOND_ORE)), "diamond is not a metal: mercury cannot take it");
        for (int i = 0; i < StirringTankBlockEntity.TICKS + 10; i++) {
            StirringTankBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), stirrer);
        }
        check(helper, countIn(helper.getBlockEntity(pos.below(), ChestBlockEntity.class), ModItems.GOLD_AMALGAM.get()) == 2,
                "one silk-touched gold ore gives 2 gold amalgam, what Fortune III would make of it in raw gold");
        check(helper, tank.mercury() + stirrer.mercury() == 800, "200 mB were used, pulled through the pipes");
        helper.succeed();
    }

    /**
     * Dirt and piped water in a powered Stirring Tank make mud, a bottle's worth of water (250 mB) to a block, into the
     * chest below (the user, 2026-10-03); short of water it stops and says so. Making mud it draws no mercury from its
     * pipes, its screen shows the water tank first, and its item keeps the water when it is broken. A water bucket
     * clicked on it pours in, as the game clicks, and the hand keeps the empty bucket.
     */
    static void stirringTankMakesMud(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var stirrer = placeMercuryMachine(helper, ModBlocks.STIRRING_TANK.get(), pos, StirringTankBlockEntity.class);
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), ModBlocks.MERCURY_PIPE.get());
        }
        var mercury = placeTank(helper, new BlockPos(5, 2, 3), 1_000);
        for (Item dirt : List.of(Items.DIRT, Items.COARSE_DIRT, Items.ROOTED_DIRT)) {
            check(helper, stirrer.canPlaceItem(0, new ItemStack(dirt)), dirt + " is stirred into mud, as a water bottle makes it");
        }
        check(helper, !StirringTankBlockEntity.muddies(new ItemStack(Items.GRASS_BLOCK))
                && !StirringTankBlockEntity.muddies(new ItemStack(Items.MUD)), "grass and mud are not");
        check(helper, Move.insert(stirrer.fluidHandler(), FluidResource.of(Fluids.WATER), 600) == 600, "water is piped in");
        stirrer.setItem(0, new ItemStack(Items.DIRT, 3));
        for (int i = 0; i < StirringTankBlockEntity.TICKS * 3 + 10; i++) {
            StirringTankBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), stirrer);
        }
        check(helper, countIn(helper.getBlockEntity(pos.below(), ChestBlockEntity.class), Items.MUD) == 2, "two mud in the chest below");
        check(helper, stirrer.waterStored() == 100 && stirrer.getItem(0).getCount() == 1,
                "250 mB to each, one dirt left: " + stirrer.waterStored() + " mB, " + stirrer.getItem(0));
        check(helper, stirrer.guiStatus() == MachineStatus.LOW_WATER, "100 mB is not enough for the third: " + stirrer.guiStatus());
        check(helper, stirrer.mercury() == 0 && mercury.mercury() == 1_000, "no mercury drawn while it makes mud");
        check(helper, stirrer.guiTanks().getFirst().kind() == GuiTank.Kind.WATER
                && stirrer.guiTanks().size() == MachineLayout.STIRRING_TANK.tanks().size(), "the water tank first on its screen");
        for (MachineLayout layout : MachineLayout.values()) {
            check(helper, layout.tanks().size() <= MachineMenu.MAX_TANKS, layout + " shows more tanks than a screen sends");
        }
        var held = stirrer.collectComponents().get(ModDataComponents.HELD_FLUIDS.get());
        check(helper, held != null && held.stream().anyMatch(f -> f.getFluid() == Fluids.WATER && f.getAmount() == 100),
                "its item would keep the water: " + held);

        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(Items.WATER_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockPos at = helper.absolutePos(pos);
        player.gameMode.useItemOn(player, helper.getLevel(), bucket, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.NORTH, 0.5), Direction.NORTH, at, false));
        check(helper, stirrer.waterStored() == 1_100 && player.getMainHandItem().is(Items.BUCKET),
                "a water bucket pours in: " + stirrer.waterStored() + " mB, " + player.getMainHandItem());
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    static void distillerNeedsTower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var distiller = placeMercuryMachine(helper, ModBlocks.DISTILLER.get(), pos, DistillerBlockEntity.class);
        var tank = placeTank(helper, pos.east(), 0);
        distiller.setItem(0, new ItemStack(ModItems.IRON_AMALGAM.get(), 2));
        Runnable run = () -> {
            for (int i = 0; i < DistillerBlockEntity.TICKS + 5; i++) {
                DistillerBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), distiller);
            }
        };
        run.run();
        check(helper, distiller.getItem(0).getCount() == 2, "no tower, no distilling");
        helper.setBlock(pos.above(1), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(3), ModBlocks.CONDENSER.get());
        check(helper, DistillerBlockEntity.isFormed(helper.getLevel(), helper.absolutePos(pos)), "tower complete");
        run.run();
        check(helper, distiller.getItem(0).getCount() == 1, "one amalgam distilled");
        int ingots = 0;
        for (int slot = 1; slot < distiller.getContainerSize(); slot++) {
            ingots += distiller.getItem(slot).is(Items.IRON_NUGGET) ? distiller.getItem(slot).getCount() : 0;
        }
        var drops = helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(pos)).inflate(2), e -> e.getItem().is(Items.IRON_NUGGET));
        for (ItemEntity e : drops) {
            ingots += e.getItem().getCount();
        }
        check(helper, ingots == 10 || ingots == 12, "ten iron nuggets (sometimes twelve), got " + ingots);
        check(helper, tank.mercury() + distiller.mercury() == 90, "90 mB of mercury recovered");
        helper.succeed();
    }

    static void mercuryPoisons(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.MERCURY.get());
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, pos);
        // Mercury drowns what stands in it, and a drowned pig holds no effects: this test is about the
        // exposure, not about how long a pig lasts, so take dying out of the picture. Do not teleport it
        // into place either - an entity that is moved rather than walking skips the inside-block scan.
        pig.setPermanentlyInvulnerable(true);
        helper.succeedWhen(() -> {
            check(helper, pig.hasEffect(MobEffects.POISON), "the pig in mercury is poisoned");
            check(helper, pig.hasEffect(MobEffects.NAUSEA), "and dizzy");
        });
    }

    /** The exposure effect itself, with no physics in the way: applied to anything living, but not in creative. */
    static void mercuryPoisonSkipsCreative(GameTestHelper helper) {
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
        MercuryBlock.poison(helper.getLevel(), pig);
        check(helper, pig.hasEffect(MobEffects.POISON)
                && pig.hasEffect(MobEffects.NAUSEA), "mercury poisons a mob");

        // A mock *server* player has no connection, and giving one an effect tries to send it a packet.
        Player survivor = helper.makeMockPlayer(GameType.SURVIVAL);
        MercuryBlock.poison(helper.getLevel(), survivor);
        check(helper, survivor.hasEffect(MobEffects.POISON), "and a player in survival");

        Player builder = helper.makeMockPlayer(GameType.CREATIVE);
        MercuryBlock.poison(helper.getLevel(), builder);
        check(helper, !builder.hasEffect(MobEffects.POISON),
                "but someone building in creative is left alone");
        helper.succeed();
    }

    static void mercuryTankBuckets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var tank = placeTank(helper, pos, 1_500);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        BlockState state = helper.getBlockState(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, player.getMainHandItem().is(ModItems.MERCURY_BUCKET.get()) && tank.mercury() == 500, "a bucket takes 1000 mB out");
        state.useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        check(helper, player.getMainHandItem().is(Items.BUCKET) && tank.mercury() == 1_500, "and pours it back");
        check(helper, helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), null) == null,
                "other mods get no fluid handler for mercury");
        helper.succeed();
    }

    /** Crafts the 3x3 grid and returns what comes out. */
    static ItemStack craft(GameTestHelper helper, List<ItemStack> grid) {
        var input = CraftingInput.of(3, 3, grid);
        var level = helper.getLevel();
        return level.getServer().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .<ItemStack>map(r -> r.value().assemble(input))
                .orElse(ItemStack.EMPTY);
    }

    static void sulfurMakesGunpowder(GameTestHelper helper) {
        ItemStack bone = new ItemStack(Items.BONE_MEAL);
        ItemStack coal = new ItemStack(Items.CHARCOAL);
        ItemStack dust = new ItemStack(ModItems.SULFUR_DUST.get());
        ItemStack powder = craft(helper, List.of(bone, bone, bone, bone, coal, bone, bone, dust, bone));
        check(helper, powder.is(Items.GUNPOWDER) && powder.getCount() == 3, "saltpetre, charcoal and sulfur make gunpowder");
        // Powdering a sulfur block is the Crusher's job, not something you do by hand on a table.
        ItemStack sulfur = craft(helper, List.of(new ItemStack(Items.SULFUR), ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        check(helper, sulfur.isEmpty(), "a sulfur block does not grind itself on a crafting table");
        ItemStack ingot = craft(helper, Collections.nCopies(9, new ItemStack(ModItems.VELARIUM_NUGGET.get())));
        check(helper, ingot.is(ModItems.VELARIUM_INGOT.get()), "nine nuggets make one ingot");
        helper.succeed();
    }

    static void brineMakesCausticSoda(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var retort = placeMercuryMachine(helper, ModBlocks.RETORT.get(), pos, RetortBlockEntity.class);
        retort.setItem(0, new ItemStack(Items.WATER_BUCKET));
        for (int i = 0; i < RetortBlockEntity.TICKS + 5; i++) {
            RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        check(helper, countIn(chest, ModItems.CRUDE_SALT.get()) == 2, "boiling a bucket of sea water dry leaves 2 crude salt");
        check(helper, countIn(chest, Items.BUCKET) == 1, "and gives the bucket back");
        check(helper, retort.mercury() == 0, "no mercury comes out of water");

        // Crude salt still has its bittern in it: the mercury cell only takes the sifted salt.
        check(helper, MercuryRecipes.amalgamation(new ItemStack(ModItems.CRUDE_SALT.get())) == null, "crude salt is too dirty for the tank");
        check(helper, new ItemStack(ModItems.CRUDE_SALT.get()).is(SifterBlockEntity.SIFTABLE), "crude salt is siftable");
        var washed = SifterBlockEntity.sift(helper.getLevel(), helper.absolutePos(pos), new ItemStack(ModItems.CRUDE_SALT.get()));
        // (salt among what comes out, not alone: an add-on may add to the table - nookTechnology's limestone powder)
        check(helper, washed.stream().anyMatch(stack -> stack.is(ModItems.SALT.get())), "sifting it gives salt: " + washed);

        check(helper, MercuryRecipes.amalgamation(new ItemStack(ModItems.SALT.get())) == null,
                "salt no longer amalgamates in the Stirring Tank: the Electrolytic Cell makes caustic soda of it");
        var cell = Electrolysis.of(new ItemStack(ModItems.SALT.get()));
        check(helper, cell != null && cell.result().value() == ModItems.CAUSTIC_SODA.get() && cell.needsMercury(), "a mercury cell recipe");
        check(helper, BuiltInRegistries.ITEM.getValue(Nookworks.id("sodium_amalgam")) == ModItems.CAUSTIC_SODA.get(),
                "sodium amalgam from 0.2.0 loads as the caustic soda it distilled to");
        helper.succeed();
    }

    static void logsBecomeFinePaper(GameTestHelper helper) {
        var chips = CrushingRecipe.of(new ItemStack(Items.OAK_LOG));
        check(helper, chips != null && chips.output().is(ModItems.WOOD_CHIPS.get()) && chips.output().getCount() == 4,
                "the crusher turns one log into 4 wood chips");
        check(helper, CrushingRecipe.of(new ItemStack(Items.SPRUCE_LOG)) != null, "every log type, not just oak");
        ItemStack pulp = new ItemStack(ModItems.WOOD_PULP.get(), 4); // cooked in the retort, see retort_pulps_wood_chips
        ItemStack sheet = craft(helper, List.of(pulp.copyWithCount(1), pulp.copyWithCount(1), ItemStack.EMPTY,
                pulp.copyWithCount(1), pulp.copyWithCount(1), ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY));
        check(helper, sheet.is(ModItems.FINE_PAPER.get()), "four pulp press into one sheet of fine paper");
        helper.succeed();
    }

    /** Loose item entities near a position, for checking that a broken machine gave its contents back. */
    static int countItemsAround(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(pos)).inflate(4), e -> true).size();
    }

    static void printingPressPrintsPosters(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.PRINTING_PRESS.get());
        var press = helper.getBlockEntity(pos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        var id = Nookworks.id("safety_cross");
        var design = Posters.get(helper.getLevel(), id);
        check(helper, design.inkCost().equals(PosterVariant.PROCESS_INKS),
                "a design that declares no inks is printed with the four process colours");

        Runnable run = () -> {
            for (int i = 0; i < PrintingPressBlockEntity.TICKS + 2; i++) {
                PrintingPressBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                        helper.getBlockState(pos), press);
            }
        };
        press.select(id);
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            press.setItem(PrintingPressBlockEntity.FIRST_INK + i,
                    new ItemStack(PosterVariant.PROCESS_INKS.get(i), 2));
        }
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).isEmpty(),
                "stocked and selected is not enough: it waits for Print");

        press.setRunning(true);
        run.run();
        ItemStack printed = press.getItem(PrintingPressBlockEntity.OUTPUT);
        check(helper, printed.is(ModItems.POSTER.get()) && printed.getCount() == 1, "one copy comes out");
        check(helper, id.equals(Posters.idOf(printed)), "carrying the chosen design");
        check(helper, press.getItem(PrintingPressBlockEntity.PAPER).getCount() == 1, "one sheet of paper was spent");
        check(helper, press.getItem(PrintingPressBlockEntity.FIRST_INK).getCount() == 1, "and one of each ink");

        check(helper, !press.running(), "and the job ends with it");
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).getCount() == 1, "one press, one copy");

        press.setItem(PrintingPressBlockEntity.PAPER, ItemStack.EMPTY);
        press.setRunning(true);
        run.run();
        check(helper, !press.running(), "a job with no paper is dropped");
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).getCount() == 1,
                "so topping the paper up later does not start the press by itself");

        var ids = Posters.ids(helper.getLevel().registryAccess());
        ToIntFunction<Identifier> area = listed -> {
            var variant = Posters.get(helper.getLevel(), listed);
            return variant.width() * variant.height();
        };
        for (int i = 1; i < ids.size(); i++) {
            check(helper, area.applyAsInt(ids.get(i - 1)) <= area.applyAsInt(ids.get(i)), "designs are listed smallest first");
        }
        check(helper, Nookworks.id("potato").equals(Posters.idOf(Posters.item(Nookworks.id("bread")))),
                "a poster saved as bread is the potato now");
        press.select(null);
        press.setRunning(true);
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).getCount() == 1,
                "Print with no design chosen does nothing");

        // Without these the press cannot be powered by a cable or fed by a pipe from any other mod.
        var energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
                helper.absolutePos(pos), null);
        check(helper, energy != null, "the press takes FE from cables");
        var items = helper.getLevel().getCapability(Capabilities.Item.BLOCK,
                helper.absolutePos(pos), Direction.DOWN);
        check(helper, items != null, "and its copies can be pulled out of the bottom");

        // Each process colour has its own slot, so a hopper cannot silt cyan into the magenta hole.
        var inks = PosterVariant.PROCESS_INKS;
        int cyanSlot = PrintingPressBlockEntity.FIRST_INK;
        int magentaSlot = cyanSlot + 1;
        check(helper, press.canPlaceItem(cyanSlot, new ItemStack(inks.get(0))), "a hopper can feed cyan into the cyan slot");
        check(helper, !press.canPlaceItem(cyanSlot, new ItemStack(inks.get(1))), "but not magenta");
        check(helper, press.canPlaceItem(magentaSlot, new ItemStack(inks.get(1))), "magenta has its own slot");
        check(helper, !press.canPlaceItem(cyanSlot, new ItemStack(Items.COBBLESTONE)), "and junk is refused");
        check(helper, Arrays.stream(press.getSlotsForFace(Direction.UP)).anyMatch(slot -> slot == cyanSlot),
                "the ink slots face the hopper above");

        Player operator = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = (PrintingPressMenu) press.createMenu(1, operator.getInventory(), operator);
        check(helper, menu.getSlot(PrintingPressBlockEntity.PAPER)
                .mayPlace(new ItemStack(ModItems.FINE_PAPER.get())), "the paper slot takes Fine Paper");
        check(helper, !menu.getSlot(PrintingPressBlockEntity.PAPER).mayPlace(new ItemStack(inks.get(0))),
                "and not dye");
        check(helper, menu.getSlot(cyanSlot).mayPlace(new ItemStack(inks.get(0))), "the GUI cyan slot takes cyan");
        check(helper, !menu.getSlot(cyanSlot).mayPlace(new ItemStack(inks.get(1))), "and refuses the others");
        check(helper, !menu.getSlot(PrintingPressBlockEntity.OUTPUT).mayPlace(printed),
                "the output is take-only");

        press.select(id);
        check(helper, !press.running(), "choosing a design does not start the press");
        check(helper, menu.clickMenuButton(operator, PrintingPressMenu.BUTTON_PRINT), "Print is a menu button");
        check(helper, press.running(), "and it starts the job");
        menu.clickMenuButton(operator, PrintingPressMenu.BUTTON_PRINT);
        check(helper, !press.running(), "pressing it again stops the job");

        // Breaking the press hands everything inside back rather than voiding it.
        int before = countItemsAround(helper, pos);
        helper.getLevel().destroyBlock(helper.absolutePos(pos), true);
        check(helper, countItemsAround(helper, pos) > before, "breaking it drops the paper and inks");
        helper.succeed();
    }

    /** The block models drawn from their own art: every texture they name must be there, and be an opaque 16x16. */
    static final List<String> FINISHED_MODELS = List.of(
            "hub_stone_on", "hub_stone_off", "experience_tank", "magnet_chest",
            "spawner_controller", "spawner_link_sender", "spawner_link_receiver",
            "retort", "mercury_tank", "generator", "generator_lit",
            "mercury_pipe_core", "mercury_pipe_side", "mercury_pipe_end", "mercury_pipe_inventory");

    /**
     * Every blockstate and item model of ours, followed down to the PNGs, so a texture that is not there is
     * caught here instead of by someone noticing a magenta block in the game. See {@link AssetAudit}.
     */
    static void everyModelResolves(GameTestHelper helper) {
        check(helper, AssetAudit.vanillaAssetsVisible(),
                "vanilla assets are not on the test classpath, so this audit would pass by seeing nothing");
        List<String> problems = AssetAudit.run();
        check(helper, problems.isEmpty(), problems.size() + " asset problem(s):\n  " + String.join("\n  ", problems));
        helper.succeed();
    }

    /**
     * A model naming a texture that is not in the jar renders as the black-and-magenta missing texture, which no
     * test that only touches blocks would ever catch. These models are hand-finished art rather than generated,
     * so nothing else checks them either.
     */
    static void finishedBlockArtIsPresent(GameTestHelper helper) {
        int checked = 0;
        for (String model : FINISHED_MODELS) {
            var json = JsonParser.parseString(readAsset(helper, "/assets/nookworks/models/block/" + model + ".json"))
                    .getAsJsonObject().getAsJsonObject("textures");
            for (var entry : json.entrySet()) {
                String texture = entry.getValue().getAsString();
                if (!texture.startsWith("nookworks:")) {
                    continue;
                }
                String path = "/assets/nookworks/" + texture.substring("nookworks:".length()).replace("block/", "textures/block/") + ".png";
                try (var stream = NookworksGameTests.class.getResourceAsStream(path)) {
                    check(helper, stream != null, model + " names a texture that is not in the jar: " + path);
                    var image = ImageIO.read(stream);
                    check(helper, image.getWidth() == 16 && image.getHeight() == 16,
                            path + " is " + image.getWidth() + "x" + image.getHeight() + ", not 16x16");
                    // These blocks are plain cubes now; a transparent pixel left over from the old overlay art
                    // would show as a hole, because none of them ask for a cutout render type any more.
                    for (int y = 0; y < 16; y++) {
                        for (int x = 0; x < 16; x++) {
                            check(helper, (image.getRGB(x, y) >>> 24) > 0, path + " is see-through at " + x + "," + y);
                        }
                    }
                    checked++;
                } catch (IOException e) {
                    throw new AssertionError("could not read " + path, e);
                }
            }
        }
        check(helper, checked >= 20, "every finished texture was checked, got " + checked);
        // The overlays these replaced would still load, so only their absence proves the swap actually happened.
        for (String gone : List.of("spawner_controller", "spawner_link_sender", "spawner_link_receiver",
                "experience_tank", "generator_side_lit")) {
            check(helper, NookworksGameTests.class.getResource("/assets/nookworks/textures/block/" + gone + ".png") == null,
                    "the old " + gone + " overlay is still in the jar");
        }
        helper.succeed();
    }

    static String readAsset(GameTestHelper helper, String path) {
        try (var stream = NookworksGameTests.class.getResourceAsStream(path)) {
            check(helper, stream != null, "missing asset " + path);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * Every design must have artwork of the size it claims, or it renders stretched or as a missing texture; and an
     * icon of its own picked by the poster item's model (tools/textures/gen_poster_icons.py), or it shows as the plain
     * rolled poster in the inventory.
     */
    static void posterArtworkMatches(GameTestHelper helper) {
        var registry = Posters.lookup(helper.getLevel().registryAccess()).orElseThrow();
        String itemModel;
        try (var stream = NookworksGameTests.class.getResourceAsStream("/assets/nookworks/items/poster.json")) {
            check(helper, stream != null, "the poster item has a model");
            itemModel = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("could not read the poster item's model", e);
        }
        int checked = 0;
        for (var id : Posters.ids(helper.getLevel().registryAccess())) {
            var design = Posters.get(helper.getLevel(), id);
            var texture = design.texture();
            String path = "/assets/" + texture.getNamespace() + "/" + texture.getPath();
            try (var stream = NookworksGameTests.class.getResourceAsStream(path)) {
                check(helper, stream != null, "artwork is missing for " + id + " (" + path + ")");
                var image = ImageIO.read(stream);
                check(helper, image.getWidth() == design.width() * 16 && image.getHeight() == design.height() * 16,
                        id + " is declared " + design.width() + "x" + design.height() + " blocks but its artwork is "
                                + image.getWidth() + "x" + image.getHeight() + " px");
            } catch (IOException e) {
                throw new AssertionError("could not read artwork for " + id, e);
            }
            check(helper, !Posters.item(id).getHoverName().getString().equals("Poster"), id + " has no name of its own");
            check(helper, itemModel.contains("\"when\": \"" + id + "\""), id + " has no icon of its own (run gen_poster_icons.py)");
            check(helper, NookworksGameTests.class.getResource("/assets/nookworks/textures/item/poster/" + id.getPath() + ".png") != null,
                    id + "'s icon is missing");
            checked++;
        }
        check(helper, checked == registry.listElementIds().count() && checked > 1, "every design was checked, got " + checked);
        helper.succeed();
    }

    static final String XP = "dev.nookworks.experience.";

    /**
     * At a level only a command gives (30,000), the pad stores what it takes. (Counted in ints held to the largest one,
     * the points before and after a drain both read the same up there, and the pad took a fifth of a level a drain for
     * nothing.)
     */
    static void absorptionPadAtACommandsLevel(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 1, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var entity = helper.getBlockEntity(pad, AbsorptionPadBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 on = helper.absoluteVec(new Vec3(3.5, 1.1, 3.5));
        player.snapTo(on.x, on.y, on.z);
        player.giveExperienceLevels(30_000);
        long before = AbsorptionPadBlockEntity.heldPoints(player);
        helper.succeedWhen(() -> {
            long gone = before - AbsorptionPadBlockEntity.heldPoints(player);
            check(helper, gone > 0, "it drains them");
            check(helper, entity.stored() > 0 && entity.stored() == ExperienceFluids.millibuckets((int) Math.min(gone, AbsorptionPadBlockEntity.BUFFER)),
                    "and stores what it took: took " + gone + " points, holds " + entity.stored() + " mB");
        });
    }

    static void absorptionPadCollectsOrbs(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 2, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var entity = helper.getBlockEntity(pad, AbsorptionPadBlockEntity.class);
        var orb = new ExperienceOrb(helper.getLevel(), 0, 0, 0, 10);
        orb.snapTo(helper.absoluteVec(Vec3.atCenterOf(pad).add(0, 0.5, 0)));
        // still: an orb is made with a random toss, which now and then carried it off the edge of the pad, raised a block
        // over the arena's floor, to the floor below, where no pad would reach it
        orb.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(orb);
        helper.succeedWhen(() -> {
            check(helper, entity.stored() == ExperienceFluids.millibuckets(10),
                    "a 10 point orb becomes 200 mB, got " + entity.stored());
            check(helper, !orb.isAlive(), "and the orb is gone");
        });
    }

    /**
     * A pad on the floor takes an orb that has come to rest on it off its middle, as a mob farm's orbs land: lying on the
     * plate it is further from the pad's middle than an orb is taken from, and is pulled in and up until it is.
     */
    static void absorptionPadTakesAnOrbLyingOnIt(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 1, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var entity = helper.getBlockEntity(pad, AbsorptionPadBlockEntity.class);
        var orb = new ExperienceOrb(helper.getLevel(), 0, 0, 0, 10);
        orb.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(pad).add(0.38, 1 / 16.0, -0.38)));
        orb.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(orb);
        helper.succeedWhen(() -> {
            check(helper, entity.stored() == ExperienceFluids.millibuckets(10), "the orb lying on the pad is taken, got " + entity.stored());
            check(helper, !orb.isAlive(), "and the orb is gone");
        });
    }

    static void absorptionPadTakesMergedOrbs(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 2, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var entity = helper.getBlockEntity(pad, AbsorptionPadBlockEntity.class);
        var orb = new ExperienceOrb(helper.getLevel(), 0, 0, 0, 10);
        // One entity standing for 150 merged orbs of 10 points: 30,000 mB, far more than the pad's 2,000.
        ((ExperienceOrbAccessor) orb).nookworks$setCount(150);
        orb.snapTo(helper.absoluteVec(Vec3.atCenterOf(pad).add(0, 0.5, 0)));
        orb.setDeltaMovement(Vec3.ZERO); // still, as above
        helper.getLevel().addFreshEntity(orb);
        helper.succeedWhen(() -> {
            check(helper, entity.stored() == AbsorptionPadBlockEntity.BUFFER,
                    "the pad fills up, got " + entity.stored());
            check(helper, orb.isAlive() && ((ExperienceOrbAccessor) orb).nookworks$getCount() == 140,
                    "the 140 orbs that did not fit are still there to pick up, count "
                            + ((ExperienceOrbAccessor) orb).nookworks$getCount());
        });
    }

    /**
     * A player on the pad gives up about a level a second, whatever level: a level goes in five drains or a little under
     * (worked through from level 40 to nothing), and the pad drains at that rate - timed by the level's own clock, as a
     * test beside this one may tick the level itself and run it faster than the test's ticks.
     */
    static void absorptionPadDrainsALevelASecond(GameTestHelper helper) {
        Player counted = helper.makeMockPlayer(GameType.SURVIVAL);
        counted.giveExperienceLevels(40);
        int drains = 0;
        while (AbsorptionPadBlockEntity.heldPoints(counted) > 0 && drains < 1_000) {
            counted.giveExperiencePoints(-(int) Math.min(AbsorptionPadBlockEntity.drainRate(counted), AbsorptionPadBlockEntity.heldPoints(counted)));
            drains++;
        }
        check(helper, drains >= 4 * 40 && drains <= AbsorptionPadBlockEntity.DRAINS_PER_LEVEL * 40,
                "forty levels go in four to five drains each, took " + drains);

        BlockPos pad = new BlockPos(1, 2, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        helper.setBlock(pad.east(), ModBlocks.EXPERIENCE_TANK.get());
        var tank = helper.getBlockEntity(pad.east(), ExperienceTankBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.giveExperienceLevels(30);
        player.snapTo(helper.absoluteVec(Vec3.atCenterOf(pad).add(0, 0.1, 0)));
        long from = helper.getLevel().getGameTime();
        helper.runAfterDelay(60, () -> {
            double seconds = (helper.getLevel().getGameTime() - from) / 20.0;
            int lost = 30 - player.experienceLevel;
            // and one more at once: a player just at a level has none of it to give before the one under it; fewer only
            // if the tank filled first (the level run fast)
            boolean full = tank.amount() == ExperienceTankBlockEntity.CAPACITY;
            check(helper, (lost >= seconds - 1 || full) && lost <= seconds + 2,
                    "from level 30, about a level a second: lost " + lost + " in " + seconds + " s");
            helper.succeed();
        });
    }

    static void padPipesIntoTank(GameTestHelper helper) {
        BlockPos pad = new BlockPos(1, 2, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), ModBlocks.EXPERIENCE_PIPE.get());
        }
        BlockPos tankPos = new BlockPos(5, 2, 3);
        helper.setBlock(tankPos, ModBlocks.EXPERIENCE_TANK.get());
        var tank = helper.getBlockEntity(tankPos, ExperienceTankBlockEntity.class);

        // A player sneaking on the pad is what feeds the line; the mock has to be in the level to be found.
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setShiftKeyDown(true);
        player.giveExperiencePoints(60);
        player.snapTo(helper.absoluteVec(Vec3.atCenterOf(pad).add(0, 0.1, 0)));
        check(helper, AbsorptionPadBlockEntity.heldPoints(player) >= 60,
                "the player starts with the experience to give");
        helper.succeedWhen(() -> {
            check(helper, tank.amount() > 0, "experience reaches the tank through the pipes, got " + tank.amount() + " mB");
            check(helper, AbsorptionPadBlockEntity.heldPoints(player) < 60,
                    "and came off the player sneaking on the pad");
        });
    }

    static void experienceTankTalksToOtherMods(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.EXPERIENCE_TANK.get());
        var tank = helper.getBlockEntity(pos, ExperienceTankBlockEntity.class);
        var handler = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(pos), Direction.UP);
        check(helper, handler != null, "the tank is a plain fluid handler, so other mods' pipes can reach it");

        var ours = FluidResource.of(ExperienceFluids.SOURCE.get());
        check(helper, handler.isValid(0, ours), "it takes liquid experience");
        var water = FluidResource.of(Fluids.WATER);
        check(helper, !handler.isValid(0, water), "but not just any fluid");
        check(helper, ExperienceFluids.SOURCE.get().builtInRegistryHolder()
                .is(ExperienceFluids.EXPERIENCE), "and our fluid is in #c:experience");

        tank.fill(1_000);
        check(helper, tank.amount() == 1_000, "a bucket goes in");
        check(helper, ExperienceFluids.points(1_000) == 50, "a bucket is 50 points");
        check(helper, tank.comparatorSignal() > 0, "and a comparator can read it");
        helper.succeed();
    }

    static void liquidExperienceCreditsTheKill(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.snapTo(helper.absoluteVec(Vec3.atCenterOf(pos.east().east())));
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, pos);
        var source = LiquidExperienceBlock.source(helper.getLevel(), pig);
        check(helper, source.getEntity() == player, "the nearest player is named as the cause, so the kill is theirs");

        pig.hurtServer(helper.getLevel(), source, 4.0F);
        check(helper, pig.getLastHurtByPlayer() != null, "vanilla records it as a player hit");
        helper.succeed();
    }

    /** Two boxes facing the same way pair up as a chest does, and come apart when one goes. */
    static void repairBoxesJoin(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 2, 3);
        BlockPos right = new BlockPos(3, 2, 3);
        var type = RepairBoxBlock.TYPE;
        BlockState single = ModBlocks.REPAIR_BOX.get().defaultBlockState().setValue(RepairBoxBlock.FACING, Direction.NORTH);
        helper.setBlock(right, single);
        helper.setBlock(left, single.setValue(type, ChestType.LEFT));
        check(helper, helper.getBlockState(right).getValue(type) == ChestType.RIGHT,
                "the lone box beside a left half becomes its right half");
        var box = helper.getBlockEntity(left, RepairBoxBlockEntity.class);
        var other = helper.getBlockEntity(right, RepairBoxBlockEntity.class);
        var menu = new RepairBoxMenu(1, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(),
                new CompoundContainer(box, other), new SimpleContainerData(RepairBoxMenu.DATA_COUNT),
                new RepairBoxBlockEntity[] {box, other});
        check(helper, menu.slots.size() == 12 + 36, "the pair's screen has twelve gear slots");
        helper.setBlock(left, Blocks.AIR);
        check(helper, helper.getBlockState(right).getValue(type) == ChestType.SINGLE,
                "with its partner gone it is a single box again");
        helper.succeed();
    }

    /** The pair's power is in one half and its experience in the other; the bow in either still gets mended. */
    static void repairBoxPairPools(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 2, 3);
        BlockPos right = new BlockPos(3, 2, 3);
        var type = RepairBoxBlock.TYPE;
        BlockState single = ModBlocks.REPAIR_BOX.get().defaultBlockState().setValue(RepairBoxBlock.FACING, Direction.NORTH);
        helper.setBlock(right, single);
        helper.setBlock(left, single.setValue(type, ChestType.LEFT));
        var box = helper.getBlockEntity(left, RepairBoxBlockEntity.class);
        var other = helper.getBlockEntity(right, RepairBoxBlockEntity.class);
        box.energy().set((int) box.energy().getCapacityAsLong());
        var fluid = FluidResource.of(ExperienceFluids.SOURCE.get());
        try (var transaction = Transaction.openRoot()) {
            other.fluidHandler().insert(fluid, RepairBoxBlockEntity.TANK, transaction);
            transaction.commit();
        }
        ItemStack bow = new ItemStack(Items.BOW);
        bow.setDamageValue(200);
        other.setItem(3, bow);
        // Both halves tick, in the test's own ticks; the right one must leave the work to the left, or the bow is
        // mended twice.
        helper.runAfterDelay(40, () -> {
            int steps = (200 - bow.getDamageValue()) / RepairBoxBlockEntity.REPAIR_PER_STEP;
            check(helper, steps > 0, "the bow in the right half is mended with the left half's power and the right half's experience");
            check(helper, box.energyStored() == (int) box.energy().getCapacityAsLong() - steps * RepairBoxBlockEntity.FE_PER_STEP,
                    "each step was paid for once");
            helper.succeed();
        });
    }

    /**
     * The inside of an open box shows how full it is and the gear lying in it (RepairBoxRenderer), so those go to the
     * client: the fill in twelfths - none when empty, a step for any experience at all, every step to full - and each
     * slot's item without its wear, so a repair is not sent step by step. Nothing else is sent.
     */
    static void repairBoxTellsHowFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.REPAIR_BOX.get());
        var box = helper.getBlockEntity(pos, RepairBoxBlockEntity.class);
        var fluid = FluidResource.of(ExperienceFluids.SOURCE.get());
        check(helper, box.shownStep() == 0, "empty: nothing shown, got " + box.shownStep());
        int[][] pours = {{1, 1}, {RepairBoxBlockEntity.TANK / 2 - 1, 6}, {RepairBoxBlockEntity.TANK / 2, RepairBoxBlockEntity.SHOWN_STEPS}};
        for (int[] pour : pours) {
            try (var transaction = Transaction.openRoot()) {
                box.fluidHandler().insert(fluid, pour[0], transaction);
                transaction.commit();
            }
            check(helper, box.shownStep() == pour[1], box.experience() + " mB shows step " + pour[1] + ", got " + box.shownStep());
        }
        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(120);
        box.setItem(2, worn);
        var shown = box.shownItems();
        check(helper, shown.size() == RepairBoxBlockEntity.SLOTS && shown.get(2).is(Items.IRON_PICKAXE) && !shown.get(2).isDamaged()
                && shown.get(0).isEmpty(), "the gear lying in it is shown, its wear not: " + shown);
        var tag = box.getUpdateTag(helper.getLevel().registryAccess());
        check(helper, tag.getIntOr("Shown", -1) == RepairBoxBlockEntity.SHOWN_STEPS && tag.contains("Items") && tag.size() == 2,
                "the client is sent the fill and the gear alone: " + tag);
        helper.succeed();
    }

    static void repairBoxMendsWithoutMending(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.REPAIR_BOX.get());
        var box = helper.getBlockEntity(pos, RepairBoxBlockEntity.class);
        box.energy().set((int) box.energy().getCapacityAsLong());

        // A plain bow: no Mending, and an anvil would not touch it without materials.
        ItemStack bow = new ItemStack(Items.BOW);
        bow.setDamageValue(200);
        box.setItem(0, bow);
        check(helper, box.canPlaceItem(0, bow), "damaged gear goes in");
        check(helper, !box.canPlaceItem(0, new ItemStack(Items.BOW)), "undamaged gear does not");
        check(helper, !box.canPlaceItem(0, new ItemStack(Items.COBBLESTONE)), "and neither does a block");

        helper.startSequence().thenExecuteAfter(40, () -> {
            check(helper, bow.getDamageValue() == 200, "with no experience it does nothing");
            var fluid = FluidResource.of(ExperienceFluids.SOURCE.get());
            try (var transaction = Transaction.openRoot()) {
                box.fluidHandler().insert(fluid, RepairBoxBlockEntity.TANK, transaction);
                transaction.commit();
            }
        }).thenExecuteAfter(40, () -> {
            check(helper, bow.getDamageValue() < 200, "with experience it mends, got " + bow.getDamageValue());
            check(helper, box.experience() < RepairBoxBlockEntity.TANK, "and spends experience doing it");
            check(helper, box.energyStored() < box.energy().getCapacityAsLong(), "and power");

            check(helper, !box.canTakeItemThroughFace(0, bow, Direction.DOWN), "a hopper cannot take it out half done");
            bow.setDamageValue(0);
            check(helper, box.canTakeItemThroughFace(0, bow, Direction.DOWN), "but can once it is finished");
        }).thenSucceed();
    }

    static void wrenchTurnsBlocks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, Blocks.FURNACE.defaultBlockState()
                .setValue(FurnaceBlock.FACING, Direction.NORTH));
        BlockState turned = WrenchItem.turn(helper.getBlockState(pos), false);
        check(helper, turned.getValue(FurnaceBlock.FACING) == Direction.EAST,
                "a furnace turns clockwise");
        BlockState back = WrenchItem.turn(turned, true);
        check(helper, back.getValue(FurnaceBlock.FACING) == Direction.NORTH,
                "and sneaking turns it back");

        // A log has an axis rather than a facing, and a Y rotation leaves it alone; the wrench steps it anyway.
        helper.setBlock(pos, Blocks.OAK_LOG);
        BlockState log = helper.getBlockState(pos);
        check(helper, WrenchItem.turn(log, false) != log, "a log's axis still turns");
        check(helper, WrenchItem.turn(Blocks.STONE.defaultBlockState(), false)
                == Blocks.STONE.defaultBlockState(), "a block with nothing to turn is left alone");

        // Half of a pair: turning it would leave the other half behind.
        BlockState door = Blocks.OAK_DOOR.defaultBlockState();
        check(helper, WrenchItem.turn(door, false) == door, "a door half is left alone");
        BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState();
        check(helper, WrenchItem.turn(bed, false) == bed, "a bed half is left alone");
        BlockState paired = Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.CHEST_TYPE,
                ChestType.LEFT);
        check(helper, WrenchItem.turn(paired, false) == paired, "half a double chest is left alone");
        check(helper, WrenchItem.turn(Blocks.CHEST.defaultBlockState(), false)
                != Blocks.CHEST.defaultBlockState(), "a single chest still turns");
        BlockState extended = Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.EXTENDED, true);
        check(helper, WrenchItem.turn(extended, false) == extended, "an extended piston is left alone");
        helper.succeed();
    }

    static void trashCanNeedsPowerAndAHopper(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.TRASH_CAN.get());
        var can = helper.getBlockEntity(pos, TrashCanBlockEntity.class);
        ItemStack rubbish = new ItemStack(Items.COBBLESTONE, 64);

        // Unpowered it takes nothing at all, so a hopper queues up instead of the items vanishing.
        check(helper, !can.accepting(), "it starts switched off");
        check(helper, !can.canPlaceItemThroughFace(0, rubbish, Direction.UP), "and a hopper is refused");
        check(helper, can.getMaxStackSize() == 0, "so nothing sees room in it");
        can.setItem(0, rubbish);
        check(helper, can.swallowed() == 0 && rubbish.getCount() == 64, "and nothing is destroyed");

        helper.setBlock(pos.above(), Blocks.REDSTONE_BLOCK);
        check(helper, can.accepting(), "a redstone signal switches it on");
        check(helper, can.canPlaceItemThroughFace(0, rubbish, Direction.NORTH), "and a hopper may feed it");
        can.setItem(0, rubbish);
        check(helper, can.swallowed() == 64 && rubbish.isEmpty(), "what goes in is counted and gone");

        check(helper, !can.canTakeItemThroughFace(0, new ItemStack(Items.DIRT), Direction.DOWN),
                "and nothing can be pulled back out");
        helper.succeed();
    }

    static void buildingWandFillsASurface(GameTestHelper helper) {
        // A 3x3 patch of stone with air above it: one click should cover all nine.
        for (int x = 2; x <= 4; x++) {
            for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos clicked = new BlockPos(3, 1, 3);
        BlockState template = helper.getBlockState(clicked);
        var targets = BuildingWandItem.plan(helper.getLevel(), helper.absolutePos(clicked),
                Direction.UP, template);
        check(helper, targets.size() == 9, "the whole face is covered, got " + targets.size());

        // A block of a different kind is not part of the surface, so the wand stops there.
        helper.setBlock(new BlockPos(4, 1, 3), Blocks.DIRT);
        var smaller = BuildingWandItem.plan(helper.getLevel(), helper.absolutePos(clicked),
                Direction.UP, template);
        check(helper, smaller.size() < 9 && !smaller.contains(helper.absolutePos(new BlockPos(4, 2, 3))),
                "it does not spread over a different block, got " + smaller.size());
        check(helper, BuildingWandItem.MAX_BLOCKS == 32, "and one click stays undoable");
        helper.succeed();
    }

    static void precisionSorterFilters(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.PRECISION_SORTER.get(), false);
        var settings = sorter.settings();
        check(helper, sorter.sorterTier().filterSlots == 18, "it has twice the filter slots");
        check(helper, sorter.sorterTier().configurable, "and the switches");

        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.setDamageValue(100);
        sorter.filter().setItem(0, new ItemStack(Items.IRON_PICKAXE));
        check(helper, sorter.isRegistered(worn), "a damaged tool matches a fresh one by default");
        settings.toggle(SorterSettings.MATCH_COMPONENTS);
        check(helper, !sorter.isRegistered(worn), "unless it is told to be exact");
        settings.toggle(SorterSettings.MATCH_COMPONENTS);

        // Blacklist: the same filter, the other way round.
        check(helper, !sorter.isRegistered(new ItemStack(Items.COBBLESTONE)), "an unlisted item is not registered");
        settings.toggle(SorterSettings.BLACKLIST);
        check(helper, sorter.isRegistered(new ItemStack(Items.COBBLESTONE)), "with the filter inverted it is");
        check(helper, !sorter.isRegistered(new ItemStack(Items.IRON_PICKAXE)), "and the listed item is not");
        settings.toggle(SorterSettings.BLACKLIST);

        // Tags: an entry for minecraft:planks stands for every plank.
        sorter.filter().setItem(0, new ItemStack(Items.OAK_PLANKS));
        check(helper, !sorter.isRegistered(new ItemStack(Items.SPRUCE_PLANKS)), "a different plank is a different item");
        sorter.filter().setItem(0, SorterTags.entry(new ItemStack(Items.OAK_PLANKS), ItemTags.PLANKS));
        check(helper, sorter.isRegistered(new ItemStack(Items.SPRUCE_PLANKS)), "but the planks tag's entry catches it");
        check(helper, !sorter.isRegistered(new ItemStack(Items.COBBLESTONE)), "while cobblestone still does not");
        // An iron ingot's own tag is iron alone: the diamond sharing the beacon's tag with it goes the other way.
        sorter.filter().setItem(0, SorterTags.entry(new ItemStack(Items.IRON_INGOT),
                Tags.Items.INGOTS_IRON));
        check(helper, sorter.isRegistered(new ItemStack(Items.IRON_INGOT)), "c:ingots/iron catches iron");
        check(helper, !sorter.isRegistered(new ItemStack(Items.DIAMOND)) && !sorter.isRegistered(new ItemStack(Items.GOLD_INGOT)),
                "and not diamonds or gold");

        // The extra row is only usable on this tier.
        check(helper, ModBlocks.ITEM_SORTER.get() instanceof SorterBlock plain
                && plain.tier.filterSlots == 9, "the Item Sorter keeps its nine slots");
        helper.succeed();
    }

    static void retortPulpsWoodChips(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var retort = placeMercuryMachine(helper, ModBlocks.RETORT.get(), pos, RetortBlockEntity.class);
        Runnable run = () -> {
            for (int i = 0; i < RetortBlockEntity.TICKS + 5; i++) {
                RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                        helper.getBlockState(pos), retort);
            }
        };

        retort.setItem(RetortBlockEntity.INPUT, new ItemStack(ModItems.WOOD_CHIPS.get(), 4));
        run.run();
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        check(helper, countIn(chest, ModItems.WOOD_PULP.get()) == 0, "chips alone are not pulp");

        int first = RetortBlockEntity.FIRST_REAGENT;
        retort.setItem(first, new ItemStack(ModItems.CAUSTIC_SODA.get(), 4));
        run.run();
        check(helper, countIn(chest, ModItems.WOOD_PULP.get()) == 0, "and caustic soda alone is not white liquor");

        retort.setItem(first + 1, new ItemStack(ModItems.SULFUR_DUST.get(), 4));
        run.run();
        check(helper, countIn(chest, ModItems.WOOD_PULP.get()) == 1, "cooked with both, chips become pulp");
        check(helper, retort.getItem(first).getCount() == 3 && retort.getItem(first + 1).getCount() == 3,
                "one of each reagent was spent");
        check(helper, retort.mercury() == 0, "and pulping frees no mercury");

        check(helper, retort.canPlaceItem(first, new ItemStack(ModItems.CAUSTIC_SODA.get())), "reagents go in the reagent slots");
        check(helper, !retort.canPlaceItem(first, new ItemStack(Items.COBBLESTONE)), "but rubbish does not");
        helper.succeed();
    }

    /** Two copper and one zinc make three brass: copper in the input slot, copper and zinc as reagents. */
    static void retortAlloysBrass(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var retort = placeMercuryMachine(helper, ModBlocks.RETORT.get(), pos, RetortBlockEntity.class);
        int first = RetortBlockEntity.FIRST_REAGENT;
        retort.setItem(RetortBlockEntity.INPUT, new ItemStack(Items.COPPER_INGOT, 2));
        retort.setItem(first, new ItemStack(Items.COPPER_INGOT, 2));
        retort.setItem(first + 1, new ItemStack(ModItems.ZINC_INGOT.get(), 2));
        for (int i = 0; i < RetortBlockEntity.TICKS + 5; i++) {
            RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        check(helper, countIn(chest, ModItems.BRASS_INGOT.get()) == 3, "three brass, got " + countIn(chest, ModItems.BRASS_INGOT.get()));
        check(helper, retort.getItem(RetortBlockEntity.INPUT).getCount() == 1
                && retort.getItem(first).getCount() == 1 && retort.getItem(first + 1).getCount() == 1,
                "two copper and one zinc were spent");
        check(helper, retort.mercury() == 0, "and alloying frees no mercury");
        helper.succeed();
    }

    /** Zinc has no ore of its own; the Sifter is where it comes from. */
    static void sifterFindsZinc(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        int zinc = 0;
        for (int i = 0; i < 400; i++) {
            for (ItemStack found : SifterBlockEntity.sift(helper.getLevel(), helper.absolutePos(pos),
                    new ItemStack(Items.GRAVEL))) {
                if (found.is(ModItems.RAW_ZINC.get())) {
                    zinc += found.getCount();
                }
            }
        }
        // 18 in 1,000 of weight: about 7 in 400, and none at all is a chance of well under 1 in 1,000.
        check(helper, zinc > 0 && zinc < 40, "gravel gives raw zinc now and then, got " + zinc + " in 400");
        helper.succeed();
    }

    static void storageIsABarrel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.IRON_STORAGE.get());
        helper.setBlock(pos.above(), Blocks.STONE);
        BlockState state = helper.getBlockState(pos);
        check(helper, state.hasProperty(StorageBlock.OPEN), "a barrel knows whether it is open");
        check(helper, !state.getValue(StorageBlock.OPEN), "and starts closed");

        // A chest would refuse here; a barrel does not care what is on top of it.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var result = state.useWithoutItem(helper.getLevel(), player, new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false));
        check(helper, result.consumesAction(), "it opens with a block on top");

        // Six ways up, unlike the chest's four.
        var facing = StorageBlock.FACING;
        check(helper, facing.getPossibleValues().size() == 6, "and it points any of six ways");
        check(helper, facing.getPossibleValues().contains(Direction.UP), "including straight up");
        helper.succeed();
    }

    static void laserCostsRealPower(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 3);
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState()
                .setValue(LaserBlock.FACING, Direction.EAST));
        var laser = helper.getBlockEntity(pos, LaserBlockEntity.class);
        int perTick = LaserBlockEntity.FE_PER_TICK;

        // Running the beam is the expensive part, not an afterthought.
        check(helper, perTick > GeneratorBlockEntity.FE_PER_TICK,
                "one generator cannot hold the beam on its own");
        check(helper, perTick > DistillerBlockEntity.FE_PER_TICK,
                "and it draws more than any other machine here");

        // a pig in the beam: something to fire at
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 2, 3));
        laser.energy().set(perTick * 3);
        for (int i = 0; i < 3; i++) {
            LaserBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                    helper.getBlockState(pos), laser);
        }
        check(helper, laser.energy().getAmountAsLong() == 0, "three ticks of beam spend three ticks of power");
        LaserBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), laser);
        check(helper, helper.getBlockState(pos).getValue(LaserBlock.BEAM) > 0,
                "a tick short of power does not blink the beam out (power that comes in fits)");
        for (int i = 0; i < LaserBlockEntity.BEAM_HOLD; i++) {
            LaserBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                    helper.getBlockState(pos), laser);
        }
        check(helper, helper.getBlockState(pos).getValue(LaserBlock.BEAM) == 0,
                "and with none left the beam goes out");

        // Nothing in the way: it stands by on a trickle, the beam off, and fires again when something comes.
        pig.discard();
        laser.energy().set(1_000);
        LaserBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), laser);
        check(helper, laser.energy().getAmountAsLong() == 1_000 - LaserBlockEntity.FE_STANDBY
                && helper.getBlockState(pos).getValue(LaserBlock.BEAM) == 0
                && laser.guiStatus() == MachineStatus.STANDBY, "standing by: " + laser.energy().getAmountAsLong());
        helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 2, 3));
        LaserBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), laser);
        check(helper, helper.getBlockState(pos).getValue(LaserBlock.BEAM) > 0, "a pig comes in: it fires");

        // Felling a tree costs a surge on top, and is refused rather than done for free.
        check(helper, LaserBlockEntity.FE_PER_FELL > perTick, "felling costs more than a tick of beam");
        check(helper, LaserBlockEntity.CAPACITY
                > LaserBlockEntity.FE_PER_FELL, "and the buffer can hold a whole fell");
        helper.succeed();
    }

    static void posterHangsThin(GameTestHelper helper) {
        var id = Nookworks.id("safety_cross");
        var variant = Posters.get(helper.getLevel(), id);
        check(helper, variant != null, "the safety cross poster is loaded from the data pack registry");
        check(helper, variant.width() == 1 && variant.height() == 2, "it is 1x2 blocks");
        check(helper, variant.texture().equals(Nookworks.id("textures/poster/safety_cross.png")), "and its artwork resolves");

        // A 1x2 poster needs two blocks of wall behind it.
        BlockPos wall = new BlockPos(3, 2, 3);
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(wall.above(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = Posters.item(id);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos front = wall.north();
        var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(front)), Direction.NORTH, helper.absolutePos(wall), false);
        var result = stack.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, stack, hit));
        check(helper, result.consumesAction(), "it goes onto the wall");

        var posters = helper.getLevel().getEntities(EntityTypeTest.forClass(PosterEntity.class),
                new AABB(helper.absolutePos(front)).inflate(3), e -> true);
        check(helper, posters.size() == 1, "exactly one poster entity exists");
        var poster = posters.getFirst();
        check(helper, id.equals(poster.variantId()), "carrying the design it was crafted with");
        var box = poster.getBoundingBox();
        check(helper, box.getYsize() > 1.9 && box.getXsize() > 0.9, "it is 1x2 blocks of wall");
        check(helper, box.getZsize() <= PosterEntity.DEPTH + 1.0E-4,
                "and paper thin, not a painting's board: " + box.getZsize());

        // A poster with no design is just a blank sheet and must not place.
        ItemStack blank = new ItemStack(ModItems.POSTER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, blank);
        var blankHit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(front)), Direction.SOUTH, helper.absolutePos(wall), false);
        check(helper, !blank.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, blank, blankHit))
                .consumesAction(), "a blank poster cannot be placed");
        helper.succeed();
    }

    static void velariumOnlyFromDistilling(GameTestHelper helper) {
        var ingots = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "ingots"));
        var nuggets = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", "nuggets"));
        check(helper, new ItemStack(ModItems.VELARIUM_INGOT.get()).is(ingots), "velarium ingot is in #c:ingots");
        check(helper, new ItemStack(ModItems.VELARIUM_NUGGET.get()).is(nuggets), "velarium nugget is in #c:nuggets");
        // Velarium has no ore block and no worldgen: the nugget can only fall out of the distiller.
        check(helper, BuiltInRegistries.BLOCK.getOptional(Nookworks.id("velarium_ore")).isEmpty(),
                "there is no velarium ore block");
        var recipe = MercuryRecipes.distillation(new ItemStack(ModItems.TRACE_AMALGAM.get()));
        check(helper, recipe != null && recipe.velariumChance() > 0 && recipe.velariumChance() <= 0.002F,
                "distilling mixed amalgam gives a nugget 0.2% of the time at most");
        check(helper, MercuryRecipes.distillation(new ItemStack(ModItems.GOLD_AMALGAM.get())).count()
                > MercuryRecipes.distillation(new ItemStack(ModItems.IRON_AMALGAM.get())).count(),
                "gold amalgamates best, iron badly: barely more than smelting it");
        helper.succeed();
    }

    static void velariumGearBeatsNetherite(GameTestHelper helper) {
        ItemStack sword = new ItemStack(ModItems.VELARIUM_SWORD.get());
        ItemStack chest = new ItemStack(ModItems.VELARIUM_CHESTPLATE.get());
        check(helper, sword.getMaxDamage() > new ItemStack(Items.NETHERITE_SWORD).getMaxDamage(), "the sword outlasts netherite");
        check(helper, VelariumGear.TOOL.attackDamageBonus() > ToolMaterial.NETHERITE.attackDamageBonus(),
                "and hits harder");
        check(helper, chest.getMaxDamage() > new ItemStack(Items.NETHERITE_CHESTPLATE).getMaxDamage(), "the chestplate outlasts netherite");
        check(helper, sword.is(ItemTags.SWORDS) && new ItemStack(ModItems.VELARIUM_PICKAXE.get()).is(ItemTags.PICKAXES),
                "the tools are in the vanilla tool tags, so vanilla behaviour and enchanting work");
        check(helper, chest.is(ItemTags.CHEST_ARMOR), "and the armour is in the armour tags");
        check(helper, new ItemStack(ModItems.VELARIUM_HAMMER.get()).is(Hammers.TAG), "the hammer is a hammer");
        helper.succeed();
    }

    private MercuryTests() {}
}
