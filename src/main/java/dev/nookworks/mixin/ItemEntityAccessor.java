package dev.nookworks.mixin;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.entity.item.ItemEntity;

/**
 * The one player an item on the ground is meant for, if any (vanilla has no getter): only they may pick it up, so the
 * Magnet must not pull it towards anyone else.
 */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
    @Accessor("target")
    @Nullable UUID nookworks$getTarget();
}
