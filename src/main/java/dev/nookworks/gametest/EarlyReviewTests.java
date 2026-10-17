package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.SifterTests.SIFTER;
import static dev.nookworks.gametest.StorageTests.SORTER_POS;
import static dev.nookworks.gametest.StorageTests.placeSorter;
import static dev.nookworks.gametest.StorageTests.placeStorage;
import static dev.nookworks.gametest.StorageTests.storageMenu;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import dev.nookworks.block.EnergyCableBlock;
import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.block.NestedTimerBlock;
import dev.nookworks.block.ThinRopeBlock;
import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.block.entity.TrashCanBlockEntity;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.GrillBlockEntity;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.NukadokoBlockEntity;
import dev.nookworks.cooking.PicklingRecipe;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.crop.RicePaniclesBlock;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.experience.AbsorptionPadBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.feature.FoodPoisoning;
import dev.nookworks.feature.Lubrication;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.WaterPumpBlockEntity;
import dev.nookworks.furniture.HangingPlanterBlock;
import dev.nookworks.furniture.TrailingPlantBlock;
import dev.nookworks.inventory.InventoryTweaks;
import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.machine.Drive;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MotorBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.oil.OilPressBlockEntity;
import dev.nookworks.oil.PressingRecipe;
import dev.nookworks.registry.GlazedPots;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageInventory;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageSpill;
import dev.nookworks.storage.StorageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** What the reviews of September 26, 2026 found, each held to the right behaviour. */
final class EarlyReviewTests {
    static void register() {
        test("experience_moves_lose_nothing", EarlyReviewTests::experienceMovesLoseNothing);
        test("energy_network_keeps_every_side", EarlyReviewTests::energyNetworkKeepsEverySide);
        test("storage_trusts_no_saved_count", EarlyReviewTests::storageTrustsNoSavedCount);
        test("trash_can_remembers_its_count", EarlyReviewTests::trashCanRemembersItsCount);
        test("soil_spends_only_itself", EarlyReviewTests::soilSpendsOnlyItself);
        test("storage_swaps_give_one_stack", EarlyReviewTests::storageSwapsGiveOneStack);
        test("building_wand_stays_bounded", EarlyReviewTests::buildingWandStaysBounded);
        test("mob_bottle_empties_an_allay", EarlyReviewTests::mobBottleEmptiesAnAllay);
        test("explosion_takes_only_what_it_breaks", EarlyReviewTests::explosionTakesOnlyWhatItBreaks);
        test("cable_sends_nothing_back", EarlyReviewTests::cableSendsNothingBack);
        test("sifter_leaves_unsiftable_inputs", EarlyReviewTests::sifterLeavesUnsiftableInputs);
        test("wheel_leaves_ghost_filters_alone", EarlyReviewTests::wheelLeavesGhostFiltersAlone);
        test("trash_can_counts_only_committed_items", EarlyReviewTests::trashCanCountsOnlyCommittedItems);
        test("warp_never_delivers_into_itself", EarlyReviewTests::warpNeverDeliversIntoItself);
        test("barrels_and_wand_refuse_lookalikes", EarlyReviewTests::barrelsAndWandRefuseLookalikes);
        test("generator_wastes_no_fuel", EarlyReviewTests::generatorWastesNoFuel);
        test("retort_keeps_a_room_for_each_reagent", EarlyReviewTests::retortKeepsARoomForEachReagent);
        test("retort_keeps_experience", EarlyReviewTests::retortKeepsExperience);
        test("glazed_pot_holds_plants", EarlyReviewTests::glazedPotHoldsPlants);
        test("storage_sort_gathers_each_kind", EarlyReviewTests::storageSortGathersEachKind);
        test("hanging_planter_hangs", EarlyReviewTests::hangingPlanterHangs);
        test("water_pump_feeds_the_retort", 300, EarlyReviewTests::waterPumpFeedsTheRetort);
        test("water_pump_drains_a_lone_source", EarlyReviewTests::waterPumpDrainsALoneSource);
        test("copper_pipe_carries_oil_or_water", EarlyReviewTests::copperPipeCarriesOilOrWater);
        test("oil_press_by_hand", EarlyReviewTests::oilPressByHand);
        test("motor_turns_the_oil_press", 300, EarlyReviewTests::motorTurnsTheOilPress);
        test("two_motors_turn_a_press_no_faster_than_one", 200, EarlyReviewTests::twoMotorsTurnNoFasterThanOne);
        test("oil_burns_in_a_generator", EarlyReviewTests::oilBurnsInAGenerator);
        test("lubricated_door_is_silent", EarlyReviewTests::lubricatedDoorIsSilent);
        test("iron_pot_fries_in_oil", 400, EarlyReviewTests::ironPotFriesInOil);
        test("rice_polisher_makes_white_rice_and_bran", EarlyReviewTests::ricePolisherMakesWhiteRiceAndBran);
        test("nukadoko_pickles_carrots", EarlyReviewTests::nukadokoPicklesCarrots);
        test("soap_washes_out_dye_and_oil", EarlyReviewTests::soapWashesOutDyeAndOil);
        test("raw_chicken_makes_you_ill_later", EarlyReviewTests::rawChickenMakesYouIllLater);
        test("raw_meat_and_fish_risk_by_kind", EarlyReviewTests::rawFoodRisks);
        test("wood_to_methanol_to_biodiesel", EarlyReviewTests::woodToMethanolToBiodiesel);
        test("stirring_tank_makes_biodiesel_for_a_generator", 400, EarlyReviewTests::stirringTankMakesBiodieselForAGenerator);
        test("rice_grows_in_water_and_heads_up", EarlyReviewTests::riceGrowsInWaterAndHeadsUp);
        test("nested_timer_pulses_and_holds", 120, EarlyReviewTests::redstoneTimerPulsesAndHolds);
        test("dispenser_throws_dice", EarlyReviewTests::dispenserThrowsDice);
        test("dice_from_an_upward_dispenser_roll_alike_everywhere", EarlyReviewTests::diceFromAnUpwardDispenser);
        test("dice_from_a_dispenser_keep_out_of_water", EarlyReviewTests::diceFromADispenserKeepOutOfWater);
        test("dice_from_before_0_5_get_their_block_entity", EarlyReviewTests::oldDiceGetTheirBlockEntity);
        test("iron_pot_boils_rice_over_a_campfire", 400, EarlyReviewTests::ironPotBoilsRiceOverACampfire);
        test("grill_cooks_and_keeps_the_food", 700, EarlyReviewTests::grillCooksAndKeepsTheFood);
        test("grill_over_a_dead_fire_stays_cold", 200, EarlyReviewTests::grillOverADeadFireStaysCold);
        test("nested_timer_pours_and_takes_back_water", 120, EarlyReviewTests::redstoneTimerPoursAndTakesBackWater);
        test("hanging_planter_grows_down", EarlyReviewTests::hangingPlanterGrowsDown);
        test("thin_rope_hangs_with_a_fitting", EarlyReviewTests::thinRopeHangsWithAFitting);
    }

    // ---- Review fixes (2026-09-26) ----

    /**
     * Fluid moves are all or nothing. A bucket only empties into the tank when the whole bucket fits, and a move
     * from one handler to another never takes more than the other side accepts - the Repair Box used to take
     * first and lose whatever the box then refused.
     */
    static void experienceMovesLoseNothing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.EXPERIENCE_TANK.get());
        var tank = helper.getBlockEntity(pos, ExperienceTankBlockEntity.class);
        tank.fill(ExperienceTankBlockEntity.CAPACITY - 500);
        int before = tank.amount();
        check(helper, !tank.fillBucket(), "a bucket does not go into 500 mB of room");
        check(helper, tank.amount() == before, "and none of it went in, so the bucket can stay full");

        BlockPos padPos = new BlockPos(1, 1, 1);
        helper.setBlock(padPos, ModBlocks.ABSORPTION_PAD.get());
        var pad = helper.getBlockEntity(padPos, AbsorptionPadBlockEntity.class);
        var ours = FluidResource.of(ExperienceFluids.SOURCE.get());
        int moved = Move.transfer(tank.fluidHandler(), pad.fluidHandler(), ours, 1_000);
        check(helper, moved == 0 && tank.amount() == before,
                "a handler that refuses (the pad takes nothing in) leaves the source untouched, moved " + moved);
        helper.succeed();
    }

    /** A cable that touches a machine on two faces reaches both, so a machine that takes power on one face only still gets it. */
    static void energyNetworkKeepsEverySide(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(2, 1, 3), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(2, 1, 2), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 2), ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.CRUSHER.get());
        var receivers = MachineIO.receivers(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 3)));
        var crusher = receivers.stream().filter(r -> r.pos().equals(helper.absolutePos(new BlockPos(3, 1, 3)))).toList();
        check(helper, crusher.size() == 1, "the crusher is one receiver, not counted twice, got " + crusher.size());
        check(helper, crusher.get(0).sides().containsAll(List.of(Direction.WEST, Direction.NORTH)),
                "and both faces the cable touches are kept, got " + crusher.get(0).sides());
        check(helper, MachineIO.handlerOf(helper.getLevel(), crusher.get(0)) != null, "and it can be fed");
        helper.succeed();
    }

    /** Saved barrel contents are held to what the tier can hold, and a spill in progress is saved with the level. */
    static void storageTrustsNoSavedCount(GameTestHelper helper) {
        var inventory = new StorageInventory(StorageTier.IRON, () -> {});
        inventory.load(new StorageContents(List.of(new StorageContents.Entry(0, new ItemStack(Items.COBBLESTONE), Integer.MAX_VALUE))));
        // to the most a slot of the tier could ever hold - not today's stack size of the item, which a pack can lower
        check(helper, inventory.getAmountAsInt(0) == StorageTier.IRON.slotCapacity(Item.ABSOLUTE_MAX_STACK_SIZE),
                "a count past the tier's capacity is cut to it, got " + inventory.getAmountAsInt(0));

        var level = helper.getLevel();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < 200; i++) {
            stacks.add(new ItemStack(Items.COBBLESTONE, 64));
        }
        StorageSpill.spill(level, helper.absolutePos(new BlockPos(3, 2, 3)), stacks);
        int waiting = StorageSpill.pending(level);
        check(helper, waiting > 0, "some of the pile waits for later ticks");
        var ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var data = level.getDataStorage().get(StorageSpill.Pending.TYPE);
        check(helper, data != null && data.isDirty(), "what is waiting is marked to be saved");
        var saved = StorageSpill.Pending.CODEC.encodeStart(ops, data).getOrThrow();
        var loaded = StorageSpill.Pending.CODEC.parse(ops, saved).getOrThrow();
        check(helper, loaded.stacks() == waiting, "and comes back whole, " + loaded.stacks() + " of " + waiting);
        // Two barrels broken in the same tick share one tick's budget, rather than dropping 64 stacks each.
        BlockPos a = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos b = helper.absolutePos(new BlockPos(5, 2, 5));
        List<ItemStack> hundred = Collections.nCopies(100, new ItemStack(Items.COBBLESTONE, 64)).stream()
                .map(ItemStack::copy).toList();
        StorageSpill.spill(level, a, hundred);
        StorageSpill.spill(level, b, hundred.stream().map(ItemStack::copy).toList());
        int left = StorageSpill.pending(level, a) + StorageSpill.pending(level, b);
        check(helper, left >= 200 - StorageSpill.PER_TICK, "two barrels share one tick's 64, " + left + " left of 200");
        BlockPos pile = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.succeedWhen(() -> check(helper, StorageSpill.pending(level, pile) == 0
                && StorageSpill.pending(level, a) == 0 && StorageSpill.pending(level, b) == 0,
                "the rest follows"));
    }

    /** The Trash Can's running count survives a reload. */
    static void trashCanRemembersItsCount(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 1, 3);
        BlockPos second = new BlockPos(4, 1, 3);
        helper.setBlock(first, ModBlocks.TRASH_CAN.get());
        helper.setBlock(second, ModBlocks.TRASH_CAN.get());
        var can = helper.getBlockEntity(first, TrashCanBlockEntity.class);
        can.swallow(new ItemStack(Items.DIRT, 40));
        var registries = helper.getLevel().registryAccess();
        var copy = helper.getBlockEntity(second, TrashCanBlockEntity.class);
        copy.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING, registries, can.saveWithoutMetadata(registries)));
        check(helper, copy.swallowed() == 40, "the count is saved, got " + copy.swallowed());
        helper.succeed();
    }

    // ---- Second review (2026-09-26) ----

    /** A soil that has become something else (a tree grew and turned it to dirt) is simply not spent. */
    static void soilSpendsOnlyItself(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Blocks.DIRT);
        FertileSoilBlock.spend(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos));
        check(helper, helper.getBlockState(pos).is(Blocks.DIRT), "the dirt stays dirt, and nothing threw");
        helper.succeed();
    }

    /** No way out of a barrel slot hands a player more than one normal stack. */
    static void storageSwapsGiveOneStack(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.NETHERITE_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.DIAMOND), 4096);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        StorageMenu menu = storageMenu(player, storage);

        menu.clicked(0, 0, ContainerInput.SWAP, player); // the number key 1 over a slot of 4096
        check(helper, player.getInventory().getItem(0).getCount() == 64, "the hotbar gets one stack, got "
                + player.getInventory().getItem(0).getCount());
        check(helper, storage.inventory().getAmountAsInt(0) == 4096 - 64, "and the rest stays in the barrel");

        menu.clicked(0, 40, ContainerInput.SWAP, player); // F: the off-hand
        check(helper, player.getInventory().getItem(40).getCount() == 64, "the off-hand too");

        player.getInventory().setItem(1, new ItemStack(Items.COBBLESTONE, 10));
        menu.clicked(0, 1, ContainerInput.SWAP, player); // swapping with something else would pour out thousands
        check(helper, player.getInventory().getItem(1).is(Items.COBBLESTONE), "a swap with another item is refused");

        menu.setCarried(new ItemStack(Items.STICK, 3));
        menu.clicked(0, 0, ContainerInput.PICKUP, player);
        check(helper, menu.getCarried().is(Items.STICK) && storage.inventory().getAmountAsInt(0) == 4096 - 128,
                "and so is swapping the cursor's different item for the whole slot");
        helper.succeed();
    }

    /** The wand's search is bounded by itself, and never puts a block inside a mob. */
    static void buildingWandStaysBounded(GameTestHelper helper) {
        var level = helper.getLevel();
        // A floor with stone over it: nowhere to build, which used to send the search across the whole surface.
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        BlockPos clicked = helper.absolutePos(new BlockPos(3, 1, 3));
        var targets = BuildingWandItem.plan(level, clicked, Direction.UP,
                level.getBlockState(clicked));
        check(helper, targets.isEmpty(), "a covered floor has nowhere to build");

        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                helper.setBlock(new BlockPos(x, 2, z), Blocks.AIR);
            }
        }
        var planned = BuildingWandItem.plan(level, clicked, Direction.UP, level.getBlockState(clicked));
        check(helper, planned.stream().allMatch(p -> p.distChessboard(clicked.above()) <= BuildingWandItem.MAX_REACH),
                "and nothing it plans is further than its reach");

        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 2, 3));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(1, new ItemStack(Items.STONE, 64));
        ItemStack wand = new ItemStack(ModItems.BUILDING_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        wand.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).is(Blocks.STONE),
                "the wand builds where nothing stands");
        check(helper, helper.getBlockState(new BlockPos(4, 2, 3)).isAir(), "but not inside the pig");
        pig.discard();
        helper.succeed();
    }

    /** A bottled allay keeps what it had collected: nothing drops, and it comes back out still holding it. */
    static void mobBottleEmptiesAnAllay(GameTestHelper helper) {
        var allay = helper.spawnWithNoFreeWill(EntityTypes.ALLAY, new BlockPos(3, 2, 3));
        allay.getInventory().addItem(new ItemStack(Items.EMERALD, 7));
        allay.setHealth(allay.getMaxHealth() / 4);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        check(helper, MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, allay).consumesAction(), "the allay is caught");
        helper.assertItemEntityCountIs(Items.EMERALD, new BlockPos(3, 2, 3), 3.0, 0);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(
                Vec3.atCenterOf(helper.absolutePos(new BlockPos(3, 0, 3))), Direction.UP, helper.absolutePos(new BlockPos(3, 0, 3)), false)));
        var allays = helper.getEntities(EntityTypes.ALLAY);
        check(helper, allays.size() == 1 && allays.get(0).getInventory().countItem(Items.EMERALD) == 7,
                "it comes back out still holding its seven emeralds");
        helper.succeed();
    }

    /** An explosion that breaks nothing leaves a block's coat on; one that takes the block takes the coat. */
    static void explosionTakesOnlyWhatItBreaks(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos kept = new BlockPos(2, 1, 3);
        BlockPos blown = new BlockPos(5, 1, 3);
        helper.setBlock(kept, Blocks.OAK_PLANKS);
        helper.setBlock(blown, Blocks.OAK_PLANKS);
        Fireproofing.coat(level, helper.absolutePos(kept));
        Fireproofing.coat(level, helper.absolutePos(blown));
        Vec3 atKept = helper.absoluteVec(Vec3.atCenterOf(kept).add(0, 0.6, 0));
        level.explode(null, atKept.x, atKept.y, atKept.z, 1.5F, Level.ExplosionInteraction.NONE);
        Vec3 atBlown = helper.absoluteVec(Vec3.atCenterOf(blown));
        level.explode(null, atBlown.x, atBlown.y, atBlown.z, 2.0F, Level.ExplosionInteraction.TNT);
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(kept).is(Blocks.OAK_PLANKS), "the harmless blast left the plank");
            check(helper, Fireproofing.isCoated(level, helper.absolutePos(kept)), "and its coat");
            check(helper, !helper.getBlockState(blown).is(Blocks.OAK_PLANKS), "the other plank went");
            check(helper, !Fireproofing.isCoated(level, helper.absolutePos(blown)), "and its coat with it");
        });
    }

    /** A cable never hands energy back to the machine it came from. */
    static void cableSendsNothingBack(GameTestHelper helper) {
        BlockPos cable = new BlockPos(3, 1, 3);
        helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(cable.west(), ModBlocks.CRUSHER.get());
        var fromCrusher = EnergyCableBlock.networkHandler(helper.getLevel(), helper.absolutePos(cable), Direction.WEST);
        check(helper, fromCrusher.getCapacityAsLong() == 0, "the only machine on the cable is the sender: no room to send to");
        var fromElsewhere = EnergyCableBlock.networkHandler(helper.getLevel(), helper.absolutePos(cable), Direction.EAST);
        check(helper, fromElsewhere.getCapacityAsLong() > 0, "from any other side the crusher is a receiver");
        helper.succeed();
    }

    /** The sifter leaves an input alone once it can no longer be sifted, instead of eating it. */
    static void sifterLeavesUnsiftableInputs(GameTestHelper helper) {
        helper.setBlock(SIFTER, ModBlocks.SIFTER.get());
        SifterBlockEntity sifter = helper.getBlockEntity(SIFTER, SifterBlockEntity.class);
        sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(Items.OAK_PLANKS, 5)); // as a /reload that dropped its table would leave it
        for (int i = 0; i < 3; i++) {
            SifterBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(SIFTER), helper.getBlockState(SIFTER), sifter);
        }
        check(helper, sifter.getItem(SifterBlockEntity.INPUT).getCount() == 5, "nothing was eaten");
        helper.succeed();
    }

    /** The scroll-wheel moves never treat a Sorter's ghost filter as a real item. */
    static void wheelLeavesGhostFiltersAlone(GameTestHelper helper) {
        SorterBlockEntity sorter = placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 20));
        sorter.filter().setItem(0, new ItemStack(Items.DIAMOND));
        SorterMenu menu = new SorterMenu(1, player.getInventory(), sorter.filter(), sorter.gearSlot(), helper.absolutePos(SORTER_POS),
                sorter.sorterTier(), sorter.settings());
        check(helper, InventoryTweaks.pushOne(menu, player, menu.getSlot(0)).isEmpty(), "a filter is not pushed out");
        check(helper, InventoryTweaks.pullOne(menu, player, menu.getSlot(0)).isEmpty(), "nor filled from the inventory");
        helper.succeed();
    }

    /** The Trash Can counts only what a transaction really gave it. */
    static void trashCanCountsOnlyCommittedItems(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.TRASH_CAN.get());
        helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK);
        var can = helper.getBlockEntity(pos, TrashCanBlockEntity.class);
        check(helper, can.accepting(), "a powered can is on");
        var handler = can.itemHandler();
        var dirt = ItemResource.of(Items.DIRT);
        try (var tx = Transaction.openRoot()) {
            handler.insert(dirt, 30, tx); // a simulated move: never committed
        }
        check(helper, can.swallowed() == 0, "a rolled-back insert destroyed nothing, counted " + can.swallowed());
        try (var tx = Transaction.openRoot()) {
            handler.insert(dirt, 12, tx);
            tx.commit();
        }
        check(helper, can.swallowed() == 12, "a committed one counts, got " + can.swallowed());
        // and it is heard taking it: its first time was "just heard" by an overflow, so it never was (r13)
        check(helper, can.heardAt() == helper.getLevel().getGameTime(), "it knocked and sizzled as it took them");
        helper.succeed();
    }

    // ---- Self-review (2026-09-26) ----

    /** A receiver sitting on its own sender is not a place to deliver to: the items stay, rather than vanish. */
    static void warpNeverDeliversIntoItself(GameTestHelper helper) {
        BlockPos sender = new BlockPos(3, 1, 3);
        helper.setBlock(sender, ModBlocks.ITEM_WARP_SENDER.get());
        helper.setBlock(sender.above(), ModBlocks.ITEM_WARP_RECEIVER.get());
        var entity = helper.getBlockEntity(sender, ItemWarpSenderBlockEntity.class);
        entity.setItem(0, new ItemStack(Items.COBBLESTONE, 10));
        for (int i = 0; i < 5; i++) {
            ItemWarpSenderBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(sender), helper.getBlockState(sender), entity);
        }
        check(helper, entity.getItem(0).getCount() == 10, "all 10 are still there, found " + entity.getItem(0).getCount());
        helper.succeed();
    }

    /**
     * No bundles in barrels, nor shulker boxes - though a Crate goes in, full or not - and the wand copies only blocks
     * its item really places. A barrel saved with a shulker box in it, from before they were refused, keeps it.
     */
    static void barrelsAndWandRefuseLookalikes(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        check(helper, !storage.inventory().isValid(0, ItemResource.of(Items.BUNDLE)), "a bundle does not go into a barrel");
        check(helper, !storage.inventory().isValid(0, ItemResource.of(Items.SHULKER_BOX))
                && !storage.inventory().isValid(0, ItemResource.of(Items.DYED_SHULKER_BOX.pick(DyeColor.RED))), "nor a shulker box");
        ItemStack fullCrate = new ItemStack(ModItems.CRATE.get());
        fullCrate.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.DIAMOND, 64))));
        check(helper, storage.inventory().isValid(0, ItemResource.of(ModItems.CRATE.get())) && storage.inventory().isValid(0, ItemResource.of(fullCrate)),
                "but a crate does, empty or full");
        try (Transaction transaction = Transaction.openRoot()) {
            check(helper, storage.inventory().insert(ItemResource.of(Items.SHULKER_BOX), 1, transaction) == 0, "a hopper cannot put one in");
        }
        storage.inventory().set(3, ItemResource.of(Items.SHULKER_BOX), 2);
        var saved = storage.inventory().toContents();
        storage.inventory().load(saved);
        check(helper, storage.inventory().getResource(3).is(Items.SHULKER_BOX) && storage.inventory().getAmountAsInt(3) == 2,
                "two shulker boxes saved in a barrel from before are still there");
        check(helper, !BuildingWandItem.canCopy(Blocks.LAVA_CAULDRON.defaultBlockState()),
                "the wand does not copy a lava cauldron (its item is a plain cauldron)");
        check(helper, BuildingWandItem.canCopy(Blocks.STONE.defaultBlockState()),
                "but copies stone as ever");
        helper.succeed();
    }

    /** A generator with a small steady load burns only what it can store. */
    static void generatorWastesNoFuel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        GeneratorBlockEntity gen = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        gen.energy().set(GeneratorBlockEntity.CAPACITY - 20);
        gen.setItem(0, new ItemStack(Items.COAL));
        GeneratorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), gen);
        check(helper, gen.getItem(0).getCount() == 1, "with no room for a tick's output it does not light a coal");
        check(helper, gen.energyStored() == GeneratorBlockEntity.CAPACITY - 20, "and nothing was burnt and thrown away");
        helper.succeed();
    }

    /** One kind of reagent to a Retort slot: a hopper of copper leaves a slot for the zinc. */
    static void retortKeepsARoomForEachReagent(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        retort.setItem(0, new ItemStack(Items.COPPER_INGOT, 64));
        int first = RetortBlockEntity.FIRST_REAGENT;
        check(helper, retort.canPlaceItem(first, new ItemStack(Items.COPPER_INGOT)), "copper may take one reagent slot");
        retort.setItem(first, new ItemStack(Items.COPPER_INGOT, 64));
        check(helper, !retort.canPlaceItem(first + 1, new ItemStack(Items.COPPER_INGOT)), "but not the other as well");
        check(helper, retort.canPlaceItem(first + 1, new ItemStack(ModItems.ZINC_INGOT.get())), "which is left for the zinc");
        helper.succeed();
    }

    /** A Retort's charge leaves experience behind, which goes into the tank beside it; nothing can be poured back in. */
    static void retortKeepsExperience(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        helper.setBlock(pos.east(), ModBlocks.EXPERIENCE_TANK.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        var tank = helper.getBlockEntity(pos.east(), ExperienceTankBlockEntity.class);
        retort.energy().set((int) retort.energy().getCapacityAsLong());
        retort.setItem(0, new ItemStack(Items.CINNABAR));
        for (int i = 0; i < RetortBlockEntity.TICKS + 5; i++) {
            RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        int kept = retort.experienceStored() + tank.amount();
        check(helper, kept == 14, "one cinnabar leaves 0.7 point, 14 mB, found " + kept);
        check(helper, tank.amount() > 0, "and it goes into the tank beside it");
        var ours = FluidResource.of(ExperienceFluids.SOURCE.get());
        check(helper, Move.insert(retort.experienceHandler(), ours, 100) == 0,
                "nothing can be poured into the Retort's experience");
        helper.succeed();
    }

    /** The glazed pot takes a plant and gives it back as a vanilla pot does, and has a twin for every vanilla one. */
    static void glazedPotHoldsPlants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, GlazedPots.EMPTY.get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DANDELION));
        BlockPos abs = helper.absolutePos(pos);
        var hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        helper.getBlockState(pos).useItemOn(player.getMainHandItem(), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        var planted = helper.getBlockState(pos).getBlock();
        check(helper, planted instanceof FlowerPotBlock pot && pot.getPotted() == Blocks.DANDELION
                && pot.getEmptyPot() == GlazedPots.EMPTY.get(), "a dandelion goes into the glazed pot, got " + planted);
        check(helper, player.getMainHandItem().isEmpty(), "and leaves the hand");
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        check(helper, helper.getBlockState(pos).is(GlazedPots.EMPTY.get()), "an empty hand takes it out again");
        long vanilla = BuiltInRegistries.BLOCK.stream()
                .filter(b -> b instanceof FlowerPotBlock p && p.getEmptyPot() == Blocks.FLOWER_POT
                        && p.getPotted() != Blocks.AIR && BuiltInRegistries.BLOCK.getKey(b).getNamespace().equals("minecraft"))
                .count();
        check(helper, GlazedPots.FULL.size() == vanilla + 1, "a glazed twin for every vanilla pot, and the Dawnwood sapling");
        helper.succeed();
    }

    /** Sorting gathers each kind into as few slots as it fits in, keeps differently-named items apart, loses nothing. */
    static void storageSortGathersEachKind(GameTestHelper helper) {
        StorageBlockEntity storage = placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        var inv = storage.inventory();
        ItemStack named = new ItemStack(Items.DIRT);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special"));
        inv.set(9, ItemResource.of(Items.DIRT), 30);
        inv.set(5, ItemResource.of(Items.STONE), 20);
        inv.set(0, ItemResource.of(Items.DIRT), 10);
        inv.set(3, ItemResource.of(named), 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        storageMenu(player, storage).clickMenuButton(player, StorageMenu.BUTTON_SORT);
        int used = 0;
        int dirt = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getAmountAsInt(i) > 0) {
                check(helper, i == used, "the sorted slots come first, with no gaps, slot " + i);
                used++;
                if (inv.getResource(i).is(Items.DIRT) && inv.getResource(i).toStack(1).get(DataComponents.CUSTOM_NAME) == null) {
                    dirt += inv.getAmountAsInt(i);
                    check(helper, inv.getAmountAsInt(i) == 40, "both lots of dirt are one slot of 40");
                }
            }
        }
        check(helper, used == 3, "dirt, the named dirt and stone: three slots, got " + used);
        check(helper, dirt == 40, "and nothing was lost");
        helper.succeed();
    }

    /** A hanging planter needs something above it, and falls when that goes. */
    static void hangingPlanterHangs(GameTestHelper helper) {
        BlockPos support = new BlockPos(3, 4, 3);
        BlockPos pos = support.below();
        var planter = GlazedPots.HANGING_GREEN.get().defaultBlockState();
        check(helper, !planter.canSurvive(helper.getLevel(), helper.absolutePos(pos)), "nothing above: it cannot hang");
        helper.setBlock(support, Blocks.OAK_PLANKS);
        check(helper, planter.canSurvive(helper.getLevel(), helper.absolutePos(pos)), "under planks it hangs");
        helper.setBlock(pos, planter);
        helper.setBlock(support, Blocks.AIR);
        check(helper, helper.getBlockState(pos).isAir(), "and it falls when they go");
        helper.succeed();
    }

    /** The plant grows down a block at a time, 3 at most, stops at a block in its way, and goes with its planter. */
    static void hangingPlanterGrowsDown(GameTestHelper helper) {
        var level = helper.getLevel();
        var trail = GlazedPots.TRAIL_GREEN.get();
        BlockPos planter = new BlockPos(1, 6, 1);
        helper.setBlock(planter.above(), Blocks.OAK_PLANKS);
        helper.setBlock(planter, GlazedPots.HANGING_GREEN.get());
        check(helper, BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, helper.absolutePos(planter)),
                "bone meal grows the plant");
        helper.assertBlockPresent(trail, planter.below());
        for (int i = 0; i < 5; i++) {
            HangingPlanterBlock.grow(level, helper.absolutePos(planter));
        }
        for (int i = 1; i <= 3; i++) {
            helper.assertBlockPresent(trail, planter.below(i));
        }
        helper.assertBlockNotPresent(trail, planter.below(4));
        check(helper, helper.getBlockState(planter.below(3)).getValue(TrailingPlantBlock.TIP)
                && !helper.getBlockState(planter.below(1)).getValue(TrailingPlantBlock.TIP), "only the lowest is the tip");
        check(helper, !BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, helper.absolutePos(planter.below(2))),
                "full grown: bone meal does nothing");

        BlockPos blocked = new BlockPos(3, 6, 3);
        helper.setBlock(blocked.above(), Blocks.OAK_PLANKS);
        helper.setBlock(blocked, GlazedPots.HANGING_PINK.get());
        helper.setBlock(blocked.below(2), Blocks.STONE);
        for (int i = 0; i < 3; i++) {
            HangingPlanterBlock.grow(level, helper.absolutePos(blocked));
        }
        helper.assertBlockPresent(GlazedPots.TRAIL_PINK.get(), blocked.below());
        helper.assertBlockPresent(Blocks.STONE, blocked.below(2));

        helper.setBlock(planter.above(), Blocks.AIR);
        for (int i = 0; i <= 3; i++) {
            check(helper, helper.getBlockState(planter.below(i)).isAir(), "the plant goes with its planter");
        }
        helper.succeed();
    }

    /** Thin rope hangs from a block with its fitting on the top length only, holds a planter, and is not a ladder. */
    static void thinRopeHangsWithAFitting(GameTestHelper helper) {
        var level = helper.getLevel();
        var rope = ModBlocks.THIN_ROPE.get();
        BlockPos top = new BlockPos(2, 5, 2);
        helper.setBlock(top.above(), Blocks.OAK_PLANKS);
        check(helper, !rope.defaultBlockState().canSurvive(level, helper.absolutePos(top.above(2))), "nothing above: it cannot hang");
        helper.setBlock(top, rope);
        helper.setBlock(top.below(), rope);
        helper.setBlock(top, Block.updateFromNeighbourShapes(rope.defaultBlockState(), level, helper.absolutePos(top)));
        helper.setBlock(top.below(), Block.updateFromNeighbourShapes(rope.defaultBlockState(), level, helper.absolutePos(top.below())));
        check(helper, helper.getBlockState(top).getValue(ThinRopeBlock.ANCHORED), "the top length has the fitting");
        check(helper, !helper.getBlockState(top.below()).getValue(ThinRopeBlock.ANCHORED), "the one below does not");
        check(helper, !helper.getBlockState(top).is(BlockTags.CLIMBABLE), "it cannot be climbed");
        var planter = GlazedPots.HANGING_GREEN.get().defaultBlockState();
        check(helper, planter.canSurvive(level, helper.absolutePos(top.below(2))), "a planter hangs from it");
        helper.succeed();
    }

    /** Pump on a pool, pipe to a Retort: the water is boiled to crude salt, and the pool never runs out. */
    static void waterPumpFeedsTheRetort(GameTestHelper helper) {
        for (int x = 0; x <= 2; x++) { // a 3x1 pool walled in stone: its middle source has two source neighbours
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, 2, 1), Blocks.STONE);
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        helper.setBlock(new BlockPos(0, 2, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 2, 2), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 2, 2), Blocks.WATER);
        BlockPos pumpPos = new BlockPos(1, 3, 2);
        helper.setBlock(pumpPos, ModBlocks.WATER_PUMP.get());
        var pump = helper.getBlockEntity(pumpPos, WaterPumpBlockEntity.class);
        pump.energy().set((int) pump.energy().getCapacityAsLong());
        helper.setBlock(pumpPos.east(), ModBlocks.COPPER_PIPE.get());
        helper.setBlock(pumpPos.east(2), ModBlocks.COPPER_PIPE.get());
        BlockPos retortPos = pumpPos.east(3);
        helper.setBlock(retortPos.below(), Blocks.CHEST);
        helper.setBlock(retortPos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(retortPos, RetortBlockEntity.class);
        retort.energy().set((int) retort.energy().getCapacityAsLong());
        // Placed after its neighbours, the pipe joins the pump and the Retort, but not an Experience Pipe.
        helper.setBlock(pumpPos.east().north(), ModBlocks.EXPERIENCE_PIPE.get());
        helper.setBlock(pumpPos.east(2), Blocks.AIR);
        helper.setBlock(pumpPos.east(2), ModBlocks.COPPER_PIPE.get().getStateForPlacement(
                new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.COPPER_PIPE.get()), new BlockHitResult(
                                Vec3.atCenterOf(helper.absolutePos(pumpPos.east(2))), Direction.UP,
                                helper.absolutePos(pumpPos.east(2)), false))));
        var pipe = helper.getBlockState(pumpPos.east(2));
        check(helper, pipe.getValue(PipeBlock.WEST)
                && pipe.getValue(PipeBlock.EAST), "the pipe joins the pipe before it and the Retort");
        check(helper, !helper.getBlockState(pumpPos.east()).getValue(PipeBlock.NORTH),
                "a water pipe does not join an experience pipe");
        helper.succeedWhen(() -> {
            var chest = helper.getBlockEntity(retortPos.below(), ChestBlockEntity.class);
            check(helper, chest.countItem(ModItems.CRUDE_SALT.get()) >= 2, "the piped water boiled down to crude salt");
            check(helper, chest.countItem(Items.BUCKET) == 0, "with no bucket left over");
            check(helper, retort.guiTanks().get(1).capacity() == RetortBlockEntity.WATER_TANK
                    && retort.guiTanks().get(1).amount() == retort.waterStored(), "the screen's water tank reads the Retort's");
            helper.assertBlockPresent(Blocks.WATER, new BlockPos(1, 2, 2));
        });
    }

    /** A source on its own is pumped dry, as a bucket would take it. */
    static void waterPumpDrainsALoneSource(GameTestHelper helper) {
        BlockPos water = new BlockPos(2, 2, 2);
        for (Direction side : Direction.values()) {
            if (side != Direction.UP) {
                helper.setBlock(water.relative(side), Blocks.STONE);
            }
        }
        helper.setBlock(water, Blocks.WATER);
        BlockPos pumpPos = water.above();
        helper.setBlock(pumpPos, ModBlocks.WATER_PUMP.get());
        var pump = helper.getBlockEntity(pumpPos, WaterPumpBlockEntity.class);
        pump.energy().set((int) pump.energy().getCapacityAsLong());
        for (int i = 0; i < WaterPumpBlockEntity.TICKS; i++) {
            WaterPumpBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pumpPos), helper.getBlockState(pumpPos), pump);
        }
        check(helper, pump.stored() == WaterPumpBlockEntity.BUCKET, "a bucket was pumped");
        check(helper, helper.getBlockState(water).isAir(), "and the lone source is gone");
        check(helper, helper.getLevel().getCapability(Capabilities.Fluid.BLOCK,
                helper.absolutePos(pumpPos), Direction.UP) != null, "other mods' pipes can draw from it");
        helper.succeed();
    }

    /** Rice stands in water, heads up into the block above, ripens there and is cut without losing the plant. */
    static void riceGrowsInWaterAndHeadsUp(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos soil = new BlockPos(2, 1, 2);
        BlockPos plant = soil.above();
        helper.setBlock(soil, Blocks.DIRT);
        helper.setBlock(plant, Blocks.WATER);
        var rice = ModCrops.RICE_CROP.get().defaultBlockState();
        check(helper, rice.canSurvive(level, helper.absolutePos(plant)), "rice can stand in water on dirt");
        check(helper, !rice.canSurvive(level, helper.absolutePos(new BlockPos(4, 4, 4))), "but not on nothing");
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.MUD);
        check(helper, rice.canSurvive(level, helper.absolutePos(new BlockPos(4, 2, 4))), "and on mud, as in a paddy");
        helper.setBlock(plant, rice);
        check(helper, helper.getBlockState(plant).getFluidState().isSource(), "and it keeps its water");
        for (int i = 0; i < 12; i++) {
            BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, helper.absolutePos(plant));
        }
        helper.assertBlockPresent(ModCrops.RICE_PANICLES.get(), plant.above());
        check(helper, helper.getBlockState(plant.above()).getValue(RicePaniclesBlock.AGE) == 3, "the heads ripen");
        level.destroyBlock(helper.absolutePos(plant.above()), true);
        helper.assertBlockPresent(ModCrops.RICE_CROP.get(), plant);
        helper.assertItemEntityPresent(ModCrops.BROWN_RICE.get(), plant.above(), 2.0);
        check(helper, helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(plant.above())).inflate(2),
                e -> e.getItem().is(ModCrops.BROWN_RICE.get())).stream().mapToInt(e -> e.getItem().getCount()).sum() >= 2,
                "ripe heads give two rice or more");
        check(helper, BoneMealItem.growCrop(new ItemStack(Items.BONE_MEAL), level, helper.absolutePos(plant))
                && helper.getBlockState(plant.above()).is(ModCrops.RICE_PANICLES.get()), "the plant heads up again");
        level.destroyBlock(helper.absolutePos(plant), false);
        helper.assertBlockPresent(Blocks.WATER, plant);
        check(helper, helper.getBlockState(plant.above()).isAir(), "and without it the heads go too");
        check(helper, ModCrops.preferred(new ItemStack(ModCrops.BROWN_RICE.get()))
                .is(ModCrops.BROWN_RICE.get()), "without Farmer's Delight the Sifter gives our rice");
        helper.succeed();
    }

    /** A timer pulses its front on its interval, and a signal into its side holds it. */
    static void redstoneTimerPulsesAndHolds(GameTestHelper helper) {
        var timer = ModBlocks.NESTED_TIMER.get();
        BlockPos free = new BlockPos(1, 2, 1);
        BlockPos held = new BlockPos(4, 2, 1);
        BlockPos twicePos = new BlockPos(1, 2, 4); // inside the arena (x 7 is past its wall)
        List<BlockPos> timers = List.of(free, held, twicePos);
        for (BlockPos pos : timers) {
            helper.setBlock(pos.below(), Blocks.STONE);
            helper.setBlock(pos, timer.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        }
        helper.getBlockEntity(free, NestedTimerBlockEntity.class).setInterval(10);
        helper.getBlockEntity(held, NestedTimerBlockEntity.class).setInterval(10);
        var twice = helper.getBlockEntity(twicePos, NestedTimerBlockEntity.class);
        twice.setInterval(40);
        twice.setPulses(2);
        twice.setGap(10);
        check(helper, helper.getBlockState(free).getValue(NestedTimerBlock.INTERVAL) == 0
                && helper.getBlockState(twicePos).getValue(NestedTimerBlock.INTERVAL) == 2, "the meter follows the interval");
        twice.setInterval(1_000_000);
        check(helper, twice.interval() == 72_000, "settings snap to their steps: an hour at most");
        twice.setInterval(40);
        helper.setBlock(held.east(), Blocks.REDSTONE_BLOCK);
        int[] pulses = new int[3];
        boolean[] was = new boolean[3];
        helper.onEachTick(() -> {
            for (int i = 0; i < 3; i++) {
                boolean on = helper.getLevel().getSignal(helper.absolutePos(timers.get(i)), Direction.SOUTH) > 0; // what it sends out of its front
                if (on && !was[i]) {
                    pulses[i]++;
                }
                was[i] = on;
            }
        });
        helper.runAfterDelay(100, () -> {
            check(helper, pulses[0] >= 8, "every half second, the front gets a pulse: " + pulses[0]);
            check(helper, pulses[1] == 0, "held from the side, it stays quiet: " + pulses[1]);
            check(helper, pulses[2] >= 4 && pulses[2] <= 6, "every two seconds, twice: " + pulses[2]);
            helper.succeed();
        });
    }

    /** The farm the timer is for: a dispenser pours its water bucket on the first pulse and takes it back on the second. */
    static void redstoneTimerPoursAndTakesBackWater(GameTestHelper helper) {
        BlockPos timerPos = new BlockPos(1, 2, 2);
        BlockPos dispenser = timerPos.east();
        helper.setBlock(timerPos.below(), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 1, 2), Blocks.STONE);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(Items.WATER_BUCKET));
        helper.setBlock(timerPos, ModBlocks.NESTED_TIMER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.WEST));
        var timer = helper.getBlockEntity(timerPos, NestedTimerBlockEntity.class);
        timer.setInterval(60);
        timer.setPulses(2);
        timer.setGap(20);
        helper.runAfterDelay(70, () -> helper.assertBlockPresent(Blocks.WATER, dispenser.east()));
        helper.runAfterDelay(95, () -> {
            check(helper, !helper.getBlockState(dispenser.east()).is(Blocks.WATER), "the second pulse takes the water back");
            check(helper, helper.getBlockEntity(dispenser, DispenserBlockEntity.class)
                    .countItem(Items.WATER_BUCKET) == 1, "into the bucket, ready for the next round");
            helper.succeed();
        });
    }

    /** A dispenser throws a die: it lands in front, on the ground, and rolls. */
    static void dispenserThrowsDice(GameTestHelper helper) {
        BlockPos dispenser = new BlockPos(1, 3, 2);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class)
                .setItem(0, new ItemStack(ModItems.DICE.get()));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
        helper.setBlock(dispenser.above(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.DICE.get(), new BlockPos(2, 2, 2));
            check(helper, helper.getBlockEntity(dispenser, DispenserBlockEntity.class).isEmpty(),
                    "the die left the dispenser");
        });
    }

    /**
     * A die out of a dispenser facing up is tossed up where it lands, and its roll read back from what is saved and
     * sent to clients is the same roll - the number shown is the number a comparator reads, on every client. (A throw
     * up or down was worked out on the server as a throw from that side, which a client, reading no side for up or
     * down, worked out as a toss: the die showed one number and the comparator read another.)
     */
    static void diceFromAnUpwardDispenser(GameTestHelper helper) {
        BlockPos dispenser = new BlockPos(2, 1, 2);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.UP));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(ModItems.DICE.get()));
        helper.setBlock(dispenser.east(), Blocks.REDSTONE_BLOCK);
        BlockPos spot = dispenser.above();
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(ModBlocks.DICE.get(), spot);
            var level = helper.getLevel();
            var die = helper.getBlockEntity(spot, dev.nookworks.block.entity.DiceBlockEntity.class);
            var roll = die.tumble();
            check(helper, roll != null, "it was thrown");
            try (var reporter = new net.minecraft.util.ProblemReporter.ScopedCollector(die.problemPath(), com.mojang.logging.LogUtils.getLogger())) {
                var output = net.minecraft.world.level.storage.TagValueOutput.createWithContext(reporter, level.registryAccess());
                die.saveWithoutMetadata(output);
                var copy = new dev.nookworks.block.entity.DiceBlockEntity(die.getBlockPos(), die.getBlockState());
                copy.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
                var read = copy.tumble();
                check(helper, read != null && read.value() == roll.value() && java.util.Arrays.equals(read.rest(), roll.rest()),
                        "read back, it is the same roll: " + roll.value() + " and " + (read == null ? null : read.value()));
            }
        });
    }

    /**
     * A die thrown from a dispenser into a pond does not take the place of its water (a source lost, a die lying in
     * water): it comes out as an item, as anything with nowhere to land does. Thrown over the pond's edge onto a stone,
     * it lands there.
     */
    static void diceFromADispenserKeepOutOfWater(GameTestHelper helper) {
        BlockPos dispenser = new BlockPos(1, 2, 2);
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(ModItems.DICE.get()));
        BlockPos pond = dispenser.east();
        helper.setBlock(pond.below(), Blocks.STONE);
        helper.setBlock(pond, Blocks.WATER);
        helper.setBlock(dispenser.north(), Blocks.REDSTONE_BLOCK);
        helper.succeedWhen(() -> {
            helper.assertBlockPresent(Blocks.WATER, pond);
            helper.assertItemEntityPresent(ModItems.DICE.get());
        });
    }

    /**
     * A die set down before 0.5.0 has no block entity, and a die is drawn from its block entity now: found so in a chunk
     * loaded, it is given one (lying in the middle of its block, as before), so it is not invisible until clicked.
     */
    static void oldDiceGetTheirBlockEntity(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos die = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(die, ModBlocks.DICE.get().defaultBlockState().setValue(dev.nookworks.block.DiceBlock.VALUE, 4));
        level.removeBlockEntity(die); // as a 0.4.0 world has it
        var chunk = level.getChunkAt(die);
        check(helper, chunk.getBlockEntity(die, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) == null, "a bare die");
        var bare = dev.nookworks.block.DiceUpgrade.upgrade(chunk); // as a chunk read from disk does
        check(helper, bare.contains(die), "found bare in its chunk: " + bare);
        var entity = chunk.getBlockEntity(die, net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        check(helper, entity instanceof dev.nookworks.block.entity.DiceBlockEntity, "given its block entity: " + entity);
        check(helper, !dev.nookworks.block.DiceUpgrade.bareDice(chunk).contains(die), "and no longer bare");
        helper.succeed();
    }

    /** Rice and water in a pot over a campfire cook to bowls of rice; the same pot on cold stone does nothing. */
    static void ironPotBoilsRiceOverACampfire(GameTestHelper helper) {
        BlockPos hot = new BlockPos(1, 2, 1);
        BlockPos cold = new BlockPos(4, 2, 1);
        helper.setBlock(hot.below(), Blocks.CAMPFIRE); // the pot on its grill, in the grill's place
        helper.setBlock(cold.below(), Blocks.CAMPFIRE); // a campfire alone does not heat a pot: it needs the grill
        var rice = new ItemStack(ModCrops.WHITE_RICE.get());
        for (BlockPos pos : List.of(hot, cold)) {
            helper.setBlock(pos, pos == hot ? ModCooking.IRON_POT.get().onGrill(Direction.NORTH)
                    : ModCooking.IRON_POT.get().defaultBlockState());
            var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
            check(helper, pot.addIngredient(rice) && pot.addIngredient(rice), "rice goes in");
            check(helper, pot.addLiquid(PotRecipe.Liquid.WATER, 500), "and water");
            check(helper, !pot.addLiquid(PotRecipe.Liquid.OIL, 250), "and then no oil");
        }
        check(helper, helper.getBlockState(hot).getValue(IronPotBlock.ON_GRILL), "on a grill it sits on the grate");
        check(helper, helper.getBlockState(hot).getValue(IronPotBlock.LIQUID) == IronPotBlock.Contents.WATER,
                "and shows its water");
        check(helper, PotRecipe.isIngredient(helper.getLevel(), rice)
                && !PotRecipe.isIngredient(helper.getLevel(), new ItemStack(Items.STONE)),
                "only what a recipe takes goes in");
        List<Component> said = new ArrayList<>();
        helper.getBlockEntity(cold, IronPotBlockEntity.class).readout(said::add);
        check(helper, said.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents key
                && key.getKey().equals("nookworks.iron_pot.needs_grill")), "set straight on a campfire, it says it needs a grill: " + said);
        helper.succeedWhen(() -> {
            var pot = helper.getBlockEntity(hot, IronPotBlockEntity.class);
            check(helper, pot.dish().is(ModCooking.COOKED_RICE.get()) && pot.dish().getCount() == 2 && pot.container() == Items.BOWL,
                    "two batches: two bowls of rice");
            check(helper, pot.amount() == 0 && pot.count() == 0, "the rice and the water are used up");
            check(helper, helper.getBlockState(hot).getValue(IronPotBlock.LIQUID) == IronPotBlock.Contents.EMPTY,
                    "and the pot is dry");
            check(helper, helper.getBlockEntity(cold, IronPotBlockEntity.class).dish().isEmpty(), "no heat, no rice");
            ItemStack served = pot.serve();
            check(helper, served.is(ModCooking.COOKED_RICE.get()) && pot.dish().getCount() == 1, "a bowl is served");
        });
    }

    /** A grill over a lit campfire cooks what a campfire cooks, keeps it on the grate done, and gives it back. */
    static void grillCooksAndKeepsTheFood(GameTestHelper helper) {
        BlockPos grillPos = new BlockPos(2, 2, 2);
        helper.setBlock(grillPos.below(), Blocks.CAMPFIRE);
        helper.setBlock(grillPos, ModCooking.GRILL.get().defaultBlockState().setValue(GrillBlock.OVER_FIRE, true));
        helper.setBlock(new BlockPos(5, 2, 5), ModCooking.GRILL.get());
        check(helper, helper.getBlockState(grillPos).getValue(GrillBlock.OVER_FIRE)
                && !helper.getBlockState(new BlockPos(5, 2, 5)).getValue(GrillBlock.OVER_FIRE),
                "a grill stands astride a campfire, or anywhere else on its own legs");
        var grill = helper.getBlockEntity(grillPos, GrillBlockEntity.class);
        check(helper, grill.place(helper.getLevel(), null, new ItemStack(Items.BEEF, 2)), "raw beef goes on");
        check(helper, !grill.place(helper.getLevel(), null, new ItemStack(Items.STONE)), "stone does not");
        helper.succeedWhen(() -> {
            check(helper, grill.items().get(0).is(Items.COOKED_BEEF) && grill.done(0), "the beef cooks and stays on the grate");
            ItemStack off = grill.take();
            check(helper, off.is(Items.COOKED_BEEF), "and comes off cooked");
        });
    }

    /**
     * Over a put-out campfire the grill does not cook; take the campfire away and the grill stands up on its own legs,
     * still holding what was on it, and drops back down over a new fire.
     */
    static void grillOverADeadFireStaysCold(GameTestHelper helper) {
        BlockPos grillPos = new BlockPos(2, 2, 2);
        helper.setBlock(grillPos.below(), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, false));
        helper.setBlock(grillPos, ModCooking.GRILL.get().defaultBlockState()
                .setValue(GrillBlock.OVER_FIRE, true));
        var grill = helper.getBlockEntity(grillPos, GrillBlockEntity.class);
        check(helper, grill.place(helper.getLevel(), null, new ItemStack(Items.BEEF)), "beef goes on a cold grill too");
        check(helper, !GrillBlock.hot(helper.getLevel(), helper.absolutePos(grillPos)), "a dead fire is not hot");
        helper.runAfterDelay(100, () -> {
            check(helper, grill.items().get(0).is(Items.BEEF) && !grill.done(0), "the beef is still raw");
            helper.destroyBlock(grillPos.below());
            check(helper, !helper.getBlockState(grillPos).getValue(GrillBlock.OVER_FIRE),
                    "the grill stands up on its own");
            check(helper, grill.items().get(0).is(Items.BEEF), "with the beef still on it");
            helper.setBlock(grillPos.below(), Blocks.CAMPFIRE);
            check(helper, helper.getBlockState(grillPos).getValue(GrillBlock.OVER_FIRE), "a new fire under it, and down it goes");
            helper.succeed();
        });
    }

    /**
     * A Copper Pipe carries vegetable oil as well as water, one at a time; spilt, the oil burns.
     */
    static void copperPipeCarriesOilOrWater(GameTestHelper helper) {
        BlockPos first = new BlockPos(1, 2, 1);
        helper.setBlock(first, ModBlocks.COPPER_PIPE.get());
        helper.setBlock(first.east(), ModBlocks.COPPER_PIPE.get());
        var pipe = helper.getBlockEntity(first, CopperPipeBlockEntity.class);
        var handler = pipe.handlerFor(null);
        var oil = FluidResource.of(OilFluids.SOURCE.get());
        var water = FluidResource.of(Fluids.WATER);
        var lava = FluidResource.of(Fluids.LAVA);
        try (var tx = Transaction.openRoot()) {
            check(helper, handler.insert(lava, 100, tx) == 0, "no lava");
            check(helper, handler.insert(oil, 500, tx) == 500, "oil goes in");
            check(helper, handler.insert(water, 100, tx) == 0, "and water does not while it holds oil");
            tx.commit();
        }
        BlockPos pool = new BlockPos(4, 2, 1);
        helper.setBlock(pool, ModOil.VEGETABLE_OIL.get());
        check(helper, helper.getLevel().getFluidState(helper.absolutePos(pool)).getType() == OilFluids.SOURCE.get(),
                "placed oil is an oil source");
        check(helper, helper.getBlockState(pool).getFlammability(helper.getLevel(), helper.absolutePos(pool), Direction.UP) > 0,
                "spilt oil burns");
        helper.succeedWhen(() -> {
            var next = helper.getBlockEntity(first.east(), CopperPipeBlockEntity.class);
            check(helper, next.stored() == 250 && pipe.stored() == 250, "the oil evens out along the pipe");
        });
    }

    /** Two turns of the handle press a sunflower seed's oil; now and then a cake drops; the oil pours off by the bottle. */
    static void oilPressByHand(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModOil.OIL_PRESS.get());
        var press = helper.getBlockEntity(pos, OilPressBlockEntity.class);
        check(helper, !press.canPlaceItem(OilPressBlockEntity.INPUT, new ItemStack(Items.WHEAT_SEEDS)),
                "wheat seeds give no oil");
        check(helper, !press.wantsTurn(), "an empty press has nothing to turn for");
        check(helper, !press.canPlaceItem(OilPressBlockEntity.INPUT, new ItemStack(Items.SUNFLOWER)),
                "nor the sunflower itself: its seeds are pressed");
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        press.turn(helper.getLevel());
        check(helper, press.stored() == 0 && press.turns() == 1, "one turn is not yet enough");
        press.turn(helper.getLevel());
        check(helper, press.stored() == 60 && press.getItem(OilPressBlockEntity.INPUT).getCount() == 63,
                "the second presses out a seed's oil");
        // a cake one seed in four: sixty more seeds without one would be one press in some thirty million
        while (press.getItem(OilPressBlockEntity.CAKE).isEmpty() && !press.getItem(OilPressBlockEntity.INPUT).isEmpty()) {
            press.turn(helper.getLevel());
        }
        check(helper, press.getItem(OilPressBlockEntity.CAKE).is(ModOil.OIL_CAKE.get()), "now and then a cake");
        while (press.stored() < OilFluids.BOTTLE) {
            press.turn(helper.getLevel());
        }
        int before = press.stored();
        check(helper, press.pour(OilFluids.BOTTLE) && press.stored() == before - OilFluids.BOTTLE, "the oil fills a bottle");
        helper.succeed();
    }

    /** A powered Motor set against the press turns it until the seeds are gone, then stops drawing power. */
    static void motorTurnsTheOilPress(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(2, 2, 1);
        BlockPos motorPos = pressPos.west();
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get());
        helper.setBlock(motorPos, ModOil.MOTOR.get().defaultBlockState()
                .setValue(MotorBlock.FACING, Direction.EAST));
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        var motor = helper.getBlockEntity(motorPos, MotorBlockEntity.class);
        check(helper, helper.getBlockState(pressPos).getValue(Drive.PROPERTY) == Drive.WEST,
                "the press shows an axle out to the motor on its west side");
        motor.energy().set((int) motor.energy().getCapacityAsLong());
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(Items.PUMPKIN_SEEDS, 4));
        int[] left = new int[1];
        helper.startSequence().thenWaitUntil(() -> {
            check(helper, press.stored() == 200 && press.getItem(OilPressBlockEntity.INPUT).isEmpty(),
                    "four pumpkin seeds pressed to 200 mB");
            check(helper, !helper.getBlockState(motorPos).getValue(MotorBlock.POWERED), "the motor stops");
        }).thenExecute(() -> left[0] = motor.energyStored()).thenExecuteAfter(40, () -> {
            // read again two seconds on, not twice in the same tick
            check(helper, motor.energyStored() == left[0] && left[0] > 0,
                    "and stops drawing power: " + left[0] + " FE then, " + motor.energyStored() + " now");
        }).thenSucceed();
    }

    /**
     * Two Motors set against one press turn it no faster than one: one turns it, the other stands by and spends
     * nothing, and takes over when the first runs out of power (the user, 2026-10-01: doubling made no sense).
     */
    static void twoMotorsTurnNoFasterThanOne(GameTestHelper helper) {
        BlockPos pressPos = new BlockPos(2, 2, 1);
        helper.setBlock(pressPos, ModOil.OIL_PRESS.get());
        helper.setBlock(pressPos.west(), ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.EAST));
        helper.setBlock(pressPos.east(), ModOil.MOTOR.get().defaultBlockState().setValue(MotorBlock.FACING, Direction.WEST));
        var press = helper.getBlockEntity(pressPos, OilPressBlockEntity.class);
        List<MotorBlockEntity> motors = List.of(helper.getBlockEntity(pressPos.west(), MotorBlockEntity.class),
                helper.getBlockEntity(pressPos.east(), MotorBlockEntity.class));
        int full = (int) motors.get(0).energy().getCapacityAsLong();
        motors.forEach(motor -> motor.energy().set(full));
        press.setItem(OilPressBlockEntity.INPUT, new ItemStack(ModOil.SUNFLOWER_SEEDS.get(), 64));
        helper.runAfterDelay(40, () -> {
            int turns = press.stored() / 60 * 2 + press.turns();
            check(helper, turns >= 3 && turns <= 5, "forty ticks are four turns, as for one motor, not " + turns);
            List<MotorBlockEntity> spent = motors.stream().filter(m -> m.energyStored() < full).toList();
            check(helper, spent.size() == 1, "one motor spends power, not " + spent.size());
            MotorBlockEntity other = motors.get(spent.get(0) == motors.get(0) ? 1 : 0);
            check(helper, other.guiStatus() == MachineStatus.ANOTHER_MOTOR, "the other stands by: " + other.guiStatus());
            spent.get(0).energy().set(0);
            helper.runAfterDelay(5, () -> {
                check(helper, other.energyStored() < full && other.guiStatus() == MachineStatus.RUNNING,
                        "with the first out of power, the other takes over: " + other.guiStatus());
                helper.succeed();
            });
        });
    }

    /** A bucket of oil is fuel, ten coal's worth - as biodiesel's, the same energy - and the Generator hands the bucket back. */
    static void oilBurnsInAGenerator(GameTestHelper helper) {
        ItemStack bucket = new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get());
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        check(helper, generator.burnTime(helper.getLevel(), bucket) == 16_000, "an oil bucket burns for 16000 ticks");
        generator.setItem(0, bucket);
        helper.succeedWhen(() -> {
            check(helper, generator.getItem(0).is(Items.BUCKET), "the empty bucket is left in the slot");
            check(helper, generator.energyStored() > 0, "and the oil burns into energy");
        });
    }

    /** A lubricated door (both halves) plays no sound; an untreated one, or stone, still does. */
    static void lubricatedDoorIsSilent(GameTestHelper helper) {
        BlockPos door = new BlockPos(1, 2, 1);
        BlockPos plain = new BlockPos(3, 2, 1);
        for (BlockPos pos : List.of(door, plain)) {
            helper.setBlock(pos, Blocks.OAK_DOOR.defaultBlockState());
            helper.setBlock(pos.above(), Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        helper.setBlock(new BlockPos(5, 2, 1), Blocks.STONE);
        var level = helper.getLevel();
        check(helper, level.getBlockState(helper.absolutePos(door)).is(Lubrication.LUBRICATABLE)
                && !Blocks.STONE.defaultBlockState().is(Lubrication.LUBRICATABLE), "doors take lubricant, stone does not");
        Lubrication.lubricate(level, helper.absolutePos(door));
        Lubrication.lubricate(level, helper.absolutePos(door.above()));
        Lubrication.lubricate(level, helper.absolutePos(new BlockPos(5, 2, 1)));
        Predicate<BlockPos> silent = pos -> {
            var event = new PlayLevelSoundEvent.AtPosition(level,
                    Vec3.atCenterOf(helper.absolutePos(pos)),
                    Holder.direct(SoundEvents.WOODEN_DOOR_OPEN), SoundSource.BLOCKS, 1.0F, 1.0F);
            return NeoForge.EVENT_BUS.post(event).isCanceled();
        };
        check(helper, silent.test(door) && silent.test(door.above()), "the lubricated door is silent, top and bottom");
        check(helper, !silent.test(plain), "the plain door still creaks");
        check(helper, !silent.test(new BlockPos(5, 2, 1)), "a mark on anything else silences nothing");
        var breaking = new PlayLevelSoundEvent.AtPosition(level,
                Vec3.atCenterOf(helper.absolutePos(door)),
                Holder.direct(SoundEvents.WOOD_BREAK), SoundSource.BLOCKS, 1.0F, 1.0F);
        check(helper, !NeoForge.EVENT_BUS.post(breaking).isCanceled(), "oil quiets the hinge, not the wood: it still breaks with a crack");
        helper.succeed();
    }

    /** Potatoes and salt in hot oil fry to French fries, and the oil mostly stays; rice and an egg in oil make fried rice. */
    static void ironPotFriesInOil(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(pos, ModCooking.IRON_POT.get());
        var pot = helper.getBlockEntity(pos, IronPotBlockEntity.class);
        var access = helper.getLevel();
        check(helper, PotRecipe.isIngredient(access, new ItemStack(Items.CHICKEN)), "chicken goes in");
        check(helper, helper.getBlockState(pos).getValue(IronPotBlock.LIQUID) == IronPotBlock.Contents.EMPTY,
                "a new pot is empty");
        check(helper, pot.addLiquid(PotRecipe.Liquid.OIL, 1_000), "a bucket of oil goes in");
        check(helper, helper.getBlockState(pos).getValue(IronPotBlock.LIQUID) == IronPotBlock.Contents.OIL,
                "and shows the oil");
        var fluids = pot.liquidHandler();
        try (var tx = Transaction.openRoot()) {
            check(helper, fluids.insert(FluidResource.of(Fluids.WATER), 100, tx) == 0,
                    "water does not");
        }
        for (int i = 0; i < 2; i++) {
            pot.addIngredient(new ItemStack(Items.POTATO));
        }
        pot.addIngredient(new ItemStack(ModItems.SALT.get()));
        helper.succeedWhen(() -> {
            check(helper, pot.dish().is(ModCooking.FRENCH_FRIES.get()) && pot.dish().getCount() == 2,
                    "two potatoes and salt fry to two French fries");
            check(helper, pot.amount() == 950 && pot.count() == 0 && pot.container() == null, "a little oil is used, the rest stays");
            check(helper, pot.takeDish().getCount() == 2 && pot.dish().isEmpty(), "taken out all at once");
            var fried = PotRecipe.find(access, List.of(new ItemStack(ModCooking.COOKED_RICE.get()),
                    new ItemStack(Items.EGG)), PotRecipe.Liquid.OIL, 950);
            check(helper, fried != null && fried.value().result().item().value() == ModCooking.FRIED_RICE.get() && fried.value().container().isPresent(),
                    "rice and an egg in oil make fried rice, in a bowl");
            check(helper, new ItemStack(ModCooking.COOKED_RICE.get()).getCraftingRemainder() != null,
                    "and the rice comes in a bowl, which the pot keeps for it");
        });
    }

    /**
     * Three turns polish a grain of brown rice white, into the hopper under the polisher; every fourth grain leaves a
     * bran, which presses to oil and feeds chickens.
     */
    static void ricePolisherMakesWhiteRiceAndBran(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 3, 1);
        helper.setBlock(pos.below(), Blocks.HOPPER);
        helper.setBlock(pos, ModCrops.RICE_POLISHER.get());
        var polisher = helper.getBlockEntity(pos, RicePolisherBlockEntity.class);
        check(helper, !polisher.canPlaceItem(0, new ItemStack(ModCrops.WHITE_RICE.get())), "white rice is done already");
        polisher.setItem(0, new ItemStack(ModCrops.BROWN_RICE.get(), 4));
        for (int turn = 0; turn < 3 * 4; turn++) {
            polisher.turn(helper.getLevel());
        }
        var hopper = helper.getBlockEntity(pos.below(), HopperBlockEntity.class);
        check(helper, polisher.getItem(0).isEmpty() && !polisher.wantsTurn(), "four grains polished, and nothing left to turn for");
        check(helper, hopper.countItem(ModCrops.WHITE_RICE.get()) == 4, "four white rice in the hopper");
        check(helper, hopper.countItem(ModCrops.RICE_BRAN.get()) == 1, "and one bran");
        var bran = new ItemStack(ModCrops.RICE_BRAN.get());
        check(helper, PressingRecipe.of(bran) != null, "bran presses to oil");
        check(helper, bran.is(ItemTags.CHICKEN_FOOD), "and chickens eat it");
        check(helper, new ItemStack(ModCrops.BROWN_RICE.get()).is(RicePolisherBlockEntity.RICE),
                "brown rice is the rice crop");
        helper.succeed();
    }

    /** A carrot buried in the Nukadoko pickles in its time; stone does not go in, and only pickles come out by hand. */
    static void nukadokoPicklesCarrots(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, ModCooking.NUKADOKO.get());
        var tub = helper.getBlockEntity(pos, NukadokoBlockEntity.class);
        check(helper, !tub.bury(new ItemStack(Items.STONE)), "stone does not pickle");
        check(helper, tub.bury(new ItemStack(Items.CARROT)) && tub.bury(new ItemStack(Items.BEETROOT)), "a carrot and a beetroot go in");
        var recipe = PicklingRecipe.of(new ItemStack(Items.CARROT));
        check(helper, recipe != null && recipe.result().value() == ModCooking.CARROT_NUKAZUKE.get(), "a carrot pickles to nukazuke");
        check(helper, tub.digOut(false).isEmpty(), "nothing is ready at first");
        for (int t = 0; t < recipe.time(); t++) {
            NukadokoBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), tub);
        }
        check(helper, tub.done(0) && tub.items().get(0).is(ModCooking.CARROT_NUKAZUKE.get()), "in its time the carrot is pickled");
        var out = tub.digOut(false);
        check(helper, out.size() == 2 && tub.items().stream().allMatch(ItemStack::isEmpty), "both are pickled and come out");
        helper.succeed();
    }

    /**
     * Soap boils up from oil and caustic soda; it washes red wool white and a blue bed back to white, both halves, and
     * takes the oil off a hinge.
     */
    static void soapWashesOutDyeAndOil(GameTestHelper helper) {
        var level = helper.getLevel();
        var soapRecipe = PotRecipe.find(level,
                List.of(new ItemStack(ModItems.CAUSTIC_SODA.get())), PotRecipe.Liquid.OIL, 1_000);
        check(helper, soapRecipe != null && soapRecipe.value().result().item().value() == ModOil.SOAP.get(),
                "caustic soda in oil boils up into soap");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        Consumer<BlockPos> scrub = pos -> {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModOil.SOAP.get()));
            var hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)),
                    Direction.UP, helper.absolutePos(pos), false);
            new ItemStack(ModOil.SOAP.get()).onItemUseFirst(new UseOnContext(player,
                    InteractionHand.MAIN_HAND, hit));
        };
        BlockPos wool = new BlockPos(1, 2, 1);
        helper.setBlock(wool, Blocks.WOOL.pick(DyeColor.RED));
        scrub.accept(wool);
        helper.assertBlockPresent(Blocks.WOOL.pick(DyeColor.WHITE), wool);
        BlockPos foot = new BlockPos(3, 2, 1);
        helper.setBlock(foot, Blocks.BED.pick(DyeColor.BLUE).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH));
        helper.setBlock(foot.south(), Blocks.BED.pick(DyeColor.BLUE).defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.HEAD));
        scrub.accept(foot);
        helper.assertBlockPresent(Blocks.BED.pick(DyeColor.WHITE), foot);
        helper.assertBlockPresent(Blocks.BED.pick(DyeColor.WHITE), foot.south());
        check(helper, helper.getBlockState(foot.south()).getValue(BedBlock.PART)
                == BedPart.HEAD, "the bed keeps its shape");
        BlockPos door = new BlockPos(5, 2, 1);
        helper.setBlock(door, Blocks.OAK_TRAPDOOR);
        Lubrication.lubricate(level, helper.absolutePos(door));
        scrub.accept(door);
        check(helper, !Lubrication.isLubricated(level, helper.absolutePos(door)), "the oil is washed off the hinge");
        helper.succeed();
    }

    /**
     * Each raw food risks what its data map says: chicken always and badly, pork half the time as badly, beef a quarter
     * of the time and mildly, cod one time in twenty with a parasite; cooked food nothing. The dice are rolled by hand
     * here. Something worse eaten while something milder brews takes its place, the clock running on; and each strikes
     * with its own effects.
     */
    static void rawFoodRisks(GameTestHelper helper) {
        var FP = new Object() {
            FoodPoisoning.Risk risk(Item item) {
                return FoodPoisoning.risk(new ItemStack(item));
            }
        };
        var severe = FoodPoisoning.Illness.SEVERE;
        var mild = FoodPoisoning.Illness.MILD;
        var parasite = FoodPoisoning.Illness.PARASITE;
        check(helper, FP.risk(Items.CHICKEN).chance() == 1.0F && FP.risk(Items.CHICKEN).illness() == severe, "chicken: always, badly");
        check(helper, FP.risk(Items.PORKCHOP).chance() == 0.5F && FP.risk(Items.PORKCHOP).illness() == severe, "pork: half the time, badly");
        check(helper, FP.risk(Items.BEEF).chance() == 0.25F && FP.risk(Items.BEEF).illness() == mild
                && FP.risk(Items.MUTTON).illness() == mild && FP.risk(Items.RABBIT).illness() == mild, "red meat: a quarter, mildly");
        check(helper, FP.risk(Items.COD).chance() == 0.05F && FP.risk(Items.SALMON).illness() == parasite, "fish: one in twenty, a parasite");
        check(helper, FP.risk(Items.COOKED_BEEF) == null && FP.risk(Items.COOKED_COD) == null && FP.risk(Items.BREAD) == null,
                "cooked food and bread: nothing");

        var lucky = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(lucky, new ItemStack(Items.BEEF), 0.3F);
        FoodPoisoning.ate(lucky, new ItemStack(Items.COD), 0.06F);
        check(helper, FoodPoisoning.incubating(lucky) == 0, "rolled above the chance: nothing");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(player, new ItemStack(Items.BEEF), 0.1F);
        check(helper, FoodPoisoning.brewing(player) == mild, "beef: an upset stomach brewing");
        int wait = FoodPoisoning.incubating(player);
        FoodPoisoning.tick(player);
        FoodPoisoning.ate(player, new ItemStack(Items.PORKCHOP), 0.4F);
        check(helper, FoodPoisoning.brewing(player) == severe
                && FoodPoisoning.incubating(player) == wait - 1, "then pork: worse, and the clock runs on");
        FoodPoisoning.ate(player, new ItemStack(Items.COD), 0.01F);
        check(helper, FoodPoisoning.brewing(player) == severe, "a milder one does not take its place");

        var fishEater = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(fishEater, new ItemStack(Items.SALMON), 0.01F);
        check(helper, FoodPoisoning.brewing(fishEater) == parasite, "salmon: a parasite");
        for (int t = FoodPoisoning.incubating(fishEater); t > 0; t--) {
            FoodPoisoning.tick(fishEater);
        }
        check(helper, fishEater.hasEffect(MobEffects.WEAKNESS)
                && !fishEater.hasEffect(MobEffects.POISON), "cramps: weakness, no poison");
        var beefEater = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(beefEater, new ItemStack(Items.MUTTON), 0.0F);
        for (int t = FoodPoisoning.incubating(beefEater); t > 0; t--) {
            FoodPoisoning.tick(beefEater);
        }
        check(helper, beefEater.hasEffect(MobEffects.HUNGER)
                && !beefEater.hasEffect(MobEffects.POISON), "an upset stomach: hunger, no poison");
        helper.succeed();
    }

    /** Raw chicken is fine at first; a while later poison and nausea set in. Cooked chicken does nothing. */
    static void rawChickenMakesYouIllLater(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var cook = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(player, new ItemStack(Items.CHICKEN));
        FoodPoisoning.ate(cook, new ItemStack(Items.COOKED_CHICKEN));
        int wait = FoodPoisoning.incubating(player);
        check(helper, wait >= FoodPoisoning.MIN_DELAY && wait <= FoodPoisoning.MAX_DELAY,
                "raw chicken starts the clock");
        check(helper, FoodPoisoning.incubating(cook) == 0, "cooked chicken does not");
        check(helper, !player.hasEffect(MobEffects.POISON), "nothing at first");
        for (int t = 0; t < wait; t++) {
            FoodPoisoning.tick(player);
        }
        check(helper, player.hasEffect(MobEffects.POISON)
                && player.hasEffect(MobEffects.NAUSEA), "then poison and nausea");
        check(helper, FoodPoisoning.incubating(player) == 0, "and it is over");
        helper.succeed();
    }

    /**
     * A log and a glass bottle in the Retort give charcoal and wood vinegar; the Distiller makes it methanol; methanol and
     * caustic soda in oil in the Iron Pot make biodiesel, ladled into bottles, which a Generator burns - putting the
     * empty bottles aside when it burns from a stack.
     */
    static void woodToMethanolToBiodiesel(GameTestHelper helper) {
        var retorting = MercuryRecipes.retorting(new ItemStack(Items.OAK_LOG));
        check(helper, retorting != null && retorting.byproduct().value() == ModOil.WOOD_VINEGAR.get()
                && retorting.remainder().map(h -> h.value() == Items.CHARCOAL).orElse(false)
                && retorting.reagents().stream().anyMatch(set -> new ItemStack(Items.GLASS_BOTTLE).is(set)), "a log and a bottle: charcoal and wood vinegar");
        var distilling = MercuryRecipes.distillation(new ItemStack(ModOil.WOOD_VINEGAR.get()));
        check(helper, distilling != null && distilling.result().map(h -> h.value() == ModOil.METHANOL.get()).orElse(false),
                "wood vinegar distils to methanol");
        // a bucket of oil, four methanol and the one caustic soda that catalyses it all
        var biodiesel = PotRecipe.find(helper.getLevel(), List.of(
                new ItemStack(ModOil.METHANOL.get(), 4), new ItemStack(ModItems.CAUSTIC_SODA.get())),
                PotRecipe.Liquid.OIL, 1_000);
        check(helper, biodiesel != null && biodiesel.value().result().item().value() == ModOil.BIODIESEL_BOTTLE.get()
                && biodiesel.value().container().map(h -> h.value() == Items.GLASS_BOTTLE).orElse(false), "methanol and lye in oil: biodiesel, in bottles");
        BlockPos pos = new BlockPos(1, 3, 1);
        helper.setBlock(pos.below(), Blocks.HOPPER);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        // the same energy in either: the Diesel Generator, which takes only biodiesel, is what gets more out of it
        check(helper, generator.burnTime(helper.getLevel(), new ItemStack(ModOil.BIODIESEL_BOTTLE.get()))
                == generator.burnTime(helper.getLevel(), new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get())),
                "biodiesel burns as long as oil in a Generator");
        // bottles burn in a Generator only: a furnace keeps a stack's empties only when it runs out (the user, 2026-10-06)
        check(helper, !new ItemStack(ModOil.BIODIESEL_BOTTLE.get()).has(DataComponents.COOKING_FUEL)
                && !new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()).has(DataComponents.COOKING_FUEL)
                && new ItemStack(ModOil.BIODIESEL_BUCKET.get()).has(DataComponents.COOKING_FUEL),
                "a bottle is no furnace fuel; a bucket is");
        generator.setItem(0, new ItemStack(ModOil.BIODIESEL_BOTTLE.get(), 2));
        helper.succeedWhen(() -> {
            var hopper = helper.getBlockEntity(pos.below(), HopperBlockEntity.class);
            check(helper, generator.getItem(0).getCount() == 1 && hopper.countItem(Items.GLASS_BOTTLE) == 1,
                    "one bottle burning, its empty put aside into the hopper");
        });
    }

    /**
     * Piped oil, methanol and caustic soda in a powered Stirring Tank make biodiesel, pushed up a Copper Pipe into a
     * Generator, which burns it from its tank with nothing in its fuel slot; the methanol's bottle goes into the chest below.
     */
    static void stirringTankMakesBiodieselForAGenerator(GameTestHelper helper) {
        BlockPos tankPos = new BlockPos(1, 2, 1);
        helper.setBlock(tankPos.below(), Blocks.CHEST);
        helper.setBlock(tankPos, ModBlocks.STIRRING_TANK.get());
        BlockPos generatorPos = tankPos.above().east(2);
        helper.setBlock(generatorPos, ModBlocks.GENERATOR.get());
        for (BlockPos pipe : List.of(tankPos.above(), tankPos.above().east())) {   // placed as a player would, so they join up
            helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().getStateForPlacement(new BlockPlaceContext(
                    helper.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_PIPE.get()),
                    new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pipe)), Direction.UP,
                            helper.absolutePos(pipe), false))));
        }
        var tank = helper.getBlockEntity(tankPos, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        check(helper, tank.canPlaceItem(0, new ItemStack(ModOil.METHANOL.get()))
                && tank.canPlaceItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()))
                && !tank.canPlaceItem(StirringTankBlockEntity.CATALYST, new ItemStack(Items.STONE)),
                "methanol in the charge slot, caustic soda in the second");
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get()));
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        int poured = Move.insert(tank.fluidHandler(),
                FluidResource.of(OilFluids.SOURCE.get()), 1_000);
        check(helper, poured == 1_000, "the oil tank takes piped oil");
        var generator = helper.getBlockEntity(generatorPos, GeneratorBlockEntity.class);
        helper.succeedWhen(() -> {
            var chest = helper.getBlockEntity(tankPos.below(), ChestBlockEntity.class);
            check(helper, tank.oilStored() == 750 && tank.getItem(0).isEmpty(), "one batch stirred");
            check(helper, chest.countItem(Items.GLASS_BOTTLE) == 1, "the empty bottle below");
            check(helper, generator.energyStored() > 0 && generator.getItem(0).isEmpty(), "the generator runs on the piped biodiesel");
        });
    }

    private EarlyReviewTests() {}
}
