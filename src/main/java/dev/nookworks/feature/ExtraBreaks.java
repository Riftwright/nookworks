package dev.nookworks.feature;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.nookworks.NookworksConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks extra blocks on behalf of a player through {@code gameMode.destroyBlock}, so drops, enchantments,
 * tool wear, statistics and protection mods behave exactly as if each block were mined by hand.
 * Shared by timber, vein mining and hammers, and public for add-ons' area tools (nookTechnology's Heavy Hammer), so
 * the breaks they make are known as extra ones and set off no vein mining or felling of their own.
 */
public final class ExtraBreaks {
    /**
     * How many of a swing's extra blocks the miner sees and hears break. The server tells everyone near but the miner of
     * each break (the miner's own client foresees the block clicked only), so the extra ones went without a crumble or
     * a sound for the one who broke them (r21, 2026-11-06); a whole tree's worth would be a din, so the nearest only.
     */
    static final int SHOWN = 16;

    /** Guards against our own destroyBlock calls re-triggering break handlers. Server thread only. */
    private static boolean breaking;

    /**
     * Whether a break event is {@code player}'s own break of the block they clicked - not another mod asking about one,
     * nor a block another mod breaks for them inside their own break (FTB Ultimine's run): an area tool starts its sweep
     * only from this one, as our hammers, felling and vein mining do (r21; public for add-ons' area tools).
     */
    public static boolean isOwnBreak(ServerPlayer player, BlockPos pos) {
        return ChainBreaking.outermost(player, pos);
    }

    /** Whether extra blocks are being broken now: a break handler seeing one leaves it alone. */
    public static boolean inProgress() {
        return breaking;
    }

    /**
     * Breaks {@code targets} in order as {@code player} mining them with {@code tool}, stopping when the tool is no
     * longer in the main hand or would break (one use kept for the block clicked, two with tool protection on), and
     * skipping blocks the player may not touch there (spawn protection, the world border).
     */
    public static void breakAll(ServerPlayer player, ItemStack tool, List<BlockPos> targets) {
        breaking = true;
        int shown = 0;
        Vec3 eye = player.getEyePosition();
        Set<BlockPos> nearest = new HashSet<>(targets.stream()
                .sorted(Comparator.comparingDouble(pos -> pos.distToCenterSqr(eye)))
                .limit(SHOWN).toList());
        try {
            for (BlockPos pos : targets) {
                ItemStack held = player.getMainHandItem();
                if (held.isEmpty() || !ItemStack.isSameItem(held, tool) || wouldBreak(held)) {
                    break;
                }
                if (!player.level().mayInteract(player, pos)) {
                    continue; // spawn protection or the world border: vanilla only asks this of the block clicked
                }
                BlockState state = player.level().getBlockState(pos);
                if (player.gameMode.destroyBlock(pos) && nearest.contains(pos) && player.connection != null) { // a test's player has none
                    shown++;
                    player.connection.send(new ClientboundLevelEventPacket(LevelEvent.PARTICLES_AND_SOUND_DESTROY_BLOCK, pos,
                            Block.getId(state), false));
                }
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
