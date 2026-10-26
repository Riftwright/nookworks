package dev.nookworks.item;

import java.util.function.Consumer;

import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * The Horizontal Item Warp Receiver's item. Receivers no longer pair (a receiver takes from its owner's senders), but one
 * paired while they did keeps its link in its item, and set down again takes from that sender too: such an item says
 * which, and shimmers, as a Spawner Link Receiver does.
 */
public class HorizontalItemWarpReceiverItem extends BlockItem {
    public HorizontalItemWarpReceiverItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.WARP_LINK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        GlobalPos link = stack.get(ModDataComponents.WARP_LINK.get());
        if (link != null) {
            builder.accept(Component.translatable("tooltip.nookworks.horizontal_item_warp.paired", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                    LodePearlItem.dimension(link)).withStyle(ChatFormatting.GRAY));
        }
    }
}
