package dev.nookworks.client.gui;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.WorldFeatures;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Mods > Nookworks > Config: NeoForge's settings screen for the mod's configs, and, in a world, the world's choice of
 * parts (the user, 2026-10-31) - asked of the server, which says whether this player may change it.
 */
public final class NookworksSettingsScreen extends Screen {
    private static final int BUTTON_WIDTH = 220;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final ModContainer mod;
    private final Screen parent;

    public NookworksSettingsScreen(ModContainer mod, Screen parent) {
        super(Component.translatable("nookworks.settings.title"));
        this.mod = mod;
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);
        LinearLayout column = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        column.addChild(Button.builder(Component.translatable("nookworks.settings.configs"),
                b -> this.minecraft.gui.setScreen(new ConfigurationScreen(this.mod, this))).width(BUTTON_WIDTH).build());
        Button parts = column.addChild(Button.builder(Component.translatable("nookworks.settings.world_parts"),
                b -> ClientPacketDistributor.sendToServer(new NookworksNetwork.AskWorldParts())).width(BUTTON_WIDTH).build());
        boolean inWorld = this.minecraft.getConnection() != null && this.minecraft.getConnection().hasChannel(NookworksNetwork.AskWorldParts.TYPE);
        // in a world, the server's config says whether its worlds choose (its screen tells): not this game's own (r19)
        parts.active = inWorld;
        parts.setTooltip(Tooltip.create(Component.translatable(inWorld ? "nookworks.settings.world_parts.tooltip"
                : !WorldFeatures.menuShown() ? "nookworks.settings.world_parts.off" : "nookworks.settings.world_parts.not_in_world")));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).width(200).build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
