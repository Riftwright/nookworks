package dev.nookworks.port.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;

/**
 * Stands in for 26.3's {@code net.minecraft.client.input.InputWithModifiers}: a key or a mouse button with the modifier
 * keys held. 26.3 reads SDL codes; 1.21.1 hands the screens GLFW ones, so here every code is GLFW's - the key codes of
 * {@link com.mojang.blaze3d.platform.InputConstants} (1.21.1's, which the mod's sources name: they compile to GLFW
 * values) and GLFW's modifier bits. The questions asked of it mean what 26.3's mean.
 */
public interface InputWithModifiers {
    /** The key's code (GLFW), or the mouse button (GLFW: 0 left, 1 right, 2 middle). */
    int input();

    /** The code shortcuts are read from: the key as typed on the layout in use, which GLFW's key code already is. */
    default int shortcutKey() {
        return this.input();
    }

    /** GLFW's modifier bits: 1 shift, 2 control, 4 alt, 8 super. */
    int modifiers();

    /** Enter, space or the keypad's Enter: what presses a button. */
    default boolean isSelection() {
        return this.input() == GLFW.GLFW_KEY_ENTER || this.input() == GLFW.GLFW_KEY_SPACE || this.input() == GLFW.GLFW_KEY_KP_ENTER;
    }

    /** Enter or the keypad's Enter. */
    default boolean isConfirmation() {
        return this.input() == GLFW.GLFW_KEY_ENTER || this.input() == GLFW.GLFW_KEY_KP_ENTER;
    }

    default boolean isEscape() {
        return this.input() == GLFW.GLFW_KEY_ESCAPE;
    }

    default boolean isLeft() {
        return this.input() == GLFW.GLFW_KEY_LEFT;
    }

    default boolean isRight() {
        return this.input() == GLFW.GLFW_KEY_RIGHT;
    }

    default boolean isUp() {
        return this.input() == GLFW.GLFW_KEY_UP;
    }

    default boolean isDown() {
        return this.input() == GLFW.GLFW_KEY_DOWN;
    }

    default boolean isCycleFocus() {
        return this.input() == GLFW.GLFW_KEY_TAB;
    }

    default boolean hasAltDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_ALT) != 0;
    }

    default boolean hasShiftDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    default boolean hasControlDown() {
        return (this.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    /** Control, or Command on a Mac: the key editing shortcuts are held with there (26.3's quirk, 1.21.1's too). */
    default boolean hasControlDownWithQuirk() {
        return (this.modifiers() & (Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL)) != 0;
    }

    default boolean isSelectAll() {
        return this.shortcutKey() == GLFW.GLFW_KEY_A && this.hasControlDownWithQuirk() && !this.hasShiftDown() && !this.hasAltDown();
    }

    default boolean isCopy() {
        return this.shortcutKey() == GLFW.GLFW_KEY_C && this.hasControlDownWithQuirk() && !this.hasShiftDown() && !this.hasAltDown();
    }

    default boolean isPaste() {
        return this.shortcutKey() == GLFW.GLFW_KEY_V && this.hasControlDownWithQuirk() && !this.hasShiftDown() && !this.hasAltDown();
    }

    default boolean isCut() {
        return this.shortcutKey() == GLFW.GLFW_KEY_X && this.hasControlDownWithQuirk() && !this.hasShiftDown() && !this.hasAltDown();
    }
}
