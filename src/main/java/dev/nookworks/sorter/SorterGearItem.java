package dev.nookworks.sorter;

import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A Sorter Gear, fitted into the gear slot of either sorter as a Brass Mesh is into the Sifter's: it makes the sorter
 * work faster ({@link SorterGear}), and comes out again as it went in.
 */
public class SorterGearItem extends Item {
    public final SorterGear gear;

    public SorterGearItem(SorterGear gear, Properties properties) {
        super(properties);
        this.gear = gear;
    }

    /** The gear {@code stack} is, or NONE. */
    public static SorterGear of(ItemStack stack) {
        return stack.getItem() instanceof SorterGearItem item ? item.gear : SorterGear.NONE;
    }

    /** How fast a sorter with {@code gear} works, in words. */
    public static Component speed(SorterGear gear) {
        if (gear.batch >= 64) {
            return Component.translatable("tooltip.nookworks.sorter_gear.stack");
        }
        if (gear.batch > 1) {
            return Component.translatable("tooltip.nookworks.sorter_gear.batch", gear.batch);
        }
        return Component.translatable("tooltip.nookworks.sorter_gear.interval", String.format(Locale.ROOT, "%.1f", gear.interval / 20.0));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        builder.accept(Component.translatable("tooltip.nookworks.sorter_gear", speed(this.gear)).withStyle(ChatFormatting.GRAY));
    }
}
