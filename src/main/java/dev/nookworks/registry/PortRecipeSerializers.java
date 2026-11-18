package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.port.recipe.TransmuteRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Recipe serializers the 1.21.1 port needs of its own (registered beside the mod's others, from {@link Nookworks}).
 * PORT: 26.3's vanilla {@code minecraft:crafting_transmute} (a crafting recipe that turns one item into another and
 * keeps its components) does not exist in 1.21.1; our recipes of that type are written as
 * {@code nookworks:crafting_transmute}, read by {@link TransmuteRecipe}.
 */
public final class PortRecipeSerializers {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nookworks.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<TransmuteRecipe>> CRAFTING_TRANSMUTE =
            SERIALIZERS.register("crafting_transmute", () -> TransmuteRecipe.SERIALIZER);

    private PortRecipeSerializers() {}
}
