package dev.nookworks.compat.jei;

import dev.nookworks.Nookworks;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.Tags;

/** Spawn Egg Brewer: a Mob Bottle holding the mob + an egg (+ blaze powder as fuel) = its spawn egg. */
@SuppressWarnings("rawtypes")
public class SpawnEggBrewingCategory extends AbstractRecipeCategory<EntityType> {
    public static final RecipeType<EntityType> TYPE = new RecipeType<>(Nookworks.id("spawn_egg_brewing"), EntityType.class);

    public SpawnEggBrewingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.spawn_egg_brewer"),
                gui.createDrawableItemLike(ModItems.SPAWN_EGG_BREWER.get()), 120, 40);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EntityType type, IFocusGroup focuses) {
        builder.addInputSlot(1, 1).setStandardSlotBackground().addItemStack(MobBottleItem.filled(type));
        builder.addInputSlot(1, 21).setStandardSlotBackground().addIngredients(Ingredient.of(Tags.Items.EGGS)); // 1.21.1 has no #minecraft:eggs: the common tag the Brewer takes
        builder.addInputSlot(30, 21).setStandardSlotBackground().addItemLike(Items.BLAZE_POWDER);
        MobBottleItem.spawnEgg(type).ifPresent(egg -> builder.addOutputSlot(98, 11).setOutputSlotBackground().addItemStack(new ItemStack(egg)));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, EntityType type, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(200, 62, 11);
    }
}
