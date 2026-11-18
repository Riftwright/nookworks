package dev.nookworks.client;

import java.util.function.IntPredicate;

/**
 * The keys the development input bridge holds down. Minecraft 1.21.1 reads Shift, Ctrl and Alt from the keyboard's
 * live state rather than from the click or key press it is handling, so a bridge that sends a shift-click has to make
 * that state say so ({@code mixin.dev.HeldKeysMixin}, added only in the agent's development run). In a player's game
 * nothing ever sets this, and the hook that would read it is not there.
 */
public final class AgentKeys {
    /** Whether a key (a GLFW key code) is held by the bridge; never by default. */
    public static volatile IntPredicate held = key -> false;

    private AgentKeys() {}
}
