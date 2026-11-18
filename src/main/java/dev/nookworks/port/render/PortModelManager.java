package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.level.material.FluidState;

/**
 * What 26.3's {@code ModelManager} gives the mod that 1.21.1's lacks: a model loaded on its own by its key, and the
 * look of a liquid. A scripted edit (tools/port1211/tx/render.py) wraps the mod's {@code getModelManager()} in this
 * where those are asked for.
 */
public final class PortModelManager {
    private final ModelManager models;

    private PortModelManager(ModelManager models) {
        this.models = models;
    }

    public static PortModelManager of(ModelManager models) {
        return new PortModelManager(models);
    }

    /** The game's own model manager. */
    public ModelManager vanilla() {
        return this.models;
    }

    /** The model {@code key} was registered for, as last baked; null before then, or if its file was missing. */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getStandaloneModel(StandaloneModelKey<T> key) {
        return (T) ModelEvent.baked.get(key);
    }

    public FluidStateModelSet getFluidStateModelSet() {
        return FluidStateModelSet.INSTANCE;
    }

    /** 26.3's {@code FluidStateModelSet}: each liquid's look, here read off its client extensions. */
    public static final class FluidStateModelSet {
        static final FluidStateModelSet INSTANCE = new FluidStateModelSet();

        private FluidStateModelSet() {}

        public FluidModel get(FluidState fluid) {
            return FluidModel.of(fluid);
        }
    }
}
