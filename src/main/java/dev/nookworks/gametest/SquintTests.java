package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.feature.Squinting;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Squinting (the user, 2026-10-10): the server's part - a player's word that they squint marks them for whoever sees
 * them, and is gone with them - and the arithmetic the client's view and mouse run on. The view, the hand and the arm
 * raised are drawn by the client, checked in the agent's client.
 */
final class SquintTests {
    static void register() {
        test("squinting_is_marked_for_others_and_not_kept", SquintTests::markedAndNotKept);
        test("squinting_arithmetic", SquintTests::arithmetic);
    }

    /** Off to begin with; on and off at the player's word; not saved with the player (nor so carried through death). */
    private static void markedAndNotKept(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        check(helper, !Squinting.of(player), "nobody squints to begin with");
        Squinting.set(player, true);
        check(helper, Squinting.of(player), "the key down: squinting");
        Squinting.set(player, true);
        check(helper, Squinting.of(player), "the same word again changes nothing");
        Squinting.set(player, false);
        check(helper, !Squinting.of(player), "the key up: not");
        Squinting.set(player, true);
        var saved = net.minecraft.world.level.storage.TagValueOutput.createWithContext(net.minecraft.util.ProblemReporter.DISCARDING,
                helper.getLevel().registryAccess());
        player.saveWithoutId(saved);
        check(helper, !saved.buildResult().toString().contains("squinting"), "and not saved with the player");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /** The view divided by the zoom only all the way in; the mouse slowed by as much; the easing from 0 to 1. */
    private static void arithmetic(GameTestHelper helper) {
        check(helper, Squinting.fovDivisor(2.0, 0.0) == 1.0 && Squinting.fovDivisor(2.0, 1.0) == 2.0
                && Math.abs(Squinting.fovDivisor(3.0, 0.5) - 2.0) < 1e-9, "the field of view divided by 1 to the zoom");
        check(helper, Squinting.ease(0) == 0 && Squinting.ease(1) == 1 && Math.abs(Squinting.ease(0.5) - 0.5) < 1e-9
                && Squinting.ease(-1) == 0 && Squinting.ease(2) == 1 && Squinting.ease(0.25) < 0.25, "eased in and out");
        for (double sensitivity : new double[] {0.0, 0.5, 1.0}) {
            double turned = Math.pow(sensitivity * 0.6 + 0.2, 3);
            double slowed = Math.pow(Squinting.sensitivityFor(sensitivity, 0.5) * 0.6 + 0.2, 3);
            check(helper, Math.abs(slowed - turned * 0.5) < 1e-9, "the mouse at half speed for " + sensitivity + ": " + slowed / turned);
        }
        helper.succeed();
    }
}
