package dev.nookworks.feature;

import java.util.List;

import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Breaks extra blocks on behalf of a player through {@code gameMode.destroyBlock}, so drops, enchantments,
 * tool wear, statistics and protection mods behave exactly as if each block were mined by hand.
 * Shared by timber, vein mining and hammers.
 */
final class ExtraBreaks {
    /** Guards against our own destroyBlock calls re-triggering break handlers. Server thread only. */
    private static boolean breaking;

    static boolean inProgress() {
        return breaking;
    }

    static void breakAll(ServerPlayer player, ItemStack tool, List<BlockPos> targets) {
        breaking = true;
        try {
            for (BlockPos pos : targets) {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || !ItemStack.isSameItem(held, tool) || wouldBreak(held)) {
                    break;
                }
                if (!player.level().mayInteract(player, pos)) {
                    continue; // spawn protection or the world border: vanilla only asks this of the block clicked
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            breaking = false;
        }
    }

    /**
     * The origin block is mined after the handlers and costs one more point, which is always kept for it: broken on an
     * extra block, the tool left the block clicked to an empty hand, and an ore or stone gave nothing. Protecting the
     * tool keeps one more, hence a margin of 2; without, the tool breaks on the block clicked, as by hand.
     */
    private static boolean wouldBreak(ItemStack tool) {
        int keep = NookworksConfig.PROTECT_TOOLS.getAsBoolean() ? 2 : 1;
        return tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= keep;
    }

    private ExtraBreaks() {}
}
