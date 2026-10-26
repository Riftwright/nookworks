package dev.nookworks.recycling;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.common.Tags;

/**
 * What the Compressor makes of nine of an item: whatever the crafting table makes of nine of it laid 3x3 - nuggets into
 * an ingot, ingots into a block, scrap into a Scrap Bale - so other mods' metals need nothing set (the user, 2026-10-18).
 * A recipe that gives back more than one, or the same item, or leaves something in the grid (a container), is not
 * compressing and is not taken.
 */
public final class Compressing {
    public static final int TAKES = 9;
    /** What the Compressor takes of vanilla's and ours, for sorting (the Precision Sorter's tags). */
    public static final TagKey<Item> COMPRESSIBLE = TagKey.create(Registries.ITEM, Nookworks.id("compressible"));

    /** {@link #compressible} as last worked out, and from which recipes. Server thread only. */
    private static @Nullable Set<Item> found;
    private static @Nullable RecipeManager foundFrom;
    /**
     * On a client: what the server said presses (a client has no recipes to look in), or null before it has said. Every
     * client is told on joining (the payload is no optional one: a server and client whose Nookworks differ in their
     * payloads, as this one's do from 1.1.0's, do not connect); until then, and for a test's stand-in, it guesses by
     * tags as before ({@link #guess}).
     */
    private static volatile @Nullable Set<Item> heard;

    /** The result of nine of {@code one}, or empty when the crafting table has no such recipe. */
    public static ItemStack result(ServerLevel level, ItemStack one) {
        if (one.isEmpty()) {
            return ItemStack.EMPTY;
        }
        CraftingInput input = CraftingInput.of(3, 3, Collections.nCopies(TAKES, one.copyWithCount(1)));
        return level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(holder -> pressed(holder.value(), input, one)).orElse(ItemStack.EMPTY);
    }

    /** What {@code recipe} makes of nine of {@code one} laid out as {@code input}, if that is compressing; else empty. */
    public static ItemStack pressed(CraftingRecipe recipe, CraftingInput input, ItemStack one) {
        if (recipe.getRemainingItems(input).stream().anyMatch(left -> !left.isEmpty())) {
            return ItemStack.EMPTY; // one that leaves something in the grid (a bottle, a bucket) is not compressing: it would be lost
        }
        ItemStack out = recipe.assemble(input);
        return out.getCount() != 1 || ItemStack.isSameItem(out, one) ? ItemStack.EMPTY : out;
    }

    /**
     * Every item nine of which press into something on this server - what {@link #result} gives for it - for its clients:
     * guessed by tags, another mod's dust or gem with no 9-to-1 recipe (and brown rice, with the kitchen off) went into a
     * Compressor's slot on a shift-click and jumped back out as the server refused it. Looked for among the first
     * ingredients of the crafting recipes that take nine: one nine of which its own recipe presses is then asked as a
     * press asks (the recipe the grid finds first is the one made) - asked of every candidate, every recipe was scanned
     * for each, slow in a big pack, on the server thread. Worked out again once the recipes change (a /reload). A
     * special recipe of another mod (one with no ingredients to show, as a firework's) is not seen.
     */
    public static synchronized Set<Item> compressible(ServerLevel level) {
        RecipeManager recipes = level.getServer().getRecipeManager();
        if (found == null || foundFrom != recipes) {
            Set<Item> presses = new LinkedHashSet<>();
            Set<Item> asked = new HashSet<>();
            for (RecipeHolder<CraftingRecipe> holder : recipes.recipeMap().byType(RecipeType.CRAFTING)) {
                CraftingRecipe recipe = holder.value();
                List<Ingredient> ingredients = recipe.placementInfo().ingredients();
                if (ingredients.size() != TAKES) {
                    continue;
                }
                ingredients.getFirst().items().forEach(item -> {
                    ItemStack one = new ItemStack(item);
                    CraftingInput nine = CraftingInput.of(3, 3, Collections.nCopies(TAKES, one));
                    if (!presses.contains(item.value()) && recipe.matches(nine, level) && !pressed(recipe, nine, one).isEmpty()
                            && asked.add(item.value()) && !result(level, one).isEmpty()) {
                        presses.add(item.value());
                    }
                });
            }
            found = Set.copyOf(presses);
            foundFrom = recipes;
        }
        return found;
    }

    /** On a client: the server's word ({@link #compressible}). */
    public static void heard(Set<Item> items) {
        heard = Set.copyOf(items);
    }

    /** On a client: left the server; its word goes with it. */
    public static void leftServer() {
        heard = null;
    }

    /** On a client: whether nine of {@code stack} press, as the server said; else as guessed by tags. */
    public static boolean pressesHere(ItemStack stack) {
        Set<Item> known = heard;
        if (stack.getComponentsPatch().size() > 0) {
            return false; // named or otherwise changed things are not pressed (CompressorBlockEntity#resultOf)
        }
        return known != null ? known.contains(stack.getItem()) : guess(stack);
    }

    /**
     * With no word from the server: #nookworks:compressible is what it takes of vanilla's and ours (a GameTest keeps them
     * one), and another mod's metal is most likely a nugget, an ingot, raw ore, a gem or a dust.
     */
    private static boolean guess(ItemStack stack) {
        return stack.is(COMPRESSIBLE) || stack.is(Tags.Items.NUGGETS) || stack.is(Tags.Items.INGOTS)
                || stack.is(Tags.Items.RAW_MATERIALS) || stack.is(Tags.Items.GEMS) || stack.is(Tags.Items.DUSTS);
    }

    private Compressing() {}
}
