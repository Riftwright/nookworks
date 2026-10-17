package dev.nookworks.quench;

import com.mojang.serialization.MapCodec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
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
    public static final MapCodec<HeatingRecipe> MAP_CODEC = cookingMapCodec(HeatingRecipe::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, HeatingRecipe> STREAM_CODEC = cookingStreamCodec(HeatingRecipe::new);

    public HeatingRecipe(CommonInfo commonInfo, CookingBookInfo bookInfo, Ingredient ingredient, ItemStackTemplate result,
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
        return stamped(super.assemble(input));
    }

    /** Stamped with the server's clock (one for every dimension); a client showing the recipe has none to stamp with. */
    static ItemStack stamped(ItemStack result) {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? result : Quenching.stamp(result, server.overworld().getGameTime());
    }

    /** Ours, under smelting's type: the cast only names the serializer's type as the method was declared with. */
    @SuppressWarnings("unchecked")
    @Override
    public RecipeSerializer<SmeltingRecipe> getSerializer() {
        return (RecipeSerializer<SmeltingRecipe>) (RecipeSerializer<?>) Quenching.HEATING.get();
    }
}
