package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.Map;

import dev.nookworks.furniture.BenchBlock;
import dev.nookworks.furniture.ChairBlock;
import dev.nookworks.furniture.DeskBlock;
import dev.nookworks.furniture.WallShelfBlock;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Furniture that joins (the user, 2026-10-23): a bench set down beside another takes its facing (sneaking, the
 * player's); benches, desks and wall shelves side by side are kept apart by a Wrench sneak-used on the end or edge
 * between them, and joined again the same way, the parting cleared once the other one goes; and poplar, 26.3's wood,
 * has all six pieces.
 */
final class FurnitureApartTests {
    private FurnitureApartTests() {}

    static void register() {
        test("a_bench_set_beside_one_takes_its_facing", FurnitureApartTests::facing);
        test("a_wrench_keeps_benches_apart_and_joins_them_again", FurnitureApartTests::benches);
        test("a_wrench_keeps_desks_apart_and_joins_them_again", FurnitureApartTests::desks);
        test("a_wrench_keeps_wall_shelves_apart_and_joins_them_again", FurnitureApartTests::shelves);
        test("poplar_has_every_piece_of_furniture", FurnitureApartTests::poplar);
    }

    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, Block.updateFromNeighbourShapes(state, helper.getLevel(), helper.absolutePos(pos)));
    }

    /** {@code stack} used by {@code player} at {@code point} (relative to the test) on {@code face} of {@code block}. */
    private static void use(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos block, Vec3 point, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(block);
        Vec3 hit = Vec3.atLowerCornerOf(helper.absolutePos(BlockPos.ZERO)).add(point);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, at, false));
    }

    private static ServerPlayer player(GameTestHelper helper, float yaw, boolean sneaking) {
        ServerPlayer player = MiningTests.playerWith(helper, ItemStack.EMPTY);
        player.setYRot(yaw);
        player.setShiftKeyDown(sneaking);
        return player;
    }

    /**
     * A bench facing north stands at x2; a player facing west sets one down east of it: it faces north too, and joins.
     * Sneaking, the next one faces as the player does (east, the player's back to it) and stands apart.
     */
    private static void facing(GameTestHelper helper) {
        BlockPos first = new BlockPos(2, 1, 2);
        place(helper, first, ModWoods.BENCHES.get("oak").get().defaultBlockState().setValue(ChairBlock.FACING, Direction.NORTH));
        ItemStack benches = new ItemStack(ModWoods.BENCH_ITEMS.get("spruce").get(), 4);
        use(helper, player(helper, 90.0F, false), benches.copy(), new BlockPos(3, 0, 2), new Vec3(3.5, 1.0, 2.5), Direction.UP);
        BlockState second = helper.getBlockState(new BlockPos(3, 1, 2));
        check(helper, second.getBlock() instanceof BenchBlock && second.getValue(ChairBlock.FACING) == Direction.NORTH,
                "set beside a bench facing north, it faces north as well: " + second);
        check(helper, helper.getBlockState(first).getValue(BenchBlock.LEFT), "and the two are joined");
        use(helper, player(helper, 90.0F, true), benches.copy(), new BlockPos(4, 0, 2), new Vec3(4.5, 1.0, 2.5), Direction.UP);
        BlockState third = helper.getBlockState(new BlockPos(4, 1, 2));
        check(helper, third.getBlock() instanceof BenchBlock && third.getValue(ChairBlock.FACING) == Direction.EAST,
                "sneaking, it faces as the player has it: " + third);
        check(helper, !helper.getBlockState(new BlockPos(3, 1, 2)).getValue(BenchBlock.LEFT), "and stands apart");
        helper.succeed();
    }

    /** A sneaking player in the level, with a connection for the Wrench's word on the action bar, clear of the test. */
    private static ServerPlayer wrencher(GameTestHelper helper) {
        // creative and flying, as R7WrenchFluidsTests' clicker: a survival one held up there fell to the void and died
        // over and over, and the batch it was in never ended
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.setShiftKeyDown(true);
        return player;
    }

    private static void done(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** Two benches facing north (a bench's left, so, is east): the Wrench on the west one's east end parts them, again joins. */
    private static void benches(GameTestHelper helper) {
        BlockPos west = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        BlockState bench = ModWoods.BENCHES.get("oak").get().defaultBlockState().setValue(ChairBlock.FACING, Direction.NORTH);
        place(helper, west, bench);
        place(helper, east, bench);
        check(helper, helper.getBlockState(west).getValue(BenchBlock.LEFT), "two benches side by side are joined");
        ServerPlayer player = wrencher(helper);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        use(helper, player, wrench, west, new Vec3(2.9, 1.4, 2.5), Direction.UP);
        check(helper, !helper.getBlockState(west).getValue(BenchBlock.LEFT) && helper.getBlockState(west).getValue(BenchBlock.LEFT_APART)
                && !helper.getBlockState(east).getValue(BenchBlock.RIGHT) && helper.getBlockState(east).getValue(BenchBlock.RIGHT_APART),
                "the Wrench keeps them apart, both");
        check(helper, helper.getBlockState(west).getValue(ChairBlock.FACING) == Direction.NORTH, "and turns neither");
        use(helper, player, wrench, east, new Vec3(3.1, 1.4, 2.5), Direction.UP);
        check(helper, helper.getBlockState(west).getValue(BenchBlock.LEFT) && helper.getBlockState(east).getValue(BenchBlock.RIGHT),
                "used again on the same join, from the other bench, joins them again");
        use(helper, player, wrench, west, new Vec3(2.9, 1.4, 2.5), Direction.UP);
        helper.setBlock(east, Blocks.AIR);
        check(helper, !helper.getBlockState(west).getValue(BenchBlock.LEFT_APART), "the other one gone, the parting goes with it");
        place(helper, east, bench);
        check(helper, helper.getBlockState(west).getValue(BenchBlock.LEFT), "and a bench set there again joins");
        use(helper, player, wrench, new BlockPos(2, 1, 2), new Vec3(2.1, 1.4, 2.5), Direction.UP);
        check(helper, helper.getBlockState(west).getValue(ChairBlock.FACING) != Direction.NORTH,
                "the end with no bench beside it: the Wrench turns it, as before");
        done(helper, player);
    }

    /** Two desks east and west of each other: the Wrench on the west one's east edge parts them, again joins. */
    private static void desks(GameTestHelper helper) {
        BlockPos west = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        BlockState desk = ModWoods.DESKS.get("oak").get().defaultBlockState();
        place(helper, west, desk);
        place(helper, east, desk);
        check(helper, helper.getBlockState(west).getValue(DeskBlock.EAST), "two desks side by side are joined");
        ServerPlayer player = wrencher(helper);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        use(helper, player, wrench, west, new Vec3(2.95, 2.0, 2.5), Direction.UP);
        check(helper, !helper.getBlockState(west).getValue(DeskBlock.EAST) && helper.getBlockState(west).getValue(DeskBlock.EAST_APART)
                && !helper.getBlockState(east).getValue(DeskBlock.WEST) && helper.getBlockState(east).getValue(DeskBlock.WEST_APART),
                "the Wrench keeps them apart, both");
        use(helper, player, wrench, west, new Vec3(2.95, 2.0, 2.5), Direction.UP);
        check(helper, helper.getBlockState(west).getValue(DeskBlock.EAST) && helper.getBlockState(east).getValue(DeskBlock.WEST),
                "used again, joins them again");
        done(helper, player);
    }

    /** Two shelves on a wall to their south, facing north (left is east): the Wrench on the west one's east end parts them. */
    private static void shelves(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            helper.setBlock(new BlockPos(x, 2, 3), Blocks.STONE);
        }
        BlockPos west = new BlockPos(2, 2, 2);
        BlockPos east = new BlockPos(3, 2, 2);
        BlockState shelf = ModWoods.WALL_SHELVES.get("oak").get().defaultBlockState().setValue(WallShelfBlock.FACING, Direction.NORTH);
        place(helper, west, shelf);
        place(helper, east, shelf);
        check(helper, helper.getBlockState(west).getValue(WallShelfBlock.LEFT), "two shelves side by side are joined");
        ServerPlayer player = wrencher(helper);
        ItemStack wrench = new ItemStack(ModItems.WRENCH.get());
        use(helper, player, wrench, west, new Vec3(2.9, 3.0, 2.5), Direction.UP);
        check(helper, !helper.getBlockState(west).getValue(WallShelfBlock.LEFT) && helper.getBlockState(west).getValue(WallShelfBlock.LEFT_APART)
                && !helper.getBlockState(east).getValue(WallShelfBlock.RIGHT) && helper.getBlockState(east).getValue(WallShelfBlock.RIGHT_APART),
                "the Wrench keeps them apart, both");
        use(helper, player, wrench, west, new Vec3(2.9, 3.0, 2.5), Direction.UP);
        check(helper, helper.getBlockState(west).getValue(WallShelfBlock.LEFT) && helper.getBlockState(east).getValue(WallShelfBlock.RIGHT),
                "used again, joins them again");
        done(helper, player);
    }

    /** Poplar (26.3's wood) has a support, a chair, a stool, a desk, a bench and a wall shelf, each with its recipe. */
    private static void poplar(GameTestHelper helper) {
        List<Map<String, ? extends DeferredBlock<?>>> kinds = List.of(ModWoods.SUPPORTS, ModWoods.CHAIRS, ModWoods.STOOLS, ModWoods.DESKS,
                ModWoods.BENCHES, ModWoods.WALL_SHELVES);
        for (Map<String, ? extends DeferredBlock<?>> kind : kinds) {
            DeferredBlock<?> block = kind.get("poplar");
            check(helper, block != null, "a poplar piece of every kind");
            ResourceKey<net.minecraft.world.item.crafting.Recipe<?>> recipe = ResourceKey.create(Registries.RECIPE, block.getId());
            check(helper, helper.getLevel().getServer().getRecipeManager().byKey(recipe).isPresent(), "and its recipe: " + block.getId());
        }
        check(helper, ModWoods.SUPPORT_WOODS.indexOf("poplar") == ModWoods.SUPPORT_WOODS.indexOf("pale_oak") + 1,
                "listed after pale oak, as vanilla lists it");
        helper.succeed();
    }
}
