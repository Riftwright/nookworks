package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.nookworks.Nookworks;
import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Sifter: possible results with their chance, from the tables the server plays with (see {@link LootSummaries}): a
 * data pack's changes show here, and so does another item a pack has made siftable.
 */
public class SiftingCategory extends AbstractRecipeCategory<SiftingCategory.Recipe> {
    public static final IRecipeType<Recipe> TYPE = IRecipeType.create(Nookworks.id("sifting"), Recipe.class);
    private static final int COLUMNS = 6;
    /** Rows of results: the biggest tables of ours give 20 things. A data pack's bigger one shares out its last slot. */
    private static final int ROWS = 4;

    /** One input's table; {@code fine} when it is the table rolled through the Brass Mesh. */
    public record Recipe(ItemStack input, List<LootSummaries.Drop> drops, boolean fine) {}

    public SiftingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.sifter"), gui.createDrawableItemLike(ModItems.SIFTER.get()), 160, ROWS * 18 + 2);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, Recipe recipe, IFocusGroup focuses) {
        int middle = (ROWS - 2) * 9; // the input and the arrow stand at the middle of the rows
        builder.addInputSlot(1, middle + (recipe.fine() ? 1 : 14)).setStandardSlotBackground().add(recipe.input());
        if (recipe.fine()) {
            builder.addInputSlot(1, middle + 24).setStandardSlotBackground().add(new ItemStack(ModItems.BRASS_MESH.get()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                            Component.translatable("jei.nookworks.sifting.fine").withStyle(ChatFormatting.GOLD)));
        }
        int slots = COLUMNS * ROWS;
        List<LootSummaries.Drop> drops = recipe.drops();
        for (int i = 0; i < Math.min(drops.size(), slots); i++) {
            // the last slot takes turns with whatever did not fit, each with its own odds
            List<LootSummaries.Drop> here = i == slots - 1 ? drops.subList(i, drops.size()) : List.of(drops.get(i));
            builder.addOutputSlot(52 + (i % COLUMNS) * 18, 1 + (i / COLUMNS) * 18).setOutputSlotBackground()
                    .addItemStacks(here.stream().map(LootSummaries.Drop::stack).toList())
                    .addRichTooltipCallback((view, tooltip) -> view.getDisplayedItemStack().ifPresent(shown -> here.stream()
                            .filter(drop -> ItemStack.isSameItemSameComponents(drop.stack(), shown)).findFirst()
                            .ifPresent(drop -> tooltip.add(Component.translatable("jei.nookworks.sifting.chance",
                                    drop.min() == drop.max() ? Integer.toString(drop.min()) : drop.min() + "-" + drop.max(),
                                    LootSummaries.chanceText(drop.chance())).withStyle(ChatFormatting.GOLD)))));
        }
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, Recipe recipe, IFocusGroup focuses) {
        builder.addRecipeArrowWidget().setPosition(23, 14 + (ROWS - 2) * 9);
    }

    static List<Recipe> recipes() {
        List<Recipe> list = new ArrayList<>();
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(SifterBlockEntity.SIFTABLE)) {
            Identifier id = holder.unwrapKey().orElseThrow().identifier();
            for (boolean fine : new boolean[] {false, true}) {
                List<LootSummaries.Drop> drops = LootSummaries.sifted(id, fine);
                if (!drops.isEmpty()) {
                    list.add(new Recipe(new ItemStack(holder), drops, fine));
                }
            }
        }
        return list;
    }
}
