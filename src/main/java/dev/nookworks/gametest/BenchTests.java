package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;

import dev.nookworks.furniture.BenchBlock;
import dev.nookworks.furniture.ChairBlock;
import dev.nookworks.furniture.StoolBlock;
import dev.nookworks.registry.ModWoods;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Benches (2026-10-22): a long seat with no back that joins the benches beside it into one, as desks do, any wood,
 * either way round, so long as they run the same way; sat on as a stool is; made of three slabs over two sticks.
 */
final class BenchTests {
    private BenchTests() {}

    static void register() {
        test("benches_side_by_side_join_into_one", BenchTests::join);
        test("a_bench_seats_as_low_as_a_stool", BenchTests::seat);
        test("a_bench_turned_or_mirrored_keeps_its_joins", BenchTests::turned);
        test("a_bench_is_made_of_three_slabs_over_two_sticks", BenchTests::recipe);
        test("benches_stools_and_wall_shelves_burn_as_chairs_do", BenchTests::burn);
    }

    private static BlockState bench(String wood, Direction facing) {
        return ModWoods.BENCHES.get(wood).get().defaultBlockState().setValue(ChairBlock.FACING, facing);
    }

    /** Set down as a player sets one down: joined to what is beside it already (and they to it). */
    private static void place(GameTestHelper helper, BlockPos pos, BlockState state) {
        helper.setBlock(pos, Block.updateFromNeighbourShapes(state, helper.getLevel(), helper.absolutePos(pos)));
    }

    private static boolean left(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BenchBlock.LEFT);
    }

    private static boolean right(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(BenchBlock.RIGHT);
    }

    /**
     * Three benches in a row, two woods, one turned round: the middle one is joined on both sides, each end on its inner
     * side only; one running across them joins nothing; the middle one taken away, the ends let go.
     */
    private static void join(GameTestHelper helper) {
        // facing south, a bench's left (to someone in front, looking north) is west
        BlockPos west = new BlockPos(1, 1, 2);
        BlockPos middle = new BlockPos(2, 1, 2);
        BlockPos east = new BlockPos(3, 1, 2);
        BlockPos across = new BlockPos(4, 1, 2);
        place(helper, west, bench("oak", Direction.SOUTH));
        place(helper, middle, bench("spruce", Direction.SOUTH));
        place(helper, east, bench("oak", Direction.NORTH)); // turned round: its left is the east
        place(helper, across, bench("oak", Direction.EAST));
        check(helper, left(helper, middle) && right(helper, middle), "the middle bench is joined on both sides");
        check(helper, !left(helper, west) && right(helper, west), "the west end is joined on its east side only");
        check(helper, right(helper, east) && !left(helper, east), "the east one, turned round, on its west side (its right) only");
        check(helper, !left(helper, across) && !right(helper, across), "one running across them joins nothing");
        helper.setBlock(middle, Blocks.AIR);
        check(helper, !right(helper, west) && !right(helper, east), "the middle one taken away, the ends let go");
        helper.succeed();
    }

    private static void seat(GameTestHelper helper) {
        BenchBlock bench = ModWoods.BENCHES.get("oak").get();
        check(helper, bench.seatHeight() == StoolBlock.SEAT_HEIGHT, "a bench seats as a stool does: " + bench.seatHeight());
        check(helper, ModWoods.BENCHES.keySet().equals(ModWoods.STOOLS.keySet()), "a bench for every wood with a stool");
        helper.succeed();
    }

    /** Turned, its joins stay its own; mirrored, its left is its right. */
    private static void turned(GameTestHelper helper) {
        BlockState joined = bench("oak", Direction.SOUTH).setValue(BenchBlock.LEFT, true);
        BlockState turned = joined.rotate(Rotation.CLOCKWISE_90);
        check(helper, turned.getValue(ChairBlock.FACING) == Direction.WEST && turned.getValue(BenchBlock.LEFT)
                && !turned.getValue(BenchBlock.RIGHT), "turned a quarter, it faces west and is joined on its left still");
        BlockState mirrored = joined.mirror(Mirror.FRONT_BACK);
        check(helper, mirrored.getValue(BenchBlock.RIGHT) && !mirrored.getValue(BenchBlock.LEFT), "mirrored, it is joined on its right");
        helper.succeed();
    }

    /** Every wooden piece of furniture catches fire, unless its wood grew in the Nether (the stool and shelf did not at all). */
    private static void burn(GameTestHelper helper) {
        var fire = (FireBlock) Blocks.FIRE;
        ModWoods.CHAIRS.forEach((wood, chair) -> {
            boolean nether = wood.equals("crimson") || wood.equals("warped");
            for (var block : List.of(chair.get(), ModWoods.STOOLS.get(wood).get(), ModWoods.BENCHES.get(wood).get(),
                    ModWoods.DESKS.get(wood).get(), ModWoods.WALL_SHELVES.get(wood).get())) {
                check(helper, (fire.getBurnOdds(block.defaultBlockState()) > 0) != nether, block + " burns only if its wood does");
            }
        });
        helper.succeed();
    }

    private static void recipe(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack slab = new ItemStack(Items.OAK_SLAB);
        ItemStack stick = new ItemStack(Items.STICK);
        CraftingInput grid = CraftingInput.of(3, 2, List.of(slab, slab, slab, stick, ItemStack.EMPTY, stick));
        var made = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, grid, level)
                .map(holder -> holder.value().assemble(grid)).orElse(ItemStack.EMPTY);
        check(helper, made.is(ModWoods.BENCH_ITEMS.get("oak").get()) && made.getCount() == 1, "three oak slabs over two sticks make an oak bench: " + made);
        CraftingInput planks = CraftingInput.of(3, 2, List.of(new ItemStack(Items.OAK_PLANKS), new ItemStack(Items.OAK_PLANKS),
                new ItemStack(Items.OAK_PLANKS), stick, ItemStack.EMPTY, stick));
        var stool = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, planks, level)
                .map(holder -> holder.value().assemble(planks)).orElse(ItemStack.EMPTY);
        check(helper, stool.is(ModWoods.STOOL_ITEMS.get("oak").get()), "planks over sticks are still a stool: " + stool);
        helper.succeed();
    }
}
