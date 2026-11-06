package dev.nookworks.experience;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.WadeFluidType;
import dev.nookworks.fluid.KeptFluid;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Liquid Experience: bottled-up XP, the same idea other tech mods call XP Juice or Essence.
 * <p>
 * Unlike mercury this is a fully public fluid: it is tagged {@code #c:experience}, and everything here that
 * holds or moves it accepts any fluid in that tag, so another mod's pipes, tanks and machines work with ours
 * in either direction. {@link #MB_PER_POINT} follows the same 20 mB per XP point those mods settled on, so a
 * bucket is worth 50 points however it was filled.
 */
public final class ExperienceFluids {
    /** The cross-mod tag for XP fluids. */
    public static final TagKey<Fluid> EXPERIENCE = TagKey.create(Registries.FLUID,
            Identifier.fromNamespaceAndPath("c", "experience"));

    /** The de-facto standard exchange rate: 20 mB is one experience point. */
    public static final int MB_PER_POINT = 20;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Nookworks.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Nookworks.MODID);

    public static final Supplier<FluidType> TYPE = FLUID_TYPES.register("liquid_experience",
            () -> new WadeFluidType(FluidType.Properties.create()
                    .descriptionId("block.nookworks.liquid_experience")
                    .density(1_000).viscosity(1_200).temperature(300)
                    .canSwim(true).canDrown(false).canPushEntity(true).supportsBoating(false)
                    .lightLevel(7)
                    .motionScale(0.008)
                    .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                    .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.8F));

    public static final Supplier<FlowingFluid> SOURCE = FLUIDS.register("liquid_experience",
            () -> new KeptFluid.Source(properties()));
    public static final Supplier<FlowingFluid> FLOWING = FLUIDS.register("flowing_liquid_experience",
            () -> new KeptFluid.Flowing(properties(), false));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
                .bucket(ModItems.EXPERIENCE_BUCKET).block(ModBlocks.LIQUID_EXPERIENCE)
                .slopeFindDistance(3).levelDecreasePerBlock(1).tickRate(5);
    }

    /**
     * What an experience block held, poured out as orbs where it stood. Tanks, pads, pipes and the Repair Box keep
     * nothing when they are broken, so their contents come out this way rather than vanishing.
     */
    public static void spillAsOrbs(ServerLevel level, BlockPos pos, int millibuckets) {
        int points = points(millibuckets);
        if (points > 0) {
            ExperienceOrb.award(level, Vec3.atCenterOf(pos), points);
        }
    }

    /**
     * Experience a machine made but has no room left to keep, given off as orbs on top of it; the part of a point that
     * does not make a whole one comes out by chance, as a furnace rounds its experience.
     */
    public static void overflowAsOrbs(ServerLevel level, BlockPos pos, int millibuckets) {
        if (millibuckets <= 0) {
            return;
        }
        int points = millibuckets / MB_PER_POINT;
        if (level.getRandom().nextInt(MB_PER_POINT) < millibuckets % MB_PER_POINT) {
            points++;
        }
        if (points > 0) {
            ExperienceOrb.award(level, Vec3.atBottomCenterOf(pos.above()), points);
        }
    }

    /** Points worth of fluid, rounded down. */
    public static int points(int millibuckets) {
        return millibuckets / MB_PER_POINT;
    }

    /** Saturating, because a big enough orb would otherwise wrap round and leave a tank holding nonsense. */
    public static int millibuckets(int points) {
        long millibuckets = (long) points * MB_PER_POINT;
        return (int) Math.min(millibuckets, Integer.MAX_VALUE);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private ExperienceFluids() {}
}
