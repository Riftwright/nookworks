package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.feature.TrimmedVines;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * r8: cutting a vine so it stops spreading is the Fine Shears' alone (the user, 2026-10-06). Vanilla's shears act on a
 * vine as in vanilla - nothing at a right-click - and a dispenser holding the Fine Shears leaves a vine be, as one holding
 * vanilla's does. What a cut does after (breaking, burning, a vine grown into its place) is PlayTests' vine test.
 */
final class R8VinesTests {
    private R8VinesTests() {}

    static void register() {
        NookworksGameTests.test("fine_shears_trim_the_tip_of_twisting_vines_and_kelp", R8VinesTests::fineShearsTrimTips);
        NookworksGameTests.test("vanilla_shears_leave_a_vine_growing", R8VinesTests::vanillaShearsLeaveAVineGrowing);
        NookworksGameTests.test("fine_shears_stop_a_vine_spreading", R8VinesTests::fineShearsStopAVineSpreading);
        NookworksGameTests.test("a_dispenser_with_fine_shears_leaves_a_vine_be", R8VinesTests::aDispenserWithFineShearsLeavesAVineBe);
    }

    /** Vanilla's shears used on a vine - through the game's own right-click - neither cut it nor wear: it spreads as any. */
    private static void vanillaShearsLeaveAVineGrowing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = hangVine(helper, new BlockPos(3, 5, 2));
        ServerPlayer player = survivor(helper, new ItemStack(Items.SHEARS));
        InteractionResult result = click(player, at);
        check(helper, !result.consumesAction(), "vanilla's shears on a vine do nothing, as in vanilla: " + result);
        check(helper, !TrimmedVines.isTrimmed(level, at), "the vine is not cut");
        check(helper, player.getMainHandItem().getDamageValue() == 0, "nor are the shears worn");
        int grown = grow(level, at);
        check(helper, grown > 0, "the vine should have spread, as one never cut does");
        helper.succeed();
    }

    /** The Fine Shears used on a vine cut it, and wear one: given the same many random ticks, it spreads nowhere. */
    /**
     * The Fine Shears trim a growing plant's tip as vanilla's shears do: twisting vines and kelp set at their full age,
     * grow no further (the user, 2026-10-06).
     */
    private static void fineShearsTrimTips(GameTestHelper helper) {
        BlockPos vines = new BlockPos(2, 2, 2);
        helper.setBlock(vines.below(), Blocks.STONE);
        helper.setBlock(vines, Blocks.TWISTING_VINES.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 0));
        BlockPos kelp = new BlockPos(4, 2, 2);
        helper.setBlock(kelp.below(), Blocks.SAND);
        helper.setBlock(kelp, Blocks.KELP.defaultBlockState().setValue(GrowingPlantHeadBlock.AGE, 0));
        ServerPlayer player = survivor(helper, new ItemStack(ModItems.WOOL_SHEARS.get()));
        check(helper, click(player, helper.absolutePos(vines)).consumesAction(), "the twisting vines' tip is trimmed");
        check(helper, click(player, helper.absolutePos(kelp)).consumesAction(), "and the kelp's");
        check(helper, helper.getBlockState(vines).getValue(GrowingPlantHeadBlock.AGE) == GrowingPlantHeadBlock.MAX_AGE
                && helper.getBlockState(kelp).getValue(GrowingPlantHeadBlock.AGE) == GrowingPlantHeadBlock.MAX_AGE,
                "both at full age: they grow no further");
        check(helper, player.getMainHandItem().getDamageValue() == 2, "the shears wear one each");
        helper.succeed();
    }

    private static void fineShearsStopAVineSpreading(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = hangVine(helper, new BlockPos(3, 5, 2));
        ServerPlayer player = survivor(helper, new ItemStack(ModItems.WOOL_SHEARS.get()));
        InteractionResult result = click(player, at);
        check(helper, result.consumesAction(), "the click cuts: " + result);
        check(helper, TrimmedVines.isTrimmed(level, at), "the vine is cut");
        check(helper, player.getMainHandItem().getDamageValue() == 1, "and the Fine Shears wear one");
        int grown = grow(level, at);
        check(helper, grown == 0, "the cut vine spread to " + grown + " blocks");
        helper.succeed();
    }

    /**
     * A dispenser holding the Fine Shears, powered with a vine in front of it and no sheep: the vine is left uncut and the
     * shears unworn, as vanilla's shears in a dispenser leave it. A cut is made by hand.
     */
    private static void aDispenserWithFineShearsLeavesAVineBe(GameTestHelper helper) {
        BlockPos dispenser = new BlockPos(1, 1, 3);
        BlockPos vine = dispenser.east();
        helper.setBlock(vine.east(), Blocks.STONE);
        helper.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.EAST, true));
        helper.setBlock(dispenser, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.EAST));
        helper.getBlockEntity(dispenser, DispenserBlockEntity.class).setItem(0, new ItemStack(ModItems.WOOL_SHEARS.get()));
        helper.setBlock(dispenser.west(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(10, () -> {
            check(helper, helper.getBlockState(vine).is(Blocks.VINE), "the vine is still there");
            check(helper, !TrimmedVines.isTrimmed(helper.getLevel(), helper.absolutePos(vine)), "and not cut");
            ItemStack inside = helper.getBlockEntity(dispenser, DispenserBlockEntity.class).getItem(0);
            check(helper, inside.is(ModItems.WOOL_SHEARS.get()) && inside.getDamageValue() == 0,
                    "the shears stay in it, unworn: " + inside);
            helper.succeed();
        });
    }

    /** A vine on a stone wall to its south, with room below and beside it to spread; its absolute position. */
    private static BlockPos hangVine(GameTestHelper helper, BlockPos vine) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -3; y <= 1; y++) {
                helper.setBlock(vine.offset(x, y, 1), Blocks.STONE);
            }
        }
        helper.setBlock(vine, Blocks.VINE.defaultBlockState().setValue(VineBlock.SOUTH, true));
        return helper.absolutePos(vine);
    }

    /** A player in survival (a creative one keeps what they use) holding {@code held}, in front of the wall. */
    private static ServerPlayer survivor(GameTestHelper helper, ItemStack held) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 0.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /** A right-click on the vine at {@code at}, from the north, as the game's own handling of one takes it. */
    private static InteractionResult click(ServerPlayer player, BlockPos at) {
        return player.gameMode.useItemOn(player, player.level(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
    }

    /** Gives the vine at {@code at} 3000 random ticks; how many blocks about it then hold a vine (it excluded). */
    private static int grow(ServerLevel level, BlockPos at) {
        for (int tick = 0; tick < 3000; tick++) {
            BlockState state = level.getBlockState(at);
            if (state.is(Blocks.VINE)) {
                state.randomTick(level, at, level.getRandom());
            }
        }
        int grown = 0;
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-2, -4, -1), at.offset(2, 2, 1))) {
            if (!p.equals(at) && level.getBlockState(p).is(Blocks.VINE)) {
                grown++;
            }
        }
        return grown;
    }
}
