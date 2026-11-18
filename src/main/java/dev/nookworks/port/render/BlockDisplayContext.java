package dev.nookworks.port.render;

/**
 * 26.3's {@code BlockDisplayContext}: a key for how a block model is looked up away from the world. 1.21.1 has one way,
 * the block state's model, so it carries nothing.
 */
public final class BlockDisplayContext {
    private BlockDisplayContext() {}

    public static BlockDisplayContext create() {
        return new BlockDisplayContext();
    }
}
