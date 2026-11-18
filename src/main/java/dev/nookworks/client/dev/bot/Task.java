package dev.nookworks.client.dev.bot;

import net.minecraft.client.Minecraft;

/** One thing the walker has been asked to do, ticked until it is done or fails (its reason in {@link Bot#fail}). */
interface Task {
    enum Result { RUNNING, DONE, FAILED }

    /** A line for the state file: what it is doing, and how far along. */
    String describe();

    Result tick(Minecraft mc, Controls c);

    /** Let go of whatever it holds (a key, a screen, a half-broken block). */
    default void cancel(Minecraft mc) {}
}
