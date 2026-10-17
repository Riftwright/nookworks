package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.nookworks.Nookworks;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/** JEI pages for the three mercury process steps, read from their data maps, and the Stirring Tank's mud. */
public final class MercuryCategories {
    public record Retort(ItemStack input, MercuryRecipes.Retorting recipe) {}

    public record Amalgam(ItemStack input, MercuryRecipes.Amalgamation recipe) {}

    public record Distil(ItemStack input, MercuryRecipes.Distillation recipe) {}

    /** Dirt and water stirred into mud: one page, its input turning through everything that makes mud. */
    public record Mud(List<ItemStack> inputs) {}

    public static final IRecipeType<Retort> RETORTING = IRecipeType.create(Nookworks.id("retorting"), Retort.class);
    public static final IRecipeType<Amalgam> AMALGAMATION = IRecipeType.create(Nookworks.id("amalgamation"), Amalgam.class);
    public static final IRecipeType<Distil> DISTILLATION = IRecipeType.create(Nookworks.id("distillation"), Distil.class);
    public static final IRecipeType<Mud> MUD = IRecipeType.create(Nookworks.id("mud"), Mud.class);

    private static final int W = 150;
    private static final int H = 40;
    private static final int TEXT = 0xFF404040;

    public static List<Retort> retortRecipes() {
        List<Retort> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            var r = h.getData(MercuryRecipes.RETORTING);
            if (r != null) {
                list.add(new Retort(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Amalgam> amalgamRecipes() {
        List<Amalgam> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            var r = h.getData(MercuryRecipes.AMALGAMATION);
            if (r != null) {
                list.add(new Amalgam(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Distil> distilRecipes() {
        List<Distil> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            var r = h.getData(MercuryRecipes.DISTILLATION);
            if (r != null) {
                list.add(new Distil(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Mud> mudRecipes() {
        return List.of(new Mud(StirringTankBlockEntity.muddied().stream().map(ItemStack::new).toList()));
    }

    private static void text(IRecipeExtrasBuilder builder, Component text, int x, int y) {
        builder.addText(text.copy().withStyle(ChatFormatting.DARK_GRAY), W - x, 10).setPosition(x, y).setColor(TEXT);
    }

    public static class RetortCategory extends AbstractRecipeCategory<Retort> {
        public RetortCategory(IGuiHelper gui) {
            super(RETORTING, Component.translatable("jei.nookworks.retorting"), icon(gui, ModItems.RETORT.get()), W, H + 2);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Retort r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            for (int i = 0; i < r.recipe().reagents().size(); i++) { // under the charge, as the Retort's own slots go
                builder.addInputSlot(1 + i * 18, 24).setStandardSlotBackground()
                        .addItemStacks(r.recipe().reagents().get(i).stream().map(ItemStack::new).toList());
            }
            if (r.recipe().count() > 0) {
                builder.addOutputSlot(61, 5).setOutputSlotBackground().add(new ItemStack(r.recipe().byproduct(), r.recipe().count()));
            }
            if (r.recipe().mercury() > 0) {
                builder.addOutputSlot(83, 5).setOutputSlotBackground().add(ModItems.MERCURY_BUCKET.get());
            }
            r.recipe().remainder().ifPresent(item -> builder.addOutputSlot(105, 5).setOutputSlotBackground().add(new ItemStack(item)));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Retort r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(100).setPosition(26, 5);
            if (r.recipe().mercury() > 0) {
                text(builder, Component.translatable("jei.nookworks.mercury_made", r.recipe().mercury()),
                        1 + r.recipe().reagents().size() * 18 + 2, 28);
            }
        }
    }

    public static class AmalgamCategory extends AbstractRecipeCategory<Amalgam> {
        public AmalgamCategory(IGuiHelper gui) {
            super(AMALGAMATION, Component.translatable("jei.nookworks.amalgamation"), icon(gui, ModItems.STIRRING_TANK.get()), W, H);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Amalgam r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addInputSlot(21, 5).setStandardSlotBackground().add(ModItems.MERCURY_BUCKET.get());
            builder.addOutputSlot(83, 5).setOutputSlotBackground().add(r.recipe().output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Amalgam r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(80).setPosition(48, 5);
            text(builder, Component.translatable("jei.nookworks.mercury_used", r.recipe().mercury()), 1, 28);
        }
    }

    public static class MudCategory extends AbstractRecipeCategory<Mud> {
        public MudCategory(IGuiHelper gui) {
            super(MUD, Component.translatable("jei.nookworks.mud"), icon(gui, ModItems.STIRRING_TANK.get()), W, H);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Mud r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStacks(r.inputs());
            builder.addInputSlot(21, 5).setStandardSlotBackground().add(Items.WATER_BUCKET);
            builder.addOutputSlot(83, 5).setOutputSlotBackground().add(Items.MUD);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Mud r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(80).setPosition(48, 5);
            text(builder, Component.translatable("jei.nookworks.water_used", StirringTankBlockEntity.WATER_PER_MUD), 1, 28);
        }
    }

    public static class DistilCategory extends AbstractRecipeCategory<Distil> {
        public DistilCategory(IGuiHelper gui) {
            super(DISTILLATION, Component.translatable("jei.nookworks.distillation"), icon(gui, ModItems.DISTILLER.get()), W + 10, H + 18);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Distil r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            if (r.recipe().result().isPresent()) {
                builder.addOutputSlot(61, 5).setOutputSlotBackground().add(new ItemStack(r.recipe().result().get(), r.recipe().count()));
            } else if (r.recipe().lootTable().isPresent()) {
                var key = r.recipe().lootTable().get().identifier();
                var drops = LootSummaries.get(key);
                for (int i = 0; i < drops.size(); i++) {
                    var drop = drops.get(i);
                    String chance = String.format(Locale.ROOT, "%.2f%%", drop.chance() * 100);
                    builder.addOutputSlot(61 + (i % 5) * 18, 1 + (i / 5) * 18).setOutputSlotBackground().add(drop.stack())
                            .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                                    Component.translatable("jei.nookworks.sifting.chance", 1, chance).withStyle(ChatFormatting.GOLD)));
                }
            }
            if (r.recipe().mercury() > 0) {
                // recovered mercury - none from wood vinegar, which is no amalgam: no bucket there, or JEI would name it
                // a way to mercury
                builder.addOutputSlot(1, 24).setOutputSlotBackground().add(ModItems.MERCURY_BUCKET.get());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Distil r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(120).setPosition(26, 5);
            if (r.recipe().mercury() > 0) {
                text(builder, Component.translatable("jei.nookworks.mercury_back", r.recipe().mercury()), 1, 46);
            }
            if (r.recipe().bonusChance() > 0) {
                text(builder, Component.translatable("jei.nookworks.bonus", Math.round(r.recipe().bonusChance() * 100), r.recipe().bonus()), 22, 29);
            }
        }
    }

    private static IDrawable icon(IGuiHelper gui, ItemLike item) {
        return gui.createDrawableItemLike(item);
    }

    private MercuryCategories() {}
}
