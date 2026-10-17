package dev.nookworks.registry;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.trading.TradingCounterBlock;
import dev.nookworks.trading.TradingCounterBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** The Trading Counter, over which a villager sells (see TradingCounterBlockEntity). */
public final class ModTrading {
    public static final DeferredBlock<TradingCounterBlock> TRADING_COUNTER = ModBlocks.BLOCKS.registerBlock("trading_counter",
            TradingCounterBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5F).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredItem<BlockItem> TRADING_COUNTER_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TRADING_COUNTER);
    public static final Supplier<BlockEntityType<TradingCounterBlockEntity>> TRADING_COUNTER_ENTITY =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("trading_counter",
                    () -> new BlockEntityType<>(TradingCounterBlockEntity::new, TRADING_COUNTER.get()));

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("trading: {}", TRADING_COUNTER.getId());
    }

    private ModTrading() {}
}
