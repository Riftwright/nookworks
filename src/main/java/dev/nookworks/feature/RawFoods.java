package dev.nookworks.feature;

import java.util.function.Supplier;

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
        // PORT: 26.3 gives each a Consumable (vanilla's food default, 1.6 s; 0.8 s for sugar and salt) with its effects;
        // 1.21.1 keeps the eating time and the effects in FoodProperties: fast() is 0.8 s, the default 1.6 s
        edible(event, Items.KELP, 1, 0.1F, food());
        edible(event, Items.SUGAR_CANE, 1, 0.1F, food());
        edible(event, Items.SUGAR, 1, 0.1F, food().fast());
        edible(event, ModItems.SALT.get(), 1, 0.0F, food().fast()
                .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 100, 0), 0.25F));
        // Grass is not food for a stomach that is not a cow's.
        edible(event, Items.SHORT_GRASS, 1, 0.0F, food()
                .effect(() -> new MobEffectInstance(MobEffects.HUNGER, 200, 0), 0.3F));
        edible(event, Items.BROWN_MUSHROOM, 1, 0.1F, food());
        edible(event, Items.RED_MUSHROOM, 1, 0.1F, food().effect(() ->
                new MobEffectInstance(MobEffects.POISON, 100, 0), 1.0F).effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 0), 1.0F));
        Supplier<FoodProperties.Builder> queasy = () -> food()
                .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200, 0), 0.6F);
        edible(event, Items.CRIMSON_FUNGUS, 1, 0.1F, queasy.get());
        edible(event, Items.WARPED_FUNGUS, 1, 0.1F, queasy.get());
        edible(event, Items.HONEYCOMB, 2, 0.2F, food());
        edible(event, Items.COCOA_BEANS, 1, 0.1F, food());
    }

    /** Vanilla's plain food: eaten in 1.6 s, nothing more (26.3's {@code Consumables.defaultFood()}). */
    private static FoodProperties.Builder food() {
        return new FoodProperties.Builder();
    }

    private static void edible(ModifyDefaultComponentsEvent event, ItemLike item, int nutrition, float saturation, FoodProperties.Builder food) {
        FoodProperties properties = food.nutrition(nutrition).saturationModifier(saturation).build();
        event.modify(item, builder -> builder
                .set(DataComponents.FOOD, properties));
    }

    private RawFoods() {}
}
