package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.block.NestedTimerBlock;
import dev.nookworks.block.PlanterBlock;
import dev.nookworks.block.RopeBlock;
import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.Elevators;
import dev.nookworks.feature.FoodPoisoning;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.item.BuildingWandItem;
import dev.nookworks.item.MagnetItem;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.mercury.MercuryTankBlock;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.animal.equine.AbstractChestedHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** Things the September 2026 reviews found broken, each held to the right behaviour. */
final class FixTests {
    static void register() {
        NookworksGameTests.test("timer_lets_go_after_a_save_mid_pulse", FixTests::timerLetsGoAfterASaveMidPulse);
        NookworksGameTests.test("spawner_held_off_keeps_its_controller_to_itself", FixTests::spawnerHoldNotSent);
        NookworksGameTests.test("wrench_leaves_a_command_block_to_operators", FixTests::wrenchLeavesCommandBlocks);
        NookworksGameTests.test("absorption_pad_drinks_only_from_who_stands_on_it", 80, FixTests::padDrinksOnlyFromOnIt);
        NookworksGameTests.test("timer_hold_stops_the_rest_of_a_round", 120, FixTests::timerHoldStopsTheRestOfARound);
        NookworksGameTests.test("pipe_hands_on_its_last_drop", 200, FixTests::pipeHandsOnItsLastDrop);
        NookworksGameTests.test("laser_short_of_power_never_lights_a_tree", 120, FixTests::laserShortOfPowerNeverLightsATree);
        NookworksGameTests.test("elevator_refuses_lava_and_walls", FixTests::elevatorRefusesLavaAndWalls);
        NookworksGameTests.test("mob_bottle_keeps_the_whole_mob", FixTests::mobBottleKeepsTheWholeMob);
        NookworksGameTests.test("mob_bottle_takes_a_pet_only_from_its_owner", FixTests::mobBottleTakesAPetOnlyFromItsOwner);
        NookworksGameTests.test("stirring_tank_making_biodiesel_takes_no_mercury", FixTests::stirringTankTakesNoMercury);
        NookworksGameTests.test("tank_sight_glasses_show_the_level", FixTests::tankSightGlassesShowTheLevel);
        NookworksGameTests.test("crusher_starts_over_for_a_swapped_input", FixTests::crusherStartsOverForASwappedInput);
        NookworksGameTests.test("wand_places_what_the_item_holds", FixTests::wandPlacesWhatTheItemHolds);
        NookworksGameTests.test("planter_plants_rice_in_a_paddy", FixTests::planterPlantsRiceInAPaddy);
        NookworksGameTests.test("rope_hangs_from_a_beam", FixTests::ropeHangsFromABeam);
        NookworksGameTests.test("chair_sits_low_under_a_low_ceiling", FixTests::chairSitsLowUnderALowCeiling);
        NookworksGameTests.test("raw_chicken_spares_the_creative_and_milk_cures", FixTests::rawChickenSparesTheCreative);
        NookworksGameTests.test("magnet_leaves_what_is_not_its_owners", FixTests::magnetLeavesOthersItems);
        NookworksGameTests.test("rice_polisher_stops_when_full", FixTests::ricePolisherStopsWhenFull);
        NookworksGameTests.test("wrench_flushes_a_pipe", FixTests::wrenchFlushesAPipe);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), face, helper.absolutePos(pos), false);
    }

    /**
     * A world saved while a pulse is on comes back with the output on and no pulse running: the timer lets go at once,
     * rather than holding the line high until the next round and swallowing that round's rising edge.
     */
    private static void timerLetsGoAfterASaveMidPulse(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.NESTED_TIMER.get().defaultBlockState().setValue(NestedTimerBlock.POWERED, true));
        var timer = helper.getBlockEntity(pos, NestedTimerBlockEntity.class);
        timer.setInterval(72_000); // no pulse of its own for an hour
        var level = helper.getLevel();
        var saved = timer.saveWithoutMetadata(level.registryAccess());
        saved.remove("OnFor"); // as an older build saved it
        try (var reporter = new ProblemReporter.ScopedCollector(timer.problemPath(), LogUtils.getLogger())) {
            timer.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
        }
        for (int i = 0; i < 3; i++) {
            NestedTimerBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), timer);
        }
        check(helper, !helper.getBlockState(pos).getValue(NestedTimerBlock.POWERED), "the output is let go");
        check(helper, timer.saveWithoutMetadata(level.registryAccess()).contains("OnFor"), "and a pulse's length is saved now");
        helper.succeed();
    }

    /** A signal arriving part-way through a round of two pulses holds the second, as the screen says it will. */
    private static void timerHoldStopsTheRestOfARound(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.NESTED_TIMER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        var timer = helper.getBlockEntity(pos, NestedTimerBlockEntity.class);
        timer.setInterval(40);
        timer.setPulses(2);
        timer.setGap(20);
        int[] pulses = {0};
        boolean[] was = {false};
        helper.onEachTick(() -> {
            boolean on = helper.getLevel().getSignal(helper.absolutePos(pos), Direction.SOUTH) > 0;
            if (on && !was[0]) {
                pulses[0]++;
                if (pulses[0] == 1) {
                    helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK); // held right after the first
                }
            }
            was[0] = on;
        });
        helper.runAfterDelay(75, () -> {
            check(helper, pulses[0] == 1, "one pulse, and the held second never came: " + pulses[0]);
            helper.succeed();
        });
    }

    /**
     * Two pipes to a pot hand on every last millibucket of water, so the line is empty after and takes oil - no drop
     * stays behind in the pipe before the pot to keep other fluids out for good.
     */
    private static void pipeHandsOnItsLastDrop(GameTestHelper helper) {
        BlockPos first = new BlockPos(1, 2, 1);
        BlockPos second = first.east();
        BlockPos potPos = second.east();
        for (BlockPos pipe : List.of(first, second)) {
            helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().getStateForPlacement(new BlockPlaceContext(
                    helper.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_PIPE.get()), hit(helper, pipe, Direction.UP))));
        }
        helper.setBlock(potPos, ModCooking.IRON_POT.get());
        helper.setBlock(first, helper.getBlockState(first).updateShape(helper.getLevel(), helper.getLevel(), helper.absolutePos(first),
                Direction.EAST, helper.absolutePos(second), helper.getBlockState(second), helper.getLevel().getRandom()));
        var upstream = helper.getBlockEntity(first, CopperPipeBlockEntity.class);
        var downstream = helper.getBlockEntity(second, CopperPipeBlockEntity.class);
        var pot = helper.getBlockEntity(potPos, IronPotBlockEntity.class);
        int poured = Move.insert(upstream.handlerFor(null),
                FluidResource.of(Fluids.WATER), 999);
        check(helper, poured == 999, "an odd amount of water goes in");
        helper.succeedWhen(() -> {
            check(helper, upstream.stored() == 0 && downstream.stored() == 0, "both pipes emptied: " + upstream.stored() + " / " + downstream.stored());
            check(helper, pot.amount() == 999, "all of it in the pot");
            int oil = Move.insert(upstream.handlerFor(null), FluidResource.of(OilFluids.SOURCE.get()), 100);
            check(helper, oil == 100, "and the empty line takes oil now");
        });
    }

    /** A laser without the surge stored to fell a tree does not set it on fire while it waits. */
    private static void laserShortOfPowerNeverLightsATree(GameTestHelper helper) {
        BlockPos laserPos = new BlockPos(1, 2, 3);
        helper.setBlock(laserPos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, Direction.EAST));
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.OAK_LOG);
        }
        var leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1); // not 7, "already decaying": a random tick took one now and then
        for (BlockPos p : BlockPos.betweenClosed(new BlockPos(3, 5, 2), new BlockPos(5, 5, 4))) {
            helper.setBlock(p.immutable(), leaves);
        }
        var laser = helper.getBlockEntity(laserPos, LaserBlockEntity.class);
        helper.onEachTick(() -> laser.energy().set(LaserBlockEntity.FE_PER_FELL - 1)); // always a little short
        helper.runAfterDelay(100, () -> {
            check(helper, helper.getBlockState(new BlockPos(4, 2, 3)).is(Blocks.OAK_LOG), "the tree still stands");
            for (BlockPos p : BlockPos.betweenClosed(new BlockPos(2, 1, 2), new BlockPos(5, 5, 4))) {
                check(helper, !helper.getBlockState(p.immutable()).is(BlockTags.FIRE), "and nothing near it burns at " + p);
            }
            helper.succeed();
        });
    }

    /** An elevator will not send anyone into lava over the next one up, nor into the wall of a shaft. */
    private static void elevatorRefusesLavaAndWalls(GameTestHelper helper) {
        BlockPos low = new BlockPos(2, 1, 2);
        BlockPos high = low.above(4);
        helper.setBlock(low, ModBlocks.ELEVATOR.get());
        helper.setBlock(high, ModBlocks.ELEVATOR.get());
        helper.setBlock(high.above(), Blocks.LAVA);
        var level = helper.getLevel();
        check(helper, Elevators.findDestination(level, helper.absolutePos(low), true, 8) == null,
                "lava over the upper elevator: no trip");
        helper.setBlock(high.above(), Blocks.AIR);
        check(helper, Elevators.findDestination(level, helper.absolutePos(low), true, 8) != null, "clear, it goes");
        // Stood at the very edge below, the player arrives clear of the wall beside the shaft above.
        helper.setBlock(high.above().east(), Blocks.STONE);
        helper.setBlock(high.above(2).east(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 edge = Vec3.atBottomCenterOf(helper.absolutePos(low.above())).add(0.49, 0, 0);
        player.snapTo(edge.x, edge.y, edge.z);
        Vec3 arrives = Elevators.arrival(player, helper.absolutePos(high));
        check(helper, level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(arrives)),
                "stood at the edge below, the player arrives clear of the wall above");
        // A carpet laid on it, or two layers of snow, is no obstacle: one arrives standing on it (the user, 2026-10-01).
        helper.setBlock(high.above(), Blocks.MOSS_CARPET);
        check(helper, Elevators.findDestination(level, helper.absolutePos(low), true, 8) != null, "a moss carpet on it: it goes");
        double onCarpet = Elevators.arrival(player, helper.absolutePos(high)).y - helper.absolutePos(high).getY();
        check(helper, Math.abs(onCarpet - (1 + 1 / 16.0)) < 1e-6, "and one arrives on the carpet, " + onCarpet);
        helper.setBlock(high.above(), Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3));
        check(helper, Elevators.findDestination(level, helper.absolutePos(low), true, 8) == null, "three layers of snow are a step: no trip");
        helper.succeed();
    }

    /**
     * The whole mob goes in the bottle and comes back out: a named villager keeps its name, job and trades; a donkey
     * its chest and what is in it; nothing is dropped or lost on the way.
     */
    private static void mobBottleKeepsTheWholeMob(GameTestHelper helper) {
        var level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var villager = helper.spawn(EntityTypes.VILLAGER, new BlockPos(2, 2, 2));
        villager.setCustomName(Component.literal("Hanako"));
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(),
                VillagerProfession.LIBRARIAN).withLevel(3));
        villager.setHealth(villager.getMaxHealth() / 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, villager);
        ItemStack bottle = player.getMainHandItem();
        check(helper, MobBottleItem.captured(bottle) == EntityTypes.VILLAGER && MobBottleItem.capturedData(bottle) != null, "the villager is in, whole");
        check(helper, villager.isRemoved(), "and gone from the world");
        bottle.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, new BlockPos(4, 0, 4), Direction.UP)));
        var back = helper.getEntities(EntityTypes.VILLAGER);
        check(helper, back.size() == 1, "one villager comes back out");
        var released = back.get(0);
        check(helper, released.getCustomName() != null && released.getCustomName().getString().equals("Hanako"), "with its name");
        check(helper, released.getVillagerData().level() == 3 && released.getVillagerData().profession()
                .is(VillagerProfession.LIBRARIAN), "its job and its level");
        check(helper, MobBottleItem.captured(bottle) == null, "and the bottle is empty again");

        var donkey = helper.spawn(EntityTypes.DONKEY, new BlockPos(2, 2, 5));
        withChest(helper, donkey);
        donkey.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 7));
        donkey.setHealth(donkey.getMaxHealth() / 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, donkey);
        check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing it carried falls out");
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, new BlockPos(5, 0, 5), Direction.UP)));
        var donkeys = helper.getEntities(EntityTypes.DONKEY);
        check(helper, donkeys.size() == 1 && donkeys.get(0).hasChest(), "the donkey comes back with its chest");
        check(helper, donkeys.get(0).getInventory().countItem(Items.DIAMOND) == 7, "and the seven diamonds in it");
        helper.succeed();
    }

    /** A tame wolf goes in a bottle only by its owner's hand, and comes out still theirs. */
    private static void mobBottleTakesAPetOnlyFromItsOwner(GameTestHelper helper) {
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        Player stranger = helper.makeMockPlayer(GameType.SURVIVAL);
        var wolf = helper.spawn(EntityTypes.WOLF, new BlockPos(2, 2, 2));
        wolf.tame(owner);
        wolf.setHealth(wolf.getMaxHealth() / 2);
        check(helper, MobBottleItem.check(wolf, stranger) == MobBottleItem.Refusal.OWNED, "not by a stranger");
        check(helper, MobBottleItem.check(wolf, owner) == MobBottleItem.Refusal.NONE, "by its owner");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(owner, InteractionHand.MAIN_HAND, wolf);
        owner.getMainHandItem().useOn(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit(helper, new BlockPos(4, 0, 4), Direction.UP)));
        var wolves = helper.getEntities(EntityTypes.WOLF);
        check(helper, wolves.size() == 1, "one wolf comes back out, got " + wolves.size());
        check(helper, wolves.get(0).isTame() && wolves.get(0).getOwnerReference() != null
                && wolves.get(0).getOwnerReference().getUUID().equals(owner.getUUID()), "still tame, and still theirs");
        helper.succeed();
    }

    /** Gives a donkey a chest, as a player putting one on it would (the chest is a whole new inventory). */
    private static void withChest(GameTestHelper helper, AbstractChestedHorse horse) {
        var level = helper.getLevel();
        try (var reporter = new ProblemReporter.ScopedCollector(horse.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            horse.saveWithoutId(output);
            var tag = output.buildResult();
            tag.putBoolean("ChestedHorse", true);
            horse.load(TagValueInput.create(reporter, level.registryAccess(), tag));
        }
    }

    /** Making biodiesel, a Stirring Tank refuses mercury: the retort's mercury is for somewhere that uses it. */
    private static void stirringTankTakesNoMercury(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(pos, StirringTankBlockEntity.class);
        check(helper, tank.fillMercury(100, true) == 100, "idle, it takes mercury for amalgam");
        tank.setItem(0, new ItemStack(ModOil.METHANOL.get()));
        check(helper, tank.fillMercury(100, true) == 0, "with methanol in, none");
        helper.succeed();
    }

    /**
     * A tank's sight glass stands as high as what is in it: empty shows empty, the first drop shows a step, full shows
     * full - and it follows the tank down again. Both tanks, the Mercury Tank keeping its facing.
     */
    private static void tankSightGlassesShowTheLevel(GameTestHelper helper) {
        var level = SightGlass.LEVEL;
        BlockPos hg = new BlockPos(1, 2, 1);
        helper.setBlock(hg, ModBlocks.MERCURY_TANK.get().defaultBlockState()
                .setValue(MercuryTankBlock.FACING, Direction.EAST));
        var mercury = helper.getBlockEntity(hg, MercuryTankBlockEntity.class);
        check(helper, helper.getBlockState(hg).getValue(level) == 0, "an empty tank shows an empty glass");
        mercury.fillMercury(1, false);
        check(helper, helper.getBlockState(hg).getValue(level) == 1, "one drop shows the first step");
        mercury.fillMercury(MercuryTankBlockEntity.CAPACITY, false);
        check(helper, helper.getBlockState(hg).getValue(level) == 6, "full shows full");
        check(helper, helper.getBlockState(hg).getValue(MercuryTankBlock.FACING) == Direction.EAST,
                "and the tank still faces the way it was set");
        mercury.drainMercury(MercuryTankBlockEntity.CAPACITY / 2, false);
        check(helper, helper.getBlockState(hg).getValue(level) == 3, "half drained shows half");
        BlockPos xp = new BlockPos(3, 2, 1);
        helper.setBlock(xp, ModBlocks.EXPERIENCE_TANK.get());
        var experience = helper.getBlockEntity(xp, ExperienceTankBlockEntity.class);
        experience.fill(ExperienceTankBlockEntity.CAPACITY / 6 + 1);
        check(helper, helper.getBlockState(xp).getValue(level) == 2, "a sixth and a drop over shows two steps");
        experience.drain(ExperienceTankBlockEntity.CAPACITY);
        check(helper, helper.getBlockState(xp).getValue(level) == 0, "drained dry, empty again");
        helper.succeed();
    }

    /** Swapping the ore in a crusher for another in one click starts the work over, not a tick from done. */
    private static void crusherStartsOverForASwappedInput(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(pos, CrusherBlockEntity.class);
        crusher.energy().set((int) crusher.energy().getCapacityAsLong());
        crusher.setItem(0, new ItemStack(Items.COBBLESTONE, 4));
        for (int i = 0; i < 20; i++) {
            CrusherBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), crusher);
        }
        check(helper, crusher.guiProgress() > 0, "cobblestone under way");
        crusher.setItem(0, new ItemStack(Items.GRAVEL, 4));
        check(helper, crusher.guiProgress() == 0, "gravel swapped in: from the start");
        helper.succeed();
    }

    /**
     * The wand places each block as the item it takes would be placed: a worn-out Nourished Soil stays worn out. And it
     * copies how a slab or stair sits - a top slab gives top slabs.
     */
    private static void wandPlacesWhatTheItemHolds(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 1, 2);
        helper.setBlock(pos, ModBlocks.FERTILE_SOIL.get().defaultBlockState().setValue(FertileSoilBlock.RICHNESS, 0));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack worn = new ItemStack(ModItems.FERTILE_SOIL.get());
        worn.set(DataComponents.BLOCK_STATE, new BlockItemStateProperties(Map.of("richness", "0")));
        player.getInventory().setItem(1, worn);
        ItemStack wand = new ItemStack(ModItems.BUILDING_WAND.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        wand.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP)));
        check(helper, worn.isEmpty(), "the worn soil was used");
        check(helper, helper.getBlockState(pos.above()).getValue(FertileSoilBlock.RICHNESS) == 0, "and placed as worn as it was");

        BlockState top = Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                SlabType.TOP);
        check(helper, BuildingWandItem.toPlace(top).getValue(BlockStateProperties.SLAB_TYPE)
                == SlabType.TOP, "a top slab copies as a top slab");
        BlockState upside = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HALF,
                Half.TOP);
        check(helper, BuildingWandItem.toPlace(upside).getValue(BlockStateProperties.HALF)
                == Half.TOP, "an upside-down stair as one");
        check(helper, BuildingWandItem.cost(Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                SlabType.DOUBLE)) == 2, "and a double slab costs two");
        helper.succeed();
    }

    /** Rice from a Planter goes into the paddy's water, never onto dry ground (where it would make water of nothing). */
    private static void planterPlantsRiceInAPaddy(GameTestHelper helper) {
        BlockPos planterPos = new BlockPos(3, 2, 1);
        helper.setBlock(planterPos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 2), Blocks.DIRT);
            helper.setBlock(new BlockPos(x, 1, 3), Blocks.MUD);
        }
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.WATER); // the one paddy square, a row further out
        var planter = helper.getBlockEntity(planterPos, PlanterBlockEntity.class);
        planter.energy().set((int) planter.energy().getCapacityAsLong());
        planter.setItem(0, new ItemStack(ModCrops.BROWN_RICE.get(), 4));
        check(helper, planter.plantOne(helper.getLevel()), "it plants");
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).is(ModCrops.RICE_CROP.get()), "in the paddy");
        for (int x = 2; x <= 4; x++) {
            check(helper, helper.getBlockState(new BlockPos(x, 2, 2)).isAir(), "and nothing on the dry row");
        }
        helper.succeed();
    }

    /** A rope hangs under a support's beam, reaching up to it, and under a top slab. */
    private static void ropeHangsFromABeam(GameTestHelper helper) {
        BlockPos beam = new BlockPos(2, 5, 2);
        helper.setBlock(beam, ModWoods.SUPPORTS.get("oak").get().defaultBlockState()
                .setValue(SupportBlock.SHAPE, SupportBlock.Shape.BEAM));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var rope = ModBlocks.ROPE.get();
        BlockState placed = rope.getStateForPlacement(new BlockPlaceContext(helper.getLevel(), player,
                InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get()), hit(helper, beam.below(), Direction.UP)));
        check(helper, placed != null && placed.canSurvive(helper.getLevel(), helper.absolutePos(beam.below())), "a rope hangs under the beam");
        check(helper, placed.getValue(RopeBlock.TIE) == 11, "reaching 11 px up to it, got " + placed.getValue(RopeBlock.TIE));
        BlockPos slab = new BlockPos(5, 5, 5);
        helper.setBlock(slab, Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                SlabType.TOP));
        check(helper, rope.defaultBlockState().canSurvive(helper.getLevel(), helper.absolutePos(slab.below())), "and under a top slab");
        helper.succeed();
    }

    /**
     * A player sits at the usual height in a room two blocks high (sat, their head is 1.62 over the floor); under a top
     * slab, 1.5 up, a little lower, their head clear of it - measured where the game really sets a rider, its feet
     * 0.6 under the seat; under a full block straight above the chair, not at all.
     */
    private static void chairSitsLowUnderALowCeiling(GameTestHelper helper) {
        BlockPos chair = new BlockPos(2, 1, 2);
        helper.setBlock(chair, ModWoods.CHAIRS.get("oak").get());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var level = helper.getLevel();
        double head = 1.8 - 0.6; // a player's height, less the vehicle attachment that sets their feet under a seat
        check(helper, SeatEntity.seatHeight(level, helper.absolutePos(chair), player) == SeatEntity.SEAT_HEIGHT,
                "under the open sky, the usual height");
        helper.setBlock(chair.above(2), Blocks.STONE);
        check(helper, SeatEntity.seatHeight(level, helper.absolutePos(chair), player) == SeatEntity.SEAT_HEIGHT,
                "two blocks up: the usual height, the head under the ceiling");
        helper.setBlock(chair.above(2), Blocks.AIR);
        helper.setBlock(chair.above(), Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.SLAB_TYPE,
                SlabType.TOP));
        double low = SeatEntity.seatHeight(level, helper.absolutePos(chair), player);
        check(helper, low >= SeatEntity.LOW_SEAT && low < SeatEntity.SEAT_HEIGHT && low + head <= 1.5,
                "under a top slab: a little lower, the head clear of it (" + low + ")");
        helper.setBlock(chair.above(), Blocks.STONE);
        check(helper, SeatEntity.seatHeight(level, helper.absolutePos(chair), player) < 0, "a block straight over the chair: no room");
        helper.succeed();
    }

    /**
     * A spawner held off by a Spawner Controller saves which controller (it keeps holding off with that one out of
     * reach), but what it sends the clients near it leaves that out: a modified client would read a far-off
     * controller's place, someone's base, off any spawner.
     */
    private static void spawnerHoldNotSent(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        helper.setBlock(at, Blocks.SPAWNER);
        var spawner = helper.getBlockEntity(at, net.minecraft.world.level.block.entity.SpawnerBlockEntity.class);
        var controller = net.minecraft.core.GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(40, 2, 40)));
        dev.nookworks.spawner.ControlledSpawner.of(spawner.getSpawner()).nookworks$holdOff(controller);
        var registries = helper.getLevel().registryAccess();
        check(helper, spawner.saveWithoutMetadata(registries).contains("NookworksHeldOffBy"), "saved with the spawner");
        check(helper, !spawner.getUpdateTag(registries).contains("NookworksHeldOffBy"), "not sent to the clients near it");
        // nor kept by a pick-block with its data, as the game saves it for one
        var picked = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, registries);
        spawner.saveCustomOnly(picked);
        spawner.removeComponentsFromTag(picked);
        check(helper, !picked.buildResult().contains("NookworksHeldOffBy"), "nor copied by a pick-block with its data");
        // only that it is off, so the clients show it standing still: the mob not turning, no flame (2026-10-11)
        var seen = new net.minecraft.world.level.block.entity.SpawnerBlockEntity(spawner.getBlockPos(), spawner.getBlockState());
        seen.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, spawner.getUpdateTag(registries)));
        check(helper, dev.nookworks.spawner.ControlledSpawner.of(seen.getSpawner()).nookworks$shownOff(), "a client hears it is off");
        dev.nookworks.spawner.ControlledSpawner.of(spawner.getSpawner()).nookworks$release();
        seen.loadWithComponents(net.minecraft.world.level.storage.TagValueInput.create(
                net.minecraft.util.ProblemReporter.DISCARDING, registries, spawner.getUpdateTag(registries)));
        check(helper, !dev.nookworks.spawner.ControlledSpawner.of(seen.getSpawner()).nookworks$shownOff(), "and that it is let go");
        helper.succeed();
    }

    /** A command block is a game master's to turn, as to edit: the Wrench in anybody else's hand leaves it as it is. */
    private static void wrenchLeavesCommandBlocks(GameTestHelper helper) {
        BlockPos command = new BlockPos(1, 2, 2);
        BlockPos furnace = new BlockPos(3, 2, 2);
        helper.setBlock(command, Blocks.COMMAND_BLOCK.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        helper.setBlock(furnace, Blocks.FURNACE.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        var player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.WRENCH.get()));
        for (BlockPos at : List.of(command, furnace)) {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit(helper, at, Direction.UP));
        }
        check(helper, helper.getBlockState(command).getValue(BlockStateProperties.FACING) == Direction.NORTH, "the command block not turned");
        check(helper, helper.getBlockState(furnace).getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH, "the furnace beside it is");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Standing beside an Absorption Pad - a hair's breadth over the next block - drains nobody; on it, it does. */
    private static void padDrinksOnlyFromOnIt(GameTestHelper helper) {
        BlockPos pad = new BlockPos(2, 2, 2);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var beside = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        beside.giveExperienceLevels(10);
        beside.snapTo(helper.absoluteVec(new Vec3(pad.getX() + 1.35, pad.getY() + 0.1, pad.getZ() + 0.5)));
        helper.runAfterDelay(40, () -> {
            check(helper, beside.experienceLevel == 10, "beside the pad, nothing drunk: level " + beside.experienceLevel);
            beside.snapTo(helper.absoluteVec(new Vec3(pad.getX() + 0.5, pad.getY() + 0.1, pad.getZ() + 0.5)));
            helper.runAfterDelay(30, () -> {
                check(helper, beside.experienceLevel < 10, "on it, drunk: level " + beside.experienceLevel);
                helper.getLevel().getServer().getPlayerList().remove(beside);
                helper.succeed();
            });
        });
    }

    /** Raw chicken does nothing to a creative player; a bucket of milk before it strikes settles it. */
    private static void rawChickenSparesTheCreative(GameTestHelper helper) {
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        FoodPoisoning.ate(creative, new ItemStack(Items.CHICKEN));
        check(helper, FoodPoisoning.incubating(creative) == 0, "creative: nothing");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        FoodPoisoning.ate(player, new ItemStack(Items.CHICKEN));
        check(helper, FoodPoisoning.incubating(player) > 0, "survival: it starts");
        FoodPoisoning.ate(player, new ItemStack(Items.MILK_BUCKET));
        check(helper, FoodPoisoning.incubating(player) == 0, "and milk stops it");
        helper.succeed();
    }

    /** The Magnet leaves an item that is someone else's to pick up, and one behind a wall. */
    private static void magnetLeavesOthersItems(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        var level = helper.getLevel();
        var mine = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.APPLE));
        var theirs = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.APPLE));
        var walled = new ItemEntity(level, 0, 0, 0, new ItemStack(Items.APPLE));
        Vec3 open = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 1, 1)));
        mine.setPos(open);
        theirs.setPos(open.add(0, 0, 1));
        theirs.setTarget(UUID.randomUUID());
        helper.setBlock(new BlockPos(3, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 2, 4), Blocks.STONE);
        walled.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 5))).add(0, 0.1, 0));
        for (var item : List.of(mine, theirs, walled)) {
            item.setNoPickUpDelay();
            item.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(item);
        }
        helper.setBlock(new BlockPos(1, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(1, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 1, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(0, 2, 4), Blocks.STONE);
        MagnetItem.pull(player, 8);
        check(helper, mine.getDeltaMovement().lengthSqr() > 0, "its own item is pulled");
        check(helper, theirs.getDeltaMovement().lengthSqr() == 0, "one meant for another player is not");
        check(helper, walled.getDeltaMovement().lengthSqr() == 0, "nor one behind a wall");
        helper.succeed();
    }

    /** With nowhere to put what it makes, the Rice Polisher keeps it inside, and when that is full the handle stops. */
    private static void ricePolisherStopsWhenFull(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModCrops.RICE_POLISHER.get());
        var polisher = helper.getBlockEntity(pos, RicePolisherBlockEntity.class);
        polisher.setItem(RicePolisherBlockEntity.INPUT, new ItemStack(ModCrops.BROWN_RICE.get(), 10));
        polisher.setItem(RicePolisherBlockEntity.RICE_OUT, new ItemStack(ModCrops.WHITE_RICE.get(), 63));
        for (int i = 0; i < 12; i++) {
            polisher.turn(helper.getLevel());
        }
        check(helper, polisher.getItem(RicePolisherBlockEntity.RICE_OUT).getCount() == 64, "one more fits, then it is full");
        check(helper, !polisher.wantsTurn() && polisher.getItem(RicePolisherBlockEntity.INPUT).getCount() == 9,
                "and the handle stops, the rest of the rice waiting");
        check(helper, helper.getEntities(EntityTypes.ITEM).isEmpty(), "nothing spilt on the floor");
        helper.succeed();
    }

    /** A Wrench used on a pipe holding water empties it, so the line can carry oil. */
    private static void wrenchFlushesAPipe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COPPER_PIPE.get());
        var pipe = helper.getBlockEntity(pos, CopperPipeBlockEntity.class);
        Move.insert(pipe.handlerFor(null), FluidResource.of(Fluids.WATER), 300);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        wrench.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.UP)));
        check(helper, pipe.stored() == 0, "flushed");
        check(helper, Move.insert(pipe.handlerFor(null), FluidResource.of(OilFluids.SOURCE.get()), 100) == 100,
                "and it takes oil");
        helper.succeed();
    }

    private FixTests() {}
}
