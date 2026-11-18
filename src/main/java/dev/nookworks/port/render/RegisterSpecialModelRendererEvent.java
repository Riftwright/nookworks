package dev.nookworks.port.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** 26.3 NeoForge's {@code RegisterSpecialModelRendererEvent}, posted on every mod's bus by the port at client setup. */
public final class RegisterSpecialModelRendererEvent extends Event implements IModBusEvent {
    static final Map<ResourceLocation, MapCodec<? extends SpecialModelRenderer.Unbaked<?>>> TYPES = new ConcurrentHashMap<>();

    RegisterSpecialModelRendererEvent() {}

    public void register(ResourceLocation location, MapCodec<? extends SpecialModelRenderer.Unbaked<?>> source) {
        TYPES.put(location, source);
    }
}
