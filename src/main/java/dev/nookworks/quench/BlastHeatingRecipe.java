package dev.nookworks.quench;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** {@link HeatingRecipe} in a blast furnace: the same stamp, in half the time. */
public class BlastHeatingRecipe extends BlastingRecipe {
    // PORT: 26.3's cookingMapCodec / cookingStreamCodec are 1.21.1's SimpleCookingSerializer (Quenching.BLAST_HEATING)
    public BlastHeatingRecipe(String group, CookingBookCategory category, Ingredient ingredient, ItemStack result,
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
        return HeatingRecipe.stamped(super.assemble(input, registries));
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Quenching.BLAST_HEATING.get();
    }
}
