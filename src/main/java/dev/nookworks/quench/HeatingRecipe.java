package dev.nookworks.quench;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * A furnace recipe (type minecraft:smelting, so a furnace and the Electric Furnace both run it) whose result is
 * stamped with the moment it came out of the fire: a red-hot ingot cools from then (see {@link Quenching}).
 */
public class HeatingRecipe extends SmeltingRecipe {
    // PORT: 26.3's cookingMapCodec / cookingStreamCodec are 1.21.1's SimpleCookingSerializer (Quenching.HEATING), the
    // same fields; 26.3's CommonInfo + CookingBookInfo are 1.21.1's group and category
    public HeatingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result,
            float experience, int cookingTime) {
        super(group, category, ingredient, result, experience, cookingTime);
    }

    /**
     * A red-hot ingot that has cooled heats again; one still hot does not (a loop through the fire would keep it hot) -
     * ours or an add-on's on our recipes.
     */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return super.matches(input, level) && !Quenching.isHot(input.item(), level.getGameTime())
                && !Quenching.stampedHot(input.item(), level.getGameTime());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return stamped(super.assemble(input, registries));
    }

    /** Stamped with the server's clock (one for every dimension); a client showing the recipe has none to stamp with. */
    static ItemStack stamped(ItemStack result) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? result : Quenching.stamp(result, server.overworld().getGameTime());
    }

    /** Ours, under smelting's type. */
    @Override
    public RecipeSerializer<?> getSerializer() {
        return Quenching.HEATING.get();
    }
}
