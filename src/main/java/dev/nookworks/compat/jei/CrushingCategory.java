package dev.nookworks.compat.jei;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Crusher: one item in, one stack out (data map {@code nookworks:crushing}). */
public class CrushingCategory extends AbstractRecipeCategory<CrushingCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(Nookworks.id("crushing"), Recipe.class);

    public record Recipe(ItemStack input, ItemStack output) {}

    public CrushingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.crusher"), gui.createDrawableItemLike(ModItems.CRUSHER.get()), 82, 26);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.input());
        builder.addOutputSlot(61, 5).setOutputSlotBackground().add(recipe.output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(dev.nookworks.machine.CrusherBlockEntity.TICKS_PER_ITEM).setPosition(26, 5);
    }
}
