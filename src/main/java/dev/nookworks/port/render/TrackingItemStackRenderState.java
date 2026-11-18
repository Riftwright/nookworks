package dev.nookworks.port.render;

/**
 * 26.3's {@code TrackingItemStackRenderState}: an item's state that also says which model it was drawn with
 * ({@link #getModelIdentity}), so what is worked out from a model can be kept by it. Here the identity is the 1.21.1
 * baked model picked for the stack (overrides and all); a reload bakes new ones.
 */
public class TrackingItemStackRenderState extends ItemStackRenderState {
    public Object getModelIdentity() {
        return this.isEmpty() ? "empty" : this.model();
    }
}
