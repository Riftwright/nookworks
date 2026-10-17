package dev.nookworks.client.gui;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import dev.nookworks.machine.MachineStatus;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Spawner Controller GUI: one row per setting in the bay - its name on the left, its value lined up against the
 * buttons, so no language runs one into the other - the energy bar under it, and a two-line status display: how many
 * spawners it holds and in what state, and what that costs or why it is not running. No inventory.
 */
public class SpawnerControllerScreen extends AbstractContainerScreen<SpawnerControllerMenu> {
    private static final int ROW_Y = 24;
    private static final int ROW_H = 20;
    private static final int BAY_BOTTOM = ROW_Y + 6 * ROW_H - 4;
    private static final int BAR_Y = BAY_BOTTOM + 5;
    private static final int STATUS_Y = BAR_Y + 13;
    private static final int STATUS_H = 22;
    /** Where the buttons start; the values end a little short of it. */
    private static final int BUTTONS_X = 124;
    private static final int VALUE_RIGHT = BUTTONS_X - 5;
    private boolean statusCut;

    public SpawnerControllerScreen(SpawnerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, STATUS_Y + STATUS_H + 8);
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.toggle(0, SpawnerControllerMenu.TOGGLE_ENABLED);
        this.stepper(1, SpawnerControllerMenu.INTERVAL_DOWN, SpawnerControllerMenu.INTERVAL_UP);
        this.stepper(2, SpawnerControllerMenu.COUNT_DOWN, SpawnerControllerMenu.COUNT_UP);
        this.stepper(3, SpawnerControllerMenu.NEARBY_DOWN, SpawnerControllerMenu.NEARBY_UP);
        this.toggle(4, SpawnerControllerMenu.TOGGLE_LIGHT);
        this.toggle(5, SpawnerControllerMenu.TOGGLE_PLAYER);
        this.addRenderableWidget(new RedstoneButton(this.leftPos + this.imageWidth - 8 - RedstoneButton.SIZE, this.topPos + 3,
                this.menu::redstoneMode, () -> this.press(SpawnerControllerMenu.BUTTON_REDSTONE)));
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    private void toggle(int row, int id) {
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X, this.topPos + ROW_Y + row * ROW_H - 4, 44, 16,
                Component.translatable("gui.nookworks.spawner_controller.change"), () -> this.press(id)));
    }

    private void stepper(int row, int down, int up) {
        int y = this.topPos + ROW_Y + row * ROW_H - 4;
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X, y, 20, 16, Component.literal("-"), () -> this.press(down)));
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X + 24, y, 20, 16, Component.literal("+"), () -> this.press(up)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, BAY_BOTTOM - 16);
        for (int row = 1; row < 6; row++) {
            MachineStyle.divider(graphics, this.leftPos + 8, this.topPos + ROW_Y + row * ROW_H - 7, this.imageWidth - 16);
        }
        int energy = this.menu.energy();
        int cost = this.menu.costPerTick() * SpawnerControllerBlockEntity.PERIOD;
        MachineStatus.Tone tone = this.menu.linked() > 0 && !this.menu.powered() ? MachineStatus.Tone.BAD
                : cost > 0 && energy < cost * 20 ? MachineStatus.Tone.WARN : MachineStatus.Tone.GOOD; // under 10 s left
        MachineStyle.icon(graphics, MachineStyle.Icon.ENERGY, this.leftPos + 8, this.topPos + BAR_Y, energy <= 0);
        MachineStyle.energyBar(graphics, this.leftPos + 19, this.topPos + BAR_Y, this.imageWidth - 27, 8, energy,
                SpawnerLinkReceiverBlockEntity.capacity(), tone);
        MachineStyle.display(graphics, this.leftPos + 7, this.topPos + STATUS_Y, this.imageWidth - 14, STATUS_H, this.statusTone());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 30, MachineStyle.TITLE, true);
        String k = "gui.nookworks.spawner_controller.";
        this.row(graphics, 0, k + "state", Component.translatable(k + (this.menu.enabled() ? "on" : "off")));
        this.row(graphics, 1, k + "interval", Component.translatable(k + "seconds", this.menu.intervalSeconds()));
        this.row(graphics, 2, k + "count", Component.literal(String.valueOf(this.menu.count())));
        this.row(graphics, 3, k + "nearby", Component.literal(String.valueOf(this.menu.maxNearby())));
        this.row(graphics, 4, k + "light", Component.translatable(k + (this.menu.anyLight() ? "light.any" : "light.normal")));
        this.row(graphics, 5, k + "player", Component.translatable(k + (this.menu.needsPlayer() ? "player.needed" : "player.not_needed")));
        int width = this.imageWidth - 28;
        boolean first = MachineStyle.fitText(graphics, this.font, this.status(), 18, STATUS_Y + 3, width,
                MachineStyle.toneColour(this.statusTone()), false);
        boolean second = MachineStyle.fitText(graphics, this.font, this.detail(), 18, STATUS_Y + 12, width, MachineStyle.DIM, false);
        this.statusCut = first || second;
    }

    /** A setting: its name, and its value set right, just short of its buttons (the name gives way if both are long). */
    private void row(GuiGraphicsExtractor graphics, int row, String nameKey, Component value) {
        int y = ROW_Y + row * ROW_H;
        int valueX = VALUE_RIGHT - this.font.width(value);
        graphics.text(this.font, value, valueX, y, MachineStyle.TEXT, false);
        MachineStyle.fitText(graphics, this.font, Component.translatable(nameKey), 8, y, valueX - 12, MachineStyle.DIM, false);
    }

    private Component status() {
        String k = "gui.nookworks.spawner_controller.";
        int n = this.menu.linked();
        if (n == 0) {
            return Component.translatable(k + "status.none");
        }
        String state = !this.menu.powered() ? "no_power" : this.menu.heldByRedstone() ? "redstone" : !this.menu.enabled() ? "off" : "active";
        return Component.translatable(k + "status." + state + (n == 1 ? ".one" : ""), n);
    }

    /** The status's second line: what running costs, or what to do about not running. */
    private Component detail() {
        String k = "gui.nookworks.spawner_controller.";
        if (this.menu.linked() == 0) {
            return Component.translatable(k + "none_hint");
        }
        if (!this.menu.powered()) {
            return Component.translatable(k + "vanilla");
        }
        if (this.menu.heldByRedstone()) {
            return Component.translatable(k + "held");
        }
        if (!this.menu.enabled()) {
            return Component.translatable(k + "off_hint");
        }
        return Component.translatable(k + "using", String.format(Locale.ROOT, "%,d", this.menu.costPerTick()));
    }

    private MachineStatus.Tone statusTone() {
        // switched off, as held by redstone: quiet, not the green of spawners at work
        return this.menu.linked() == 0 || (this.menu.heldByRedstone() || !this.menu.enabled()) && this.menu.powered() ? MachineStatus.Tone.QUIET
                : !this.menu.powered() ? MachineStatus.Tone.BAD : MachineStatus.Tone.GOOD;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.statusCut && this.isHovering(7, STATUS_Y, this.imageWidth - 14, STATUS_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(this.status(), this.detail()), Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.isHovering(8, BAR_Y, this.imageWidth - 16, 8, mouseX, mouseY)) {
            int perSpawner = this.menu.settings().energyPerTick();
            graphics.setTooltipForNextFrame(this.font, List.of(
                    Component.translatable("gui.nookworks.machine.energy", String.format(Locale.ROOT, "%,d", this.menu.energy()),
                            String.format(Locale.ROOT, "%,d", SpawnerLinkReceiverBlockEntity.capacity())),
                    Component.translatable("gui.nookworks.spawner_controller.cost", String.format(Locale.ROOT, "%,d", perSpawner))
                            .withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
