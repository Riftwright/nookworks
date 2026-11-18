package dev.nookworks.port.render;

import java.io.IOException;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;

import dev.nookworks.Nookworks;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/**
 * The Standing Mirrors' programs, as 1.21.1 has programs: a JSON file each under {@code shaders/core/}, loaded with the
 * game's own when resources load (26.3 registers render pipelines instead). Each is null until loaded, or if it failed
 * to: a mirror then shows its silver, and the game goes on.
 * <ul>
 * <li>{@code mirror_glass}: the glass, reading its picture where it is on the screen, fogged as the world is;</li>
 * <li>{@code mirror_glass_over_shaders}: the same with no fog, painted over a shader pack's finished picture;</li>
 * <li>{@code mirror_light}: what the pack painted on the measuring board, read into a light table;</li>
 * <li>{@code mirror_light_start}: a table begun as a copy of the game's own lightmap.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MirrorShaders {
    private static @Nullable ShaderInstance glass;
    private static @Nullable ShaderInstance glassOverShaders;
    private static @Nullable ShaderInstance light;
    private static @Nullable ShaderInstance lightStart;
    /** Counted up each time the programs load (the resources loading again): what was drawn with the last may be let go. */
    private static int loads;

    private MirrorShaders() {}

    public static int loads() {
        return loads;
    }

    public static @Nullable ShaderInstance glass() {
        return glass;
    }

    public static @Nullable ShaderInstance glassOverShaders() {
        return glassOverShaders;
    }

    public static @Nullable ShaderInstance light() {
        return light;
    }

    public static @Nullable ShaderInstance lightStart() {
        return lightStart;
    }

    /** Whether every program loaded. */
    public static boolean ready() {
        return glass != null && glassOverShaders != null && light != null && lightStart != null;
    }

    @SubscribeEvent
    static void register(RegisterShadersEvent event) {
        loads++;
        glass = null;
        glassOverShaders = null;
        light = null;
        lightStart = null;
        load(event, "mirror_glass", shader -> glass = shader);
        load(event, "mirror_glass_over_shaders", shader -> glassOverShaders = shader);
        load(event, "mirror_light", shader -> light = shader);
        load(event, "mirror_light_start", shader -> lightStart = shader);
    }

    private static void load(RegisterShadersEvent event, String name, Consumer<ShaderInstance> loaded) {
        ResourceLocation id = Nookworks.id(name);
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), id, DefaultVertexFormat.POSITION), loaded);
        } catch (IOException | RuntimeException e) {
            Nookworks.LOGGER.error("The Standing Mirror program {} did not load; mirrors show their silver", id, e);
        }
    }
}
