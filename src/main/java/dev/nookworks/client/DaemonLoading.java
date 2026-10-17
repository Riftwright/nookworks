package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.client.render.DaemonGates;
import dev.nookworks.travel.DaemonTravel;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * While the far side of a Dock-a-Daemon Door loads, the screen is filled with the gate one stepped into - the Nether's
 * swirl, the End's stars, the If's mirror, another mod's portal - as vanilla fills it with its own portals', so the step
 * through runs on into the door's own gate.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class DaemonLoading {
    private DaemonLoading() {}

    @SubscribeEvent
    static void onBackground(ScreenEvent.Render.Background event) {
        if (event.getScreen() instanceof LevelLoadingScreen screen) {
            DaemonTravel.trippingBy().ifPresent(gate -> DaemonGates.fill(event.getGuiGraphics(), DaemonGates.of(gate), screen.width,
                    screen.height, (System.currentTimeMillis() % 3_600_000L) / 50.0F)); // (the whole clock as a float moves
                    // in steps of minutes)
        }
    }

    /** A trip cut short by leaving the world is over: the next world's loading screen is not the gate's (r15). */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DaemonTravel.arrivedThere();
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof LevelLoadingScreen) {
            DaemonTravel.arrivedThere();
        }
    }
}
