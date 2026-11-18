package dev.nookworks.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.NookworksNetwork;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Picking among recipes that make different things from the same crafting grid (the user, 2026-10-10: 他のmodとレシピ
 * 干渉した時に、作るアイテムを選べる機能; off with Polymorph, which does it). Vanilla makes whatever recipe it finds first;
 * here every recipe the grid matches is looked at, and when they make more than one thing, the player is told what they
 * could make (a button by the result, client.RecipeChoiceClient) and makes the one they pick. A pick is remembered for
 * the player - the newest first, a few dozen - so the same grid makes it again, shift-clicked batches too, until they
 * pick otherwise; it lasts until they leave the game. The crafting table, the inventory's own grid, and any other mod's
 * table built on vanilla's crafting menu (mixin CraftingMenuMixin); what is left in the grid after crafting - a bucket,
 * a bottle - is the picked recipe's (mixin ResultSlotMixin), where vanilla looked the first one up again.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class RecipeChoice {
    /** Mods that let the player pick among recipes themselves: with one installed, ours stands down. */
    private static final List<String> OTHER_CHOOSERS = List.of("polymorph");
    private static final boolean STANDS_DOWN = OTHER_CHOOSERS.stream().anyMatch(ModList.get()::isLoaded);
    /** How many picks a player's choices remember. */
    private static final int REMEMBERED = 48;
    /**
     * Each player's picks, the newest first, by who they are: kept on the player object, they were lost once the one that
     * died (or left the End) was gone, the respawned player being a new object (r21, 2026-11-06). Let go when they leave.
     */
    private static final Map<UUID, Deque<ResourceKey<Recipe<?>>>> PICKED = new HashMap<>();
    /** What each player was last told they could make: the menu, and a recipe for each thing, in the order shown. */
    private static final Map<ServerPlayer, Offer> OFFERED = new WeakHashMap<>();

    record Offer(int containerId, List<ResourceKey<Recipe<?>>> recipes, int selected) {}

    @SubscribeEvent
    static void onLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        PICKED.remove(event.getEntity().getUUID());
    }

    /** Where the client takes what it is told (set by client.RecipeChoiceClient; nothing on a server). */
    public static java.util.function.Consumer<NookworksNetwork.RecipeChoices> shown = choices -> {};

    static {
        if (STANDS_DOWN) {
            Nookworks.LOGGER.info("Nookworks' recipe choice stands down: {} picks among recipes.",
                    OTHER_CHOOSERS.stream().filter(ModList.get()::isLoaded).toList());
        }
    }

    private RecipeChoice() {}

    /**
     * The most things a grid is offered: more than a row of the picker can sensibly hold, and what the list sent can
     * carry. Matches past it are not offered (a mod pack with 65 recipes for one grid once lost its player the server).
     */
    public static final int MAX_OFFERED = 64;

    /** Whether ours stands down for another mod that lets the player pick ({@link #OTHER_CHOOSERS}). */
    public static boolean standsDown() {
        return STANDS_DOWN;
    }

    public static boolean active() {
        return !STANDS_DOWN && NookworksConfig.SPEC.isLoaded() && NookworksConfig.RECIPE_CHOICE.get();
    }

    /**
     * The recipe {@code input} makes for {@code player} in {@code menu}, in place of vanilla's first one found: the hinted
     * one (the recipe book's) if it matches, else the newest of the player's picks among the matches, else vanilla's.
     * When the matches make more than one thing, the player is told what they could make.
     */
    public static Optional<RecipeHolder<CraftingRecipe>> choose(ServerLevel level, ServerPlayer player, AbstractContainerMenu menu,
            CraftingInput input, @Nullable RecipeHolder<CraftingRecipe> hint) {
        Scan scan = scan(level, player, input);
        if (scan == null) {
            // a grid passed through as a craft takes its makings away one square at a time: vanilla's first recipe for it
            // (the hinted one first, as vanilla), and the grid looked at once more as it is left when the take is done
            return level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level, hint);
        }
        List<RecipeHolder<CraftingRecipe>> matches = scan.matches();
        List<RecipeHolder<CraftingRecipe>> distinct = scan.distinct();
        List<ItemStack> outputs = scan.outputs();
        RecipeHolder<CraftingRecipe> chosen = hint != null && matches.contains(hint) ? hint : null;
        if (chosen == null && distinct.size() > 1) {
            for (ResourceKey<Recipe<?>> picked : PICKED.getOrDefault(player.getUUID(), new ArrayDeque<>())) {
                chosen = distinct.stream().filter(r -> key(r).equals(picked)).findFirst().orElse(null);
                if (chosen != null) {
                    break;
                }
            }
        }
        if (chosen == null && !matches.isEmpty()) {
            chosen = matches.getFirst();
        }
        offer(player, menu.containerId, distinct.size() > 1 ? distinct : List.of(), outputs, chosen);
        return Optional.ofNullable(chosen);
    }

    /**
     * What a grid matches: every recipe, another mod's before ours (Nookworks is a guest: where a recipe of ours takes
     * the same grid as theirs, theirs is what the grid makes until the player picks ours - compat.RecipeCrossings also
     * puts ours after theirs in the server's own order, for vanilla's first match), and one recipe for each thing made.
     */
    private record Scan(Object recipes, int width, int height, List<ItemStack> kinds, List<RecipeHolder<CraftingRecipe>> matches,
            List<RecipeHolder<CraftingRecipe>> distinct, List<ItemStack> outputs) {
        /**
         * Whether this was a scan of a grid holding the same things as {@code input} (counts aside), among {@code recipes}
         * (the manager's own collection, made anew whenever its recipes are replaced: a /reload, or a crossing's change).
         */
        boolean of(Object recipes, CraftingInput input) {
            if (this.recipes != recipes || this.width != input.width() || this.height != input.height()) {
                return false;
            }
            for (int i = 0; i < this.kinds.size(); i++) {
                if (!ItemStack.isSameItemSameComponents(this.kinds.get(i), input.getItem(i))) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Each player's last scan. A grid's recipes depend on what it holds, not how many of each (a crafting recipe takes one
     * from every square): while the grid holds the same things - each round of a shift-click craft, a stack put down on
     * one already there - the last scan is the answer, and every crafting recipe is looked through only when the grid's
     * things change (vanilla looks through them until its first match each time). A /reload, or any other change to the
     * server's recipes, makes a new collection of them, which no scan was of.
     */
    private static final Map<ServerPlayer, Scan> SCANNED = new WeakHashMap<>();

    /** How many full scans have been made since the game started (for the tests). */
    private static int scans;

    public static int scans() {
        return scans;
    }

    /** For the tests' timing: the player's last scan forgotten, so the next grid is looked through again. */
    public static void forget(ServerPlayer player) {
        SCANNED.remove(player);
    }

    /** Players whose craft is being taken now (ResultSlotMixin), and those whose grid went by unlooked-at meanwhile. */
    private static final Set<ServerPlayer> TAKING = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Set<ServerPlayer> PASSED = Collections.newSetFromMap(new WeakHashMap<>());

    /**
     * A craft's take begins ({@code on}) or ends for {@code player}. Between, the grid changes a square at a time as its
     * makings are used up (a shift-click: every round), and each grid it passes through on the way is not looked through
     * - one with a square already empty is not one the player laid out. Once done, {@code again} gives the grid as it is
     * left to the menu again, if one went by: looked through once, if its things are new.
     */
    public static void taking(ServerPlayer player, boolean on, Runnable again) {
        if (on) {
            TAKING.add(player);
            return;
        }
        TAKING.remove(player);
        if (PASSED.remove(player)) {
            again.run();
        }
    }

    /** The grid's scan; null while a take passes through it (see {@link #taking}). */
    private static @Nullable Scan scan(ServerLevel level, ServerPlayer player, CraftingInput input) {
        RecipeManager manager = level.getRecipeManager();
        Scan last = SCANNED.get(player);
        Object recipes = manager.getRecipes();
        if (last != null && last.of(recipes, input)) {
            return last;
        }
        if (input.isEmpty()) {
            return new Scan(recipes, 0, 0, List.of(), List.of(), List.of(), List.of()); // nothing to look through
        }
        if (TAKING.contains(player)) {
            PASSED.add(player);
            return null;
        }
        scans++;
        List<ItemStack> kinds = new ArrayList<>(input.size());
        for (int i = 0; i < input.size(); i++) {
            kinds.add(input.getItem(i).copyWithCount(1));
        }
        // (1.21.1's getRecipesFor sorts the matches by name: they are filtered here in the order getRecipeFor finds them,
        // then another mod's put before ours, keeping that order among each)
        List<RecipeHolder<CraftingRecipe>> matches = input.isEmpty() ? List.of() : manager
                .getAllRecipesFor(RecipeType.CRAFTING).stream().filter(r -> r.value().matches(input, level))
                .sorted(java.util.Comparator.comparing(r -> r.id().getNamespace().equals(Nookworks.MODID))).toList();
        // one recipe for each thing made; nothing made, or not in this world, is no choice
        List<RecipeHolder<CraftingRecipe>> distinct = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> recipe : matches) {
            ItemStack made = recipe.value().assemble(input, level.registryAccess());
            if (distinct.size() < MAX_OFFERED && !made.isEmpty() && made.isItemEnabled(level.enabledFeatures())
                    && outputs.stream().noneMatch(o -> ItemStack.matches(o, made))) {
                distinct.add(recipe);
                outputs.add(made);
            }
        }
        Scan scan = new Scan(recipes, input.width(), input.height(), List.copyOf(kinds), matches, List.copyOf(distinct), List.copyOf(outputs));
        SCANNED.put(player, scan);
        return scan;
    }

    /** Tells the player what their grid could make, when that changes; an empty list takes the button away. */
    private static void offer(ServerPlayer player, int containerId, List<RecipeHolder<CraftingRecipe>> recipes, List<ItemStack> outputs,
            @Nullable RecipeHolder<CraftingRecipe> chosen) {
        List<ResourceKey<Recipe<?>>> keys = recipes.stream().map(RecipeChoice::key).toList();
        int selected = chosen == null ? -1 : keys.indexOf(key(chosen));
        Offer offer = new Offer(containerId, keys, selected);
        Offer before = OFFERED.get(player);
        if (offer.equals(before) || keys.isEmpty() && (before == null || before.recipes().isEmpty())) {
            return;
        }
        OFFERED.put(player, offer);
        if (!player.connection.hasChannel(NookworksNetwork.RecipeChoices.TYPE)) {
            return; // a client without Nookworks (or a test's stand-in) is shown nothing, and makes what it is offered first
        }
        PacketDistributor.sendToPlayer(player, new NookworksNetwork.RecipeChoices(containerId,
                keys.isEmpty() ? List.of() : List.copyOf(outputs.subList(0, keys.size())), selected));
    }

    /**
     * With the setting turned off while a player has a grid's things before them, the button is taken away (the grid
     * makes vanilla's first recipe from then on, and a pick would do nothing).
     */
    public static void withdraw(ServerPlayer player, int containerId) {
        Offer before = OFFERED.remove(player);
        if (before != null && !before.recipes().isEmpty() && player.connection.hasChannel(NookworksNetwork.RecipeChoices.TYPE)) {
            PacketDistributor.sendToPlayer(player, new NookworksNetwork.RecipeChoices(containerId, List.of(), -1));
        }
    }

    /** The player picked the {@code index}th thing they were offered in menu {@code containerId}: their grid makes it now. */
    public static void pick(ServerPlayer player, int containerId, int index) {
        Offer offer = OFFERED.get(player);
        if (!active() || player.isSpectator() || offer == null || offer.containerId() != containerId || player.containerMenu.containerId != containerId
                || index < 0 || index >= offer.recipes().size() || !(player.containerMenu instanceof RecipeBookMenu<?, ?> menu && crafting(menu))) {
            return;
        }
        Deque<ResourceKey<Recipe<?>>> picked = PICKED.computeIfAbsent(player.getUUID(), p -> new ArrayDeque<>());
        ResourceKey<Recipe<?>> key = offer.recipes().get(index);
        picked.remove(key);
        picked.addFirst(key);
        while (picked.size() > REMEMBERED) {
            picked.removeLast();
        }
        // the result made again, with the pick (the grid's first slot is the one after the result, in both menus)
        menu.slotsChanged(menu.getSlot(menu.getResultSlotIndex() + 1).container);
    }

    /**
     * The player's game lost sight of its grid's result (a look in JEI empties it on that side only - see
     * RecipeChoiceClient): the menu is sent again whole. Only for a player offered a choice there, in the menu they have
     * open.
     */
    public static void showAgain(ServerPlayer player, int containerId) {
        Offer offer = OFFERED.get(player);
        if (offer != null && offer.containerId() == containerId && player.containerMenu.containerId == containerId
                && crafting(player.containerMenu)) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    /**
     * PORT: 26.3's {@code AbstractCraftingMenu}, the crafting table's and the inventory's common parent, which 1.21.1
     * lacks (its two are RecipeBookMenus, as the furnaces are): a crafting grid's menu is one of the two, or a mod's
     * built on them.
     */
    public static boolean crafting(AbstractContainerMenu menu) {
        return menu instanceof CraftingMenu || menu instanceof InventoryMenu;
    }

    /** A recipe's id as 26.3 keeps it (a recipe key): 1.21.1's holders carry the bare location. */
    private static ResourceKey<Recipe<?>> key(RecipeHolder<?> recipe) {
        return ResourceKey.create(Registries.RECIPE, recipe.id());
    }

    /** For tests: the recipes a player was last offered, in order, and which is made. */
    public static @Nullable List<ResourceKey<Recipe<?>>> offered(ServerPlayer player) {
        Offer offer = OFFERED.get(player);
        return offer == null ? null : offer.recipes();
    }
}
