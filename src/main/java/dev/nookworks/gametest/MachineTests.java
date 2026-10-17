package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.StorageTests.buildControl;
import static dev.nookworks.gametest.StorageTests.pigsAround;
import static dev.nookworks.gametest.StorageTests.storageTotal;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.block.GeneratorBlock;
import dev.nookworks.block.LaserBlock;
import dev.nookworks.block.PipeSides;
import dev.nookworks.block.PlanterBlock;
import dev.nookworks.block.RepairBoxBlock;
import dev.nookworks.block.SorterBlock;
import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.MagnetChestBlockEntity;
import dev.nookworks.experience.AbsorptionPadBlock;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlock;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.feature.Fireproofing;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.machine.LaserBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.PlanterBlockEntity;
import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.machine.StoneMakerBlockEntity;
import dev.nookworks.mercury.MercuryNetwork;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.registry.ModBarter;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.spawner.SpawnerSettings;
import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** The powered machines, the Laser and Planter, pipe and cable sides, and experience pipes. */
final class MachineTests {
    static void register() {
        test("generator_burns_fuel_into_energy", MachineTests::generatorBurns);
        test("generator_faces_the_player", MachineTests::generatorFacesThePlayer);
        test("fronted_cubes_face_the_player", MachineTests::frontedCubesFaceThePlayer);
        test("cables_connect_to_cables_and_machines", MachineTests::cablesConnect);
        test("generator_powers_crusher_over_cables", MachineTests::generatorPowersCrusher);
        test("crusher_powders_sulfur", MachineTests::crusherPowdersSulfur);
        test("metal_only_grinds_for_other_mods", MachineTests::metalOnlyGrindsForOtherMods);
        test("wood_grinds_but_fungus_does_not", MachineTests::woodGrindsButFungusDoesNot);
        test("stone_maker_fills_chest_below", MachineTests::stoneMakerFillsChest);
        test("stone_maker_idles_without_energy", MachineTests::stoneMakerIdles);
        test("machines_reject_wrong_items", MachineTests::machinesRejectWrongItems);
        test("energy_inserted_into_cable_reaches_machine", MachineTests::cableCapabilityForwards);
        test("one_generator_runs_two_machines", MachineTests::generatorSharesEnergy);
        test("laser_fells_whole_tree", MachineTests::laserFellsTree);
        // about 150 ticks; the time limit well past it, as the felling shares the server with the tests beside it (it
        // once ran out at 280 in a full run on a busy machine)
        test("laser_fells_a_tree_on_a_generators_power", 600, MachineTests::laserFellsOnAGeneratorsPower);
        test("laser_hurts_mobs_in_beam", MachineTests::laserHurtsMobs);
        test("energy_moves_what_the_giver_gives", MachineTests::energyMovesWhatTheGiverGives);
        test("laser_stands_by_for_what_it_cannot_hurt", MachineTests::laserStandsByForWhatItCannotHurt);
        test("laser_stops_on_redstone", MachineTests::laserStopsOnRedstone);
        test("laser_spares_coated_wood", MachineTests::laserSparesCoatedWood);
        test("laser_sets_uncoated_wood_alight", 400, MachineTests::laserSetsWoodAlight);
        test("planter_plants_seeds_on_farmland", MachineTests::planterPlantsSeeds);
        test("sapling_loop_delivers_to_planter", 800, MachineTests::saplingLoopFlows);
        test("sapling_loop_buffers_when_planter_full", 800, MachineTests::saplingLoopBuffers);
        test("machine_redstone_modes", MachineTests::machineRedstoneModes);
        test("machine_redstone_mode_is_saved", MachineTests::machineRedstoneModeSaved);
        test("machine_screens_fit_their_machines", MachineTests::machineScreensFit);
        test("machine_screen_sends_big_numbers", MachineTests::machineScreenBigNumbers);
        test("machine_screen_keeps_outputs_output_only", MachineTests::machineScreenOutputsOnly);
        test("repair_box_pair_screen_shows_its_whole_store", MachineTests::repairBoxScreenWholeStore);
        test("printing_press_waits_for_redstone", MachineTests::printingPressWaitsForRedstone);
        test("spawner_controller_redstone_switches_spawners_off", MachineTests::controllerRedstoneOff);
        test("spawn_egg_brewer_redstone_button", MachineTests::brewerRedstoneButton);
        test("planter_plants_tall_flowers_whole", MachineTests::planterPlantsTallFlowersWhole);
        test("absorption_pad_rises_to_its_pipes", MachineTests::absorptionPadRisesToItsPipes);
        test("experience_tank_tap_pours", MachineTests::experienceTankTapPours);
        test("wrench_disconnects_a_cable", MachineTests::wrenchDisconnectsACable);
        test("pipe_side_off_shuts_its_capability", MachineTests::pipeSideOffShutsItsCapability);
    }

    // ---- FE machines ----

    static void generatorBurns(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.GENERATOR.get());
        check(helper, helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos)) == 0,
                "a cold generator emits no light");
        GeneratorBlockEntity gen = helper.getBlockEntity(pos, GeneratorBlockEntity.class);
        gen.setItem(0, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> {
            check(helper, gen.getItem(0).isEmpty(), "the coal should be burning");
            check(helper, gen.energyStored() >= 200, "energy should build up, has " + gen.energyStored());
            check(helper, helper.getBlockState(pos).getValue(GeneratorBlock.LIT), "generator should look lit");
            check(helper, helper.getBlockState(pos).getLightEmission(helper.getLevel(), helper.absolutePos(pos))
                            == GeneratorBlock.LIGHT_LEVEL,
                    "and light a room the way a furnace does");
        });
    }

    /** The firebox looks at you when placed, and the wrench turns it. */
    static void generatorFacesThePlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var facing = GeneratorBlock.FACING;
        float[] yaws = {0, 90, 180, 270};
        Direction[] expected = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int i = 0; i < yaws.length; i++) {
            helper.setBlock(pos, Blocks.AIR);
            Player player = helper.makeMockPlayer(GameType.CREATIVE);
            player.setYRot(yaws[i]);
            ItemStack stack = new ItemStack(ModItems.GENERATOR.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos below = helper.absolutePos(pos.below());
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
            BlockState state = helper.getBlockState(pos);
            check(helper, state.is(ModBlocks.GENERATOR.get()),
                    "looking " + Direction.fromYRot(yaws[i]).getName() + ", the generator should have been placed");
            check(helper, state.getValue(facing) == expected[i],
                    "looking " + Direction.fromYRot(yaws[i]).getName() + ", the firebox should face "
                            + expected[i].getName() + ", got " + state.getValue(facing).getName());
        }
        BlockState turned = WrenchItem.turn(helper.getBlockState(pos), false);
        check(helper, turned.getValue(facing) == Direction.NORTH,
                "the wrench turns the generator clockwise, got " + turned.getValue(facing).getName());
        helper.succeed();
    }

    /**
     * Finished cubes whose south face is the front (retort firebox, mercury tank windowed front, spawner-link
     * faces) must look at the player when placed, and the wrench must turn them.
     */
    static void frontedCubesFaceThePlayer(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        var facing = BlockStateProperties.HORIZONTAL_FACING;
        record Front(Block block, Item item) {}
        Front[] fronts = {
                new Front(ModBlocks.RETORT.get(), ModItems.RETORT.get()),
                new Front(ModBlocks.MERCURY_TANK.get(), ModItems.MERCURY_TANK.get()),
                new Front(ModBlocks.SPAWNER_LINK_SENDER.get(), ModItems.SPAWNER_LINK_SENDER.get()),
                new Front(ModBlocks.SPAWNER_LINK_RECEIVER.get(), ModItems.SPAWNER_LINK_RECEIVER.get())
        };
        float[] yaws = {0, 90, 180, 270};
        Direction[] expected = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (Front front : fronts) {
            for (int i = 0; i < yaws.length; i++) {
                helper.setBlock(pos, Blocks.AIR);
                Player player = helper.makeMockPlayer(GameType.CREATIVE);
                player.setYRot(yaws[i]);
                ItemStack stack = new ItemStack(front.item());
                player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                BlockPos below = helper.absolutePos(pos.below());
                stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
                BlockState state = helper.getBlockState(pos);
                check(helper, state.is(front.block()), front.block() + " should have been placed");
                check(helper, state.getValue(facing) == expected[i],
                        front.block() + " looking " + Direction.fromYRot(yaws[i]).getName()
                                + " should face " + expected[i].getName() + ", got "
                                + state.getValue(facing).getName());
            }
            BlockState turned = WrenchItem.turn(helper.getBlockState(pos), false);
            check(helper, turned.getValue(facing) == Direction.NORTH,
                    front.block() + " should turn clockwise with the wrench");
        }
        helper.succeed();
    }

    /** Places {@code stack} at {@code pos} the way a player does: clicking the top of the block below it. */
    static void placeByHand(GameTestHelper helper, ItemStack stack, BlockPos pos) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos below = helper.absolutePos(pos.below());
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(below), Direction.UP, below, false)));
    }

    static void cablesConnect(GameTestHelper helper) {
        BlockPos gen = new BlockPos(2, 1, 3);
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        BlockPos a = gen.east();
        BlockPos b = a.east();
        placeByHand(helper, new ItemStack(ModItems.ENERGY_CABLE.get()), a);
        placeByHand(helper, new ItemStack(ModItems.ENERGY_CABLE.get()), b);

        BlockState first = helper.getBlockState(a);
        check(helper, first.is(ModBlocks.ENERGY_CABLE.get()), "cable should be placed at " + a + ", found " + first);
        check(helper, first.getValue(PipeBlock.WEST), "cable should connect to the generator");
        check(helper, first.getValue(PipeBlock.EAST), "cable should connect to the next cable");
        check(helper, !first.getValue(PipeBlock.UP), "cable must not connect to air");
        helper.succeed();
    }

    static void generatorPowersCrusher(GameTestHelper helper) {
        BlockPos gen = new BlockPos(1, 1, 3);
        BlockPos crusher = new BlockPos(5, 1, 3);
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 1, 3), ModBlocks.ENERGY_CABLE.get());
        }
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        helper.setBlock(crusher.below(), Blocks.CHEST);
        helper.getBlockEntity(gen, GeneratorBlockEntity.class).energy().set(40_000);
        helper.getBlockEntity(crusher, CrusherBlockEntity.class).setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.COBBLESTONE, 2));

        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(crusher.below(), ChestBlockEntity.class), Items.GRAVEL) >= 1,
                "gravel should arrive in the chest under the crusher"));
    }

    /** Powdering is what the Crusher is for, so the sulfur block has to actually go through it. */
    static void crusherPowdersSulfur(GameTestHelper helper) {
        BlockPos crusher = new BlockPos(3, 2, 3);
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        helper.setBlock(crusher.below(), Blocks.CHEST);
        var machine = helper.getBlockEntity(crusher, CrusherBlockEntity.class);
        machine.energy().set((int) machine.energy().getCapacityAsLong());
        machine.setItem(CrusherBlockEntity.INPUT, new ItemStack(Items.SULFUR, 1));

        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(crusher.below(), ChestBlockEntity.class), ModItems.SULFUR_DUST.get()) >= 4,
                "one sulfur block should crush into four dust"));
    }

    /**
     * The metal recipes are written against {@code #c:dusts/<metal>}, which only an industrial mod fills. On
     * their own they must not exist at all - the Crusher should refuse the ore rather than grind it to nothing,
     * because doubling metal is the mercury chain's job.
     */
    static void metalOnlyGrindsForOtherMods(GameTestHelper helper) {
        var crusher = helper.getBlockEntity(place(helper, new BlockPos(2, 2, 2), ModBlocks.CRUSHER.get()),
                CrusherBlockEntity.class);
        for (ItemStack metal : List.of(new ItemStack(Items.IRON_ORE), new ItemStack(Items.RAW_IRON),
                new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.COPPER_INGOT))) {
            check(helper, CrushingRecipe.of(metal) == null,
                    metal.getItem() + " must not be crushable with no dust tag filled");
            check(helper, !crusher.canPlaceItem(CrusherBlockEntity.INPUT, metal),
                    "and a hopper must not be able to feed it in either");
        }
        // The same tag machinery does work: our own sulfur dust is in #c:dusts/sulfur, so a recipe written
        // against that tag resolves. This is what an industrial mod's iron dust would do.
        var viaTag = new CrushingRecipe(
                new CrushingRecipe.Result.OfTag(
                        TagKey.create(Registries.ITEM,
                                Identifier.fromNamespaceAndPath("c", "dusts/sulfur"))), 2);
        check(helper, viaTag.output().is(ModItems.SULFUR_DUST.get()) && viaTag.output().getCount() == 2,
                "a tag result resolves to whatever is in the tag");
        helper.succeed();
    }

    /**
     * The wood recipes go through tags of ours that subtract the nether woods, so this checks the tags loaded
     * at all, that a plank is worth exactly the chip a quarter of a log is, and that crimson and warped are
     * left out: they are fungus, and a kraft process has no lignin to work on.
     */
    static void woodGrindsButFungusDoesNot(GameTestHelper helper) {
        var chips = ModItems.WOOD_CHIPS.get();
        for (var timber : List.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS, Items.CHERRY_PLANKS, Items.BAMBOO_PLANKS)) {
            var recipe = CrushingRecipe.of(new ItemStack(timber));
            check(helper, recipe != null && recipe.output().is(chips) && recipe.output().getCount() == 1,
                    timber + " should grind into one wood chip");
        }
        for (var fungus : List.of(Items.CRIMSON_PLANKS, Items.WARPED_PLANKS, Items.CRIMSON_STEM, Items.WARPED_STEM)) {
            check(helper, CrushingRecipe.of(new ItemStack(fungus)) == null,
                    fungus + " is fungus and must not pulp");
        }
        var log = CrushingRecipe.of(new ItemStack(Items.OAK_LOG));
        check(helper, log != null && log.output().getCount() == 4, "a log is still four chips");
        var chest = CrushingRecipe.of(new ItemStack(Items.CHEST));
        check(helper, chest != null && chest.output().getCount() == 8, "a chest gives back its eight planks");
        // A slab is half a plank, which rounds to nothing, so it stays out - and that is what stops a plank
        // being split into two slabs and ground into two chips.
        check(helper, CrushingRecipe.of(new ItemStack(Items.OAK_SLAB)) == null,
                "slabs round down to nothing and are left out");

        // Diamond comes through a crusher intact; iron only turns to dust where a mod defines one.
        var sword = CrushingRecipe.of(new ItemStack(Items.DIAMOND_SWORD));
        check(helper, sword != null && sword.output().is(Items.DIAMOND) && sword.output().getCount() == 2,
                "a diamond sword gives its two diamonds back");
        check(helper, CrushingRecipe.of(new ItemStack(Items.IRON_SWORD)) == null,
                "an iron sword needs a mod that has iron dust");
        helper.succeed();
    }

    static BlockPos place(GameTestHelper helper, BlockPos pos, Block block) {
        helper.setBlock(pos, block);
        return pos;
    }

    /** Each redstone mode, on a Stone Maker with a redstone block beside it and then without one. */
    static void machineRedstoneModes(GameTestHelper helper) {
        BlockPos maker = new BlockPos(3, 2, 3);
        helper.setBlock(maker, ModBlocks.STONE_MAKER.get());
        helper.setBlock(maker.east(), Blocks.REDSTONE_BLOCK);
        StoneMakerBlockEntity be = helper.getBlockEntity(maker, StoneMakerBlockEntity.class);
        be.energy().set(StoneMakerBlockEntity.CAPACITY);
        BlockPos at = helper.absolutePos(maker);
        Runnable oneItem = () -> {
            for (int i = 0; i < StoneMakerBlockEntity.TICKS_PER_ITEM; i++) {
                StoneMakerBlockEntity.serverTick(helper.getLevel(), at, helper.getBlockState(maker), be);
            }
        };
        IntSupplier made = () -> be.getItem(0).getCount();
        oneItem.run();
        check(helper, made.getAsInt() == 0 && be.guiStatus() == MachineStatus.REDSTONE,
                "by default a signal stops the machine");
        be.setRedstoneMode(RedstoneMode.ALWAYS);
        oneItem.run();
        check(helper, made.getAsInt() == 1, "set to always run, it ignores the signal");
        be.setRedstoneMode(RedstoneMode.WITH_SIGNAL);
        oneItem.run();
        check(helper, made.getAsInt() == 2, "set to run with a signal, it runs while powered");
        be.setRedstoneMode(RedstoneMode.NEVER);
        oneItem.run();
        check(helper, made.getAsInt() == 2, "set to never run, it does not");
        helper.setBlock(maker.east(), Blocks.AIR);
        be.setRedstoneMode(RedstoneMode.WITHOUT_SIGNAL);
        oneItem.run();
        check(helper, made.getAsInt() == 3, "without a signal the default mode runs");
        be.setRedstoneMode(RedstoneMode.WITH_SIGNAL);
        oneItem.run();
        check(helper, made.getAsInt() == 3 && be.guiStatus() == MachineStatus.REDSTONE,
                "set to run with a signal, it waits for one");
        helper.succeed();
    }

    /** The mode survives a save, and a new laser still stops on a signal as lasers always have. */
    static void machineRedstoneModeSaved(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 2, 3);
        BlockPos second = new BlockPos(4, 2, 3);
        helper.setBlock(first, ModBlocks.CRUSHER.get());
        helper.setBlock(second, ModBlocks.CRUSHER.get());
        CrusherBlockEntity crusher = helper.getBlockEntity(first, CrusherBlockEntity.class);
        crusher.setRedstoneMode(RedstoneMode.NEVER);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = crusher.saveWithoutMetadata(registries);
        CrusherBlockEntity copy = helper.getBlockEntity(second, CrusherBlockEntity.class);
        copy.loadWithComponents(TagValueInput.create(
                ProblemReporter.DISCARDING, registries, saved));
        check(helper, copy.redstoneMode() == RedstoneMode.NEVER, "the redstone mode is saved");
        helper.setBlock(new BlockPos(3, 2, 5), ModBlocks.LASER.get());
        check(helper, helper.getBlockEntity(new BlockPos(3, 2, 5), LaserBlockEntity.class).redstoneMode()
                == RedstoneMode.WITHOUT_SIGNAL, "a new laser stops on a signal");
        helper.succeed();
    }

    /** Every machine's container matches the layout its screen is drawn from, and every layout has its machine. */
    static void machineScreensFit(GameTestHelper helper) {
        List<Block> machines = List.of(ModBlocks.GENERATOR.get(), ModBlocks.DIESEL_GENERATOR.get(), ModBlocks.CRUSHER.get(),
                ModBlocks.STONE_MAKER.get(), ModBlocks.LASER.get(), ModBlocks.PLANTER.get(), ModBlocks.SIFTER.get(),
                ModBlocks.RETORT.get(), ModBlocks.STIRRING_TANK.get(), ModBlocks.DISTILLER.get(),
                ModBlocks.ELECTRIC_FURNACE.get(), ModBlocks.ELECTROLYTIC_CELL.get(), ModBarter.BARTER_TERMINAL.get());
        Set<MachineLayout> seen = EnumSet.noneOf(MachineLayout.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < machines.size(); i++) {
            BlockPos pos = new BlockPos(1 + (i % 5), 2, 1 + (i / 5) * 2);
            helper.setBlock(pos, machines.get(i));
            var entity = helper.getBlockEntity(pos, BlockEntity.class);
            if (!(entity instanceof MachineInfo info)) {
                helper.fail(machines.get(i) + " has no screen");
                return;
            }
            var layout = info.layout();
            seen.add(layout);
            check(helper, info.slots().getContainerSize() == layout.slotCount(),
                    layout + ": the screen shows " + layout.slotCount() + " slots of " + info.slots().getContainerSize());
            var menu = new MachineMenu(1, player.getInventory(), layout, info.slots(),
                    MachineMenu.data(info), entity);
            check(helper, menu.slots.size() == layout.slotCount() + 36, layout + ": machine slots and the inventory");
        }
        check(helper, seen.size() == MachineLayout.values().length, "every layout has a machine");
        helper.succeed();
    }

    /** Menu data travels as shorts; a laser's 120,000 FE must still arrive as 120,000. */
    static void machineScreenBigNumbers(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.LASER.get());
        var laser = helper.getBlockEntity(pos, LaserBlockEntity.class);
        laser.energy().set(LaserBlockEntity.CAPACITY - 7);
        var server = MachineMenu.data(laser);
        var client = new SimpleContainerData(MachineMenu.DATA_COUNT);
        for (int i = 0; i < server.getCount(); i++) {
            client.set(i, (short) server.get(i)); // what the data packet does to it
        }
        var menu = new MachineMenu(1, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(),
                laser.layout(), laser.slots(), client, null);
        check(helper, menu.energy() == LaserBlockEntity.CAPACITY - 7, "energy arrived as " + menu.energy());
        check(helper, menu.energyCapacity() == LaserBlockEntity.CAPACITY, "capacity arrived whole");
        helper.succeed();
    }

    /** Shift-clicking fills inputs only: crude salt must not top up the Retort's crude salt output. */
    static void machineScreenOutputsOnly(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        retort.setItem(3, new ItemStack(ModItems.CRUDE_SALT.get(), 10));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(9, new ItemStack(ModItems.CRUDE_SALT.get(), 5));
        player.getInventory().setItem(10, new ItemStack(Items.COPPER_INGOT, 3));
        var layout = retort.layout();
        var menu = new MachineMenu(1, player.getInventory(), layout, retort.slots(),
                MachineMenu.data(retort), retort);
        menu.quickMoveStack(player, layout.slotCount());
        check(helper, retort.getItem(3).getCount() == 10 && player.getInventory().getItem(9).getCount() == 5,
                "the output slot took shift-clicked crude salt");
        menu.quickMoveStack(player, layout.slotCount() + 1);
        check(helper, retort.getItem(0).is(Items.COPPER_INGOT), "copper shift-clicks into the input");
        // glass bottles into the fill box (the slot with a bottle drawn in it), not the reagents, which take them too...
        player.getInventory().setItem(11, new ItemStack(Items.GLASS_BOTTLE, 16));
        menu.quickMoveStack(player, layout.slotCount() + 2);
        check(helper, retort.getItem(RetortBlockEntity.FILL).getCount() == 16, "bottles shift-click into the fill box: "
                + retort.getItem(RetortBlockEntity.FILL));
        // ...but with logs in, which cook with bottles into wood vinegar, to the reagents
        retort.setItem(RetortBlockEntity.FILL, ItemStack.EMPTY);
        retort.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        player.getInventory().setItem(11, new ItemStack(Items.GLASS_BOTTLE, 16));
        menu.quickMoveStack(player, layout.slotCount() + 2);
        check(helper, retort.getItem(RetortBlockEntity.FILL).isEmpty() && player.getInventory().getItem(11).isEmpty(),
                "with logs in, bottles go to the reagents");
        helper.succeed();
    }

    /** A pair holds 40,000 FE; the screen must get 40,000, not what is left of it after a short. */
    static void repairBoxScreenWholeStore(GameTestHelper helper) {
        BlockPos left = new BlockPos(2, 2, 3);
        BlockPos right = new BlockPos(3, 2, 3);
        var type = RepairBoxBlock.TYPE;
        BlockState single = ModBlocks.REPAIR_BOX.get().defaultBlockState().setValue(RepairBoxBlock.FACING, Direction.NORTH);
        helper.setBlock(right, single);
        helper.setBlock(left, single.setValue(type, ChestType.LEFT));
        var box = helper.getBlockEntity(left, RepairBoxBlockEntity.class);
        var other = helper.getBlockEntity(right, RepairBoxBlockEntity.class);
        box.energy().set(RepairBoxBlockEntity.CAPACITY);
        other.energy().set(RepairBoxBlockEntity.CAPACITY);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var server = RepairBoxBlockEntity.dataFor(new RepairBoxBlockEntity[] {box, other});
        var client = new SimpleContainerData(RepairBoxMenu.DATA_COUNT);
        for (int i = 0; i < server.getCount(); i++) {
            client.set(i, (short) server.get(i)); // what the data packet does to it
        }
        var shown = new RepairBoxMenu(2, player.getInventory(),
                new SimpleContainer(12), client, new RepairBoxBlockEntity[0]);
        check(helper, shown.energy() == 2 * RepairBoxBlockEntity.CAPACITY, "the pair's power arrived as " + shown.energy());
        check(helper, shown.boxes() == 2, "and it knows it is a pair");
        helper.succeed();
    }

    /** A print job set going waits while the redstone mode says stop, and carries on after. */
    static void printingPressWaitsForRedstone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.PRINTING_PRESS.get());
        helper.setBlock(pos.east(), Blocks.REDSTONE_BLOCK);
        var press = helper.getBlockEntity(pos, PrintingPressBlockEntity.class);
        press.energy().set((int) press.energy().getCapacityAsLong());
        press.select(Nookworks.id("safety_cross"));
        press.setItem(PrintingPressBlockEntity.PAPER, new ItemStack(ModItems.FINE_PAPER.get(), 2));
        for (int i = 0; i < PosterVariant.PROCESS_INKS.size(); i++) {
            press.setItem(PrintingPressBlockEntity.FIRST_INK + i,
                    new ItemStack(PosterVariant.PROCESS_INKS.get(i), 2));
        }
        press.setRunning(true);
        Runnable run = () -> {
            for (int i = 0; i < PrintingPressBlockEntity.TICKS + 2; i++) {
                PrintingPressBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos),
                        helper.getBlockState(pos), press);
            }
        };
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).isEmpty(), "a signal holds the job");
        helper.setBlock(pos.east(), Blocks.AIR);
        run.run();
        check(helper, press.getItem(PrintingPressBlockEntity.OUTPUT).is(ModItems.POSTER.get()),
                "without it the copy comes out");
        helper.succeed();
    }

    /** Stopped by redstone, the controller keeps its spawners off instead of handing them back to vanilla. */
    static void controllerRedstoneOff(GameTestHelper helper) {
        var controller = buildControl(helper,
                SpawnerSettings.DEFAULT.withInterval(20).withCount(16).withAnyLight(true).withNeedsPlayer(false), 20_000);
        helper.setBlock(new BlockPos(1, 2, 0), Blocks.REDSTONE_BLOCK); // beside the console, which is on the receiver
        IntSupplier countdown = StorageTests.spawnerCountdown(helper);
        int[] before = new int[1];
        helper.runAfterDelay(30, () -> before[0] = countdown.getAsInt()); // once the controller has looked
        helper.runAfterDelay(90, () -> {
            check(helper, pigsAround(helper) == 0, "a controller stopped by redstone let its spawner spawn " + pigsAround(helper));
            check(helper, countdown.getAsInt() == before[0], "nor count down to a spawn with a player beside it: "
                    + before[0] + " -> " + countdown.getAsInt());
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            var menu = (SpawnerControllerMenu) controller.createMenu(1, player.getInventory(), player);
            check(helper, menu.heldByRedstone(), "its screen says redstone is holding it");
            helper.succeed();
        });
    }

    static void brewerRedstoneButton(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.SPAWN_EGG_BREWER.get());
        var brewer = helper.getBlockEntity(pos, SpawnEggBrewerBlockEntity.class);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var menu = (SpawnEggBrewerMenu) brewer.createMenu(1, player.getInventory(), player);
        check(helper, brewer.redstoneMode() == RedstoneMode.DEFAULT, "a new brewer stops on a signal");
        menu.clickMenuButton(player, SpawnEggBrewerMenu.BUTTON_REDSTONE);
        check(helper, brewer.redstoneMode() == RedstoneMode.DEFAULT.next(), "the button steps the mode");
        helper.succeed();
    }

    static void stoneMakerFillsChest(GameTestHelper helper) {
        BlockPos maker = new BlockPos(3, 2, 3);
        helper.setBlock(maker, ModBlocks.STONE_MAKER.get());
        helper.setBlock(maker.below(), Blocks.CHEST);
        helper.getBlockEntity(maker, StoneMakerBlockEntity.class).energy().set(10_000);

        helper.succeedWhen(() -> check(helper,
                countIn(helper.getBlockEntity(maker.below(), ChestBlockEntity.class), Items.COBBLESTONE) >= 3,
                "stone maker should produce cobblestone into the chest"));
    }

    static void stoneMakerIdles(GameTestHelper helper) {
        BlockPos maker = new BlockPos(3, 2, 3);
        helper.setBlock(maker, ModBlocks.STONE_MAKER.get());
        helper.setBlock(maker.below(), Blocks.CHEST);

        helper.runAfterDelay(50, () -> {
            check(helper, countIn(helper.getBlockEntity(maker.below(), ChestBlockEntity.class), Items.COBBLESTONE) == 0,
                    "without energy nothing may be produced");
            helper.succeed();
        });
    }

    static void machinesRejectWrongItems(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 1, 3), ModBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(4, 1, 3), ModBlocks.CRUSHER.get());
        GeneratorBlockEntity gen = helper.getBlockEntity(new BlockPos(2, 1, 3), GeneratorBlockEntity.class);
        CrusherBlockEntity crusher = helper.getBlockEntity(new BlockPos(4, 1, 3), CrusherBlockEntity.class);
        check(helper, gen.canPlaceItem(0, new ItemStack(Items.COAL)), "generator takes coal");
        check(helper, gen.canPlaceItem(0, new ItemStack(Items.OAK_PLANKS)), "generator takes planks");
        check(helper, !gen.canPlaceItem(0, new ItemStack(Items.DIRT)), "generator refuses dirt");
        check(helper, crusher.canPlaceItem(CrusherBlockEntity.INPUT, new ItemStack(Items.GRAVEL)), "crusher takes gravel");
        check(helper, !crusher.canPlaceItem(CrusherBlockEntity.INPUT, new ItemStack(Items.DIRT)), "crusher refuses dirt");
        helper.succeed();
    }

    /** A burning generator (40 FE/t) must keep two 20 FE/t stone makers running, not just the first one found. */
    static void generatorSharesEnergy(GameTestHelper helper) {
        BlockPos gen = new BlockPos(3, 2, 3);
        BlockPos west = gen.west();
        BlockPos east = gen.east();
        helper.setBlock(gen, ModBlocks.GENERATOR.get());
        helper.setBlock(west, ModBlocks.STONE_MAKER.get());
        helper.setBlock(east, ModBlocks.STONE_MAKER.get());
        helper.setBlock(west.below(), Blocks.CHEST);
        helper.setBlock(east.below(), Blocks.CHEST);
        helper.getBlockEntity(gen, GeneratorBlockEntity.class).setItem(0, new ItemStack(Items.COAL));

        helper.succeedWhen(() -> {
            int w = countIn(helper.getBlockEntity(west.below(), ChestBlockEntity.class), Items.COBBLESTONE);
            int e = countIn(helper.getBlockEntity(east.below(), ChestBlockEntity.class), Items.COBBLESTONE);
            check(helper, w >= 2 && e >= 2, "both stone makers should run, got " + w + " and " + e);
        });
    }

    static void cableCapabilityForwards(GameTestHelper helper) {
        BlockPos cable = new BlockPos(2, 1, 3);
        BlockPos crusher = cable.east();
        helper.setBlock(cable, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        var handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK,
                helper.absolutePos(cable), Direction.WEST);
        check(helper, handler != null, "cables must expose an energy capability");
        int accepted;
        try (var tx = Transaction.openRoot()) {
            accepted = handler.insert(150, tx);
            tx.commit();
        }
        int stored = helper.getBlockEntity(crusher, CrusherBlockEntity.class).energyStored();
        check(helper, accepted == 150 && stored == 150, "150 FE should pass through the cable, accepted " + accepted + " stored " + stored);
        helper.succeed();
    }

    // ---- Laser and Planter ----
    static LaserBlockEntity placeLaser(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.LASER.get().defaultBlockState().setValue(LaserBlock.FACING, facing));
        var laser = helper.getBlockEntity(pos, LaserBlockEntity.class);
        laser.energy().set(LaserBlockEntity.CAPACITY);
        return laser;
    }

    static void laserFellsTree(GameTestHelper helper) {
        placeLaser(helper, new BlockPos(0, 2, 3), Direction.EAST);
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1); // not 7, "already decaying": a random tick took one now and then
        helper.setBlock(new BlockPos(4, 5, 3), leaves);
        helper.setBlock(new BlockPos(5, 4, 3), leaves);
        helper.succeedWhen(() -> {
            for (int y = 1; y <= 4; y++) {
                check(helper, helper.getBlockState(new BlockPos(4, y, 3)).isAir(), "the whole trunk is felled, y=" + y);
            }
            check(helper, helper.getBlockState(new BlockPos(0, 2, 3)).getValue(LaserBlock.BEAM) == 0,
                    "with the trunk gone there is nothing in the way: it stands by, the beam out");
        });
    }

    /**
     * On one Generator's power (40 FE/t) a laser waits at a tree with its beam off until the surge to fell it is stored,
     * then fells it at once - about 150 ticks. (It turned the beam on for a tick when the surge was stored but judged the
     * tree only on the whole second: off again unfelled, as often as not, and on some steady inputs never felled at all.)
     */
    static void laserFellsOnAGeneratorsPower(GameTestHelper helper) {
        var laser = placeLaser(helper, new BlockPos(0, 2, 3), Direction.EAST);
        laser.energy().set(0);
        helper.onEachTick(() -> laser.energy().set((int) Math.min(LaserBlockEntity.CAPACITY, laser.energy().getAmountAsLong() + 40)));
        for (int y = 1; y <= 4; y++) {
            helper.setBlock(new BlockPos(4, y, 3), Blocks.OAK_LOG);
        }
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, false)
                .setValue(LeavesBlock.DISTANCE, 1); // not 7, "already decaying": a random tick took one now and then
        helper.setBlock(new BlockPos(4, 5, 3), leaves);
        helper.setBlock(new BlockPos(5, 4, 3), leaves);
        helper.succeedWhen(() -> {
            for (int y = 1; y <= 4; y++) {
                check(helper, helper.getBlockState(new BlockPos(4, y, 3)).isAir(), "the tree is felled on a generator's power, y=" + y);
            }
        });
    }

    static void laserHurtsMobs(GameTestHelper helper) {
        placeLaser(helper, new BlockPos(0, 1, 3), Direction.EAST);
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
        float full = pig.getHealth();
        helper.succeedWhen(() -> check(helper, pig.getHealth() < full || !pig.isAlive(), "the pig in the beam is hurt"));
    }

    /**
     * An armour stand, or a mob made invulnerable, alone in the beam is nothing to fire at: the laser stands by, its beam
     * out (it fired at full draw at them for nothing, r7 review).
     */
    static void laserStandsByForWhatItCannotHurt(GameTestHelper helper) {
        placeLaser(helper, new BlockPos(0, 1, 3), Direction.EAST);
        helper.spawn(EntityTypes.ARMOR_STAND, new BlockPos(2, 1, 3));
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(4, 1, 3));
        pig.setPermanentlyInvulnerable(true);
        helper.runAfterDelay(30, () -> {
            check(helper, helper.getBlockState(new BlockPos(0, 1, 3)).getValue(LaserBlock.BEAM) == 0,
                    "no beam for an armour stand and an invulnerable pig");
            helper.succeed();
        });
    }

    /**
     * A giver that gives less a tick than the taker would take (a Generator built before its output was raised in play,
     * 200 FE/t, feeding a Laser that takes 4,000): the smaller amount moves, not nothing (r7 review).
     */
    static void energyMovesWhatTheGiverGives(GameTestHelper helper) {
        var giver = new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(10_000, 0, 200);
        giver.set(10_000);
        var taker = new net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler(10_000, 4_000, 0);
        int moved = dev.nookworks.machine.MachineIO.transfer(giver, taker, 400);
        check(helper, moved == 200 && taker.getAmountAsLong() == 200 && giver.getAmountAsLong() == 9_800,
                "200 of the 400 asked moves: " + moved + ", taker " + taker.getAmountAsLong() + ", giver " + giver.getAmountAsLong());
        check(helper, dev.nookworks.machine.MachineIO.transfer(giver, taker, 100) == 100, "and as asked when both can");
        helper.succeed();
    }

    static void laserStopsOnRedstone(GameTestHelper helper) {
        placeLaser(helper, new BlockPos(1, 1, 3), Direction.EAST);
        helper.setBlock(new BlockPos(0, 1, 3), Blocks.REDSTONE_BLOCK);
        var pig = helper.spawnWithNoFreeWill(EntityTypes.PIG, new BlockPos(3, 1, 3));
        float full = pig.getHealth();
        helper.runAfterDelay(30, () -> {
            check(helper, pig.getHealth() == full, "a powered-off laser does no harm");
            check(helper, helper.getBlockState(new BlockPos(1, 1, 3)).getValue(LaserBlock.BEAM) == 0, "no beam");
            helper.succeed();
        });
    }

    static void laserSparesCoatedWood(GameTestHelper helper) {
        placeLaser(helper, new BlockPos(0, 1, 3), Direction.EAST);
        BlockPos planks = new BlockPos(4, 1, 3);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        Fireproofing.coat(helper.getLevel(), helper.absolutePos(planks));
        helper.runAfterDelay(90, () -> {
            check(helper, !helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.FIRE), "coated planks never catch fire");
            check(helper, helper.getBlockState(planks).is(Blocks.OAK_PLANKS), "and are not destroyed");
            helper.succeed();
        });
    }

    /**
     * The other half of the one above: the same planks uncoated catch fire in the beam. Without it, the coated planks
     * coming to no harm proved nothing - a laser that set nothing alight passed as well. (One roll in three a second: in
     * twenty seconds it all but surely has.)
     */
    static void laserSetsWoodAlight(GameTestHelper helper) {
        var laser = placeLaser(helper, new BlockPos(0, 1, 3), Direction.EAST);
        BlockPos planks = new BlockPos(4, 1, 3);
        helper.setBlock(planks, Blocks.OAK_PLANKS);
        helper.succeedWhen(() -> {
            laser.energy().set(LaserBlockEntity.CAPACITY); // kept fed: whether it lights the wood is the question
            check(helper, helper.getBlockState(new BlockPos(3, 1, 3)).is(Blocks.FIRE) || !helper.getBlockState(planks).is(Blocks.OAK_PLANKS),
                    "uncoated planks in the beam catch fire");
        });
    }

    static void planterPlantsSeeds(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 0);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        for (int x = 2; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 0, 1), Blocks.FARMLAND);
        }
        var planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.setItem(0, new ItemStack(Items.WHEAT_SEEDS, 5));
        planter.setItem(1, new ItemStack(Items.OAK_SAPLING, 4)); // slot order: seeds first, nothing left for these
        check(helper, !planter.plantOne(helper.getLevel()), "no power: nothing planted");
        planter.energy().set(1_000);
        for (int i = 0; i < 3; i++) {
            check(helper, planter.plantOne(helper.getLevel()), "plant #" + (i + 1));
        }
        check(helper, !planter.plantOne(helper.getLevel()), "field full: nothing more to plant");
        for (int x = 2; x <= 4; x++) {
            check(helper, helper.getBlockState(new BlockPos(x, 1, 1)).is(Blocks.WHEAT), "wheat planted on the farmland at x=" + x);
        }
        check(helper, planter.getItem(0).getCount() == 2, "three seeds used, 2 left, has " + planter.getItem(0).getCount());
        check(helper, planter.getItem(1).getCount() == 4, "saplings kept (the stone beyond the farmland grows nothing)");
        check(helper, !planter.canPlaceItem(0, new ItemStack(Items.COBBLESTONE)), "only plants go in");
        helper.succeed();
    }

    static void planterPlantsTallFlowersWhole(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 0);
        helper.setBlock(pos, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        BlockPos spot = new BlockPos(3, 1, 1);
        helper.setBlock(spot.below(), Blocks.GRASS_BLOCK);
        var planter = helper.getBlockEntity(pos, PlanterBlockEntity.class);
        planter.setItem(0, new ItemStack(Items.SUNFLOWER, 2));
        planter.energy().set(1_000);
        check(helper, planter.plantOne(helper.getLevel()), "the sunflower is planted");
        check(helper, helper.getBlockState(spot).is(Blocks.SUNFLOWER)
                && helper.getBlockState(spot.above()).is(Blocks.SUNFLOWER), "both halves stand, not a stump");
        check(helper, helper.getBlockState(spot.above()).getValue(DoublePlantBlock.HALF)
                == DoubleBlockHalf.UPPER, "the top half is the upper one");
        check(helper, planter.getItem(0).getCount() == 1, "one sunflower used");
        helper.succeed();
    }

    static final BlockPos LOOP_BUFFER = new BlockPos(2, 5, 1);
    static final BlockPos LOOP_OTHERS = new BlockPos(4, 5, 1);
    static final BlockPos LOOP_PLANTER = new BlockPos(2, 1, 1);

    /** Builds the loop and loads the magnet chest with 10 saplings and 10 logs. */
    static MagnetChestBlockEntity buildLoop(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 7, 1), ModBlocks.MAGNET_CHEST.get());
        helper.setBlock(new BlockPos(3, 6, 1), Blocks.HOPPER);
        helper.setBlock(new BlockPos(3, 5, 1), ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        helper.getBlockEntity(new BlockPos(3, 5, 1), SorterBlockEntity.class).filter().setItem(0, new ItemStack(Items.OAK_SAPLING));
        helper.setBlock(LOOP_BUFFER, ModBlocks.IRON_STORAGE.get());
        helper.setBlock(LOOP_OTHERS, ModBlocks.IRON_STORAGE.get());
        helper.setBlock(new BlockPos(2, 4, 1), Blocks.HOPPER); // pulls from the buffer above
        helper.setBlock(new BlockPos(2, 3, 1), ModBlocks.ITEM_WARP_SENDER.get());
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.ITEM_WARP_RECEIVER.get());
        helper.setBlock(LOOP_PLANTER, ModBlocks.PLANTER.get().defaultBlockState().setValue(PlanterBlock.FACING, Direction.SOUTH));
        var chest = helper.getBlockEntity(new BlockPos(3, 7, 1), MagnetChestBlockEntity.class);
        chest.setItem(0, new ItemStack(Items.OAK_SAPLING, 10));
        chest.setItem(1, new ItemStack(Items.OAK_LOG, 10));
        return chest;
    }

    static void saplingLoopFlows(GameTestHelper helper) {
        buildLoop(helper);
        helper.succeedWhen(() -> {
            var planter = helper.getBlockEntity(LOOP_PLANTER, PlanterBlockEntity.class);
            check(helper, countIn(planter, Items.OAK_SAPLING) == 10, "all 10 saplings reach the planter, has " + countIn(planter, Items.OAK_SAPLING));
            var others = helper.getBlockEntity(LOOP_OTHERS, StorageBlockEntity.class);
            check(helper, storageTotal(others, Items.OAK_LOG) == 10, "all 10 logs go to the other chest");
        });
    }

    static void saplingLoopBuffers(GameTestHelper helper) {
        buildLoop(helper);
        var planter = helper.getBlockEntity(LOOP_PLANTER, PlanterBlockEntity.class);
        for (int i = 0; i < 9; i++) {
            planter.setItem(i, new ItemStack(Items.WHEAT_SEEDS, 64)); // full, and nothing can be planted on stone
        }
        helper.succeedWhen(() -> {
            var buffer = helper.getBlockEntity(LOOP_BUFFER, StorageBlockEntity.class);
            var others = helper.getBlockEntity(LOOP_OTHERS, StorageBlockEntity.class);
            var sender = helper.getBlockEntity(new BlockPos(2, 3, 1), ItemWarpSenderBlockEntity.class);
            int waiting = storageTotal(buffer, Items.OAK_SAPLING) + countIn(sender, Items.OAK_SAPLING)
                    + countIn(helper.getBlockEntity(new BlockPos(2, 4, 1), HopperBlockEntity.class), Items.OAK_SAPLING);
            check(helper, waiting == 10, "the 10 saplings wait in the buffer line, found " + waiting);
            check(helper, storageTotal(others, Items.OAK_LOG) == 10, "the logs still get through: nothing jams");
            check(helper, countIn(planter, Items.OAK_SAPLING) == 0, "the full planter takes nothing");
        });
    }

    // ---- Experience pipes, pad and tap ----

    /** A pipe run beside or above the pad gets a riser down to the plate, so it does not hang in the air. */
    static void absorptionPadRisesToItsPipes(GameTestHelper helper) {
        BlockPos pad = new BlockPos(3, 1, 3);
        helper.setBlock(pad, ModBlocks.ABSORPTION_PAD.get());
        var riser = AbsorptionPadBlock.riser(Direction.EAST);
        check(helper, !helper.getBlockState(pad).getValue(riser), "no riser with nothing beside it");
        helper.setBlock(pad.east(), ModBlocks.EXPERIENCE_PIPE.get());
        check(helper, helper.getBlockState(pad).getValue(riser), "a pipe to the east gets a riser");
        helper.setBlock(pad.above(), ModBlocks.EXPERIENCE_PIPE.get());
        check(helper, helper.getBlockState(pad).getValue(AbsorptionPadBlock.UP), "and so does one above");
        helper.setBlock(pad.north(), Blocks.STONE);
        check(helper, !helper.getBlockState(pad).getValue(AbsorptionPadBlock.riser(Direction.NORTH)),
                "a block that is not a pipe gets none");
        helper.setBlock(pad.east(), Blocks.AIR);
        check(helper, !helper.getBlockState(pad).getValue(riser), "and the riser goes with its pipe");
        check(helper, helper.getBlockState(pad).getShape(helper.getLevel(), helper.absolutePos(pad)).max(Direction.Axis.Y) == 1.0,
                "the riser above is part of the outline, so it can be aimed at");
        helper.succeed();
    }

    /** The open tap pours the tank out as orbs, a few points a tick, and stops when turned back or dry. */
    static void experienceTankTapPours(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.EXPERIENCE_TANK.get().defaultBlockState()
                .setValue(ExperienceTankBlock.FACING, Direction.SOUTH));
        var tank = helper.getBlockEntity(pos, ExperienceTankBlockEntity.class);
        tank.fill(1_000);
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pos);

        ExperienceTankBlockEntity.serverTick(level, abs, helper.getBlockState(pos), tank);
        check(helper, tank.amount() == 1_000, "a shut tap pours nothing");

        BlockState open = helper.getBlockState(pos).setValue(ExperienceTankBlock.OPEN, true);
        helper.setBlock(pos, open);
        int poured = 0;
        for (int i = 0; i < 4; i++) {
            ExperienceTankBlockEntity.serverTick(level, abs, open, tank);
            poured += ExperienceTankBlockEntity.POUR_PER_TICK;
        }
        check(helper, tank.amount() == 1_000 - ExperienceFluids.millibuckets(poured),
                "four ticks pour four lots of points, left " + tank.amount());
        var orbs = helper.getEntities(EntityTypes.EXPERIENCE_ORB);
        check(helper, orbs.stream().mapToInt(ExperienceOrb::getValue).sum() == poured,
                "every point poured is an orb, found " + orbs.size() + " orbs");
        check(helper, orbs.stream().allMatch(orb -> orb.getZ() > abs.getZ() + 1.0),
                "and they come out of the spout on the south face");

        for (int i = 0; i < 100; i++) {
            ExperienceTankBlockEntity.serverTick(level, abs, open, tank);
        }
        check(helper, tank.amount() < ExperienceFluids.MB_PER_POINT,
                "it runs dry, leaving only less than a point, got " + tank.amount());
        helper.succeed();
    }

    // ---- Pipe and cable sides switched off with the Wrench ----

    /** Sneak-using the wrench on a cable's arm cuts that joint, on both cables, and the network stops there. */
    static void wrenchDisconnectsACable(GameTestHelper helper) {
        BlockPos generator = new BlockPos(1, 1, 3);
        BlockPos first = new BlockPos(2, 1, 3);
        BlockPos second = new BlockPos(3, 1, 3);
        BlockPos crusher = new BlockPos(4, 1, 3);
        helper.setBlock(generator, ModBlocks.GENERATOR.get());
        helper.setBlock(first, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(second, ModBlocks.ENERGY_CABLE.get());
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        var level = helper.getLevel();
        Supplier<Boolean> reaches = () -> MachineIO.receivers(level, helper.absolutePos(generator))
                .stream().anyMatch(r -> r.pos().equals(helper.absolutePos(crusher)));
        check(helper, reaches.get(), "joined up, the generator reaches the crusher");

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
        // aim at the first cable's east arm
        Vec3 onArm = helper.absoluteVec(Vec3.atCenterOf(first).add(0.4, 0.2, 0));
        wrench.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(onArm, Direction.UP, helper.absolutePos(first), false)));
        check(helper, PipeSides.isOff(helper.getBlockState(first), Direction.EAST), "the east side is off");
        check(helper, PipeSides.isOff(helper.getBlockState(second), Direction.WEST), "and so is the other cable's");
        check(helper, !PipeSides.joined(helper.getBlockState(first), Direction.EAST)
                && !PipeSides.joined(helper.getBlockState(second), Direction.WEST), "neither shows an arm there");
        check(helper, !reaches.get(), "and the generator no longer reaches the crusher");

        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE); // a neighbour changing
        check(helper, PipeSides.isOff(helper.getBlockState(first), Direction.EAST), "it stays off through updates");

        // back on: aim at the middle of the cable's east face, where there is no arm now
        Vec3 onCore = helper.absoluteVec(Vec3.atCenterOf(first).add(0.19, 0, 0));
        wrench.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(onCore, Direction.EAST, helper.absolutePos(first), false)));
        check(helper, PipeSides.joined(helper.getBlockState(first), Direction.EAST), "the same click on the face joins it again");
        check(helper, reaches.get(), "and the power flows again");
        helper.succeed();
    }

    /** A side switched off is shut for other mods too: no capability is offered through it. */
    static void pipeSideOffShutsItsCapability(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(3, 1, 3);
        helper.setBlock(pipe, ModBlocks.EXPERIENCE_PIPE.get());
        helper.setBlock(pipe.east(), ModBlocks.EXPERIENCE_TANK.get());
        var level = helper.getLevel();
        BlockPos abs = helper.absolutePos(pipe);
        check(helper, PipeSides.joined(helper.getBlockState(pipe), Direction.EAST), "the pipe joins the tank");
        PipeSides.toggle(level, abs, helper.getBlockState(pipe), Direction.EAST);
        check(helper, !PipeSides.joined(helper.getBlockState(pipe), Direction.EAST), "switched off, it lets go");
        check(helper, level.getCapability(Capabilities.Fluid.BLOCK, abs, Direction.EAST) == null,
                "and offers nothing on that side");
        check(helper, level.getCapability(Capabilities.Fluid.BLOCK, abs, Direction.WEST) != null,
                "but still does on the others");

        BlockPos mercury = new BlockPos(3, 1, 5);
        helper.setBlock(mercury.west(), ModBlocks.MERCURY_TANK.get());
        helper.setBlock(mercury, ModBlocks.MERCURY_PIPE.get());
        helper.setBlock(mercury.east(), ModBlocks.MERCURY_TANK.get());
        var found = MercuryNetwork.handlers(level, helper.absolutePos(mercury.west()));
        check(helper, found.size() == 1, "the two mercury tanks see each other through the pipe");
        PipeSides.toggle(level, helper.absolutePos(mercury), helper.getBlockState(mercury), Direction.EAST);
        check(helper, MercuryNetwork.handlers(level, helper.absolutePos(mercury.west())).isEmpty(),
                "cut on one side, they do not");
        helper.succeed();
    }

    private MachineTests() {}
}
