package dev.nookworks.registry;

import dev.nookworks.Nookworks;
import dev.nookworks.ventilation.ChimneyBlock;
import dev.nookworks.ventilation.DuctBlock;
import dev.nookworks.ventilation.RangeHoodBlock;
import dev.nookworks.ventilation.VentFanBlock;
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

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("ventilation: {}", VENT_FAN.getId());
    }

    private ModVentilation() {}
}
