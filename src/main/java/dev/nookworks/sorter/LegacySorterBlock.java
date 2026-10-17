package dev.nookworks.sorter;

import dev.nookworks.block.SorterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * The Golden, Diamond and Netherite Item Sorters of 0.2.0, when speed was the sorter's own. They are kept so worlds and
 * inventories that have them lose nothing: one set down, or loaded, turns at once into an Item Sorter with the matching
 * Sorter Gear fitted, its filter, settings and what it held kept (see {@link SorterBlockEntity#serverTick}). They are
 * made no more.
 */
public class LegacySorterBlock extends SorterBlock {
    /** The gear it becomes an Item Sorter with. */
    public final SorterGear gear;

    public LegacySorterBlock(SorterGear gear, BlockBehaviour.Properties properties) {
        super(SorterTier.ITEM, properties);
        this.gear = gear;
    }
}
