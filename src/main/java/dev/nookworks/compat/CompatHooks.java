package dev.nookworks.compat;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The hooks into other mods' own classes (the mixins of {@code nookworks.compat.mixins.json}), each with the class it
 * goes into. The config's plugin, {@code dev.nookworks.mixin.compat.CompatMixins}, adds a hook only when its class is
 * there: listed in the config outright, each was looked for in every game without its mod, and every start logged a
 * warning that the class was missing. Kept out of the mixin package, from which the game loads nothing but mixins, so
 * that a test can read it.
 */
public final class CompatHooks {
    /** Each hook, by its class's simple name, and the class of the other mod it goes into. */
    public static final Map<String, String> TARGETS = Map.of(
            "SodiumLevelSliceMixin", "net.caffeinemc.mods.sodium.client.world.LevelSlice",
            "IrisBlockIdsMixin", "net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings",
            "IrisHandLightMixin", "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier");

    /** The hooks whose class {@code installed} says is there, by name. */
    public static List<String> present(Predicate<String> installed) {
        return TARGETS.entrySet().stream().filter(hook -> installed.test(hook.getValue())).map(Map.Entry::getKey).sorted().toList();
    }

    private CompatHooks() {}
}
