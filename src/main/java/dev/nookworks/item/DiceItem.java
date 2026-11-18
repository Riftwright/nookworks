package dev.nookworks.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;
import dev.nookworks.port.core.ItemUse;

/**
 * The Dice as an item. Used on a block it places the die; used in the air it is rolled in the hand, and everyone
 * within {@link #RANGE} blocks sees the result over their hotbar - for a board game at the table, or settling who goes
 * first - without it piling up in the chat. A second a roll at most.
 */
public class DiceItem extends BlockItem {
    public static final double RANGE = 16.0;

    public DiceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            int value = 1 + level.getRandom().nextInt(6);
            Component message = Component.translatable("nookworks.dice.rolled", player.getDisplayName(), value);
            for (Player near : level.players()) {
                if (near instanceof ServerPlayer server && near.distanceToSqr(player) <= RANGE * RANGE) {
                    server.displayClientMessage(message, true);
                }
            }
            level.playSound(null, player.blockPosition(), dev.nookworks.registry.ModSounds.DICE_ROLL.get(), SoundSource.PLAYERS, 0.7F, 1.6F);
            player.getCooldowns().addCooldown(player.getItemInHand(hand).getItem(), 20); // 1.21.1: by item
        }
        return ItemUse.holder(InteractionResult.SUCCESS, player, hand);
    }
}
