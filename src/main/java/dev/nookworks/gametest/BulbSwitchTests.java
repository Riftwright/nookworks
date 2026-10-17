package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.List;

import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.FilamentBulbBlock;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Filament Bulbs switched off and on by hand (the user, 2026-10-16: "フィラメント電球も右クリックでオンオフできたらいいんじゃない？
 * 火打ち石は使わなくていいから。"): a bulb set down, by an empty hand; one hung on a rope, by sneaking with an empty hand, as a
 * lantern is put out, the plain hand still taking it down.
 */
final class BulbSwitchTests {
    private BulbSwitchTests() {}

    static void register() {
        NookworksGameTests.test("a_filament_bulb_set_down_switches_off_and_on", BulbSwitchTests::placed);
        NookworksGameTests.test("a_hung_filament_bulb_switches_off_and_on", 80, BulbSwitchTests::hung);
    }

    /**
     * Set down it is on, at 13; an empty hand switches it off - no light, its coil dark (its own model) - and on again;
     * an adventurer's hand does nothing. Mined, on or off, it gives the plain Filament Bulb.
     */
    private static void placed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 3, 3);
        helper.setBlock(pos.above(), Blocks.STONE);
        helper.setBlock(pos, ModRope.FILAMENT_BULB.get());
        BlockState on = helper.getBlockState(pos);
        check(helper, on.getValue(FilamentBulbBlock.LIT) && on.getLightEmission() == FilamentBulbBlock.LIGHT_LEVEL, "set down, it is on");
        ServerPlayer player = RopeTests.player(helper, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos at = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false);
        check(helper, helper.getBlockState(pos).useWithoutItem(level, player, hit) == InteractionResult.SUCCESS
                && !helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT) && helper.getBlockState(pos).getLightEmission() == 0,
                "an empty hand switches it off: no light");
        List<ItemStack> drops = Block.getDrops(helper.getBlockState(pos), level, at, null);
        check(helper, drops.size() == 1 && drops.get(0).is(ModRope.FILAMENT_BULB_ITEM.get()) && drops.get(0).get(DataComponents.BLOCK_STATE) == null,
                "off, it is mined as the plain bulb: " + drops);
        helper.getBlockState(pos).useWithoutItem(level, player, hit);
        check(helper, helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT)
                && helper.getBlockState(pos).getLightEmission() == FilamentBulbBlock.LIGHT_LEVEL, "and on again");
        player.setGameMode(GameType.ADVENTURE);
        check(helper, helper.getBlockState(pos).useWithoutItem(level, player, hit) == InteractionResult.PASS
                && helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT), "an adventurer cannot switch it");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Hung on a rope: sneaking with an empty hand switches it off where it hangs - its light gone at once - and on again,
     * lighting a second later as a filament warms; not sneaking, the hand takes it down, the plain bulb. Saved off, it
     * loads off; an adventurer cannot switch it.
     */
    private static void hung(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        level.addFreshEntity(bulb);
        helper.runAfterDelay(25, () -> {
            BlockPos lit = bulb.litAt();
            check(helper, lit != null && level.getBlockState(lit).getValue(LightBlock.LEVEL) == HungItemEntity.BULB_LIGHT, "hung, it lights");
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            player.setShiftKeyDown(true);
            check(helper, bulb.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.SUCCESS && !bulb.isRemoved()
                    && bulb.switchedOff() && bulb.glow() == 0 && bulb.litAt() == null && !level.getBlockState(lit).is(ModRope.HUNG_LIGHT.get()),
                    "sneaking, an empty hand switches it off where it hangs, its light gone");
            TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
            bulb.saveWithoutId(saved);
            HungItemEntity loaded = new HungItemEntity(ModRope.HUNG_ITEM.get(), level);
            loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
            check(helper, loaded.switchedOff() && loaded.glow() == 0, "saved off, it loads off");
            player.setGameMode(GameType.ADVENTURE);
            check(helper, bulb.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.FAIL && bulb.switchedOff(),
                    "an adventurer cannot switch it");
            player.setGameMode(GameType.SURVIVAL);
            bulb.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            check(helper, !bulb.switchedOff() && bulb.glow() == HungItemEntity.BULB_LIGHT, "switched on again");
            helper.runAfterDelay(5, () -> {
                check(helper, bulb.litAt() == null, "its light not yet: the filament warms");
                helper.runAfterDelay(20, () -> {
                    check(helper, bulb.litAt() != null && level.getBlockState(bulb.litAt()).is(ModRope.HUNG_LIGHT.get()),
                            "and lights a second later");
                    bulb.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO); // off again, then taken down
                    player.setShiftKeyDown(false);
                    check(helper, bulb.interact(player, InteractionHand.MAIN_HAND, Vec3.ZERO) == InteractionResult.SUCCESS && bulb.isRemoved()
                            && player.getInventory().countItem(ModRope.FILAMENT_BULB_ITEM.get()) == 1,
                            "not sneaking, the hand takes it down, the plain bulb, off or on");
                    level.getServer().getPlayerList().remove(player);
                    helper.succeed();
                });
            });
        });
    }
}
