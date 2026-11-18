package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;

import dev.nookworks.cooking.IronPotBlockEntity;
import dev.nookworks.cooking.KitchenTapBlock;
import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.FluidResource;

/**
 * The Kitchen Tap, as a tap (the user, 2026-11-15: 「「蛇口」というブロックを追加するのであれば、きちんとした見た目と、蛇口と
 * して必要な挙動や仕様をちゃんと考えてほしい」): set on what holds a liquid and on nothing else; open, it pours from a pipe,
 * a tank or a cauldron into what stands under it - a pot, or another cauldron - and a bucket held to it is filled;
 * redstone turns it as it does the Valve; a pipe joins it only at its back, and no pipe joins a pot.
 */
final class KitchenTapTests {
    private KitchenTapTests() {}

    static final String[] NEED_KITCHEN = {"a_kitchen_tap_pours_a_pipe_into_the_pot_under_it",
            "a_kitchen_tap_goes_only_on_what_holds_a_liquid", "a_kitchen_tap_pours_a_tank_into_a_pot",
            "a_kitchen_tap_pours_one_cauldron_into_another", "a_kitchen_tap_fills_a_bucket_and_is_turned_by_redstone",
            "a_kitchen_tap_draws_nothing_with_nothing_under_it", "a_kitchen_tap_stands_only_where_liquid_reaches_it",
            "a_kitchen_tap_leaves_a_bucket_it_cannot_fill"};

    static void register() {
        NookworksGameTests.test(NEED_KITCHEN[0], 400, KitchenTapTests::pours);
        NookworksGameTests.test(NEED_KITCHEN[1], KitchenTapTests::onlyOnSources);
        NookworksGameTests.test(NEED_KITCHEN[2], 300, KitchenTapTests::tankIntoPot);
        NookworksGameTests.test(NEED_KITCHEN[3], 300, KitchenTapTests::cauldronIntoCauldron);
        NookworksGameTests.test(NEED_KITCHEN[4], 200, KitchenTapTests::bucketAndRedstone);
        NookworksGameTests.test(NEED_KITCHEN[5], 200, KitchenTapTests::drawsForNobody);
        NookworksGameTests.test(NEED_KITCHEN[6], KitchenTapTests::whereItStands);
        NookworksGameTests.test(NEED_KITCHEN[7], KitchenTapTests::emptyBucketRefused);
    }

    /**
     * Open with nothing under it, it draws nothing: a cauldron's bucket went into its cup out of sight and was lost with
     * the tap (1.7 review). Set a cauldron under it and the bucket goes there.
     */
    private static void drawsForNobody(GameTestHelper helper) {
        BlockPos upper = new BlockPos(2, 3, 3);
        BlockPos tap = upper.north();
        BlockPos lower = tap.below();
        helper.setBlock(upper.below(), Blocks.STONE);
        helper.setBlock(upper, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(tap, tapFacing(Direction.NORTH, true));
        var cup = helper.getBlockEntity(tap, dev.nookworks.cooking.KitchenTapBlockEntity.class);
        helper.runAfterDelay(40, () -> {
            check(helper, helper.getBlockState(upper).is(Blocks.WATER_CAULDRON) && cup.held() == 0,
                    "nothing under it: the cauldron keeps its water, " + cup.held() + " mB in the tap");
            helper.setBlock(lower.below(), Blocks.STONE);
            helper.setBlock(lower, Blocks.CAULDRON);
            helper.succeedWhen(() -> check(helper, helper.getBlockState(lower).is(Blocks.WATER_CAULDRON)
                    && helper.getBlockState(upper).is(Blocks.CAULDRON), "a cauldron set under it: the water goes there"));
        });
    }

    /**
     * It stays on a cauldron whatever fills it - powder snow gives no liquid to be asked about, and a tap on an empty
     * cauldron fell off as it filled (1.7 review) - and goes on no pipe side that lets nothing out to it: an Experience
     * Pipe, a Valve's side off its axis.
     */
    private static void whereItStands(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos cauldron = new BlockPos(2, 2, 3);
        BlockPos tap = cauldron.north();
        helper.setBlock(cauldron, Blocks.CAULDRON);
        helper.setBlock(tap, tapFacing(Direction.NORTH, false));
        helper.setBlock(cauldron, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        check(helper, helper.getBlockState(tap).is(ModCooking.KITCHEN_TAP.get()), "the tap stays on a cauldron of powder snow");
        BlockPos xp = new BlockPos(5, 2, 3);
        helper.setBlock(xp, ModBlocks.EXPERIENCE_PIPE.get());
        check(helper, !tapFacing(Direction.NORTH, false).canSurvive(level, helper.absolutePos(xp.north())), "no tap on an Experience Pipe");
        BlockPos valve = new BlockPos(8, 2, 3);
        helper.setBlock(valve, ModBlocks.VALVE.get().defaultBlockState().setValue(dev.nookworks.fluid.CopperFittingBlock.AXIS, Direction.Axis.X));
        check(helper, !tapFacing(Direction.NORTH, false).canSurvive(level, helper.absolutePos(valve.north())),
                "no tap on a Valve's side off its axis");
        helper.setBlock(valve, ModBlocks.VALVE.get().defaultBlockState().setValue(dev.nookworks.fluid.CopperFittingBlock.AXIS, Direction.Axis.Z));
        check(helper, tapFacing(Direction.NORTH, false).canSurvive(level, helper.absolutePos(valve.north())), "but on its end");
        helper.succeed();
    }

    /** An empty bucket held to a tap with less than a bucket behind it fills nothing and leaves the tap as it was. */
    private static void emptyBucketRefused(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 2, 3);
        BlockPos tap = tank.north();
        helper.setBlock(tank, ModBlocks.COPPER_TANK.get());
        var source = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        Move.insert(source.handlerFor(null), FluidResource.of(Fluids.WATER), 600);
        helper.setBlock(tap, tapFacing(Direction.NORTH, false));
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, new ArrayList<>());
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2, 0.5));
        player.moveTo(near.x, near.y, near.z);
        ItemStack bucket = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockPos at = helper.absolutePos(tap);
        player.gameMode.useItemOn(player, helper.getLevel(), bucket, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        check(helper, !helper.getBlockState(tap).getValue(KitchenTapBlock.OPEN), "the tap is not turned by a bucket it cannot fill");
        check(helper, player.getMainHandItem().is(Items.BUCKET) && source.amount() == 600, "the bucket empty, the tank as it was");
        helper.succeed();
    }

    private static BlockState tapFacing(Direction facing, boolean open) {
        return ModCooking.KITCHEN_TAP.get().defaultBlockState().setValue(KitchenTapBlock.FACING, facing).setValue(KitchenTapBlock.OPEN, open);
    }

    private static void rejoin(GameTestHelper helper, BlockPos pos) {
        BlockPos at = helper.absolutePos(pos);
        helper.getLevel().setBlock(at, Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(at), helper.getLevel(), at), Block.UPDATE_ALL);
    }

    /**
     * On a pipe: the pipe joins its back; shut, the line keeps its water; open, the water runs through it into the pot
     * under its spout, a bucket in about two seconds, and it is marked pouring. Nothing joins it but at its back.
     */
    private static void pours(GameTestHelper helper) {
        BlockPos tap = new BlockPos(2, 3, 2);
        BlockPos pipe = tap.south();
        BlockPos pot = tap.below();
        helper.setBlock(pot.below(), Blocks.STONE);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get());
        BlockState shut = tapFacing(Direction.NORTH, false);
        check(helper, shut.canSurvive(helper.getLevel(), helper.absolutePos(tap)), "a tap may be set on a pipe");
        helper.setBlock(tap, shut);
        rejoin(helper, pipe);
        check(helper, helper.getBlockState(pipe).getValue(PipeBlock.NORTH), "the pipe joins the tap set on it");

        var level = helper.getLevel();
        BlockPos tapAt = helper.absolutePos(tap);
        for (Direction side : new Direction[] {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.EAST, Direction.WEST}) {
            check(helper, level.getCapability(Capabilities.Fluid.BLOCK, tapAt, side) == null, "no pipe joins it " + side);
        }
        check(helper, level.getCapability(Capabilities.Fluid.BLOCK, tapAt, Direction.SOUTH) != null, "a pipe joins it at its back");

        var line = helper.getBlockEntity(pipe, CopperPipeBlockEntity.class);
        check(helper, Move.insert(line.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000) == 1_000, "a bucket into the pipe");
        var vessel = helper.getBlockEntity(pot, IronPotBlockEntity.class);
        boolean[] opened = {false};
        boolean[] poured = {false};
        long[] openedAt = {0};
        int[] early = {-1};
        helper.runAfterDelay(40, () -> {
            check(helper, vessel.amount() == 0, "shut, none reaches the pot: " + vessel.amount());
            helper.setBlock(tap, helper.getBlockState(tap).setValue(KitchenTapBlock.OPEN, true));
            opened[0] = true;
            openedAt[0] = level.getGameTime();
        });
        helper.onEachTick(() -> {
            poured[0] |= helper.getBlockState(tap).getValue(KitchenTapBlock.POURING);
            if (opened[0] && early[0] < 0 && level.getGameTime() - openedAt[0] >= 10) {
                early[0] = vessel.amount();
            }
        });
        helper.succeedWhen(() -> {
            check(helper, opened[0], "opened");
            check(helper, vessel.amount() == 1_000, "open, the bucket goes through into the pot: " + vessel.amount());
            check(helper, early[0] >= 0 && early[0] < 1_000, "not all in a tick, as a tap runs: " + early[0] + " after half a second");
            check(helper, poured[0], "and the tap was seen pouring");
        });
    }

    /**
     * It goes on a pipe, a Copper Tank or a cauldron, and not on dirt or stone, nor on a pot. And no pipe joins an Iron
     * Pot set beside it: the tap pours into a pot (「蛇口使うのであれば、鍋に向かって銅管が開く必要ないよね？」).
     */
    private static void onlyOnSources(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos[] behind = {new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1), new BlockPos(7, 2, 1), new BlockPos(9, 2, 1)};
        helper.setBlock(behind[0], Blocks.DIRT);
        helper.setBlock(behind[1], Blocks.STONE);
        helper.setBlock(behind[2], ModBlocks.COPPER_TANK.get());
        helper.setBlock(behind[3], Blocks.CAULDRON);
        helper.setBlock(behind[4], ModCooking.IRON_POT.get());
        boolean[] expected = {false, false, true, true, false};
        String[] what = {"dirt", "stone", "a Copper Tank", "a cauldron", "an Iron Pot"};
        for (int i = 0; i < behind.length; i++) {
            BlockPos tap = behind[i].north();
            boolean survives = tapFacing(Direction.NORTH, false).canSurvive(level, helper.absolutePos(tap));
            check(helper, survives == expected[i], (expected[i] ? "a tap goes on " : "no tap goes on ") + what[i]);
        }
        BlockPos pot = new BlockPos(2, 2, 4);
        BlockPos pipe = pot.west();
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get());
        rejoin(helper, pipe);
        check(helper, !helper.getBlockState(pipe).getValue(PipeBlock.EAST), "a pipe does not join a pot beside it");
        helper.succeed();
    }

    /** On a Copper Tank's side, over a pot: open, the tank's water goes into the pot, as much as the pot holds. */
    private static void tankIntoPot(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 3, 3);
        BlockPos tap = tank.north();
        BlockPos pot = tap.below();
        helper.setBlock(tank.below(), Blocks.STONE);
        helper.setBlock(tank, ModBlocks.COPPER_TANK.get());
        helper.setBlock(pot.below(), Blocks.STONE);
        helper.setBlock(pot, ModCooking.IRON_POT.get());
        var source = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        check(helper, Move.insert(source.handlerFor(null), FluidResource.of(Fluids.WATER), 3_000) == 3_000, "three buckets in the tank");
        helper.setBlock(tap, tapFacing(Direction.NORTH, true));
        var vessel = helper.getBlockEntity(pot, IronPotBlockEntity.class);
        helper.succeedWhen(() -> {
            check(helper, vessel.amount() == IronPotBlockEntity.CAPACITY, "the pot filled from the tank: " + vessel.amount());
            check(helper, source.amount() <= 2_000, "out of the tank: " + source.amount());
        });
    }

    /** On a full water cauldron's side, over an empty cauldron: the water goes from the one into the other. */
    private static void cauldronIntoCauldron(GameTestHelper helper) {
        BlockPos upper = new BlockPos(2, 3, 3);
        BlockPos tap = upper.north();
        BlockPos lower = tap.below();
        helper.setBlock(upper.below(), Blocks.STONE);
        helper.setBlock(upper, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3));
        helper.setBlock(lower.below(), Blocks.STONE);
        helper.setBlock(lower, Blocks.CAULDRON);
        check(helper, tapFacing(Direction.NORTH, true).canSurvive(helper.getLevel(), helper.absolutePos(tap)), "a tap goes on the cauldron");
        helper.setBlock(tap, tapFacing(Direction.NORTH, true));
        helper.succeedWhen(() -> {
            BlockState below = helper.getBlockState(lower);
            check(helper, below.is(Blocks.WATER_CAULDRON) && below.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    "the lower cauldron full of water: " + below);
            check(helper, helper.getBlockState(upper).is(Blocks.CAULDRON), "and the upper one empty: " + helper.getBlockState(upper));
        });
    }

    /** An empty bucket held to a tap on a tank is filled from it; a redstone signal opens the tap, and its end shuts it. */
    private static void bucketAndRedstone(GameTestHelper helper) {
        BlockPos tank = new BlockPos(2, 2, 3);
        BlockPos tap = tank.north();
        helper.setBlock(tank, ModBlocks.COPPER_TANK.get());
        var source = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        Move.insert(source.handlerFor(null), FluidResource.of(Fluids.WATER), 2_000);
        helper.setBlock(tap, tapFacing(Direction.NORTH, false));
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, new ArrayList<>());
        Vec3 near = helper.absoluteVec(new Vec3(2.5, 2, 0.5));
        player.moveTo(near.x, near.y, near.z);
        ItemStack bucket = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        BlockPos at = helper.absolutePos(tap);
        player.gameMode.useItemOn(player, helper.getLevel(), bucket, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        check(helper, player.getInventory().countItem(Items.WATER_BUCKET) == 1 && source.amount() == 1_000,
                "a bucket filled at the shut tap from the tank: " + player.getInventory().items + ", " + source.amount());
        check(helper, !helper.getBlockState(tap).getValue(KitchenTapBlock.OPEN), "and the tap still shut");
        helper.setBlock(tap.east(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(tap).getValue(KitchenTapBlock.OPEN), "a signal opens it");
        helper.setBlock(tap.east(), Blocks.AIR);
        check(helper, !helper.getBlockState(tap).getValue(KitchenTapBlock.OPEN), "and its end shuts it");
        helper.succeed();
    }
}
