package dev.nookworks.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Vanilla-style GUI panels and slots drawn with plain fills, so Nookworks screens need no background PNGs
 * (and never copy vanilla's). Colours match the classic light-grey container look.
 */
public final class GuiDraw {
    private static final int FACE = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int OUTLINE = 0xFF000000;
    private static final int SLOT_FACE = 0xFF8B8B8B;
    private static final int SLOT_DARK = 0xFF373737;

    /** A raised panel with a black outline, like a container background. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 1, y, x + w - 1, y + h, OUTLINE);
        g.fill(x, y + 1, x + w, y + h - 1, OUTLINE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, LIGHT);
        g.fill(x + 3, y + 3, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, FACE);
    }

    /** An 18x18 sunken slot whose item area starts at (x+1, y+1). */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, SLOT_DARK);
        g.fill(x + 1, y + 1, x + 18, y + 18, LIGHT);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT_FACE);
    }

    /** A sunken track (a scrollbar's), dark at the top left and lit at the bottom right. */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FACE);
    }

    /** A raised scrollbar thumb with grip lines; lit while it is held. */
    public static void thumb(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean held) {
        g.fill(x, y, x + w, y + h, OUTLINE);
        g.fill(x, y, x + w - 1, y + h - 1, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SHADOW);
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, held ? 0xFFDADADA : FACE);
        for (int i = -2; i <= 2; i += 2) {
            g.fill(x + 2, y + h / 2 + i, x + w - 3, y + h / 2 + i + 1, SHADOW);
        }
    }

    /** How full something is: a sunken bar filling with warm amber, lit along its top. */
    public static void gauge(GuiGraphicsExtractor g, int x, int y, int w, int h, float done) {
        well(g, x, y, w, h);
        int filled = done <= 0 ? 0 : Math.max(1, Math.round((w - 2) * Math.min(1.0F, done)));
        if (filled > 0) {
            int colour = done >= 0.95F ? 0xFFC0442E : 0xFFB8822E; // red when all but full
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, colour);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, done >= 0.95F ? 0xFFE8826A : 0xFFE8B868);
        }
    }

    /** Slots for every menu slot position given as item-area coordinates (as stored in Slot.x/y). */
    public static void slotAt(GuiGraphicsExtractor g, int left, int top, int slotX, int slotY) {
        slot(g, left + slotX - 1, top + slotY - 1);
    }

    private GuiDraw() {}
}
