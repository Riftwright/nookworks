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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import dev.nookworks.port.core.Props;

/**
 * The Grill, the Iron Pot and the Nukadoko and what comes out of them: rice, soup and stir-fries in bowls, the rice
 * balls made from the rice, fried food, and pickles. Dishes in bowls stack to 16 (vanilla stews do not stack; a pot of rice would fill a hotbar) and
 * give the bowl back.
 */
public final class ModCooking {
    /** Ticks in a minute, for how long a dish's boost lasts. */
    public static final int MINUTE = 20 * 60;

    public static final DeferredBlock<GrillBlock> GRILL = ModBlocks.BLOCKS.registerBlock("grill",
            GrillBlock::new, Props.block(p -> p.mapColor(MapColor.METAL).strength(1.0F).sound(SoundType.CHAIN).noOcclusion()
                    .pushReaction(PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> GRILL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(GRILL);
    public static final DeferredBlock<IronPotBlock> IRON_POT = ModBlocks.BLOCKS.registerBlock("iron_pot",
            IronPotBlock::new, Props.block(p -> p.mapColor(MapColor.COLOR_BLACK).strength(2.0F).sound(SoundType.LANTERN).noOcclusion()
                    .forceSolidOn()));
    public static final DeferredItem<BlockItem> IRON_POT_ITEM = ModItems.ITEMS.registerSimpleBlockItem(IRON_POT);
    /**
     * Trial (cooking review, 2026-11-14): a Frying Pan, for what is fried or stir-fried in a little oil - fried eggs,
     * tamagoyaki, pancakes, fried rice, stir-fried vegetables. The pot keeps boiling and deep-frying.
     */
    public static final DeferredBlock<dev.nookworks.cooking.FryingPanBlock> FRYING_PAN = ModBlocks.BLOCKS.registerBlock("frying_pan",
            dev.nookworks.cooking.FryingPanBlock::new, Props.block(p -> p.mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.LANTERN)
                    .noOcclusion().forceSolidOn()));
    public static final DeferredItem<BlockItem> FRYING_PAN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(FRYING_PAN);
    /** Trial (2026-11-14): a kitchen range with no fire in it - a pot or pan on it cooks while it has power. */
    public static final DeferredBlock<dev.nookworks.cooking.ElectricStoveBlock> ELECTRIC_STOVE = ModBlocks.BLOCKS.registerBlock("electric_stove",
            dev.nookworks.cooking.ElectricStoveBlock::new, Props.block(p -> p.mapColor(MapColor.METAL).strength(3.0F).sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(dev.nookworks.cooking.ElectricStoveBlock.LIT) ? dev.nookworks.cooking.ElectricStoveBlock.LIGHT_LEVEL : 0)));
    public static final DeferredItem<BlockItem> ELECTRIC_STOVE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(ELECTRIC_STOVE);
    /** A brass tap on a wall or a Copper Pipe, pouring what the pipe brings into the pot under it (the user, 2026-11-15). */
    public static final DeferredBlock<dev.nookworks.cooking.KitchenTapBlock> KITCHEN_TAP = ModBlocks.BLOCKS.registerBlock("kitchen_tap",
            dev.nookworks.cooking.KitchenTapBlock::new, Props.block(p -> p.mapColor(MapColor.GOLD).strength(1.5F).sound(SoundType.COPPER)
                    .noOcclusion().forceSolidOn().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredItem<BlockItem> KITCHEN_TAP_ITEM = ModItems.ITEMS.registerSimpleBlockItem(KITCHEN_TAP);
    public static final DeferredBlock<NukadokoBlock> NUKADOKO = ModBlocks.BLOCKS.registerBlock("nukadoko",
            NukadokoBlock::new, Props.block(p -> p.mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> NUKADOKO_ITEM = ModItems.ITEMS.registerSimpleBlockItem(NUKADOKO);

    public static final DeferredItem<Item> COOKED_RICE = bowlFood("cooked_rice", 6, 0.6F);
    public static final DeferredItem<Item> VEGETABLE_SOUP = bowlFood("vegetable_soup", 7, 0.7F);
    public static final DeferredItem<Item> FRIED_RICE = bowlFood("fried_rice", 9, 0.8F);
    public static final DeferredItem<Item> STIR_FRIED_VEGETABLES = bowlFood("stir_fried_vegetables", 8, 0.8F);
    public static final DeferredItem<Item> SALT_ONIGIRI = ModItems.ITEMS.registerSimpleItem("salt_onigiri",
            Props.item(p -> p.food(new FoodProperties.Builder().nutrition(5).saturationModifier(0.6F).build())));
    public static final DeferredItem<Item> SALMON_ONIGIRI = ModItems.ITEMS.registerSimpleItem("salmon_onigiri",
            Props.item(p -> p.food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build())));

    public static final DeferredItem<Item> FRENCH_FRIES = food("french_fries", 6, 0.6F);
    // The dishes it takes most to make give a few minutes of a boost when eaten (the user, 2026-10-10: 作るの大変な食事
    // 食べたら数分間バフ). As strong as vanilla's own, never more: a beacon's or a potion's at the same level is the same.
    public static final DeferredItem<Item> KARAAGE = meal("karaage", 8, 0.8F, new MobEffectInstance(MobEffects.DAMAGE_BOOST, 2 * MINUTE));
    public static final DeferredItem<Item> FRIED_FISH = meal("fried_fish", 7, 0.8F,
            new MobEffectInstance(MobEffects.WATER_BREATHING, 3 * MINUTE));
    /** Tonkatsu: a pork cutlet in flour, egg and breadcrumbs, deep-fried, sliced. */
    public static final DeferredItem<Item> TONKATSU = meal("tonkatsu", 7, 0.8F, new MobEffectInstance(MobEffects.DIG_SPEED, 3 * MINUTE));
    /** Wheat ground in the Crusher; bread crumbled in it. For coating what is fried. */
    public static final DeferredItem<Item> FLOUR = ModItems.ITEMS.registerSimpleItem("flour");
    public static final DeferredItem<Item> BREADCRUMBS = ModItems.ITEMS.registerSimpleItem("breadcrumbs");
    /** A porkchop cut in two and coated, flour, egg and crumbs, at the crafting table: the Iron Pot's oil fries it. */
    public static final DeferredItem<Item> BREADED_PORK = ModItems.ITEMS.registerSimpleItem("breaded_pork");
    // Trial (cooking review, 2026-11-14): what is fried is got ready at the crafting table first, as Breaded Pork is.
    /** A raw chicken cut up and dusted in flour, two to a bird: the pot's oil fries each into Karaage. */
    public static final DeferredItem<Item> COATED_CHICKEN = ModItems.ITEMS.registerSimpleItem("coated_chicken");
    /** A raw cod filleted and floured, two to a fish: fried into Fried Fish. */
    public static final DeferredItem<Item> COATED_FISH = ModItems.ITEMS.registerSimpleItem("coated_fish");
    /** Flour, sugar and an egg kneaded and cut into rings, four to a batch: fried into Doughnuts. */
    public static final DeferredItem<Item> DONUT_DOUGH = ModItems.ITEMS.registerSimpleItem("donut_dough");
    /** Three eggs beaten with sugar: poured into the Frying Pan, a rolled omelette of three slices. */
    public static final DeferredItem<Item> BEATEN_EGGS = ModItems.ITEMS.registerSimpleItem("beaten_eggs");
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
                Props.item(p -> p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())));
    }

    /** A dish that gives {@code effect} when it is eaten, as a golden apple gives its own. */
    static DeferredItem<Item> meal(String name, int nutrition, float saturation, MobEffectInstance effect) {
        return ModItems.ITEMS.registerSimpleItem(name,
                Props.item(p -> p.food(withEffect(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation), effect).build())));
    }

    /**
     * Eaten as any food is, and {@code effect} given (a copy each time: the instance here is only the pattern).
     * 1.21.1: the effect is the food's own, always given (26.3: a consume effect of the default food's consumable).
     */
    static FoodProperties.Builder withEffect(FoodProperties.Builder food, MobEffectInstance effect) {
        return food.effect(() -> new MobEffectInstance(effect), 1.0F);
    }

    private static DeferredItem<Item> bowlFood(String name, int nutrition, float saturation) {
        return ModItems.ITEMS.registerSimpleItem(name, Props.item(p -> p.stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).usingConvertsTo(Items.BOWL).build())
                .craftRemainder(Items.BOWL)));
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("cooking: {}", IRON_POT.getId());
    }

    private ModCooking() {}
}
