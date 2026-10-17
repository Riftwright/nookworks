package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

/**
 * A creative tab keeps the icon it first drew for good; the Kitchen tab's depends on whether the world played has the
 * kitchen ({@code ModCreativeTabs}), so it is let go when the next world's parts are known.
 */
@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("iconItemStack")
    void nookworks$setIconItemStack(ItemStack icon);
}
