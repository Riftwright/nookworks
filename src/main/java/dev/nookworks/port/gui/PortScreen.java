package dev.nookworks.port.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * What the mod's screens extend in place of {@link Screen} ({@code tools/port1211/tx/gui.py} changes their
 * {@code extends}): 1.21.1's screen, drawing and taking input through 26.3's methods, so the screens keep their bodies.
 * <p>
 * Drawing goes as 26.3's frame does: {@link #extractBackground} (by default 1.21.1's background: the blur and the menu
 * background, or the panorama outside a world - what 26.3 draws for a screen that is not an in-game one), then
 * {@link #extractRenderState} (by default the widgets, through 1.21.1's {@code render}), then the tooltip kept that frame.
 * Input from 1.21.1's calls ({@code mouseClicked(double, double, int)} and the rest) goes to the 26.3 methods taking
 * events, whose defaults hand it back to 1.21.1's own handling - never to the 26.3 method again.
 */
public abstract class PortScreen extends Screen {
    /** Set while 1.21.1's {@code render} draws the widgets for {@link #extractRenderState}: its background is drawn already. */
    private boolean drawingWidgets;

    protected PortScreen(Component title) {
        super(title);
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor g = GuiGraphicsExtractor.of(graphics).begin();
        this.extractBackground(g, mouseX, mouseY, a);
        g.nextStratum();
        this.extractRenderState(g, mouseX, mouseY, a);
        g.finishFrame();
    }

    /** The screen's background, before anything else. */
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.beforeVanilla();
        super.renderBackground(graphics.graphics(), mouseX, mouseY, a);
    }

    /** 1.21.1 asks for the background from inside {@code render}: drawn already, at the frame's start, by the port. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (this.drawingWidgets) {
            return;
        }
        this.extractBackground(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    /** The screen's contents over its background: by default its widgets. */
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.beforeVanilla();
        this.drawingWidgets = true;
        try {
            super.render(graphics.graphics(), mouseX, mouseY, a);
        } finally {
            this.drawingWidgets = false;
        }
    }

    // ---- input: 1.21.1's calls to 26.3's methods, whose defaults are 1.21.1's handling ----

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return this.mouseClicked(Inputs.event(x, y, button), Inputs.doubleClick(x, y, button));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return this.mouseReleased(Inputs.event(x, y, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return this.mouseDragged(Inputs.event(x, y, button), dx, dy);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return this.keyPressed(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        return this.keyReleased(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.charTyped(new CharacterEvent(codePoint, modifiers));
    }

    public boolean charTyped(CharacterEvent event) {
        return super.charTyped((char) event.codepoint(), event.modifiers());
    }
}
