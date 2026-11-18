package dev.nookworks.port.render;

/**
 * 26.3 NeoForge's {@code StandaloneModelKey<T>}: the handle a model loaded on its own (not through a block state or an
 * item) is asked for by, once baked ({@link PortModelManager#getStandaloneModel}). Compared by identity, as 26.3's.
 */
public final class StandaloneModelKey<T> {
    /** 26.3's {@code ModelDebugName}: the key's name, for logs and for the mod's own lookups. */
    @FunctionalInterface
    public interface DebugName {
        String debugName();
    }

    private final DebugName name;

    public StandaloneModelKey(DebugName name) {
        this.name = name;
    }

    public String getName() {
        return this.name.debugName();
    }

    @Override
    public String toString() {
        return "StandaloneModelKey[name=" + this.name.debugName() + ']';
    }
}
