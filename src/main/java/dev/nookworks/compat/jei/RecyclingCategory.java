package dev.nookworks.compat.jei;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.recycling.RecyclingRecipe;
import dev.nookworks.registry.ModRecycling;
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

/**
 * Recycler: a piece of metal junk in, half its metal back (data map {@code nookworks:recycling}). The output shows what
 * a new piece gives, rounded; its tooltip gives the average, which can be a fraction (a rail is three eighths of an
 * ingot), and on gear that wears says a worn piece gives less.
 */
public class RecyclingCategory extends AbstractRecipeCategory<RecyclingCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(Nookworks.id("recycling"), Recipe.class);

    public record Recipe(ItemStack input, ItemStack output, float average, int ticks) {}

    public RecyclingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.recycler"), gui.createDrawableItemLike(ModRecycling.RECYCLER_ITEM.get()), 82, 26);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 5).setStandardSlotBackground().add(recipe.input());
        builder.addOutputSlot(61, 5).setOutputSlotBackground().add(recipe.output())
                .addRichTooltipCallback((view, tooltip) -> {
                    tooltip.add(Component.translatable("jei.nookworks.recycling.average", figure(recipe.average())).withStyle(ChatFormatting.GRAY));
                    if (recipe.input().isDamageableItem()) {
                        tooltip.add(Component.translatable("jei.nookworks.recycling.worn").withStyle(ChatFormatting.GRAY));
                    }
                });
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(recipe.ticks()).setPosition(26, 5);
    }

    /** Every piece the Recycler takes, as the Recycler itself looks it up. */
    static List<Recipe> recipes() {
        List<Recipe> list = new ArrayList<>();
        if (!Features.enabled(ModRecycling.RECYCLER_ITEM.get())) {
            return list; // the part switched off: no Recycler to show it on
        }
        List<Recipe> aged = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(holder -> {
            ItemStack input = new ItemStack(holder);
            RecyclingRecipe recipe = RecyclingRecipe.of(input);
            if (recipe != null) {
                float average = recipe.expected();
                // copper junk's weathered and waxed forms after the rest, not eight copper bars on the first page
                String path = holder.key().identifier().getPath();
                (path.startsWith("waxed_") || path.startsWith("exposed_") || path.startsWith("weathered_") || path.startsWith("oxidized_")
                        ? aged : list).add(new Recipe(input, new ItemStack(recipe.result(), Math.max(1, Math.min(64, Math.round(average)))), average,
                        recipe.ticks()));
            }
        });
        list.addAll(aged);
        return list;
    }

    /** 4, 22.5, 0.19: whole numbers bare, the rest to two places at most. */
    private static String figure(float value) {
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}
