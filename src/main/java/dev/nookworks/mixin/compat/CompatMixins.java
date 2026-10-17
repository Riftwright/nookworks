package dev.nookworks.mixin.compat;

import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import dev.nookworks.compat.CompatHooks;

/**
 * Adds the hooks into Sodium and Iris ({@link CompatHooks}) to {@code nookworks.compat.mixins.json} when those mods are
 * installed, and only then; a game without them no longer logs, each start, that their classes are missing. Each
 * hook that takes says so in the log, so one whose mod renamed its classes shows up as a line gone missing.
 */
public final class CompatMixins implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("nookworks");

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    /** The hooks whose mod is installed: its class is on the class path. None on a server: they are the client's. */
    @Override
    public List<String> getMixins() {
        if (MixinEnvironment.getCurrentEnvironment().getSide() == MixinEnvironment.Side.SERVER) {
            return List.of();
        }
        ClassLoader loader = CompatMixins.class.getClassLoader();
        return CompatHooks.present(target -> loader.getResource(target.replace('.', '/') + ".class") != null);
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.info("Compatibility hook {} applied to {}", mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1), targetClassName);
    }
}
