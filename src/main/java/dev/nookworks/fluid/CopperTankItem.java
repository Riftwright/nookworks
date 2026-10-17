package dev.nookworks.fluid;

import java.util.function.Consumer;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.SimpleFluidContent;

/** A Copper Tank as an item: it says what it still holds. */
public class CopperTankItem extends BlockItem {
    public CopperTankItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        SimpleFluidContent held = stack.getOrDefault(ModDataComponents.FLUID_CONTENTS.get(), SimpleFluidContent.EMPTY);
        builder.accept((held.isEmpty()
                ? Component.translatable("nookworks.copper_tank.empty", CopperTankBlockEntity.CAPACITY)
                : Component.translatable("nookworks.copper_tank.holds", held.copy().getHoverName(), held.getAmount(), CopperTankBlockEntity.CAPACITY))
                .withStyle(ChatFormatting.GRAY));
    }
}
