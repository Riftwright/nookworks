package dev.nookworks.compat.farmersdelight;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;

/**
 * What Nookworks knows of Farmer's Delight (1.21.1), all by id: no class of Farmer's Delight's is linked from here or
 * from anything that loads without it, so a game without it runs as before. Its tags and recipe types are simply empty
 * or absent then.
 */
public final class FarmersDelight {
    public static final String MODID = "farmersdelight";
    /** Whether it is installed (asked once: the mod list does not change while the game runs). */
    public static final boolean LOADED = ModList.get() != null && ModList.get().isLoaded(MODID);

    /** What heats its Cooking Pot and Skillet from below: a block with a "lit" state only while lit. */
    public static final TagKey<Block> HEAT_SOURCES = TagKey.create(Registries.BLOCK, id("heat_sources"));
    /** What its vessels take heat through from a source under it (a hopper). */
    public static final TagKey<Block> HEAT_CONDUCTORS = TagKey.create(Registries.BLOCK, id("heat_conductors"));

    /** Its Cooking Pot's recipe type and serializer. */
    public static final ResourceLocation COOKING = id("cooking");
    /** Its Cutting Board's. */
    public static final ResourceLocation CUTTING = id("cutting");

    private FarmersDelight() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    /** The recipe type {@code id} names, or null when no mod registers it. */
    public static @Nullable RecipeType<?> recipeType(ResourceLocation id) {
        return BuiltInRegistries.RECIPE_TYPE.containsKey(id) ? BuiltInRegistries.RECIPE_TYPE.get(id) : null;
    }
}
