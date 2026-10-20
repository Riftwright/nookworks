package dev.nookworks.recycling;

import java.util.Collections;

import dev.nookworks.Nookworks;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * What the Compressor makes of nine of an item: whatever the crafting table makes of nine of it laid 3x3 - nuggets into
 * an ingot, ingots into a block, scrap into a Scrap Bale - so other mods' metals need nothing set (the user, 2026-10-18).
 * A recipe that gives back more than one, or the same item, or leaves something in the grid (a container), is not
 * compressing and is not taken.
 */
public final class Compressing {
    public static final int TAKES = 9;
    /** What the Compressor takes, for sorting and for the client, which cannot look recipes up. */
    public static final TagKey<Item> COMPRESSIBLE = TagKey.create(Registries.ITEM, Nookworks.id("compressible"));

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

    private Compressing() {}
}
