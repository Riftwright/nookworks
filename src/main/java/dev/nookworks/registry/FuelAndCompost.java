package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Compostable;
import net.minecraft.world.item.component.CookingFuel;
import net.minecraft.world.level.storage.loot.providers.number.floats.ContextFloatProviders;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Which of our items burn in a furnace, and which a composter takes. In 26.3 both are components an item carries
 * (minecraft:cooking_fuel, minecraft:compostable) against vanilla's named amounts, as its own items are given them;
 * until then they were NeoForge's furnace_fuels and compostables data maps, and for the wood the vanilla tags it is in.
 * The amounts are the ones those gave: the wood as vanilla's own pieces of each kind, a support as a fence, a bucket of
 * oil or biodiesel 16,000 ticks (a block of coal's), the compost chances 30, 50, 65 and 85 in a hundred.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class FuelAndCompost {
    private FuelAndCompost() {}

    @SubscribeEvent
    static void defaults(ModifyDefaultComponentsEvent event) {
        burns(event, ContextIntProviders.COOKING_TIME_WOOD_BLOCKS, "dawnwood_log", "dawnwood_wood", "stripped_dawnwood_log",
                "stripped_dawnwood_wood", "dawnwood_planks", "dawnwood_stairs", "dawnwood_fence", "dawnwood_fence_gate",
                "dawnwood_trapdoor", "dawnwood_pressure_plate", "dawnwood_shelf");
        burns(event, ContextIntProviders.COOKING_TIME_WOOD_SLABS, "dawnwood_slab");
        burns(event, ContextIntProviders.COOKING_TIME_WOOD_ITEMS_LARGE, "dawnwood_door", "dawnwood_sign");
        burns(event, ContextIntProviders.COOKING_TIME_HANGING_SIGNS, "dawnwood_hanging_sign");
        burns(event, ContextIntProviders.COOKING_TIME_BOATS, "dawnwood_boat", "dawnwood_chest_boat");
        burns(event, ContextIntProviders.COOKING_TIME_WOOD_ITEMS_EXTRA_SMALL, "dawnwood_button");
        burns(event, ContextIntProviders.COOKING_TIME_DRY_PLANTS, "rare_seed"); // a sapling's
        for (String wood : ModWoods.SUPPORT_WOODS) {
            if (!wood.equals("crimson") && !wood.equals("warped")) { // #nookworks:burnable_supports
                burns(event, ContextIntProviders.COOKING_TIME_WOOD_BLOCKS, wood + "_support");
            }
        }
        burns(event, ContextIntProviders.COOKING_TIME_COAL_BLOCK, "vegetable_oil_bucket", "biodiesel_bucket");

        compost(event, ContextIntProviders.COMPOSTABLE_LOW, "dawnwood_leaves", "rare_seed", "brown_rice", "white_rice",
                "corn_kernels", "sunflower_seeds", "end_seed");
        compost(event, ContextIntProviders.COMPOSTABLE_LOW_MEDIUM, "oil_cake", "rice_bran", "bread_dough", "risen_dough",
                "udon_dough", "raw_udon", "pancake_batter");
        compost(event, ContextIntProviders.COMPOSTABLE_MEDIUM, "corn", "onion");
        compost(event, ContextIntProviders.COMPOSTABLE_MEDIUM_HIGH, "rice_bale", "grilled_corn", "pancake", "donut",
                "fried_egg", "boiled_egg", "tamagoyaki");
    }

    private static void burns(ModifyDefaultComponentsEvent event, ResourceKey<ContextIntProvider> time, String... items) {
        CookingFuel fuel = new CookingFuel(time, ContextFloatProviders.COOKING_DEFAULT_SPEED_MULTIPLIER);
        for (String name : items) {
            event.modify(item(name), (components, registries, it) ->
                    components.set(DataComponents.COOKING_FUEL, fuel));
        }
    }

    private static void compost(ModifyDefaultComponentsEvent event, ResourceKey<ContextIntProvider> layers, String... items) {
        Compostable compostable = new Compostable(layers);
        for (String name : items) {
            event.modify(item(name), (components, registries, it) ->
                    components.set(DataComponents.COMPOSTABLE, compostable));
        }
    }

    /** Ours by name: a name that is no item is a mistake here, not something to pass over. */
    private static Item item(String name) {
        return BuiltInRegistries.ITEM.getOptional(Nookworks.id(name))
                .orElseThrow(() -> new IllegalStateException("no item " + name));
    }
}
