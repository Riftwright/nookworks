package dev.nookworks.rope;

import dev.nookworks.wood.SupportBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Thin Rope: let down from a ceiling as a block, and tied to a post - a fence post, bars, a rod, a hook, a wall
 * bracket's brace - to string it to another ({@link RopeSpans}). A fence post's or bars' underside still takes it as a
 * ceiling does: the rope hangs down from it; a hook or a rod is tied to from any side, and so is a bracket - but for its
 * beam's underside, clicked from below past the brace, which a rope hangs from as before.
 */
public class ThinRopeItem extends BlockItem {
    public ThinRopeItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var post = context.getLevel().getBlockState(context.getClickedPos());
        if (context.getPlayer() == null || !RopeSpans.isPost(post)
                || context.getClickedFace() == Direction.DOWN && hangsUnder(post, context)) {
            return InteractionResult.PASS;
        }
        return RopeSpans.tie(context.getLevel(), context.getPlayer(), context.getClickedPos(), stack);
    }

    /** Clicked from below, whether the rope is let down from {@code post} rather than tied to it. */
    private static boolean hangsUnder(BlockState post, UseOnContext context) {
        if (post.getBlock() instanceof HookBlock || post.getBlock() instanceof RodBlock) {
            return false; // a rod under a ceiling is clicked from below, on its tip
        }
        // a bracket's brace and its upright end low in the block; its beam's underside lies above the middle
        return !SupportBlock.braced(post) || context.getClickLocation().y - context.getClickedPos().getY() > 0.5;
    }
}
