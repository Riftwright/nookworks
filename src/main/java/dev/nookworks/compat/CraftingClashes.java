package dev.nookworks.compat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * Crafting grids a recipe of ours shares with another mod's (or vanilla's) recipe for something else, found as the
 * server loads its recipes (a step of {@link RecipeCrossings}). Nookworks is a guest in the pack (the owner,
 * 2026-11-17): theirs stays as it is, and ours fits beside it or stands down.
 * <ul>
 * <li>The same material in the same form - our Copper Nugget beside the Twilight Forest's and Cyclic's, each made of a
 * copper ingot - is one thing three times over: when theirs takes everything ours takes, ours stands down, as our
 * station recipes do for another mod's of the same material. Their nuggets are in {@code c:nuggets/copper}, which our
 * recipes ask for.</li>
 * <li>Anything else - our Bench and the Twilight Forest's banister of the same wood, our Brown Rice and TofuCraft's
 * rice from a Rice Bale (its recipe takes every {@code c:storage_blocks/rice}) - is a different thing, which a player
 * may want: ours stays, but is put after theirs in the server's own order. So the grid makes theirs, as it would
 * without Nookworks - for vanilla's first match, a Crafter, another mod's autocrafter - and our recipe choice button
 * (or Polymorph, where installed) offers ours beside it.</li>
 * </ul>
 * A grid two recipes make the same thing from is no clash and is left alone (our copper ingot from nine nuggets beside
 * theirs).
 */
public final class CraftingClashes {
    private CraftingClashes() {}

    /** What the last run did: our recipes stood down, and those put after another mod's. */
    public record Done(List<ResourceLocation> stoodDown, List<ResourceLocation> after) {}

    private static volatile Done last = new Done(List.of(), List.of());

    public static Done last() {
        return last;
    }

    static void run(MinecraftServer server) {
        try {
            long start = System.nanoTime();
            Done done = find(server.getRecipeManager(), server.overworld());
            if (!done.stoodDown().isEmpty() || !done.after().isEmpty()) {
                Map<ResourceLocation, RecipeHolder<?>> kept = new LinkedHashMap<>();
                server.getRecipeManager().getRecipes().forEach(holder -> kept.put(holder.id(), holder));
                done.stoodDown().forEach(kept::remove);
                List<RecipeHolder<?>> moved = new ArrayList<>();
                for (ResourceLocation id : done.after()) {
                    RecipeHolder<?> holder = kept.remove(id);
                    if (holder != null) {
                        moved.add(holder);
                    }
                }
                List<RecipeHolder<?>> order = new ArrayList<>(kept.values());
                order.addAll(moved);
                server.getRecipeManager().replaceRecipes(order);
                Nookworks.LOGGER.info("Crafting recipes of ours beside another mod's of the same grid: {} stand down for the same"
                        + " material {}, {} come after theirs {}", done.stoodDown().size(), done.stoodDown(), done.after().size(), done.after());
            }
            last = done;
            Nookworks.LOGGER.debug("Crafting grids looked through for clashes in {} ms", (System.nanoTime() - start) / 1_000_000);
        } catch (RuntimeException e) {
            last = new Done(List.of(), List.of());
            Nookworks.LOGGER.error("Could not look for crafting grids shared with other mods; each recipe stays as it is", e);
        }
    }

    /** Every crafting recipe of ours a grid of another's also takes, and what is to be done with it. */
    static Done find(RecipeManager manager, Level level) {
        // the others, by how many squares their grid fills (a grid can only match a recipe filling as many), and the
        // special recipes, which may match anything
        Map<Integer, List<RecipeHolder<?>>> bySize = new HashMap<>();
        List<RecipeHolder<?>> special = new ArrayList<>();
        List<RecipeHolder<?>> ours = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof CraftingRecipe recipe)) {
                continue;
            }
            if (holder.id().getNamespace().equals(Nookworks.MODID)) {
                ours.add(holder);
            } else {
                int size = filled(recipe);
                (size < 0 ? special : bySize.computeIfAbsent(size, s -> new ArrayList<>())).add(holder);
            }
        }
        List<ResourceLocation> stoodDown = new ArrayList<>();
        List<ResourceLocation> after = new ArrayList<>();
        for (RecipeHolder<?> holder : ours) {
            CraftingRecipe mine = (CraftingRecipe) holder.value();
            CraftingInput grid = sampleGrid(mine);
            if (grid == null) {
                continue;
            }
            ItemStack made = mine.getResultItem(level.registryAccess());
            List<RecipeHolder<?>> candidates = new ArrayList<>(bySize.getOrDefault(filled(mine), List.of()));
            candidates.addAll(special);
            boolean down = false;
            boolean behind = false;
            for (RecipeHolder<?> other : candidates) {
                CraftingRecipe theirs = (CraftingRecipe) other.value();
                if (!matches(theirs, grid, level)) {
                    continue;
                }
                ItemStack makes = theirs.assemble(grid, level.registryAccess());
                if (makes.isEmpty() || ItemStack.matches(made, makes)) {
                    continue; // the same thing either way: no clash
                }
                if (RecipeCrossings.sameMaterial(made.getItem(), makes.getItem()) && covers(theirs, mine, level)) {
                    down = true;
                } else {
                    behind = true;
                }
            }
            if (down) {
                stoodDown.add(holder.id());
            } else if (behind) {
                after.add(holder.id());
            }
        }
        return new Done(List.copyOf(stoodDown), List.copyOf(after));
    }

    private static boolean matches(CraftingRecipe recipe, CraftingInput grid, Level level) {
        try {
            return recipe.matches(grid, level);
        } catch (RuntimeException e) {
            return false; // a recipe that cannot judge this grid is not one that takes it
        }
    }

    /** How many squares {@code recipe}'s grid fills, or -1 for a special recipe. */
    private static int filled(CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return (int) shaped.getIngredients().stream().filter(i -> !i.isEmpty()).count();
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return shapeless.getIngredients().size();
        }
        return -1;
    }

    /**
     * Whether {@code theirs} takes every grid {@code mine} takes: each item each of our squares may hold, put into our
     * sample grid in turn, is still theirs. Standing ours down then leaves no grid without a recipe.
     */
    private static boolean covers(CraftingRecipe theirs, CraftingRecipe mine, Level level) {
        if (sampleGrid(mine) == null) {
            return false;
        }
        List<Ingredient> ingredients = mine.getIngredients();
        int width = mine instanceof ShapedRecipe shaped ? shaped.getWidth() : 3; // (a CraftingInput is trimmed to what it holds)
        for (int i = 0; i < ingredients.size(); i++) {
            Ingredient ingredient = ingredients.get(i);
            if (ingredient.isEmpty()) {
                continue;
            }
            for (ItemStack item : ingredient.getItems()) {
                List<ItemStack> squares = new ArrayList<>(squares(mine, width));
                squares.set(i, item.copyWithCount(1));
                CraftingInput grid = CraftingInput.of(width, squares.size() / width, squares);
                if (!matches(theirs, grid, level)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The squares of {@link #sampleGrid}, in the order of {@code recipe}'s ingredients. */
    private static List<ItemStack> squares(CraftingRecipe recipe, int width) {
        List<ItemStack> items = new ArrayList<>();
        for (Ingredient ingredient : recipe.getIngredients()) {
            items.add(ingredient.isEmpty() ? ItemStack.EMPTY : firstOf(ingredient));
        }
        if (recipe instanceof ShapelessRecipe) {
            while (items.size() < 9) {
                items.add(ItemStack.EMPTY);
            }
        }
        return items;
    }

    /** A recipe's grid as a player would lay it out, with the first item each ingredient takes; null for a special recipe. */
    public static @Nullable CraftingInput sampleGrid(Recipe<?> recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), squares(shaped, shaped.getWidth()));
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return CraftingInput.of(3, 3, squares(shapeless, 3));
        }
        return null;
    }

    private static ItemStack firstOf(Ingredient ingredient) {
        return java.util.Arrays.stream(ingredient.getItems()).findFirst().map(stack -> stack.copyWithCount(1)).orElse(ItemStack.EMPTY);
    }
}
