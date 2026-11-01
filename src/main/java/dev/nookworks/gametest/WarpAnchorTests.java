package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.anchor.WarpAnchorBlock;
import dev.nookworks.anchor.WarpAnchors;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * The Warp Anchor (the user, 2026-10-31): while on, nothing warps into or out of the 9x5x9 round it - endermen and
 * shulkers, ender pearls, Lode Pearls, chorus fruit - and Elevators, the Dock-a-Daemon Door and Item Warps (which post
 * the plain teleport event) go on; off, it stops nothing.
 */
final class WarpAnchorTests {
    private WarpAnchorTests() {}

    static void register() {
        test("a_warp_anchor_holds_nine_by_five_by_nine_round_it", WarpAnchorTests::holdsItsArea);
        test("a_warp_anchor_stops_endermen_pearls_and_chorus_fruit_not_elevators", WarpAnchorTests::stopsTheWarpsItShould);
    }

    /** Four blocks out each way across and two up and down are held, the fifth and the third not; switched off, none. */
    private static void holdsItsArea(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(5, 3, 5);
        helper.setBlock(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
        helper.runAfterDelay(2, () -> {
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(anchor);
            check(helper, WarpAnchors.holds(level, Vec3.atCenterOf(at)), "it holds its own place");
            check(helper, WarpAnchors.holds(level, Vec3.atCenterOf(at.offset(4, 2, -4))), "four across and two up are held");
            check(helper, WarpAnchors.holds(level, Vec3.atCenterOf(at.offset(-4, -2, 4))), "four across and two down are held");
            check(helper, !WarpAnchors.holds(level, Vec3.atCenterOf(at.offset(5, 0, 0))), "the fifth across is not");
            check(helper, !WarpAnchors.holds(level, Vec3.atCenterOf(at.offset(0, 3, 0))), "the third up is not");
            helper.setBlock(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState().setValue(WarpAnchorBlock.ON, false));
            check(helper, !WarpAnchors.holds(level, Vec3.atCenterOf(at)), "switched off, it holds nothing");
            helper.setBlock(anchor, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            check(helper, !WarpAnchors.holds(level, Vec3.atCenterOf(at)), "taken away, nothing");
            helper.succeed();
        });
    }

    /**
     * An enderman's teleport into the hold and one out of it are stopped and one far from it is not; an ender pearl's and
     * chorus fruit's the same; the plain teleport event (an Elevator's, the Dock-a-Daemon Door's) is left alone.
     */
    private static void stopsTheWarpsItShould(GameTestHelper helper) {
        BlockPos anchor = new BlockPos(5, 3, 5);
        helper.setBlock(anchor, ModBlocks.WARP_ANCHOR.get().defaultBlockState());
        Enderman inside = helper.spawn(EntityTypes.ENDERMAN, new BlockPos(6, 2, 6));
        Enderman outside = helper.spawn(EntityTypes.ENDERMAN, new BlockPos(13, 2, 13));
        inside.setNoAi(true);
        outside.setNoAi(true);
        helper.runAfterDelay(2, () -> {
            Vec3 in = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
            Vec3 far = helper.absoluteVec(new Vec3(14.5, 2, 14.5));
            check(helper, EventHooks.onEnderTeleport(inside, far.x, far.y, far.z).isCanceled(), "an enderman cannot teleport out of the hold");
            check(helper, EventHooks.onEnderTeleport(outside, in.x, in.y, in.z).isCanceled(), "nor into it");
            check(helper, !EventHooks.onEnderTeleport(outside, far.x, far.y, far.z + 1).isCanceled(), "but may far from it");
            // (a mob eats in the player's place: a test's stand-in player has no connection to be moved by)
            var chorus = new EntityTeleportEvent.ItemConsumption(outside, new ItemStack(Items.CHORUS_FRUIT), in.x, in.y, in.z);
            check(helper, NeoForge.EVENT_BUS.post(chorus).isCanceled(), "chorus fruit does not take one into the hold");
            check(helper, WarpAnchors.stops(outside, helper.getLevel(), in), "nor does a Lode Pearl (ThrownLodePearl asks the same)");
            var plain = new EntityTeleportEvent(outside, helper.getLevel(), in.x, in.y, in.z);
            check(helper, !NeoForge.EVENT_BUS.post(plain).isCanceled(), "an Elevator's or the door's plain teleport goes on");
            helper.succeed();
        });
    }
}
