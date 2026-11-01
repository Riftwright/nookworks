package dev.nookworks.registry;

import dev.nookworks.cooking.PlateBlock;
import dev.nookworks.Nookworks;
import dev.nookworks.cooking.HandQuernBlock;
import dev.nookworks.cooking.NoodleMakerBlock;
import dev.nookworks.cooking.TrayBlock;
import dev.nookworks.crop.CornBlock;
import dev.nookworks.crop.OnionBlock;
import dev.nookworks.crop.RiceBaleBlock;
import dev.nookworks.mixin.FireBlockAccessor;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * The bakery and the noodle bar, corn and onions, and the rice bale.
 * <ul>
 * <li>Flour is ground from wheat in the Hand Quern (or the Crusher). Bread is made as bread is: flour, water, salt and
 * a little sugar kneaded into dough, set on a Wooden Tray to rise ({@link TrayBlock}), and baked - three loaves from three
 * flour, so a bundle of wheat goes three times as far as the old way. Cookies, cake and pumpkin pie take flour too
 * (the {@code flourBakery} startup setting).</li>
 * <li>Udon: flour, water and salt make udon dough; the Noodle Maker rolls and cuts it into noodles; the Iron Pot boils
 * them with dried kelp for the broth. Doughnuts fry in the pot's oil; pancake batter cooks on the Grill.</li>
 * <li>Corn kernels and onions come up now and then when soil is sifted. Corn is planted from its kernels, and an ear
 * shells into four more; the ear grills, and goes into a potage with milk and onion, and onion into the stews and the
 * soup.</li>
 * </ul>
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ModKitchen {
    public static final DeferredBlock<HandQuernBlock> HAND_QUERN = ModBlocks.BLOCKS.registerBlock("hand_quern", HandQuernBlock::new,
            p -> p.mapColor(MapColor.STONE).strength(2.0F).requiresCorrectToolForDrops().sound(SoundType.STONE).noOcclusion());
    public static final DeferredItem<BlockItem> HAND_QUERN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HAND_QUERN);
    public static final DeferredBlock<NoodleMakerBlock> NOODLE_MAKER = ModBlocks.BLOCKS.registerBlock("noodle_maker", NoodleMakerBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.LANTERN).noOcclusion());
    public static final DeferredItem<BlockItem> NOODLE_MAKER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(NOODLE_MAKER);

    /** A board with a rim for the kitchen table: dough rises on it, and food is served on it. */
    public static final DeferredBlock<TrayBlock> WOODEN_TRAY = ModBlocks.BLOCKS.registerBlock("wooden_tray", TrayBlock::new,
            p -> p.mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOD).ignitedByLava().noOcclusion().pushReaction(PushReaction.POPPED)
                    .forceSolidOn());
    public static final DeferredItem<BlockItem> WOODEN_TRAY_ITEM = ModItems.ITEMS.registerSimpleBlockItem(WOODEN_TRAY);
    /** A white glazed plate for the table: one dish served on it (the user, 2026-10-31). */
    public static final DeferredBlock<PlateBlock> PLATE = ModBlocks.BLOCKS.registerBlock("plate", PlateBlock::new,
            p -> p.mapColor(MapColor.QUARTZ).strength(0.3F).sound(SoundType.DECORATED_POT).noOcclusion().pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> PLATE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(PLATE);
    public static final DeferredItem<Item> BREAD_DOUGH = ModItems.ITEMS.registerSimpleItem("bread_dough");
    public static final DeferredItem<Item> RISEN_DOUGH = ModItems.ITEMS.registerSimpleItem("risen_dough");
    public static final DeferredItem<Item> UDON_DOUGH = ModItems.ITEMS.registerSimpleItem("udon_dough");
    public static final DeferredItem<Item> RAW_UDON = ModItems.ITEMS.registerSimpleItem("raw_udon");
    public static final DeferredItem<Item> PANCAKE_BATTER = ModItems.ITEMS.registerSimpleItem("pancake_batter");

    // the dishes it takes most to make give a boost when eaten, as ModCooking's fried things do
    public static final DeferredItem<Item> UDON = bowlFood("udon", 8, 0.7F, new MobEffectInstance(MobEffects.SPEED, 3 * ModCooking.MINUTE));
    /** Regeneration for half a minute only: for minutes it would heal some thirty-six hearts. */
    public static final DeferredItem<Item> CREAM_STEW = bowlFood("cream_stew", 8, 0.8F,
            new MobEffectInstance(MobEffects.REGENERATION, ModCooking.MINUTE / 2));
    public static final DeferredItem<Item> CORN_POTAGE = bowlFood("corn_potage", 7, 0.6F);
    public static final DeferredItem<Item> DONUT = food("donut", 4, 0.4F);
    public static final DeferredItem<Item> PANCAKE = food("pancake", 5, 0.5F);
    public static final DeferredItem<Item> GRILLED_CORN = food("grilled_corn", 6, 0.6F);

    public static final DeferredBlock<CornBlock> CORN_CROP = ModBlocks.BLOCKS.registerBlock("corn", CornBlock::new,
            p -> p.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
    /** An ear of corn: eaten raw (sweet corn is), grilled, or shelled for its kernels. It is not planted as it is. */
    public static final DeferredItem<Item> CORN = food("corn", 2, 0.3F);
    /** Kernels dried for seed: what corn is planted from, as wheat is from its seeds. Not eaten raw. */
    public static final DeferredItem<BlockItem> CORN_KERNELS = ModItems.ITEMS.registerItem("corn_kernels",
            p -> new BlockItem(CORN_CROP.get(), p));
    public static final DeferredBlock<OnionBlock> ONION_CROP = ModBlocks.BLOCKS.registerBlock("onions", OnionBlock::new,
            p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> ONION = ModItems.ITEMS.registerItem("onion", p -> new BlockItem(ONION_CROP.get(), p),
            p -> p.food(new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).build()));

    public static final DeferredBlock<RiceBaleBlock> RICE_BALE = ModBlocks.BLOCKS.registerBlock("rice_bale", RiceBaleBlock::new,
            p -> p.mapColor(MapColor.COLOR_YELLOW).instrument(NoteBlockInstrument.BANJO).strength(0.5F).sound(SoundType.GRASS).ignitedByLava()
                    .noOcclusion()); // round, not a cube: its lids and ropes are lit, and what is behind its corners shows
    public static final DeferredItem<BlockItem> RICE_BALE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(RICE_BALE);

    private static DeferredItem<Item> food(String name, int nutrition, float saturation) {
        return ModItems.ITEMS.registerSimpleItem(name,
                p -> p.food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
    }

    /** As the other dishes in bowls: they stack to 16 and give the bowl back. */
    private static DeferredItem<Item> bowlFood(String name, int nutrition, float saturation) {
        return ModItems.ITEMS.registerSimpleItem(name, p -> p.stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build())
                .usingConvertsTo(Items.BOWL).craftRemainder(Items.BOWL));
    }

    /** The same, giving {@code effect} when it is eaten. */
    private static DeferredItem<Item> bowlFood(String name, int nutrition, float saturation, MobEffectInstance effect) {
        return ModItems.ITEMS.registerSimpleItem(name, p -> p.stacksTo(16)
                .food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build(), ModCooking.withEffect(effect))
                .usingConvertsTo(Items.BOWL).craftRemainder(Items.BOWL));
    }

    /** Straw burns as hay does. */
    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> ((FireBlockAccessor) (FireBlock) Blocks.FIRE).nookworks$setFlammable(RICE_BALE.get(), 60, 20));
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("kitchen: {}", HAND_QUERN.getId());
    }

    private ModKitchen() {}
}
