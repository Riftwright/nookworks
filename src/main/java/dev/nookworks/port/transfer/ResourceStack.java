package dev.nookworks.port.transfer;

/** Stand-in for NeoForge 26.3's {@code transfer.resource.ResourceStack}: a resource with an amount, as moves report them. */
public record ResourceStack<T extends Resource>(T resource, int amount) {
    public boolean isEmpty() {
        return this.resource.isEmpty() || this.amount == 0;
    }
}
