package dev.nookworks.mixin.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Adds the two hooks the development input bridge needs on 1.21.1 to {@code nookworks.dev.mixins.json}, and only in
 * the run that sets {@code -Dnookworks.agentInput=true} (the agent's development client), and the hook the GameTests
 * need ({@link GameTestChunksMixin}) to the runs that run them ({@code -Dneoforge.enabledGameTestNamespaces}): in a
 * player's game the list is empty and no class of the game is touched.
 */
public final class DevMixins implements IMixinConfigPlugin {
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

    @Override
    public List<String> getMixins() {
        List<String> mixins = new ArrayList<>();
        // the runs that run our GameTests (the test server; a development client or server that runs them by /test)
        if (System.getProperty("neoforge.enabledGameTestNamespaces") != null) {
            mixins.add("GameTestChunksMixin");
        }
        if (Boolean.getBoolean("nookworks.agentInput")
                && MixinEnvironment.getCurrentEnvironment().getSide() != MixinEnvironment.Side.SERVER) {
            mixins.add("HeldKeysMixin");
            mixins.add("HiddenWindowMixin");
        }
        return mixins;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
