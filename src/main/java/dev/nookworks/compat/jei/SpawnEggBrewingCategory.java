package dev.nookworks.compat.jei;

import dev.nookworks.Nookworks;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/** Spawn Egg Brewer: a Mob Bottle holding the mob + an egg (+ blaze powder as fuel) = its spawn egg. */
@SuppressWarnings("rawtypes")
public class SpawnEggBrewingCategory extends AbstractRecipeCategory<EntityType> {
    public static final IRecipeType<EntityType> TYPE = IRecipeType.create(Nookworks.id("spawn_egg_brewing"), EntityType.class);

    public SpawnEggBrewingCategory(IGuiHelper gui) {
        super(TYPE, Component.translatable("block.nookworks.spawn_egg_brewer"),
                gui.createDrawableItemLike(ModItems.SPAWN_EGG_BREWER.get()), 120, 40);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, EntityType type, IFocusGroup focuses) {
        builder.addInputSlot(1, 1).setStandardSlotBackground().add(MobBottleItem.filled(type));
        builder.addInputSlot(1, 21).setStandardSlotBackground().add(Ingredient.of(BuiltInRegistries.ITEM.getOrThrow(ItemTags.EGGS)));
        builder.addInputSlot(30, 21).setStandardSlotBackground().add(Items.BLAZE_POWDER);
        MobBottleItem.spawnEgg(type).ifPresent(egg -> builder.addOutputSlot(98, 11).setOutputSlotBackground().add(new ItemStack(egg)));
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, EntityType type, IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(200).setPosition(62, 11);
    }
}
