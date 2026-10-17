package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.HandWork;
import dev.nookworks.feature.Features;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.CrushingRecipe;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.registry.ModRope;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IIngredientAliasRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * JEI integration (optional: JEI is compile-only and only loads this class when it is installed).
 * Shows what every machine and kitchen block makes - the Crusher, Sifter, Spawn Egg Brewer, the mercury steps, the
 * Iron Pot, Oil Press, Nukadoko, Rice Polisher and Printing Press, and the Grill as a campfire - plus info pages for
 * things that are not recipes. Posters, Labels and filled Mob Bottles are told apart by what they carry.
 */
@JeiPlugin
public class NookworksJeiPlugin implements IModPlugin {
    @Override
    public Identifier getPluginUid() {
        return Nookworks.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new CrushingCategory(gui), new ElectrolysisCategory(gui), new SiftingCategory(gui), new SpawnEggBrewingCategory(gui),
                new MercuryCategories.RetortCategory(gui), new MercuryCategories.AmalgamCategory(gui), new MercuryCategories.DistilCategory(gui),
                new MercuryCategories.MudCategory(gui),
                new KitchenCategories.PotCategory(gui), new KitchenCategories.PressingCategory(gui), new KitchenCategories.PicklingCategory(gui),
                new KitchenCategories.PolishingCategory(gui), new PrintingCategory(gui),
                new KitchenCategories.HandWorkCategory(gui, KitchenCategories.MILLING, "block.nookworks.hand_quern",
                        ModKitchen.HAND_QUERN_ITEM.get()),
                new KitchenCategories.HandWorkCategory(gui, KitchenCategories.NOODLE_MAKING, "block.nookworks.noodle_maker",
                        ModKitchen.NOODLE_MAKER_ITEM.get()),
                new KitchenCategories.DryingCategory(gui), new KitchenCategories.RisingCategory(gui));
    }

    /** A poster by its design, a label by the item it shows, a filled bottle by the mob in it: each its own entry. */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        registration.registerFromDataComponentTypes(ModItems.POSTER.get(), ModDataComponents.POSTER.get());
        registration.registerFromDataComponentTypes(ModItems.LABEL.get(), ModDataComponents.LABEL.get());
        registration.registerFromDataComponentTypes(ModItems.MOB_BOTTLE.get(), ModDataComponents.CAPTURED_ENTITY.get());
    }

    /** Every item found by its English name too, while the game is in another language (EnglishSearch). */
    @Override
    public void registerIngredientAliases(IIngredientAliasRegistration registration) {
        EnglishSearch.register(registration);
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(CrushingCategory.TYPE, crushingRecipes());
        registration.addRecipes(ElectrolysisCategory.TYPE, ElectrolysisCategory.recipes());
        registration.addRecipes(SiftingCategory.TYPE, SiftingCategory.recipes());
        registration.addRecipes(SpawnEggBrewingCategory.TYPE, on("mob_bottle", brewingRecipes()));
        registration.addRecipes(MercuryCategories.RETORTING, MercuryCategories.retortRecipes());
        registration.addRecipes(MercuryCategories.AMALGAMATION, MercuryCategories.amalgamRecipes());
        registration.addRecipes(MercuryCategories.DISTILLATION, MercuryCategories.distilRecipes());
        registration.addRecipes(MercuryCategories.MUD, MercuryCategories.mudRecipes());
        registration.addRecipes(KitchenCategories.POT, on("kitchen", KitchenCategories.potRecipes()));
        registration.addRecipes(KitchenCategories.PRESSING, KitchenCategories.pressingRecipes());
        registration.addRecipes(KitchenCategories.PICKLING, on("kitchen", KitchenCategories.picklingRecipes()));
        registration.addRecipes(KitchenCategories.POLISHING, on("kitchen", KitchenCategories.polishingRecipes()));
        registration.addRecipes(KitchenCategories.MILLING, on("kitchen", KitchenCategories.handRecipes(HandWork.MILLING)));
        registration.addRecipes(KitchenCategories.NOODLE_MAKING,
                on("kitchen", KitchenCategories.handRecipes(HandWork.NOODLE_MAKING)));
        registration.addRecipes(PrintingCategory.TYPE, on("printing", PrintingCategory.recipes()));
        registration.addRecipes(KitchenCategories.DRYING, KitchenCategories.dryingRecipes());
        registration.addRecipes(KitchenCategories.RISING, on("kitchen", KitchenCategories.risingRecipes()));
        InfoPages.each((item, page) -> {
            if (!Features.enabled(item)) {
                return;
            }
            Component text = fitted(page);
            if (item == ModItems.LABEL.get()) {
                // a label is listed by what it shows (the subtypes above): the page goes on the one listed, the
                // creative tab's, since on a blank label - listed nowhere - nobody found it
                registration.addItemStackInfo(LabelItem.sample(), text);
            } else {
                registration.addIngredientInfo(item, text);
            }
        });
    }

    /** The width of an information page's text: JEI's page, less its scroll bar. */
    private static final int INFO_WIDTH = 170 - 16 - 2;

    /** {@code page} as JEI can show it: Japanese and Chinese broken into lines here ({@link CjkLines}), anything else as it is. */
    private static Component fitted(Component page) {
        String text = page.getString();
        if (!CjkLines.needed(text)) {
            return page;
        }
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        return Component.literal(CjkLines.wrap(text, INFO_WIDTH, font::width));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        station(registration, CrushingCategory.TYPE, ModItems.CRUSHER.get());
        station(registration, ElectrolysisCategory.TYPE, ModItems.ELECTROLYTIC_CELL.get());
        station(registration, RecipeTypes.CRAFTING, ModItems.IRON_WORKBENCH.get(), ModItems.BRASS_WORKBENCH.get());
        station(registration, RecipeTypes.SMELTING, ModItems.ELECTRIC_FURNACE.get());
        station(registration, SiftingCategory.TYPE, ModItems.SIFTER.get());
        station(registration, SpawnEggBrewingCategory.TYPE, ModItems.SPAWN_EGG_BREWER.get());
        station(registration, MercuryCategories.RETORTING, ModItems.RETORT.get());
        station(registration, MercuryCategories.AMALGAMATION, ModItems.STIRRING_TANK.get());
        station(registration, MercuryCategories.DISTILLATION, ModItems.DISTILLER.get());
        station(registration, MercuryCategories.MUD, ModItems.STIRRING_TANK.get());
        station(registration, KitchenCategories.POT, ModCooking.IRON_POT_ITEM.get());
        station(registration, KitchenCategories.PRESSING, ModOil.OIL_PRESS_ITEM.get(),
                ModOil.MOTOR_ITEM.get());
        station(registration, KitchenCategories.PICKLING, ModCooking.NUKADOKO_ITEM.get());
        station(registration, KitchenCategories.MILLING, ModKitchen.HAND_QUERN_ITEM.get(),
                ModOil.MOTOR_ITEM.get());
        station(registration, KitchenCategories.NOODLE_MAKING, ModKitchen.NOODLE_MAKER_ITEM.get(),
                ModOil.MOTOR_ITEM.get());
        station(registration, KitchenCategories.POLISHING, ModCrops.RICE_POLISHER_ITEM.get(),
                ModOil.MOTOR_ITEM.get());
        station(registration, PrintingCategory.TYPE, ModItems.PRINTING_PRESS.get());
        station(registration, KitchenCategories.DRYING, ModItems.THIN_ROPE.get());
        station(registration, KitchenCategories.DRYING, ModRope.THIN_CHAIN.get());
        station(registration, KitchenCategories.RISING, ModKitchen.WOODEN_TRAY_ITEM.get());
        // the Grill cooks what a campfire cooks, in the same time
        station(registration, RecipeTypes.CAMPFIRE_COOKING, ModCooking.GRILL_ITEM.get());
    }

    /** A station of a part switched off is no station: no Grill or Iron Pot with the kitchen off. */
    private static void station(IRecipeCatalystRegistration registration, IRecipeType<?> type, ItemLike... stations) {
        ItemLike[] on = Arrays.stream(stations).filter(station -> Features.enabled(station.asItem())).toArray(ItemLike[]::new);
        if (on.length > 0) {
            registration.addCraftingStation(type, on);
        }
    }

    /** A page's recipes, or none while the part of the mod they are of is switched off ({@link dev.nookworks.feature.Features}). */
    private static <T> List<T> on(String part, List<T> recipes) {
        return Features.enabled(part) ? recipes : List.of();
    }

    /**
     * Not in the list: 0.2.0's tiered sorters, made no more (set down, they become Item Sorters with a gear), and the
     * items of every part of the mod a pack has switched off.
     */
    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        List<ItemStack> hidden = new ArrayList<>(List.of(
                new ItemStack(ModItems.GOLD_SORTER.get()), new ItemStack(ModItems.DIAMOND_SORTER.get()), new ItemStack(ModItems.NETHERITE_SORTER.get())));
        Features.disabledItems().forEach(item -> hidden.add(new ItemStack(item)));
        runtime.getIngredientManager().removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, hidden);
    }

    static List<CrushingCategory.Recipe> crushingRecipes() {
        List<CrushingCategory.Recipe> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            // The Crusher's own lookup: a tag recipe nothing fills is not a recipe (without an industrial mod there
            // is no iron dust), nor one that makes a part's item with the part off (flour, with the kitchen off) -
            // JEI must not offer a grind that the Crusher would refuse.
            CrushingRecipe recipe = CrushingRecipe.of(new ItemStack(holder));
            if (recipe != null && Features.enabled(holder.value())) {
                ItemStack out = recipe.output();
                if (new ItemStack(holder).isDamageableItem()) { // new gear: the most it gives back (worn, less)
                    out = out.copyWithCount(Math.max(1, (int) Math.ceil(out.getCount() * CrushingRecipe.GEAR_RECOVERY)));
                }
                list.add(new CrushingCategory.Recipe(new ItemStack(holder), out));
            }
        });
        return list;
    }

    @SuppressWarnings("rawtypes")
    static List<EntityType> brewingRecipes() {
        List<EntityType> list = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (MobBottleItem.bottleable(type)) { // only what a bottle will take: the rule the bottle itself uses
                list.add(type);
            }
        }
        return list;
    }
}
