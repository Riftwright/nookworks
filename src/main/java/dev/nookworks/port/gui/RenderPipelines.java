package dev.nookworks.port.gui;

/**
 * Stands in for the GUI pipelines of 26.3's {@code net.minecraft.client.renderer.RenderPipelines} - only those, for the
 * screens (the world's pipelines are the rendering port's). 1.21.1 draws the GUI without pipelines: these name how
 * {@link GuiGraphicsExtractor} draws a fill or a texture, as 26.3's pipelines of the same names do.
 */
public final class RenderPipelines {
    /** Untextured, blended: fills. */
    public static final GuiPipeline GUI = GuiPipeline.GUI;
    /** Textured, blended: blits and sprites. */
    public static final GuiPipeline GUI_TEXTURED = GuiPipeline.GUI_TEXTURED;
    /** Textured, opaque: a background no blending is wanted for. */
    public static final GuiPipeline GUI_OPAQUE_TEXTURED_BACKGROUND = GuiPipeline.GUI_OPAQUE_TEXTURED_BACKGROUND;

    private RenderPipelines() {}
}
