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
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
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
        test("recipe_choice_looks_through_a_grid_once", RecipeChoiceTests::looksThroughAGridOnce);
    }

    /**
     * Beside Polymorph, which lets the player pick among recipes itself, ours stands down: nothing is offered by us, and
     * the grid makes one of the two (Polymorph's pick, or vanilla's first). True if so, the test then passing.
     */
    private static boolean standsDown(GameTestHelper helper) {
        if (!RecipeChoice.standsDown()) {
            return false;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Saved before = saved(manager);
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        all.add(recipe(helper, "string", "minecraft:string", 2));
        all.add(recipe(helper, "sticks", "minecraft:stick", 3));
        manager.replaceRecipes(all);
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            check(helper, !RecipeChoice.active(), "standing down, ours is not active");
            InventoryMenu menu = player.inventoryMenu;
            fill(menu);
            check(helper, RecipeChoice.offered(player) == null, "beside Polymorph we offer nothing: " + RecipeChoice.offered(player));
            check(helper, made(menu, Items.STRING, 2) || made(menu, Items.STICK, 3), "and the grid makes one of the two: "
                    + menu.getSlot(0).getItem());
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            restore(manager, before);
        }
        helper.succeed();
        return true;
    }

    /**
     * The grid's recipes are looked through once for the things it holds: a shift-click craft of four rounds, the same
     * things in it each round, scans it once, and putting one more of a thing already there scans nothing again; a /reload
     * (the recipes replaced) scans again. Timed beside whatever mods this run has: a fresh grid (every crafting recipe
     * looked through), the same grid again, and vanilla's own lookup - written to the log.
     */
    private static void looksThroughAGridOnce(GameTestHelper helper) {
        if (standsDown(helper)) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Saved before = saved(manager);
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        all.add(recipe(helper, "string", "minecraft:string", 2));
        all.add(recipe(helper, "sticks", "minecraft:stick", 3));
        manager.replaceRecipes(all);
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            InventoryMenu menu = player.inventoryMenu;
            menu.getSlot(1).set(new ItemStack(Items.DEAD_BUSH, 4));
            int scans = RecipeChoice.scans();
            menu.getSlot(2).set(new ItemStack(Items.COBWEB, 4));
            check(helper, RecipeChoice.scans() - scans == 1, "a new grid is looked through once: " + (RecipeChoice.scans() - scans));
            menu.getSlot(2).set(new ItemStack(Items.COBWEB, 5));
            check(helper, RecipeChoice.scans() - scans == 1, "one more of a thing already there: no new look");
            menu.getSlot(2).set(new ItemStack(Items.COBWEB, 4));
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
            check(helper, count(player, Items.STRING) == 8 && RecipeChoice.scans() - scans == 1,
                    "a shift-click of four rounds, the same things each round: still one look (" + (RecipeChoice.scans() - scans)
                            + "), " + count(player, Items.STRING) + " string");
            fill(menu);
            manager.replaceRecipes(new ArrayList<>(manager.getRecipes()));
            int now = RecipeChoice.scans();
            menu.getSlot(2).set(new ItemStack(Items.COBWEB, 3));
            check(helper, RecipeChoice.scans() - now == 1, "the recipes replaced (a /reload): looked through again");

            // the timing: what a player who never picks pays, against vanilla
            var level = helper.getLevel();
            var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, List.of(new ItemStack(Items.DEAD_BUSH), new ItemStack(Items.COBWEB)));
            int rounds = 200;
            for (int i = 0; i < rounds; i++) { // warmed up first, each way, so neither is timed cold
                manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
                RecipeChoice.forget(player);
                RecipeChoice.choose(level, player, menu, input, null);
            }
            long t0 = System.nanoTime();
            for (int i = 0; i < rounds; i++) {
                manager.getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
            }
            long t1 = System.nanoTime();
            for (int i = 0; i < rounds; i++) {
                RecipeChoice.forget(player);
                RecipeChoice.choose(level, player, menu, input, null);
            }
            long t2 = System.nanoTime();
            for (int i = 0; i < rounds; i++) {
                RecipeChoice.choose(level, player, menu, input, null);
            }
            long t3 = System.nanoTime();
            int crafting = manager.getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING).size();
            Nookworks.LOGGER.info("Recipe choice timing over {} crafting recipes: vanilla's lookup {} us, a fresh grid looked through {} us,"
                    + " the same grid again {} us (each the mean of {})", crafting, (t1 - t0) / rounds / 1000, (t2 - t1) / rounds / 1000,
                    (t3 - t2) / rounds / 1000, rounds);
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            restore(manager, before);
        }
        helper.succeed();
    }

    private static void picksAndRemembers(GameTestHelper helper) {
        if (standsDown(helper)) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Saved before = saved(manager);
        RecipeHolder<?> string = recipe(helper, "string", "minecraft:string", 2);
        RecipeHolder<?> sticks = recipe(helper, "sticks", "minecraft:stick", 3);
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        all.add(string);
        all.add(sticks);
        manager.replaceRecipes(all);
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            InventoryMenu menu = player.inventoryMenu;
            fill(menu);
            check(helper, made(menu, Items.STRING, 2), "first the recipe found first, as vanilla: " + menu.getSlot(0).getItem());
            check(helper, List.of(ResourceKey.create(Registries.RECIPE, string.id()), ResourceKey.create(Registries.RECIPE, sticks.id())).equals(RecipeChoice.offered(player)),
                    "and the player is told both: " + RecipeChoice.offered(player));
            RecipeChoice.pick(player, menu.containerId, 1);
            check(helper, made(menu, Items.STICK, 3), "the pick makes sticks: " + menu.getSlot(0).getItem());
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
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
            restore(manager, before);
        }
        helper.succeed();
    }

    /**
     * Sixty-five recipes making as many things of one grid: the player is offered the first {@link RecipeChoice#MAX_OFFERED},
     * which is what the list sent to their game can carry (review r12: offered all 65, its sending threw the player off
     * the server).
     */
    private static void offersWhatItCanSend(GameTestHelper helper) {
        if (standsDown(helper)) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Saved before = saved(manager);
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        List<Item> made = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(RecipeChoice.MAX_OFFERED + 1).toList();
        for (int i = 0; i < made.size(); i++) {
            all.add(recipe(helper, "many_" + i, BuiltInRegistries.ITEM.getKey(made.get(i)).toString(), 1));
        }
        manager.replaceRecipes(all);
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
            restore(manager, before);
        }
        helper.succeed();
    }

    /**
     * What a craft leaves behind is the picked recipe's, by a click on the result and by a shift-click (review r12: the
     * pick made sticks and left the other recipe's leftover). Two recipes of one grid, each leaving its own gem.
     */
    private static void leavesTheMadeRecipesLeftovers(GameTestHelper helper) {
        if (standsDown(helper)) {
            return;
        }
        RecipeManager manager = helper.getLevel().getServer().getRecipeManager();
        Saved before = saved(manager);
        List<RecipeHolder<?>> all = new ArrayList<>(manager.getRecipes());
        all.add(leaving("left_diamond", Items.STRING, Items.DIAMOND));
        all.add(leaving("left_emerald", Items.STICK, Items.EMERALD));
        manager.replaceRecipes(all);
        try {
            ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
            InventoryMenu menu = player.inventoryMenu;
            fill(menu);
            check(helper, menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.STRING), "first, string");
            RecipeChoice.pick(player, menu.containerId, 1);
            check(helper, menu.getSlot(InventoryMenu.RESULT_SLOT).getItem().is(Items.STICK), "picked, sticks");
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
            check(helper, count(player, Items.EMERALD) == 1 && count(player, Items.DIAMOND) == 0,
                    "a click leaves the sticks' emerald: " + count(player, Items.EMERALD) + " emerald, " + count(player, Items.DIAMOND) + " diamond");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(InventoryMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
            // the last round's leftover stays in the emptied square, as vanilla leaves a bucket there
            check(helper, count(player, Items.EMERALD) == 3 && menu.getSlot(1).getItem().is(Items.EMERALD) && count(player, Items.DIAMOND) == 0
                    && menu.slots.stream().noneMatch(slot -> slot.getItem().is(Items.DIAMOND)),
                    "and a shift-click, each round: " + count(player, Items.EMERALD) + " emerald and " + menu.getSlot(1).getItem() + " in the grid, "
                            + count(player, Items.DIAMOND) + " diamond");
            helper.getLevel().getServer().getPlayerList().remove(player);
        } finally {
            restore(manager, before);
        }
        helper.succeed();
    }

    /** A dead bush and a cobweb making {@code result}, and leaving {@code left} in the bush's square. */
    private static RecipeHolder<?> leaving(String name, Item result, Item left) {
        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC,
                new ItemStack(result), NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.DEAD_BUSH), Ingredient.of(Items.COBWEB))) {
            @Override
            public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
                NonNullList<ItemStack> leftovers = NonNullList.withSize(input.size(), ItemStack.EMPTY);
                leftovers.set(0, new ItemStack(left));
                return leftovers;
            }
        };
        return new RecipeHolder<>(Nookworks.id("test/recipe_choice_" + name), recipe);
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
        return player.getInventory().items.stream().filter(s -> s.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static RecipeHolder<?> recipe(GameTestHelper helper, String name, String result, int count) {
        String json = "{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:dead_bush\"},{\"item\":\"minecraft:cobweb\"}],"
                + "\"result\":{\"id\":\"" + result + "\",\"count\":" + count + "}}";
        Recipe<?> recipe = Recipe.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess()),
                JsonParser.parseString(json)).getOrThrow();
        return new RecipeHolder<>(Nookworks.id("test/recipe_choice_" + name), recipe);
    }

    /**
     * The server's recipes as they were (26.3's RecipeMap): 1.21.1's manager keeps them in two maps, by type and by id,
     * which are put back as they were - not built again - when the test ends.
     */
    private record Saved(Object byType, Object byName) {}

    private static Saved saved(RecipeManager manager) {
        try {
            return new Saved(field("byType").get(manager), field("byName").get(manager));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void restore(RecipeManager manager, Saved saved) {
        try {
            field("byType").set(manager, saved.byType());
            field("byName").set(manager, saved.byName());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = RecipeManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}
