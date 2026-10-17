package dev.nookworks.feature;

import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * What can be eaten as it is, and vanilla does not let you: a strand of kelp, a stick of sugar cane to chew, a lick of
 * sugar or salt, a mouthful of grass, mushrooms and fungi (a red mushroom is poisonous, as the fly agaric it is drawn
 * from), a piece of honeycomb, a bitter cocoa bean. None of it is much of a meal - dried kelp, cooking and baking stay
 * the better way - but a player who eats spider eyes can eat a mushroom. Seeds and eggs are left out: they are planted
 * and thrown with the same click, and a missed click would eat them. For the same reason, those of them that are
 * planted or placed ({@link #EATEN_ONLY_IN_THE_AIR}) are never eaten off a click on a block, only in the air
 * ({@code mixin/BlockItemMixin}).
 * <p>
 * Items are what they are from the start, so this is a startup setting ({@code edibleRawFoods}).
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class RawFoods {
    /** Edible here, but placed when a block is clicked with them, even where they cannot go. */
    public static final TagKey<Item> EATEN_ONLY_IN_THE_AIR = TagKey.create(
            Registries.ITEM, Nookworks.id("eaten_only_in_the_air"));

    @SubscribeEvent
    static void modify(ModifyDefaultComponentsEvent event) {
        if (!NookworksStartupConfig.EDIBLE_RAW_FOODS.getAsBoolean()) {
            return;
        }
        edible(event, Items.KELP, 1, 0.1F, Consumables.defaultFood().build());
        edible(event, Items.SUGAR_CANE, 1, 0.1F, Consumables.defaultFood().build());
        edible(event, Items.SUGAR, 1, 0.1F, Consumables.defaultFood().consumeSeconds(0.8F).build());
        edible(event, ModItems.SALT.get(), 1, 0.0F, Consumables.defaultFood().consumeSeconds(0.8F)
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0), 0.25F)).build());
        // Grass is not food for a stomach that is not a cow's.
        edible(event, Items.SHORT_GRASS, 1, 0.0F, Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 0), 0.3F)).build());
        edible(event, Items.BROWN_MUSHROOM, 1, 0.1F, Consumables.defaultFood().build());
        edible(event, Items.RED_MUSHROOM, 1, 0.1F, Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.POISON, 100, 0), new MobEffectInstance(MobEffects.NAUSEA, 200, 0)))).build());
        Consumable queasy = Consumables.defaultFood()
                .onConsume(new ApplyStatusEffectsConsumeEffect(new MobEffectInstance(MobEffects.NAUSEA, 200, 0), 0.6F)).build();
        edible(event, Items.CRIMSON_FUNGUS, 1, 0.1F, queasy);
        edible(event, Items.WARPED_FUNGUS, 1, 0.1F, queasy);
        edible(event, Items.HONEYCOMB, 2, 0.2F, Consumables.defaultFood().build());
        edible(event, Items.COCOA_BEANS, 1, 0.1F, Consumables.defaultFood().build());
    }

    private static void edible(ModifyDefaultComponentsEvent event, ItemLike item, int nutrition, float saturation, Consumable consumable) {
        event.modify(item, (builder, registries, target) -> builder
                .set(DataComponents.FOOD, new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())
                .set(DataComponents.CONSUMABLE, consumable));
    }

    private RawFoods() {}
}
