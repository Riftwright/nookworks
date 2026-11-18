package dev.nookworks.port.render;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/** 26.3 NeoForge's {@code RegisterConditionalItemModelPropertyEvent}, posted on every mod's bus by the port at client
 * setup. In 1.21.1 a condition applies to the items {@link ItemModelProperties#addCondition} names (ours are in its
 * table). */
public final class RegisterConditionalItemModelPropertyEvent extends Event implements IModBusEvent {
    static final Map<ResourceLocation, MapCodec<? extends ConditionalItemModelProperty>> TYPES = new ConcurrentHashMap<>();

    RegisterConditionalItemModelPropertyEvent() {}

    public void register(ResourceLocation location, MapCodec<? extends ConditionalItemModelProperty> source) {
        TYPES.put(location, source);
    }
}
