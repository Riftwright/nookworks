package dev.nookworks.sorter;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import java.util.List;

/** A 0.2.0 sorter still in a chest or a hand ({@link LegacySorterBlock}): it says what it becomes when set down. */
public class LegacySorterItem extends BlockItem {
    public LegacySorterItem(LegacySorterBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipLines, TooltipFlag flag) {
        Consumer<Component> builder = tooltipLines::add;
        SorterGear gear = ((LegacySorterBlock) this.getBlock()).gear;
        builder.accept(Component.translatable("tooltip.nookworks.legacy_sorter", SorterBlockEntity.gearItem(gear).getHoverName())
                .withStyle(ChatFormatting.GRAY));
    }
}
