package dev.nookworks.rope;

import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Thin Chain: Thin Rope in iron (the user, 2026-10-10). Tied to what Thin Rope ties to - a fence post, bars, a rod, a
 * hook, a wall bracket's brace - and strung to another as a rope is ({@link RopeSpans}), sagging the same, carrying the
 * same things. It is no block: it is not let down from a ceiling (vanilla's chain hangs so), so any side of a post,
 * its underside too, ties it.
 */
public class ThinChainItem extends Item {
    public ThinChainItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (context.getPlayer() == null || !RopeSpans.isPost(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        return RopeSpans.tie(context.getLevel(), context.getPlayer(), context.getClickedPos(), stack);
    }
}
