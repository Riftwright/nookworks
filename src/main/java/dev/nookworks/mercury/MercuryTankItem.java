package dev.nookworks.mercury;

import java.util.function.Consumer;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/** Mercury Tank item: shows how much mercury it carries. */
public class MercuryTankItem extends BlockItem {
    public MercuryTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        builder.accept(Component.translatable("nookworks.mercury.amount", stack.getOrDefault(ModDataComponents.MERCURY.get(), 0),
                MercuryTankBlockEntity.CAPACITY).withStyle(ChatFormatting.GRAY));
    }
}
