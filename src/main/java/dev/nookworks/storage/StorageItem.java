package dev.nookworks.storage;

import java.util.Locale;
import java.util.function.Consumer;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.Block;

/**
 * Storage Barrel item. A broken barrel spills what it held (only the Crate keeps its contents), so the item is an empty
 * barrel and packs away like any block - into another barrel or a shulker box. One made to carry contents (by a
 * command) says so, and goes into nothing: a barrel of full barrels would nest warehouses.
 */
public class StorageItem extends BlockItem {
    public StorageItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** Whether this barrel item carries anything, which only a command can make it do. */
    public static boolean holdsSomething(ItemStack stack) {
        return !stack.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY).isEmpty()
                || stack.has(DataComponents.BLOCK_ENTITY_DATA);
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return !holdsSomething(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder,
            TooltipFlag flag) {
        StorageContents contents = stack.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY);
        if (!contents.isEmpty()) {
            builder.accept(Component.translatable("tooltip.nookworks.storage.contents",
                    String.format(Locale.ROOT, "%,d", contents.totalItems()), contents.entries().size()).withStyle(ChatFormatting.GRAY));
        }
    }
}
