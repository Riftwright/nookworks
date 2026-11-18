package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.crop.EndSproutBlock;
import dev.nookworks.crop.RiceBlock;
import dev.nookworks.crop.RicePaniclesBlock;
import dev.nookworks.crop.RicePolisherBlock;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import dev.nookworks.port.core.Props;

/**
 * Crops, and what is made of them. Rice comes from sifting soil as brown rice, the grain to plant (with Farmer's Delight
 * installed the Sifter hands out its rice instead, see {@link #preferred}); the Rice Polisher rubs it down to white
 * rice, the grain to cook, and the bran it rubs off goes to the Oil Press, the pickle tub, the compost and the chickens.
 */
public final class ModCrops {
    public static final DeferredBlock<RiceBlock> RICE_CROP = ModBlocks.BLOCKS.registerBlock("rice", RiceBlock::new,
            Props.block(p -> p.mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.WET_GRASS).pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<RicePaniclesBlock> RICE_PANICLES = ModBlocks.BLOCKS.registerBlock("rice_panicles",
            RicePaniclesBlock::new,
            Props.block(p -> p.mapColor(MapColor.PLANT).noCollission().instabreak().sound(SoundType.CROP).pushReaction(PushReaction.DESTROY)));
    // PORT: 1.21.1 names a plain BlockItem after its block (the Rice Plant, not Brown Rice); ItemNameBlockItem keeps the
    // item's own name, as 26.3 gives every item
    public static final DeferredItem<BlockItem> BROWN_RICE = ModItems.ITEMS.registerItem("brown_rice", p -> new net.minecraft.world.item.ItemNameBlockItem(RICE_CROP.get(), p));
    public static final DeferredItem<Item> WHITE_RICE = ModItems.ITEMS.registerSimpleItem("white_rice");
    /** Rubbed off brown rice. A fertilizer too, used like bone meal. */
    public static final DeferredItem<BoneMealItem> RICE_BRAN = ModItems.ITEMS.registerItem("rice_bran", BoneMealItem::new);
    public static final DeferredBlock<RicePolisherBlock> RICE_POLISHER = ModBlocks.BLOCKS.registerBlock("rice_polisher",
            RicePolisherBlock::new, Props.block(p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredItem<BlockItem> RICE_POLISHER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(RICE_POLISHER);
    /** Sifted, rarely, from End Gravel; sown in farmland it grows into a plant drawn by lot (dev.nookworks.crop.EndSeedPlant). */
    public static final DeferredBlock<EndSproutBlock> END_SPROUT = ModBlocks.BLOCKS.registerBlock("end_sprout",
            EndSproutBlock::new, Props.block(p -> p.mapColor(MapColor.PLANT).noCollission().randomTicks().instabreak().sound(SoundType.CROP)
                    .pushReaction(PushReaction.DESTROY)));
    // PORT: 1.21.1 names a plain BlockItem after its block (the Rice Plant, not Brown Rice); ItemNameBlockItem keeps the
    // item's own name, as 26.3 gives every item
    public static final DeferredItem<BlockItem> END_SEED = ModItems.ITEMS.registerItem("end_seed",
            p -> new net.minecraft.world.item.ItemNameBlockItem(END_SPROUT.get(), p));

    private static final ResourceLocation FARMERS_DELIGHT_RICE = ResourceLocation.fromNamespaceAndPath("farmersdelight", "rice");
    private static final ResourceLocation FARMERS_DELIGHT_ONION = ResourceLocation.fromNamespaceAndPath("farmersdelight", "onion");

    /**
     * The rice or onion to hand out: Farmer's Delight's when it is installed, so a pack with it has one rice and one
     * onion to plant and cook rather than two (our recipes take any {@code c:crops/rice} and {@code c:crops/onion});
     * ours otherwise. Anything else is returned as it is.
     */
    public static ItemStack preferred(ItemStack stack) {
        ResourceLocation theirs = stack.is(BROWN_RICE.get()) ? FARMERS_DELIGHT_RICE : stack.is(ModKitchen.ONION.get()) ? FARMERS_DELIGHT_ONION : null;
        if (theirs == null || !ModList.get().isLoaded("farmersdelight")) {
            return stack;
        }
        return BuiltInRegistries.ITEM.getOptional(theirs)
                .map(item -> new ItemStack(item, stack.getCount()))
                .orElse(stack);
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("crops: {}", BROWN_RICE.getId());
    }

    private ModCrops() {}
}
