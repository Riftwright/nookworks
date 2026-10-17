package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.inventory.RecipeChoice;
import io.netty.buffer.Unpooled;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.GameType;

/**
 * Picking among recipes that clash (the user, 2026-10-10). Vanilla and Nookworks have no two crafting recipes that make
 * different things of one grid, so the test adds a pair for its own while - a dead bush and a cobweb make string, or
 * sticks - and takes them away again; it runs in a batch of its own, the recipes being the whole server's.
 */
final class RecipeChoiceTests {
    static void register() {
        test("recipe_choice_picks_and_remembers", RecipeChoiceTests::picksAndRemembers);
        test("recipe_choice_offers_what_it_can_send", RecipeChoiceTests::offersWhatItCanSend);
        test("recipe_choice_leaves_the_made_recipes_leftovers", RecipeChoiceTests::leavesTheMadeRecipesLeftovers);
    }

    private static void picksAndRemembers(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        RecipeMap before = manager.recipeMap();
        RecipeHolder<?> string = recipe(helper, "string", "minecraft:string", 2);
        RecipeHolder<?> sticks = recipe(helper, "sticks", "minecraft:stick", 3);
        List<RecipeHolder<?>> all = new ArrayList<>(before.values());
        all.add(string);
        all.add(sticks);
        recipes(manager, RecipeMap.createClient(all));
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            InventoryMenu menu = player.inventoryMenu;
            fill(menu);
            check(helper, made(menu, Items.STRING, 2), "first the recipe found first, as vanilla: " + menu.getSlot(0).getItem());
            check(helper, List.of(string.id(), sticks.id()).equals(RecipeChoice.offered(player)),
                    "and the player is told both: " + RecipeChoice.offered(player));
            RecipeChoice.pick(player, menu.containerId, 1);
            check(helper, made(menu, Items.STICK, 3), "the pick makes sticks: " + menu.getSlot(0).getItem());
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
            check(helper, count(player, Items.STICK) == 12 && count(player, Items.STRING) == 0,
                    "a shift-click makes all four batches of the pick: " + count(player, Items.STICK) + " sticks, "
                            + count(player, Items.STRING) + " string");
            fill(menu);
            check(helper, made(menu, Items.STICK, 3), "the pick is remembered for the same grid: " + menu.getSlot(0).getItem());
            RecipeChoice.pick(player, menu.containerId, 0);
            check(helper, made(menu, Items.STRING, 2), "and picked again, the other: " + menu.getSlot(0).getItem());
            RecipeChoice.pick(player, menu.containerId + 7, 1);
            RecipeChoice.pick(player, menu.containerId, 5);
            check(helper, made(menu, Items.STRING, 2), "a pick for another menu, or out of the list, is let be");
            menu.getSlot(2).set(ItemStack.EMPTY);
            check(helper, menu.getSlot(0).getItem().isEmpty() && RecipeChoice.offered(player).isEmpty(),
                    "a grid that makes nothing offers nothing: " + RecipeChoice.offered(player));
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            recipes(manager, before);
        }
        helper.succeed();
    }

    /**
     * Sixty-five recipes making as many things of one grid: the player is offered the first {@link RecipeChoice#MAX_OFFERED},
     * which is what the list sent to their game can carry (review r12: offered all 65, its sending threw the player off
     * the server).
     */
    private static void offersWhatItCanSend(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        RecipeMap before = manager.recipeMap();
        List<RecipeHolder<?>> all = new ArrayList<>(before.values());
        List<Item> made = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(RecipeChoice.MAX_OFFERED + 1).toList();
        for (int i = 0; i < made.size(); i++) {
            all.add(recipe(helper, "many_" + i, BuiltInRegistries.ITEM.getKey(made.get(i)).toString(), 1));
        }
        recipes(manager, RecipeMap.createClient(all));
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            fill(player.inventoryMenu);
            List<?> offered = RecipeChoice.offered(player);
            check(helper, offered != null && offered.size() == RecipeChoice.MAX_OFFERED, "offered " + (offered == null ? 0 : offered.size()));
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
            List<ItemStack> outputs = made.subList(0, RecipeChoice.MAX_OFFERED).stream().map(ItemStack::new).toList();
            NookworksNetwork.RecipeChoices.STREAM_CODEC.encode(buffer, new NookworksNetwork.RecipeChoices(1, outputs, 0));
            check(helper, buffer.readableBytes() > 0, "and that many go over the wire");
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            recipes(manager, before);
        }
        helper.succeed();
    }

    /**
     * What a craft leaves behind is the picked recipe's, by a click on the result and by a shift-click (review r12: the
     * pick made sticks and left the other recipe's leftover). Two recipes of one grid, each leaving its own gem.
     */
    private static void leavesTheMadeRecipesLeftovers(GameTestHelper helper) {
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        RecipeMap before = manager.recipeMap();
        List<RecipeHolder<?>> all = new ArrayList<>(before.values());
        all.add(leaving("left_diamond", Items.STRING, Items.DIAMOND));
        all.add(leaving("left_emerald", Items.STICK, Items.EMERALD));
        recipes(manager, RecipeMap.createClient(all));
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            InventoryMenu menu = player.inventoryMenu;
            fill(menu);
            check(helper, menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.STRING), "first, string");
            RecipeChoice.pick(player, menu.containerId, 1);
            check(helper, menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.STICK), "picked, sticks");
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
            check(helper, count(player, Items.EMERALD) == 1 && count(player, Items.DIAMOND) == 0,
                    "a click leaves the sticks' emerald: " + count(player, Items.EMERALD) + " emerald, " + count(player, Items.DIAMOND) + " diamond");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, player);
            // the last round's leftover stays in the emptied square, as vanilla leaves a bucket there
            check(helper, count(player, Items.EMERALD) == 3 && menu.getSlot(1).getItem().is(Items.EMERALD) && count(player, Items.DIAMOND) == 0
                    && menu.slots.stream().noneMatch(slot -> slot.getItem().is(Items.DIAMOND)),
                    "and a shift-click, each round: " + count(player, Items.EMERALD) + " emerald and " + menu.getSlot(1).getItem() + " in the grid, "
                            + count(player, Items.DIAMOND) + " diamond");
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            recipes(manager, before);
        }
        helper.succeed();
    }

    /** A dead bush and a cobweb making {@code result}, and leaving {@code left} in the bush's square. */
    private static RecipeHolder<?> leaving(String name, Item result, Item left) {
        ShapelessRecipe recipe = new ShapelessRecipe(new Recipe.CommonInfo(true), new CraftingRecipe.CraftingBookInfo(CraftingBookCategory.MISC, ""),
                new ItemStackTemplate(result), List.of(Ingredient.of(Items.DEAD_BUSH), Ingredient.of(Items.COBWEB))) {
            @Override
            public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                NonNullList<ItemStack> leftovers = NonNullList.withSize(input.size(), ItemStack.EMPTY);
                leftovers.set(0, new ItemStack(left));
                return leftovers;
            }
        };
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Nookworks.id("test/recipe_choice_" + name)), recipe);
    }

    /** A dead bush and a cobweb in the inventory's grid, four of each. */
    private static void fill(InventoryMenu menu) {
        menu.getSlot(1).set(new ItemStack(Items.DEAD_BUSH, 4));
        menu.getSlot(2).set(new ItemStack(Items.COBWEB, 4));
    }

    private static boolean made(InventoryMenu menu, Item item, int count) {
        ItemStack result = menu.getSlot(InventoryMenu.RESULT_SLOT).getItem();
        return result.is(item) && result.getCount() == count;
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().getNonEquipmentItems().stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static RecipeHolder<?> recipe(GameTestHelper helper, String name, String result, int count) {
        String json = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"minecraft:dead_bush\",\"minecraft:cobweb\"],"
                + "\"result\":{\"id\":\"" + result + "\",\"count\":" + count + "}}";
        Recipe<?> recipe = Recipe.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                JsonParser.parseString(json)).getOrThrow();
        return new RecipeHolder<>(ResourceKey.create(Registries.RECIPE, Nookworks.id("test/recipe_choice_" + name)), recipe);
    }

    private static void recipes(RecipeManager manager, RecipeMap map) {
        try {
            Field field = RecipeManager.class.getDeclaredField("recipes");
            field.setAccessible(true);
            field.set(manager, map);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
