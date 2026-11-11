package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

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
     * Copper Tank beside the Retort: a quarter bucket a log. Its screen shows a wood vinegar tank, a place for each.
     */
    private static void retort(GameTestHelper helper) {
        var recipe = MercuryRecipes.retorting(new ItemStack(Items.OAK_LOG));
        check(helper, recipe != null && !recipe.needsReagents() && recipe.byproduct().value() == Items.CHARCOAL
                && recipe.liquid().map(l -> l.getFluid() == OilFluids.WOOD_VINEGAR.get() && l.getAmount() == OilFluids.BOTTLE).orElse(false),
                "a log gives charcoal and a quarter bucket of wood vinegar, with no bottle: " + recipe);
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at.east(), ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(at.east(), CopperTankBlockEntity.class);
        var retort = retort(helper, at, 4, 4 * (RetortBlockEntity.TICKS + 5) * 4);
        RetortBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(at), helper.getBlockState(at), retort);
        check(helper, retort.getItem(RetortBlockEntity.INPUT).isEmpty(), "all four logs charred");
        check(helper, tank.amount() == 4 * OilFluids.BOTTLE && tank.resource().getFluid() == OilFluids.WOOD_VINEGAR.get(),
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
        int fits = RetortBlockEntity.LIQUID_TANK / OilFluids.BOTTLE;
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
     * Piped in (half a bucket), poured from two bottles in its charge slot and a bucket after them: the eight bottles'
     * worth distil to methanol in the Copper Tank beside it; below, two empty bottles and an empty bucket, and no bottle of
     * methanol. A pipe cannot draw the wood vinegar back out. A bucket clicked on it pours into its tank.
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
        check(helper, tank.amount() == 8 * OilFluids.BOTTLE && tank.resource().getFluid() == OilFluids.METHANOL.get(),
                "eight bottles' worth of methanol in the tank beside it: " + tank.amount());
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
     * Eight bottles with nothing beside it: their wood vinegar poured in and the bottles out empty below; its methanol
     * tank fills (a bucket) and it waits, saying so, with the rest of the wood vinegar still in - not one bottle of
     * methanol. A bucket on its gauge draws the methanol. Broken, its item keeps both liquids, and the Recycler turns it down.
     */
    private static void distillerWaits(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        var distiller = tower(helper, at);
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get(), 8));
        run(helper, at, distiller, 8 * (DistillerBlockEntity.TICKS + 5) * 4);
        var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
        check(helper, chest.countItem(Items.GLASS_BOTTLE) == 8 && chest.countItem(ModOil.METHANOL.get()) == 0,
                "eight empty bottles below, no methanol in a bottle: " + chest.countItem(Items.GLASS_BOTTLE) + ", "
                        + chest.countItem(ModOil.METHANOL.get()));
        check(helper, distiller.methanolStored() == DistillerBlockEntity.METHANOL_TANK
                && distiller.woodVinegarStored() == 8 * OilFluids.BOTTLE - DistillerBlockEntity.METHANOL_TANK,
                "its methanol tank full, the rest of the wood vinegar waiting: " + distiller.methanolStored() + ", "
                        + distiller.woodVinegarStored());
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
