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
import net.minecraft.world.level.block.Block;
import java.util.List;
import net.minecraft.world.damagesource.DamageSource;

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

    /**
     * PORT: 26.3 gives the item a barrel leaves in whole a damage_resistant component (#nookworks:whole_barrel_resists),
     * which 1.21.1 lacks; here every barrel item that holds something is spared by those damage types instead.
     */
    @Override
    public boolean canBeHurtBy(ItemStack stack, DamageSource source) {
        return !(holdsSomething(stack) && source.is(StorageBlockEntity.WHOLE_RESISTS)) && super.canBeHurtBy(stack, source);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipLines, TooltipFlag flag) {
        Consumer<Component> builder = tooltipLines::add;
        StorageContents contents = stack.getOrDefault(ModDataComponents.STORAGE.get(), StorageContents.EMPTY);
        if (!contents.isEmpty()) {
            builder.accept(Component.translatable("tooltip.nookworks.storage.contents",
                    String.format(Locale.ROOT, "%,d", contents.totalItems()), contents.entries().size()).withStyle(ChatFormatting.GRAY));
        }
    }
}
