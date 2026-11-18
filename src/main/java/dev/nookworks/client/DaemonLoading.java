package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.client.render.DaemonGates;
import dev.nookworks.travel.DaemonTravel;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
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

    /**
     * PORT: 1.21.1's screen between dimensions is the ReceivingLevelScreen (26.3: LevelLoadingScreen), which draws its
     * background without posting an event for it: on a trip the screen is drawn here instead - the gate, then the
     * screen's one line, where and as it writes it.
     */
    @SubscribeEvent
    static void onBackground(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof ReceivingLevelScreen screen) {
            DaemonTravel.trippingBy().ifPresent(gate -> {
                GuiGraphicsExtractor g = GuiGraphicsExtractor.of(event.getGuiGraphics());
                DaemonGates.fill(g, DaemonGates.of(gate), screen.width,
                    screen.height, (System.currentTimeMillis() % 3_600_000L) / 50.0F); // (the whole clock as a float moves
                    // in steps of minutes)
                g.centeredText(screen.getMinecraft().font, DOWNLOADING_TERRAIN, screen.width / 2, screen.height / 2 - 50, 0xFFFFFFFF);
                event.setCanceled(true);
            });
        }
    }

    private static final Component DOWNLOADING_TERRAIN = Component.translatable("multiplayer.downloadingTerrain");

    /** A trip cut short by leaving the world is over: the next world's loading screen is not the gate's (r15). */
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DaemonTravel.arrivedThere();
    }

    @SubscribeEvent
    static void onClosing(ScreenEvent.Closing event) {
        if (event.getScreen() instanceof ReceivingLevelScreen) {
            DaemonTravel.arrivedThere();
        }
    }
}
