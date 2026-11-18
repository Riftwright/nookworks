package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.machine.LootSummaries;
import dev.nookworks.mercury.DistillerBlockEntity;
import dev.nookworks.mercury.MercuryRecipes;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.RecipeType;
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

    public static final RecipeType<Retort> RETORTING = new RecipeType<>(Nookworks.id("retorting"), Retort.class);
    public static final RecipeType<Amalgam> AMALGAMATION = new RecipeType<>(Nookworks.id("amalgamation"), Amalgam.class);
    public static final RecipeType<Distil> DISTILLATION = new RecipeType<>(Nookworks.id("distillation"), Distil.class);
    public static final RecipeType<Mud> MUD = new RecipeType<>(Nookworks.id("mud"), Mud.class);

    private static final int W = 150;
    private static final int H = 40;
    private static final int TEXT = 0xFF404040;

    public static List<Retort> retortRecipes() {
        List<Retort> list = new ArrayList<>();
        BuiltInRegistries.ITEM.holders().forEach(h -> {
            var r = Features.enabled(h.value()) ? MercuryRecipes.retorting(new ItemStack(h)) : null;
            if (r != null) {
                list.add(new Retort(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Amalgam> amalgamRecipes() {
        List<Amalgam> list = new ArrayList<>();
        BuiltInRegistries.ITEM.holders().forEach(h -> {
            var r = Features.enabled(h.value()) ? MercuryRecipes.amalgamation(new ItemStack(h)) : null;
            if (r != null) {
                list.add(new Amalgam(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Distil> distilRecipes() {
        List<Distil> list = new ArrayList<>();
        BuiltInRegistries.ITEM.holders().forEach(h -> {
            var r = Features.enabled(h.value()) ? MercuryRecipes.distillation(new ItemStack(h)) : null;
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
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStack(r.input());
            for (int i = 0; i < r.recipe().reagents().size(); i++) { // under the charge, as the Retort's own slots go
                builder.addInputSlot(1 + i * 18, 24).setStandardSlotBackground()
                        .addItemStacks(r.recipe().reagents().get(i).stream().map(ItemStack::new).toList());
            }
            if (r.recipe().count() > 0) {
                builder.addOutputSlot(61, 5).setOutputSlotBackground().addItemStack(new ItemStack(r.recipe().byproduct(), r.recipe().count()));
            }
            if (r.recipe().mercury() > 0) {
                builder.addOutputSlot(83, 5).setOutputSlotBackground().addItemLike(ModItems.MERCURY_BUCKET.get());
            }
            r.recipe().remainder().ifPresent(item -> builder.addOutputSlot(105, 5).setOutputSlotBackground().addItemStack(new ItemStack(item)));
            // a liquid it gives off into its tank, to be piped out (wood vinegar from logs, since 1.5)
            r.recipe().liquid().ifPresent(liquid -> builder.addOutputSlot(r.recipe().mercury() > 0 ? 127 : 83, 5).setOutputSlotBackground()
                    .addFluidStack(liquid.getFluid(), liquid.getAmount()).setFluidRenderer(Math.max(liquid.getAmount(), OilFluids.BOTTLE), false, 16, 16));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Retort r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(100, 26, 5);
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
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStack(r.input());
            builder.addInputSlot(21, 5).setStandardSlotBackground().addItemLike(ModItems.MERCURY_BUCKET.get());
            builder.addOutputSlot(83, 5).setOutputSlotBackground().addItemStack(r.recipe().output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Amalgam r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(80, 48, 5);
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
            builder.addInputSlot(21, 5).setStandardSlotBackground().addItemLike(Items.WATER_BUCKET);
            builder.addOutputSlot(83, 5).setOutputSlotBackground().addItemLike(Items.MUD);
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Mud r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(80, 48, 5);
            text(builder, Component.translatable("jei.nookworks.water_used", StirringTankBlockEntity.WATER_PER_MUD), 1, 28);
        }
    }

    public static class DistilCategory extends AbstractRecipeCategory<Distil> {
        public DistilCategory(IGuiHelper gui) {
            super(DISTILLATION, Component.translatable("jei.nookworks.distillation"), icon(gui, ModItems.DISTILLER.get()), W + 10, H + 18);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Distil r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStack(r.input());
            if (DistillerBlockEntity.pours(r.input()) && r.recipe().result().map(h -> h.value() == ModOil.METHANOL.get()).orElse(false)) {
                // wood vinegar, poured into its tank or piped in, distils to methanol in its tank and water, a bucket's
                // worth a run (since the play-test after 1.5)
                int run = DistillerBlockEntity.WOOD_VINEGAR_RUN;
                long methanol = (long) OilFluids.BOTTLE * r.recipe().count();
                builder.addInputSlot(1, 24).setStandardSlotBackground().addFluidStack(OilFluids.WOOD_VINEGAR.get(), run)
                        .setFluidRenderer(run, false, 16, 16);
                builder.addOutputSlot(61, 5).setOutputSlotBackground().addFluidStack(OilFluids.METHANOL.get(), methanol)
                        .setFluidRenderer(run, false, 16, 16);
                builder.addOutputSlot(83, 5).setOutputSlotBackground().addFluidStack(net.minecraft.world.level.material.Fluids.WATER, run - methanol)
                        .setFluidRenderer(run, false, 16, 16);
            } else if (r.recipe().result().isPresent()) {
                builder.addOutputSlot(61, 5).setOutputSlotBackground().addItemStack(new ItemStack(r.recipe().result().get(), r.recipe().count()));
                if (r.recipe().result().get().value() == ModOil.METHANOL.get()) {
                    // or as the liquid, into a pipe or tank beside the Distiller, the bottle back empty (since 1.5)
                    builder.addOutputSlot(83, 5).setOutputSlotBackground()
                            .addFluidStack(OilFluids.METHANOL.get(), (long) OilFluids.BOTTLE * r.recipe().count())
                            .setFluidRenderer(OilFluids.BOTTLE, false, 16, 16);
                }
            } else if (r.recipe().lootTable().isPresent()) {
                var key = r.recipe().lootTable().get().location();
                var drops = LootSummaries.get(key);
                for (int i = 0; i < drops.size(); i++) {
                    var drop = drops.get(i);
                    String chance = String.format(Locale.ROOT, "%.2f%%", drop.chance() * 100);
                    builder.addOutputSlot(61 + (i % 5) * 18, 1 + (i / 5) * 18).setOutputSlotBackground().addItemStack(drop.stack())
                            .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                                    Component.translatable("jei.nookworks.sifting.chance", 1, chance).withStyle(ChatFormatting.GOLD)));
                }
            }
            if (r.recipe().mercury() > 0) {
                // recovered mercury - none from wood vinegar, which is no amalgam: no bucket there, or JEI would name it
                // a way to mercury
                builder.addOutputSlot(1, 24).setOutputSlotBackground().addItemLike(ModItems.MERCURY_BUCKET.get());
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Distil r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(120, 26, 5);
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
