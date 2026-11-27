package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModOil;
import dev.nookworks.sorter.SorterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

/**
 * A vanilla crafter crafting into an Item Sorter (the user, 2026-11-24: 「自動作業台から搬出されたアイテム、ソーターが受け入れ
 * ない」). Set over the sorter facing down, the sorter drew the crafter's ingredients out as from a chest, its one-slot
 * input filled with them, and what was crafted fell on the ground (a hopper there, with five slots, took it). A crafter's
 * grid is its recipe: the sorter leaves it alone and takes what it crafts. From a side it always did.
 */
final class SorterCrafterTests {
    private SorterCrafterTests() {}

    static void register() {
        for (Direction side : new Direction[] {Direction.UP, Direction.SOUTH}) {
            test("sorter_takes_what_a_crafter_" + (side == Direction.UP ? "above" : "beside") + "_crafts", 100,
                    helper -> crafts(helper, side));
        }
        test("sorter_takes_a_sunflowers_dye_and_seeds_from_a_crafter", 100, SorterCrafterTests::sunflower);
    }

    /**
     * The user's circuit: a crafter plucks a sunflower - yellow dye, and the four seeds its recipe leaves - into a sorter
     * whose filter holds the seeds, the seeds bound for an Oil Press. Both come out of the crafter at once; with one slot
     * the sorter took the dye and the seeds fell on the ground. Five slots take both, and each goes its own way.
     */
    private static void sunflower(GameTestHelper helper) {
        BlockPos sorter = new BlockPos(3, 2, 3);
        BlockPos crafter = sorter.south();
        helper.setBlock(sorter, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        BlockPos registered = sorter.relative(SorterBlock.registeredSide(helper.getBlockState(sorter)));
        BlockPos other = sorter.relative(SorterBlock.otherSide(helper.getBlockState(sorter)));
        helper.setBlock(registered, Blocks.BARREL);
        helper.setBlock(other, Blocks.BARREL);
        helper.setBlock(crafter, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION, FrontAndTop.NORTH_UP));
        helper.getBlockEntity(sorter, SorterBlockEntity.class).filter().setItem(0, new ItemStack(ModOil.SUNFLOWER_SEEDS.get()));
        helper.getBlockEntity(crafter, CrafterBlockEntity.class).setItem(0, new ItemStack(Items.SUNFLOWER, 2));
        helper.setBlock(crafter.south(), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(60, () -> {
            var seeds = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(registered));
            var rest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(other));
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(sorter)).inflate(4));
            check(helper, loose.isEmpty(), "nothing on the ground: " + loose.stream().map(ItemEntity::getItem).toList());
            check(helper, countIn(seeds, ModOil.SUNFLOWER_SEEDS.get()) == 4, "the four seeds go the seeds' way: "
                    + countIn(seeds, ModOil.SUNFLOWER_SEEDS.get()));
            check(helper, countIn(rest, Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW)) == 2 && countIn(rest, ModOil.SUNFLOWER_SEEDS.get()) == 0,
                    "the dye the other way: " + countIn(rest, Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW)));
            helper.succeed();
        });
    }

    /** Four logs in a crafter's first slot on the sorter's {@code side}, facing it; one pulse: four planks, sorted. */
    private static void crafts(GameTestHelper helper, Direction side) {
        BlockPos sorter = new BlockPos(3, 2, 3);
        BlockPos crafter = sorter.relative(side);
        helper.setBlock(sorter, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        BlockPos registered = sorter.relative(SorterBlock.registeredSide(helper.getBlockState(sorter)));
        BlockPos other = sorter.relative(SorterBlock.otherSide(helper.getBlockState(sorter)));
        helper.setBlock(registered, Blocks.BARREL);
        helper.setBlock(other, Blocks.BARREL);
        Direction front = side.getOpposite();
        helper.setBlock(crafter, Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION,
                FrontAndTop.fromFrontAndTop(front, front.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP)));
        helper.getBlockEntity(sorter, SorterBlockEntity.class).filter().setItem(0, new ItemStack(Items.OAK_PLANKS));
        var grid = helper.getBlockEntity(crafter, CrafterBlockEntity.class);
        grid.setItem(0, new ItemStack(Items.OAK_LOG, 4));
        helper.setBlock(crafter.relative(side), Blocks.REDSTONE_BLOCK);
        helper.runAfterDelay(60, () -> {
            var sorted = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(registered));
            var rest = (Container) helper.getLevel().getBlockEntity(helper.absolutePos(other));
            var loose = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(sorter)).inflate(4));
            check(helper, countIn(sorted, Items.OAK_PLANKS) == 4, "the four planks crafted go out by the registered side: "
                    + countIn(sorted, Items.OAK_PLANKS) + ", on the ground " + loose.stream().map(ItemEntity::getItem).toList());
            check(helper, grid.getItem(0).is(Items.OAK_LOG) && grid.getItem(0).getCount() == 3,
                    "the crafter keeps its other three logs: " + grid.getItem(0));
            check(helper, countIn(rest, Items.OAK_LOG) == 0 && loose.isEmpty(), "no log drawn off, nothing on the ground");
            helper.succeed();
        });
    }
}
