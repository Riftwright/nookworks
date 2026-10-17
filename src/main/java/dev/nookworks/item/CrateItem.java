package dev.nookworks.item;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public class CrateItem extends BlockItem {
    public CrateItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** A filled crate must not go into another crate, a shulker box or a bundle. */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }
}
