package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.StorageTests.placeStorage;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.block.FertileSoilBlock;
import dev.nookworks.block.OilLampBlock;
import dev.nookworks.block.RopeBlock;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.experience.AbsorptionPadBlock;
import dev.nookworks.experience.AbsorptionPadBlockEntity;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperiencePipeBlock;
import dev.nookworks.experience.ExperiencePipeBlockEntity;
import dev.nookworks.experience.ExperienceTankBlock;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.feature.Elevators;
import dev.nookworks.feature.VillageMap;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.furniture.ChairBlock;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.label.LabelEntity;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.DieselGeneratorBlockEntity;
import dev.nookworks.machine.ElectricFurnaceBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.mercury.MercuryBlock;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mixin.ExperienceOrbAccessor;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModVentilation;
import dev.nookworks.registry.ModWoods;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterSettings;
import dev.nookworks.sorter.SorterTags;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import dev.nookworks.spawner.SpawnerSettings;
import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageContents;
import dev.nookworks.storage.StorageMenu;
import dev.nookworks.storage.StorageTier;
import dev.nookworks.storage.StorageUpgradeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** What the third round of reviews (September 30, 2026) and the user's own play found, each held to the right behaviour. */
final class ReviewThreeTests {
    static void register() {
        NookworksGameTests.test("spawner_sender_goes_dark_after_a_restart", ReviewThreeTests::senderGoesDarkAfterARestart);
        NookworksGameTests.test("barrel_ctrl_q_throws_one_stack", ReviewThreeTests::barrelCtrlQThrowsOneStack);
        NookworksGameTests.test("machine_click_leaves_gear_in_hand", ReviewThreeTests::machineClickLeavesGearInHand);
        NookworksGameTests.test("upgrade_keeps_the_name_and_drops_what_it_cannot_hold", ReviewThreeTests::upgradeKeepsNameAndDropsTheRest);
        NookworksGameTests.test("machines_keep_their_liquids", ReviewThreeTests::machinesKeepTheirLiquids);
        NookworksGameTests.test("full_machines_give_off_experience", ReviewThreeTests::fullMachinesGiveOffExperience);
        NookworksGameTests.test("storage_barrel_drops_to_a_bare_hand", ReviewThreeTests::storageBarrelDropsToABareHand);
        NookworksGameTests.test("oil_lamp_keeps_its_oil_and_burns_in_steps", ReviewThreeTests::oilLampKeepsItsOil);
        NookworksGameTests.test("copper_tank_pours_out_of_its_bottom", ReviewThreeTests::copperTankPoursDown);
        NookworksGameTests.test("wrench_leaves_joins_alone", ReviewThreeTests::wrenchLeavesJoinsAlone);
        NookworksGameTests.test("rope_hangs_in_water", ReviewThreeTests::ropeHangsInWater);
        NookworksGameTests.test("elevator_ignores_fliers_and_spectators", ReviewThreeTests::elevatorIgnoresFliers);
        NookworksGameTests.test("corn_grows_on_tilled_nourished_soil", ReviewThreeTests::cornOnNourishedSoil);
        NookworksGameTests.test("kelp_clicked_on_a_block_is_not_eaten", ReviewThreeTests::kelpOnABlockIsNotEaten);
        NookworksGameTests.test("village_map_waits_for_a_newcomer", ReviewThreeTests::villageMapWaitsForANewcomer);
        NookworksGameTests.test("crusher_forgets_a_removed_input_while_blocked", ReviewThreeTests::crusherForgetsARemovedInput);
        NookworksGameTests.test("sifter_fills_its_buffer_before_stopping", ReviewThreeTests::sifterFillsItsBuffer);
        NookworksGameTests.test("generator_banks_its_fire_when_full", 150, ReviewThreeTests::generatorBanksItsFire);
        NookworksGameTests.test("generator_says_running_under_a_light_load", 150, ReviewThreeTests::generatorSteadyUnderALightLoad);
        NookworksGameTests.test("tank_clicks_end_at_the_tank", ReviewThreeTests::tankClicksEndAtTheTank);
        NookworksGameTests.test("precision_sorter_registers_tags_picked", ReviewThreeTests::sorterRegistersTagsPicked);
        NookworksGameTests.test("old_tag_sorters_come_over_as_tag_entries", ReviewThreeTests::oldTagSortersComeOver);
        NookworksGameTests.test("spawner_settings_cost_by_what_they_ask", ReviewThreeTests::spawnerSettingsCost);
        NookworksGameTests.test("label_in_the_middle_of_a_chest_sits_under_the_latch", ReviewThreeTests::labelUnderTheLatch);
        NookworksGameTests.test("fast_sorter_throws_at_a_hoppers_beat", 60, ReviewThreeTests::fastSorterThrowsAtAHoppersBeat);
        NookworksGameTests.test("mercury_tops_up_its_effects", ReviewThreeTests::mercuryTopsUpItsEffects);
        NookworksGameTests.test("generator_says_when_cables_run_past_reach", ReviewThreeTests::generatorSaysWhenCablesRunPastReach);
        NookworksGameTests.test("absorption_pad_stops_for_a_signal", 80, ReviewThreeTests::padStopsForASignal);
        NookworksGameTests.test("chair_keeps_its_shapes", ReviewThreeTests::chairKeepsItsShapes);
        NookworksGameTests.test("storage_contents_are_held_to_a_barrel", ReviewThreeTests::storageContentsHeldToABarrel);
        NookworksGameTests.test("charring_is_not_food_in_the_book", ReviewThreeTests::charringIsNotFood);
        NookworksGameTests.test("mob_bottle_leaves_riders_behind", ReviewThreeTests::mobBottleLeavesRidersBehind);
        NookworksGameTests.test("experience_pipe_is_clear_glass_when_empty", ReviewThreeTests::experiencePipeClearWhenEmpty);
        NookworksGameTests.test("every_vanilla_mob_has_bottle_colours", ReviewThreeTests::everyMobHasBottleColours);
        NookworksGameTests.test("water_flows_round_pots_and_pipes", ReviewThreeTests::waterFlowsRoundPotsAndPipes);
        NookworksGameTests.test("machines_light_their_lamp_while_they_run", ReviewThreeTests::machinesLightTheirLamp);
        NookworksGameTests.test("saved_energy_is_held_to_capacity", ReviewThreeTests::savedEnergyHeldToCapacity);
    }

    /**
     * A save holding more FE than the machine can - set by a command, or kept from a build with a bigger buffer - is
     * held to the capacity when it loads: Jade, drawing the energy bar of a block holding more than it can, crashed the
     * client that looked at it.
     */
    private static void savedEnergyHeldToCapacity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(pos, CrusherBlockEntity.class);
        var level = helper.getLevel();
        var saved = crusher.saveWithoutMetadata(level.registryAccess());
        var energy = new CompoundTag();
        energy.putInt("energy", 100 * (int) crusher.energy().getCapacityAsLong());
        saved.put("Energy", energy);
        try (var reporter = new ProblemReporter.ScopedCollector(crusher.problemPath(), LogUtils.getLogger())) {
            crusher.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), saved));
        }
        check(helper, crusher.energy().getAmountAsLong() == crusher.energy().getCapacityAsLong(),
                "held to its capacity: " + crusher.energy().getAmountAsLong() + " of " + crusher.energy().getCapacityAsLong());
        helper.succeed();
    }

    /**
     * The powered machines with no fire to show carry a status lamp: dark while they stand, lit while they run, and for
     * a moment after, so it does not blink between one item and the next. The Crusher shows it; the rest share the code.
     */
    private static void machinesLightTheirLamp(GameTestHelper helper) {
        var running = MachineBlockEntity.RUNNING;
        for (var block : List.of(ModBlocks.CRUSHER.get(), ModBlocks.STONE_MAKER.get(), ModBlocks.PRINTING_PRESS.get(),
                ModBlocks.WATER_PUMP.get(), ModBlocks.STIRRING_TANK.get(), ModBlocks.DISTILLER.get(), ModBlocks.PLANTER.get())) {
            check(helper, block.defaultBlockState().hasProperty(running) && !block.defaultBlockState().getValue(running),
                    block + " has a lamp, dark to begin with");
        }
        check(helper, !ModBlocks.RETORT.get().defaultBlockState().hasProperty(running), "the Retort shows its fire instead");
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        helper.setBlock(pos.below(), Blocks.CHEST);
        var crusher = helper.getBlockEntity(pos, CrusherBlockEntity.class);
        crusher.energy().set((int) crusher.energy().getCapacityAsLong());
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 64));
        helper.runAfterDelay(5, () -> {
            check(helper, helper.getBlockState(pos).getValue(running), "crushing, its lamp is lit");
            crusher.setItem(CrusherBlockEntity.INPUT, ItemStack.EMPTY);
            helper.runAfterDelay(3, () -> {
                check(helper, helper.getBlockState(pos).getValue(running), "a moment after, it is still lit");
                helper.runAfterDelay(MachineBlockEntity.LAMP_HOLD, () -> {
                    check(helper, !helper.getBlockState(pos).getValue(running), "and then dark");
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Flowing water washed away whatever was not solid in its way, as it does a torch: an Iron Pot with its soup, a
     * Wooden Tray with its loaf, an Absorption Pad, and pipes, cables and ducts, which broke a line - a missed click
     * with a water bucket by the stove was enough. They stand in the water's way now, as a sign does, and it flows round.
     */
    private static void waterFlowsRoundPotsAndPipes(GameTestHelper helper) {
        var water = Fluids.FLOWING_WATER;
        for (var block : List.of(ModCooking.IRON_POT.get(), ModKitchen.WOODEN_TRAY.get(),
                ModBlocks.ABSORPTION_PAD.get(), ModVentilation.DUCT.get(), ModBlocks.COPPER_PIPE.get(),
                ModBlocks.MERCURY_PIPE.get(), ModBlocks.EXPERIENCE_PIPE.get(), ModBlocks.ENERGY_CABLE.get())) {
            check(helper, !block.defaultBlockState().canBeReplaced(water), block + " is washed away by water");
        }
        BlockPos pot = new BlockPos(2, 2, 2);
        BlockPos tray = new BlockPos(4, 2, 2);
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 3; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE); // a floor for the tray to stand on
            }
        }
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(tray, ModKitchen.WOODEN_TRAY.get());
        helper.setBlock(new BlockPos(3, 2, 2), Blocks.WATER);
        helper.runAfterDelay(30, () -> {
            check(helper, helper.getBlockState(pot).is(ModCooking.IRON_POT.get()), "the pot is still there");
            check(helper, helper.getBlockState(tray).is(ModKitchen.WOODEN_TRAY.get()), "the tray is still there");
            check(helper, helper.getLevel().getFluidState(helper.absolutePos(new BlockPos(3, 2, 3))).is(FluidTags.WATER),
                    "the water did flow");
            helper.succeed();
        });
    }

    /**
     * A filled Mob Bottle's contents take the colours of its mob's spawn egg (mob_bottle_colors.json, made by
     * tools/textures/gen_mob_bottle_colors.py): every mob of vanilla's with an egg has its pair, so a new one is not
     * left to the colours for its kind. A black egg keeps its black (the Enderman's was once taken for the outline and
     * set aside), and a filled bottle has no enchantment glint, which washed every colour purple.
     */
    private static void everyMobHasBottleColours(GameTestHelper helper) {
        JsonObject table;
        try (var stream = ReviewThreeTests.class.getResourceAsStream("/assets/nookworks/mob_bottle_colors.json")) {
            check(helper, stream != null, "the colours are in the jar");
            table = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        int checked = 0;
        for (var type : BuiltInRegistries.ENTITY_TYPE) {
            var id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id.getNamespace().equals("minecraft") && MobBottleItem.spawnEgg(type).isPresent()) {
                check(helper, table.has(id.toString()) && table.getAsJsonArray(id.toString()).size() == 2, id + " has no bottle colours");
                checked++;
            }
        }
        check(helper, checked > 50, "the vanilla mobs with eggs were checked: " + checked);
        int enderman = Integer.parseInt(table.getAsJsonArray("minecraft:enderman").get(0).getAsString().substring(1), 16);
        check(helper, (enderman >> 16 & 0xFF) < 0x50 && (enderman >> 8 & 0xFF) < 0x50 && (enderman & 0xFF) < 0x50,
                "the Enderman's bottle is black: " + Integer.toHexString(enderman));
        check(helper, !MobBottleItem.filled(EntityTypes.ZOMBIE).hasFoil(), "a filled bottle does not glint");
        helper.succeed();
    }

    /**
     * An Experience Pipe is clear glass while it is empty and green while it holds experience - and for a second after,
     * so a line in use, where the experience comes and goes a step at a time, does not flicker. It was green always.
     */
    private static void experiencePipeClearWhenEmpty(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.EXPERIENCE_PIPE.get());
        var filled = ExperiencePipeBlock.FILLED;
        var pipe = helper.getBlockEntity(pos, ExperiencePipeBlockEntity.class);
        Runnable tick = () -> FluidPipeBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                helper.getBlockState(pos), pipe);
        tick.run();
        check(helper, !helper.getBlockState(pos).getValue(filled), "a new pipe is clear glass");
        var experience = FluidResource.of(ExperienceFluids.SOURCE.get());
        try (var transaction = Transaction.openRoot()) {
            pipe.handlerFor(Direction.UP).insert(experience, 100, transaction);
            transaction.commit();
        }
        tick.run();
        check(helper, helper.getBlockState(pos).getValue(filled), "with experience in it, it shows green");
        try (var transaction = Transaction.openRoot()) {
            pipe.handlerFor(Direction.UP).extract(experience, 100, transaction);
            transaction.commit();
        }
        tick.run();
        check(helper, helper.getBlockState(pos).getValue(filled), "emptied, it stays green a moment");
        for (int i = 0; i < ExperiencePipeBlockEntity.SHOW_FOR; i++) {
            tick.run();
        }
        check(helper, !helper.getBlockState(pos).getValue(filled), "then it is clear again");
        helper.succeed();
    }

    /**
     * A chicken jockey's chicken goes into the bottle alone: the zombie riding it stays where it was, and is not in the
     * bottle as well - the game saves riders with their mount, and one let out again was a second zombie.
     */
    private static void mobBottleLeavesRidersBehind(GameTestHelper helper) {
        var chicken = helper.spawn(EntityTypes.CHICKEN, new BlockPos(3, 2, 3));
        var zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 2, 3));
        zombie.setBaby(true);
        check(helper, zombie.startRiding(chicken, true, false), "the zombie rides the chicken");
        chicken.setHealth(chicken.getMaxHealth() / 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        check(helper, MobBottleItem.tryCapture(player, InteractionHand.MAIN_HAND, chicken) == InteractionResult.SUCCESS,
                "the chicken is caught");
        var data = MobBottleItem.capturedData(player.getMainHandItem());
        check(helper, data != null && !data.contains("Passengers"), "alone: its rider is not in the bottle");
        check(helper, zombie.isAlive() && !zombie.isPassenger(), "the zombie is left standing");
        helper.succeed();
    }

    /**
     * Mercury gives its poison and nausea again only once they are nearly out: given afresh each tick, the poison, which
     * bites when its time left is a multiple of 25, bit on every tick it was new.
     */
    private static void mercuryTopsUpItsEffects(GameTestHelper helper) {
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
        var poison = MobEffects.POISON;
        MercuryBlock.poison(helper.getLevel(), pig);
        var given = pig.getEffect(poison);
        MercuryBlock.poison(helper.getLevel(), pig);
        check(helper, given != null && pig.getEffect(poison) == given && given.getDuration() == MercuryBlock.POISON_TICKS,
                "with plenty left, the poison is left as it is");
        pig.removeEffect(poison);
        pig.addEffect(new MobEffectInstance(poison, 10, 0));
        MercuryBlock.poison(helper.getLevel(), pig);
        check(helper, pig.getEffect(poison).getDuration() == MercuryBlock.POISON_TICKS, "nearly out, it is given again");
        helper.succeed();
    }

    /**
     * A Generator whose cables run on past the reach a network is followed through says so, rather than running with
     * the machines far along quietly unpowered; with the reach long enough, it simply runs.
     */
    private static void generatorSaysWhenCablesRunPastReach(GameTestHelper helper) {
        BlockPos gen = new BlockPos(0, 1, 0);
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        // two layers of cable, 97 of them, each laid after the ones between it and the generator (so joined towards it)
        for (int y = 1; y <= 2; y++) {
            for (int x = 0; x < 7; x++) {
                for (int z = 0; z < 7; z++) {
                    if (!(x == 0 && y == 1 && z == 0)) {
                        helper.setBlock(new BlockPos(x, y, z), ModBlocks.ENERGY_CABLE.get());
                    }
                }
            }
        }
        GeneratorBlockEntity generator = helper.getBlockEntity(gen, GeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 4));
        var reach = NookworksConfig.ENERGY_CABLE_REACH;
        int was = reach.getAsInt();
        try {
            reach.set(64);
            MachineIO.networkChanged();
            GeneratorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(gen), helper.getBlockState(gen), generator);
            check(helper, generator.guiStatus() == MachineStatus.TOO_MANY_CABLES,
                    "97 cables, a reach of 64: it says so, " + generator.guiStatus());
            reach.set(512);
            MachineIO.networkChanged();
            GeneratorBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(gen), helper.getBlockState(gen), generator);
            check(helper, generator.guiStatus() == MachineStatus.RUNNING, "all in reach: it runs, " + generator.guiStatus());
        } finally {
            reach.set(was);
            MachineIO.networkChanged();
        }
        helper.succeed();
    }

    /**
     * The Absorption Pad drains whoever stands on it (sneaking or not, as the author wants it), and a redstone signal -
     * a lever beside it - switches it off: it drains nobody (and pulls in no orbs) while it lasts, its intake dark.
     */
    private static void padStopsForASignal(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 1, 3);
        BlockPos lever = new BlockPos(4, 1, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 on = helper.absoluteVec(new Vec3(3.5, 1.1, 3.5));
        player.snapTo(on.x, on.y, on.z);
        player.giveExperienceLevels(5);
        var held = new long[] {AbsorptionPadBlockEntity.heldPoints(player)};
        helper.runAfterDelay(10, () -> {
            long now = AbsorptionPadBlockEntity.heldPoints(player);
            check(helper, now < held[0], "standing on it, they pour it in");
            helper.setBlock(lever, Blocks.REDSTONE_BLOCK);
            held[0] = now;
        });
        helper.runAfterDelay(12, () -> {
            check(helper, helper.getBlockState(pad).getValue(AbsorptionPadBlock.POWERED), "the signal switches it off");
            held[0] = AbsorptionPadBlockEntity.heldPoints(player);
        });
        helper.runAfterDelay(40, () -> {
            check(helper, AbsorptionPadBlockEntity.heldPoints(player) == held[0], "switched off, it takes nothing");
            helper.setBlock(lever, Blocks.AIR);
        });
        helper.runAfterDelay(52, () -> {
            check(helper, !helper.getBlockState(pad).getValue(AbsorptionPadBlock.POWERED), "the signal gone, it is on again");
            check(helper, AbsorptionPadBlockEntity.heldPoints(player) < held[0], "and drains again");
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * The chair's shape, made once for each way it faces: the seat, and the back behind it - its outline up to the
     * back's crest above the block, what a player bumps into no higher than the block.
     */
    private static void chairKeepsItsShapes(GameTestHelper helper) {
        var chair = ModWoods.CHAIRS.get("oak").get();
        BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int top : new int[] {22, 16}) {
                var back = switch (facing.getOpposite()) {
                    case SOUTH -> Block.box(2, 9, 12, 14, top, 14);
                    case WEST -> Block.box(2, 9, 2, 4, top, 14);
                    case EAST -> Block.box(12, 9, 2, 14, top, 14);
                    default -> Block.box(2, 9, 2, 14, top, 4);
                };
                var expected = Shapes.or(Block.box(2, 0, 2, 14, 9, 14), back);
                var state = chair.defaultBlockState().setValue(ChairBlock.FACING, facing);
                var shape = top == 22 ? state.getShape(helper.getLevel(), at) : state.getCollisionShape(helper.getLevel(), at);
                check(helper, !Shapes.joinIsNotEmpty(shape, expected, BooleanOp.NOT_SAME),
                        "facing " + facing + ", the back is behind the seat, up to " + top);
            }
        }
        helper.succeed();
    }

    /**
     * Storage contents read in (a barrel's save, a dropped whole barrel, another player's item) are held to what a
     * barrel can be: no slot past the biggest barrel's, and no slot named twice (the first kept).
     */
    private static void storageContentsHeldToABarrel(GameTestHelper helper) {
        var ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag stone = ItemStack.CODEC.encodeStart(ops, new ItemStack(Items.STONE)).getOrThrow();
        Function<int[], Boolean> reads = slots -> {
            ListTag list = new ListTag();
            for (int slot : slots) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("slot", slot);
                entry.put("item", stone.copy());
                entry.putInt("count", 5);
                list.add(entry);
            }
            return StorageContents.CODEC.parse(ops, list).isSuccess();
        };
        int last = StorageTier.MOST_SLOTS - 1;
        check(helper, reads.apply(new int[] {0, 1, last}), "slots of a Velarium barrel read in");
        check(helper, !reads.apply(new int[] {0, last + 1}), "one past the biggest barrel does not");
        ListTag twice = new ListTag();
        for (int count : new int[] {5, 7}) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", 3);
            entry.put("item", stone.copy());
            entry.putInt("count", count);
            twice.add(entry);
        }
        var once = StorageContents.CODEC.parse(ops, twice).result();
        check(helper, once.isPresent() && once.get().entries().size() == 1 && once.get().entries().getFirst().count() == 5,
                "a slot named twice is named once, the first kept (r15: refused, the whole contents were lost)");
        helper.succeed();
    }

    /** Burning cooked food to char is listed in the furnace's book with the other things, not with the food. */
    private static void charringIsNotFood(GameTestHelper helper) {
        var key = ResourceKey.create(Registries.RECIPE, Nookworks.id("charred_lump_from_smelting"));
        var recipe = helper.getLevel().getServer().getRecipeManager().byKey(key).orElseThrow().value();
        check(helper, recipe instanceof AbstractCookingRecipe cooking
                && cooking.category() == CookingBookCategory.MISC, "in the misc tab");
        helper.succeed();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos, Direction face) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), face, helper.absolutePos(pos), false);
    }

    private static List<ItemEntity> itemsAround(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(pos)).inflate(3), e -> true);
    }

    /** A lit Spawner Link Sender comes back from a restart with no lease (they are not saved): it goes dark, no crash. */
    private static void senderGoesDarkAfterARestart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.SPAWNER_LINK_SENDER.get().defaultBlockState().setValue(BlockStateProperties.LIT, true));
        BlockState lit = helper.getBlockState(pos);
        lit.tick(helper.getLevel(), helper.absolutePos(pos), helper.getLevel().getRandom()); // threw once: an immutable map
        check(helper, !helper.getBlockState(pos).getValue(BlockStateProperties.LIT), "it goes dark until a controller runs through it");
        helper.succeed();
    }

    /** Ctrl+Q on a barrel slot of ten stacks throws one of them, not all ten as vanilla's loop would. */
    private static void barrelCtrlQThrowsOneStack(GameTestHelper helper) {
        StorageBlockEntity storage = StorageTests.placeStorage(helper, ModBlocks.IRON_STORAGE.get());
        storage.inventory().set(0, ItemResource.of(Items.COBBLESTONE), 640);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        StorageMenu menu = new StorageMenu(1, player.getInventory(), storage.inventory(), storage.getBlockPos());
        menu.clicked(0, 1, ContainerInput.THROW, player);
        check(helper, storage.inventory().getAmountAsInt(0) == 576, "one stack thrown, 576 left: " + storage.inventory().getAmountAsInt(0));
        menu.clicked(0, 0, ContainerInput.THROW, player);
        check(helper, storage.inventory().getAmountAsInt(0) == 575, "Q alone throws one, as ever");
        helper.succeed();
    }

    /** An enchanted bow would burn in a Generator: a click with it opens the screen instead; coal still goes in. */
    private static void machineClickLeavesGearInHand(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = new ItemStack(Items.BOW);
        bow.set(DataComponents.CUSTOM_NAME, Component.literal("Old Faithful"));
        for (ItemStack gear : List.of(bow, new ItemStack(Items.WOODEN_SWORD))) {
            player.setItemInHand(InteractionHand.MAIN_HAND, gear);
            helper.getBlockState(pos).useItemOn(gear, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.NORTH));
            check(helper, generator.getItem(0).isEmpty() && !player.getMainHandItem().isEmpty(), gear + " stays in hand");
        }
        ItemStack coal = new ItemStack(Items.COAL, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, coal);
        helper.getBlockState(pos).useItemOn(coal, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.NORTH));
        check(helper, generator.getItem(0).is(Items.COAL) && generator.getItem(0).getCount() == 5, "coal is loaded by a click");
        helper.succeed();
    }

    /**
     * A wooden barrel holding a bundle becomes a Storage Barrel: the bundle, which a barrel slot cannot hold, lands on
     * the floor rather than in nothing, and the name the barrel was given stays on it.
     */
    private static void upgradeKeepsNameAndDropsTheRest(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, Blocks.BARREL);
        var barrel = helper.getBlockEntity(pos, BarrelBlockEntity.class);
        // named as an anvil-named barrel is set down (applying components also sets the contents: first, then)
        barrel.applyComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, Component.literal("Stone")).build(),
                DataComponentPatch.EMPTY);
        barrel.setItem(0, new ItemStack(Items.COBBLESTONE, 40));
        barrel.setItem(4, new ItemStack(Items.BUNDLE));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, StorageUpgradeItem.upgrade(helper.getLevel(), helper.absolutePos(pos), ModBlocks.IRON_STORAGE.get(), player),
                "the upgrade goes on");
        var storage = helper.getBlockEntity(pos, StorageBlockEntity.class);
        check(helper, storage.inventory().getAmountAsInt(0) == 40, "the cobblestone is kept in its slot");
        check(helper, storage.customName() != null && storage.customName().getString().equals("Stone"), "and the name");
        check(helper, storage.getDisplayName().getString().equals("Stone"), "which its screen shows");
        check(helper, itemsAround(helper, pos).stream().anyMatch(e -> e.getItem().is(Items.BUNDLE)), "the bundle is on the floor");
        helper.succeed();
    }

    /**
     * A Sealed Retort broken with mercury and water in it drops an item that holds them, as a Copper Tank does, and put
     * down again it has them back; the Generator keeps its liquid fuel the same way.
     */
    private static void machinesKeepTheirLiquids(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        ItemStack kept = new ItemStack(ModBlocks.RETORT.get());
        kept.set(ModDataComponents.HELD_FLUIDS.get(), List.of(
                SimpleFluidContent.copyOf(new FluidStack(MercuryFluids.SOURCE.get(), 2_000)),
                SimpleFluidContent.copyOf(new FluidStack(Fluids.WATER, 3_000))));
        retort.applyComponentsFromItemStack(kept);
        check(helper, retort.mercury() == 2_000 && retort.waterStored() == 3_000, "set down, it has them back: " + retort.mercury() + ", " + retort.waterStored());
        level.destroyBlock(helper.absolutePos(pos), true);
        ItemStack dropped = itemsAround(helper, pos).stream().map(ItemEntity::getItem).filter(s -> s.is(ModItems.RETORT.get()))
                .findFirst().orElse(ItemStack.EMPTY);
        var held = dropped.getOrDefault(ModDataComponents.HELD_FLUIDS.get(), List.<SimpleFluidContent>of());
        check(helper, held.stream().anyMatch(f -> f.getFluid() == MercuryFluids.SOURCE.get() && f.getAmount() == 2_000)
                && held.stream().anyMatch(f -> f.getFluid() == Fluids.WATER && f.getAmount() == 3_000), "broken, its item keeps both: " + held);

        BlockPos gen = new BlockPos(4, 2, 2);
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(gen, GeneratorBlockEntity.class);
        check(helper, Move.insert(generator.fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), 1_500) == 1_500,
                "oil piped into the generator");
        var components = generator.collectComponents().get(ModDataComponents.HELD_FLUIDS.get());
        check(helper, components != null && components.size() == 1 && components.getFirst().getAmount() == 1_500, "its item would keep the oil");
        check(helper, new ItemStack(ModBlocks.GENERATOR.get()).get(ModDataComponents.HELD_FLUIDS.get()) == null, "a new one holds nothing");
        helper.succeed();
    }

    /** Experience that no longer fits in an Electric Furnace comes out on top of it as orbs, not into nothing. */
    private static void fullMachinesGiveOffExperience(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.ELECTRIC_FURNACE.get());
        var furnace = helper.getBlockEntity(pos, ElectricFurnaceBlockEntity.class);
        furnace.keepExperience(45); // 900 mB of the 1,000 it holds
        check(helper, furnace.experienceStored() == 900 && helper.getEntities(EntityTypes.EXPERIENCE_ORB).isEmpty(), "it keeps what fits");
        furnace.keepExperience(15); // 300 mB more: 100 fit, 10 points are over
        check(helper, furnace.experienceStored() == 1_000, "full");
        int points = helper.getEntities(EntityTypes.EXPERIENCE_ORB).stream()
                .mapToInt(o -> o.getValue() * ((ExperienceOrbAccessor) o).nookworks$getCount()).sum();
        check(helper, points == 10, "the rest came out as orbs: " + points);
        helper.succeed();
    }

    /** A Storage Barrel breaks like a wooden barrel: to a bare hand it drops itself, not nothing. */
    private static void storageBarrelDropsToABareHand(GameTestHelper helper) {
        StorageBlockEntity storage = StorageTests.placeStorage(helper, ModBlocks.GOLD_STORAGE.get());
        BlockState state = storage.getBlockState();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, player.hasCorrectToolForDrops(state), "no tool is needed");
        var drops = Block.getDrops(state, helper.getLevel(), storage.getBlockPos(), storage, player, ItemStack.EMPTY);
        check(helper, drops.stream().anyMatch(s -> s.is(ModItems.GOLD_STORAGE.get())), "the barrel drops: " + drops);
        helper.succeed();
    }

    /**
     * Knocked off its stand, an Oil Lamp drops, keeping the oil in it. It burns a bottle in ten steps, so one blown out
     * and lit again burns on from about where it was: a tick already due takes one step, not a bottle.
     */
    private static void oilLampKeepsItsOil(GameTestHelper helper) {
        BlockPos stand = new BlockPos(2, 1, 2);
        BlockPos lamp = stand.above();
        helper.setBlock(stand, Blocks.STONE);
        helper.setBlock(lamp, ModBlocks.OIL_LAMP.get().defaultBlockState().setValue(OilLampBlock.OIL, 3));
        helper.setBlock(stand, Blocks.AIR);
        helper.assertBlockNotPresent(ModBlocks.OIL_LAMP.get(), lamp);
        ItemStack dropped = itemsAround(helper, lamp).stream().map(ItemEntity::getItem).filter(s -> s.is(ModItems.OIL_LAMP.get()))
                .findFirst().orElse(ItemStack.EMPTY);
        var state = dropped.get(DataComponents.BLOCK_STATE);
        check(helper, state != null && Integer.valueOf(3).equals(state.get(OilLampBlock.OIL)), "the dish drops with its oil: " + dropped);

        helper.setBlock(stand, Blocks.STONE);
        helper.setBlock(lamp, ModBlocks.OIL_LAMP.get().defaultBlockState().setValue(OilLampBlock.OIL, 2).setValue(OilLampBlock.LIT, true));
        helper.getBlockState(lamp).tick(helper.getLevel(), helper.absolutePos(lamp), helper.getLevel().getRandom());
        check(helper, helper.getBlockState(lamp).getValue(OilLampBlock.OIL) == 2 && helper.getBlockState(lamp).getValue(OilLampBlock.BURNT) == 1,
                "a tick burns one step of the bottle");
        helper.setBlock(lamp, helper.getBlockState(lamp).setValue(OilLampBlock.BURNT, OilLampBlock.STEPS - 1));
        helper.getBlockState(lamp).tick(helper.getLevel(), helper.absolutePos(lamp), helper.getLevel().getRandom());
        check(helper, helper.getBlockState(lamp).getValue(OilLampBlock.OIL) == 1 && helper.getBlockState(lamp).getValue(OilLampBlock.BURNT) == 0,
                "the last step takes the bottle");
        helper.setBlock(lamp, helper.getBlockState(lamp).setValue(OilLampBlock.LIT, false));
        helper.getBlockState(lamp).tick(helper.getLevel(), helper.absolutePos(lamp), helper.getLevel().getRandom());
        check(helper, helper.getBlockState(lamp).getValue(OilLampBlock.OIL) == 1, "blown out, it burns nothing");
        helper.succeed();
    }

    /** A Copper Tank pours down into what is under it; what is under it cannot pour back up into its bottom. */
    private static void copperTankPoursDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos upper = new BlockPos(2, 3, 2);
        BlockPos lower = upper.below();
        helper.setBlock(lower, ModBlocks.COPPER_TANK.get());
        helper.setBlock(upper, ModBlocks.COPPER_TANK.get());
        var top = helper.getBlockEntity(upper, CopperTankBlockEntity.class);
        var bottom = helper.getBlockEntity(lower, CopperTankBlockEntity.class);
        Move.insert(top.fluidHandler(), FluidResource.of(Fluids.WATER), 3_000);
        CopperTankBlockEntity.serverTick(level, helper.absolutePos(upper), helper.getBlockState(upper), top);
        check(helper, bottom.amount() == CopperTankBlockEntity.POUR && top.amount() == 3_000 - CopperTankBlockEntity.POUR,
                "a pipe's worth goes down: " + top.amount() + " / " + bottom.amount());
        check(helper, Move.insert(top.handlerFor(Direction.DOWN), FluidResource.of(Fluids.WATER), 100) == 0,
                "nothing goes in by the bottom");
        check(helper, Move.extract(top.handlerFor(Direction.DOWN), FluidResource.of(Fluids.WATER), 100) == 100,
                "but it may be drawn from there");
        helper.succeed();
    }

    /** The Wrench turns what faces some way, and leaves a fence's or a pane's joins alone. */
    private static void wrenchLeavesJoinsAlone(GameTestHelper helper) {
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState().setValue(BlockStateProperties.NORTH, true);
        BlockState pane = Blocks.GLASS_PANE.defaultBlockState().setValue(BlockStateProperties.EAST, true);
        check(helper, WrenchItem.turn(fence, false) == fence, "a fence is not turned");
        check(helper, WrenchItem.turn(pane, false) == pane, "nor a pane");
        BlockState stairs = Blocks.OAK_STAIRS.defaultBlockState();
        check(helper, WrenchItem.turn(stairs, false) != stairs, "stairs are");
        BlockState dripstone = Blocks.POINTED_DRIPSTONE.defaultBlockState();
        check(helper, WrenchItem.turn(dripstone, false) == dripstone, "dripstone is left pointing where it points");
        helper.succeed();
    }

    /** A rope let down into water hangs in it, waterlogged; reeled back up, the water is still there. */
    private static void ropeHangsInWater(GameTestHelper helper) {
        BlockPos ceiling = new BlockPos(3, 5, 3);
        helper.setBlock(ceiling, Blocks.STONE);
        helper.setBlock(ceiling.below(), ModBlocks.ROPE.get());
        helper.setBlock(ceiling.below(2), Blocks.WATER);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ROPE.get()));
        helper.useBlock(ceiling.below(), player);
        BlockState rope = helper.getBlockState(ceiling.below(2));
        check(helper, rope.is(ModBlocks.ROPE.get()) && rope.getValue(RopeBlock.WATERLOGGED), "the rope goes down into the water, waterlogged: " + rope);
        check(helper, helper.getLevel().getFluidState(helper.absolutePos(ceiling.below(2))).is(FluidTags.WATER), "the water stays");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.useBlock(ceiling.below(), player);
        check(helper, helper.getBlockState(ceiling.below(2)).is(Blocks.WATER), "reeled up, the water is left: " + helper.getBlockState(ceiling.below(2)));
        helper.succeed();
    }

    /** Flying over an elevator, gliding over it or passing through as a spectator goes nowhere. */
    private static void elevatorIgnoresFliers(GameTestHelper helper) {
        BlockPos lower = new BlockPos(3, 1, 3);
        helper.setBlock(lower, ModBlocks.ELEVATOR.get());
        helper.setBlock(new BlockPos(3, 4, 3), ModBlocks.ELEVATOR.get());
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.CREATIVE);
        Vec3 feet = helper.absoluteVec(new Vec3(3.5, 2.0, 3.5));
        player.connection.teleport(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        player.getAbilities().flying = true;
        check(helper, !Elevators.tryMove(player, true), "not while flying");
        player.getAbilities().flying = false;
        player.setGameMode(GameType.SPECTATOR);
        check(helper, !Elevators.tryMove(player, true), "nor as a spectator");
        player.setGameMode(GameType.SURVIVAL);
        player.connection.teleport(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        check(helper, Elevators.tryMove(player, true), "standing on it, up it goes");
        helper.succeed();
    }

    /** Corn is sown in tilled Nourished Soil, as any crop is; untilled, it is not. */
    private static void cornOnNourishedSoil(GameTestHelper helper) {
        BlockPos soil = new BlockPos(2, 1, 2);
        BlockState corn = ModKitchen.CORN_CROP.get().defaultBlockState();
        helper.setBlock(soil, ModBlocks.FERTILE_SOIL.get().defaultBlockState().setValue(FertileSoilBlock.TILLED, true));
        check(helper, corn.canSurvive(helper.getLevel(), helper.absolutePos(soil.above())), "corn takes to tilled Nourished Soil");
        helper.setBlock(soil, ModBlocks.FERTILE_SOIL.get().defaultBlockState().setValue(FertileSoilBlock.TILLED, false));
        check(helper, !corn.canSurvive(helper.getLevel(), helper.absolutePos(soil.above())), "not to it untilled");
        helper.setBlock(soil, Blocks.FARMLAND);
        check(helper, corn.canSurvive(helper.getLevel(), helper.absolutePos(soil.above())), "and to farmland, as before");
        helper.succeed();
    }

    /** Kelp clicked on dry stone cannot be placed, and is not eaten instead; in the air it is food, as set up. */
    private static void kelpOnABlockIsNotEaten(GameTestHelper helper) {
        BlockPos stone = new BlockPos(2, 1, 2);
        helper.setBlock(stone, Blocks.STONE);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.getFoodData().setFoodLevel(10);
        ItemStack kelp = new ItemStack(Items.KELP, 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, kelp);
        BlockHitResult top = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(stone)).add(0, 0.5, 0), Direction.UP,
                helper.absolutePos(stone), false);
        InteractionResult result = kelp.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, top));
        check(helper, !result.consumesAction() && !player.isUsingItem() && kelp.getCount() == 3, "not placed, not eaten: " + result);
        check(helper, kelp.has(DataComponents.CONSUMABLE) == NookworksStartupConfig.EDIBLE_RAW_FOODS.getAsBoolean(),
                "kelp is still food, to eat in the air");
        helper.succeed();
    }

    /** A player who has been to a village leaves a trader undecided; the first newcomer to open his stall decides. */
    private static void villageMapWaitsForANewcomer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var trader = helper.spawnWithNoFreeWill(EntityTypes.WANDERING_TRADER, new BlockPos(1, 2, 1));
        ServerPlayer traveller = MockPlayers.inLevel(helper);
        var visited = level.getServer().getAdvancements().get(VillageMap.VISITED_VILLAGE);
        for (String criterion : traveller.getAdvancements().getOrStartProgress(visited).getRemainingCriteria()) {
            traveller.getAdvancements().award(visited, criterion);
        }
        check(helper, VillageMap.hasBeenInAVillage(traveller), "the traveller has been in a village");
        check(helper, VillageMap.offerTo(trader, traveller, 0.0) == null, "no map for him");
        check(helper, !trader.getPersistentData().getBooleanOr("nookworks:village_map_decided", false), "and the trader is still undecided");
        ServerPlayer newcomer = MockPlayers.inLevel(helper);
        VillageMap.offerTo(trader, newcomer, 0.99);
        check(helper, trader.getPersistentData().getBooleanOr("nookworks:village_map_decided", false), "a newcomer's visit decides it");
        helper.succeed();
    }

    /** A Crusher waiting for room forgets what it had done once its input is taken away, so the next thing starts afresh. */
    private static void crusherForgetsARemovedInput(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        helper.setBlock(pos, ModBlocks.CRUSHER.get());
        var crusher = helper.getBlockEntity(pos, CrusherBlockEntity.class);
        crusher.energy().set(CrusherBlockEntity.CAPACITY);
        crusher.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 4));
        for (int i = 0; i < 30; i++) {
            CrusherBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), crusher);
        }
        check(helper, crusher.guiProgress() == 30, "half through the first: " + crusher.guiProgress());
        crusher.setItem(CrusherBlockEntity.OUTPUT, new ItemStack(Items.GRAVEL)); // nowhere for it to go
        CrusherBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), crusher);
        check(helper, crusher.guiProgress() == 30, "waiting for room, it keeps what it had done");
        crusher.setItem(CrusherBlockEntity.INPUT, ItemStack.EMPTY);
        CrusherBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), crusher);
        check(helper, crusher.guiProgress() == 0, "the input taken away, the work goes with it");
        helper.succeed();
    }

    /** With nowhere below for results to go, the Sifter fills all nine of its slots before it stops. */
    private static void sifterFillsItsBuffer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos.below(), Blocks.CHEST);
        var chest = helper.getBlockEntity(pos.below(), ChestBlockEntity.class);
        for (int i = 0; i < chest.getContainerSize(); i++) {
            chest.setItem(i, new ItemStack(Items.DIRT, 64));
        }
        helper.setBlock(pos, ModBlocks.SIFTER.get());
        var sifter = helper.getBlockEntity(pos, SifterBlockEntity.class);
        sifter.setItem(SifterBlockEntity.INPUT, new ItemStack(Items.GRAVEL, 64));
        for (int i = 0; i < 64 * (NookworksConfig.SIFTER_TICKS.getAsInt() + 2); i++) {
            SifterBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), sifter);
        }
        int used = 0;
        for (int slot = 1; slot <= 9; slot++) {
            used += sifter.getItem(slot).isEmpty() ? 0 : 1;
        }
        int left = sifter.getItem(SifterBlockEntity.INPUT).getCount();
        check(helper, left < 60, "it went on sifting with results waiting: " + left + " left");
        check(helper, left == 0 || used == 9, "and stopped only with the input gone or all nine slots taken: " + used + " used, " + left + " left");
        helper.succeed();
    }

    /** A full Generator with nothing drawing on it banks its fire: the front goes dark. Drawn on again, it lights. */
    private static void generatorBanksItsFire(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        var generator = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 2));
        helper.startSequence()
                .thenExecuteAfter(3, () -> {
                    check(helper, helper.getBlockState(pos).getValue(BlockStateProperties.LIT), "burning, it is lit");
                    generator.energy().set(GeneratorBlockEntity.CAPACITY);
                })
                .thenExecuteAfter(60, () -> {
                    check(helper, !helper.getBlockState(pos).getValue(BlockStateProperties.LIT), "full and idle, the fire is banked and the front dark");
                    check(helper, generator.guiProgress() == 0, "and the screen shows no flame");
                    generator.energy().set(0);
                })
                .thenExecuteAfter(2, () -> check(helper, helper.getBlockState(pos).getValue(BlockStateProperties.LIT), "drawn on again, it lights at once"))
                .thenSucceed();
    }

    /**
     * A Generator or Diesel Generator with half what it makes drawn a tick burns every other tick, its store full in
     * between: its screen says Running all through, as its fire stays lit, not Running and Full by turns. With nothing
     * drawing on it, once its fire is banked, it says Full.
     */
    private static void generatorSteadyUnderALightLoad(GameTestHelper helper) {
        BlockPos coal = new BlockPos(1, 2, 2);
        BlockPos diesel = new BlockPos(3, 2, 2);
        helper.setBlock(coal, ModBlocks.GENERATOR.get());
        helper.setBlock(diesel, ModBlocks.DIESEL_GENERATOR.get());
        var generator = helper.getBlockEntity(coal, GeneratorBlockEntity.class);
        var dieselGenerator = helper.getBlockEntity(diesel, DieselGeneratorBlockEntity.class);
        generator.setItem(0, new ItemStack(Items.COAL, 2));
        check(helper, dieselGenerator.pour(4 * DieselGeneratorBlockEntity.GULP), "biodiesel pours in");
        int made = MachineTuning.generated(GeneratorBlockEntity.FE_PER_TICK);
        int madeDiesel = MachineTuning.generated(DieselGeneratorBlockEntity.FE_PER_TICK);
        generator.energy().set(GeneratorBlockEntity.CAPACITY - made);
        dieselGenerator.energy().set(DieselGeneratorBlockEntity.CAPACITY - madeDiesel);
        int[] ticks = {0};
        boolean[] drawn = {true};
        Set<MachineStatus> seen = EnumSet.noneOf(MachineStatus.class);
        Set<MachineStatus> seenDiesel = EnumSet.noneOf(MachineStatus.class);
        helper.onEachTick(() -> {
            if (!drawn[0]) {
                return;
            }
            generator.energy().set(generator.energyStored() - made / 2);
            dieselGenerator.energy().set(dieselGenerator.energyStored() - madeDiesel / 2);
            if (++ticks[0] > 3) {
                seen.add(generator.guiStatus());
                seenDiesel.add(dieselGenerator.guiStatus());
            }
        });
        helper.startSequence()
                .thenExecuteAfter(60, () -> {
                    check(helper, seen.equals(EnumSet.of(MachineStatus.RUNNING)), "the Generator says Running all through, not " + seen);
                    check(helper, seenDiesel.equals(EnumSet.of(MachineStatus.RUNNING)),
                            "the Diesel Generator says Running all through, not " + seenDiesel);
                    drawn[0] = false;
                })
                .thenExecuteAfter(50, () -> {
                    check(helper, generator.guiStatus() == MachineStatus.FULL, "nothing drawing, the Generator says Full: " + generator.guiStatus());
                    check(helper, dieselGenerator.guiStatus() == MachineStatus.FULL,
                            "and the Diesel Generator: " + dieselGenerator.guiStatus());
                })
                .thenSucceed();
    }

    /**
     * The tag picker: a sample set with an iron ingot in hand (which stays there), its tags listed with the common ones
     * first, one ticked and registered - an entry of its own that catches iron and not diamonds; the same tag twice is
     * one entry.
     */
    private static void sorterRegistersTagsPicked(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.PRECISION_SORTER.get());
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        ServerPlayer player = MockPlayers.inLevel(helper);
        var menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        check(helper, menu.picking() && !menu.getSlot(0).isActive() && menu.getSlot(SorterMenu.SAMPLE_SLOT).isActive(),
                "the picker takes the filter's place");
        menu.setCarried(new ItemStack(Items.IRON_INGOT, 5));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        check(helper, menu.sample().is(Items.IRON_INGOT) && menu.getCarried().getCount() == 5, "a sample is set; the ingots stay in hand");
        var choices = menu.choices();
        int iron = choices.indexOf(Tags.Items.INGOTS_IRON);
        check(helper, iron >= 0, "c:ingots/iron is offered: " + choices);
        check(helper, choices.getFirst().location().getNamespace().equals("c"), "the common tags come first: " + choices);
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE + iron);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_TAGS);
        check(helper, !menu.picking(), "Register closes the picker");
        ItemStack entry = sorter.filter().getItem(0);
        check(helper, Tags.Items.INGOTS_IRON.equals(SorterTags.of(entry)),
                "the tag is an entry: " + entry);
        check(helper, sorter.isRegistered(new ItemStack(Items.IRON_INGOT)) && !sorter.isRegistered(new ItemStack(Items.DIAMOND)),
                "it catches iron, not diamonds");
        menu.clickMenuButton(player, SorterMenu.BUTTON_PICKER_OPEN);
        menu.setCarried(new ItemStack(Items.IRON_INGOT));
        menu.clicked(SorterMenu.SAMPLE_SLOT, 0, ContainerInput.PICKUP, player);
        menu.clickMenuButton(player, SorterMenu.BUTTON_CHOOSE + iron);
        menu.clickMenuButton(player, SorterMenu.BUTTON_REGISTER_TAGS);
        check(helper, sorter.filter().getItem(1).isEmpty(), "the same tag twice is one entry");
        helper.succeed();
    }

    /**
     * A 0.2.0 Precision Sorter with its Tags switch on comes over with each entry turned into the entry for the one tag
     * it was meant for - an iron ingot into c:ingots/iron, a plank into a tag of planks - and the switch gone.
     */
    private static void oldTagSortersComeOver(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.PRECISION_SORTER.get());
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        sorter.settings().toggle(SorterSettings.MATCH_TAGS);
        sorter.filter().setItem(0, new ItemStack(Items.OAK_PLANKS));
        sorter.filter().setItem(1, new ItemStack(Items.IRON_INGOT));
        var registries = helper.getLevel().registryAccess();
        var saved = sorter.saveWithoutMetadata(registries);
        try (var reporter = new ProblemReporter.ScopedCollector(sorter.problemPath(), LogUtils.getLogger())) {
            sorter.loadWithComponents(TagValueInput.create(reporter, registries, saved));
        }
        check(helper, !sorter.settings().get(SorterSettings.MATCH_TAGS), "the switch is gone");
        check(helper, Tags.Items.INGOTS_IRON.equals(SorterTags.of(sorter.filter().getItem(1))),
                "the iron ingot is c:ingots/iron's entry: " + sorter.filter().getItem(1));
        check(helper, SorterTags.of(sorter.filter().getItem(0)) != null && sorter.isRegistered(new ItemStack(Items.SPRUCE_PLANKS)),
                "the plank still sorts every plank: " + SorterTags.of(sorter.filter().getItem(0)));
        helper.succeed();
    }

    /**
     * A spawner costs 20 FE a tick at the defaults and as much more as it is asked to spawn - the same per mob - twice
     * for ignoring the rules, twice again for needing nobody near; off it costs nothing. The receiver holds enough for
     * the strongest settings.
     */
    private static void spawnerSettingsCost(GameTestHelper helper) {
        var d = SpawnerSettings.DEFAULT;
        check(helper, d.energyPerTick() == 20, "the defaults: " + d.energyPerTick());
        check(helper, d.withInterval(100).withCount(8).energyPerTick() == 80, "twice as often, twice as many: four times");
        var strongest = d.withInterval(20).withCount(16).withAnyLight(true).withNeedsPlayer(false);
        check(helper, strongest.energyPerTick() == 3_200, "the strongest: " + strongest.energyPerTick());
        check(helper, d.withInterval(2_400).withCount(1).energyPerTick() == 20, "never under the base while it runs");
        check(helper, d.withEnabled(false).energyPerTick() == 0, "off costs nothing");
        check(helper, SpawnerLinkReceiverBlockEntity.CAPACITY
                >= 6 * strongest.energyPerTick() * SpawnerControllerBlockEntity.PERIOD,
                "a receiver can pay a period of six spawners at the strongest");
        helper.succeed();
    }

    /**
     * A sorter with a Velarium gear and nothing at its outlet throws onto the ground a stack at a hopper's beat, not a
     * stack a tick: after 40 ticks, five throws, not forty.
     */
    private static void fastSorterThrowsAtAHoppersBeat(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.below(), Blocks.STONE);
        helper.setBlock(pos, ModBlocks.ITEM_SORTER.get());
        var sorter = helper.getBlockEntity(pos, SorterBlockEntity.class);
        sorter.gearSlot().setItem(0, new ItemStack(ModItems.VELARIUM_SORTER_GEAR.get()));
        sorter.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        var extra = new ItemStack(Items.COBBLESTONE, 64);
        helper.runAfterDelay(40, () -> {
            int thrown = helper.getLevel().getEntities(EntityTypes.ITEM, new AABB(helper.absolutePos(pos)).inflate(4), e -> true).size();
            check(helper, thrown >= 1 && thrown <= 6, "a throw a hopper beat: " + thrown + " item entities");
            helper.succeed();
        });
        // keep it fed, so a stack a tick would have thrown one every tick
        for (int t = 1; t < 40; t++) {
            helper.runAfterDelay(t, () -> {
                if (sorter.getItem(0).isEmpty()) {
                    sorter.setItem(0, extra.copy());
                }
            });
        }
    }

    /** A label set in the middle of a chest's front goes under the latch (7..11 px up), not across it; on a barrel, in the middle. */
    private static void labelUnderTheLatch(GameTestHelper helper) {
        BlockPos chest = new BlockPos(2, 2, 2);
        helper.setBlock(chest, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        BlockPos at = helper.absolutePos(chest);
        ItemStack shows = new ItemStack(Items.IRON_INGOT);
        var label = LabelItem.place(helper.getLevel(), at, Direction.NORTH, Vec3.atCenterOf(at).add(0, 0, -0.44), shows, true);
        check(helper, label != null, "a label goes on a chest's front");
        double top = 8 + label.up() + LabelEntity.HEIGHT * 8;
        check(helper, top <= 7.001, "its top is at or under the latch's foot (7 px): " + top);
        BlockPos barrel = new BlockPos(4, 2, 2);
        helper.setBlock(barrel, Blocks.BARREL.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        BlockPos at2 = helper.absolutePos(barrel);
        var onBarrel = LabelItem.place(helper.getLevel(), at2, Direction.NORTH, Vec3.atCenterOf(at2).add(0, 0, -0.5), shows, true);
        check(helper, onBarrel != null && onBarrel.up() == 0, "on a barrel it goes in the middle");
        helper.succeed();
    }

    /**
     * An empty bucket on an Experience Tank fills from it; one on an empty tank does nothing, and the bucket stays as
     * it was. The tap turns only when the tap is clicked.
     */
    private static void tankClicksEndAtTheTank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.EXPERIENCE_TANK.get().defaultBlockState().setValue(ExperienceTankBlock.FACING, Direction.NORTH));
        var tank = helper.getBlockEntity(pos, ExperienceTankBlockEntity.class);
        tank.fill(1_500);
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setGameMode(GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        InteractionResult first = helper.getBlockState(pos).useItemOn(bucket, level, player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.EAST));
        check(helper, first.consumesAction() && player.getMainHandItem().is(ModItems.EXPERIENCE_BUCKET.get()) && tank.amount() == 500,
                "a bucketful out: " + first + ", " + player.getMainHandItem() + ", " + tank.amount());
        ItemStack another = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, another);
        helper.getBlockState(pos).useItemOn(another, level, player, InteractionHand.MAIN_HAND, hit(helper, pos, Direction.EAST));
        check(helper, player.getMainHandItem().is(Items.BUCKET) && tank.amount() == 500, "too little for a bucket: nothing moves");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos at = helper.absolutePos(pos);
        BlockHitResult side = new BlockHitResult(new Vec3(at.getX() + 1.0, at.getY() + 0.5, at.getZ() + 0.5), Direction.EAST, at, false);
        helper.getBlockState(pos).useWithoutItem(level, player, side);
        check(helper, !helper.getBlockState(pos).getValue(ExperienceTankBlock.OPEN), "a hand on the side leaves the tap shut");
        Vec3 onTap = new Vec3(at.getX() + 0.5, at.getY() + 5.0 / 16, at.getZ() - 2.0 / 16);
        check(helper, ExperienceTankBlock.onTap(helper.getBlockState(pos), at, onTap), "the spout is on the tap");
        helper.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(onTap, Direction.NORTH, at, false));
        check(helper, helper.getBlockState(pos).getValue(ExperienceTankBlock.OPEN), "a hand on the tap opens it");
        helper.succeed();
    }
}
