package dev.nookworks.port.gui;

/**
 * Stands in for 26.3's {@code com.mojang.blaze3d.platform.cursor.CursorTypes}: the pointer shapes, as GLFW's standard
 * cursors (the numbers are GLFW's shape ids: the pointing hand and the two resize arrows are in every GLFW 1.21.1 ships
 * with; the four-way arrow and "not allowed" only from GLFW 3.4 - with an older one they fall back to the arrow).
 */
public final class CursorTypes {
    public static final CursorType ARROW = new CursorType(0x00036001, "arrow");
    public static final CursorType IBEAM = new CursorType(0x00036002, "ibeam");
    public static final CursorType CROSSHAIR = new CursorType(0x00036003, "crosshair");
    public static final CursorType POINTING_HAND = new CursorType(0x00036004, "pointing_hand");
    public static final CursorType RESIZE_EW = new CursorType(0x00036005, "resize_ew");
    public static final CursorType RESIZE_NS = new CursorType(0x00036006, "resize_ns");
    // PORT: the next two only with GLFW 3.4 (an older one logs an error once and shows the arrow)
    public static final CursorType RESIZE_ALL = new CursorType(0x00036009, "resize_all");
    public static final CursorType NOT_ALLOWED = new CursorType(0x0003600A, "not_allowed");

    private CursorTypes() {}
}
