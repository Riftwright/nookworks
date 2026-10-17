package dev.nookworks.client.gui;

import java.util.List;
import java.util.Optional;
import java.util.function.IntSupplier;

import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.menu.NestedTimerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Nested Timer GUI: three sliders - how long until the next round, how many pulses in it, and how far apart -
 * each with its value beside its name, in the bay; under it a status display with a lamp that lights for every pulse
 * that goes out. With one pulse the gap means nothing, and its slider stands idle. No inventory.
 */
public class NestedTimerScreen extends AbstractContainerScreen<NestedTimerMenu> {
    private static final int ROW_Y = 22;
    private static final int STATUS_Y = ROW_Y + 3 * 30 + 2;
    private boolean statusCut;
    private static final int ROW_H = 30;
    private static final String K = "gui.nookworks.nested_timer.";

    private StepSlider interval;
    private StepSlider pulses;
    private StepSlider gap;

    public NestedTimerScreen(NestedTimerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, STATUS_Y + 20);
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.interval = this.slider(0, NestedTimerBlockEntity.INTERVALS.length, "interval", this.menu::intervalStep);
        this.pulses = this.slider(1, NestedTimerBlockEntity.MAX_PULSES, "pulses", () -> this.menu.pulses() - 1);
        this.gap = this.slider(2, NestedTimerBlockEntity.GAPS.length, "gap", this.menu::gapStep);
    }

    private StepSlider slider(int row, int steps, String name, IntSupplier current) {
        return this.addRenderableWidget(new StepSlider(this.leftPos + 8, this.topPos + ROW_Y + row * ROW_H + 11, this.imageWidth - 16,
                steps, Component.translatable(K + name), current,
                step -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, row * NestedTimerMenu.PER_SETTING + step)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, STATUS_Y - 3 - 16);
        for (int row = 1; row < 3; row++) {
            MachineStyle.divider(graphics, this.leftPos + 8, this.topPos + ROW_Y + row * ROW_H - 5, this.imageWidth - 16);
        }
        this.gap.setActive(this.pulses.shown() > 0);
        MachineStyle.display(graphics, this.leftPos + 7, this.topPos + STATUS_Y, this.imageWidth - 14, 12, this.tone());
        // The output lamp at the display's right end: lit while a pulse goes out.
        int lx = this.leftPos + this.imageWidth - 16;
        int ly = this.topPos + STATUS_Y + 3;
        graphics.fill(lx, ly, lx + 6, ly + 6, 0xFF121418);
        graphics.fill(lx + 1, ly + 1, lx + 5, ly + 5, this.menu.output() ? 0xFFFF4A32 : 0xFF3A1410);
        if (this.menu.output()) {
            graphics.fill(lx + 1, ly + 1, lx + 3, ly + 3, 0xFFFFB0A0);
        }
    }

    private boolean overflow() {
        int pulseCount = this.pulses.shown() + 1;
        return pulseCount > 1 && (pulseCount - 1) * NestedTimerBlockEntity.GAPS[this.gap.shown()]
                >= NestedTimerBlockEntity.INTERVALS[this.interval.shown()];
    }

    private MachineStatus.Tone tone() {
        return this.overflow() ? MachineStatus.Tone.WARN : this.menu.held() ? MachineStatus.Tone.QUIET : MachineStatus.Tone.GOOD;
    }

    private Component status() {
        return this.overflow() ? Component.translatable(K + "status.overflow")
                : Component.translatable(K + (this.menu.held() ? "status.held" : "status.running"));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 16, MachineStyle.TITLE, true);
        int intervalTicks = NestedTimerBlockEntity.INTERVALS[this.interval.shown()];
        int pulseCount = this.pulses.shown() + 1;
        int gapTicks = NestedTimerBlockEntity.GAPS[this.gap.shown()];
        this.row(graphics, 0, Component.translatable(K + "interval"), time(intervalTicks));
        this.row(graphics, 1, Component.translatable(K + "pulses"), Component.translatable(K + "times", pulseCount));
        boolean single = pulseCount <= 1;
        this.row(graphics, 2, Component.translatable(K + "gap"), single ? Component.translatable(K + "gap.unused") : time(gapTicks));
        this.statusCut = MachineStyle.fitText(graphics, this.font, this.status(), 18, STATUS_Y + 2, this.imageWidth - 40,
                MachineStyle.toneColour(this.tone()), false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.isHovering(this.imageWidth - 17, STATUS_Y, 10, 12, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(Component.translatable(K + "output")), Optional.empty(),
                    mouseX, mouseY);
            return;
        }
        if (this.statusCut && this.isHovering(7, STATUS_Y, this.imageWidth - 14, 12, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(this.status()), Optional.empty(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    private void row(GuiGraphicsExtractor graphics, int row, Component name, Component value) {
        int y = ROW_Y + row * ROW_H;
        graphics.text(this.font, name, 8, y, MachineStyle.TEXT, false);
        graphics.text(this.font, value, this.imageWidth - 8 - this.font.width(value), y, MachineStyle.TITLE, false);
    }

    private static Component time(int ticks) {
        return NestedTimerBlockEntity.time(ticks);
    }
}
