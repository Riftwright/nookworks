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
            "IrisHandLightMixin", "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier",
            // the 1.21.1 port's Create part, a client's look: a Create Gearbox turned through one of our trains
            "create.GearboxRendererMixin", "com.simibubi.create.content.kinetics.gearbox.GearboxRenderer",
            "create.GearboxVisualMixin", "com.simibubi.create.content.kinetics.gearbox.GearboxVisual");

    /**
     * The hooks that go in on a server too, by name (relative to the mixin package), and the class whose being there
     * says their mod is: the 1.21.1 port's Create part - a Create block entity's stress with the Nookworks machines it
     * turns, our machines answering Create's goggles, and Create's pipes and Hose Pulley leaving mercury pools alone, its Threshold Switch
     * reading our Storage Barrels' big slots,
     * and our turning parts in Create's own network: a Create block entity as an end of our trains meets it, Create's
     * propagation with our trains in it, a Gearbox's face through a train (dev.nookworks.compat.create.CreateEnds).
     */
    public static final Map<String, String> COMMON_TARGETS = Map.of(
            "create.KineticStressMixin", "com.simibubi.create.content.kinetics.base.KineticBlockEntity",
            "create.MachineGogglesMixin", "com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation",
            "create.OpenPipeMercuryMixin", "com.simibubi.create.content.fluids.OpenEndedPipe",
            "create.HosePulleyMercuryMixin", "com.simibubi.create.content.fluids.transfer.FluidDrainingBehaviour",
            "create.StockpileSwitchMixin", "com.simibubi.create.content.redstone.thresholdSwitch.ThresholdSwitchBlockEntity",
            "create.KineticEndMixin", "com.simibubi.create.content.kinetics.base.KineticBlockEntity",
            "create.PropagatorEndMixin", "com.simibubi.create.content.kinetics.RotationPropagator",
            "create.GeneratorSpeedMixin", "com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity",
            "create.ShaftHalvesFacingMixin", "com.simibubi.create.content.kinetics.base.DirectionalShaftHalvesBlockEntity");

    /** The common hooks whose class {@code installed} says is there, by name. */
    public static List<String> presentCommon(Predicate<String> installed) {
        return COMMON_TARGETS.entrySet().stream().filter(hook -> installed.test(hook.getValue())).map(Map.Entry::getKey).sorted().toList();
    }

    /** The hooks whose class {@code installed} says is there, by name. */
    public static List<String> present(Predicate<String> installed) {
        return TARGETS.entrySet().stream().filter(hook -> installed.test(hook.getValue())).map(Map.Entry::getKey).sorted().toList();
    }

    private CompatHooks() {}
}
