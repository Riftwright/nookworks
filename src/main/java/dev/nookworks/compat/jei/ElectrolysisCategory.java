package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.Electrolysis;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Electrolytic Cell: one item in, what the current makes of it out, with the water it takes and any mercury it loses. */
public class ElectrolysisCategory extends AbstractRecipeCategory<ElectrolysisCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(Nookworks.id("electrolysis"), Recipe.class);

    public record Recipe(ItemStack input, Electrolysis recipe) {}

    public ElectrolysisCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.electrolytic_cell"), gui.createDrawableItemLike(ModItems.ELECTROLYTIC_CELL.get()), 120, 44);
    }

    static List<Recipe> recipes() {
        List<Recipe> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            Electrolysis recipe = Features.enabled(holder.value()) ? Electrolysis.of(new ItemStack(holder)) : null;
            if (recipe != null) {
                list.add(new Recipe(new ItemStack(holder), recipe));
            }
        });
        return list;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe r, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
        builder.addOutputSlot(61, 5).setOutputSlotBackground().add(r.recipe().output());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe r, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(100).setPosition(26, 5);
        List<Component> lines = new ArrayList<>();
        if (r.recipe().bonusChance() > 0) {
            lines.add(Component.translatable("jei.nookworks.bonus", Math.round(r.recipe().bonusChance() * 100), r.recipe().bonus()));
        }
        lines.add(Component.translatable("jei.nookworks.electrolysis.water", r.recipe().water()));
        if (r.recipe().needsMercury()) {
            lines.add(Component.translatable("jei.nookworks.electrolysis.mercury", r.recipe().mercury()));
        }
        int y = 25;
        for (Component line : lines) {
            builder.addText(line.copy().withStyle(ChatFormatting.DARK_GRAY), 120, 10).setPosition(1, y).setColor(0xFF404040);
            y += 9;
        }
    }
}
