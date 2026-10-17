package dev.nookworks.quench;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

/** {@link HeatingRecipe} in a blast furnace: the same stamp, in half the time. */
public class BlastHeatingRecipe extends BlastingRecipe {
    public static final MapCodec<BlastHeatingRecipe> MAP_CODEC = cookingMapCodec(BlastHeatingRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, BlastHeatingRecipe> STREAM_CODEC = cookingStreamCodec(BlastHeatingRecipe::new);

    public BlastHeatingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient ingredient, ItemStackTemplate result,
            float experience, int cookingTime) {
        super(commonInfo, bookInfo, ingredient, result, experience, cookingTime);
    }

    /** A red-hot ingot that has cooled heats again; one still hot does not (a loop through the fire would keep it hot). */
    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return super.matches(input, level) && !Quenching.isHot(input.item(), level.getGameTime());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input) {
        return HeatingRecipe.stamped(super.assemble(input));
    }

    @SuppressWarnings("unchecked")
    @Override
    public RecipeSerializer<BlastingRecipe> getSerializer() {
        return (RecipeSerializer<BlastingRecipe>) (RecipeSerializer<?>) Quenching.BLAST_HEATING.get();
    }
}
