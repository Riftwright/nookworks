package dev.nookworks.oil;

import java.util.List;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.NormalCraftingRecipe;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * A shapeless recipe that leaves something of its first ingredient behind in the grid, as a cake leaves its buckets:
 * vanilla's sunflower into yellow dye, overridden so its four seeds stay when the petals are plucked (the user,
 * 2026-10-12: "バニラのレシピを改変して、作業台から種と染料が出るように"). The grid, the result and the recipe book are vanilla's.
 */
public class LeftoverRecipe extends NormalCraftingRecipe {
    public static final MapCodec<LeftoverRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Recipe.CommonInfo.MAP_CODEC.forGetter(o -> o.commonInfo),
            CraftingRecipe.CraftingBookInfo.MAP_CODEC.forGetter(o -> o.bookInfo),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(o -> o.result),
            Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(o -> o.ingredients),
            ItemStackTemplate.CODEC.fieldOf("leftover").forGetter(o -> o.leftover)).apply(i, LeftoverRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LeftoverRecipe> STREAM_CODEC = StreamCodec.composite(
            Recipe.CommonInfo.STREAM_CODEC, o -> o.commonInfo,
            CraftingRecipe.CraftingBookInfo.STREAM_CODEC, o -> o.bookInfo,
            ItemStackTemplate.STREAM_CODEC, o -> o.result,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list()), o -> o.ingredients,
            ItemStackTemplate.STREAM_CODEC, o -> o.leftover,
            LeftoverRecipe::new);

    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nookworks.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<LeftoverRecipe>> SERIALIZER =
            SERIALIZERS.register("crafting_shapeless_leftover", () -> new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));

    private final ItemStackTemplate result;
    private final List<Ingredient> ingredients;
    private final ItemStackTemplate leftover;
    /** Vanilla's shapeless recipe of the same grid and result, for the matching, the result and the book's display. */
    private final ShapelessRecipe shapeless;

    public LeftoverRecipe(Recipe.CommonInfo commonInfo, CraftingRecipe.CraftingBookInfo bookInfo, ItemStackTemplate result,
            List<Ingredient> ingredients, ItemStackTemplate leftover) {
        super(commonInfo, bookInfo);
        this.result = result;
        this.ingredients = ingredients;
        this.leftover = leftover;
        this.shapeless = new ShapelessRecipe(commonInfo, bookInfo, result, ingredients);
    }

    /** What is left in the grid, one for each of the first ingredient. Public for JEI and tests. */
    public ItemStack leftover() {
        return this.leftover.create();
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return this.shapeless.matches(input, level);
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return this.shapeless.assemble(input);
    }

    /** Whatever the items leave as vanilla's would, and the leftover where the first ingredient lay. */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> left = CraftingRecipe.defaultCraftingReminder(input);
        for (int slot = 0; slot < input.size(); slot++) {
            if (left.get(slot).isEmpty() && this.ingredients.getFirst().test(input.getItem(slot))) {
                left.set(slot, this.leftover.create());
            }
        }
        return left;
    }

    @Override
    public List<RecipeDisplay> display() {
        return this.shapeless.display();
    }

    @Override
    protected PlacementInfo createPlacementInfo() {
        return PlacementInfo.create(this.ingredients);
    }

    @Override
    public RecipeSerializer<LeftoverRecipe> getSerializer() {
        return SERIALIZER.get();
    }
}
