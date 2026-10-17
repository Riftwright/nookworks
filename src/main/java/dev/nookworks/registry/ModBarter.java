package dev.nookworks.registry;

import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.barter.BarterTerminalBlock;
import dev.nookworks.barter.BarterTerminalBlockEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/** The Barter Terminal, which keeps piglins out of the Nether in a room of their own (see BarterTerminalBlockEntity). */
public final class ModBarter {
    public static final DeferredBlock<BarterTerminalBlock> BARTER_TERMINAL = ModBlocks.BLOCKS.registerBlock("barter_terminal",
            BarterTerminalBlock::new, p -> p.mapColor(MapColor.METAL).strength(3.0F, 6.0F).requiresCorrectToolForDrops()
                    .sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> BARTER_TERMINAL_ITEM = ModItems.ITEMS.registerSimpleBlockItem(BARTER_TERMINAL);
    public static final Supplier<BlockEntityType<BarterTerminalBlockEntity>> BARTER_TERMINAL_ENTITY =
            ModBlockEntities.BLOCK_ENTITY_TYPES.register("barter_terminal",
                    () -> new BlockEntityType<>(BarterTerminalBlockEntity::new, BARTER_TERMINAL.get()));

    /** Loads this class, so its blocks and items are registered with the rest. */
    public static void init() {
        Nookworks.LOGGER.debug("barter: {}", BARTER_TERMINAL.getId());
    }

    private ModBarter() {}
}
