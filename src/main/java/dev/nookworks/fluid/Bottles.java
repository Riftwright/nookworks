package dev.nookworks.fluid;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.feature.Features;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Which bottle holds which liquid: a bottle is a quarter of a bucket ({@link #AMOUNT} mB), poured into a tank and drawn
 * back out of one by a glass bottle. Water's is vanilla's Water Bottle; ours are the bottles of vegetable oil, biodiesel,
 * methanol, wood vinegar and liquid experience. Everything of ours that fills or pours a bottle asks here: the Copper
 * Tank ({@link CopperTankBlock#bottleOf} / {@link CopperTankBlock#filledBottle}, which read this), a machine screen's
 * gauges, a dispenser facing a tank.
 * <p>
 * An add-on with bottles of its own liquids registers them with {@link #register(Fluid, Item)} at setup (on the main
 * thread: in {@code FMLCommonSetupEvent.enqueueWork}), and they pour and draw as ours do - a dispenser holding one is
 * given the same behaviour as ours unless the bottle already has one.
 */
public final class Bottles {
    /** What a bottle holds, in mB: a quarter of a bucket. */
    public static final int AMOUNT = OilFluids.BOTTLE;

    /** Ours, looked up when first asked (the registries are not filled when this class is loaded). */
    private static final List<Pair> OURS = List.of(
            new Pair(OilFluids.SOURCE, ModOil.VEGETABLE_OIL_BOTTLE::get),
            new Pair(OilFluids.BIODIESEL, ModOil.BIODIESEL_BOTTLE::get),
            new Pair(OilFluids.METHANOL, ModOil.METHANOL::get),
            new Pair(OilFluids.WOOD_VINEGAR, ModOil.WOOD_VINEGAR::get),
            new Pair(ExperienceFluids.SOURCE, ModItems.LIQUID_EXPERIENCE_BOTTLE::get));

    private record Pair(Supplier<? extends Fluid> fluid, Supplier<? extends Item> bottle) {}

    /** Add-ons' bottles, both ways. */
    private static final Map<Fluid, Item> BOTTLE_OF = new ConcurrentHashMap<>();
    private static final Map<Item, Fluid> FLUID_OF = new ConcurrentHashMap<>();

    private Bottles() {}

    /**
     * Registers {@code bottle} as holding {@link #AMOUNT} mB of {@code fluid} (its source fluid): a glass bottle drawn from
     * a tank of it becomes {@code bottle}, and {@code bottle} poured into a tank leaves a glass bottle. A dispenser holding
     * {@code bottle} pours it into a tank in front, unless {@code bottle} has a dispenser behaviour already.
     * Call at setup, on the main thread.
     */
    public static void register(Fluid fluid, Item bottle) {
        BOTTLE_OF.put(fluid, bottle);
        FLUID_OF.put(bottle, fluid);
        LiquidDispensing.bottle(bottle);
    }

    /** The liquid a filled bottle pours (a Water Bottle's water, ours, an add-on's), or null for anything else. */
    public static @Nullable Fluid fluidOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        if (stack.is(Items.POTION) && stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
            return Fluids.WATER;
        }
        for (Pair pair : OURS) {
            if (stack.is(pair.bottle().get())) {
                return pair.fluid().get();
            }
        }
        return FLUID_OF.get(stack.getItem());
    }

    /**
     * A bottle of {@code fluid}, or empty for a liquid there is no bottle of (or whose bottle a pack switched off). Any
     * liquid in #c:experience fills a Liquid Experience Bottle.
     */
    public static ItemStack filled(Fluid fluid) {
        if (fluid.isSame(Fluids.WATER)) {
            return PotionContents.createItemStack(Items.POTION, Potions.WATER);
        }
        for (Pair pair : OURS) {
            if (fluid.isSame(pair.fluid().get())) {
                return enabled(new ItemStack(pair.bottle().get()));
            }
        }
        if (fluid.defaultFluidState().is(ExperienceFluids.EXPERIENCE)) {
            return enabled(new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()));
        }
        for (var entry : BOTTLE_OF.entrySet()) {
            if (fluid.isSame(entry.getKey())) {
                return new ItemStack(entry.getValue());
            }
        }
        return ItemStack.EMPTY;
    }

    /** Every bottle of ours and the add-ons' (not the Water Bottle), for a dispenser's behaviour. */
    static List<Item> all() {
        List<Item> all = new java.util.ArrayList<>();
        OURS.forEach(pair -> all.add(pair.bottle().get()));
        all.addAll(FLUID_OF.keySet());
        return all;
    }

    private static ItemStack enabled(ItemStack stack) {
        return Features.enabled(stack) ? stack : ItemStack.EMPTY;
    }
}
