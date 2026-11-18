package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.feature.ChainBreaking;
import dev.nookworks.port.test.GameTestHelper;

/**
 * What the tests of a feature that stands down beside another mod expect in a run with that mod loaded. Each such test
 * sets its scene up and does what it does in every run; with the feature switched on but standing down, it then checks
 * that ours had no part in what happened (whatever the tree came to is the other mod's doing, tried in
 * {@link StandDownTests}) instead of what ours would have done. Not skipped: a stand-down that let ours run alongside the
 * other mod fails here.
 */
final class StandDowns {
    private StandDowns() {}

    /** Whether tree felling is switched on and stands down now for another mod's. */
    static boolean felling() {
        return NookworksConfig.TIMBER_ENABLED.getAsBoolean() && !FeatureGate.timber();
    }

    /** Whether vein mining is switched on and stands down now for another mod's. */
    static boolean vein() {
        return NookworksConfig.VEIN_ENABLED.getAsBoolean() && !FeatureGate.vein();
    }

    /** The mod tree felling stands down for, or null. */
    static @Nullable String feller() {
        return FeatureGate.firstLoaded(FeatureGate.timberConflicts());
    }

    /** Our felling count now, to compare after the axe has been used. */
    static int fellings() {
        return ChainBreaking.fellings();
    }

    /** Ours felled nothing since {@code before} in {@code scene}: the test passes so. */
    static void oursFelledNothing(GameTestHelper helper, int before, String scene) {
        check(helper, ChainBreaking.fellings() == before, "beside " + feller() + " our tree felling stands down, but it felled: " + scene);
        helper.succeed();
    }
}
