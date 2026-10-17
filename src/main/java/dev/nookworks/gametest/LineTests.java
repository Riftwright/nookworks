package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperFittingBlock;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.LineContent;
import dev.nookworks.fluid.SightGlassBlockEntity;
import dev.nookworks.fluid.ValveBlock;
import dev.nookworks.machine.SightGlass;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/** The copper line's fittings and tank: the Sight Glass, the Valve, the Copper Tank. */
final class LineTests {
    static void register() {
        NookworksGameTests.test("sight_glass_passes_fluid_on_as_a_pipe", 60, LineTests::sightGlassPassesFluidOn);
        NookworksGameTests.test("sight_glass_shows_what_stands_in_it", 60, LineTests::sightGlassShowsWhatStandsInIt);
        NookworksGameTests.test("valve_shut_stops_the_line_and_open_lets_it_run", 200, LineTests::valveShutStopsTheLine);
        NookworksGameTests.test("valve_turns_with_a_signal", LineTests::valveTurnsWithASignal);
        NookworksGameTests.test("copper_tank_takes_buckets_and_bottles", LineTests::copperTankTakesBucketsAndBottles);
        NookworksGameTests.test("copper_tank_keeps_its_contents_when_broken", LineTests::copperTankKeepsItsContents);
    }

    private static BlockState fitting(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(CopperFittingBlock.AXIS, axis);
    }

    /** Sets the blocks, then works out each one's joins from its neighbours, as placing them one by one would. */
    private static void lay(GameTestHelper helper, List<BlockPos> positions, List<BlockState> states) {
        for (int i = 0; i < positions.size(); i++) {
            helper.setBlock(positions.get(i), states.get(i));
        }
        for (BlockPos pos : positions) {
            BlockPos at = helper.absolutePos(pos);
            helper.getLevel().setBlock(at, Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(at), helper.getLevel(), at), Block.UPDATE_ALL);
        }
    }

    /** A pipe, a sight glass and a tank in a row: the glass is a length of the line, and the water runs through it. */
    private static void sightGlassPassesFluidOn(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(1, 2, 1);
        BlockPos glass = pipe.east();
        BlockPos tank = glass.east();
        lay(helper, List.of(pipe, glass, tank), List.of(ModBlocks.COPPER_PIPE.get().defaultBlockState(),
                fitting(ModBlocks.SIGHT_GLASS.get(), Direction.Axis.X), ModBlocks.COPPER_TANK.get().defaultBlockState()));
        check(helper, helper.getBlockState(glass).getValue(PipeBlock.WEST)
                && helper.getBlockState(glass).getValue(PipeBlock.EAST), "the glass joins the pipe and the tank");
        check(helper, !helper.getBlockState(glass).getValue(PipeBlock.UP), "and nothing across its axis");
        var upstream = helper.getBlockEntity(pipe, CopperPipeBlockEntity.class);
        int poured = Move.insert(upstream.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000);
        check(helper, poured == 1_000, "a bucket goes into the pipe");
        var sink = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        helper.succeedWhen(() -> check(helper, sink.amount() == 1_000, "all of it through the glass into the tank: " + sink.amount()));
    }

    /** With nowhere to go, what stands in the glass shows: water, full. Oil after it, and empty once flushed. */
    private static void sightGlassShowsWhatStandsInIt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        lay(helper, List.of(pos), List.of(fitting(ModBlocks.SIGHT_GLASS.get(), Direction.Axis.Z)));
        var glass = helper.getBlockEntity(pos, SightGlassBlockEntity.class);
        Move.insert(glass.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000);
        helper.runAfterDelay(12, () -> {
            BlockState shown = helper.getBlockState(pos);
            check(helper, shown.getValue(LineContent.PROPERTY) == LineContent.WATER, "shows water: " + shown.getValue(LineContent.PROPERTY));
            check(helper, shown.getValue(SightGlass.LEVEL) == SightGlass.STEPS, "and full: " + shown.getValue(SightGlass.LEVEL));
            glass.flush(helper.getLevel());
        });
        helper.runAfterDelay(40, () -> {
            check(helper, helper.getBlockState(pos).getValue(LineContent.PROPERTY) == LineContent.EMPTY, "flushed, empty");
            check(helper, helper.getBlockState(pos).getValue(SightGlass.LEVEL) == 0, "and nothing in the glass");
            helper.succeed();
        });
    }

    /** Shut, a valve holds the line back both ways; opened, it runs on through. */
    private static void valveShutStopsTheLine(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(1, 2, 1);
        BlockPos valve = pipe.east();
        BlockPos tank = valve.east();
        lay(helper, List.of(pipe, valve, tank), List.of(ModBlocks.COPPER_PIPE.get().defaultBlockState(),
                fitting(ModBlocks.VALVE.get(), Direction.Axis.X).setValue(ValveBlock.OPEN, false),
                ModBlocks.COPPER_TANK.get().defaultBlockState()));
        var upstream = helper.getBlockEntity(pipe, CopperPipeBlockEntity.class);
        Move.insert(upstream.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000);
        var sink = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            check(helper, sink.amount() == 0, "shut, none gets past: " + sink.amount());
            check(helper, upstream.stored() == 1_000, "and the line before it stays full: " + upstream.stored());
            BlockPos at = helper.absolutePos(valve);
            helper.getLevel().setBlock(at, helper.getLevel().getBlockState(at).setValue(ValveBlock.OPEN, true), Block.UPDATE_ALL);
            helper.getLevel().invalidateCapabilities(at);
        });
        helper.runAfterDelay(120, () -> {
            check(helper, sink.amount() == 1_000, "opened, it all runs on: " + sink.amount());
            helper.succeed();
        });
    }

    /** As a trapdoor: a signal coming on opens the valve, going off shuts it. */
    private static void valveTurnsWithASignal(GameTestHelper helper) {
        BlockPos valve = new BlockPos(2, 2, 2);
        lay(helper, List.of(valve), List.of(fitting(ModBlocks.VALVE.get(), Direction.Axis.X).setValue(ValveBlock.OPEN, false)));
        helper.setBlock(valve.above(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(valve).getValue(ValveBlock.OPEN), "the signal opens it");
        helper.setBlock(valve.above(), Blocks.AIR);
        check(helper, !helper.getBlockState(valve).getValue(ValveBlock.OPEN), "and it shuts when the signal goes");
        helper.succeed();
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        return new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.NORTH, helper.absolutePos(pos), false);
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit(helper, pos));
    }

    /**
     * A bucket of water pours in and an empty bucket draws a bucket out; bottles move a quarter bucket each; a bottle
     * of oil will not go in on water. The glass shows what is in and how much.
     */
    private static void copperTankTakesBucketsAndBottles(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pos, CopperTankBlockEntity.class);
        // a mock player without a connection: the refusal below says so on the action bar, which a server player
        // with no connection could not be sent
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        use(helper, player, pos, new ItemStack(Items.WATER_BUCKET));
        check(helper, tank.amount() == 1_000, "a bucket of water in: " + tank.amount());
        check(helper, player.getMainHandItem().is(Items.BUCKET), "leaving the bucket empty");
        use(helper, player, pos, PotionContents.createItemStack(Items.POTION, Potions.WATER));
        check(helper, tank.amount() == 1_250, "a bottle of water in: " + tank.amount());
        use(helper, player, pos, new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get()));
        check(helper, tank.amount() == 1_250 && player.getMainHandItem().is(ModOil.VEGETABLE_OIL_BOTTLE.get()), "oil will not go in on water");
        use(helper, player, pos, new ItemStack(Items.GLASS_BOTTLE));
        check(helper, tank.amount() == 1_000, "an empty bottle draws a quarter out");
        use(helper, player, pos, new ItemStack(Items.BUCKET));
        check(helper, tank.amount() == 0 && player.getMainHandItem().is(Items.WATER_BUCKET), "an empty bucket draws the rest");
        tank.fluidHandler().set(0, FluidResource.of(OilFluids.BIODIESEL.get()), CopperTankBlockEntity.CAPACITY / 2);
        check(helper, helper.getBlockState(pos).getValue(LineContent.PROPERTY) == LineContent.BIODIESEL, "the glass shows biodiesel");
        check(helper, helper.getBlockState(pos).getValue(SightGlass.LEVEL) == 3, "half full");
        helper.succeed();
    }

    /** Broken, a tank keeps what is in it on the item, and set down again it holds the same. */
    private static void copperTankKeepsItsContents(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var tank = helper.getBlockEntity(pos, CopperTankBlockEntity.class);
        tank.fluidHandler().set(0, FluidResource.of(OilFluids.SOURCE.get()), 5_500);
        BlockPos at = helper.absolutePos(pos);
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), helper.getLevel(), at, tank, null, new ItemStack(Items.IRON_PICKAXE));
        ItemStack dropped = drops.stream().filter(s -> s.is(ModItems.COPPER_TANK.get())).findFirst().orElse(ItemStack.EMPTY);
        SimpleFluidContent held = dropped.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY);
        check(helper, held.getAmount() == 5_500 && held.is(OilFluids.SOURCE.get()), "the item holds the oil: " + held.getAmount());
        helper.setBlock(pos, Blocks.AIR);
        helper.setBlock(pos, ModBlocks.COPPER_TANK.get());
        var again = helper.getBlockEntity(pos, CopperTankBlockEntity.class);
        again.applyComponentsFromItemStack(dropped);
        check(helper, again.amount() == 5_500, "set down again, it holds the same: " + again.amount());
        helper.succeed();
    }
}
