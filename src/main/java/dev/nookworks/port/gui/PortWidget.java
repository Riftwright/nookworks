package dev.nookworks.port.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

/**
 * What the mod's widgets extend in place of {@link AbstractWidget} ({@code tools/port1211/tx/gui.py} changes their
 * {@code extends}): 1.21.1's widget drawn through 26.3's {@link #extractWidgetRenderState} and clicked through 26.3's
 * event methods.
 * <p>
 * A press goes as 26.3's {@code AbstractWidget.mouseClicked} takes it: active and visible, a button it takes
 * ({@link #isValidClickButton(MouseButtonInfo)}, the left one by default), over it - then the click sound and
 * {@link #onClick(MouseButtonEvent, boolean)}; a release and a drag with a button it takes go to {@link #onRelease} and
 * {@link #onDrag}. Their defaults call 1.21.1's (empty) ones, never the 26.3 methods again.
 */
public abstract class PortWidget extends AbstractWidget {
    protected PortWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    // ---- drawing ----

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        this.extractWidgetRenderState(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    /** The widget's drawing, when it is visible (1.21.1's {@code render} has worked out whether it is hovered). */
    protected abstract void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    // ---- input ----

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return this.mouseClicked(Inputs.event(x, y, button), Inputs.doubleClick(x, y, button));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!this.isActive()) {
            return false;
        }
        if (this.isValidClickButton(event.buttonInfo()) && this.isMouseOver(event.x(), event.y())) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.onClick(event, doubleClick);
            return true;
        }
        return false;
    }

    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        super.onClick(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return this.mouseReleased(Inputs.event(x, y, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.isValidClickButton(event.buttonInfo())) {
            this.onRelease(event);
            return true;
        }
        return false;
    }

    public void onRelease(MouseButtonEvent event) {
        super.onRelease(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return this.mouseDragged(Inputs.event(x, y, button), dx, dy);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.isValidClickButton(event.buttonInfo())) {
            this.onDrag(event, dx, dy);
            return true;
        }
        return false;
    }

    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        super.onDrag(event.x(), event.y(), dx, dy);
    }

    /** 1.21.1 asks with the button alone: the same answer as 26.3's question. */
    @Override
    protected final boolean isValidClickButton(int button) {
        return this.isValidClickButton(MouseButtonInfo.of(button));
    }

    protected boolean isValidClickButton(MouseButtonInfo info) {
        return info.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return this.keyPressed(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.charTyped(new CharacterEvent(codePoint, modifiers));
    }

    public boolean charTyped(CharacterEvent event) {
        return super.charTyped((char) event.codepoint(), event.modifiers());
    }
}
