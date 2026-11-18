package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3's {@code ConditionalItemModelProperty}: a yes or no an item definition switches its model on. In 1.21.1 it is an
 * item property (an override predicate, 1 or 0) of the items listed for it in {@link ItemModelProperties}; 1.21.1 does
 * not say where the item is drawn, so the display context passed is {@code NONE}.
 */
public interface ConditionalItemModelProperty {
    boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context);

    MapCodec<? extends ConditionalItemModelProperty> type();
}
