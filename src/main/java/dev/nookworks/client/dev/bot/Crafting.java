package dev.nookworks.client.dev.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import dev.nookworks.dev.bot.path.Goal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Blocks;

/**
 * Crafting as a player crafts. A recipe the recipe book has is laid in the grid by the book (the game's own
 * place-recipe click). One it has not unlocked yet - a fresh survival player knows few - is read from the game's recipes
 * (in singleplayer: the integrated server's) and laid in by hand, an item at a time, as a person does without the book.
 * Ingredients it lacks that can be crafted (planks from logs, sticks from planks) are crafted first, a few levels deep.
 */
final class Crafting {
    private Crafting() {}

    /**
     * A way to craft an item: how it is laid out, and the book's recipe for it when the book has it. PORT: 26.3's client
     * sees recipes as the book's displays; 1.21.1's has the recipes themselves (shaped, or any other crafting recipe with
     * ingredients, laid out as shapeless).
     */
    record Way(RecipeHolder<CraftingRecipe> display, @Nullable RecipeHolder<CraftingRecipe> bookId) {
        int width() {
            return this.display.value() instanceof ShapedRecipe s ? s.getWidth() : 3;
        }

        int height() {
            return this.display.value() instanceof ShapedRecipe s ? s.getHeight() : 3;
        }

        List<Ingredient> slots() {
            return this.display.value().getIngredients();
        }

        boolean needsTable() {
            if (this.display.value() instanceof ShapedRecipe s) {
                return s.getWidth() > 2 || s.getHeight() > 2;
            }
            return this.slots().size() > 4;
        }
    }

    static boolean makes(Minecraft mc, RecipeHolder<CraftingRecipe> display, Item item) {
        return display.value().getResultItem(mc.level.registryAccess()).is(item);
    }

    static int resultCount(Minecraft mc, RecipeHolder<CraftingRecipe> display) {
        return Math.max(1, display.value().getResultItem(mc.level.registryAccess()).getCount());
    }

    @SuppressWarnings("unchecked")
    private static @Nullable RecipeHolder<CraftingRecipe> crafting(RecipeHolder<?> holder) {
        return holder.value() instanceof CraftingRecipe recipe && isCrafting(recipe) ? (RecipeHolder<CraftingRecipe>) holder : null;
    }

    /** Whether any recipe it knows makes the item (bread from wheat is not one with Nookworks' flour bakery on). */
    static boolean canCraft(Minecraft mc, Item item) {
        return !ways(mc, item).isEmpty();
    }

    /** Every way to craft the item: the book's first, then the game's (singleplayer). */
    static List<Way> ways(Minecraft mc, Item item) {
        List<Way> ways = new ArrayList<>();
        for (var collection : mc.player.getRecipeBook().getCollections()) {
            for (RecipeHolder<?> entry : collection.getRecipes()) {
                // PORT: 1.21.1's book lists every recipe; those it has unlocked are its own
                RecipeHolder<CraftingRecipe> recipe = crafting(entry);
                if (recipe != null && mc.player.getRecipeBook().contains(entry) && makes(mc, recipe, item)) {
                    ways.add(new Way(recipe, recipe));
                }
            }
        }
        var server = mc.getSingleplayerServer();
        if (server != null) {
            List<RecipeHolder<CraftingRecipe>> found = server.submit(() -> {
                List<RecipeHolder<CraftingRecipe>> out = new ArrayList<>();
                for (RecipeHolder<CraftingRecipe> holder : server.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)) {
                    if (crafting(holder) != null) {
                        out.add(holder);
                    }
                }
                return out;
            }).join();
            for (RecipeHolder<CraftingRecipe> d : found) {
                if (makes(mc, d, item)) {
                    ways.add(new Way(d, null));
                }
            }
        }
        return ways;
    }

    /** PORT: 26.3's shaped and shapeless displays: a recipe with something to lay in the grid (not a special one). */
    private static boolean isCrafting(CraftingRecipe d) {
        return !d.isSpecial() && !d.getIngredients().isEmpty();
    }

    /**
     * Which inventory stack each non-empty slot of the recipe takes, or, where nothing in the inventory fits, the item to
     * craft first (the first choice that can be crafted) - all given out of the inventory as it is, an item per slot.
     */
    record Allotment(int[] from, List<Item> missing) {}

    static Allotment allot(Minecraft mc, Way way) {
        LocalPlayer player = mc.player;
        Inventory inv = player.getInventory();
        int[] left = new int[36];
        for (int i = 0; i < 36; i++) {
            left[i] = inv.getItem(i).getCount();
        }
        List<Ingredient> slots = way.slots();
        int[] from = new int[slots.size()];
        List<Item> missing = new ArrayList<>();
        for (int s = 0; s < slots.size(); s++) {
            from[s] = -1;
            List<ItemStack> choices = List.of(slots.get(s).getItems());
            if (choices.isEmpty()) {
                continue; // an empty place in the pattern
            }
            for (int i = 0; i < 36 && from[s] < 0; i++) {
                if (left[i] <= 0) {
                    continue;
                }
                for (ItemStack choice : choices) {
                    if (inv.getItem(i).is(choice.getItem())) {
                        from[s] = i;
                        left[i]--;
                        break;
                    }
                }
            }
            if (from[s] < 0) {
                missing.add(choices.get(0).getItem());
                for (ItemStack choice : choices) {
                    if (!ways(mc, choice.getItem()).isEmpty()) {
                        missing.set(missing.size() - 1, choice.getItem());
                        break;
                    }
                }
            }
        }
        return new Allotment(from, missing);
    }

    /** Crafts {@code count} of an item (see the class note); to a crafting table when the recipe wants one. */
    static final class Craft implements Task, Bot.ScreenTask {
        private final Item item;
        private final int count;
        private final int depth;
        private int startCount = -1;
        private @Nullable Task sub;
        private @Nullable BlockPos table;
        private @Nullable Goal goal;
        private int opened;
        private int wait;
        /** Clicks of a hand-laid recipe still to make (each a menu slot, a button, a click type). */
        private final List<int[]> clicks = new ArrayList<>();
        private int stalls;
        private int lastMade = -1;

        Craft(Item item, int count) {
            this(item, count, 0);
        }

        Craft(Item item, int count, int depth) {
            this.item = item;
            this.count = count;
            this.depth = depth;
        }

        private int takeTries;

        private int made(LocalPlayer player) {
            return Inv.count(player, s -> s.is(this.item)) - this.startCount;
        }

        @Override
        public String describe() {
            return "craft " + this.count + " " + Bot.name(this.item);
        }

        @Override
        public Result tick(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.startCount < 0) {
                this.startCount = Inv.count(player, s -> s.is(this.item));
            }
            if (this.sub != null) {
                Result r = this.sub.tick(mc, c);
                if (r == Result.RUNNING) {
                    return r;
                }
                this.sub.cancel(mc);
                this.sub = null;
                if (r == Result.FAILED) {
                    return Result.FAILED;
                }
            }
            if (this.wait > 0) {
                this.wait--;
                return Result.RUNNING;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!this.clicks.isEmpty()) {
                int[] click = this.clicks.remove(0);
                Inv.click(mc, menu, click[0], click[1], ClickType.values()[click[2]]);
                if (this.clicks.isEmpty()) {
                    this.wait = 2;
                }
                return Result.RUNNING;
            }
            int made = this.made(player);
            if (made >= this.count) {
                ItemTasks.closeScreen(mc);
                return Result.DONE;
            }
            // the result waiting in the grid: take it
            if ((menu instanceof CraftingMenu || menu == player.inventoryMenu) && menu.getSlot(0).getItem().is(this.item)) {
                if (++this.takeTries > 5) {
                    // the bag is full: what was made has nowhere to go (it waited on it for good, 2026-10-22)
                    ItemTasks.closeScreen(mc);
                    Bot.fail("the bag is full: no room for " + Bot.name(this.item));
                    return Result.FAILED;
                }
                Inv.click(mc, menu, 0, 0, ClickType.QUICK_MOVE);
                this.wait = 2;
                return Result.RUNNING;
            }
            this.takeTries = 0;
            List<Way> ways = ways(mc, this.item);
            if (ways.isEmpty()) {
                // on a server the client knows only the recipe book's recipes: those unlocked (most by holding one of
                // their ingredients - a shield's by iron)
                Bot.fail(mc.getSingleplayerServer() != null ? "no recipe makes " + Bot.name(this.item)
                        : "no recipe for " + Bot.name(this.item) + " in the recipe book: not unlocked yet (most unlock when one"
                                + " of their ingredients is first held), or there is none");
                return Result.FAILED;
            }
            Way way = null;
            Allotment allot = null;
            for (Way w : ways) {
                Allotment a = allot(mc, w);
                if (a.missing().isEmpty()) {
                    way = w;
                    allot = a;
                    break;
                }
            }
            if (way == null) {
                Allotment first = allot(mc, ways.get(0));
                if (this.depth >= 4) {
                    Bot.fail("missing " + first.missing().stream().map(Bot::name).toList() + " for " + Bot.name(this.item));
                    return Result.FAILED;
                }
                ItemTasks.closeScreen(mc);
                for (Item m : first.missing()) {
                    if (!ways(mc, m).isEmpty()) {
                        int n = (int) first.missing().stream().filter(x -> x == m).count();
                        // enough for the rest of what is wanted
                        int crafts = (int) Math.ceil((this.count - made) / (double) resultCount(mc, ways.get(0).display()));
                        this.sub = new Craft(m, n * crafts, this.depth + 1);
                        return Result.RUNNING;
                    }
                }
                Bot.fail("missing " + first.missing().stream().map(Bot::name).toList() + " for " + Bot.name(this.item));
                return Result.FAILED;
            }
            if (way.needsTable() && !(menu instanceof CraftingMenu)) {
                return this.toTable(mc, c);
            }
            if (!way.needsTable() && !(menu instanceof CraftingMenu)) {
                menu = player.inventoryMenu;
            }
            // a try at the grid: if the last one came to nothing, and the ones before, something is wrong
            if (made == this.lastMade) {
                if (++this.stalls > 5) {
                    ItemTasks.closeScreen(mc);
                    Bot.fail("the crafting did not take");
                    return Result.FAILED;
                }
            } else {
                this.stalls = 0;
                this.lastMade = made;
            }
            if (way.bookId() != null) {
                mc.gameMode.handlePlaceRecipe(menu.containerId, way.bookId(), false);
                this.wait = 3;
                return Result.RUNNING;
            }
            this.layByHand(player, menu, way, allot);
            return Result.RUNNING;
        }

        /** The clicks that lay one craft's ingredients in the grid: each stack picked up, one item put down, put back. */
        private void layByHand(LocalPlayer player, AbstractContainerMenu menu, Way way, Allotment allot) {
            int grid = menu instanceof CraftingMenu ? 3 : 2;
            Inventory inv = player.getInventory();
            List<Ingredient> slots = way.slots();
            for (int s = 0; s < slots.size(); s++) {
                if (allot.from()[s] < 0) {
                    continue;
                }
                int gridSlot;
                if (way.display().value() instanceof ShapedRecipe) {
                    gridSlot = 1 + (s / way.width()) * grid + s % way.width();
                } else {
                    gridSlot = 1 + s; // any place in the grid will do
                }
                int invSlot = Inv.menuSlot(menu, inv, allot.from()[s]);
                this.clicks.add(new int[] {invSlot, 0, ClickType.PICKUP.ordinal()});
                this.clicks.add(new int[] {gridSlot, 1, ClickType.PICKUP.ordinal()});
                this.clicks.add(new int[] {invSlot, 0, ClickType.PICKUP.ordinal()});
            }
        }

        /** To a crafting table, and open it: one near, one set down, one made first. */
        private Result toTable(Minecraft mc, Controls c) {
            LocalPlayer player = mc.player;
            if (this.table == null || !mc.level.getBlockState(this.table).is(Blocks.CRAFTING_TABLE)) {
                this.table = Scan.nearest(mc, s -> s.is(Blocks.CRAFTING_TABLE), 24, Set.of());
                this.goal = null;
                if (this.table == null) {
                    if (Inv.find(player, s -> s.is(Items.CRAFTING_TABLE)) >= 0 || player.getAbilities().instabuild) {
                        BlockPos spot = ItemTasks.freeSpot(mc);
                        if (spot == null) {
                            Bot.fail("nowhere to set a crafting table down");
                            return Result.FAILED;
                        }
                        this.sub = new WorkTasks.Build(List.of(new WorkTasks.Target(spot, Blocks.CRAFTING_TABLE.defaultBlockState())),
                                "set a crafting table down");
                        return Result.RUNNING;
                    }
                    this.sub = new Craft(Items.CRAFTING_TABLE, 1, this.depth + 1);
                    return Result.RUNNING;
                }
            }
            if (this.goal == null || !Bot.walker.isFor(this.goal)) {
                this.goal = new WorkTasks.ReachGoal(this.table);
                Bot.walker.go(this.goal);
            }
            if (Bot.walker.state() != Walker.State.DONE) {
                Bot.walker.tick(mc, c);
                if (Bot.walker.state() == Walker.State.FAILED) {
                    Bot.fail("cannot get to the crafting table");
                    return Result.FAILED;
                }
                return Result.RUNNING;
            }
            return ItemTasks.useBlock(mc, c, this.table, ++this.opened) ? Result.RUNNING : Result.FAILED;
        }

        @Override
        public void cancel(Minecraft mc) {
            ItemTasks.closeScreen(mc);
            Bot.walker.stop(mc);
            if (this.sub != null) {
                this.sub.cancel(mc);
            }
        }
    }

    @SuppressWarnings("unused")
    private static Map<Item, Integer> tally(List<Item> items) {
        Map<Item, Integer> out = new HashMap<>();
        for (Item i : items) {
            out.merge(i, 1, Integer::sum);
        }
        return out;
    }
}
