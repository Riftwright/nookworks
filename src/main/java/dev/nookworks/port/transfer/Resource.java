package dev.nookworks.port.transfer;

/** Stand-in for NeoForge 26.3's {@code transfer.resource.Resource}: what a handler holds (an item, a fluid), without an amount. */
public interface Resource {
    /** Whether this is the empty resource (an empty slot or tank holds it). */
    boolean isEmpty();
}
