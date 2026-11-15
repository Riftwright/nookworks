package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.Bottles;
import dev.nookworks.fluid.CopperTankBlock;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;

/**
 * Wood vinegar as a liquid (1.5; the user, 2026-11-10: 「木酢液も液体として作って欲しい」, and their rule of 2026-11-08 that a
 * machine takes what comes in a bottle or bucket as a liquid, the container going out empty, never refilled): its fluid,
 * bucket and pool; the Sealed Retort chars logs with no bottle, giving the wood vinegar off into a tank beside it and
 * waiting when it has nowhere to go; the Vacuum Distiller takes it by pipe or poured from bottles and buckets, the empties
 * going out below, and its methanol goes into its tank, never into a bottle.
 */
final class WoodVinegarTests {
    private WoodVinegarTests() {}

    static void register() {
        test("wood_vinegar_is_c_wood_vinegar_with_a_bucket_a_bottle_and_a_pool", WoodVinegarTests::fluidAndBucket);
        test("the_retort_chars_logs_into_wood_vinegar_piped_off_beside_it", 300, WoodVinegarTests::retort);
        test("a_retort_with_nowhere_for_its_wood_vinegar_waits", WoodVinegarTests::retortWaits);
        test("the_distiller_takes_wood_vinegar_by_pipe_bottle_and_bucket", 200, WoodVinegarTests::distiller);
        test("a_distiller_with_nowhere_for_its_methanol_waits_and_bottles_nothing", WoodVinegarTests::distillerWaits);
        test("a_distillers_mercury_leaves_by_pipe_at_its_condenser_and_its_water_not_by_its_feed", 200, WoodVinegarTests::distillerOutlets);
        test("a_distillers_water_goes_to_no_retort_till_it_knows_where_its_wood_vinegar_comes_from", 300, WoodVinegarTests::waterNotToRetort);
        test("a_distillers_water_goes_to_a_retort_on_a_line_of_its_own_not_to_the_one_feeding_it", 300, WoodVinegarTests::waterToOtherRetort);
        test("a_distillers_water_goes_to_a_retort_once_its_wood_vinegar_is_poured_in_by_hand", 300, WoodVinegarTests::waterToRetortPouredByHand);
        test("a_distillers_water_keeps_off_a_retort_making_wood_vinegar_or_with_half_a_tank", 400, WoodVinegarTests::waterOffBusyRetort);
        test("a_retorts_wood_vinegar_waits_for_water_in_its_line_to_pass", 300, WoodVinegarTests::vinegarWaitsForWater);
        test("a_pipe_set_to_pull_where_one_was_broken_draws_no_water_till_the_distiller_looks", 300, WoodVinegarTests::noPullBeforeLook);
        test("a_distillers_water_keeps_off_its_feed_line_till_the_line_is_moved", 1200, WoodVinegarTests::waterOffFeedLine);
        test("a_distillers_water_keeps_off_a_retort_line_joined_while_it_holds_water", 200, WoodVinegarTests::waterOffNewRetortLine);
        test("a_bank_of_distillers_on_shared_lines_lets_its_water_out", 200, WoodVinegarTests::distillerBank);
        test("retorts_and_distillers_saved_before_wood_vinegar_load_with_it_empty", WoodVinegarTests::saves);
    }

    /**
     * The fluid, source and flowing, is in #c:wood_vinegar; its bucket holds a bucket of it and is the fluid's bucket; it
     * pours out as a pool an empty bucket takes back; its bottle is the Bottle of Wood Vinegar, both ways, poured into a
     * Copper Tank and drawn back; Copper Pipes and Tanks carry it.
     */
    private static void fluidAndBucket(GameTestHelper helper) {
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "wood_vinegar"));
        check(helper, OilFluids.WOOD_VINEGAR.get().defaultFluidState().is(tag)
                && OilFluids.FLOWING_WOOD_VINEGAR.get().defaultFluidState().is(tag), "source and flowing in #c:wood_vinegar");
        check(helper, "nookworks:wood_vinegar".equals(BuiltInRegistries.FLUID.getKey(OilFluids.WOOD_VINEGAR.get()).toString()),
                "the fluid's id is nookworks:wood_vinegar");
        var held = ItemAccess.forStack(new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get())).getCapability(Capabilities.Fluid.ITEM);
        check(helper, held != null && held.getResource(0).getFluid() == OilFluids.WOOD_VINEGAR.get() && held.getAmountAsLong(0) == 1_000,
                "its bucket holds a bucket of wood vinegar");
        check(helper, OilFluids.WOOD_VINEGAR.get().getBucket() == ModOil.WOOD_VINEGAR_BUCKET.get(), "and is the fluid's bucket");
        check(helper, Bottles.fluidOf(new ItemStack(ModOil.WOOD_VINEGAR.get())) == OilFluids.WOOD_VINEGAR.get()
                && Bottles.filled(OilFluids.WOOD_VINEGAR.get()).is(ModOil.WOOD_VINEGAR.get()),
                "the Bottle of Wood Vinegar is its bottle, both ways");
        check(helper, CopperTankBlockEntity.holds(OilFluids.WOOD_VINEGAR.get()), "the copper line carries it");

        BlockPos pool = new BlockPos(2, 2, 2);
        helper.setBlock(pool, Blocks.AIR);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, ModOil.WOOD_VINEGAR_BUCKET.get().emptyContents(player, helper.getLevel(), helper.absolutePos(pool), null),
                "the bucket pours out");
        var state = helper.getBlockState(pool);
        check(helper, state.is(ModOil.WOOD_VINEGAR_BLOCK.get()) && state.getFluidState().isSource(), "a pool of wood vinegar: " + state);
        ItemStack back = ((LiquidBlock) state.getBlock()).pickupBlock(player, helper.getLevel(), helper.absolutePos(pool), state);
        check(helper, back.is(ModOil.WOOD_VINEGAR_BUCKET.get()), "taken back up as its bucket: " + back);

        BlockPos tankAt = new BlockPos(4, 2, 2);
        helper.setBlock(tankAt, ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(tankAt, CopperTankBlockEntity.class);
        use(helper, player, tankAt, new ItemStack(ModOil.WOOD_VINEGAR.get()));
        check(helper, tank.amount() == OilFluids.BOTTLE && tank.resource().getFluid() == OilFluids.WOOD_VINEGAR.get()
                && player.getMainHandItem().is(Items.GLASS_BOTTLE), "a bottle pours a quarter bucket into a Copper Tank: " + tank.amount());
        use(helper, player, tankAt, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, tank.amount() == 0 && player.getMainHandItem().is(ModOil.WOOD_VINEGAR.get()),
                "and a glass bottle draws it back: " + player.getMainHandItem());
        helper.succeed();
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.NORTH, helper.absolutePos(pos), false));
    }

    /** A powered Retort at {@code pos} with {@code logs} oak logs in, run until they are done (or {@code ticks} at most). */
    private static RetortBlockEntity retort(GameTestHelper helper, BlockPos pos, int logs, int ticks) {
        helper.setBlock(pos, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(pos, RetortBlockEntity.class);
        retort.setItem(RetortBlockEntity.INPUT, new ItemStack(Items.OAK_LOG, logs));
        ServerLevel level = helper.getLevel();
        for (int tick = 0; tick < ticks && !retort.getItem(RetortBlockEntity.INPUT).isEmpty(); tick++) {
            retort.energy().set((int) retort.energy().getCapacityAsLong());
            RetortBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), retort);
        }
        return retort;
    }

    /**
     * Logs alone, no bottle with them, char into charcoal (into the chest below) and wood vinegar, which goes into the
     * Copper Tank beside the Retort: a bucket a log (a quarter bucket before the play-test after 1.5, when a run of the
     * Distiller came to take a bucket of it for a quarter of methanol). Its screen shows a wood vinegar tank.
     */
    private static void retort(GameTestHelper helper) {
        var recipe = MercuryRecipes.retorting(new ItemStack(Items.OAK_LOG));
        check(helper, recipe != null && !recipe.needsReagents() && recipe.byproduct().value() == Items.CHARCOAL
                && recipe.liquid().map(l -> l.getFluid() == OilFluids.WOOD_VINEGAR.get() && l.getAmount() == PER_LOG).orElse(false),
                "a log gives charcoal and a bucket of wood vinegar, with no bottle: " + recipe);
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at.east(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(at.east(), CopperTankBlockEntity.class);
        var retort = retort(helper, at, 4, 4 * (RetortBlockEntity.TICKS + 5) * 4);
        RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(at), helper.getBlockState(at), retort);
        check(helper, retort.getItem(RetortBlockEntity.INPUT).isEmpty(), "all four logs charred");
        check(helper, tank.amount() == 4 * PER_LOG && tank.resource().getFluid() == OilFluids.WOOD_VINEGAR.get(),
                "the tank beside it has the wood vinegar: " + tank.amount());
        check(helper, retort.liquidStored() == 0, "none left in the Retort: " + retort.liquidStored());
        var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
        check(helper, chest.countItem(Items.CHARCOAL) == 4 && chest.countItem(ModOil.WOOD_VINEGAR.get()) == 0,
                "charcoal below, no bottle of wood vinegar: " + chest.countItem(Items.CHARCOAL));
        check(helper, retort.guiTanks().size() == MachineLayout.RETORT.tanks().size()
                && retort.guiTanks().get(2).kind() == GuiTank.Kind.WOOD_VINEGAR, "a place on its screen for each tank, wood vinegar third");
        helper.succeed();
    }

    /** With nothing beside it to take the wood vinegar, it fills its own tank, then waits with the next log in. */
    private static void retortWaits(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        int fits = RetortBlockEntity.LIQUID_TANK / PER_LOG;
        var retort = retort(helper, at, fits + 2, (fits + 2) * (RetortBlockEntity.TICKS + 5) * 4);
        check(helper, retort.liquidStored() == RetortBlockEntity.LIQUID_TANK, "its tank full: " + retort.liquidStored());
        check(helper, retort.getItem(RetortBlockEntity.INPUT).getCount() == 2 && retort.guiStatus() == MachineStatus.TANK_FULL,
                "the rest wait, and it says why: " + retort.getItem(RetortBlockEntity.INPUT) + ", " + retort.guiStatus());
        check(helper, Move.extract(retort.fluidHandler(), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 1_000) == 1_000,
                "a pipe set to pull draws it out");
        check(helper, Move.insert(retort.fluidHandler(), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 0,
                "and nothing pours it back in");
        helper.succeed();
    }

    /** The wood vinegar a log gives, a bucket: a Distiller's run of it. */
    private static final int PER_LOG = DistillerBlockEntity.WOOD_VINEGAR_RUN;

    private static DistillerBlockEntity tower(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DISTILLER.get());
        helper.setBlock(pos.above(1), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(3), ModBlocks.CONDENSER.get());
        return helper.getBlockEntity(pos, DistillerBlockEntity.class);
    }

    private static void run(GameTestHelper helper, BlockPos pos, DistillerBlockEntity distiller, int ticks) {
        ServerLevel level = helper.getLevel();
        for (int tick = 0; tick < ticks; tick++) {
            distiller.energy().set((int) distiller.energy().getCapacityAsLong());
            DistillerBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), distiller);
        }
    }

    /**
     * Piped in (half a bucket), poured from two bottles in its charge slot and a bucket after them: two buckets' worth,
     * two runs, a quarter of each methanol kept in its tank for its Condenser's pipes, the rest water, which goes into the
     * Copper Tank beside it; below, two empty bottles and an empty bucket, and no bottle of methanol. The Distiller itself
     * gives no methanol to a pipe, and a pipe cannot draw the wood vinegar back out. A bucket clicked on it pours in.
     */
    private static void distiller(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at.east(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(at.east(), CopperTankBlockEntity.class);
        var distiller = tower(helper, at);
        check(helper, Move.insert(distiller.fluidHandler(), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 500) == 500, "piped in");
        check(helper, Move.extract(distiller.fluidHandler(), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 100) == 0,
                "and no pipe draws it back out");
        check(helper, DistillerBlockEntity.takes(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get())),
                "its charge slot takes a bucket of it");
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get(), 2));
        run(helper, at, distiller, 4);
        check(helper, distiller.woodVinegarStored() >= 750, "the bottles poured into its tank: " + distiller.woodVinegarStored());
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get()));
        run(helper, at, distiller, 8 * (DistillerBlockEntity.TICKS + 5) * 4);
        check(helper, distiller.getItem(DistillerBlockEntity.INPUT).isEmpty() && distiller.woodVinegarStored() == 0,
                "all of it distilled: " + distiller.woodVinegarStored());
        check(helper, distiller.methanolStored() == 2 * OilFluids.BOTTLE, "two runs' methanol in its tank: " + distiller.methanolStored());
        check(helper, tank.amount() == 2 * (DistillerBlockEntity.WOOD_VINEGAR_RUN - OilFluids.BOTTLE)
                && tank.resource().getFluid() == net.minecraft.world.level.material.Fluids.WATER,
                "the rest of it water, in the tank beside it: " + tank.amount() + " " + tank.resource());
        check(helper, Move.extract(distiller.fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 100) == 0,
                "no methanol for a pipe on the Distiller itself");
        var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
        check(helper, chest.countItem(Items.GLASS_BOTTLE) == 2 && chest.countItem(Items.BUCKET) == 1
                && chest.countItem(ModOil.METHANOL.get()) == 0,
                "below, the two bottles and the bucket empty, no methanol bottled: " + chest.countItem(Items.GLASS_BOTTLE) + " bottles, "
                        + chest.countItem(Items.BUCKET) + " buckets, " + chest.countItem(ModOil.METHANOL.get()) + " methanol");
        check(helper, distiller.guiTanks().size() == MachineLayout.DISTILLER.tanks().size()
                && distiller.guiTanks().get(1).kind() == GuiTank.Kind.WOOD_VINEGAR, "its screen shows the wood vinegar tank");

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, at, new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get()));
        check(helper, distiller.woodVinegarStored() == 1_000 && player.getMainHandItem().is(Items.BUCKET),
                "a bucket clicked on it pours in: " + distiller.woodVinegarStored());
        helper.succeed();
    }

    /**
     * Five buckets' worth (a tankful and four bottles) with nothing beside it: the bottles poured in as there is room and
     * out empty below; four runs fill its methanol tank (a bucket) and it waits, saying so, with the last bucket of wood
     * vinegar still in - not one bottle of methanol. Its water, with nowhere to go, let off as steam: it never waits for
     * that. A bucket on its gauge draws the methanol. Broken, its item keeps its liquids, and the Recycler turns it down.
     */
    private static void distillerWaits(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        var distiller = tower(helper, at);
        Tanks.set(distiller.woodVinegarTank(), OilFluids.WOOD_VINEGAR.get(), DistillerBlockEntity.WOOD_VINEGAR_TANK);
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get(), 4));
        run(helper, at, distiller, 6 * (DistillerBlockEntity.TICKS + 5) * 4);
        var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
        check(helper, chest.countItem(Items.GLASS_BOTTLE) == 4 && chest.countItem(ModOil.METHANOL.get()) == 0,
                "four empty bottles below, no methanol in a bottle: " + chest.countItem(Items.GLASS_BOTTLE) + ", "
                        + chest.countItem(ModOil.METHANOL.get()));
        check(helper, distiller.methanolStored() == DistillerBlockEntity.METHANOL_TANK
                && distiller.woodVinegarStored() == DistillerBlockEntity.WOOD_VINEGAR_RUN,
                "its methanol tank full, the last bucket of wood vinegar waiting: " + distiller.methanolStored() + ", "
                        + distiller.woodVinegarStored());
        check(helper, distiller.waterStored() == DistillerBlockEntity.WATER_TANK, "its water tank full, the rest let off: " + distiller.waterStored());
        check(helper, distiller.guiStatus() == MachineStatus.TANK_FULL, "and it says so: " + distiller.guiStatus());
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.inventoryMenu.setCarried(new ItemStack(Items.BUCKET));
        check(helper, dev.nookworks.machine.GaugePouring.pour(player, player.inventoryMenu, distiller.gaugeTank(0), GuiTank.Kind.METHANOL)
                && player.inventoryMenu.getCarried().is(ModOil.METHANOL_BUCKET.get()) && distiller.methanolStored() == 0,
                "a bucket on its methanol gauge draws a bucket: " + player.inventoryMenu.getCarried());

        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(at);
        ItemStack item = Block.getDrops(level.getBlockState(abs), level, abs, level.getBlockEntity(abs)).stream()
                .filter(s -> s.is(ModItems.DISTILLER.get())).findFirst().orElse(ItemStack.EMPTY);
        var kept = item.get(dev.nookworks.registry.ModDataComponents.HELD_FLUIDS.get());
        check(helper, kept != null && kept.stream().anyMatch(f -> f.copy().getFluid() == OilFluids.WOOD_VINEGAR.get()),
                "broken, its item keeps the wood vinegar: " + item.getComponentsPatch());
        check(helper, RecyclingRecipe.holdsThings(item), "and the Recycler turns it down");
        helper.succeed();
    }

    /**
     * The tower's outlets (the user, 2026-11-12): its mercury leaves at the Condenser by a Mercury Pipe only - a Mercury
     * Tank set against the Condenser gets none - and its water leaves the Distiller's sides, but never the side its wood
     * vinegar came in by: so not back up the Retort's line.
     */
    private static void distillerOutlets(GameTestHelper helper) {
        BlockPos at = new BlockPos(2, 2, 2);
        var distiller = tower(helper, at);
        BlockPos top = at.above(3);
        helper.setBlock(top.south(), ModBlocks.MERCURY_TANK.get());
        mercuryPipe(helper, top.north());
        helper.setBlock(top.north(2), ModBlocks.MERCURY_TANK.get());
        ItemStack held = new ItemStack(Items.STONE); // the mercury a run of amalgam gives back, as its item would carry it
        held.set(dev.nookworks.registry.ModDataComponents.HELD_FLUIDS.get(), java.util.List.of(net.neoforged.neoforge.fluids.SimpleFluidContent.copyOf(
                new net.neoforged.neoforge.fluids.FluidStack(dev.nookworks.mercury.MercuryFluids.SOURCE.get(), 500))));
        distiller.applyComponentsFromItemStack(held);
        check(helper, distiller.mercury() == 500, "500 mB of mercury in it: " + distiller.mercury());
        // wood vinegar comes in by its west side; a tank on each side of it
        helper.setBlock(at.west(), ModBlocks.COPPER_TANK.get());
        helper.setBlock(at.east(), ModBlocks.COPPER_TANK.get());
        check(helper, Move.insert(distiller.fluidHandler(Direction.WEST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()),
                DistillerBlockEntity.WOOD_VINEGAR_RUN) == DistillerBlockEntity.WOOD_VINEGAR_RUN, "a run of wood vinegar in by its west side");
        var west = helper.getBlockEntity(at.west(), CopperTankBlockEntity.class);
        var east = helper.getBlockEntity(at.east(), CopperTankBlockEntity.class);
        var against = helper.getBlockEntity(top.south(), dev.nookworks.mercury.MercuryTankBlockEntity.class);
        var piped = helper.getBlockEntity(top.north(2), dev.nookworks.mercury.MercuryTankBlockEntity.class);
        helper.onEachTick(() -> distiller.energy().set((int) distiller.energy().getCapacityAsLong()));
        helper.succeedWhen(() -> {
            check(helper, piped.fillMercury(Integer.MAX_VALUE, true) < dev.nookworks.mercury.MercuryTankBlockEntity.CAPACITY,
                    "the mercury down the pipe from the Condenser into the tank at its end");
            check(helper, against.fillMercury(Integer.MAX_VALUE, true) == dev.nookworks.mercury.MercuryTankBlockEntity.CAPACITY,
                    "none into the tank set against the Condenser");
            check(helper, east.amount() == DistillerBlockEntity.WOOD_VINEGAR_RUN - OilFluids.BOTTLE && west.amount() == 0,
                    "the water out of its east side, none by the west its wood vinegar came in by: " + east.amount() + ", " + west.amount());
        });
    }

    private static void copperPipe(GameTestHelper helper, BlockPos pipe) {
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(
                helper.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_PIPE.get()),
                new BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pipe)), Direction.UP, helper.absolutePos(pipe), false))));
    }

    /**
     * A Distiller with water to give, a Copper Pipe line from its west side to a Sealed Retort (which takes water by pipe)
     * and one from its east to a Copper Tank: no wood vinegar has come in yet - by a line or by hand - so the still knows
     * of nothing it comes from, and that Retort may be its own: none of the water goes up the Retort's line, only to the
     * tank (the user, 2026-11-13: an idle still sent its water back to the Retort, to be boiled into salt; a still from
     * before 1.7 knows no line). A shut side gives no water to what draws from it either.
     */
    private static void waterNotToRetort(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        copperPipe(helper, at.west());
        copperPipe(helper, at.west(2));
        helper.setBlock(at.west(3), ModBlocks.RETORT.get());
        copperPipe(helper, at.east());
        helper.setBlock(at.east(2), ModBlocks.COPPER_TANK.get());
        Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        var retort = helper.getBlockEntity(at.west(3), RetortBlockEntity.class);
        var tank = helper.getBlockEntity(at.east(2), CopperTankBlockEntity.class);
        helper.runAfterDelay(100, () -> {
            check(helper, retort.waterStored() == 0, "none up the Retort's line: " + retort.waterStored());
            int first = tank.amount();
            check(helper, first > 0, "the water out east, to the tank: " + first);
            check(helper, Move.extract(distiller.fluidHandler(Direction.WEST), FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 100) == 0,
                    "nor drawn out by the Retort's side");
            // a second run's water, the east pipe having stood empty, asking the Distiller every tick what it would take
            // (a pipe's asking once marked its side as the wood vinegar's, and no water left by pipe again)
            Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
            helper.runAfterDelay(100, () -> {
                check(helper, tank.amount() > first, "a second run's water to the tank too: " + first + " -> " + tank.amount());
                check(helper, retort.waterStored() == 0, "and still none to the Retort: " + retort.waterStored());
                helper.succeed();
            });
        });
    }

    /**
     * A still fed by a Sealed Retort against its west side's pipe, a second line from its north side round to that same
     * Retort, and a line of its own from its east side to another Retort: the water goes to the other Retort, and none to
     * the one feeding it by either line (the user, 2026-11-14: 「木酢液に全く関係ない別のレトルト炉に別のパイプで蒸留器から直接
     * 水を送るのは、できた方がいい」, and of the feeding one 「出来なくてよいと思う」).
     */
    private static void waterToOtherRetort(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        copperPipe(helper, at.west());
        helper.setBlock(at.west(2), ModBlocks.RETORT.get()); // the Retort that feeds it
        for (BlockPos pipe : List.of(at.north(), at.north(2), at.north(2).west(), at.north(2).west(2), at.north().west(2))) {
            copperPipe(helper, pipe); // round to the feeding Retort's north face, apart from the west line
        }
        copperPipe(helper, at.east());
        helper.setBlock(at.east(2), ModBlocks.RETORT.get()); // another Retort, on a line of its own
        check(helper, Move.insert(distiller.fluidHandler(Direction.WEST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                "wood vinegar in by its west side, from its Retort");
        Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        var feeder = helper.getBlockEntity(at.west(2), RetortBlockEntity.class);
        var other = helper.getBlockEntity(at.east(2), RetortBlockEntity.class);
        helper.runAfterDelay(100, () -> {
            check(helper, other.waterStored() > 0, "the water to the other Retort: " + other.waterStored());
            check(helper, feeder.waterStored() == 0, "none to the one feeding it, by either line: " + feeder.waterStored());
            helper.succeed();
        });
    }

    /**
     * Two stills with water to give, each with a line from its east side to a Sealed Retort, no wood vinegar come in by
     * any line (one had some by its top, the column's place, never noted): neither gives its Retort water (it may be the
     * still's own). Then one has a bucket of wood vinegar poured at its gauge, the other a bottle put in its charge slot:
     * knowing where their wood vinegar comes from - by hand - both give their Retorts water. (The top's mark was never
     * cleared, and no pour by hand was seen after it: 2026-11-14 review.)
     */
    private static void waterToRetortPouredByHand(GameTestHelper helper) {
        BlockPos gauged = new BlockPos(2, 2, 1);
        BlockPos charged = new BlockPos(2, 2, 4);
        var byGauge = tower(helper, gauged);
        var byCharge = tower(helper, charged);
        for (BlockPos at : List.of(gauged, charged)) {
            copperPipe(helper, at.east());
            helper.setBlock(at.east(2), ModBlocks.RETORT.get());
        }
        for (var still : List.of(byGauge, byCharge)) {
            Tanks.set(still.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        }
        check(helper, Move.insert(byGauge.fluidHandler(Direction.UP), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                "wood vinegar in by its top");
        var gaugedRetort = helper.getBlockEntity(gauged.east(2), RetortBlockEntity.class);
        var chargedRetort = helper.getBlockEntity(charged.east(2), RetortBlockEntity.class);
        helper.runAfterDelay(60, () -> {
            check(helper, gaugedRetort.waterStored() == 0 && chargedRetort.waterStored() == 0,
                    "none to either Retort yet: " + gaugedRetort.waterStored() + ", " + chargedRetort.waterStored());
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.inventoryMenu.setCarried(new ItemStack(ModOil.WOOD_VINEGAR_BUCKET.get()));
            check(helper, dev.nookworks.machine.GaugePouring.pour(player, player.inventoryMenu, byGauge.gaugeTank(1), GuiTank.Kind.WOOD_VINEGAR)
                    && byGauge.woodVinegarStored() == 1_250, "a bucket poured at its gauge: " + byGauge.woodVinegarStored());
            byCharge.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get()));
            helper.runAfterDelay(60, () -> {
                check(helper, gaugedRetort.waterStored() > 0, "poured at its gauge, its water to the Retort: " + gaugedRetort.waterStored());
                check(helper, chargedRetort.waterStored() > 0, "poured from its charge slot, its water to the Retort: " + chargedRetort.waterStored());
                // then fed by a line: known by that line, no longer by the bottle (its line moved, it knows of nothing)
                check(helper, Move.insert(byCharge.fluidHandler(Direction.EAST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                        "wood vinegar in by its east line");
                helper.runAfterDelay(40, () -> {
                    var saved = byCharge.saveWithoutMetadata(helper.getLevel().registryAccess());
                    check(helper, !saved.contains("wood_vinegar_poured") && saved.contains("wood_vinegar_feeds"),
                            "known by its line now, not by the bottle: " + saved.keySet());
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A still that knows where its wood vinegar comes from (a tank against its west side), with lines to two Sealed
     * Retorts it never had wood vinegar from - east, one with a log in it (piped in to feed the still, its logs come
     * before its wood vinegar has); north, one unpowered, half its water tank full - and south to a tank: the water goes
     * only to the tank. Its log taken out, the first has water; its water drawn off, the second too (2026-11-14 review:
     * the water stood in a new feeding Retort's pipes, and its wood vinegar had nowhere to go).
     */
    private static void waterOffBusyRetort(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        helper.setBlock(at.west(), ModBlocks.COPPER_TANK.get());
        copperPipe(helper, at.east());
        helper.setBlock(at.east(2), ModBlocks.RETORT.get());
        copperPipe(helper, at.north());
        helper.setBlock(at.north(2), ModBlocks.RETORT.get());
        copperPipe(helper, at.south());
        helper.setBlock(at.south(2), ModBlocks.COPPER_TANK.get());
        var charged = helper.getBlockEntity(at.east(2), RetortBlockEntity.class);
        var filled = helper.getBlockEntity(at.north(2), RetortBlockEntity.class);
        var tank = helper.getBlockEntity(at.south(2), CopperTankBlockEntity.class);
        charged.setItem(RetortBlockEntity.INPUT, new ItemStack(Items.OAK_LOG));
        Tanks.set((FluidStacksResourceHandler) filled.waterHandler(), net.minecraft.world.level.material.Fluids.WATER, RetortBlockEntity.WATER_TANK / 2);
        check(helper, Move.insert(distiller.fluidHandler(Direction.WEST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                "wood vinegar in by its west side, from the tank");
        helper.onEachTick(() -> {
            if (distiller.waterStored() < 500) {
                Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK); // a still at work
            }
        });
        helper.runAfterDelay(100, () -> {
            check(helper, tank.amount() > 0, "the water to the tank: " + tank.amount());
            check(helper, charged.waterStored() == 0, "none to the Retort with a log in it: " + charged.waterStored());
            check(helper, filled.waterStored() == RetortBlockEntity.WATER_TANK / 2, "none to the half-full one: " + filled.waterStored());
            charged.setItem(RetortBlockEntity.INPUT, ItemStack.EMPTY);
            Tanks.set((FluidStacksResourceHandler) filled.waterHandler(), net.minecraft.world.level.material.Fluids.WATER, 0);
            helper.runAfterDelay(100, () -> {
                check(helper, charged.waterStored() > 0, "its log out, water to it: " + charged.waterStored());
                check(helper, filled.waterStored() > 0, "its water drawn off, water to it: " + filled.waterStored());
                helper.succeed();
            });
        });
    }

    /**
     * A Sealed Retort with wood vinegar to give off, an empty pipe beside it and beyond that a pipe holding water on its
     * way to the Retort, then a Distiller: the empty pipe leads nowhere for the wood vinegar while the water stands in
     * the way, so it is not sent in to meet the water head on (neither could pass, and the Retort stopped: 2026-11-14
     * review); the water goes into the Retort, and then the wood vinegar to the Distiller.
     */
    private static void vinegarWaitsForWater(GameTestHelper helper) {
        BlockPos still = new BlockPos(3, 2, 3);
        var distiller = tower(helper, still);
        copperPipe(helper, still.west());
        copperPipe(helper, still.west(2));
        BlockPos retortAt = still.west(3);
        helper.setBlock(retortAt, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(retortAt, RetortBlockEntity.class);
        var near = helper.getBlockEntity(still.west(2), dev.nookworks.fluid.FluidPipeBlockEntity.class);
        var far = helper.getBlockEntity(still.west(), dev.nookworks.fluid.FluidPipeBlockEntity.class);
        check(helper, Move.insert(far.handlerFor(null), FluidResource.of(net.minecraft.world.level.material.Fluids.WATER), 500) == 500, "water in the pipe by the still");
        FluidResource vinegar = FluidResource.of(OilFluids.WOOD_VINEGAR.get());
        check(helper, !near.leadsToTakerOf(helper.getLevel(), vinegar, helper.absolutePos(retortAt), 512),
                "the empty pipe by the Retort leads nowhere for wood vinegar, water standing in the way");
        Tanks.set(retort.liquidTank(), OilFluids.WOOD_VINEGAR.get(), 1_000);
        helper.succeedWhen(() -> {
            check(helper, retort.waterStored() == 500, "the water into the Retort: " + retort.waterStored());
            check(helper, distiller.woodVinegarStored() > 0, "and the wood vinegar to the still: " + distiller.woodVinegarStored());
        });
    }

    /**
     * A still holding water, a pipe on its east side leading to a Sealed Retort (shut: the still knows of nothing its
     * wood vinegar comes from): the pipe broken, and once the still has looked at the gap, a pipe set to pull from it at
     * other points of the second, each time taken away again - none of the water goes to the Retort. The side, open at a
     * look that found a gap, gave water to a pulling pipe set there before the next (2026-11-14 review).
     */
    private static void noPullBeforeLook(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        copperPipe(helper, at.east());
        helper.setBlock(at.east(2), ModBlocks.RETORT.get());
        Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        var retort = helper.getBlockEntity(at.east(2), RetortBlockEntity.class);
        helper.runAfterDelay(25, () -> helper.setBlock(at.east(), Blocks.AIR));
        int[][] spells = {{50, 55}, {76, 84}, {102, 115}, {131, 150}};
        for (int[] spell : spells) {
            helper.runAfterDelay(spell[0], () -> {
                copperPipe(helper, at.east());
                var pipe = helper.getBlockEntity(at.east(), dev.nookworks.fluid.FluidPipeBlockEntity.class);
                check(helper, pipe.cycle(helper.getLevel(), Direction.WEST) == dev.nookworks.fluid.FluidPipeBlockEntity.SideSetting.PULLING,
                        "set to pull from the still");
            });
            helper.runAfterDelay(spell[1], () -> helper.setBlock(at.east(), Blocks.AIR));
        }
        helper.runAfterDelay(170, () -> {
            check(helper, retort.waterStored() == 0, "none drawn to the Retort: " + retort.waterStored());
            check(helper, distiller.waterStored() > 0, "the water kept: " + distiller.waterStored());
            helper.succeed();
        });
    }

    /**
     * Wood vinegar comes in by the Distiller's west side from a tank between it and where it is made: that line takes
     * none of the water, the tank empty or not (water in it, it took no wood vinegar after, and the Retort stopped).
     * Moved - the tank taken away, the line run on to another tank for the water - the side takes water again, once its
     * line has missed what fed it for half a minute (a pipe of it changed is not a line moved) (the user, 2026-11-13: a
     * pipe kept when the machines are moved about).
     */
    private static void waterOffFeedLine(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        copperPipe(helper, at.west());
        helper.setBlock(at.west(2), ModBlocks.COPPER_TANK.get());
        copperPipe(helper, at.east());
        helper.setBlock(at.east(2), ModBlocks.COPPER_TANK.get());
        check(helper, Move.insert(distiller.fluidHandler(Direction.WEST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                "wood vinegar in by its west side, as the line gives it");
        Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        var buffer = helper.getBlockEntity(at.west(2), CopperTankBlockEntity.class);
        var east = helper.getBlockEntity(at.east(2), CopperTankBlockEntity.class);
        helper.runAfterDelay(80, () -> {
            check(helper, buffer.amount() == 0, "none into the empty tank its wood vinegar came by: " + buffer.amount());
            check(helper, east.amount() > 0, "the water out east: " + east.amount());
            // moved: the tank taken away, the line run on past where it stood to a tank for water
            helper.setBlock(at.west(2), Blocks.AIR);
            copperPipe(helper, at.west(2));
            helper.setBlock(at.west(3), ModBlocks.COPPER_TANK.get());
            helper.setBlock(at.east(), Blocks.AIR); // the east line gone too: the water stays, and the still keeps looking
            Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
            var collector = helper.getBlockEntity(at.west(3), CopperTankBlockEntity.class);
            helper.runAfterDelay(200, () -> check(helper, collector.amount() == 0, "not at once: a line being changed is not moved"));
            helper.runAfterDelay(800, () -> {
                int first = collector.amount();
                check(helper, first > 0, "moved, the west line takes water again: " + first);
                Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
                helper.runAfterDelay(100, () -> {
                    check(helper, collector.amount() > first, "and keeps taking it, its pipes stood empty a while: " + first + " -> " + collector.amount());
                    helper.succeed();
                });
            });
        });
    }

    /**
     * A Distiller holding water (knowing of nothing its wood vinegar comes from, so a Retort may be its own), nothing
     * against its west side, and beyond that gap a pipe line to a Sealed Retort; the
     * pipe that joins them set mid-second, taken away and set again at other points of the second: none of the water goes
     * to the Retort. The outlet asks along a line it has not seen at once, and on its word alone it poured into the newly
     * joined line before the Distiller looked (2026-11-13 review).
     */
    private static void waterOffNewRetortLine(GameTestHelper helper) {
        BlockPos at = new BlockPos(3, 2, 3);
        var distiller = tower(helper, at);
        copperPipe(helper, at.west(2));
        helper.setBlock(at.west(3), ModBlocks.RETORT.get());
        Tanks.set(distiller.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        var retort = helper.getBlockEntity(at.west(3), RetortBlockEntity.class);
        int[][] spells = {{7, 12}, {33, 41}, {59, 90}};
        for (int[] spell : spells) {
            helper.runAfterDelay(spell[0], () -> copperPipe(helper, at.west()));
            helper.runAfterDelay(spell[1], () -> helper.setBlock(at.west(), Blocks.AIR));
        }
        helper.runAfterDelay(120, () -> {
            check(helper, retort.waterStored() == 0, "none to the Retort the line was joined to: " + retort.waterStored());
            check(helper, distiller.waterStored() > 0, "the water kept: " + distiller.waterStored());
            helper.succeed();
        });
    }

    /**
     * Two stills side by side on one feed manifold (their west sides, from a tank) and one water manifold (their east
     * sides, to a collecting tank): their water goes out. Each still's feed line reaches the other still, and noted as
     * what fed it, each shut the other's water line - the whole bank let its water off as steam (2026-11-13 review).
     */
    private static void distillerBank(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 2);
        BlockPos b = new BlockPos(2, 2, 4);
        var first = tower(helper, a);
        var second = tower(helper, b);
        for (int z = 2; z <= 4; z++) {
            copperPipe(helper, new BlockPos(1, 2, z));
            copperPipe(helper, new BlockPos(3, 2, z));
        }
        helper.setBlock(new BlockPos(0, 2, 3), ModBlocks.COPPER_TANK.get()); // where the wood vinegar comes from
        helper.setBlock(new BlockPos(4, 2, 3), ModBlocks.COPPER_TANK.get()); // where the water goes
        for (var still : List.of(first, second)) {
            check(helper, Move.insert(still.fluidHandler(Direction.WEST), FluidResource.of(OilFluids.WOOD_VINEGAR.get()), 250) == 250,
                    "wood vinegar in by its west side");
            Tanks.set(still.waterTank(), net.minecraft.world.level.material.Fluids.WATER, DistillerBlockEntity.WATER_TANK);
        }
        var collector = helper.getBlockEntity(new BlockPos(4, 2, 3), CopperTankBlockEntity.class);
        var feed = helper.getBlockEntity(new BlockPos(0, 2, 3), CopperTankBlockEntity.class);
        helper.runAfterDelay(120, () -> {
            check(helper, collector.amount() >= DistillerBlockEntity.WATER_TANK, "both stills' water collected: " + collector.amount());
            check(helper, feed.amount() == 0, "none to the feed tank: " + feed.amount());
            helper.succeed();
        });
    }

    private static void mercuryPipe(GameTestHelper helper, BlockPos pipe) {
        helper.setBlock(pipe, ModBlocks.MERCURY_PIPE.get().getStateForPlacement(new net.minecraft.world.item.context.BlockPlaceContext(
                helper.getLevel(), null, net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModBlocks.MERCURY_PIPE.get()),
                new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(helper.absolutePos(pipe)),
                        Direction.UP, helper.absolutePos(pipe), false))));
    }

    /** Saved before 1.5 - no "liquid", no "wood_vinegar" - they load with those tanks empty; saved with them, read back. */
    private static void saves(GameTestHelper helper) {
        BlockPos retortAt = new BlockPos(1, 2, 1);
        helper.setBlock(retortAt, ModBlocks.RETORT.get());
        var retort = helper.getBlockEntity(retortAt, RetortBlockEntity.class);
        Tanks.set(retort.liquidTank(), OilFluids.WOOD_VINEGAR.get(), 750);
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        retort.saveCustomOnly(saved);
        var tag = saved.buildResult();
        check(helper, tag.getCompound("liquid").isPresent(), "its wood vinegar saved as \"liquid\"");
        var reread = new RetortBlockEntity(helper.absolutePos(retortAt), helper.getBlockState(retortAt));
        reread.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        check(helper, reread.liquidStored() == 750, "and read back: " + reread.liquidStored());
        tag.remove("liquid");
        var old = new RetortBlockEntity(helper.absolutePos(retortAt), helper.getBlockState(retortAt));
        old.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        check(helper, old.liquidStored() == 0, "an old Retort loads with it empty");

        BlockPos at = new BlockPos(4, 2, 1);
        var distiller = tower(helper, at);
        Tanks.set(distiller.woodVinegarTank(), OilFluids.WOOD_VINEGAR.get(), 500);
        var again = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        distiller.saveCustomOnly(again);
        var dtag = again.buildResult();
        check(helper, dtag.getCompound("wood_vinegar").isPresent(), "the Distiller's saved as \"wood_vinegar\"");
        var back = new DistillerBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        back.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), dtag));
        check(helper, back.woodVinegarStored() == 500, "and read back: " + back.woodVinegarStored());
        dtag.remove("wood_vinegar");
        var oldDistiller = new DistillerBlockEntity(helper.absolutePos(at), helper.getBlockState(at));
        oldDistiller.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), dtag));
        check(helper, oldDistiller.woodVinegarStored() == 0, "an old Distiller loads with it empty");
        check(helper, CopperTankBlock.bottleOf(new ItemStack(ModOil.WOOD_VINEGAR.get())) == OilFluids.WOOD_VINEGAR.get(),
                "the Copper Tank's bottle table reads the same");
        helper.succeed();
    }
}
