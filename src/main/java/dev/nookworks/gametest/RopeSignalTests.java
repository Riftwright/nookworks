package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.FilamentBulbBlock;
import dev.nookworks.rope.HookBlock;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Redstone at a post switches the lights on the lines tied at it (the user, 2026-10-16: "柱で受けるのは天才だな",
 * "フックでかかってるロープも対応できる？"): bulbs on and lanterns lit as a signal comes, off and out as it goes; between, a
 * hand switches them. A Hook takes the signal of its wall too. A Filament Bulb set down switches the same way.
 */
final class RopeSignalTests {
    private RopeSignalTests() {}

    static void register() {
        NookworksGameTests.test("a_signal_at_a_post_switches_the_lights_on_its_lines", 120, RopeSignalTests::post);
        NookworksGameTests.test("a_hook_takes_the_signal_of_its_wall", 80, RopeSignalTests::hook);
        NookworksGameTests.test("a_filament_bulb_set_down_switches_by_redstone", RopeSignalTests::placed);
    }

    /**
     * A redstone block set by one post: the bulb on its line goes on, the lantern put out is lit and the unlit soul
     * lantern too; the bottle is let be. Taken away: off and out. A hand switching the bulb between keeps it so - no
     * signal changed. The knot keeps the signal it saw through a save.
     */
    private static void post(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos[] posts = RopeTests.posts(helper, 2, 5);
        BlockPos a = helper.absolutePos(posts[0]);
        BlockPos b = helper.absolutePos(posts[1]);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.2, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        HungItemEntity lantern = HungItemEntity.onSpan(level, a, b, 0.4, new ItemStack(Items.LANTERN));
        HungItemEntity soul = HungItemEntity.onSpan(level, a, b, 0.6, new ItemStack(ModItems.UNLIT_SOUL_LANTERN.get()));
        HungItemEntity bottle = HungItemEntity.onSpan(level, a, b, 0.8, new ItemStack(Items.GLASS_BOTTLE));
        for (HungItemEntity hung : new HungItemEntity[] {bulb, lantern, soul, bottle}) {
            level.addFreshEntity(hung);
        }
        helper.runAfterDelay(8, () -> {
            bulb.switchLight(level, false);
            lantern.switchLight(level, false);
            check(helper, bulb.switchedOff() && lantern.item().is(ModItems.UNLIT_LANTERN.get()), "the bulb off and the lantern out, to start");
            helper.setBlock(posts[0].north(), Blocks.REDSTONE_BLOCK);
            helper.runAfterDelay(6, () -> {
                check(helper, RopeKnotEntity.at(level, a).signalled(), "the knot sees its post's signal");
                check(helper, !bulb.switchedOff() && lantern.item().is(Items.LANTERN) && soul.item().is(Items.SOUL_LANTERN)
                        && bottle.item().is(Items.GLASS_BOTTLE), "as the signal comes, the bulb goes on and both lanterns are lit");
                check(helper, lantern.litAt() != null, "the lantern's light at once");
                TagValueOutput saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                RopeKnotEntity.at(level, a).saveWithoutId(saved);
                RopeKnotEntity loaded = new RopeKnotEntity(ModRope.ROPE_KNOT.get(), level);
                loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), saved.buildResult()));
                check(helper, loaded.signalled(), "saved and loaded, the knot keeps the signal it saw: nothing switched again");
                helper.setBlock(posts[0].north(), Blocks.AIR);
                helper.runAfterDelay(6, () -> {
                    check(helper, bulb.switchedOff() && bulb.litAt() == null && lantern.item().is(ModItems.UNLIT_LANTERN.get())
                            && soul.item().is(ModItems.UNLIT_SOUL_LANTERN.get()) && lantern.litAt() == null,
                            "as it goes, the bulb goes off and the lanterns out, their light with them");
                    bulb.switchLight(level, true); // by hand, between signals
                    helper.runAfterDelay(10, () -> {
                        check(helper, !bulb.switchedOff(), "switched on by hand with no signal changing, it stays on");
                        helper.succeed();
                    });
                });
            });
        });
    }

    /** A Hook on a wall, a redstone block behind the wall: the wall is powered, and the bulb on the hook's line goes on. */
    private static void hook(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = RopeTests.player(helper, 20);
        BlockPos wall = new BlockPos(0, 3, 3);
        BlockPos hook = wall.east();
        helper.setBlock(wall, Blocks.STONE);
        helper.setBlock(hook, ModRope.HOOK.get().defaultBlockState().setValue(HookBlock.FACE, AttachFace.WALL)
                .setValue(HookBlock.FACING, Direction.EAST));
        BlockPos fence = new BlockPos(5, 3, 3);
        helper.setBlock(fence, Blocks.OAK_FENCE);
        BlockPos a = helper.absolutePos(hook);
        BlockPos b = helper.absolutePos(fence);
        RopeSpans.tie(level, player, a, player.getMainHandItem());
        RopeSpans.tie(level, player, b, player.getMainHandItem());
        level.getServer().getPlayerList().remove(player);
        check(helper, RopeKnotEntity.at(level, a) != null && RopeKnotEntity.at(level, a).ties(b), "a line from the hook to the post");
        HungItemEntity bulb = HungItemEntity.onSpan(level, a, b, 0.5, new ItemStack(ModRope.FILAMENT_BULB_ITEM.get()));
        level.addFreshEntity(bulb);
        // the wall powered only once the knot has looked at its post's signal, unpowered: in a full run, under load, it
        // could first look after the wall was powered, take that as how it stands, and see no change (failed now and then)
        afterTicks(helper, RopeKnotEntity.at(level, a), 8, () -> {
            bulb.switchLight(level, false);
            helper.setBlock(wall.west(), Blocks.REDSTONE_BLOCK); // behind the wall: the hook's own neighbours have no signal
            check(helper, !level.hasNeighborSignal(a), "nothing by the hook itself");
            helper.succeedWhen(() -> check(helper, !bulb.switchedOff(), "its wall powered, the hook's line goes on"));
        });
    }

    /** {@code then}, once {@code entity} has ticked {@code ticks} times: one added to a world under load may start late. */
    private static void afterTicks(GameTestHelper helper, Entity entity, int ticks, Runnable then) {
        if (entity.tickCount >= ticks) {
            then.run();
        } else {
            helper.runAfterDelay(1, () -> afterTicks(helper, entity, ticks, then));
        }
    }

    /**
     * Set down: a signal coming switches it on, going switches it off; a hand switches it between, the signal let be.
     */
    private static void placed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = new BlockPos(3, 3, 3);
        helper.setBlock(pos.above(), Blocks.STONE);
        helper.setBlock(pos, ModRope.FILAMENT_BULB.get());
        check(helper, helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT), "set down, on");
        ServerPlayer player = RopeTests.player(helper, 0);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos at = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false);
        helper.getBlockState(pos).useWithoutItem(level, player, hit);
        check(helper, !helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT), "off by hand");
        helper.setBlock(pos.north(), Blocks.REDSTONE_BLOCK);
        check(helper, helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT) && helper.getBlockState(pos).getValue(FilamentBulbBlock.POWERED)
                && helper.getBlockState(pos).getLightEmission() == FilamentBulbBlock.LIGHT_LEVEL, "a signal come: on");
        helper.getBlockState(pos).useWithoutItem(level, player, hit);
        check(helper, !helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT), "a hand switches it off with the signal still there");
        helper.setBlock(pos.north(), Blocks.AIR);
        check(helper, !helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT) && !helper.getBlockState(pos).getValue(FilamentBulbBlock.POWERED),
                "the signal gone: off");
        helper.getBlockState(pos).useWithoutItem(level, player, hit);
        check(helper, helper.getBlockState(pos).getValue(FilamentBulbBlock.LIT), "and a hand switches it on, no signal");
        level.getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
