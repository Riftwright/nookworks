package dev.nookworks.mercury;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.WadeFluidType;
import dev.nookworks.fluid.KeptFluid;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.PathType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Liquid mercury: heavy (13.5x water), slow-flowing, silvery and toxic to touch (see {@link MercuryBlock}).
 * Only Nookworks' iron tanks and pipes move it; it is deliberately not offered to other mods' fluid handlers.
 */
public final class MercuryFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Nookworks.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Nookworks.MODID);

    public static final Supplier<FluidType> TYPE = FLUID_TYPES.register("mercury", () -> new WadeFluidType(FluidType.Properties.create()
            .descriptionId("block.nookworks.mercury")
            .density(13_546).viscosity(1_550).temperature(293)
            .canSwim(false).canDrown(false).canPushEntity(true).supportsBoating(false)
            .pathType(PathType.DAMAGE_CAUTIOUS).adjacentPathType(null)
            .motionScale(0.002)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA), 0.5F));

    public static final Supplier<FlowingFluid> SOURCE = FLUIDS.register("mercury", () -> new KeptFluid.Source(properties()));
    public static final Supplier<FlowingFluid> FLOWING = FLUIDS.register("flowing_mercury", () -> new KeptFluid.Flowing(properties(), true));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
                .bucket(ModItems.MERCURY_BUCKET).block(ModBlocks.MERCURY)
                .slopeFindDistance(2).levelDecreasePerBlock(2).tickRate(15).explosionResistance(100.0F);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private MercuryFluids() {}
}
