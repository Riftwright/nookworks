package dev.nookworks.spawner;

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

/** Shows which sender the receiver is paired with. */
public class SpawnerLinkReceiverItem extends BlockItem {
    public SpawnerLinkReceiverItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.SPAWNER_LINK.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        GlobalPos link = stack.get(ModDataComponents.SPAWNER_LINK.get());
        builder.accept((link == null
                ? Component.translatable("tooltip.nookworks.spawner_link.unpaired")
                : Component.translatable("message.nookworks.spawner_link.target", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                        LodePearlItem.dimension(link)))
                .withStyle(ChatFormatting.GRAY));
    }
}
