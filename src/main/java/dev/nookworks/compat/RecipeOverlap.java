package dev.nookworks.compat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;

import dev.nookworks.compat.farmersdelight.FarmersDelight;
import dev.nookworks.cooking.PotRecipe;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * Where two recipes would fight: the same place (a vessel and what it cooks in, or one of the stations that cook one
 * thing at a time), the same things going in, different things coming out - the place can only make one of them, and
 * a player could not tell which. The rule the run-time crossings keep ({@link RecipeCrossings}: Farmer's Delight's
 * kitchen, the shared materials) and the data check tests in every run, with or without other mods. Farmer's
 * Delight's places (its Cooking Pot, its Cutting Board) are looked up by id and simply absent without it.
 * <p>
 * "The same things": each thing one recipe asks for can be matched to a different thing the other asks for, the two
 * sharing at least one item. In our Iron Pot and Frying Pan the kinds are matched and not their counts (a pot holding
 * the makings of both would make whichever it finds first); in Farmer's Delight's Cooking Pot every slot is one thing,
 * so the counts must agree too. The crafting grid is left out: there the player picks (inventory/RecipeChoice).
 */
public final class RecipeOverlap {
    /** One recipe as a place sees it. */
    public record Entry(ResourceLocation id, String place, List<Ingredient> things, Item result) {}

    /** The single-input stations, by recipe type. */
    private static final List<RecipeType<?>> STATIONS = List.of(RecipeType.SMELTING, RecipeType.SMOKING, RecipeType.BLASTING,
            RecipeType.CAMPFIRE_COOKING);

    private final Map<Ingredient, Set<Item>> items = new IdentityHashMap<>();

    /** Every recipe of {@code recipes} that cooks in a place this looks at, as that place sees it. */
    public static List<Entry> entries(Iterable<RecipeHolder<?>> recipes, HolderLookup.Provider registries) {
        List<Entry> out = new ArrayList<>();
        RecipeType<?> fdCooking = FarmersDelight.recipeType(FarmersDelight.COOKING);
        RecipeType<?> fdCutting = FarmersDelight.recipeType(FarmersDelight.CUTTING);
        for (RecipeHolder<?> holder : recipes) {
            Entry entry = entry(holder, registries, fdCooking, fdCutting);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    public static @Nullable Entry entry(RecipeHolder<?> holder, HolderLookup.Provider registries, @Nullable RecipeType<?> fdCooking,
            @Nullable RecipeType<?> fdCutting) {
        Recipe<?> recipe = holder.value();
        try {
            if (recipe instanceof PotRecipe pot) {
                List<Ingredient> kinds = pot.ingredients().stream().map(PotRecipe.Part::ingredient).toList();
                String place = (pot.pan() ? "frying_pan/" : "iron_pot/") + pot.liquid().getSerializedName();
                return new Entry(holder.id(), place, kinds, pot.result().item().value());
            }
            RecipeType<?> type = recipe.getType();
            ItemStack made = recipe.getResultItem(registries);
            if (made.isEmpty()) {
                return null;
            }
            if (type == fdCooking) {
                return new Entry(holder.id(), "farmersdelight:cooking_pot", List.copyOf(recipe.getIngredients()), made.getItem());
            }
            if (STATIONS.contains(type)) {
                return new Entry(holder.id(), BuiltInRegistries.RECIPE_TYPE.getKey(type).toString(), List.copyOf(recipe.getIngredients()),
                        made.getItem());
            }
            if (type == fdCutting) {
                // the tool is part of the place: a knife and an axe on one log are two different cuts
                JsonElement json = Recipe.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), recipe).getOrThrow();
                JsonElement tool = json.getAsJsonObject().get("tool");
                return new Entry(holder.id(), "farmersdelight:cutting_board " + tool, List.copyOf(recipe.getIngredients()), made.getItem());
            }
        } catch (RuntimeException e) {
            return null; // a recipe this cannot read is not one it can judge
        }
        return null;
    }

    /** Whether {@code a} and {@code b} fight: one place, the same things, different dishes. */
    public boolean fight(Entry a, Entry b) {
        if (!a.place().equals(b.place()) || a.result() == b.result() || a.things().size() != b.things().size()
                || a.things().isEmpty()) {
            return false;
        }
        return this.matches(a.things(), b.things(), 0, new boolean[b.things().size()]);
    }

    private boolean matches(List<Ingredient> a, List<Ingredient> b, int at, boolean[] used) {
        if (at == a.size()) {
            return true;
        }
        Set<Item> mine = this.items(a.get(at));
        for (int i = 0; i < b.size(); i++) {
            if (used[i] || !shares(mine, this.items(b.get(i)))) {
                continue;
            }
            used[i] = true;
            if (this.matches(a, b, at + 1, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    /**
     * Whether {@code wider} takes everything {@code narrower} takes: each of its things matched to a different one of
     * {@code wider}'s that holds every item it holds (so standing {@code narrower} down leaves nothing without a way).
     */
    public boolean covers(Entry wider, Entry narrower) {
        return wider.things().size() == narrower.things().size()
                && this.covers(wider.things(), narrower.things(), 0, new boolean[wider.things().size()]);
    }

    private boolean covers(List<Ingredient> wider, List<Ingredient> narrower, int at, boolean[] used) {
        if (at == narrower.size()) {
            return true;
        }
        Set<Item> mine = this.items(narrower.get(at));
        for (int i = 0; i < wider.size(); i++) {
            if (used[i] || !this.items(wider.get(i)).containsAll(mine)) {
                continue;
            }
            used[i] = true;
            if (this.covers(wider, narrower, at + 1, used)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    private static boolean shares(Set<Item> a, Set<Item> b) {
        for (Item item : a) {
            if (b.contains(item)) {
                return true;
            }
        }
        return false;
    }

    private Set<Item> items(Ingredient ingredient) {
        return this.items.computeIfAbsent(ingredient, i -> {
            Set<Item> set = new HashSet<>();
            for (ItemStack stack : i.getItems()) {
                set.add(stack.getItem());
            }
            return set;
        });
    }

    /** Every pair of {@code entries} that fight, each pair once. */
    public List<Entry[]> fights(List<Entry> entries) {
        List<Entry[]> out = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            for (int j = i + 1; j < entries.size(); j++) {
                if (this.fight(entries.get(i), entries.get(j))) {
                    out.add(new Entry[] {entries.get(i), entries.get(j)});
                }
            }
        }
        return out;
    }
}
