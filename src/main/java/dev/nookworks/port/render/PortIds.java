package dev.nookworks.port.render;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * The mod's id and names as the port layer uses them: the layer names the mod's things by id, not by class, so that it
 * compiles on its own (tools/port1211/javac.py --only port/render).
 */
public final class PortIds {
    public static final String MODID = "nookworks";
    static final Logger LOGGER = LogUtils.getLogger();

    private PortIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** A data component of the mod's, by its id; null if none is registered so. */
    @SuppressWarnings("unchecked")
    static <T> DataComponentType<T> component(String path) {
        return (DataComponentType<T>) BuiltInRegistries.DATA_COMPONENT_TYPE.get(id(path));
    }
}
