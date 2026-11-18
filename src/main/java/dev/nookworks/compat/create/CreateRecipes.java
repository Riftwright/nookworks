package dev.nookworks.compat.create;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.RecipeCrossings;
import dev.nookworks.compat.farmersdelight.FdKitchen;
import dev.nookworks.cooking.HandWork;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.oil.PressingRecipe;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * The two mods' like machines doing each other's work (tools/port1211/design_create.md, "Recipes across the two mods"):
 * <ul>
 * <li>ours in Create's - read at run time, so a datapack's entries in our data maps come along: the Crusher's grinds as
 * Crushing Wheel recipes, the Hand Quern's and the Rice Polisher's as Millstone recipes, the Oil Press's as compacting in
 * a Basin (oil out as a liquid), the Noodle Maker's under a Mechanical Press, our bottles of oil, biodiesel, methanol,
 * wood vinegar and liquid experience at a Spout and an Item Drain - each only for an item Create's machine
 * does nothing with already, and never gear (the Crusher gives back less the more it is worn, which a recipe cannot);
 * they are real recipes in the recipe manager (ids {@code nookworks:create/<kind>/<item>}), so Create's machines and JEI's
 * Create pages have them;</li>
 * <li>Create's in ours - looked up when our data maps have nothing: Create's crushing, then milling, in the Crusher (as
 * Crushing Wheels fall back on milling), and Create's milling in the Hand Quern; the main product at its count, Create's
 * chance extras staying the Create machines' own.</li>
 * </ul>
 * So no machine has two recipes for one thing, and no loop is made that was not there (every crossing is a copy of a
 * recipe one of the two mods already had, at no better yield).
 */
public final class CreateRecipes {
    private CreateRecipes() {}

    public static final String PREFIX = "create/";

    private static final Set<RecipeManager> DONE = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<RecipeManager, Lookups> LOOKUPS = new WeakHashMap<>();
    private static volatile List<ResourceLocation> last = List.of();

    static void register() {
        RecipeCrossings.create(CreateRecipes::cross); // run last, after Farmer's Delight's kitchen has widened the twins
        CrushingRecipe.foreign(CreateRecipes::crushingFor);
        HandWork.foreign(CreateRecipes::handFor);
    }

    /** The recipes of ours last put among Create's (for tests and the log). */
    public static List<ResourceLocation> crossed() {
        return last;
    }

    // ---- ours into Create's machines ----

    public static synchronized void cross(MinecraftServer server) {
        RecipeManager manager = server.getRecipeManager();
        if (!DONE.add(manager)) {
            return;
        }
        try {
            DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
            List<RecipeHolder<?>> recipes = new ArrayList<>(manager.getRecipes());
            Set<Item> milled = covered(manager, AllRecipeTypes.MILLING.getType());
            Set<Item> crushed = covered(manager, AllRecipeTypes.CRUSHING.getType());
            Set<Item> pressed = covered(manager, AllRecipeTypes.PRESSING.getType());
            Set<Item> compacted = covered(manager, AllRecipeTypes.COMPACTING.getType());
            List<ResourceLocation> made = new ArrayList<>();
            Crossing crossing = new Crossing(recipes, made, ops, FdKitchen.twins());
            // the Crusher's, in Crushing Wheels (which fall back on milling: neither may have it, nor may our quern or
            // polisher mill it)
            boolean crusher = Features.enabled(ModItems.CRUSHER.get());
            crossing.kind("crushing", new HashSet<>(), item -> crusher && !new ItemStack(item).isDamageableItem()
                    && !crushed.contains(item) && !milled.contains(item) && !millsHere(item), holder -> {
                CrushingRecipe crushing = holder.getData(CrushingRecipe.DATA_MAP);
                ItemStack out = crushing == null ? ItemStack.EMPTY : crushing.output();
                return out.isEmpty() || !Features.enabled(out) ? null : processing("create:crushing", 200, result(out, 1.0F));
            });
            // the Hand Quern's, then the Rice Polisher's, in the Millstone (one set: what the quern mills, the polisher leaves)
            boolean quern = Features.enabled(ModKitchen.HAND_QUERN_ITEM.get());
            crossing.kind("milling", milled, item -> quern && !milled.contains(item), holder -> {
                HandWork milling = holder.getData(HandWork.MILLING);
                return milling == null || !Features.enabled(milling.output()) ? null
                        : processing("create:milling", milling.turns() * 50, result(milling.output(), 1.0F));
            });
            // a grain to white rice, its bran one in four as the polisher gives it
            boolean polisher = Features.enabled(ModCrops.RICE_POLISHER_ITEM.get());
            crossing.kind("milling", milled, item -> polisher && !milled.contains(item) && unpolished(item),
                    holder -> !unpolished(holder.value()) ? null : processing("create:milling", RicePolisherBlockEntity.TURNS * 50,
                            result(new ItemStack(ModCrops.WHITE_RICE.get()), 1.0F),
                            result(new ItemStack(ModCrops.RICE_BRAN.get()), 1.0F / RicePolisherBlockEntity.GRAINS_PER_BRAN)));
            // the Oil Press's, compacted in a Basin: the oil out as a liquid, the cake by its chance
            boolean press = Features.enabled(ModOil.OIL_PRESS_ITEM.get());
            crossing.kind("compacting", new HashSet<>(), item -> press && !compacted.contains(item), holder -> {
                PressingRecipe pressing = holder.getData(PressingRecipe.DATA_MAP);
                if (pressing == null) {
                    return null;
                }
                JsonObject oil = new JsonObject();
                oil.addProperty("id", BuiltInRegistries.FLUID.getKey(dev.nookworks.oil.OilFluids.SOURCE.get()).toString());
                oil.addProperty("amount", pressing.oil());
                List<JsonObject> results = new ArrayList<>(List.of(oil));
                if (pressing.cake() > 0) {
                    results.add(result(new ItemStack(ModOil.OIL_CAKE.get()), pressing.cake()));
                }
                return processing("create:compacting", 0, results.toArray(JsonObject[]::new));
            });
            // the Noodle Maker's, under a Mechanical Press
            boolean noodles = Features.enabled(ModKitchen.NOODLE_MAKER_ITEM.get());
            crossing.kind("pressing", new HashSet<>(), item -> noodles && !pressed.contains(item), holder -> {
                HandWork making = holder.getData(HandWork.NOODLE_MAKING);
                return making == null || !Features.enabled(making.output()) ? null
                        : processing("create:pressing", 0, result(making.output(), 1.0F));
            });
            // our bottles, filled by a Spout and emptied by an Item Drain, a quarter of a bucket each (Bottles); water's is Create's
            Set<Item> emptied = covered(manager, AllRecipeTypes.EMPTYING.getType());
            for (net.minecraft.world.level.material.Fluid fluid : BuiltInRegistries.FLUID) {
                if (!fluid.isSource(fluid.defaultFluidState()) || fluid.isSame(net.minecraft.world.level.material.Fluids.WATER)) {
                    continue;
                }
                ItemStack bottle = dev.nookworks.fluid.Bottles.filled(fluid);
                if (bottle.isEmpty() || !Features.enabled(bottle) || dev.nookworks.fluid.Bottles.fluidOf(bottle) != fluid) {
                    continue; // none, switched off, or one bottle for many liquids (liquid experience: ours fills it, ours comes out)
                }
                String fluidId = BuiltInRegistries.FLUID.getKey(fluid).toString();
                String bottleId = BuiltInRegistries.ITEM.getKey(bottle.getItem()).toString();
                JsonObject filling = processing("create:filling", 0, result(bottle, 1.0F));
                filling.getAsJsonArray("ingredients").add(item("minecraft:glass_bottle"));
                JsonObject liquid = new JsonObject();
                liquid.addProperty("type", "neoforge:single");
                liquid.addProperty("amount", dev.nookworks.fluid.Bottles.AMOUNT);
                liquid.addProperty("fluid", fluidId);
                filling.getAsJsonArray("ingredients").add(liquid);
                add(recipes, made, ops, "filling/" + bottleId.replace(':', '/'), filling);
                if (!emptied.contains(bottle.getItem())) {
                    JsonObject poured = new JsonObject();
                    poured.addProperty("id", fluidId);
                    poured.addProperty("amount", dev.nookworks.fluid.Bottles.AMOUNT);
                    JsonObject emptying = processing("create:emptying", 0, result(new ItemStack(Items.GLASS_BOTTLE), 1.0F), poured);
                    emptying.getAsJsonArray("ingredients").add(item(bottleId));
                    add(recipes, made, ops, "emptying/" + bottleId.replace(':', '/'), emptying);
                }
            }
            manager.replaceRecipes(recipes);
            last = List.copyOf(made);
            forget();
            Nookworks.LOGGER.info("Create's machines and ours: {} of our recipes put among Create's", made.size());
        } catch (RuntimeException e) {
            Nookworks.LOGGER.error("Could not put our machines' recipes among Create's; each mod keeps its own", e);
        }
    }

    /** The items some recipe of {@code type} (not one of ours) takes. */
    private static Set<Item> covered(RecipeManager manager, RecipeType<?> type) {
        Set<Item> items = new HashSet<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (holder.value().getType() == type && !ours(holder.id())) {
                for (Ingredient ingredient : holder.value().getIngredients()) {
                    for (ItemStack stack : ingredient.getItems()) {
                        items.add(stack.getItem());
                    }
                }
            }
        }
        return items;
    }

    private static boolean ours(ResourceLocation id) {
        return id.getNamespace().equals(Nookworks.MODID) && id.getPath().startsWith(PREFIX);
    }

    private static JsonObject result(ItemStack stack, float chance) {
        JsonObject out = new JsonObject();
        out.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        if (stack.getCount() != 1) {
            out.addProperty("count", stack.getCount());
        }
        if (chance < 1.0F) {
            out.addProperty("chance", chance);
        }
        return out;
    }

    /** A Create processing recipe with no ingredients yet (the caller puts its input in). */
    private static JsonObject processing(String type, int time, JsonObject... results) {
        JsonObject json = new JsonObject();
        json.addProperty("type", type);
        json.add("ingredients", new JsonArray());
        if (time > 0) {
            json.addProperty("processing_time", time);
        }
        JsonArray out = new JsonArray();
        for (JsonObject result : results) {
            out.add(result);
        }
        json.add("results", out);
        return json;
    }

    private static JsonObject item(String id) {
        JsonObject in = new JsonObject();
        in.addProperty("item", id);
        return in;
    }

    /** Whether our quern or polisher mills {@code item} (so Crushing Wheels, falling back on milling, leave it). */
    private static boolean millsHere(Item item) {
        return BuiltInRegistries.ITEM.wrapAsHolder(item).getData(HandWork.MILLING) != null || new ItemStack(item).is(RicePolisherBlockEntity.RICE);
    }

    /** A grain the Rice Polisher takes that is not polished already. */
    private static boolean unpolished(Item item) {
        return new ItemStack(item).is(RicePolisherBlockEntity.RICE) && item != ModCrops.WHITE_RICE.get();
    }

    /**
     * Our machines' work put among Create's recipes, kind by kind, a pair of twins (Farmer's Delight's food and ours,
     * {@link FdKitchen#twins()}) as one recipe taking either - as every other recipe naming one of them does - never one
     * each, which would give a Create machine two recipes for each twin once the twins are taken for each other.
     */
    private record Crossing(List<RecipeHolder<?>> recipes, List<ResourceLocation> made, DynamicOps<JsonElement> ops, Map<Item, Item> twins) {
        /**
         * For each item {@code free} lets through and {@code make} has a recipe for (a Create recipe without its input),
         * that recipe for the item, and for its twin beside it when the twin is free too and has no other recipe of this
         * kind of its own; every item crossed goes into {@code done}. A pair is named after its item of ours.
         */
        void kind(String kind, Set<Item> done, Predicate<Item> free, Function<Holder<Item>, @Nullable JsonObject> make) {
            for (Holder<Item> holder : BuiltInRegistries.ITEM.holders().toList()) {
                Item item = holder.value();
                if (done.contains(item) || !free.test(item)) {
                    continue;
                }
                JsonObject json = make.apply(holder);
                if (json == null) {
                    continue;
                }
                List<Item> set = new ArrayList<>(List.of(item));
                Item twin = this.twins.get(item);
                if (twin != null && !done.contains(twin) && free.test(twin)) {
                    JsonObject own = make.apply(BuiltInRegistries.ITEM.wrapAsHolder(twin));
                    if (own == null || own.equals(json)) {
                        set.add(twin);
                    }
                }
                Item named = set.stream().filter(i -> BuiltInRegistries.ITEM.getKey(i).getNamespace().equals(Nookworks.MODID))
                        .findFirst().orElse(item);
                JsonObject input;
                if (set.size() == 1) {
                    input = CreateRecipes.item(BuiltInRegistries.ITEM.getKey(item).toString());
                } else { // NeoForge's compound of the two, which every ingredient codec reads, as the twin widening writes it
                    input = new JsonObject();
                    input.addProperty("type", "neoforge:compound");
                    JsonArray children = new JsonArray();
                    set.forEach(i -> children.add(CreateRecipes.item(BuiltInRegistries.ITEM.getKey(i).toString())));
                    input.add("children", children);
                }
                json.getAsJsonArray("ingredients").add(input);
                add(this.recipes, this.made, this.ops, kind + "/" + BuiltInRegistries.ITEM.getKey(named).toString().replace(':', '/'), json);
                done.addAll(set);
            }
        }
    }

    private static void add(List<RecipeHolder<?>> recipes, List<ResourceLocation> made, DynamicOps<JsonElement> ops, String path, JsonObject json) {
        ResourceLocation id = Nookworks.id(PREFIX + path);
        var read = Recipe.CODEC.parse(ops, json);
        if (read.result().isPresent()) {
            recipes.add(new RecipeHolder<>(id, read.result().get()));
            made.add(id);
        } else {
            Nookworks.LOGGER.warn("Not put among Create's recipes: {} ({})", id, read.error().map(Object::toString).orElse("?"));
        }
    }

    // ---- Create's in our machines ----

    private record Lookups(Map<Item, CrushingRecipe> crusher, Map<Item, HandWork> quern) {}

    /** Forgets what was looked up (the recipes changed). */
    public static synchronized void forget() {
        LOOKUPS.clear();
    }

    private static @Nullable CrushingRecipe crushingFor(ItemStack stack) {
        Lookups lookups = lookups();
        return lookups == null || stack.isDamageableItem() ? null : lookups.crusher().get(stack.getItem());
    }

    private static @Nullable HandWork handFor(DataMapType<Item, HandWork> type, ItemStack stack) {
        if (type != HandWork.MILLING) {
            return null;
        }
        Lookups lookups = lookups();
        return lookups == null ? null : lookups.quern().get(stack.getItem());
    }

    private static synchronized @Nullable Lookups lookups() {
        RecipeManager manager = manager();
        if (manager == null) {
            return null;
        }
        return LOOKUPS.computeIfAbsent(manager, CreateRecipes::build);
    }

    /** The recipes of the side asking: the server's on its thread, else the client's. */
    private static @Nullable RecipeManager manager() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            return server.getRecipeManager();
        }
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            return dev.nookworks.compat.create.client.ClientRecipes.manager();
        }
        return server == null ? null : server.getRecipeManager();
    }

    private static Lookups build(RecipeManager manager) {
        Map<Item, CrushingRecipe> crusher = new HashMap<>();
        Map<Item, HandWork> quern = new HashMap<>();
        // Crushing Wheels' own work first, then what they fall back on, the Millstone's
        for (RecipeType<?> type : List.of(AllRecipeTypes.CRUSHING.getType(), AllRecipeTypes.MILLING.getType())) {
            boolean milling = type == AllRecipeTypes.MILLING.getType();
            for (RecipeHolder<?> holder : manager.getRecipes()) {
                if (holder.value().getType() != type || ours(holder.id()) || !(holder.value() instanceof ProcessingRecipe<?, ?> recipe)
                        || recipe.getIngredients().size() != 1) {
                    continue;
                }
                ItemStack main = main(recipe);
                if (main.isEmpty()) {
                    continue;
                }
                int turns = Math.max(1, Math.min(8, Math.round(recipe.getProcessingDuration() / 50.0F)));
                for (ItemStack in : recipe.getIngredients().get(0).getItems()) {
                    crusher.putIfAbsent(in.getItem(), new CrushingRecipe(new CrushingRecipe.Result.OfItem(main.getItemHolder()), main.getCount()));
                    // stone and the like stay the Crusher's work: the quern takes from the Millstone only what our Crusher does not grind
                    if (milling && CrushingRecipe.ownOf(in) == null) {
                        quern.putIfAbsent(in.getItem(), new HandWork(main.getItemHolder(), main.getCount(), turns));
                    }
                }
            }
        }
        return new Lookups(crusher, quern);
    }

    /**
     * A Create recipe's main product: its first sure one. A recipe that only has chances (crimsite's two in five of
     * crushed iron, grass's one in four of seeds) has none and stays the wheels' and the Millstone's own: our machines
     * give a thing every time, and taking its first chance as sure made five times Create's gold out of ochrum.
     */
    private static ItemStack main(ProcessingRecipe<?, ?> recipe) {
        for (ProcessingOutput output : recipe.getRollableResults()) {
            if (output.getChance() >= 1.0F && !output.getStack().isEmpty()) {
                return output.getStack().copy();
            }
        }
        return ItemStack.EMPTY;
    }
}
