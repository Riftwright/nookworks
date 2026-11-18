package dev.nookworks.port.gui;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FastColor;

/**
 * What the mod's buttons extend in place of {@link AbstractButton} ({@code tools/port1211/tx/gui.py} changes their
 * {@code extends}): 1.21.1's button with 26.3's ways - pressed through {@link #onPress(InputWithModifiers)} with the
 * press that did it (a click or a key), drawn through {@link #extractContents} (vanilla's sprite there by
 * {@link #extractDefaultSprite}), its label greyed while inactive as 26.3's buttons' is, the pointer a hand over it.
 * Clicks go as {@link PortWidget}'s do.
 */
public abstract class PortButton extends AbstractButton {
    /** 26.3's {@code WithInactiveMessage}: the label shown while inactive, greyed unless it has a colour of its own. */
    private Component inactiveMessage;
    /** The press under way, for {@link #onPress()} when 1.21.1 presses the button itself. */
    private @Nullable InputWithModifiers pressing;

    protected PortButton(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
        this.inactiveMessage = inactive(message);
    }

    private static Component inactive(Component message) {
        return ComponentUtils.mergeStyles(message.copy(), Style.EMPTY.withColor(-6250336));
    }

    public abstract void onPress(InputWithModifiers input);

    /** 1.21.1 presses a button without saying how: the press under way, or a plain left click. */
    @Override
    public final void onPress() {
        InputWithModifiers input = this.pressing;
        this.onPress(input != null ? input : MouseButtonInfo.of(InputConstants.MOUSE_BUTTON_LEFT));
    }

    @Override
    public Component getMessage() {
        return this.active ? super.getMessage() : this.inactiveMessage;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.inactiveMessage = inactive(message);
    }

    // ---- drawing ----

    @Override
    protected final void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor g = GuiGraphicsExtractor.of(graphics);
        this.extractContents(g, mouseX, mouseY, a);
        if (this.isHovered()) {
            g.requestCursor(this.isActive() ? CursorTypes.POINTING_HAND : CursorTypes.NOT_ALLOWED);
        }
    }

    protected abstract void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a);

    /** Vanilla's button: plain, lit while hovered or focused, dim while inactive. */
    protected final void extractDefaultSprite(GuiGraphicsExtractor graphics) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SPRITES.get(this.active, this.isHoveredOrFocused()), this.getX(), this.getY(),
                this.getWidth(), this.getHeight(), FastColor.ARGB32.colorFromFloat(this.alpha, 1.0F, 1.0F, 1.0F));
    }

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

    /** 26.3's button: a click presses it. */
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        this.press(event);
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

    /** 26.3's button: Enter, space or the keypad's Enter presses it while it is active. */
    public boolean keyPressed(KeyEvent event) {
        if (!this.isActive()) {
            return false;
        }
        if (event.isSelection()) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            this.press(event);
            return true;
        }
        return false;
    }

    private void press(InputWithModifiers input) {
        this.pressing = input;
        try {
            this.onPress(input);
        } finally {
            this.pressing = null;
        }
    }
}
