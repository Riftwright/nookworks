package dev.nookworks.compat.farmersdelight;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.compat.RecipeOverlap;
import dev.nookworks.cooking.PotRecipe;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Farmer's Delight's kitchen and ours as one (the owner, 2026-11-15: 鍋などの料理も、FDの鍋やコンロにも対応させてください。
 * 料理も、矛盾なく作れるようにお願いします). With Farmer's Delight installed, once for each set of recipes the server loads
 * (at start, and after each /reload, before the recipes go to the players):
 * <ol>
 * <li>a recipe of any mod that asks for one of two twin foods by its id (our Bowl of Rice, its Cooked Rice) takes
 * either ({@link KitchenSettings#twins});</li>
 * <li>where a furnace, smoker, blast furnace or campfire recipe of each mod takes the same thing for its own twin (an
 * egg: our Fried Egg, its Fried Egg), the one of the mod not preferred stands down;</li>
 * <li>what the owner settled holds: with the flour bakery on, its cake of wheat stands down (ours takes its milk);</li>
 * <li>every dish of its Cooking Pot is an Iron Pot recipe too (250 mB of water a serving; the few the settings name, our
 * Frying Pan's, in its oil), and every dish of our Iron Pot and Frying Pan a Cooking Pot recipe (no liquid: its pot has
 * none), so either pot makes either mod's dishes, a pack's and an add-on's included.</li>
 * </ol>
 * No crossed recipe takes the same things in the same vessel as one already there for something else
 * ({@link RecipeOverlap}). They are put into the recipe manager itself, so players and recipe viewers get them with
 * the rest. Each recipe is read and made through its own serializer's codec, by id, so no class of Farmer's Delight's is
 * linked; a recipe that will not convert is left as it is. Run by {@link dev.nookworks.compat.RecipeCrossings}, in its
 * order: after the shared materials, before Create's machines (which take the twins from {@link #twins()}).
 */
public final class FdKitchen {
    /** The path every recipe made here begins with: {@code nookworks:farmersdelight/pot/...} or {@code .../cooking_pot/...}. */
    public static final String PREFIX = "farmersdelight/";
    /** The two folders the crossed recipes are put in (our own data files for its Cutting Board sit beside them). */
    private static final List<String> MADE = List.of(PREFIX + "pot/", PREFIX + "cooking_pot/");

    /** Whether {@code id} is one of the recipes made here. */
    public static boolean made(ResourceLocation id) {
        return id.getNamespace().equals(Nookworks.MODID) && MADE.stream().anyMatch(id.getPath()::startsWith);
    }
    /** The recipe managers already done (a player joining is told the same recipes again). */
    private static final Set<RecipeManager> DONE = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile Report last = Report.EMPTY;
    private static volatile Map<Item, Item> twins = Map.of();

    /** What the last crossing did, for the log and the tests. */
    public record Report(List<ResourceLocation> widened, List<ResourceLocation> stoodDown, List<ResourceLocation> intoOurPot,
            List<ResourceLocation> intoTheirPot, List<String> leftOut) {
        static final Report EMPTY = new Report(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private FdKitchen() {}

    public static Report last() {
        return last;
    }

    /**
     * The twin of each item that has one, as the last crossing read them (empty without Farmer's Delight): Create's
     * crossing makes one Millstone recipe for both twins, as every other recipe naming one takes either.
     */
    public static Map<Item, Item> twins() {
        return twins;
    }

    /** As the recipes go out (to a player joining, to everyone after a /reload), once they are crossed. */
    public static void giveBook(OnDatapackSyncEvent event) {
        if (!FarmersDelight.LOADED) {
            return;
        }
        MinecraftServer server = event.getPlayerList().getServer();
        // our dishes in its Cooking Pot's recipe book: none of its advancements unlocks them, so each player is given
        // them as the recipes go out - quietly, before the book is sent on joining (no toast for each)
        List<RecipeHolder<?>> book = server.getRecipeManager().getRecipes().stream()
                .filter(holder -> made(holder.id()) && !(holder.value() instanceof PotRecipe)).toList();
        event.getRelevantPlayers().forEach(player -> book.forEach(player.getRecipeBook()::add));
    }

    /** Brings the two kitchens together in the server's recipes, once for each recipe manager. */
    public static synchronized void cross(MinecraftServer server) {
        if (!FarmersDelight.LOADED) {
            return;
        }
        RecipeManager manager = server.getRecipeManager();
        if (!DONE.add(manager)) {
            return;
        }
        long start = System.nanoTime();
        try {
            KitchenSettings settings = KitchenSettings.read(server.getResourceManager());
            Crossing crossing = new Crossing(server.registryAccess(), settings);
            twins = Map.copyOf(crossing.twins);
            List<RecipeHolder<?>> recipes = crossing.run(manager.getRecipes());
            manager.replaceRecipes(recipes);
            PotRecipe.forget();
            last = crossing.report();
            Nookworks.LOGGER.info("Farmer's Delight's kitchen and ours: {} recipes take either twin, {} stood down, {} of its dishes in our pots, "
                    + "{} of ours in its Cooking Pot, {} left out ({} ms)", last.widened().size(), last.stoodDown().size(),
                    last.intoOurPot().size(), last.intoTheirPot().size(), last.leftOut().size(), (System.nanoTime() - start) / 1_000_000);
            for (String why : last.leftOut()) {
                Nookworks.LOGGER.debug("Not crossed: {}", why);
            }
        } catch (RuntimeException e) {
            Nookworks.LOGGER.error("Could not bring Farmer's Delight's kitchen and ours together; each stays as it is", e);
        }
    }

    /** One run over one set of recipes. */
    static final class Crossing {
        private final HolderLookup.Provider registries;
        private final DynamicOps<JsonElement> ops;
        private final KitchenSettings settings;
        private final Map<Item, Item> twins = new LinkedHashMap<>();
        private final @Nullable RecipeType<?> fdCooking = FarmersDelight.recipeType(FarmersDelight.COOKING);
        private final RecipeOverlap overlap = new RecipeOverlap();
        private final List<ResourceLocation> widened = new ArrayList<>();
        private final List<ResourceLocation> stoodDown = new ArrayList<>();
        private final List<ResourceLocation> intoOurPot = new ArrayList<>();
        private final List<ResourceLocation> intoTheirPot = new ArrayList<>();
        private final List<String> leftOut = new ArrayList<>();

        Crossing(HolderLookup.Provider registries, KitchenSettings settings) {
            this.registries = registries;
            this.ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            this.settings = settings;
            settings.twins().forEach((a, b) -> {
                if (BuiltInRegistries.ITEM.containsKey(a) && BuiltInRegistries.ITEM.containsKey(b)) {
                    this.twins.put(BuiltInRegistries.ITEM.get(a), BuiltInRegistries.ITEM.get(b));
                }
            });
        }

        Report report() {
            return new Report(List.copyOf(this.widened), List.copyOf(this.stoodDown), List.copyOf(this.intoOurPot),
                    List.copyOf(this.intoTheirPot), List.copyOf(this.leftOut));
        }

        List<RecipeHolder<?>> run(Iterable<RecipeHolder<?>> all) {
            Map<ResourceLocation, RecipeHolder<?>> recipes = new LinkedHashMap<>();
            for (RecipeHolder<?> holder : all) {
                if (!made(holder.id())) {
                    recipes.put(holder.id(), holder);
                }
            }
            this.widen(recipes);
            this.standDown(recipes);
            this.intoOurPot(recipes);
            this.intoTheirPot(recipes);
            return List.copyOf(recipes.values());
        }

        // ---- 1. twins ----

        private void widen(Map<ResourceLocation, RecipeHolder<?>> recipes) {
            if (this.twins.isEmpty()) {
                return;
            }
            Map<String, String> byId = new LinkedHashMap<>();
            this.twins.forEach((a, b) -> byId.put(BuiltInRegistries.ITEM.getKey(a).toString(), BuiltInRegistries.ITEM.getKey(b).toString()));
            for (var entry : recipes.entrySet()) {
                Recipe<?> recipe = entry.getValue().value();
                if (!this.asksForOneTwin(recipe)) {
                    continue;
                }
                try {
                    JsonElement json = Recipe.CODEC.encodeStart(this.ops, recipe).getOrThrow();
                    boolean[] changed = {false};
                    JsonElement wide = widen(json, byId, changed);
                    if (!changed[0]) {
                        continue;
                    }
                    Optional<Recipe<?>> read = Recipe.CODEC.parse(this.ops, wide).result();
                    if (read.isPresent()) {
                        entry.setValue(new RecipeHolder<>(entry.getKey(), read.get()));
                        this.widened.add(entry.getKey());
                    } else {
                        this.leftOut.add(entry.getKey() + ": would not read back asking for either twin");
                    }
                } catch (RuntimeException e) {
                    // not a recipe its own codec can write out: left as it is
                }
            }
        }

        /** Whether {@code recipe} may take one twin and not the other (a quick look before its file is read again). */
        private boolean asksForOneTwin(Recipe<?> recipe) {
            List<Ingredient> ingredients = recipe instanceof PotRecipe pot
                    ? pot.ingredients().stream().map(PotRecipe.Part::ingredient).toList() : recipe.getIngredients();
            if (ingredients.isEmpty()) {
                return !recipe.isSpecial() && !(recipe instanceof PotRecipe);
            }
            for (Ingredient ingredient : ingredients) {
                for (var pair : this.twins.entrySet()) {
                    if (ingredient.test(new ItemStack(pair.getKey())) && !ingredient.test(new ItemStack(pair.getValue()))) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * {@code json} with every {@code {"item": twin}} asking for either twin; {@code changed[0]} set if any was. In a
         * list of values (an ingredient written as an array, a tool's list) the twin is added beside it; anywhere else
         * the one value becomes NeoForge's compound ingredient of the two, which every ingredient codec reads - in a list
         * of ingredients ("ingredients", a compound's "children") an element stays one ingredient so.
         */
        static JsonElement widen(JsonElement json, Map<String, String> twins, boolean[] changed) {
            return widen(json, twins, changed, false);
        }

        private static JsonElement widen(JsonElement json, Map<String, String> twins, boolean[] changed, boolean ingredientList) {
            if (json.isJsonArray()) {
                JsonArray out = new JsonArray();
                for (JsonElement element : json.getAsJsonArray()) {
                    String other = twinOf(element, twins);
                    if (other != null && !ingredientList) {
                        out.add(element);
                        out.add(item(other));
                        changed[0] = true;
                    } else if (other != null) {
                        out.add(either(element, other));
                        changed[0] = true;
                    } else {
                        out.add(widen(element, twins, changed, false));
                    }
                }
                return out;
            }
            if (json.isJsonObject()) {
                String other = twinOf(json, twins);
                if (other != null) {
                    changed[0] = true;
                    return either(json, other);
                }
                JsonObject out = new JsonObject();
                json.getAsJsonObject().entrySet().forEach(e -> out.add(e.getKey(), widen(e.getValue(), twins, changed,
                        e.getKey().equals("ingredients") || e.getKey().equals("children"))));
                return out;
            }
            return json;
        }

        private static JsonObject either(JsonElement one, String other) {
            JsonObject compound = new JsonObject();
            compound.addProperty("type", "neoforge:compound");
            JsonArray children = new JsonArray();
            children.add(one);
            children.add(item(other));
            compound.add("children", children);
            return compound;
        }

        private static @Nullable String twinOf(JsonElement element, Map<String, String> twins) {
            if (!element.isJsonObject() || element.getAsJsonObject().size() != 1) {
                return null;
            }
            JsonElement item = element.getAsJsonObject().get("item");
            return item instanceof JsonPrimitive primitive && primitive.isString() ? twins.get(primitive.getAsString()) : null;
        }

        private static JsonObject item(String id) {
            JsonObject object = new JsonObject();
            object.addProperty("item", id);
            return object;
        }

        // ---- 2 and 3. what stands down ----

        private void standDown(Map<ResourceLocation, RecipeHolder<?>> recipes) {
            List<RecipeOverlap.Entry> stations = RecipeOverlap.entries(recipes.values(), this.registries).stream()
                    .filter(e -> e.place().startsWith("minecraft:")).toList();
            for (RecipeOverlap.Entry[] pair : this.overlap.fights(stations)) {
                RecipeOverlap.Entry ours = side(pair[0]).equals(Nookworks.MODID) ? pair[0] : side(pair[1]).equals(Nookworks.MODID) ? pair[1] : null;
                RecipeOverlap.Entry theirs = side(pair[0]).equals(FarmersDelight.MODID) ? pair[0]
                        : side(pair[1]).equals(FarmersDelight.MODID) ? pair[1] : null;
                if (ours == null || theirs == null || this.twins.get(ours.result()) != theirs.result()) {
                    continue; // only two twins fight over by the same station are settled here
                }
                RecipeOverlap.Entry loser = this.settings.prefer().equals(Nookworks.MODID) ? theirs : ours;
                if (recipes.remove(loser.id()) != null) {
                    this.stoodDown.add(loser.id());
                }
            }
            if (NookworksStartupConfig.flourBakery()) {
                this.remove(recipes, this.settings.bakery());
            }
            if (!NookworksStartupConfig.vanillaBread()) {
                this.remove(recipes, this.settings.noWheatBread());
            }
        }

        /** Which mod a recipe at a station is: its id's, or else its dish's. */
        private static String side(RecipeOverlap.Entry entry) {
            String ns = entry.id().getNamespace();
            return ns.equals(Nookworks.MODID) || ns.equals(FarmersDelight.MODID) ? ns : BuiltInRegistries.ITEM.getKey(entry.result()).getNamespace();
        }

        private void remove(Map<ResourceLocation, RecipeHolder<?>> recipes, Set<ResourceLocation> ids) {
            for (ResourceLocation id : ids) {
                if (recipes.remove(id) != null) {
                    this.stoodDown.add(id);
                }
            }
        }

        // ---- 4. either pot ----

        /** Its Cooking Pot's dishes as our Iron Pot's (or Frying Pan's) recipes. */
        private void intoOurPot(Map<ResourceLocation, RecipeHolder<?>> recipes) {
            if (this.fdCooking == null) {
                return;
            }
            List<RecipeOverlap.Entry> here = new ArrayList<>(RecipeOverlap.entries(recipes.values(), this.registries));
            for (RecipeHolder<?> holder : List.copyOf(recipes.values())) {
                if (holder.value().getType() != this.fdCooking || this.settings.notCrossed().contains(holder.id())) {
                    continue;
                }
                try {
                    PotRecipe pot = this.asOurs(holder);
                    if (pot == null) {
                        continue;
                    }
                    ResourceLocation id = Nookworks.id(PREFIX + "pot/" + holder.id().getNamespace() + "/" + holder.id().getPath());
                    RecipeHolder<PotRecipe> made = new RecipeHolder<>(id, pot);
                    if (this.fightsWith(made, here)) {
                        continue;
                    }
                    recipes.put(id, made);
                    here.add(RecipeOverlap.entry(made, this.registries, null, null));
                    this.intoOurPot.add(id);
                } catch (RuntimeException e) {
                    this.leftOut.add(holder.id() + ": " + e.getMessage());
                }
            }
        }

        private @Nullable PotRecipe asOurs(RecipeHolder<?> holder) {
            Recipe<?> recipe = holder.value();
            JsonObject json = Recipe.CODEC.encodeStart(this.ops, recipe).getOrThrow().getAsJsonObject();
            ItemStack dish = recipe.getResultItem(this.registries);
            boolean pan = this.settings.pan().contains(holder.id());
            int room = pan ? 4 : 8; // what the Frying Pan and the Iron Pot hold at once
            if (dish.isEmpty() || recipe.getIngredients().isEmpty() || recipe.getIngredients().size() > room) {
                this.leftOut.add(holder.id() + ": more things than our " + (pan ? "Frying Pan" : "Iron Pot") + " holds");
                return null;
            }
            Map<String, Ingredient> kinds = new LinkedHashMap<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Ingredient ingredient : recipe.getIngredients()) {
                String key = Ingredient.CODEC.encodeStart(this.ops, ingredient).getOrThrow().toString();
                kinds.putIfAbsent(key, ingredient);
                counts.merge(key, 1, Integer::sum);
            }
            List<PotRecipe.Part> parts = new ArrayList<>();
            kinds.forEach((key, ingredient) -> parts.add(new PotRecipe.Part(ingredient, counts.get(key))));
            Item container = null;
            JsonElement given = json.get("container");
            if (given != null && given.isJsonObject() && given.getAsJsonObject().has("id")) {
                container = BuiltInRegistries.ITEM.get(ResourceLocation.parse(given.getAsJsonObject().get("id").getAsString()));
            }
            if (container == null || container == Items.AIR) {
                ItemStack remainder = dish.getCraftingRemainingItem();
                container = remainder.isEmpty() ? null : remainder.getItem();
            }
            int time = json.has("cookingtime") ? json.get("cookingtime").getAsInt() : 200;
            Optional<Holder<Item>> into = container == null || container == Items.AIR ? Optional.empty()
                    : Optional.of(BuiltInRegistries.ITEM.wrapAsHolder(container));
            return new PotRecipe(parts, pan ? PotRecipe.Liquid.OIL : PotRecipe.Liquid.WATER, pan ? this.settings.oil() : this.settings.water(),
                    time, into, new PotRecipe.Result(dish.getItemHolder(), dish.getCount()), pan, Optional.empty());
        }

        /** Our Iron Pot's and Frying Pan's dishes as its Cooking Pot's recipes. */
        private void intoTheirPot(Map<ResourceLocation, RecipeHolder<?>> recipes) {
            if (this.fdCooking == null || !BuiltInRegistries.RECIPE_SERIALIZER.containsKey(FarmersDelight.COOKING)) {
                return;
            }
            List<RecipeHolder<PotRecipe>> ours = new ArrayList<>();
            for (RecipeHolder<?> holder : recipes.values()) {
                if (holder.value() instanceof PotRecipe pot && !made(holder.id())
                        && !this.settings.notCrossed().contains(holder.id())) {
                    @SuppressWarnings("unchecked")
                    RecipeHolder<PotRecipe> mine = (RecipeHolder<PotRecipe>) holder;
                    if (new ItemStack(pot.result().item()).has(DataComponents.FOOD)) {
                        ours.add(mine);
                    }
                }
            }
            // of two that meet in its pot, which has no liquid to tell them apart, the boiled before the fried
            ours.sort(Comparator.comparing((RecipeHolder<PotRecipe> h) -> h.value().pan())
                    .thenComparing(h -> h.value().liquid() != PotRecipe.Liquid.WATER).thenComparing(h -> h.id().toString()));
            List<RecipeOverlap.Entry> here = new ArrayList<>(RecipeOverlap.entries(recipes.values(), this.registries));
            for (RecipeHolder<PotRecipe> holder : ours) {
                try {
                    Recipe<?> theirs = this.asTheirs(holder);
                    if (theirs == null) {
                        continue;
                    }
                    ResourceLocation id = Nookworks.id(PREFIX + "cooking_pot/" + holder.id().getNamespace() + "/" + holder.id().getPath());
                    RecipeHolder<?> made = new RecipeHolder<>(id, theirs);
                    if (this.fightsWith(made, here)) {
                        continue;
                    }
                    recipes.put(id, made);
                    here.add(RecipeOverlap.entry(made, this.registries, this.fdCooking, null));
                    this.intoTheirPot.add(id);
                } catch (RuntimeException e) {
                    this.leftOut.add(holder.id() + ": " + e.getMessage());
                }
            }
        }

        private @Nullable Recipe<?> asTheirs(RecipeHolder<PotRecipe> holder) {
            PotRecipe pot = holder.value();
            JsonArray ingredients = new JsonArray();
            for (PotRecipe.Part part : pot.ingredients()) {
                JsonElement one = Ingredient.CODEC.encodeStart(this.ops, part.ingredient()).getOrThrow();
                if (one.isJsonArray()) { // its pot reads one object a slot: a list of values as NeoForge's compound
                    JsonObject compound = new JsonObject();
                    compound.addProperty("type", "neoforge:compound");
                    JsonArray children = new JsonArray();
                    one.getAsJsonArray().forEach(children::add);
                    compound.add("children", children);
                    one = compound;
                }
                for (int n = 0; n < part.count(); n++) {
                    ingredients.add(one);
                }
            }
            if (ingredients.size() > 6) { // its pot's six slots
                this.leftOut.add(holder.id() + ": more things than the Cooking Pot's six slots");
                return null;
            }
            JsonObject json = new JsonObject();
            json.addProperty("type", FarmersDelight.COOKING.toString());
            json.add("ingredients", ingredients);
            JsonObject result = new JsonObject();
            result.addProperty("id", pot.result().item().unwrapKey().orElseThrow().location().toString());
            result.addProperty("count", pot.result().count());
            json.add("result", result);
            pot.container().ifPresent(container -> {
                JsonObject into = new JsonObject();
                into.addProperty("id", container.unwrapKey().orElseThrow().location().toString());
                into.addProperty("count", 1);
                json.add("container", into);
            });
            json.addProperty("cookingtime", pot.time());
            json.addProperty("experience", this.settings.experience());
            json.addProperty("recipe_book_tab", pot.container().isPresent() && pot.container().get().value() == Items.BOWL ? "meals" : "misc");
            return Recipe.CODEC.parse(this.ops, json).getOrThrow();
        }

        /** Whether {@code made} would fight one of {@code here}; said in the report if it would. */
        private boolean fightsWith(RecipeHolder<?> made, List<RecipeOverlap.Entry> here) {
            RecipeOverlap.Entry entry = RecipeOverlap.entry(made, this.registries, this.fdCooking, null);
            if (entry == null) {
                return false;
            }
            for (RecipeOverlap.Entry other : here) {
                if (this.overlap.fight(entry, other)) {
                    this.leftOut.add(made.id() + ": the same things as " + other.id() + " in " + entry.place());
                    return true;
                }
            }
            return false;
        }
    }
}
