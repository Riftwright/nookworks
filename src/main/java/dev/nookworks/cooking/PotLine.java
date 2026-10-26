package dev.nookworks.cooking;

import java.util.function.Consumer;

import dev.nookworks.NookworksNetwork;

/** What the client does with an Iron Pot's contents sent for the action bar (dev.nookworks.client.PotLines sets it). */
public final class PotLine {
    /** On the client, from the payload's handler; nothing on a dedicated server, which is never sent one. */
    public static volatile Consumer<NookworksNetwork.PotContents> shown = contents -> {};

    private PotLine() {}
}
