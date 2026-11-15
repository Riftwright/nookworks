package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.machine.RedstoneMode;
import dev.nookworks.menu.tab.MachineTab;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * A sorter answers redstone as a machine does, set in its screen's Settings tab (the user, 2026-11-14: 「機械と同じような
 * GUI出るんだから、設定タブでそろえたほうがよくない？ブロックの見た目も、ホッパーよりも機械寄りだよね？」).
 */
final class SorterRedstoneTests {
    private SorterRedstoneTests() {}

    static final String[] NEED_SORTERS = {"a_sorter_answers_redstone_as_its_settings_tab_says"};

    static void register() {
        NookworksGameTests.test(NEED_SORTERS[0], 200, SorterRedstoneTests::redstone);
    }

    private static int count(ChestBlockEntity chest) {
        int n = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            n += chest.getItem(i).getCount();
        }
        return n;
    }

    /**
     * A new Item Sorter beside a block of redstone, a chest of diamonds over it and one at its right-hand outlet: it
     * neither draws nor sends (a new one stops while powered); its screen has the Settings tab, and the redstone button
     * there turns it to run whatever the signal, and the diamonds go through. A sorter saved before it had a mode loads
     * running whatever the signal, as it always did; a mode set is saved.
     */
    private static void redstone(GameTestHelper helper) {
        SorterBlockEntity sorter = StorageTests.placeSorter(helper, ModBlocks.ITEM_SORTER.get(), false);
        BlockPos at = StorageTests.SORTER_POS;
        helper.setBlock(at.above(), Blocks.CHEST);
        helper.setBlock(at.west(), Blocks.CHEST); // the right-hand outlet of one facing north
        helper.setBlock(at.south(), Blocks.REDSTONE_BLOCK);
        var above = helper.getBlockEntity(at.above(), ChestBlockEntity.class);
        var right = helper.getBlockEntity(at.west(), ChestBlockEntity.class);
        above.setItem(0, new ItemStack(Items.DIAMOND, 4));
        check(helper, sorter.redstoneMode() == RedstoneMode.WITHOUT_SIGNAL, "a new sorter stops while powered: " + sorter.redstoneMode());

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        SorterMenu menu = new SorterMenu(1, player.getInventory(), sorter.filter(), sorter.gearSlot(), helper.absolutePos(at),
                sorter.sorterTier(), sorter.settings());
        check(helper, menu.tabs().has(MachineTab.SETTINGS), "its screen has the Settings tab");

        // the old save: no mode, and it ran whatever the signal
        var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
        sorter.saveCustomOnly(saved);
        var tag = saved.buildResult();
        check(helper, tag.getStringOr("redstone_mode", "").equals("WITHOUT_SIGNAL"), "its mode is saved: " + tag.getStringOr("redstone_mode", "-"));
        tag.remove("redstone_mode");
        var old = new SorterBlockEntity(helper.absolutePos(at.east(4)), helper.getBlockState(at));
        old.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        check(helper, old.redstoneMode() == RedstoneMode.ALWAYS, "one saved before it had a mode runs whatever the signal: " + old.redstoneMode());

        helper.runAfterDelay(60, () -> {
            check(helper, count(above) == 4 && count(right) == 0 && sorter.getItem(0).isEmpty(),
                    "powered, nothing drawn or sent: " + count(above) + " above, " + count(right) + " sent");
            check(helper, menu.clickMenuButton(player, SorterMenu.BUTTON_REDSTONE) && sorter.redstoneMode() == RedstoneMode.NEVER,
                    "the redstone button: next, never runs: " + sorter.redstoneMode());
            check(helper, menu.clickMenuButton(player, SorterMenu.BUTTON_REDSTONE) && sorter.redstoneMode() == RedstoneMode.ALWAYS,
                    "and on, running whatever the signal: " + sorter.redstoneMode());
            helper.succeedWhen(() -> check(helper, count(right) == 4, "then the diamonds go through: " + count(right)));
        });
    }
}
