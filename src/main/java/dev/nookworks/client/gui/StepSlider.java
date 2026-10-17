package dev.nookworks.client.gui;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A slider over a fixed number of steps, in the machine screens' style: a groove with a notch at each step and a brass
 * knob that is dragged (or clicked to, or moved with the arrow keys) from notch to notch. It shows the server's
 * value, except while it is being dragged, when it shows where the mouse has put it; each new step is sent at once.
 */
public class StepSlider extends AbstractWidget {
    private static final int EDGE = 0xFF121418;
    private static final int GROOVE = 0xFF15171B;
    private static final int NOTCH = 0xFF5A616B;
    private static final int FILL = 0xFF7A5F2E;
    private static final int POINTER = 0xFFE2C27A;
    private static final int POINTER_DARK = 0xFFB08A3E;

    private final int steps;
    private final IntSupplier current;
    private final IntConsumer choose;
    private int dragged = -1;

    public StepSlider(int x, int y, int width, int steps, Component name, IntSupplier current, IntConsumer choose) {
        super(x, y, width, 12, name);
        this.steps = steps;
        this.current = current;
        this.choose = choose;
    }

    /** The step on show: where it is being dragged to, or the value the server has. */
    public int shown() {
        return this.dragged >= 0 ? this.dragged : Math.max(0, Math.min(this.steps - 1, this.current.getAsInt()));
    }

    private int xOf(int step) {
        return this.getX() + 3 + (int) Math.round((this.width - 7) * (double) step / Math.max(1, this.steps - 1));
    }

    private int stepAt(double mouseX) {
        double t = (mouseX - this.getX() - 3) / (this.width - 7);
        return (int) Math.max(0, Math.min(this.steps - 1, Math.round(t * (this.steps - 1))));
    }

    private void moveTo(int step) {
        if (step != this.shown()) {
            this.choose.accept(step);
        }
        this.dragged = step;
    }

    /** Whether the slider counts for anything now (the gap, with one pulse, does not): greyed out and still, if not. */
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.moveTo(this.stepAt(event.x()));
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        this.moveTo(this.stepAt(event.x()));
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        this.dragged = -1;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isLeft() || event.isRight()) {
            int step = Math.max(0, Math.min(this.steps - 1, this.shown() + (event.isLeft() ? -1 : 1)));
            this.moveTo(step);
            this.dragged = -1;
            return true;
        }
        return false;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = this.getX();
        int mid = this.getY() + 6;
        int right = x + this.width;
        g.fill(x + 1, mid - 2, right - 1, mid + 2, EDGE);
        g.fill(x + 2, mid - 1, right - 2, mid + 1, GROOVE);
        int at = this.xOf(this.shown());
        if (!this.active) { // nothing to set: the groove alone, empty, and no knob
            for (int s = 0; s < this.steps; s++) {
                g.fill(this.xOf(s), mid + 3, this.xOf(s) + 1, mid + 5, EDGE);
            }
            return;
        }
        g.fill(x + 2, mid - 1, at, mid + 1, FILL); // the groove filled up to the pointer
        for (int s = 0; s < this.steps; s++) {
            int nx = this.xOf(s);
            g.fill(nx, mid + 3, nx + 1, mid + 5, NOTCH);
        }
        g.fill(x, mid - 4, x + 1, mid + 4, NOTCH);   // |
        g.fill(right - 1, mid - 4, right, mid + 4, NOTCH);
        boolean lit = this.isHovered() || this.dragged >= 0 || this.isFocused();
        // the knob: a small rounded bar standing across the groove, centred on its notch
        int left = at - 2;
        int top = mid - 5;
        g.fill(left, top + 1, left + 5, top + 9, EDGE);
        g.fill(left + 1, top, left + 4, top + 10, EDGE);
        g.fill(left + 1, top + 1, left + 4, top + 9, lit ? POINTER : POINTER_DARK);
        g.fill(left + 1, top + 1, left + 4, top + 2, lit ? 0xFFF4E2B0 : POINTER);   // lit along its top
        g.fill(left + 2, top + 3, left + 3, top + 7, lit ? POINTER_DARK : 0xFF8A6A2E); // a grip line down its middle
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getMessage());
    }
}
