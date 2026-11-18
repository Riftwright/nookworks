package dev.nookworks.port.recipe;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * Minecraft 1.21.1 has no {@code minecraft:crafting_transmute}, the recipe later versions use to turn one item into
 * another while keeping everything stored on it (a Storage Barrel upgraded with its contents inside). This is that
 * recipe for the port, registered as {@code nookworks:crafting_transmute} and reading the same fields: one
 * {@code input}, {@code material} one to eight times ({@code material_count}, one unless said), a {@code result}
 * whose item takes the input's components, and {@code add_material_count_to_result}.
 */
public class TransmuteRecipe implements CraftingRecipe {
    private static final MinMaxBounds.Ints ONE = MinMaxBounds.Ints.exactly(1);
    /** A material count outside one to eight is a mistake in the file, as the later versions say it is. */
    private static final Codec<MinMaxBounds.Ints> MATERIAL_COUNT = MinMaxBounds.Ints.CODEC.validate(bounds ->
            bounds.min().orElse(1) >= 1 && bounds.max().orElse(8) <= 8
                    ? com.mojang.serialization.DataResult.success(bounds)
                    : com.mojang.serialization.DataResult.error(() -> "material_count must lie within 1 to 8"));

    /** What comes out: an item (the input's own when none is named), how many, and components set on top of its own. */
    public record Result(Optional<Holder<Item>> item, int count, DataComponentPatch components) {
        private static final MapCodec<Result> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("id").forGetter(Result::item),
                ExtraCodecs.intRange(1, 99).optionalFieldOf("count", 1).forGetter(Result::count),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(Result::components)
        ).apply(i, Result::new));
        /** Written out in full, or as the item's id alone. */
        static final Codec<Result> CODEC = Codec.withAlternative(MAP_CODEC.codec(),
                BuiltInRegistries.ITEM.holderByNameCodec(), item -> new Result(Optional.of(item), 1, DataComponentPatch.EMPTY));
        static final StreamCodec<RegistryFriendlyByteBuf, Result> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(net.minecraft.core.registries.Registries.ITEM)), Result::item,
                ByteBufCodecs.VAR_INT, Result::count,
                DataComponentPatch.STREAM_CODEC, Result::components,
                Result::new);

        /**
         * The result made from {@code input}: the result's item with all the input carried, then the result's own
         * components over them - where both set one (a name, a colour) the recipe's wins, as 26.3's
         * {@code ItemStackTemplate.apply} has it. (The other way round until the stand-ins' review of 2026-11-17.)
         */
        public ItemStack make(ItemStack input, int count) {
            ItemStack made = new ItemStack(this.item.orElse(input.getItemHolder()), count, input.getComponentsPatch());
            made.applyComponents(this.components);
            return made;
        }
    }

    public static final MapCodec<TransmuteRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("group", "").forGetter(r -> r.group),
            CraftingBookCategory.CODEC.fieldOf("category").orElse(CraftingBookCategory.MISC).forGetter(r -> r.category),
            Ingredient.CODEC_NONEMPTY.fieldOf("input").forGetter(r -> r.input),
            Ingredient.CODEC_NONEMPTY.fieldOf("material").forGetter(r -> r.material),
            MATERIAL_COUNT.optionalFieldOf("material_count", ONE).forGetter(r -> r.materialCount),
            Result.CODEC.fieldOf("result").forGetter(r -> r.result),
            Codec.BOOL.optionalFieldOf("add_material_count_to_result", false).forGetter(r -> r.addMaterialCountToResult)
    ).apply(i, TransmuteRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> STREAM_CODEC = StreamCodec.of(
            (buffer, recipe) -> {
                buffer.writeUtf(recipe.group);
                buffer.writeEnum(recipe.category);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.input);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.material);
                buffer.writeVarInt(recipe.minMaterialCount());
                buffer.writeVarInt(recipe.maxMaterialCount());
                Result.STREAM_CODEC.encode(buffer, recipe.result);
                buffer.writeBoolean(recipe.addMaterialCountToResult);
            },
            buffer -> {
                String group = buffer.readUtf();
                CraftingBookCategory category = buffer.readEnum(CraftingBookCategory.class);
                Ingredient input = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                Ingredient material = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                int min = buffer.readVarInt();
                int max = buffer.readVarInt();
                Result result = Result.STREAM_CODEC.decode(buffer);
                boolean add = buffer.readBoolean();
                return new TransmuteRecipe(group, category, input, material, MinMaxBounds.Ints.between(min, max), result, add);
            });

    public static final RecipeSerializer<TransmuteRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override
        public MapCodec<TransmuteRecipe> codec() {
            return MAP_CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, TransmuteRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    };

    private final String group;
    private final CraftingBookCategory category;
    private final Ingredient input;
    private final Ingredient material;
    private final MinMaxBounds.Ints materialCount;
    private final Result result;
    private final boolean addMaterialCountToResult;

    public TransmuteRecipe(String group, CraftingBookCategory category, Ingredient input, Ingredient material,
            MinMaxBounds.Ints materialCount, Result result, boolean addMaterialCountToResult) {
        this.group = group;
        this.category = category;
        this.input = input;
        this.material = material;
        this.materialCount = materialCount;
        this.result = result;
        this.addMaterialCountToResult = addMaterialCountToResult;
    }

    private int minMaterialCount() {
        return this.materialCount.min().orElse(1);
    }

    private int maxMaterialCount() {
        return this.materialCount.max().orElse(8);
    }

    private int resultSize(int materials) {
        return this.addMaterialCountToResult ? materials + this.result.count() : this.result.count();
    }

    @Override
    public boolean matches(CraftingInput grid, Level level) {
        int min = this.minMaterialCount();
        int max = this.maxMaterialCount();
        if (grid.ingredientCount() < min + 1 || grid.ingredientCount() > max + 1) {
            return false;
        }
        ItemStack found = null;
        int materials = 0;
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (this.input.test(stack)) {
                if (found != null) {
                    return false;
                }
                found = stack;
            } else if (!this.material.test(stack) || ++materials > max) {
                return false;
            }
        }
        if (found == null || !this.materialCount.matches(materials)) {
            return false;
        }
        if (this.resultSize(materials) != 1) {
            return true;
        }
        // one thing in, the very same thing out: no recipe (a dye on what is already that colour)
        ItemStack made = this.result.make(found, this.result.count());
        return !made.isEmpty() && !ItemStack.isSameItemSameComponents(found, made);
    }

    @Override
    public ItemStack assemble(CraftingInput grid, HolderLookup.Provider registries) {
        ItemStack found = ItemStack.EMPTY;
        int materials = 0;
        for (int slot = 0; slot < grid.size(); slot++) {
            ItemStack stack = grid.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (this.input.test(stack)) {
                found = stack;
            } else if (this.material.test(stack)) {
                materials++;
            }
        }
        if (found.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return this.result.make(found, this.result.count() + (this.addMaterialCountToResult ? materials : 0));
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= this.minMaterialCount() + 1;
    }

    /** For the recipe book and recipe viewers: the plain result, with nothing of an input on it. */
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        Holder<Item> item = this.result.item().orElseGet(() -> {
            ItemStack[] inputs = this.input.getItems();
            return inputs.length > 0 ? inputs[0].getItemHolder() : ItemStack.EMPTY.getItemHolder();
        });
        return new ItemStack(item, this.resultSize(this.minMaterialCount()), this.result.components());
    }

    /** The input and as many of the material as the recipe needs at least. */
    @Override
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> ingredients = NonNullList.create();
        ingredients.add(this.input);
        for (int n = 0; n < this.minMaterialCount(); n++) {
            ingredients.add(this.material);
        }
        return ingredients;
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
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER;
    }
}
