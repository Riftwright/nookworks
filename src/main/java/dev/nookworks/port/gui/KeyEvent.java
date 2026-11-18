package dev.nookworks.port.gui;

/**
 * Stands in for 26.3's {@code net.minecraft.client.input.KeyEvent}: a key press as 1.21.1's {@code keyPressed(int, int,
 * int)} receives it - {@code key} the GLFW key code, {@code keycode} the scan code (26.3's second field, kept under its
 * name), {@code modifiers} GLFW's bits. {@link #shortcutKey} is the key code: GLFW's already follows the keyboard's layout.
 */
public record KeyEvent(int key, int keycode, int modifiers) implements InputWithModifiers {
    @Override
    public int input() {
        return this.key;
    }

    /** The scan code, for passing back to a 1.21.1 widget's {@code keyPressed(key, scanCode, modifiers)}. */
    public int scancode() {
        return this.keycode;
    }
}
