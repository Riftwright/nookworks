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
 * Vegetable Oil, pressed from seeds, the Biodiesel made from it, and the Methanol it is made with (distilled from wood
 * vinegar): all lighter than water, and spilt they run as far and as fast as water does. They catch from fire or lava
 * and burn away (see {@link ModOil#setup}). A bottle is a quarter of a bucket.
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

    /**
     * Methanol as a liquid (the user, 2026-11-07: 「メタノールは液体として扱いたい」): a thin, clear spirit, lighter than
     * water and as runny - a body moves in it as in water. Its bottle is the Bottle of Methanol the Distiller has always
     * made (nookworks:methanol, the item).
     */
    public static final Supplier<FluidType> METHANOL_TYPE = FLUID_TYPES.register("methanol", () -> new WadeFluidType(FluidType.Properties.create()
            .descriptionId("block.nookworks.methanol")
            .density(792).viscosity(600).temperature(293)
            .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(false)
            .motionScale(0.010)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.8F));
    public static final Supplier<FlowingFluid> METHANOL = FLUIDS.register("methanol", () -> new KeptFluid.Source(methanol()));
    public static final Supplier<FlowingFluid> FLOWING_METHANOL = FLUIDS.register("flowing_methanol",
            () -> new KeptFluid.Flowing(methanol(), false));

    /**
     * Wood vinegar as a liquid (the user, 2026-11-10: 「木酢液も液体として作って欲しい」): what the Sealed Retort chars out
     * of logs, mostly water - as thin as water, a clear amber-brown, heavier than the spirits and oils. Its bottle is the
     * Bottle of Wood Vinegar (nookworks:wood_vinegar, the item); it does not burn.
     */
    public static final Supplier<FluidType> WOOD_VINEGAR_TYPE = FLUID_TYPES.register("wood_vinegar", () -> new WadeFluidType(FluidType.Properties.create()
            .descriptionId("block.nookworks.wood_vinegar")
            .density(1_010).viscosity(1_000).temperature(293)
            .canSwim(true).canDrown(true).canPushEntity(true).supportsBoating(false)
            .motionScale(0.010)
            .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
            .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY), 0.8F));
    public static final Supplier<FlowingFluid> WOOD_VINEGAR = FLUIDS.register("wood_vinegar", () -> new KeptFluid.Source(woodVinegar()));
    public static final Supplier<FlowingFluid> FLOWING_WOOD_VINEGAR = FLUIDS.register("flowing_wood_vinegar",
            () -> new KeptFluid.Flowing(woodVinegar(), false));

    private static BaseFlowingFluid.Properties woodVinegar() {
        return new BaseFlowingFluid.Properties(WOOD_VINEGAR_TYPE, WOOD_VINEGAR, FLOWING_WOOD_VINEGAR)
                .bucket(ModOil.WOOD_VINEGAR_BUCKET).block(ModOil.WOOD_VINEGAR_BLOCK)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5);
    }

    private static BaseFlowingFluid.Properties methanol() {
        return new BaseFlowingFluid.Properties(METHANOL_TYPE, METHANOL, FLOWING_METHANOL)
                .bucket(ModOil.METHANOL_BUCKET).block(ModOil.METHANOL_BLOCK)
                .slopeFindDistance(4).levelDecreasePerBlock(1).tickRate(5);
    }

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
