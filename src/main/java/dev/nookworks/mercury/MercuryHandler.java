package dev.nookworks.mercury;

/**
 * Nookworks-internal mercury storage, in millibuckets. Implemented by the block entities of the Mercury Tank and
 * the mercury machines, and found by {@link MercuryNetwork} through Mercury Pipes. It is intentionally not a
 * NeoForge fluid capability: other mods' pipes and tanks (brass, copper, gold...) must not handle mercury.
 */
public interface MercuryHandler {
    enum Role {
        /** Tanks: take from producers, give to consumers. */
        STORAGE,
        /** Retort and distiller: push what they make into the network. */
        PRODUCER,
        /** Stirring tank: pulls what it needs from storage. */
        CONSUMER
    }

    Role mercuryRole();

    int mercury();

    int mercuryCapacity();

    /** Adds up to {@code amount}; returns what was accepted. */
    int fillMercury(int amount, boolean simulate);

    /** Removes up to {@code amount}; returns what was removed. */
    int drainMercury(int amount, boolean simulate);
}
