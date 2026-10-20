package dev.nookworks.registry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.recycling.CompressorBlock;
import dev.nookworks.recycling.CompressorBlockEntity;
import dev.nookworks.recycling.CorrugatedSheetBlock;
import dev.nookworks.recycling.RecyclerBlock;
import dev.nookworks.recycling.RecyclerBlockEntity;
import dev.nookworks.recycling.RustingSheetBlock;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.WeatheringCopperCollection;
import net.minecraft.world.level.block.WeatheringCopperFullBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Recycling (DECISIONS, "Recycling", 2026-10-18): the Compressor, the Recycler and the Baler it is made with; Scrap, and
 * what is built of it - Scrap Bales, Corrugated Sheets (bare, which rust as copper weathers, and painted, which do not)
 * and Patched Sheet Metal.
 */
public final class ModRecycling {
    public static final DeferredItem<Item> SCRAP = ModItems.ITEMS.registerSimpleItem("scrap");
    /** The press the Recycler packs its scrap with: a part, not a machine (the user, 2026-10-18). */
    public static final DeferredItem<Item> BALER = ModItems.ITEMS.registerSimpleItem("baler");

    public static final DeferredBlock<CompressorBlock> COMPRESSOR = ModBlocks.BLOCKS.registerBlock("compressor",
            CompressorBlock::new, p -> machine(p).noOcclusion());
    public static final DeferredItem<BlockItem> COMPRESSOR_ITEM = ModItems.ITEMS.registerSimpleBlockItem(COMPRESSOR);
    public static final DeferredBlock<RecyclerBlock> RECYCLER = ModBlocks.BLOCKS.registerBlock("recycler",
            RecyclerBlock::new, p -> machine(p).noOcclusion().pushReaction(PushReaction.IMMOVEABLE));
    /** Placed as a door is: the space above made empty first, so the intake never knocks the body off. */
    public static final DeferredItem<DoubleHighBlockItem> RECYCLER_ITEM = ModItems.ITEMS.registerItem("recycler",
            p -> new DoubleHighBlockItem(RECYCLER.get(), p), p -> p.useBlockDescriptionPrefix());

    public static final Supplier<BlockEntityType<CompressorBlockEntity>> COMPRESSOR_ENTITY =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("compressor", () -> new BlockEntityType<>(CompressorBlockEntity::new, COMPRESSOR.get()));
    public static final Supplier<BlockEntityType<RecyclerBlockEntity>> RECYCLER_ENTITY =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("recycler", () -> new BlockEntityType<>(RecyclerBlockEntity::new, RECYCLER.get()));

    /** Scrap Bales by age, then the waxed ones: "scrap_bale", "exposed_scrap_bale", ..., "waxed_oxidized_scrap_bale". */
    public static final Map<String, DeferredBlock<Block>> SCRAP_BALES = weathering("scrap_bale", false);
    /** Bare Corrugated Sheets by age, then the waxed ones, named as the bales are. */
    public static final Map<String, DeferredBlock<Block>> CORRUGATED_SHEETS = weathering("corrugated_sheet", true);
    /** The painted sheets: these do not rust. */
    public static final List<DyeColor> PAINTS = List.of(DyeColor.RED, DyeColor.GREEN, DyeColor.BLUE, DyeColor.WHITE);
    public static final Map<DyeColor, DeferredBlock<Block>> PAINTED_SHEETS = painted();
    public static final DeferredBlock<Block> PATCHED_SHEET_METAL = ModBlocks.BLOCKS.registerSimpleBlock("patched_sheet_metal",
            p -> metal(p).mapColor(MapColor.METAL));
    public static final DeferredItem<BlockItem> PATCHED_SHEET_METAL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(PATCHED_SHEET_METAL);
    /** The block items of the bales and sheets, in the order above. */
    public static final Map<String, DeferredItem<BlockItem>> BUILDING_ITEMS = buildingItems();

    private static BlockBehaviour.Properties machine(BlockBehaviour.Properties p) {
        return p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static BlockBehaviour.Properties metal(BlockBehaviour.Properties p) {
        return p.strength(3.0F, 6.0F).requiresCorrectToolForDrops().sound(SoundType.METAL);
    }

    private static MapColor rust(WeatheringCopper.WeatherState age) {
        return switch (age) {
            case UNAFFECTED -> MapColor.METAL;
            case EXPOSED -> MapColor.COLOR_LIGHT_GRAY;
            case WEATHERED -> MapColor.TERRACOTTA_ORANGE;
            case OXIDIZED -> MapColor.TERRACOTTA_BROWN;
        };
    }

    private static Map<String, DeferredBlock<Block>> weathering(String base, boolean sheet) {
        Map<String, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
        for (WeatheringCopper.WeatherState age : WeatheringCopper.WeatherState.values()) {
            String name = WeatheringCopperCollection.PREFIXES.weathering().pick(age) + base;
            blocks.put(name, ModBlocks.BLOCKS.registerBlock(name,
                    sheet ? p -> new RustingSheetBlock(age, p) : p -> new WeatheringCopperFullBlock(age, p),
                    p -> sheetOrBale(metal(p).mapColor(rust(age)).randomTicks(), sheet)));
        }
        for (WeatheringCopper.WeatherState age : WeatheringCopper.WeatherState.values()) {
            String name = WeatheringCopperCollection.PREFIXES.waxed().pick(age) + base;
            blocks.put(name, ModBlocks.BLOCKS.registerBlock(name, sheet ? CorrugatedSheetBlock::new : Block::new,
                    p -> sheetOrBale(metal(p).mapColor(rust(age)), sheet)));
        }
        return blocks;
    }

    private static BlockBehaviour.Properties sheetOrBale(BlockBehaviour.Properties p, boolean sheet) {
        return sheet ? p.strength(1.5F, 6.0F).noOcclusion() : p;
    }

    private static Map<DyeColor, DeferredBlock<Block>> painted() {
        Map<DyeColor, DeferredBlock<Block>> blocks = new LinkedHashMap<>();
        for (DyeColor colour : PAINTS) {
            blocks.put(colour, ModBlocks.BLOCKS.registerBlock(colour.getName() + "_corrugated_sheet", CorrugatedSheetBlock::new,
                    p -> metal(p).strength(1.5F, 6.0F).noOcclusion().mapColor(colour.getMapColor())));
        }
        return blocks;
    }

    private static Map<String, DeferredItem<BlockItem>> buildingItems() {
        Map<String, DeferredItem<BlockItem>> items = new LinkedHashMap<>();
        SCRAP_BALES.forEach((name, block) -> items.put(name, ModItems.ITEMS.registerSimpleBlockItem(block)));
        CORRUGATED_SHEETS.forEach((name, block) -> items.put(name, ModItems.ITEMS.registerSimpleBlockItem(block)));
        PAINTED_SHEETS.forEach((colour, block) -> items.put(colour.getName() + "_corrugated_sheet", ModItems.ITEMS.registerSimpleBlockItem(block)));
        return items;
    }

    public static void init() {
        Nookworks.LOGGER.debug("recycling: {}", RECYCLER.getId());
    }

    private ModRecycling() {}
}
