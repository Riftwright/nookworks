package dev.nookworks.compat.jei;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Crusher: one item in, one stack out (data map {@code nookworks:crushing}). */
public class CrushingCategory extends AbstractRecipeCategory<CrushingCategory.Recipe> {
    public static final RecipeType<Recipe> TYPE = new RecipeType<>(Nookworks.id("crushing"), Recipe.class);

    public record Recipe(ItemStack input, ItemStack output) {}

    public CrushingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.crusher"), gui.createDrawableItemLike(ModItems.CRUSHER.get()), 82, 26);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStack(recipe.input());
        builder.addOutputSlot(61, 5).setOutputSlotBackground().addItemStack(recipe.output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(dev.nookworks.machine.CrusherBlockEntity.TICKS_PER_ITEM, 26, 5);
    }
}
