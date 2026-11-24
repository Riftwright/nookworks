package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import java.util.function.Predicate;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The advancements added in the review of 2026-11-22 that the mod awards itself or that ask for a deed, earned as a
 * player earns them - and not for the click beside it: food set out on a Plate (not a stick clicked on one), a pipe's
 * side set to pull with the Wrench (not one switched off), a Kitchen Tap fitted. The rest only ask to have an item.
 */
final class AdvancementTests {
    private AdvancementTests() {}

    static void register() {
        NookworksGameTests.test("food_set_on_a_plate_earns_dinner_is_served", AdvancementTests::setTable);
        NookworksGameTests.test("a_pipe_set_to_pull_with_the_wrench_earns_its_advancement", AdvancementTests::wrenchPull);
        NookworksGameTests.test("a_kitchen_tap_fitted_earns_its_advancement", AdvancementTests::kitchenTap);
    }

    /** A survival player flying clear above the test, sneaking or not. */
    private static ServerPlayer player(GameTestHelper helper, boolean sneaking) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        player.getAbilities().mayfly = true;
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setShiftKeyDown(sneaking);
        return player;
    }

    private static Predicate<String> had(GameTestHelper helper, ServerPlayer player) {
        var server = helper.getLevel().getServer();
        return id -> {
            var holder = server.getAdvancements().get(Nookworks.id(id));
            return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
        };
    }

    /** Right-clicks the middle of {@code face} of {@code pos} holding {@code stack}, as the game makes a click. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        Vec3 point = Vec3.atCenterOf(at).relative(face, 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, at, false));
    }

    private static void setTable(GameTestHelper helper) {
        ServerPlayer player = player(helper, false);
        var done = had(helper, player);
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at.below(), net.minecraft.world.level.block.Blocks.OAK_PLANKS);
        helper.setBlock(at, ModKitchen.PLATE.get());
        click(helper, player, new ItemStack(Items.STICK), at, Direction.UP);
        check(helper, !done.test("set_table"), "a stick clicked on a plate is no dish set out");
        click(helper, player, new ItemStack(Items.BREAD, 2), at, Direction.UP);
        check(helper, done.test("set_table"), "bread set out on the plate");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static void wrenchPull(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        var done = had(helper, player);
        BlockPos tank = new BlockPos(1, 2, 1);
        helper.setBlock(tank, ModBlocks.COPPER_TANK.get());
        pipe(helper, tank.east());
        pipe(helper, tank.east(2));
        BlockPos pipeAt = tank.east();
        Vec3 westFace = Vec3.atCenterOf(helper.absolutePos(pipeAt)).add(-0.45, 0, 0);
        Vec3 eastFace = Vec3.atCenterOf(helper.absolutePos(pipeAt)).add(0.45, 0, 0);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        // the side against the next pipe: switched off, which is no pulling
        use(helper, player, wrench, pipeAt, eastFace, Direction.EAST);
        check(helper, !done.test("wrench"), "a pipe's side switched off is not set to pull");
        // the side against the tank: joined, then pulling at the first turn
        use(helper, player, wrench, pipeAt, westFace, Direction.WEST);
        check(helper, done.test("wrench"), "the pipe's side against the tank set to pull");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    private static void kitchenTap(GameTestHelper helper) {
        ServerPlayer player = player(helper, false);
        var done = had(helper, player);
        BlockPos at = new BlockPos(1, 2, 1);
        CriteriaTriggers.PLACED_BLOCK.trigger(player, helper.absolutePos(at), new ItemStack(Items.STONE));
        check(helper, !done.test("kitchen_tap"), "a block of something else set down");
        helper.setBlock(at, ModCooking.KITCHEN_TAP.get());
        CriteriaTriggers.PLACED_BLOCK.trigger(player, helper.absolutePos(at), new ItemStack(ModCooking.KITCHEN_TAP.get()));
        check(helper, done.test("kitchen_tap"), "a Kitchen Tap set down");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** {@code stack} used at {@code hit} on {@code face} of {@code block}. */
    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos block, Vec3 hit, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit, face, helper.absolutePos(block), false));
    }

    /** A Copper Pipe set down as placing it shapes it among its neighbours. */
    private static void pipe(GameTestHelper helper, BlockPos at) {
        Block block = ModBlocks.COPPER_PIPE.get();
        helper.setBlock(at, block.getStateForPlacement(new BlockPlaceContext(helper.getLevel(), null, InteractionHand.MAIN_HAND,
                new ItemStack(block.asItem()), new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(at)), Direction.UP,
                        helper.absolutePos(at), false))));
    }
}
