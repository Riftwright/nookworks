package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.ChimneyBlock;
import dev.nookworks.ventilation.DuctBlock;
import dev.nookworks.ventilation.RangeHoodBlock;
import dev.nookworks.ventilation.VentFanBlock;
import dev.nookworks.ventilation.FlueBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** The Ventilation Fan, the Range Hood, Ducts and the Chimney: where the smoke of a kitchen goes (see Ventilation). */
public final class ModVentilation {
    public static final DeferredBlock<VentFanBlock> VENT_FAN = ModBlocks.BLOCKS.registerBlock("ventilation_fan", VentFanBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.LANTERN).noOcclusion());
    public static final DeferredItem<BlockItem> VENT_FAN_ITEM = ModItems.ITEMS.registerSimpleBlockItem(VENT_FAN);
    public static final DeferredBlock<RangeHoodBlock> RANGE_HOOD = ModBlocks.BLOCKS.registerBlock("range_hood", RangeHoodBlock::new,
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(2.0F).sound(SoundType.COPPER).noOcclusion());
    public static final DeferredItem<BlockItem> RANGE_HOOD_ITEM = ModItems.ITEMS.registerSimpleBlockItem(RANGE_HOOD);
    public static final DeferredBlock<DuctBlock> DUCT = ModBlocks.BLOCKS.registerBlock("duct", DuctBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(1.5F).sound(SoundType.LANTERN).noOcclusion().forceSolidOn());
    public static final DeferredItem<BlockItem> DUCT_ITEM = ModItems.ITEMS.registerSimpleBlockItem(DUCT);
    public static final DeferredBlock<ChimneyBlock> CHIMNEY = ModBlocks.BLOCKS.registerBlock("chimney", ChimneyBlock::new,
            p -> p.mapColor(MapColor.COLOR_RED).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS).noOcclusion());
    public static final DeferredItem<BlockItem> CHIMNEY_ITEM = ModItems.ITEMS.registerSimpleBlockItem(CHIMNEY);
    /** The chimney in the other masonry, beside the brick one (the user, 2026-10-24 and 10-31); the clay pot on each. */
    public static final DeferredBlock<ChimneyBlock> STONE_BRICK_CHIMNEY = chimney("stone_brick_chimney", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<ChimneyBlock> NETHER_BRICK_CHIMNEY = chimney("nether_brick_chimney", MapColor.NETHER, SoundType.NETHER_BRICKS);
    public static final DeferredBlock<ChimneyBlock> COBBLESTONE_CHIMNEY = chimney("cobblestone_chimney", MapColor.STONE, SoundType.STONE);
    public static final DeferredItem<BlockItem> STONE_BRICK_CHIMNEY_ITEM = ModItems.ITEMS.registerSimpleBlockItem(STONE_BRICK_CHIMNEY);
    public static final DeferredItem<BlockItem> NETHER_BRICK_CHIMNEY_ITEM = ModItems.ITEMS.registerSimpleBlockItem(NETHER_BRICK_CHIMNEY);
    public static final DeferredItem<BlockItem> COBBLESTONE_CHIMNEY_ITEM = ModItems.ITEMS.registerSimpleBlockItem(COBBLESTONE_CHIMNEY);
    /** A whole block of each chimney's masonry with the flue down it, for where the chimney goes through a roof. */
    public static final DeferredBlock<FlueBlock> BRICK_FLUE = flue("brick_flue", MapColor.COLOR_RED, SoundType.MUD_BRICKS);
    public static final DeferredBlock<FlueBlock> STONE_BRICK_FLUE = flue("stone_brick_flue", MapColor.STONE, SoundType.STONE);
    public static final DeferredBlock<FlueBlock> NETHER_BRICK_FLUE = flue("nether_brick_flue", MapColor.NETHER, SoundType.NETHER_BRICKS);
    public static final DeferredBlock<FlueBlock> COBBLESTONE_FLUE = flue("cobblestone_flue", MapColor.STONE, SoundType.STONE);
    public static final DeferredItem<BlockItem> BRICK_FLUE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(BRICK_FLUE);
    public static final DeferredItem<BlockItem> STONE_BRICK_FLUE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(STONE_BRICK_FLUE);
    public static final DeferredItem<BlockItem> NETHER_BRICK_FLUE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(NETHER_BRICK_FLUE);
    public static final DeferredItem<BlockItem> COBBLESTONE_FLUE_ITEM = ModItems.ITEMS.registerSimpleBlockItem(COBBLESTONE_FLUE);

    private static DeferredBlock<ChimneyBlock> chimney(String id, MapColor colour, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(id, ChimneyBlock::new,
                p -> p.mapColor(colour).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(sound).noOcclusion());
    }

    private static DeferredBlock<FlueBlock> flue(String id, MapColor colour, SoundType sound) {
        return ModBlocks.BLOCKS.registerBlock(id, FlueBlock::new,
                p -> p.mapColor(colour).strength(2.0F, 6.0F).requiresCorrectToolForDrops().sound(sound));
    }

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("ventilation: {}", VENT_FAN.getId());
    }

    private ModVentilation() {}
}
