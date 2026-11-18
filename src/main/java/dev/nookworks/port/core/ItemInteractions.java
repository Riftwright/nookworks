package dev.nookworks.port.core;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

/**
 * A block's {@code useItemOn} returns 26.3's {@code InteractionResult} and 1.21.1's {@code ItemInteractionResult}.
 * tx/core.py turns the constants in its body itself; a value from elsewhere (a helper, a shared constant) is passed
 * through {@link #of}. 26.3's {@code TRY_WITH_EMPTY_HAND} has no {@code InteractionResult} counterpart in 1.21.1, so a
 * helper that returns it has to return {@code ItemInteractionResult} itself (then {@link #of} lets it through as is).
 */
public final class ItemInteractions {
    private ItemInteractions() {}

    /** 26.3 PASS after an item use on a block is "the item's own use goes ahead": 1.21.1's SKIP_DEFAULT. */
    public static ItemInteractionResult of(InteractionResult result) {
        return switch (result) {
            case SUCCESS, SUCCESS_NO_ITEM_USED -> ItemInteractionResult.SUCCESS;
            case CONSUME -> ItemInteractionResult.CONSUME;
            case CONSUME_PARTIAL -> ItemInteractionResult.CONSUME_PARTIAL;
            case PASS -> ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
            case FAIL -> ItemInteractionResult.FAIL;
        };
    }

    public static ItemInteractionResult of(ItemInteractionResult result) {
        return result;
    }
}
