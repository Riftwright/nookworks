package dev.nookworks.oil;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.fluid.KeptFluid;
import dev.nookworks.fluid.WadeFluidType;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Vegetable Oil, pressed from seeds, and the Biodiesel made from it: both lighter than water, and spilt they run as far
 * and as fast as water does. They catch from fire or lava and burn away (see {@link ModOil#setup}). A bottle is a
 * quarter of a bucket.
 */
public final class OilFluids {
    public static final int BOTTLE = 250;

    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Nookworks.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Nookworks.MODID);

    public static final Supplier<FluidType> TYPE = FLUID_TYPES.register("vegetable_oil", () -> new WadeFluidType(FluidType.Properties.create()
            .descriptionId("block.nookworks.vegetable_oil")
            .density(920).viscosity(1_500).temperature(293)
            .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(false)
            .motionScale(0.004)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.7F));

    public static final Supplier<FlowingFluid> SOURCE = FLUIDS.register("vegetable_oil", () -> new KeptFluid.Source(properties()));
    public static final Supplier<FlowingFluid> FLOWING = FLUIDS.register("flowing_vegetable_oil",
            () -> new KeptFluid.Flowing(properties(), false));

    public static final Supplier<FluidType> BIODIESEL_TYPE = FLUID_TYPES.register("biodiesel", () -> new WadeFluidType(FluidType.Properties.create()
            .descriptionId("block.nookworks.biodiesel")
            .density(880).viscosity(1_200).temperature(293)
            .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(false)
            .motionScale(0.005)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.72F));
    public static final Supplier<FlowingFluid> BIODIESEL = FLUIDS.register("biodiesel", () -> new KeptFluid.Source(biodiesel()));
    public static final Supplier<FlowingFluid> FLOWING_BIODIESEL = FLUIDS.register("flowing_biodiesel",
            () -> new KeptFluid.Flowing(biodiesel(), false));

    private static BaseFlowingFluid.Properties biodiesel() {
        return new BaseFlowingFluid.Properties(BIODIESEL_TYPE, BIODIESEL, FLOWING_BIODIESEL)
                .bucket(ModOil.BIODIESEL_BUCKET).block(ModOil.BIODIESEL_BLOCK)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5);
    }

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(TYPE, SOURCE, FLOWING)
                .bucket(ModOil.VEGETABLE_OIL_BUCKET).block(ModOil.VEGETABLE_OIL)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5);
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }

    private OilFluids() {}
}
