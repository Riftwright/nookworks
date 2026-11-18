package dev.nookworks.compat.create;

import com.simibubi.create.compat.thresholdSwitch.ThresholdSwitchCompat;

import dev.nookworks.storage.StorageBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

/** A Storage Barrel's slot holds what the barrel's tier lets it (two stacks and more), for Create's Threshold Switch. */
public final class StorageBarrelSpace implements ThresholdSwitchCompat {
    @Override
    public boolean isFromThisMod(BlockEntity be) {
        return be instanceof StorageBlockEntity;
    }

    @Override
    public long getSpaceInSlot(IItemHandler inventory, int slot) {
        return inventory.getSlotLimit(slot);
    }
}
