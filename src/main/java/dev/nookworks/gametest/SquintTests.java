package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.feature.Squinting;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        test("squint_stands_aside_for_another_zoom_on_its_key", SquintTests::zoomClash);
        test("squint_stands_aside_only_for_held_things_named_for_it", SquintTests::heldThings);
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

    /**
     * Another mod's zoom on Squint's key (Zoomify's and Ok Zoomer's are on C as Squint is): Squint stands aside, so the
     * view is not brought nearer twice over (the user, 2026-11-14). Bound apart, both work; vanilla's Save Toolbar
     * Activator on C is no zoom; a key named for a zoom of ours is ours; Squint unbound has nothing to clash.
     */
    private static void zoomClash(GameTestHelper helper) {
        String c = "key.keyboard.c:NONE";
        String[] toolbar = {"key.saveToolbarActivator", c};
        String[] zoomifyOnC = {"zoomify.key.zoom", c};
        String[] zoomifyOnV = {"zoomify.key.zoom", "key.keyboard.v:NONE"};
        String[] okZoomerShiftC = {"key.ok_zoomer.zoom", "key.keyboard.c:SHIFT"};
        String[] oursNamedZoom = {"key.nookworks.zoom_test", c};
        check(helper, Squinting.zoomClash(c, List.of(toolbar, zoomifyOnC)) != null
                && Squinting.zoomClash(c, List.of(toolbar, zoomifyOnC)).equals("zoomify.key.zoom"), "a zoom on C beside Squint: it stands aside");
        check(helper, Squinting.zoomClash(c, List.of(toolbar, zoomifyOnV)) == null, "the zoom bound to V: both work");
        check(helper, Squinting.zoomClash(c, List.<String[]>of(toolbar)) == null, "vanilla's Save Toolbar Activator on C is no zoom");
        check(helper, Squinting.zoomClash(c, List.<String[]>of(okZoomerShiftC)) == null, "Shift+C is another key");
        check(helper, Squinting.zoomClash(c, List.<String[]>of(oursNamedZoom)) == null, "a key of ours is not another mod's");
        check(helper, Squinting.zoomClash("key.keyboard.unknown:NONE", List.<String[]>of(new String[] {"zoomify.key.zoom", "key.keyboard.unknown:NONE"})) == null,
                "Squint unbound clashes with nothing");
        helper.succeed();
    }

    /**
     * The tag for things that keep their holder from squinting is shipped, and empty: nothing ordinary, in either hand,
     * stops it, here or in the server's own count. (A thing named in it by a data pack - another mod's gun - was seen
     * in the agent's client: held, the key does nothing.)
     */
    private static void heldThings(GameTestHelper helper) {
        check(helper, helper.getLevel().getServer().getResourceManager()
                .getResource(Identifier.parse("nookworks:tags/item/no_squint.json")).isPresent(), "the tag #nookworks:no_squint is shipped");
        check(helper, !Squinting.heldKeepsFrom(ItemStack.EMPTY, ItemStack.EMPTY), "empty hands: nothing to stand aside for");
        check(helper, !Squinting.heldKeepsFrom(new ItemStack(Items.BOW), new ItemStack(Items.SHIELD)),
                "ordinary things in both hands: nothing to stand aside for");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        Squinting.set(player, true);
        check(helper, Squinting.of(player), "a bow in hand: the player's word is still taken");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }
}
