package dev.nookworks.experience;

import java.util.Set;

import dev.nookworks.port.transfer.FluidResource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The experience liquids of other mods that are worth what ours is, millibucket for millibucket: each counts 20 mB a
 * point in its own mod's code, as ours does ({@link ExperienceFluids#MB_PER_POINT}) - EnderIO's XP Juice
 * ({@code ExperienceUtil.EXP_TO_FLUID}), Cyclic's ({@code ExpItemGain.FLUID_PER_EXP}) and Sophisticated Core's
 * ({@code XpHelper}'s ratio). They are one thing to our tanks, pipes and machines: one poured onto another in a tank of
 * ours joins it as what is already there, and one asked for is given out of another. Nothing is minted that way: a
 * millibucket is a twentieth of a point on either side. (Their own blocks take ours as it is: EnderIO's XP Obelisk and
 * Cyclic's Experience Pylon, tried with the mods on 2026-11-17; Sophisticated Core's pump asks by the tag.)
 * <p>
 * Another liquid in {@code #c:experience} is taken in as before (the tag is what our blocks take), but is never mixed
 * with these or changed into one: its mod's worth of a millibucket is not known here.
 */
public final class ExperienceKinds {
    private static final Set<ResourceLocation> TWENTY_A_POINT = Set.of(
            ResourceLocation.fromNamespaceAndPath("nookworks", "liquid_experience"),
            ResourceLocation.fromNamespaceAndPath("enderio", "fluid_xp_juice_still"),
            ResourceLocation.fromNamespaceAndPath("cyclic", "xpjuice"),
            ResourceLocation.fromNamespaceAndPath("sophisticatedcore", "xp_still"));

    private ExperienceKinds() {}

    /** Whether {@code fluid} is one of the liquids worth what ours is. */
    public static boolean known(Fluid fluid) {
        return fluid != Fluids.EMPTY && TWENTY_A_POINT.contains(BuiltInRegistries.FLUID.getKey(fluid));
    }

    public static boolean known(FluidResource resource) {
        return !resource.isEmpty() && resource.isComponentsPatchEmpty() && known(resource.getFluid());
    }

    /** Whether the two are both of these liquids (or the same resource): one may stand for the other. */
    public static boolean alike(FluidResource a, FluidResource b) {
        return a.equals(b) || known(a) && known(b);
    }

    /**
     * What {@code incoming} is to be held as in a tank holding {@code held}: as what is there, if the two are alike;
     * else itself.
     */
    public static FluidResource as(FluidResource held, FluidResource incoming) {
        return !held.isEmpty() && !held.equals(incoming) && known(held) && known(incoming) ? held : incoming;
    }
}
