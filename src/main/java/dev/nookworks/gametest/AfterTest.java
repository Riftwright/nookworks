package dev.nookworks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;

/**
 * What a test changed outside its own arena - a player it put in the level, the world's clock - put back when it ends,
 * passed or failed. Left as it was, it carried into the tests after it, and their results turned on the order they ran.
 */
public final class AfterTest {
    private AfterTest() {}

    public static void undo(GameTestHelper helper, Runnable undo) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo testInfo) {}

            @Override
            public void testPassed(GameTestInfo testInfo, GameTestRunner runner) {
                undo.run();
            }

            @Override
            public void testFailed(GameTestInfo testInfo, GameTestRunner runner) {
                undo.run();
            }

            @Override
            public void testAddedForRerun(GameTestInfo original, GameTestInfo copy, GameTestRunner runner) {}
        });
    }
}
