package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.client.gui.WorldFeaturesScreen;
import dev.nookworks.feature.WorldFeatures;
import dev.nookworks.recycling.Compressing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * The client's side of each world's choice of parts ({@link WorldFeatures}): the button to it on the Create World
 * screen's More tab, under vanilla's Game Rules, Experiments and Data Packs - unless the pack switched the choice off -
 * and forgetting a server's word on leaving it.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class WorldFeaturesClient {
    private WorldFeaturesClient() {}

    /** Adds the button to the More tab's column of buttons, {@code moreTab} ({@code CreateWorldMoreTabMixin}). */
    public static void addButton(GridLayout moreTab) {
        if (!WorldFeatures.menuShown()) {
            return;
        }
        int[] rows = {0};
        moreTab.visitChildren(child -> rows[0]++); // one button a row: ours goes under the last, another mod's too
        moreTab.addChild(Button.builder(Component.translatable("nookworks.world_features.button"), button -> open())
                .width(210).build(), rows[0], 0);
    }

    private static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof CreateWorldScreen parent) {
            minecraft.gui.setScreen(WorldFeaturesScreen.creating(parent));
        }
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        WorldFeatures.leftServer();
        Compressing.leftServer(); // what the server said presses (the Compressor's slots), too
    }
}
