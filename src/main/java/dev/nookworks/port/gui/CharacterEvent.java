package dev.nookworks.port.gui;

import net.minecraft.util.StringUtil;

/**
 * Stands in for 26.3's {@code net.minecraft.client.input.CharacterEvent}: a character typed, as 1.21.1's
 * {@code charTyped(char, int)} receives it (the modifiers kept for passing back).
 */
public record CharacterEvent(int codepoint, int modifiers) {
    public CharacterEvent(int codepoint) {
        this(codepoint, Inputs.modifiers());
    }

    public String codepointAsString() {
        return Character.toString(this.codepoint);
    }

    public boolean isAllowedChatCharacter() {
        return StringUtil.isAllowedChatCharacter((char) this.codepoint);
    }
}
