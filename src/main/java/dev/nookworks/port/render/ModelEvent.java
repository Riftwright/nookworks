package dev.nookworks.port.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * 26.3 NeoForge's {@code ModelEvent.RegisterStandalone}, where the mod asks for models loaded on their own. The port
 * posts it on every mod's bus from 1.21.1's {@code ModelEvent.RegisterAdditional} ({@link PortClientEvents}), at every
 * resource reload, as 26.3 does; each model asked for is registered there, and once the models are baked it is found
 * by its key ({@link PortModelManager#getStandaloneModel}).
 * <p>
 * (A scripted edit, tools/port1211/tx/render.py, points the mod's {@code ModelEvent.RegisterStandalone} here; the rest
 * of 1.21.1's {@code ModelEvent} is used as it is.)
 */
public final class ModelEvent {
    private ModelEvent() {}

    /** Each key asked for at the last reload, and the model it stands for. */
    static final Map<StandaloneModelKey<?>, SimpleUnbakedStandaloneModel<?>> REQUESTED = new ConcurrentHashMap<>();
    /** Each key's model as last baked. */
    static volatile Map<StandaloneModelKey<?>, Object> baked = Map.of();

    public static final class RegisterStandalone extends Event implements IModBusEvent {
        private final net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional additional;

        RegisterStandalone(net.neoforged.neoforge.client.event.ModelEvent.RegisterAdditional additional) {
            this.additional = additional;
        }

        public <T> void register(StandaloneModelKey<T> key, SimpleUnbakedStandaloneModel<T> model) {
            REQUESTED.put(key, model);
            this.additional.register(ModelResourceLocation.standalone(model.modelId()));
        }
    }

    /** Once the models are baked: each key's model found in what was baked (missing ones are left out). */
    static void baked(Map<ModelResourceLocation, BakedModel> models) {
        Map<StandaloneModelKey<?>, Object> found = new ConcurrentHashMap<>();
        REQUESTED.forEach((key, unbaked) -> {
            BakedModel model = models.get(ModelResourceLocation.standalone(unbaked.modelId()));
            if (model != null) {
                found.put(key, unbaked.wrap(model));
            }
        });
        baked = found;
    }
}
