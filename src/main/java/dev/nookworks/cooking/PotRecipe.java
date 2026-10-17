package dev.nookworks.cooking;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.oil.OilFluids;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Something the Iron Pot cooks: these ingredients in {@code amount} mB of water (boiled: rice, soup) or oil (fried or
 * stir-fried), heated for {@code time} ticks, make {@code result}. A pot holding several times the ingredients (and the
 * liquid) cooks several batches at once. A dish with a {@code container} is ladled out into it (a bowl, a glass
 * bottle) a serving at a time; anything else is taken out by hand, all at once.
 * <p>
 * An ordinary recipe of its own type, {@code nookworks:pot} (the user, 2026-10-01): a data pack's /reload changes them,
 * and KubeJS, CraftTweaker and the like add and take them away as any other recipe. Ours are in
 * {@code data/nookworks/recipe/pot/}:
 * <pre>{"type": "nookworks:pot", "ingredients": [{"ingredient": "nookworks:white_rice", "count": 1}], "liquid": "water",
 *  "amount": 250, "time": 300, "container": "minecraft:bowl", "result": {"id": "nookworks:cooked_rice", "count": 1}}</pre>
 * The server sends them to each player on joining and on a /reload, for JEI and for a client's own guesses.
 */
public record PotRecipe(List<Part> ingredients, Liquid liquid, int amount, int time, Optional<Holder<Item>> container,
        Result result) implements Recipe<PotRecipe.Input> {
    /** What the pot holds: its ingredients, and how much of which liquid. */
    public record Input(List<ItemStack> contents, @Nullable Liquid liquid, int held) implements RecipeInput {
        @Override
        public ItemStack getItem(int index) {
            return this.contents.get(index);
        }

        @Override
        public int size() {
            return this.contents.size();
        }
    }

    /** What the pot cooks in. */
    public enum Liquid implements StringRepresentable {
        WATER("water"),
        OIL("oil");

        public static final Codec<Liquid> CODEC = StringRepresentable.fromEnum(Liquid::values);
        private final String name;

        Liquid(String name) {
            this.name = name;
        }

        public Fluid fluid() {
            return this == WATER ? Fluids.WATER : OilFluids.SOURCE.get();
        }

        /** The liquid this fluid is, or null for one the pot does not take. */
        public static @Nullable Liquid of(Fluid fluid) {
            return fluid == Fluids.WATER ? WATER : fluid == OilFluids.SOURCE.get() ? OIL : null;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }

    /**
     * What a batch makes. An item and a count rather than an ItemStack: recipes are read with the registries, before
     * items have their components, when a stack cannot yet be built.
     */
    public record Result(Holder<Item> item, int count) {
        public static final Codec<Result> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(Result::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Result::count)).apply(i, Result::new));

        /** The dish from {@code batches} batches. */
        public ItemStack stack(int batches) {
            return new ItemStack(this.item, Math.min(Item.ABSOLUTE_MAX_STACK_SIZE, this.count * batches));
        }
    }

    /** One ingredient and how many of it a batch takes. */
    public record Part(Ingredient ingredient, int count) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(Part::ingredient),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Part::count)).apply(i, Part::new));
    }

    public static final MapCodec<PotRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Part.CODEC.listOf(1, 8).fieldOf("ingredients").forGetter(PotRecipe::ingredients),
            Liquid.CODEC.fieldOf("liquid").forGetter(PotRecipe::liquid),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amount", 0).forGetter(PotRecipe::amount),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time", 200).forGetter(PotRecipe::time),
            BuiltInRegistries.ITEM.holderByNameCodec().optionalFieldOf("container").forGetter(PotRecipe::container),
            Result.CODEC.fieldOf("result").forGetter(PotRecipe::result)).apply(i, PotRecipe::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PotRecipe> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(MAP_CODEC.codec());

    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Nookworks.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Nookworks.MODID);
    public static final DeferredRegister<RecipeBookCategory> BOOK_CATEGORIES = DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, Nookworks.MODID);
    public static final DeferredHolder<RecipeType<?>, RecipeType<PotRecipe>> TYPE = TYPES.register("pot", () -> RecipeType.simple(Nookworks.id("pot")));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<PotRecipe>> SERIALIZER =
            SERIALIZERS.register("pot", () -> new RecipeSerializer<>(MAP_CODEC, STREAM_CODEC));
    /** The pot's own corner of the recipe book, where nothing of it shows: it cooks by its contents, not a grid. */
    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> BOOK_CATEGORY = BOOK_CATEGORIES.register("iron_pot", RecipeBookCategory::new);

    /** The pot recipes the server last sent: what a client knows of them (for JEI, and to guess what goes in). */
    private static volatile List<RecipeHolder<PotRecipe>> received = List.of();

    // ---- as a recipe ----

    @Override
    public boolean matches(Input input, Level level) {
        return this.batches(input.contents(), input.liquid(), input.held()) > 0;
    }

    @Override
    public ItemStack assemble(Input input) {
        return this.result.stack(this.batches(input.contents(), input.liquid(), input.held()));
    }

    /** Worked by its own logic (what is in the pot), as vanilla's special recipes are: nothing to lay out in a grid. */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public RecipeSerializer<PotRecipe> getSerializer() {
        return SERIALIZER.get();
    }

    @Override
    public RecipeType<PotRecipe> getType() {
        return TYPE.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return BOOK_CATEGORY.get();
    }

    /**
     * How many batches of this recipe {@code contents} make in {@code held} mB of {@code liquid}, or 0 when they do not:
     * the liquid has to be the recipe's, and every item in the pot has to belong to the recipe (a stray one spoils
     * nothing, but the pot will not guess what it is for).
     */
    public int batches(List<ItemStack> contents, @Nullable Liquid liquid, int held) {
        if (liquid != this.liquid) {
            return 0;
        }
        int[] have = new int[this.ingredients.size()];
        for (ItemStack stack : contents) {
            if (stack.isEmpty()) {
                continue;
            }
            int part = this.partFor(stack);
            if (part < 0) {
                return 0;
            }
            have[part] += stack.getCount();
        }
        int batches = this.amount > 0 ? held / this.amount : Integer.MAX_VALUE;
        for (int p = 0; p < have.length; p++) {
            batches = Math.min(batches, have[p] / this.ingredients.get(p).count());
        }
        if (batches == Integer.MAX_VALUE) {
            return 0;
        }
        // no more at once than a dish of 99 (the most a stack is saved with): what is over waits for the next
        return Math.min(batches, Math.max(1, Item.ABSOLUTE_MAX_STACK_SIZE / Math.max(1, this.result.count())));
    }

    /** Which part {@code stack} counts towards, or -1. */
    public int partFor(ItemStack stack) {
        for (int p = 0; p < this.ingredients.size(); p++) {
            if (this.ingredients.get(p).ingredient().test(stack)) {
                return p;
            }
        }
        return -1;
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Sync {
        /** Every player gets the pot recipes, on joining and on a /reload: JEI shows them, and a client can tell what goes in. */
        @SubscribeEvent
        static void send(OnDatapackSyncEvent event) {
            event.sendRecipes(TYPE.get());
        }

        private Sync() {}
    }

    /** What a client was sent ({@code RecipesReceivedEvent}, client side). */
    public static void received(Collection<RecipeHolder<PotRecipe>> recipes) {
        received = List.copyOf(recipes);
    }

    /** Every pot recipe: the server's own, or on a client those the server sent. */
    public static Collection<RecipeHolder<PotRecipe>> all(Level level) {
        return level instanceof ServerLevel server ? server.recipeAccess().recipeMap().byType(TYPE.get()) : received;
    }

    /** The first recipe the contents cook as, with at least one batch; null if none. */
    public static @Nullable RecipeHolder<PotRecipe> find(Level level, List<ItemStack> contents, @Nullable Liquid liquid, int held) {
        for (RecipeHolder<PotRecipe> holder : all(level)) {
            if (holder.value().batches(contents, liquid, held) > 0) {
                return holder;
            }
        }
        return null;
    }

    /** The recipe {@code id} names, or null. */
    public static @Nullable PotRecipe byId(Level level, Identifier id) {
        for (RecipeHolder<PotRecipe> holder : all(level)) {
            if (holder.id().identifier().equals(id)) {
                return holder.value();
            }
        }
        return null;
    }

    /** Whether any recipe takes this item: only those go in. */
    public static boolean isIngredient(Level level, ItemStack stack) {
        return all(level).stream().anyMatch(holder -> holder.value().partFor(stack) >= 0);
    }
}
