package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.IronPotBlock;
import dev.nookworks.cooking.NukadokoBlock;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The Grill, the Iron Pot and the Nukadoko and what comes out of them: rice, soup and stir-fries in bowls, the rice
 * balls made from the rice, fried food, and pickles. Dishes in bowls stack to 16 (vanilla stews do not stack; a pot of rice would fill a hotbar) and
 * give the bowl back.
 */
public final class ModCooking {
    /** Ticks in a minute, for how long a dish's boost lasts. */
    public static final int MINUTE = 20 * 60;

    public static final DeferredBlock<GrillBlock> GRILL = ModBlocks.BLOCKS.registerBlock("grill",
            GrillBlock::new, p -> p.mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.CHAIN).noOcclusion()
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> GRILL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(GRILL);
    public static final DeferredBlock<IronPotBlock> IRON_POT = ModBlocks.BLOCKS.registerBlock("iron_pot",
            IronPotBlock::new, p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0F).sound(SoundType.LANTERN).noOcclusion()
                    .forceSolidOn());
    public static final DeferredItem<BlockItem> IRON_POT_ITEM = ModItems.ITEMS.registerSimpleBlockItem(IRON_POT);
    public static final DeferredBlock<NukadokoBlock> NUKADOKO = ModBlocks.BLOCKS.registerBlock("nukadoko",
            NukadokoBlock::new, p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> NUKADOKO_ITEM = ModItems.ITEMS.registerSimpleBlockItem(NUKADOKO);

    public static final DeferredItem<Item> COOKED_RICE = bowlFood("cooked_rice", 6, 0.6F);
    public static final DeferredItem<Item> VEGETABLE_SOUP = bowlFood("vegetable_soup", 7, 0.7F);
    public static final DeferredItem<Item> FRIED_RICE = bowlFood("fried_rice", 9, 0.8F);
    public static final DeferredItem<Item> STIR_FRIED_VEGETABLES = bowlFood("stir_fried_vegetables", 8, 0.8F);
    public static final DeferredItem<Item> SALT_ONIGIRI = ModItems.ITEMS.registerSimpleItem("salt_onigiri",
            p -> p.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build()));
    public static final DeferredItem<Item> SALMON_ONIGIRI = ModItems.ITEMS.registerSimpleItem("salmon_onigiri",
            p -> p.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build()));

    public static final DeferredItem<Item> FRENCH_FRIES = food("french_fries", 6, 0.6F);
    // The dishes it takes most to make give a few minutes of a boost when eaten (the user, 2026-10-10: 作るの大変な食事
    // 食べたら数分間バフ). As strong as vanilla's own, never more: a beacon's or a potion's at the same level is the same.
    public static final DeferredItem<Item> KARAAGE = meal("karaage", 8, 0.8F, new MobEffectInstance(MobEffects.STRENGTH, 2 * MINUTE));
    public static final DeferredItem<Item> FRIED_FISH = meal("fried_fish", 7, 0.8F,
            new MobEffectInstance(MobEffects.WATER_BREATHING, 3 * MINUTE));
    /** Tonkatsu: a pork cutlet in flour, egg and breadcrumbs, deep-fried, sliced. */
    public static final DeferredItem<Item> TONKATSU = meal("tonkatsu", 7, 0.8F, new MobEffectInstance(MobEffects.HASTE, 3 * MINUTE));
    /** Wheat ground in the Crusher; bread crumbled in it. For coating what is fried. */
    public static final DeferredItem<Item> FLOUR = ModItems.ITEMS.registerSimpleItem("flour");
    public static final DeferredItem<Item> BREADCRUMBS = ModItems.ITEMS.registerSimpleItem("breadcrumbs");
    /** A porkchop cut in two and coated, flour, egg and crumbs, at the crafting table: the Iron Pot's oil fries it. */
    public static final DeferredItem<Item> BREADED_PORK = ModItems.ITEMS.registerSimpleItem("breaded_pork");
    public static final DeferredItem<Item> CARROT_NUKAZUKE = food("carrot_nukazuke", 4, 1.0F);
    public static final DeferredItem<Item> BEETROOT_NUKAZUKE = food("beetroot_nukazuke", 3, 1.0F);
    // The egg dishes (the user, 2026-10-16: 卵料理が欲しくない？卵焼いたら目玉焼きとか): a step up the table each, from
    // just under bread to between bread and the pot's dishes.
    /** An egg fried sunny side up: smelted, smoked, on a campfire or on a Grill. As modest as one egg and a fire. */
    public static final DeferredItem<Item> FRIED_EGG = food("fried_egg", 4, 0.6F);
    /** Eggs boiled hard in the Iron Pot's water, every egg in it at once. */
    public static final DeferredItem<Item> BOILED_EGG = food("boiled_egg", 5, 0.6F);
    /** Tamagoyaki: eggs and sugar rolled up in a little oil in the Iron Pot, a slice an egg. The best of the three. */
    public static final DeferredItem<Item> TAMAGOYAKI = food("tamagoyaki", 6, 0.8F);

    private static DeferredItem<Item> food(String name, int nutrition, float saturation) {
        return ModItems.ITEMS.registerSimpleItem(name,
                p -> p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
    }

    /** A dish that gives {@code effect} when it is eaten, as a golden apple gives its own. */
    static DeferredItem<Item> meal(String name, int nutrition, float saturation, MobEffectInstance effect) {
        return ModItems.ITEMS.registerSimpleItem(name,
                p -> p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), withEffect(effect)));
    }

    /** Eaten as any food is, and {@code effect} given (a copy each time: the instance here is only the pattern). */
    static Consumable withEffect(MobEffectInstance effect) {
        return Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(effect)).build();
    }

    private static DeferredItem<Item> bowlFood(String name, int nutrition, float saturation) {
        return ModItems.ITEMS.registerSimpleItem(name, p -> p.stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())
                .usingConvertsTo(Items.BOWL).craftRemainder(Items.BOWL));
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("cooking: {}", IRON_POT.getId());
    }

    private ModCooking() {}
}
