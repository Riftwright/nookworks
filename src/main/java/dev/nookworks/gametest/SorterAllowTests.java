package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import com.mojang.logging.LogUtils;

import dev.nookworks.block.SorterBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * The Keep Out row turned round (the user, 2026-10-31: 「特定のアイテム以外の吸い込みを禁止」に切り替える、要はホワイトリストと
 * ブラックリストの切り替え): a click on the row's sign, on either sorter, and what is in the row is all that is drawn
 * in - everything else stays in the chest above, and a hopper cannot put it in. With nothing in the row, nothing comes
 * in. Kept with the sorter's settings.
 */
final class SorterAllowTests {
    private SorterAllowTests() {}

    static void register() {
        test("sorter_keep_out_row_turned_round_lets_in_only_what_is_in_it", SorterAllowTests::switched);
        test("sorter_with_its_row_turned_round_draws_only_those_from_the_chest_above", 200, SorterAllowTests::draws);
    }

    private static SorterBlockEntity sorter(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ITEM_SORTER.get().defaultBlockState().setValue(SorterBlock.FACING, Direction.NORTH));
        return helper.getBlockEntity(pos, SorterBlockEntity.class);
    }

    private static void switched(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        SorterBlockEntity sorter = sorter(helper, pos);
        sorter.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        var menu = (SorterMenu) sorter.createMenu(1, player.getInventory(), player);
        check(helper, sorter.denies(new ItemStack(Items.COBBLESTONE)) && !sorter.denies(new ItemStack(Items.DIRT)), "as it stands, the row keeps out");
        check(helper, menu.clickMenuButton(player, SorterMenu.BUTTON_FLAG + SorterSettings.KEEP_OUT_OTHERS)
                && sorter.settings().get(SorterSettings.KEEP_OUT_OTHERS), "an Item Sorter, with no switches of its own, takes the sign's click");
        check(helper, !sorter.denies(new ItemStack(Items.COBBLESTONE)) && sorter.denies(new ItemStack(Items.DIRT)),
                "turned round, the cobblestone is let in and the dirt kept out");
        check(helper, sorter.canPlaceItemThroughFace(0, new ItemStack(Items.COBBLESTONE), Direction.UP)
                && !sorter.canPlaceItemThroughFace(0, new ItemStack(Items.DIRT), Direction.UP), "a hopper too puts in only the cobblestone");
        check(helper, !menu.clickMenuButton(player, SorterMenu.BUTTON_FLAG + SorterSettings.BLACKLIST),
                "the Precision Sorter's own switches stay refused");
        try (var reporter = new ProblemReporter.ScopedCollector(sorter.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            sorter.saveWithoutMetadata(output);
            var copy = new SorterBlockEntity(sorter.getBlockPos(), sorter.getBlockState());
            copy.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            check(helper, copy.settings().get(SorterSettings.KEEP_OUT_OTHERS) && copy.denies(new ItemStack(Items.DIRT)), "kept when saved");
        }
        sorter.deny().setItem(0, ItemStack.EMPTY);
        check(helper, sorter.denies(new ItemStack(Items.COBBLESTONE)) && sorter.denies(new ItemStack(Items.DIRT)),
                "with nothing in the row, nothing is let in");
        menu.clickMenuButton(player, SorterMenu.BUTTON_FLAG + SorterSettings.KEEP_OUT_OTHERS);
        check(helper, !sorter.denies(new ItemStack(Items.DIRT)), "clicked again, an empty row keeps out nothing, as before");
        helper.succeed();
    }

    /** Cobblestone let in from a chest of cobblestone, dirt and diamonds: only the cobblestone goes; the rest stays. */
    private static void draws(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 1, 3);
        SorterBlockEntity sorter = sorter(helper, pos);
        sorter.deny().setItem(0, new ItemStack(Items.COBBLESTONE));
        sorter.settings().toggle(SorterSettings.KEEP_OUT_OTHERS);
        helper.setBlock(pos.west(), Blocks.CHEST);
        helper.setBlock(pos.east(), Blocks.CHEST);
        helper.setBlock(pos.above(), Blocks.CHEST);
        Container above = helper.getBlockEntity(pos.above(), ChestBlockEntity.class);
        above.setItem(0, new ItemStack(Items.DIRT, 5));
        above.setItem(3, new ItemStack(Items.COBBLESTONE, 9));
        above.setItem(7, new ItemStack(Items.DIAMOND, 2));
        helper.succeedWhen(() -> {
            Container left = helper.getBlockEntity(pos.east(), ChestBlockEntity.class);
            Container right = helper.getBlockEntity(pos.west(), ChestBlockEntity.class);
            check(helper, countIn(above, Items.COBBLESTONE) == 0 && countIn(left, Items.COBBLESTONE) + countIn(right, Items.COBBLESTONE) == 9,
                    "all the cobblestone is drawn in and sorted out");
            check(helper, countIn(above, Items.DIRT) == 5 && countIn(above, Items.DIAMOND) == 2, "the dirt and diamonds stay above");
            check(helper, sorter.isEmpty(), "and the sorter holds nothing");
        });
    }
}
