package dev.nookworks.port.gui;

/**
 * How {@link GuiGraphicsExtractor} draws a GUI fill or texture: the 26.3 GUI pipelines the mod's screens name, in
 * 1.21.1's terms (blending on or off).
 */
public enum GuiPipeline {
    GUI(true),
    GUI_TEXTURED(true),
    GUI_OPAQUE_TEXTURED_BACKGROUND(false);

    private final boolean blended;

    GuiPipeline(boolean blended) {
        this.blended = blended;
    }

    public boolean blended() {
        return this.blended;
    }
}
