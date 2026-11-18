package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3's {@code ItemTintSource}: the colour of one tint index of an item's model, named in its item definition. 1.21.1
 * has no item definitions: an {@code ItemColor} for the item answers each index, and the port's
 * {@link RegisterColorHandlersEvent.ItemTintSources} fills them in from the table {@link ItemModelProperties#TINTS}.
 */
public interface ItemTintSource {
    int calculate(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner);

    MapCodec<? extends ItemTintSource> type();
}
