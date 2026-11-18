package dev.nookworks.port.render;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 26.3 NeoForge's {@code SimpleUnbakedStandaloneModel}: a model file to be loaded on its own. In 1.21.1 that is a model
 * registered with {@code ModelEvent.RegisterAdditional} under {@code ModelResourceLocation.standalone(modelId)} and
 * baked with everything else; {@link #wrap} turns the baked model into what the key is for.
 */
public final class SimpleUnbakedStandaloneModel<T> {
    private final ResourceLocation modelId;
    private final Wrapper<T> wrapper;

    interface Wrapper<T> {
        T wrap(BakedModel model);
    }

    private SimpleUnbakedStandaloneModel(ResourceLocation modelId, Wrapper<T> wrapper) {
        this.modelId = modelId;
        this.wrapper = wrapper;
    }

    /** The model file's quads, as one part ({@link BlockStateModelPart}). */
    public static SimpleUnbakedStandaloneModel<BlockStateModelPart> simpleModelWrapper(ResourceLocation modelId) {
        return new SimpleUnbakedStandaloneModel<>(modelId, BlockStateModelPart::new);
    }

    /** The model file as a whole baked model, as a block state's model is in 1.21.1. */
    public static SimpleUnbakedStandaloneModel<BakedModel> blockStateModel(ResourceLocation modelId) {
        return new SimpleUnbakedStandaloneModel<>(modelId, model -> model);
    }

    public ResourceLocation modelId() {
        return this.modelId;
    }

    T wrap(BakedModel model) {
        return this.wrapper.wrap(model);
    }
}
