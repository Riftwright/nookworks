package dev.nookworks.oil;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.port.recipe.RecipeSerializers;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A shapeless recipe that leaves something of its first ingredient behind in the grid, as a cake leaves its buckets:
 * vanilla's sunflower into yellow dye, overridden so its four seeds stay when the petals are plucked (the user,
 * 2026-10-12: "バニラのレシピを改変して、作業台から種と染料が出るように"). The grid, the result and the recipe book are vanilla's.
 */
public class LeftoverRecipe implements CraftingRecipe {
    // PORT: 26.3's CommonInfo + CraftingBookInfo are 1.21.1's group and category, the fields its shapeless recipe reads
    public static final MapCodec<LeftoverRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(o -> o.group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(o -> o.category),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(o -> o.result),
            Ingredient.CODEC_NONEMPTY.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients),
            ItemStack.STRICT_CODEC.fieldOf("leftover").forGetter(o -> o.leftover)).apply(i, LeftoverRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeftoverRecipe> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, o -> o.group,
            CraftingBookCategory.STREAM_CODEC, o -> o.category,
            ItemStack.STREAM_CODEC, o -> o.result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
            ItemStack.STREAM_CODEC, o -> o.leftover,
            LeftoverRecipe::new);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nookworks.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LeftoverRecipe>> SERIALIZER =
            SERIALIZERS.register("crafting_shapeless_leftover", () -> RecipeSerializers.of(MAP_CODEC, STREAM_CODEC));

    private final String group;
    private final CraftingBookCategory category;
    private final ItemStack result;
    private final List<Ingredient> ingredients;
    private final ItemStack leftover;
    /** Vanilla's shapeless recipe of the same grid and result, for the matching, the result and the book's display. */
    private final ShapelessRecipe shapeless;

    public LeftoverRecipe(String group, CraftingBookCategory category, ItemStack result,
            List<Ingredient> ingredients, ItemStack leftover) {
        this.group = group;
        this.category = category;
        this.result = result;
        this.ingredients = ingredients;
        this.leftover = leftover;
        this.shapeless = new ShapelessRecipe(group, category, result,
                NonNullList.of(Ingredient.EMPTY, ingredients.toArray(Ingredient[]::new)));
    }

    /** What is left in the grid, one for each of the first ingredient. Public for JEI and tests. */
    public ItemStack leftover() {
        return this.leftover.copy();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.shapeless.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return this.shapeless.assemble(input, registries);
    }

    /** Whatever the items leave as vanilla's would, and the leftover where the first ingredient lay. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = CraftingRecipe.super.getRemainingItems(input);
        for (int slot = 0; slot < input.size(); slot++) {
            if (left.get(slot).isEmpty() && this.ingredients.getFirst().test(input.getItem(slot))) {
                left.set(slot, this.leftover.copy());
            }
        }
        return left;
    }

    // PORT: 26.3's display() and placement info are 1.21.1's ingredients, result and grid size, read by its recipe book
    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.shapeless.getIngredients();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.shapeless.getResultItem(registries);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return this.shapeless.canCraftInDimensions(width, height);
    }

    @Override
    public String getGroup() {
        return this.group;
    }

    @Override
    public CraftingBookCategory category() {
        return this.category;
    }

    @Override
    public RecipeSerializer<LeftoverRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
