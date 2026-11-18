package dev.nookworks.port.gui;

/**
 * Stands in for 26.3's {@code net.minecraft.client.input.MouseButtonInfo}: a mouse button (GLFW's numbering, as 1.21.1
 * passes it: 0 left, 1 right, 2 middle - the {@code InputConstants.MOUSE_BUTTON_*} the mod compares with) and the
 * modifier keys held (GLFW's bits).
 */
public record MouseButtonInfo(int button, int modifiers) implements InputWithModifiers {
    @Override
    public int input() {
        return this.button;
    }

    /** A press of {@code button} now, with the modifier keys held as they are. */
    public static MouseButtonInfo of(int button) {
        return new MouseButtonInfo(button, Inputs.modifiers());
    }
}
