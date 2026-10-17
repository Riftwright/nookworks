package dev.nookworks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Optional;

import dev.nookworks.client.render.AnimationTime;
import dev.nookworks.client.render.DaemonView;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonPanelMenu;
import dev.nookworks.travel.TravelLog;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.Level;

/**
 * The Destination Panel's screen: first a row for Off - the door a door like any other - then a row for each dimension
 * the player has been to, a little window onto it and its name; the one chosen lit in brass. Under them, a display
 * saying where the door opens onto now. A click on a row chooses it. No inventory.
 */
public class DestinationPanelScreen extends AbstractContainerScreen<DaemonPanelMenu> {
    private static final int LIST_X = 9;
    private static final int LIST_Y = 20;
    private static final int LIST_W = 158;
    private static final int ROWS = 5;
    private static final int ROW_H = 20;
    private static final int BAR_W = 4;
    private static final int STATUS_Y = LIST_Y + ROWS * ROW_H + 8;
    /** The status box: what the door does now on a line, then what to do, over two lines (one cut it short in every language). */
    private static final int STATUS_H = 31;
    /** The window onto each dimension: 16 of the view's columns, every other row of it. */
    private static final int WINDOW = 16;
    private int scroll;

    public DestinationPanelScreen(DaemonPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, STATUS_Y + STATUS_H + 8);
        this.titleLabelY = 6;
    }

    private Optional<ResourceKey<Level>> chosen() {
        return this.minecraft.player == null ? Optional.empty() : TravelLog.of(this.minecraft.player).destination();
    }

    /** The rows: Off, and the places. */
    private int rows() {
        return this.menu.choices().size() + 1;
    }

    private int maxScroll() {
        return Math.max(0, this.rows() - ROWS);
    }

    /** The row under the mouse - 0 for Off, {@code i + 1} for the {@code i}th place, as the menu's buttons are - or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos - LIST_X;
        double y = mouseY - this.topPos - LIST_Y;
        int scrollbar = this.maxScroll() > 0 ? BAR_W + 1 : 0;
        if (x < 0 || x >= LIST_W - scrollbar || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = this.scroll + (int) y / ROW_H;
        return index < this.rows() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int index = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? this.rowAt(event.x(), event.y()) : -1;
        if (index >= 0) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, index);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.isHovering(LIST_X, LIST_Y, LIST_W, ROWS * ROW_H, x, y)) {
            this.scroll = Math.max(0, Math.min(this.scroll - (int) Math.signum(scrollY), this.maxScroll()));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        MachineStyle.panel(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        MachineStyle.bay(g, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, STATUS_Y - 20);
        int x = this.leftPos + LIST_X;
        int y = this.topPos + LIST_Y;
        MachineStyle.well(g, x - 1, y - 1, LIST_W + 2, ROWS * ROW_H + 2);
        List<ResourceKey<Level>> choices = this.menu.choices();
        if (choices.isEmpty()) {
            List<FormattedCharSequence> lines = this.font.split(Component.translatable("gui.nookworks.destination_panel.none"), LIST_W - 10);
            for (int i = 0; i < lines.size() && i < ROWS * 2 - 2; i++) {
                g.text(this.font, lines.get(i), x + 5, y + ROW_H + 6 + i * 10, MachineStyle.DIM, false);
            }
        }
        float time = AnimationTime.cycle(this.minecraft.level, a, AnimationTime.VIEW_CYCLE);
        Optional<ResourceKey<Level>> chosen = this.chosen();
        int hovered = this.rowAt(mouseX, mouseY);
        int rowRight = x + LIST_W - (this.maxScroll() > 0 ? BAR_W + 1 : 0);
        for (int row = 0; row < ROWS && this.scroll + row < this.rows(); row++) {
            int index = this.scroll + row;
            ResourceKey<Level> key = index == 0 ? null : choices.get(index - 1);
            int ry = y + row * ROW_H;
            boolean on = key == null ? chosen.isEmpty() : chosen.filter(key::equals).isPresent();
            if (on) {
                g.fill(x, ry, rowRight, ry + ROW_H, 0xFF3A3220); // lit in brass: the brass rim's own dark, warmed
                g.fill(x, ry, x + 1, ry + ROW_H, 0xFFE2C27A);
            } else if (index == hovered) {
                g.fill(x, ry, rowRight, ry + ROW_H, 0xFF2C3037);
            }
            if (key == null) {
                this.shutDoor(g, x + 4, ry + 2);
            } else {
                this.window(g, x + 4, ry + 2, DaemonDestinations.look(key, this.minecraft.getConnection().registryAccess()), time);
            }
            Component name = key == null ? Component.translatable("gui.nookworks.destination_panel.off") : DaemonDestinations.name(key);
            MachineStyle.fitText(g, this.font, name, x + WINDOW + 10, ry + 6, rowRight - x - WINDOW - 14,
                    on ? MachineStyle.TITLE : MachineStyle.TEXT, false);
        }
        if (this.maxScroll() > 0) {
            int barX = x + LIST_W - BAR_W;
            int h = ROWS * ROW_H;
            g.fill(barX, y, barX + BAR_W, y + h, 0xFF23262B);
            int thumb = Math.max(8, h * ROWS / this.rows());
            int top = y + (h - thumb) * this.scroll / this.maxScroll();
            // raised, as a thumb is: lit along its top and left, shadowed along its bottom and right
            g.fill(barX, top, barX + BAR_W, top + thumb, 0xFF3C4047);
            g.fill(barX, top, barX + BAR_W - 1, top + thumb - 1, 0xFF9DA4AE);
            g.fill(barX + 1, top + 1, barX + BAR_W - 1, top + thumb - 1, 0xFF6A717B);
        }
        MachineStyle.display(g, this.leftPos + 7, this.topPos + STATUS_Y, this.imageWidth - 14, STATUS_H,
                chosen.isPresent() ? MachineStatus.Tone.GOOD : MachineStatus.Tone.QUIET);
    }

    /** Off's picture, in the window's place: the door itself, shut - Dawnwood, its glass slits dark, its zinc pull. */
    private void shutDoor(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x + 1, y, x + WINDOW, y + 2, 0xFF8C885B); // the rail
        g.fill(x + 3, y + 2, x + 13, y + WINDOW, 0xFF3A3620);
        g.fill(x + 4, y + 2, x + 12, y + WINDOW - 1, 0xFF605C3B);
        for (int sx : new int[] {7, 10}) {
            g.fill(x + sx, y + 3, x + sx + 1, y + WINDOW - 2, 0xFF1E262C);
        }
        g.fill(x + 5, y + 6, x + 6, y + 11, 0xFFC0C5C8);
    }

    /** A window onto a dimension, 16 by 16 in a ring of brass: a picture of the place, every other row of it. */
    private void window(GuiGraphicsExtractor g, int x, int y, DaemonDestinations.Look look, float time) {
        g.fill(x - 1, y - 1, x + WINDOW + 1, y + WINDOW + 1, 0xFF6E5424);
        g.fill(x - 1, y - 1, x + WINDOW, y + WINDOW, 0xFFE2C27A);
        for (int py = 0; py < WINDOW; py++) {
            for (int px = 0; px < WINDOW; px++) {
                g.fill(x + px, y + py, x + px + 1, y + py + 1, DaemonView.pixel(look, px, py * 2, time));
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        MachineStyle.fitText(g, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 16, MachineStyle.TITLE, true);
        String k = "gui.nookworks.destination_panel.";
        Optional<ResourceKey<Level>> chosen = this.chosen();
        Component status = chosen.map(key -> Component.translatable(k + "opens_onto", DaemonDestinations.name(key)))
                .orElse(Component.translatable(k + "nowhere"));
        Component detail = Component.translatable(k + (chosen.isPresent() ? "walk_through" : "pick"));
        int width = this.imageWidth - 28;
        MachineStyle.fitText(g, this.font, status, 18, STATUS_Y + 3, width,
                MachineStyle.toneColour(chosen.isPresent() ? MachineStatus.Tone.GOOD : MachineStatus.Tone.QUIET), false);
        List<FormattedCharSequence> lines = this.font.split(detail, width);
        for (int i = 0; i < Math.min(2, lines.size()); i++) {
            g.text(this.font, lines.get(i), 18, STATUS_Y + 12 + 9 * i, MachineStyle.DIM, false);
        }
    }

    /** Over a row, its dimension's id: two mods may name theirs alike. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int index = this.rowAt(mouseX, mouseY);
        if (index > 0) {
            ResourceKey<Level> key = this.menu.choices().get(index - 1);
            g.setTooltipForNextFrame(this.font, List.of(DaemonDestinations.name(key),
                    Component.literal(key.identifier().toString()).withStyle(ChatFormatting.DARK_GRAY)), Optional.empty(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }
}
