package dev.nookworks.item;

import dev.nookworks.feature.Fireproofing;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Fireproof coating: use on a flammable block (planks, logs, wool...) to stop it from ever burning.
 * One use per block; the pot has limited uses (durability).
 */
public class FireproofCoatingItem extends Item {
    public FireproofCoatingItem(Properties properties) {
        super(properties);
    }

    public static boolean isFlammable(Level level, BlockPos pos, BlockState state) {
        for (Direction face : Direction.values()) {
            if (state.isFlammable(level, pos, face)) {
                return true;
            }
        }
        return false;
    }

    /** Before the block's own right-click, or a door or chest would only open and never be coated. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (!isFlammable(level, pos, level.getBlockState(pos))) {
            return InteractionResult.PASS;
        }
        if (Fireproofing.isCoated(level, pos)) {
            if (player != null && level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("item.nookworks.fireproof_coating.already"));
            }
            return InteractionResult.PASS; // already coated: the block is used as it would be
        }
        if (!level.isClientSide()) {
            Fireproofing.coat(level, pos);
            // 26.3's wax event is the particles alone: the sound is played beside it, as HoneycombItem does
            level.levelEvent(null, LevelEvent.PARTICLES_WAX_ON, pos, 0);
            level.playSound(null, pos, SoundEvents.HONEYCOMB_WAX_ON, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            }
        }
        return InteractionResult.SUCCESS;
    }
}
