package dev.nookworks.port.gui;

/**
 * Stands in for 26.3's {@code net.minecraft.client.input.MouseButtonEvent}: where the pointer was (GUI coordinates) and
 * which button, with the modifier keys. Made by the port's screens and widgets from 1.21.1's
 * {@code mouseClicked(double, double, int)} and its kin.
 */
public record MouseButtonEvent(double x, double y, MouseButtonInfo buttonInfo) implements InputWithModifiers {
    /** A press or release of {@code button} at ({@code x}, {@code y}) now, with the modifier keys held as they are. */
    public MouseButtonEvent(double x, double y, int button) {
        this(x, y, MouseButtonInfo.of(button));
    }

    @Override
    public int input() {
        return this.button();
    }

    public int button() {
        return this.buttonInfo.button();
    }

    @Override
    public int modifiers() {
        return this.buttonInfo.modifiers();
    }
}
