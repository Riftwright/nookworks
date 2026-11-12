package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperTankBlock;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.LineContent;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.machine.GaugePouring;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.context.BlockPlaceContext;
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
 * Methanol as a liquid (1.5; the user, 2026-11-07, relayed by nookTechnology's session: methanol to be handled as a
 * liquid): its bottle pours into a Copper Tank and is drawn back, the Vacuum Distiller pipes it off - and since wood
 * vinegar came in as a liquid (2026-11-10), keeps it in its tank with nothing to take it, bottling none - the Stirring Tank makes biodiesel of it piped in, it is #c:methanol, and its bucket and
 * pool work as oil's do. Old Distillers load with an empty methanol tank.
 */
final class MethanolTests {
    private MethanolTests() {}

    static void register() {
        test("a_methanol_bottle_pours_into_a_copper_tank_and_draws_back", MethanolTests::copperTank);
        test("the_distiller_pipes_methanol_off_and_keeps_it_with_nothing_to_take_it", 200, MethanolTests::distillerPipes);
        test("the_stirring_tank_makes_biodiesel_of_piped_methanol", 400, MethanolTests::stirringTank);
        test("methanol_is_c_methanol_with_a_bucket_and_a_pool", MethanolTests::fluidAndBucket);
        test("a_distiller_saved_before_methanol_loads_and_keeps_it_after", MethanolTests::distillerSave);
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.NORTH, helper.absolutePos(pos), false));
    }

    /**
     * A bottle of methanol pours a quarter bucket into a Copper Tank and comes back empty; an empty bottle draws one back
     * out as a Bottle of Methanol; its bucket pours and draws a bucket; the glass shows methanol; oil will not go in on it.
     * By the Stirring Tank's methanol gauge a bottle pours into that tank.
     */
    private static void copperTank(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pos, CopperTankBlockEntity.class);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, pos, new ItemStack(ModOil.METHANOL.get()));
        check(helper, tank.amount() == OilFluids.BOTTLE && tank.resource().getFluid() == OilFluids.METHANOL.get(),
                "a bottle of methanol pours in a quarter bucket: " + tank.amount() + " of " + tank.resource().getFluid());
        check(helper, player.getMainHandItem().is(Items.GLASS_BOTTLE), "and the bottle comes back empty: " + player.getMainHandItem());
        check(helper, helper.getBlockState(pos).getValue(LineContent.PROPERTY) == LineContent.METHANOL, "the glass shows methanol");
        use(helper, player, pos, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()));
        check(helper, tank.amount() == OilFluids.BOTTLE && player.getMainHandItem().is(ModOil.VEGETABLE_OIL_BOTTLE.get()),
                "oil will not go in on methanol");
        use(helper, player, pos, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, tank.amount() == 0 && player.getMainHandItem().is(ModOil.METHANOL.get()),
                "an empty bottle draws it back as a Bottle of Methanol: " + tank.amount() + ", " + player.getMainHandItem());
        use(helper, player, pos, new ItemStack(ModOil.METHANOL_BUCKET.get()));
        check(helper, tank.amount() == 1_000 && player.getMainHandItem().is(Items.BUCKET), "its bucket pours in a bucket: " + tank.amount());
        use(helper, player, pos, new ItemStack(Items.BUCKET));
        check(helper, tank.amount() == 0 && player.getMainHandItem().is(ModOil.METHANOL_BUCKET.get()),
                "and an empty bucket draws it out again: " + player.getMainHandItem());
        check(helper, CopperTankBlock.bottleOf(new ItemStack(ModOil.METHANOL.get())) == OilFluids.METHANOL.get()
                && CopperTankBlock.filledBottle(OilFluids.METHANOL.get()).is(ModOil.METHANOL.get()),
                "the bottle is methanol's, both ways (as other mods' screens pour by them)");

        BlockPos stirring = new BlockPos(5, 2, 2);
        helper.setBlock(stirring, ModBlocks.STIRRING_TANK.get());
        var stirrer = helper.getBlockEntity(stirring, StirringTankBlockEntity.class);
        int gauge = stirrer.guiTanks().indexOf(stirrer.guiTanks().stream().filter(t -> t.kind() == GuiTank.Kind.METHANOL).findFirst().orElseThrow());
        check(helper, stirrer.guiTanks().size() == MachineLayout.STIRRING_TANK.tanks().size(), "a place on its screen for each tank");
        var menu = player.inventoryMenu;
        menu.setCarried(new ItemStack(ModOil.METHANOL.get()));
        check(helper, !GaugePouring.pour(player, menu, stirrer.gaugeTank(2), GuiTank.Kind.VEGETABLE_OIL), "not by the oil's gauge");
        check(helper, GaugePouring.pour(player, menu, stirrer.gaugeTank(gauge), GuiTank.Kind.METHANOL)
                && stirrer.methanolStored() == OilFluids.BOTTLE && menu.getCarried().is(Items.GLASS_BOTTLE),
                "a bottle on the Stirring Tank's methanol gauge pours into its methanol tank: " + stirrer.methanolStored());
        helper.succeed();
    }

    /** A Vacuum Distiller's tower at {@code pos}, powered, with {@code vinegar} wood vinegar run through it. */
    private static DistillerBlockEntity distil(GameTestHelper helper, BlockPos pos, int vinegar) {
        var distiller = tower(helper, pos);
        distiller.setItem(DistillerBlockEntity.INPUT, new ItemStack(ModOil.WOOD_VINEGAR.get(), vinegar));
        ServerLevel level = helper.getLevel();
        // poured into its tank first (since 2026-11-10), then distilled from there
        for (int tick = 0; tick < vinegar * (DistillerBlockEntity.TICKS + 5) * 4
                && (!distiller.getItem(DistillerBlockEntity.INPUT).isEmpty() || distiller.woodVinegarStored() >= DistillerBlockEntity.WOOD_VINEGAR_RUN); tick++) {
            distiller.energy().set((int) distiller.energy().getCapacityAsLong());
            DistillerBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), distiller);
        }
        DistillerBlockEntity.serverTick(level, helper.absolutePos(pos), helper.getBlockState(pos), distiller);
        check(helper, distiller.getItem(DistillerBlockEntity.INPUT).isEmpty() && distiller.woodVinegarStored() == 0,
                "all the wood vinegar distilled");
        return distiller;
    }

    private static DistillerBlockEntity tower(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.DISTILLER.get());
        helper.setBlock(pos.above(1), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(2), ModBlocks.DISTILLATION_COLUMN.get());
        helper.setBlock(pos.above(3), ModBlocks.CONDENSER.get());
        return helper.getBlockEntity(pos, DistillerBlockEntity.class);
    }

    private static void pipe(GameTestHelper helper, BlockPos pipe) {
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().getStateForPlacement(new BlockPlaceContext(
                helper.getLevel(), null, InteractionHand.MAIN_HAND, new ItemStack(ModItems.COPPER_PIPE.get()),
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pipe)), Direction.UP, helper.absolutePos(pipe), false))));
    }

    private static int count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        return chest.countItem(item);
    }

    /**
     * Four bottles (a run) each; the methanol leaves at the Condenser, by pipe only (the user, 2026-11-12): a Copper Tank
     * set against the Condenser, or beside the Distiller, gets none; a Copper Pipe on the Condenser leading to a tank sends
     * it down into that tank; a pipe leading nowhere, or nothing at all, and it stays in the Distiller's tank; a pipe on the
     * Condenser whose line also runs down to the Distiller gets none (it would plug the line). Never bottled.
     */
    private static void distillerPipes(GameTestHelper helper) {
        int run = OilFluids.BOTTLE; // a run's methanol, of four bottles of wood vinegar
        BlockPos byTank = new BlockPos(1, 2, 1);
        helper.setBlock(byTank.below(), Blocks.CHEST);
        tower(helper, byTank);
        helper.setBlock(byTank.above(3).east(), ModBlocks.COPPER_TANK.get());
        var againstCondenser = helper.getBlockEntity(byTank.above(3).east(), CopperTankBlockEntity.class);
        var first = distil(helper, byTank, 4);
        var firstChest = helper.getBlockEntity(byTank.below(), ChestBlockEntity.class);
        check(helper, againstCondenser.amount() == 0 && first.methanolStored() == run,
                "a tank set against the Condenser gets none; it stays in the Distiller: " + againstCondenser.amount() + ", " + first.methanolStored());
        check(helper, count(firstChest, Items.GLASS_BOTTLE) == 4 && count(firstChest, ModOil.METHANOL.get()) == 0,
                "the four bottles below, empty: " + count(firstChest, Items.GLASS_BOTTLE) + " empty, " + count(firstChest, ModOil.METHANOL.get()) + " filled");
        check(helper, helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                helper.absolutePos(byTank.above(3)), Direction.DOWN) == null
                && helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Fluid.BLOCK,
                        helper.absolutePos(byTank.above(3)), Direction.EAST) != null, "the Condenser shows it on its sides, not underneath");

        BlockPos byPipe = new BlockPos(5, 2, 1);
        helper.setBlock(byPipe.below(), Blocks.CHEST);
        tower(helper, byPipe);
        BlockPos far = byPipe.above(3).east(2);
        helper.setBlock(far, ModBlocks.COPPER_TANK.get());
        pipe(helper, byPipe.above(3).east());
        var farTank = helper.getBlockEntity(far, CopperTankBlockEntity.class);
        var second = distil(helper, byPipe, 4);
        var secondChest = helper.getBlockEntity(byPipe.below(), ChestBlockEntity.class);
        check(helper, count(secondChest, Items.GLASS_BOTTLE) == 4 && count(secondChest, ModOil.METHANOL.get()) == 0,
                "piped off, the bottles below are empty: " + count(secondChest, ModOil.METHANOL.get()) + " filled");
        check(helper, second.guiTanks().getFirst().kind() == GuiTank.Kind.METHANOL
                && second.guiTanks().size() == MachineLayout.DISTILLER.tanks().size(), "its methanol tank first on its screen");

        BlockPos deadEnd = new BlockPos(1, 2, 6);
        helper.setBlock(deadEnd.below(), Blocks.CHEST);
        tower(helper, deadEnd);
        pipe(helper, deadEnd.above(3).east()); // a pipe that goes nowhere
        distil(helper, deadEnd, 4);
        var thirdChest = helper.getBlockEntity(deadEnd.below(), ChestBlockEntity.class);
        var third = helper.getBlockEntity(deadEnd, DistillerBlockEntity.class);
        check(helper, count(thirdChest, ModOil.METHANOL.get()) == 0 && count(thirdChest, Items.GLASS_BOTTLE) == 4
                && third.methanolStored() == run,
                "a pipe leading nowhere takes none: it stays in its tank: " + third.methanolStored());

        BlockPos alone = new BlockPos(5, 2, 6);
        helper.setBlock(alone.below(), Blocks.CHEST);
        var fourth = distil(helper, alone, 4);
        var fourthChest = helper.getBlockEntity(alone.below(), ChestBlockEntity.class);
        check(helper, count(fourthChest, ModOil.METHANOL.get()) == 0 && fourth.methanolStored() == run,
                "nothing beside it: in its tank, none bottled: " + fourth.methanolStored());

        // a line from the Condenser down to the Distiller, a tank at its foot: no methanol into it
        BlockPos looped = new BlockPos(1, 2, 10);
        helper.setBlock(looped.below(), Blocks.CHEST);
        tower(helper, looped);
        for (int y = 0; y <= 3; y++) {
            pipe(helper, looped.east().above(y));
        }
        helper.setBlock(looped.east().below(), ModBlocks.COPPER_TANK.get());
        var footTank = helper.getBlockEntity(looped.east().below(), CopperTankBlockEntity.class);
        var fifth = distil(helper, looped, 4);
        // the pipe carries what it took on into the tank at its end, as the level ticks it
        helper.succeedWhen(() -> {
            check(helper, farTank.amount() == run && farTank.resource().getFluid() == OilFluids.METHANOL.get(),
                    "down the pipe from the Condenser into the tank at its end: " + farTank.amount());
            check(helper, footTank.resource().getFluid() != OilFluids.METHANOL.get() && fifth.methanolStored() == run,
                    "no methanol down a line that runs to the Distiller too: " + footTank.resource() + ", " + fifth.methanolStored());
        });
    }

    /**
     * A powered Stirring Tank with its charge slot empty, caustic soda in, oil and methanol piped in, makes biodiesel of the
     * piped methanol, a bottle's worth a batch, and lets no bottle out (none came in); with less methanol than a batch it
     * says so. It wants no mercury while it does.
     */
    private static void stirringTank(GameTestHelper helper) {
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), Blocks.CHEST);
        helper.setBlock(at, ModBlocks.STIRRING_TANK.get());
        var tank = helper.getBlockEntity(at, StirringTankBlockEntity.class);
        tank.energy().set((int) tank.energy().getCapacityAsLong());
        tank.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        check(helper, Move.insert(tank.fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), 1_000) == 1_000, "the oil piped in");
        check(helper, Move.insert(tank.fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 500) == 500, "the methanol piped in");
        check(helper, Move.extract(tank.fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 100) == 0,
                "and nothing draws it back out by pipe");
        check(helper, tank.fillMercury(100, true) == 0, "making biodiesel, it wants no mercury");

        BlockPos low = new BlockPos(4, 2, 1);
        helper.setBlock(low, ModBlocks.STIRRING_TANK.get());
        var short_ = helper.getBlockEntity(low, StirringTankBlockEntity.class);
        short_.energy().set((int) short_.energy().getCapacityAsLong());
        short_.setItem(StirringTankBlockEntity.CATALYST, new ItemStack(ModItems.CAUSTIC_SODA.get()));
        Move.insert(short_.fluidHandler(), FluidResource.of(OilFluids.SOURCE.get()), 1_000);
        Move.insert(short_.fluidHandler(), FluidResource.of(OilFluids.METHANOL.get()), 100);

        helper.succeedWhen(() -> {
            check(helper, tank.biodieselStored() == 500 && tank.methanolStored() == 0 && tank.oilStored() == 500,
                    "two batches of the piped methanol: " + tank.biodieselStored() + " biodiesel, " + tank.methanolStored()
                            + " methanol, " + tank.oilStored() + " oil");
            var chest = helper.getBlockEntity(at.below(), ChestBlockEntity.class);
            check(helper, chest.countItem(Items.GLASS_BOTTLE) == 0 && tank.getItem(2).isEmpty(), "no bottle comes out of piped methanol");
            check(helper, short_.guiStatus() == MachineStatus.LOW_METHANOL && short_.biodieselStored() == 0,
                    "with less than a batch of methanol it says so: " + short_.guiStatus());
        });
    }

    /**
     * The fluid, source and flowing, is in #c:methanol; its bucket holds a bucket of it; poured out it lies as a pool of
     * methanol, which an empty bucket takes back as its bucket. A Distiller holding methanol, broken, keeps it on its
     * item, which the Recycler then turns down.
     */
    private static void fluidAndBucket(GameTestHelper helper) {
        TagKey<Fluid> tag = TagKey.create(Registries.FLUID, Identifier.fromNamespaceAndPath("c", "methanol"));
        check(helper, OilFluids.METHANOL.get().defaultFluidState().is(tag) && OilFluids.FLOWING_METHANOL.get().defaultFluidState().is(tag),
                "source and flowing methanol in #c:methanol");
        check(helper, "nookworks:methanol".equals(net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(OilFluids.METHANOL.get()).toString()),
                "the fluid's id is nookworks:methanol");
        var held = ItemAccess.forStack(new ItemStack(ModOil.METHANOL_BUCKET.get())).getCapability(Capabilities.Fluid.ITEM);
        check(helper, held != null && held.getResource(0).getFluid() == OilFluids.METHANOL.get() && held.getAmountAsLong(0) == 1_000,
                "its bucket holds a bucket of methanol");
        check(helper, OilFluids.METHANOL.get().getBucket() == ModOil.METHANOL_BUCKET.get(), "and is the fluid's bucket");

        BlockPos pool = new BlockPos(2, 2, 2);
        helper.setBlock(pool, Blocks.AIR);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        check(helper, ModOil.METHANOL_BUCKET.get().emptyContents(player, helper.getLevel(), helper.absolutePos(pool), null),
                "the bucket pours out");
        var state = helper.getBlockState(pool);
        check(helper, state.is(ModOil.METHANOL_BLOCK.get()) && state.getFluidState().isSource()
                && state.getFluidState().getType() == OilFluids.METHANOL.get(), "a pool of methanol: " + state);
        ItemStack back = ((LiquidBlock) state.getBlock()).pickupBlock(player, helper.getLevel(), helper.absolutePos(pool), state);
        check(helper, back.is(ModOil.METHANOL_BUCKET.get()) && helper.getBlockState(pool).isAir(), "taken back up as its bucket: " + back);
        helper.succeed();
    }

    /**
     * A Distiller's data as 1.4 saved it - no "methanol" - loads with its mercury and progress and an empty methanol tank;
     * one holding methanol saves it as "methanol" and loads it back; broken, the methanol goes with its item.
     */
    private static void distillerSave(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        var distiller = tower(helper, pos);
        distiller.mercuryStore().set(300);
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        distiller.saveCustomOnly(saved);
        var tag = saved.buildResult();
        tag.remove("methanol"); // as 1.4 wrote it
        var old = new DistillerBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        old.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        check(helper, old.methanolStored() == 0 && old.mercuryStore().amount() == 300, "an old Distiller loads, its methanol tank empty: "
                + old.methanolStored() + ", mercury " + old.mercuryStore().amount());

        Tanks.set(distiller.methanolTank(), OilFluids.METHANOL.get(), 750);
        var again = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        distiller.saveCustomOnly(again);
        check(helper, again.buildResult().getCompound("methanol").isPresent(), "its methanol saved as \"methanol\"");
        var reloaded = new DistillerBlockEntity(helper.absolutePos(pos), helper.getBlockState(pos));
        reloaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), again.buildResult()));
        check(helper, reloaded.methanolStored() == 750, "and read back: " + reloaded.methanolStored());

        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(pos);
        ItemStack item = Block.getDrops(level.getBlockState(at), level, at, level.getBlockEntity(at)).stream()
                .filter(s -> s.is(ModItems.DISTILLER.get())).findFirst().orElse(ItemStack.EMPTY);
        var kept = item.get(dev.nookworks.registry.ModDataComponents.HELD_FLUIDS.get());
        check(helper, kept != null && kept.stream().anyMatch(f -> f.copy().getFluid() == OilFluids.METHANOL.get() && f.getAmount() == 750),
                "broken, its item keeps the methanol: " + item.getComponentsPatch());
        check(helper, RecyclingRecipe.holdsThings(item), "and the Recycler turns it down while it holds it");
        helper.succeed();
    }
}
