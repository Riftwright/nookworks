package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelItem;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.Posters;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The Printing Press: a sheet of Fine Paper and the design's inks make a copy of a poster; with a sample in its slot,
 * the same sheet and a drop of each ink make eight labels of it. The labels' page shows a few samples, each with the
 * labels it makes, turning over together.
 */
public class PrintingCategory extends AbstractRecipeCategory<PrintingCategory.Print> {
    public static final IRecipeType<Print> TYPE = IRecipeType.create(Nookworks.id("printing"), Print.class);

    /** A design to print, or {@link PrintingPressBlockEntity#LABEL} for labels. */
    public record Print(Identifier design) {}

    /** What the labels' page shows being printed. Any item can be; these are only examples. */
    private static final List<Item> SAMPLES = List.of(Items.COBBLESTONE, Items.OAK_LOG, Items.IRON_INGOT, Items.WHEAT,
            Items.REDSTONE, Items.COAL, Items.GLASS, Items.STRING);

    public PrintingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.printing_press"), gui.createDrawableItemLike(ModItems.PRINTING_PRESS.get()), 130, 40);
    }

    public static List<Print> recipes() {
        List<Print> list = new ArrayList<>();
        list.add(new Print(PrintingPressBlockEntity.LABEL));
        var level = Minecraft.getInstance().level;
        if (level != null) {
            Posters.ids(level.registryAccess()).forEach(id -> list.add(new Print(id)));
        }
        return list;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Print print, IFocusGroup focuses) {
        builder.addInputSlot(1, 1).setStandardSlotBackground().add(new ItemStack(ModItems.FINE_PAPER.get(), PosterVariant.PAPER_PER_COPY));
        boolean labels = PrintingPressBlockEntity.LABEL.equals(print.design());
        List<Item> inks = PosterVariant.PROCESS_INKS;
        if (!labels) {
            var level = Minecraft.getInstance().level;
            PosterVariant design = level == null ? null : Posters.get(level, print.design());
            if (design != null) {
                inks = design.inkCost();
            }
        }
        Map<Item, Integer> counts = new LinkedHashMap<>();
        inks.forEach(ink -> counts.merge(ink, 1, Integer::sum));
        int x = 1;
        for (var ink : counts.entrySet()) {
            builder.addInputSlot(x, 21).setStandardSlotBackground().add(new ItemStack(ink.getKey(), ink.getValue()));
            x += 18;
        }
        if (labels) {
            IRecipeSlotBuilder sample = builder.addInputSlot(37, 1).setStandardSlotBackground()
                    .addItemStacks(SAMPLES.stream().map(ItemStack::new).toList())
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("gui.nookworks.printing_press.sample.hint").withStyle(ChatFormatting.GRAY)));
            IRecipeSlotBuilder out = builder.addOutputSlot(110, 11).setOutputSlotBackground()
                    .addItemStacks(SAMPLES.stream().map(item -> LabelItem.of(new ItemStack(item)).copyWithCount(PrintingPressBlockEntity.LABELS_PER_COPY)).toList());
            builder.createFocusLink(sample, out);
        } else {
            builder.addOutputSlot(110, 11).setOutputSlotBackground().add(Posters.item(print.design()));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Print print, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(MachineTuning.time(PrintingPressBlockEntity.TICKS)).setPosition(80, 11);
    }
}
