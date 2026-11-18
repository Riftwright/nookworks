package dev.nookworks.port.core;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * {@code Item.use} returns an {@code InteractionResult} in 26.3 and an {@code InteractionResultHolder<ItemStack>} in
 * 1.21.1. tx/core.py turns each {@code return x;} of an overriding {@code use} into {@code return ItemUse.holder(x,
 * player, hand);}: the same result, carrying the stack in hand as it is now (26.3's "held item transformed to" is
 * {@code SUCCESS.heldItemTransformedTo(stack)}, which has to be written as {@code InteractionResultHolder.success(stack)}
 * by hand). A value that already is a holder (from {@code ItemUtils.startUsingInstantly}, say) goes through as it is.
 */
public final class ItemUse {
    private ItemUse() {}

    public static InteractionResultHolder<ItemStack> holder(InteractionResult result, Player player, InteractionHand hand) {
        return new InteractionResultHolder<>(result, player.getItemInHand(hand));
    }

    public static InteractionResultHolder<ItemStack> holder(InteractionResultHolder<ItemStack> result, Player player,
            InteractionHand hand) {
        return result;
    }
}
