package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.OtherModPipesTests.LINE;
import static dev.nookworks.gametest.OtherModPipesTests.SOURCE;
import static dev.nookworks.gametest.OtherModPipesTests.TARGET;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.gametest.OtherModPipesTests.Kind;
import dev.nookworks.machine.CrusherBlockEntity;
import dev.nookworks.machine.GeneratorBlockEntity;
import dev.nookworks.mercury.MercuryTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.FluidResource;
import dev.nookworks.port.transfer.Transaction;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * Liquid experience between mods, mercury staying ours, our buckets and bottles in other mods' hands, and power across
 * two cable networks of two mods - with the real mods, each part registered only with its mod.
 */
final class OtherModLiquidsTests {
    private OtherModLiquidsTests() {}

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    /** The other mods' experience liquids, by id: each counts 20 mB a point in its own code, as ours does. */
    static List<String> experienceLiquids() {
        List<String> out = new ArrayList<>();
        if (Foreign.loaded("enderio")) {
            out.add("enderio:fluid_xp_juice_still");
        }
        if (Foreign.loaded("cyclic")) {
            out.add("cyclic:xpjuice");
        }
        if (Foreign.loaded("sophisticatedcore")) {
            out.add("sophisticatedcore:xp_still");
        }
        return out;
    }

    static Fluid fluid(String id) {
        Fluid fluid = BuiltInRegistries.FLUID.get(ResourceLocation.parse(id));
        if (fluid == Fluids.EMPTY) {
            throw new IllegalStateException("no fluid " + id);
        }
        return fluid;
    }

    static void register() {
        for (String id : experienceLiquids()) {
            String mod = id.substring(0, id.indexOf(':'));
            NookworksGameTests.test("xmod_xp_" + mod + "_poured_onto_ours_joins_it", 20, h -> joinsOurs(h, id));
            NookworksGameTests.test("xmod_xp_" + mod + "_given_out_of_ours_when_asked", 20, h -> givenAsTheirs(h, id));
            NookworksGameTests.test("xmod_xp_" + mod + "_into_a_repair_box", 20, h -> intoRepairBox(h, id));
            NookworksGameTests.test("xmod_xp_" + mod + "_in_a_tank_of_ours_takes_ours_on_top", 20, h -> oursOnTop(h, id));
        }
        if (Foreign.loaded("enderio")) {
            NookworksGameTests.test("xmod_xp_ours_into_an_enderio_obelisk", 20, h -> oursIntoTheirs(h, "enderio:xp_obelisk", "enderio:fluid_xp_juice_still"));
        }
        if (Foreign.loaded("cyclic")) {
            NookworksGameTests.test("xmod_xp_ours_into_a_cyclic_pylon", 20, h -> oursIntoTheirs(h, "cyclic:experience_pylon", "cyclic:xpjuice"));
        }
        List<Kind> pipes = OtherModPipesTests.fluidPipes();
        if (!pipes.isEmpty()) {
            NookworksGameTests.test("xmod_mercury_has_no_fluid_handler_for_other_mods", 20, OtherModLiquidsTests::mercuryHasNoHandler);
            NookworksGameTests.test("xmod_buckets_and_bottles_of_ours_to_other_mods", 20, OtherModLiquidsTests::itemFluids);
        }
        for (Kind kind : pipes) {
            NookworksGameTests.test("xmod_" + kind.mod() + "_fluids_leave_a_mercury_tank_alone", 200, h -> mercuryStays(h, kind));
            NookworksGameTests.test("xmod_" + kind.mod() + "_fluids_leave_a_mercury_cauldron_alone", 200, h -> mercuryCauldronStays(h, kind));
            NookworksGameTests.test("xmod_" + kind.mod() + "_fluids_from_their_experience_into_our_tank", 300, h -> theirsThroughTheirPipe(h, kind));
        }
        for (Kind kind : OtherModPipesTests.cables()) {
            NookworksGameTests.test("xmod_loop_" + kind.mod() + "_bridges_two_networks_of_ours", 300, h -> bridged(h, kind));
            NookworksGameTests.test("xmod_loop_" + kind.mod() + "_joins_ours_at_two_points", 400, h -> loop(h, kind));
            NookworksGameTests.test("xmod_" + kind.mod() + "_energy_ours_broken_and_set_again", 400, h -> brokenAndSetAgain(h, kind));
        }
    }

    // ---- experience ----

    private static ExperienceTankBlockEntity tank(GameTestHelper helper, BlockPos pos, int ours) {
        helper.setBlock(pos, ModBlocks.EXPERIENCE_TANK.get());
        ExperienceTankBlockEntity tank = helper.getBlockEntity(pos, ExperienceTankBlockEntity.class);
        if (ours > 0) {
            tank.fill(ours);
        }
        return tank;
    }

    /** Their liquid poured into our tank holding ours (as their pipe pours): it all goes in and is ours, to the mB. */
    private static void joinsOurs(GameTestHelper helper, String id) {
        BlockPos pos = new BlockPos(3, 1, 3);
        ExperienceTankBlockEntity tank = tank(helper, pos, 1_000);
        IFluidHandler handler = Foreign.fluids(helper, pos, Direction.UP);
        int in = handler.fill(new FluidStack(fluid(id), 2_000), IFluidHandler.FluidAction.EXECUTE);
        check(helper, in == 2_000 && tank.amount() == 3_000 && tank.resource().is(ExperienceFluids.SOURCE.get()),
                id + ": " + in + " of 2000 mB went in; the tank holds " + tank.amount() + " of " + tank.resource().getFluid());
        helper.succeed();
    }

    /** A pipe of theirs asking our tank for their own liquid is given it out of ours, to the mB. */
    private static void givenAsTheirs(GameTestHelper helper, String id) {
        BlockPos pos = new BlockPos(3, 1, 3);
        ExperienceTankBlockEntity tank = tank(helper, pos, 2_000);
        IFluidHandler handler = Foreign.fluids(helper, pos, Direction.UP);
        FluidStack out = handler.drain(new FluidStack(fluid(id), 500), IFluidHandler.FluidAction.EXECUTE);
        check(helper, out.is(fluid(id)) && out.getAmount() == 500 && tank.amount() == 1_500,
                id + ": asked for 500 mB of theirs, given " + out.getAmount() + " of " + out.getFluid() + "; " + tank.amount() + " left");
        helper.succeed();
    }

    private static void intoRepairBox(GameTestHelper helper, String id) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, ModBlocks.REPAIR_BOX.get());
        int in = Foreign.fill(helper, pos, new FluidStack(fluid(id), 1_000));
        check(helper, in == 1_000 && Foreign.fluid(helper, pos, null) == 1_000, id + ": the Repair Box took " + in + " mB");
        helper.succeed();
    }

    /** A tank of ours holding their liquid takes ours on top as the same thing (it refused it). */
    private static void oursOnTop(GameTestHelper helper, String id) {
        BlockPos pos = new BlockPos(3, 1, 3);
        ExperienceTankBlockEntity tank = tank(helper, pos, 0);
        Foreign.fluids(helper, pos, Direction.UP).fill(new FluidStack(fluid(id), 1_000), IFluidHandler.FluidAction.EXECUTE);
        int ours = tank.fill(1_000);
        check(helper, ours == 1_000 && tank.amount() == 2_000 && tank.resource().is(fluid(id)),
                id + ": ours went in on top of theirs: " + ours + " mB, the tank holds " + tank.amount() + " of " + tank.resource().getFluid());
        helper.succeed();
    }

    /**
     * Ours handed to their block that takes only its own (as our pipes, tanks and machines hand it on, through our
     * transfer API): it arrives as their liquid, to the mB.
     */
    private static void oursIntoTheirs(GameTestHelper helper, String block, String theirs) {
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Foreign.block(block));
        IFluidHandler real = Foreign.fluids(helper, pos, Direction.UP);
        check(helper, real != null, block + " has a fluid handler");
        int direct = real.fill(new FluidStack(ExperienceFluids.SOURCE.get(), 100), IFluidHandler.FluidAction.SIMULATE);
        var ours = helper.getLevel().getCapability(Capabilities.Fluid.BLOCK, helper.absolutePos(pos), Direction.UP);
        int moved;
        try (Transaction tx = Transaction.openRoot()) {
            moved = ours.insert(FluidResource.of(ExperienceFluids.SOURCE.get()), 400, tx);
            tx.commit();
        }
        FluidStack held = Foreign.fluidIn(helper, pos, Direction.UP);
        // (both take ours as it is: then it stays ours; a block of theirs that took only its own would be given its own)
        check(helper, moved == 400 && held.getAmount() == 400 && (direct > 0 ? held.is(ExperienceFluids.SOURCE.get()) : held.is(fluid(theirs))),
                block + " (which takes " + direct + " of 100 mB of ours itself): " + moved + " mB handed over, it holds " + held.getAmount()
                        + " of " + held.getFluid());
        helper.succeed();
    }

    /** Their experience in their tank, their pipe drawing it into our Experience Tank: every mB arrives. */
    private static void theirsThroughTheirPipe(GameTestHelper helper, Kind kind) {
        String id = experienceLiquids().isEmpty() ? null : experienceLiquids().get(0);
        if (id == null) {
            helper.succeed();
            return;
        }
        helper.setBlock(SOURCE, OtherModPipesTests.foreignTank());
        ExperienceTankBlockEntity tank = tank(helper, TARGET, 500);
        check(helper, Foreign.fill(helper, SOURCE, new FluidStack(fluid(id), 2_000)) == 2_000, "their tank holds 2000 mB of " + id);
        OtherModPipesTests.lay(helper, kind);
        helper.succeedWhen(() -> check(helper, tank.amount() == 2_500 && Foreign.fluid(helper, SOURCE, null) == 0,
                kind.mod() + ": " + (tank.amount() - 500) + " of 2000 mB of " + id + " arrived"));
    }

    // ---- mercury ----

    private static void mercuryHasNoHandler(GameTestHelper helper) {
        List<Block> blocks = List.of(ModBlocks.MERCURY_TANK.get(), ModBlocks.MERCURY_PIPE.get(), ModBlocks.MERCURY_CAULDRON.get(),
                ModBlocks.RETORT.get(), ModBlocks.DISTILLER.get());
        BlockPos pos = new BlockPos(3, 1, 3);
        for (Block block : blocks) {
            helper.setBlock(pos, block);
            for (Direction side : new Direction[] {null, Direction.UP, Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
                IFluidHandler handler = Foreign.fluids(helper, pos, side);
                if (handler == null) {
                    continue;
                }
                for (int i = 0; i < handler.getTanks(); i++) {
                    check(helper, !handler.getFluidInTank(i).getFluid().builtInRegistryHolder().is(net.minecraft.tags.TagKey.create(
                            net.minecraft.core.registries.Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", "mercury"))),
                            block + " shows mercury to other mods from " + side);
                }
            }
        }
        helper.succeed();
    }

    /** A Mercury Tank, their pipe set to pull from it: nothing is drawn, nothing put in. */
    private static void mercuryStays(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.MERCURY_TANK.get());
        helper.setBlock(TARGET, OtherModPipesTests.foreignTank());
        MercuryTankBlockEntity tank = helper.getBlockEntity(SOURCE, MercuryTankBlockEntity.class);
        tank.fillMercury(4_000, false);
        OtherModPipesTests.lay(helper, kind);
        helper.runAfterDelay(120, () -> {
            check(helper, tank.mercury() == 4_000 && Foreign.fluid(helper, TARGET, null) == 0 && OtherModPipesTests.pipeFluid(helper) == 0,
                    kind.mod() + ": the mercury stays: " + tank.mercury() + " mB in the tank, " + Foreign.fluid(helper, TARGET, null) + " drawn");
            helper.succeed();
        });
    }

    private static void mercuryCauldronStays(GameTestHelper helper, Kind kind) {
        helper.setBlock(SOURCE, ModBlocks.MERCURY_CAULDRON.get().defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(TARGET, OtherModPipesTests.foreignTank());
        OtherModPipesTests.lay(helper, kind);
        helper.runAfterDelay(120, () -> {
            check(helper, helper.getBlockState(SOURCE).is(ModBlocks.MERCURY_CAULDRON.get())
                            && helper.getBlockState(SOURCE).getValue(LayeredCauldronBlock.LEVEL) == 3 && Foreign.fluid(helper, TARGET, null) == 0,
                    kind.mod() + ": the mercury cauldron is left full: " + helper.getBlockState(SOURCE));
            helper.succeed();
        });
    }

    // ---- items holding liquids ----

    /**
     * What another mod's tank or machine sees of our liquid items: our buckets are NeoForge's own bucket wrapper (they
     * fill and empty other mods' tanks as any bucket), the mercury bucket keeps its mercury, and the bottles have no
     * fluid handler (they pour into our tanks and machines only).
     */
    private static void itemFluids(GameTestHelper helper) {
        var cap = net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM;
        for (var bucket : List.of(ModItems.EXPERIENCE_BUCKET, dev.nookworks.registry.ModOil.METHANOL_BUCKET, dev.nookworks.registry.ModOil.BIODIESEL_BUCKET)) {
            ItemStack stack = new ItemStack(bucket.get());
            var handler = stack.getCapability(cap);
            check(helper, handler != null, bucket.getId() + " has a fluid handler");
            FluidStack out = handler.drain(1_000, IFluidHandler.FluidAction.EXECUTE);
            check(helper, out.getAmount() == 1_000 && handler.getContainer().is(net.minecraft.world.item.Items.BUCKET),
                    bucket.getId() + " empties as a bucket: " + out.getAmount() + " mB, leaving " + handler.getContainer());
        }
        ItemStack mercury = new ItemStack(ModItems.MERCURY_BUCKET.get());
        var handler = mercury.getCapability(cap);
        check(helper, handler == null || handler.drain(1_000, IFluidHandler.FluidAction.SIMULATE).isEmpty(),
                "the mercury bucket gives no liquid to another mod");
        ItemStack empty = new ItemStack(net.minecraft.world.item.Items.BUCKET);
        var emptyHandler = empty.getCapability(cap);
        int filled = emptyHandler.fill(new FluidStack(OilFluids.METHANOL.get(), 1_000), IFluidHandler.FluidAction.EXECUTE);
        check(helper, filled == 1_000 && emptyHandler.getContainer().is(dev.nookworks.registry.ModOil.METHANOL_BUCKET.get()),
                "an empty bucket filled with methanol by another mod is our methanol bucket: " + emptyHandler.getContainer());
        helper.succeed();
    }

    // ---- power across two mods' networks ----

    /**
     * Generator (8,000 FE, no fuel) - our cable - their cable - our cable - Crusher: two networks of ours bridged by
     * theirs. All of it reaches the Crusher, none made, and it settles (nothing keeps moving once the Crusher is fed).
     */
    private static void bridged(GameTestHelper helper, Kind kind) {
        helper.setBlock(new BlockPos(0, 1, 3), ModBlocks.GENERATOR.get());
        cable(helper, SOURCE);
        helper.setBlock(new BlockPos(6, 1, 3), ModBlocks.CRUSHER.get());
        cable(helper, TARGET);
        GeneratorBlockEntity generator = helper.getBlockEntity(new BlockPos(0, 1, 3), GeneratorBlockEntity.class);
        generator.energy().set(8_000);
        CrusherBlockEntity crusher = helper.getBlockEntity(new BlockPos(6, 1, 3), CrusherBlockEntity.class);
        OtherModPipesTests.lay(helper, kind, LINE, Direction.WEST, Direction.EAST);
        settles(helper, kind, generator, crusher, 8_000);
    }

    /**
     * A ring: our cable along z=2 and theirs along z=4, joined by theirs at both ends - our network meets theirs at two
     * points. A Generator's 8,000 FE goes round to a Crusher: no FE is made, it settles, and the server's ticks are not
     * spent passing it to and fro (the tick time measured before the ring and with it).
     */
    private static void loop(GameTestHelper helper, Kind kind) {
        List<BlockPos> ours = new ArrayList<>();
        for (int x = 1; x <= 5; x++) {
            ours.add(new BlockPos(x, 1, 2));
        }
        List<BlockPos> theirs = List.of(new BlockPos(1, 1, 3), new BlockPos(1, 1, 4), new BlockPos(2, 1, 4), new BlockPos(3, 1, 4),
                new BlockPos(4, 1, 4), new BlockPos(5, 1, 4), new BlockPos(5, 1, 3));
        helper.setBlock(new BlockPos(3, 1, 1), ModBlocks.GENERATOR.get());
        helper.setBlock(new BlockPos(3, 1, 5), ModBlocks.CRUSHER.get());
        GeneratorBlockEntity generator = helper.getBlockEntity(new BlockPos(3, 1, 1), GeneratorBlockEntity.class);
        CrusherBlockEntity crusher = helper.getBlockEntity(new BlockPos(3, 1, 5), CrusherBlockEntity.class);
        long[] before = new long[1];
        helper.startSequence().thenExecuteAfter(40, () -> before[0] = helper.getLevel().getServer().getAverageTickTimeNanos())
                .thenExecute(() -> {
                    for (BlockPos pos : ours) {
                        cable(helper, pos);
                    }
                    for (BlockPos pos : theirs) {
                        kind.place().place(helper, pos);
                    }
                    for (BlockPos pos : theirs) {
                        Foreign.reshape(helper, pos);
                    }
                    if (kind.mod().equals("pipez")) {
                        // a Pipez pipe takes power in only on a side set to extract: one junction so, the other as it is
                        kind.pull().pull(helper, theirs.get(0), Direction.NORTH);
                    }
                    generator.energy().set(8_000);
                }).thenExecuteAfter(200, () -> {
                    long after = helper.getLevel().getServer().getAverageTickTimeNanos();
                    int sum = generator.energyStored() + crusher.energyStored();
                    check(helper, sum <= 8_000, kind.mod() + ": no FE made in the ring: " + sum);
                    check(helper, crusher.energyStored() >= 8_000 - 8, kind.mod() + ": the generator's 8000 FE reached the crusher round the ring: "
                            + crusher.energyStored() + " (generator " + generator.energyStored() + ")");
                    LOGGER.info("xmod ring of {}: average tick {} ms before, {} ms with it", kind.mod(), before[0] / 1e6, after / 1e6);
                    check(helper, after < before[0] + 20_000_000L, kind.mod() + ": the ring does not eat the server's ticks: "
                            + before[0] / 1e6 + " ms a tick before, " + after / 1e6 + " with it");
                }).thenSucceed();
    }

    /** One of our Energy Cables set down as a player sets it, joined to what stands beside it. */
    static void cable(GameTestHelper helper, BlockPos pos) {
        MachineTests.placeByHand(helper, new ItemStack(ModItems.ENERGY_CABLE.get()), pos);
    }

    /** Waits until the Crusher has stopped filling, then checks the account. */
    private static void settles(GameTestHelper helper, Kind kind, GeneratorBlockEntity generator, CrusherBlockEntity crusher, int given) {
        int[] last = {-1};
        int[] still = {0};
        helper.succeedWhen(() -> {
            int now = crusher.energyStored();
            still[0] = now == last[0] ? still[0] + 1 : 0;
            last[0] = now;
            int sum = generator.energyStored() + now;
            check(helper, sum <= given, kind.mod() + ": no FE made: " + sum + " of " + given);
            var bridge = Foreign.energy(helper, LINE.get(1), null);
            var ourLast = Foreign.energy(helper, TARGET, Direction.WEST);
            check(helper, generator.energyStored() == 0 && still[0] >= 40 && now >= given - 8,
                    kind.mod() + ": " + now + " of " + given + " FE reached the crusher (generator " + generator.energyStored() + "; their cable holds "
                            + (bridge == null ? "-" : bridge.getEnergyStored()) + "; our last cable takes "
                            + (ourLast == null ? "-" : ourLast.receiveEnergy(100, true) + " of 100, canReceive " + ourLast.canReceive())
                            + "; their first piece takes " + (Foreign.energy(helper, LINE.get(0), Direction.WEST) == null ? "-"
                                    : Foreign.energy(helper, LINE.get(0), Direction.WEST).receiveEnergy(100, true))
                            + "; our first cable's receivers " + dev.nookworks.machine.MachineIO.receivers(helper.getLevel(), helper.absolutePos(SOURCE))
                            + "; the generator's " + dev.nookworks.machine.MachineIO.receivers(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 3)))
                            + "; our first cable " + helper.getBlockState(SOURCE) + ")");
        });
    }

    /** The bridged line running; our last cable broken and set again: power comes through again (caches told). */
    private static void brokenAndSetAgain(GameTestHelper helper, Kind kind) {
        helper.setBlock(new BlockPos(0, 1, 3), ModBlocks.CREATIVE_GENERATOR.get());
        cable(helper, SOURCE);
        helper.setBlock(new BlockPos(6, 1, 3), ModBlocks.CRUSHER.get());
        cable(helper, TARGET);
        CrusherBlockEntity crusher = helper.getBlockEntity(new BlockPos(6, 1, 3), CrusherBlockEntity.class);
        OtherModPipesTests.lay(helper, kind, LINE, Direction.WEST, Direction.EAST);
        int[] at = new int[1];
        helper.startSequence().thenWaitUntil(() -> check(helper, crusher.energyStored() > 0, kind.mod() + ": power comes through"))
                .thenExecute(() -> {
                    helper.getLevel().destroyBlock(helper.absolutePos(TARGET), false);
                    crusher.energy().set(0);
                }).thenExecuteAfter(20, () -> check(helper, crusher.energyStored() == 0, "with our cable gone, none"))
                .thenExecute(() -> cable(helper, TARGET))
                .thenWaitUntil(() -> check(helper, crusher.energyStored() > 0, kind.mod() + ": set again, power comes through again"))
                .thenSucceed();
    }
}
