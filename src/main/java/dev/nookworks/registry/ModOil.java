package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.block.MotorBlock;
import dev.nookworks.drive.DriveWheelBlock;
import dev.nookworks.item.OilCanItem;
import dev.nookworks.item.SoapItem;
import dev.nookworks.item.WoodVinegarItem;
import dev.nookworks.mixin.FireBlockAccessor;
import dev.nookworks.oil.OilFluids;
import dev.nookworks.oil.OilPressBlock;
import dev.nookworks.oil.SunflowerCropBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** Vegetable oil: the placed liquid, its bucket and its bottle, and the Oil Press (with its Motor) that makes it. */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class ModOil {
    public static final DeferredBlock<LiquidBlock> VEGETABLE_OIL = ModBlocks.BLOCKS.registerBlock("vegetable_oil",
            p -> new LiquidBlock(OilFluids.SOURCE.get(), p),
            p -> p.mapColor(MapColor.GOLD).replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.POPPED)
                    .noLootTable().liquid().sound(SoundType.EMPTY));
    public static final DeferredItem<BucketItem> VEGETABLE_OIL_BUCKET = ModItems.ITEMS.registerItem("vegetable_oil_bucket",
            p -> new BucketItem(OilFluids.SOURCE.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredBlock<LiquidBlock> BIODIESEL_BLOCK = ModBlocks.BLOCKS.registerBlock("biodiesel",
            p -> new LiquidBlock(OilFluids.BIODIESEL.get(), p),
            p -> p.mapColor(MapColor.GOLD).replaceable().noCollision().strength(100.0F).pushReaction(PushReaction.POPPED)
                    .noLootTable().liquid().sound(SoundType.EMPTY));
    public static final DeferredItem<BucketItem> BIODIESEL_BUCKET = ModItems.ITEMS.registerItem("biodiesel_bucket",
            p -> new BucketItem(OilFluids.BIODIESEL.get(), p), p -> p.craftRemainder(Items.BUCKET).stacksTo(1));
    public static final DeferredBlock<OilPressBlock> OIL_PRESS = ModBlocks.BLOCKS.registerBlock("oil_press",
            OilPressBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5F, ModBlocks.KEEPER_BLAST_RESISTANCE) // a keeper of oil
                    .sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> OIL_PRESS_ITEM = ModItems.ITEMS.registerSimpleBlockItem(OIL_PRESS);
    /** Turns the handle of a hand-worked machine on FE. Here because the Oil Press is the first such machine. */
    public static final DeferredBlock<MotorBlock> MOTOR = ModBlocks.BLOCKS.registerBlock("motor",
            MotorBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> MOTOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(MOTOR);
    /** A toothed wheel on an axle: on a Motor's shaft, or against a machine, with belts between them (dev.nookworks.drive). */
    public static final DeferredBlock<DriveWheelBlock> DRIVE_WHEEL = ModBlocks.BLOCKS.registerBlock("drive_wheel",
            DriveWheelBlock::new, p -> p.mapColor(MapColor.METAL).strength(2.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.CHAIN).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    public static final DeferredItem<BlockItem> DRIVE_WHEEL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(DRIVE_WHEEL);
    /** Sown in farmland, it grows into vanilla's sunflower; what the Oil Press presses for its oil (not the flower). */
    public static final DeferredBlock<SunflowerCropBlock> SUNFLOWER_CROP = ModBlocks.BLOCKS.registerBlock("sunflower_crop",
            SunflowerCropBlock::new, p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.CROP)
                    .pushReaction(PushReaction.POPPED));
    public static final DeferredItem<BlockItem> SUNFLOWER_SEEDS = ModItems.ITEMS.registerItem("sunflower_seeds",
            p -> new BlockItem(SUNFLOWER_CROP.get(), p));
    /** What is left of pressed seeds: a fertilizer, used like bone meal. */
    public static final DeferredItem<BoneMealItem> OIL_CAKE = ModItems.ITEMS.registerItem("oil_cake", BoneMealItem::new);
    /** Lubricant for hinges and pistons; each drop is a point of durability. */
    public static final DeferredItem<OilCanItem> OIL_CAN = ModItems.ITEMS.registerItem("oil_can",
            OilCanItem::new, p -> p.durability(64));
    /** Vegetable oil boiled with caustic soda in the Iron Pot; each scrub is a point of durability. */
    public static final DeferredItem<SoapItem> SOAP = ModItems.ITEMS.registerItem("soap",
            SoapItem::new, p -> p.durability(16));
    /**
     * Wood vinegar: the smoky liquor a log gives off with its charcoal in the Sealed Retort. Sprinkled on a field, or
     * distilled for methanol.
     */
    public static final DeferredItem<Item> WOOD_VINEGAR = ModItems.ITEMS.registerItem("wood_vinegar",
            WoodVinegarItem::new, p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
    /** Wood alcohol: distilled from wood vinegar, for making biodiesel. */
    public static final DeferredItem<Item> METHANOL = bottle("methanol");
    /** Vegetable oil turned with methanol over caustic soda in the Iron Pot: a fuel that burns twice as long as the oil. */
    public static final DeferredItem<Item> BIODIESEL_BOTTLE = bottle("biodiesel_bottle");
    /** A quarter of a bucket, for the pot and the kitchen. */
    public static final DeferredItem<Item> VEGETABLE_OIL_BOTTLE = ModItems.ITEMS.registerSimpleItem("vegetable_oil_bottle",
            p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));

    private static DeferredItem<Item> bottle(String name) {
        return ModItems.ITEMS.registerSimpleItem(name, p -> p.craftRemainder(Items.GLASS_BOTTLE).stacksTo(16));
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("oil: {}", VEGETABLE_OIL.getId());
    }

    /** Spilt oil catches from a fire beside it (or from lava's sparks) and burns away, as readily as leaves. */
    @SubscribeEvent
    static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            FireBlockAccessor fire = (FireBlockAccessor) (FireBlock) Blocks.FIRE;
            fire.nookworks$setFlammable(VEGETABLE_OIL.get(), 30, 60);
            fire.nookworks$setFlammable(BIODIESEL_BLOCK.get(), 60, 100);
        });
    }

    private ModOil() {}
}
