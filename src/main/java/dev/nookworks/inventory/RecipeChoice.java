package dev.nookworks.inventory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
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
        List<RecipeHolder<CraftingRecipe>> matches = level.recipeAccess().recipeMap().getRecipesFor(RecipeType.CRAFTING, input, level).toList();
        // one recipe for each thing made, in the order vanilla finds them; nothing made, or not in this world, is no choice
        List<RecipeHolder<CraftingRecipe>> distinct = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        for (RecipeHolder<CraftingRecipe> recipe : matches) {
            ItemStack made = recipe.value().assemble(input);
            if (distinct.size() < MAX_OFFERED && !made.isEmpty() && made.isItemEnabled(level.enabledFeatures())
                    && outputs.stream().noneMatch(o -> ItemStack.matches(o, made))) {
                distinct.add(recipe);
                outputs.add(made);
            }
        }
        RecipeHolder<CraftingRecipe> chosen = hint != null && matches.contains(hint) ? hint : null;
        if (chosen == null && distinct.size() > 1) {
            for (ResourceKey<Recipe<?>> picked : PICKED.getOrDefault(player.getUUID(), new ArrayDeque<>())) {
                chosen = distinct.stream().filter(r -> r.id().equals(picked)).findFirst().orElse(null);
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

    /** Tells the player what their grid could make, when that changes; an empty list takes the button away. */
    private static void offer(ServerPlayer player, int containerId, List<RecipeHolder<CraftingRecipe>> recipes, List<ItemStack> outputs,
            @Nullable RecipeHolder<CraftingRecipe> chosen) {
        List<ResourceKey<Recipe<?>>> keys = recipes.stream().map(RecipeHolder::id).toList();
        int selected = chosen == null ? -1 : keys.indexOf(chosen.id());
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
        if (!active() || offer == null || offer.containerId() != containerId || player.containerMenu.containerId != containerId
                || index < 0 || index >= offer.recipes().size() || !(player.containerMenu instanceof AbstractCraftingMenu menu)) {
            return;
        }
        Deque<ResourceKey<Recipe<?>>> picked = PICKED.computeIfAbsent(player.getUUID(), p -> new ArrayDeque<>());
        ResourceKey<Recipe<?>> key = offer.recipes().get(index);
        picked.remove(key);
        picked.addFirst(key);
        while (picked.size() > REMEMBERED) {
            picked.removeLast();
        }
        menu.slotsChanged(menu.getInputGridSlots().getFirst().container); // the result made again, with the pick
    }

    /**
     * The player's game lost sight of its grid's result (a look in JEI empties it on that side only - see
     * RecipeChoiceClient): the menu is sent again whole. Only for a player offered a choice there, in the menu they have
     * open.
     */
    public static void showAgain(ServerPlayer player, int containerId) {
        Offer offer = OFFERED.get(player);
        if (offer != null && offer.containerId() == containerId && player.containerMenu.containerId == containerId
                && player.containerMenu instanceof AbstractCraftingMenu) {
            player.containerMenu.sendAllDataToRemote();
        }
    }

    /** For tests: the recipes a player was last offered, in order, and which is made. */
    public static @Nullable List<ResourceKey<Recipe<?>>> offered(ServerPlayer player) {
        Offer offer = OFFERED.get(player);
        return offer == null ? null : offer.recipes();
    }
}
