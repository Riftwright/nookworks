package dev.nookworks.port.gui;

import org.lwjgl.glfw.GLFW;

/**
 * Stands in for 26.3's {@code com.mojang.blaze3d.platform.cursor.CursorType}: a pointer shape a screen asks for while
 * drawing ({@link GuiGraphicsExtractor#requestCursor}). 1.21.1 has no such thing, so the port sets the window's cursor
 * through GLFW itself, as 26.3 does through SDL: one of GLFW's standard shapes, made the first time it is wanted. The
 * default ({@code shape} 0) is the window's own arrow.
 */
public final class CursorType {
    public static final CursorType DEFAULT = new CursorType(0, "default");

    private final int shape;
    private final String name;
    private long handle;
    private boolean made;

    CursorType(int shape, String name) {
        this.shape = shape;
        this.name = name;
    }

    /** The GLFW cursor to set: 0 for the window's default arrow, or where the shape is not to be had (an older GLFW). */
    long handle() {
        if (this.shape == 0) {
            return 0L;
        }
        if (!this.made) {
            this.made = true;
            this.handle = GLFW.glfwCreateStandardCursor(this.shape);
        }
        return this.handle;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
