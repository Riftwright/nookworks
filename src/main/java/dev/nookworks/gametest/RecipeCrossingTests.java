package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.RecipeCrossings;
import dev.nookworks.compat.RecipeOverlap;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * The run-time recipe changes as one (compat.RecipeCrossings), in every run - alone, with Farmer's Delight, with Create,
 * with both: no two recipes fight in one place, and a material two mods share has one result at a station.
 */
final class RecipeCrossingTests {
    private RecipeCrossingTests() {}

    static void register() {
        NookworksGameTests.test("recipes_no_two_fight_in_one_place", RecipeCrossingTests::noFights);
        NookworksGameTests.test("recipes_raw_zinc_has_one_ingot_at_a_furnace", RecipeCrossingTests::oneZinc);
    }

    /**
     * The data check: in each pot (by its liquid), a furnace, smoker, blast furnace and campfire, and with Farmer's
     * Delight its Cooking Pot and its Cutting Board (by tool), no recipe of ours or brought over takes the same things
     * as another for a different result - another mod's item of the same material included (that is a second item in
     * the output slot all the same, which does not stack with the first).
     */
    private static void noFights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<RecipeOverlap.Entry> entries = RecipeOverlap.entries(level.getRecipeManager().getRecipes(), level.registryAccess());
        List<String> fights = new ArrayList<>();
        for (RecipeOverlap.Entry[] pair : new RecipeOverlap().fights(entries)) {
            if (ours(pair[0]) || ours(pair[1])) {
                fights.add(pair[0].id() + " / " + pair[1].id() + " in " + pair[0].place());
            }
        }
        check(helper, entries.size() > 100, "the check saw the recipes: " + entries.size());
        check(helper, fights.isEmpty(), "recipes that take the same things for different results: " + fights);
        helper.succeed();
    }

    private static boolean ours(RecipeOverlap.Entry entry) {
        return entry.id().getNamespace().equals(Nookworks.MODID)
                || BuiltInRegistries.ITEM.getKey(entry.result()).getNamespace().equals(Nookworks.MODID);
    }

    /**
     * Raw Zinc in a furnace or a blast furnace has one recipe: alone, ours (our Zinc Ingot); with Create, whose recipe takes
     * all of {@code #c:raw_materials/zinc}, Create's (ours stood down, and nothing else of ours with it).
     */
    private static void oneZinc(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        boolean create = net.neoforged.fml.ModList.get().isLoaded("create");
        TagKey<Item> ingots = TagKey.create(Registries.ITEM, ResourceLocation.parse("c:ingots/zinc"));
        for (RecipeType<?> type : List.of(RecipeType.SMELTING, RecipeType.BLASTING)) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            List<RecipeHolder<?>> found = (List) level.getRecipeManager().getRecipesFor((RecipeType) type,
                    new SingleRecipeInput(new ItemStack(ModItems.RAW_ZINC.get())), level);
            String where = BuiltInRegistries.RECIPE_TYPE.getKey(type) + ": " + found.stream().map(RecipeHolder::id).toList();
            check(helper, found.size() == 1, "raw zinc has one recipe at " + where);
            ItemStack ingot = found.get(0).value().getResultItem(level.registryAccess());
            check(helper, ingot.is(ingots) && ingot.is(create ? BuiltInRegistries.ITEM.get(ResourceLocation.parse("create:zinc_ingot"))
                    : ModItems.ZINC_INGOT.get()), "and it gives " + (create ? "Create's" : "our") + " zinc ingot: " + ingot);
        }
        List<ResourceLocation> down = RecipeCrossings.materialsStoodDown();
        List<ResourceLocation> zinc = List.of(Nookworks.id("zinc_ingot_from_smelting_raw_zinc"), Nookworks.id("zinc_ingot_from_blasting_raw_zinc"));
        check(helper, create ? down.size() == 2 && down.containsAll(zinc) : down.isEmpty(), "what stood down for a shared material: " + down);
        helper.succeed();
    }
}
