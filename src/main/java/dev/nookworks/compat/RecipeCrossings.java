package dev.nookworks.compat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.farmersdelight.FdKitchen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Everything that changes the server's recipes at run time, in one fixed order, once for each set of recipes the server
 * loads (at start, and after each /reload, before the recipes go to the players):
 * <ol>
 * <li>shared materials: a furnace, smoker, blast furnace or campfire recipe of ours that takes the same things as
 * another mod's for a different item of the same material (Raw Zinc: our Zinc Ingot, Create's by {@code
 * #c:raw_materials/zinc}) stands down, when the other takes everything ours takes - so a station has one result;</li>
 * <li>crafting grids shared with another mod's recipe ({@link CraftingClashes}): ours stands down for the same material,
 * and comes after theirs for anything else;</li>
 * <li>Farmer's Delight's kitchen and ours ({@link FdKitchen}: twins widened, its pot dishes in ours and ours in its);</li>
 * <li>Create's machines and ours ({@code compat.create.CreateRecipes}, set here by name only with Create installed) -
 * last, so it sees the twins already widened and the Create recipes as Farmer's Delight left them, and makes one recipe
 * for a set of twins rather than one each that the widening would then double.</li>
 * </ol>
 * Before this, each part listened for the events itself, and which ran first was up to the order of registration: with
 * both mods installed, Create's crossing ran first and made a Millstone recipe for our Brown Rice and one for Farmer's
 * Delight's Rice, which the twin widening then made both take either.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class RecipeCrossings {
    private RecipeCrossings() {}

    /** The forms a common tag names a material by ({@code c:<form>/<material>}). */
    private static final List<String> FORMS = List.of("ingots", "nuggets", "dusts", "raw_materials", "storage_blocks", "gems");

    private static final Set<RecipeManager> DONE = Collections.newSetFromMap(new WeakHashMap<>());
    private static volatile @Nullable Consumer<MinecraftServer> create;
    private static volatile List<ResourceLocation> stoodDown = List.of();

    /** Create's crossing, run last (from compat.create, only with Create installed). */
    public static void create(Consumer<MinecraftServer> crossing) {
        create = crossing;
    }

    /** Our recipes the last run stood down for another mod's of the same material (for the log and the tests). */
    public static List<ResourceLocation> materialsStoodDown() {
        return stoodDown;
    }

    /** Whether {@code id}, a recipe of ours, was stood down at run time (for another mod's, either rule). */
    public static boolean stoodDown(ResourceLocation id) {
        return stoodDown.contains(id) || CraftingClashes.last().stoodDown().contains(id) || FdKitchen.last().stoodDown().contains(id);
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        run(event.getServer());
    }

    /** Before the recipes go out: to a player joining, and to everyone after a /reload. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onSync(OnDatapackSyncEvent event) {
        run(event.getPlayerList().getServer());
        FdKitchen.giveBook(event);
    }

    /** Every run-time change to the server's recipes, in order, once for each recipe manager. */
    public static synchronized void run(MinecraftServer server) {
        if (!DONE.add(server.getRecipeManager())) {
            return;
        }
        sharedMaterials(server);
        CraftingClashes.run(server);
        FdKitchen.cross(server);
        Consumer<MinecraftServer> then = create;
        if (then != null) {
            then.accept(server);
        }
    }

    // ---- 1. shared materials ----

    private static void sharedMaterials(MinecraftServer server) {
        try {
            RecipeManager manager = server.getRecipeManager();
            RecipeOverlap overlap = new RecipeOverlap();
            List<RecipeOverlap.Entry> stations = RecipeOverlap.entries(manager.getRecipes(), server.registryAccess()).stream()
                    .filter(e -> e.place().startsWith("minecraft:")).toList();
            List<ResourceLocation> out = new ArrayList<>();
            for (RecipeOverlap.Entry[] pair : overlap.fights(stations)) {
                boolean first = ours(pair[0]);
                if (first == ours(pair[1])) {
                    continue; // two of ours, or none: not this rule's (the data check reports ours)
                }
                RecipeOverlap.Entry mine = first ? pair[0] : pair[1];
                RecipeOverlap.Entry theirs = first ? pair[1] : pair[0];
                if (sameMaterial(mine.result(), theirs.result()) && overlap.covers(theirs, mine) && !out.contains(mine.id())) {
                    out.add(mine.id());
                }
            }
            if (!out.isEmpty()) {
                Map<ResourceLocation, RecipeHolder<?>> kept = new java.util.LinkedHashMap<>();
                manager.getRecipes().forEach(holder -> kept.put(holder.id(), holder));
                out.forEach(kept::remove);
                manager.replaceRecipes(List.copyOf(kept.values()));
                Nookworks.LOGGER.info("{} recipes of ours stand down for another mod's of the same material: {}", out.size(), out);
            }
            stoodDown = List.copyOf(out);
        } catch (RuntimeException e) {
            stoodDown = List.of();
            Nookworks.LOGGER.error("Could not look for recipes of the same material; each stays as it is", e);
        }
    }

    private static boolean ours(RecipeOverlap.Entry entry) {
        return entry.id().getNamespace().equals(Nookworks.MODID);
    }

    /** Whether two items share a common tag naming one material in one form ({@code c:ingots/zinc}, say). */
    static boolean sameMaterial(Item a, Item b) {
        List<TagKey<Item>> mine = materials(a);
        return !mine.isEmpty() && materials(b).stream().anyMatch(mine::contains);
    }

    private static List<TagKey<Item>> materials(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).tags().filter(tag -> {
            ResourceLocation id = tag.location();
            int slash = id.getPath().indexOf('/');
            return id.getNamespace().equals("c") && slash > 0 && id.getPath().indexOf('/', slash + 1) < 0
                    && FORMS.contains(id.getPath().substring(0, slash));
        }).toList();
    }
}
