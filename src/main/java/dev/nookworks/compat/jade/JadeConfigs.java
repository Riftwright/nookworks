package dev.nookworks.compat.jade;

import java.util.List;

import dev.nookworks.Nookworks;
import net.minecraft.resources.ResourceLocation;

/**
 * The switches our Jade plugin adds to Jade's settings. Jade wants a name for each, under
 * {@code config.jade.plugin_<namespace>.<path>}, and in a development game refuses to finish loading resources
 * without one (a "resource reload failed" toast on the title screen). Kept apart from the plugin, which only loads
 * with Jade installed, so a test can check every one is named.
 */
public final class JadeConfigs {
    public static final ResourceLocation READOUT = Nookworks.id("readout");
    public static final List<ResourceLocation> ALL = List.of(READOUT);

    private JadeConfigs() {}

    public static String translationKey(ResourceLocation id) {
        return "config.jade.plugin_" + id.getNamespace() + "." + id.getPath();
    }
}
