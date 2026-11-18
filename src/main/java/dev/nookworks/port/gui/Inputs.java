package dev.nookworks.port.gui;

import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * What 26.3's input events carry that 1.21.1's screen calls leave out: the modifier keys held with a mouse button, and
 * whether a press is a double click. 26.3's mouse handler works the double click out once a press - under 250 ms after
 * the last press the screen took, on the same screen, with the same button - and hands it to the screen and on to its
 * widgets; here {@link GuiEvents} works it out the same way as NeoForge announces the press, before the screen has it,
 * and the port's screens and widgets ask for it ({@link #doubleClick}).
 */
public final class Inputs {
    /** 26.3's {@code MouseHandler.DOUBLE_CLICK_THRESHOLD_MS}. */
    private static final long DOUBLE_CLICK_MS = 250L;

    private record Press(double x, double y, int button, boolean doubleClick) {}

    private record Last(long time, Screen screen, int button) {}

    private static @Nullable Press current;
    private static @Nullable Last last;

    private Inputs() {}

    /** The modifier keys held now, as GLFW's bits (shift 1, control 2, alt 4, super 8). */
    public static int modifiers() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        int mods = 0;
        if (down(window, GLFW.GLFW_KEY_LEFT_SHIFT) || down(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) {
            mods |= GLFW.GLFW_MOD_SHIFT;
        }
        if (down(window, GLFW.GLFW_KEY_LEFT_CONTROL) || down(window, GLFW.GLFW_KEY_RIGHT_CONTROL)) {
            mods |= GLFW.GLFW_MOD_CONTROL;
        }
        if (down(window, GLFW.GLFW_KEY_LEFT_ALT) || down(window, GLFW.GLFW_KEY_RIGHT_ALT)) {
            mods |= GLFW.GLFW_MOD_ALT;
        }
        if (down(window, GLFW.GLFW_KEY_LEFT_SUPER) || down(window, GLFW.GLFW_KEY_RIGHT_SUPER)) {
            mods |= GLFW.GLFW_MOD_SUPER;
        }
        return mods;
    }

    private static boolean down(long window, int key) {
        return InputConstants.isKeyDown(window, key);
    }

    /** A press is about to reach {@code screen} (NeoForge's pre event): whether it is a double click, worked out as 26.3 does. */
    static void pressing(Screen screen, double x, double y, int button) {
        long now = Util.getMillis();
        Last before = last;
        boolean doubleClick = before != null && now - before.time() < DOUBLE_CLICK_MS && before.screen() == screen && before.button() == button;
        current = new Press(x, y, button, doubleClick);
    }

    /** The screen has had the press: 26.3 counts it towards the next double click only if the screen took it. */
    static void pressed(Screen screen, int button, boolean handled) {
        if (handled) {
            last = new Last(Util.getMillis(), screen, button);
        }
    }

    /** Whether the press at ({@code x}, {@code y}) with {@code button} being handled now is a double click. */
    public static boolean doubleClick(double x, double y, int button) {
        Press press = current;
        return press != null && press.button() == button && press.x() == x && press.y() == y && press.doubleClick();
    }

    /** 1.21.1's press, as 26.3's event. */
    public static MouseButtonEvent event(double x, double y, int button) {
        return new MouseButtonEvent(x, y, MouseButtonInfo.of(button));
    }
}
