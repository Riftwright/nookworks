package dev.nookworks.port.transfer;

/** Stand-in for NeoForge 26.3's {@code transfer.TransferPreconditions}: the argument checks every handler makes. */
public final class TransferPreconditions {
    private TransferPreconditions() {}

    public static void checkNonEmpty(Resource resource) {
        if (resource.isEmpty()) {
            throw new IllegalArgumentException("Expected a non-empty resource, got " + resource);
        }
    }

    public static void checkNonNegative(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Expected a non-negative amount, got " + value);
        }
    }

    public static void checkNonEmptyNonNegative(Resource resource, int value) {
        checkNonEmpty(resource);
        checkNonNegative(value);
    }
}
