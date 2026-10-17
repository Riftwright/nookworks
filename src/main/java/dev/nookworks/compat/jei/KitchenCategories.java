package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.HandWork;
import dev.nookworks.cooking.PicklingRecipe;
import dev.nookworks.cooking.PotRecipe;
import dev.nookworks.cooking.TrayBlockEntity;
import dev.nookworks.crop.RicePolisherBlockEntity;
import dev.nookworks.feature.Features;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.oil.PressingRecipe;
import dev.nookworks.registry.ModCooking;
import dev.nookworks.registry.ModCrops;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModKitchen;
import dev.nookworks.registry.ModOil;
import dev.nookworks.rope.Drying;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * JEI pages for the kitchen and the hand-worked machines: what the Iron Pot cooks (its recipes, a datapack
 * registry), what the Oil Press gets out of a seed and the Nukadoko makes of a vegetable (data maps), what the
 * Rice Polisher makes of brown rice, what dries hung on a rope in the sun (a data map), and dough rising on a tray.
 */
public final class KitchenCategories {
    public record Pressing(ItemStack input, PressingRecipe recipe) {}

    public record Pickling(ItemStack input, PicklingRecipe recipe) {}

    public record Polishing(ItemStack input) {}

    /** One entry of a hand machine's data map ({@link dev.nookworks.cooking.HandWork}). */
    public record HandWorking(ItemStack input, HandWork work) {}

    /** What dries into {@code result} hung in the sun for {@code ticks}: every input of the data map that does so. */
    public record Dried(List<ItemStack> inputs, ItemStack result, int ticks) {}

    /** Dough set down on a Wooden Tray, and what it has risen into after {@code ticks}. */
    public record Rising(ItemStack input, ItemStack result, int ticks) {}

    public static final IRecipeType<PotRecipe> POT = IRecipeType.create(Nookworks.id("iron_pot"), PotRecipe.class);
    public static final IRecipeType<Pressing> PRESSING = IRecipeType.create(Nookworks.id("pressing"), Pressing.class);
    public static final IRecipeType<Pickling> PICKLING = IRecipeType.create(Nookworks.id("pickling"), Pickling.class);
    public static final IRecipeType<Polishing> POLISHING = IRecipeType.create(Nookworks.id("polishing"), Polishing.class);
    public static final IRecipeType<HandWorking> MILLING = IRecipeType.create(Nookworks.id("milling"), HandWorking.class);
    public static final IRecipeType<HandWorking> NOODLE_MAKING = IRecipeType.create(Nookworks.id("noodle_making"), HandWorking.class);
    public static final IRecipeType<Dried> DRYING = IRecipeType.create(Nookworks.id("drying"), Dried.class);
    public static final IRecipeType<Rising> RISING = IRecipeType.create(Nookworks.id("rising"), Rising.class);

    private static final int TEXT = 0xFF404040;

    private KitchenCategories() {}

    private static void text(IRecipeExtrasBuilder builder, Component text, int x, int y, int width) {
        builder.addText(text.copy().withStyle(ChatFormatting.DARK_GRAY), width, 10).setPosition(x, y).setColor(TEXT);
    }

    /** "12 s", "10 min": how long something takes, from ticks. */
    static Component duration(int ticks) {
        int seconds = ticks / 20;
        return seconds >= 120 ? Component.translatable("jei.nookworks.minutes", seconds / 60)
                : Component.translatable("jei.nookworks.seconds", seconds);
    }

    // ---- lists ----

    public static List<PotRecipe> potRecipes() {
        var level = Minecraft.getInstance().level;
        return level == null ? List.of() : PotRecipe.all(level).stream().map(RecipeHolder::value).toList();
    }

    public static List<Pressing> pressingRecipes() {
        List<Pressing> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            PressingRecipe r = h.getData(PressingRecipe.DATA_MAP);
            if (r != null && Features.enabled(h.value())) {  // not corn or rice bran with the kitchen off
                list.add(new Pressing(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Pickling> picklingRecipes() {
        List<Pickling> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            PicklingRecipe r = h.getData(PicklingRecipe.DATA_MAP);
            if (r != null) {
                list.add(new Pickling(new ItemStack(h), r));
            }
        });
        return list;
    }

    public static List<Polishing> polishingRecipes() {
        List<Polishing> list = new ArrayList<>();
        for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(RicePolisherBlockEntity.RICE)) {
            list.add(new Polishing(new ItemStack(h)));
        }
        return list;
    }

    public static List<HandWorking> handRecipes(DataMapType<Item,
            HandWork> type) {
        List<HandWorking> list = new ArrayList<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            var work = h.getData(type);
            if (work != null) {
                list.add(new HandWorking(new ItemStack(h), work));
            }
        });
        return list;
    }

    /**
     * Everything the drying data map names, gathered by what it dries into and how long it takes: the raw fish of
     * every mod in one page, as they all come out Dried Fish.
     */
    public static List<Dried> dryingRecipes() {
        Map<List<Object>, List<ItemStack>> byResult = new LinkedHashMap<>();
        Map<List<Object>, Drying> what = new HashMap<>();
        BuiltInRegistries.ITEM.listElements().forEach(h -> {
            Drying drying = h.getData(Drying.MAP);
            if (drying != null) {
                List<Object> key = List.of(drying.result().value(), drying.ticks());
                byResult.computeIfAbsent(key, k -> new ArrayList<>()).add(new ItemStack(h));
                what.put(key, drying);
            }
        });
        List<Dried> list = new ArrayList<>();
        byResult.forEach((key, inputs) -> list.add(new Dried(inputs, new ItemStack(what.get(key).result()), what.get(key).ticks())));
        return list;
    }

    public static List<Rising> risingRecipes() {
        return List.of(new Rising(new ItemStack(ModKitchen.BREAD_DOUGH.get()),
                new ItemStack(ModKitchen.RISEN_DOUGH.get()), TrayBlockEntity.RISE_TICKS));
    }

    // ---- categories ----

    /** Hung on a Thin Rope out in the sun: what goes up, what comes down, and how much sunshine it takes. */
    public static class DryingCategory extends AbstractRecipeCategory<Dried> {
        public DryingCategory(IGuiHelper gui) {
            super(DRYING, Component.translatable("jei.nookworks.drying"), gui.createDrawableItemLike(ModItems.THIN_ROPE.get()),
                    140, 60);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Dried r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().addItemStacks(r.inputs());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().add(r.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Dried r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(200).setPosition(26, 5);
            text(builder, Component.translatable("jei.nookworks.drying.time", duration(r.ticks())), 1, 28, 138);
            builder.addText(Component.translatable("jei.nookworks.drying.how").withStyle(ChatFormatting.DARK_GRAY), 138, 20)
                    .setPosition(1, 39).setColor(TEXT);
        }
    }

    /** Dough on a Wooden Tray, left to rise. */
    public static class RisingCategory extends AbstractRecipeCategory<Rising> {
        public RisingCategory(IGuiHelper gui) {
            super(RISING, Component.translatable("jei.nookworks.rising"),
                    gui.createDrawableItemLike(ModKitchen.WOODEN_TRAY_ITEM.get()), 100, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Rising r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().add(r.result());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Rising r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(200).setPosition(26, 5);
            text(builder, duration(r.ticks()), 1, 28, 98);
        }
    }

    /** A hand-cranked machine: what goes in, what comes out, and how many turns it takes. */
    public static class HandWorkCategory extends AbstractRecipeCategory<HandWorking> {
        public HandWorkCategory(IGuiHelper gui, IRecipeType<HandWorking> type, String title, ItemLike icon) {
            super(type, Component.translatable(title), gui.createDrawableItemLike(icon), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, HandWorking r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().add(r.work().output());
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, HandWorking r, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
            text(builder, Component.translatable("jei.nookworks.turns", r.work().turns()), 1, 28, 118);
        }
    }

    /**
     * The Iron Pot: up to six ingredients (each as many as a batch takes), the water or oil it cooks in and how much a
     * batch takes, what it is served into if anything, and the dish.
     */
    public static class PotCategory extends AbstractRecipeCategory<PotRecipe> {
        public PotCategory(IGuiHelper gui) {
            super(POT, Component.translatable("block.nookworks.iron_pot"), gui.createDrawableItemLike(ModCooking.IRON_POT_ITEM.get()), 132, 58);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, PotRecipe recipe, IFocusGroup focuses) {
            for (int i = 0; i < recipe.ingredients().size() && i < 6; i++) {
                PotRecipe.Part part = recipe.ingredients().get(i);
                builder.addInputSlot(1 + (i % 3) * 18, 1 + (i / 3) * 18).setStandardSlotBackground()
                        .addItemStacks(part.ingredient().items().map(h -> new ItemStack(h, part.count())).toList());
            }
            builder.addInputSlot(1, 40).setStandardSlotBackground().addFluidStack(recipe.liquid().fluid(), recipe.amount())
                    .setFluidRenderer(Math.max(1, recipe.amount()), false, 16, 16); // the slot full: how much is in the text beside it
            recipe.container().ifPresent(item -> builder.addInputSlot(64, 40).setStandardSlotBackground().add(new ItemStack(item)));
            builder.addOutputSlot(110, 10).setOutputSlotBackground()
                    .add(new ItemStack(recipe.result().item(), recipe.result().count()));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, PotRecipe recipe, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(Math.max(20, recipe.time())).setPosition(78, 10);
            text(builder, duration(recipe.time()), 76, 28, 40);
            text(builder, Component.translatable("jei.nookworks.millibuckets", recipe.amount()), 20, 44, 40);
        }
    }

    /** The Oil Press: a seed, the oil it gives after so many turns of the handle, and the cake it may leave. */
    public static class PressingCategory extends AbstractRecipeCategory<Pressing> {
        public PressingCategory(IGuiHelper gui) {
            super(PRESSING, Component.translatable("block.nookworks.oil_press"), gui.createDrawableItemLike(ModOil.OIL_PRESS_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Pressing r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().addFluidStack(OilFluids.SOURCE.get(), r.recipe().oil())
                    .setFluidRenderer(Math.max(1, r.recipe().oil()), false, 16, 16);
            if (r.recipe().cake() > 0) {
                String chance = String.format(Locale.ROOT, "%.0f%%", r.recipe().cake() * 100);
                builder.addOutputSlot(83, 5).setOutputSlotBackground().add(new ItemStack(ModOil.OIL_CAKE.get()))
                        .addRichTooltipCallback((view, tooltip) -> tooltip.add(
                                Component.translatable("jei.nookworks.chance", chance).withStyle(ChatFormatting.GOLD)));
            }
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Pressing r, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
            text(builder, Component.translatable("jei.nookworks.turns", r.recipe().turns()), 1, 28, 118);
        }
    }

    /** The Nukadoko: a vegetable pushed into the bran, and the pickle it is after so long. */
    public static class PicklingCategory extends AbstractRecipeCategory<Pickling> {
        public PicklingCategory(IGuiHelper gui) {
            super(PICKLING, Component.translatable("block.nookworks.nukadoko"), gui.createDrawableItemLike(ModCooking.NUKADOKO_ITEM.get()), 100, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Pickling r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().add(new ItemStack(r.recipe().result()));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Pickling r, IFocusGroup focuses) {
            builder.addAnimatedRecipeArrowWidget(200).setPosition(26, 5);
            text(builder, duration(r.recipe().time()), 1, 28, 98);
        }
    }

    /** The Rice Polisher: brown rice in, white rice out, and a bran for every so many grains. */
    public static class PolishingCategory extends AbstractRecipeCategory<Polishing> {
        public PolishingCategory(IGuiHelper gui) {
            super(POLISHING, Component.translatable("block.nookworks.rice_polisher"),
                    gui.createDrawableItemLike(ModCrops.RICE_POLISHER_ITEM.get()), 120, 40);
        }

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, Polishing r, IFocusGroup focuses) {
            builder.addInputSlot(1, 5).setStandardSlotBackground().add(r.input());
            builder.addOutputSlot(61, 5).setOutputSlotBackground().add(new ItemStack(ModCrops.WHITE_RICE.get()));
            builder.addOutputSlot(83, 5).setOutputSlotBackground().add(new ItemStack(ModCrops.RICE_BRAN.get()))
                    .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.nookworks.polishing.bran",
                            RicePolisherBlockEntity.GRAINS_PER_BRAN).withStyle(ChatFormatting.GOLD)));
        }

        @Override
        public void createRecipeExtras(IRecipeExtrasBuilder builder, Polishing r, IFocusGroup focuses) {
            builder.addRecipeArrowWidget().setPosition(26, 5);
            text(builder, Component.translatable("jei.nookworks.turns", RicePolisherBlockEntity.TURNS), 1, 28, 118);
        }
    }
}
