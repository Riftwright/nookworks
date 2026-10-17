package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.block.OilLampBlock;
import dev.nookworks.registry.ModDataComponents;
import dev.nookworks.registry.ModItems;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * What a broken machine's item still holds (its power, and its mercury, oil, fuel or water; a Stirring Tank's caustic
 * soda already taken in), and an Oil Lamp's oil, under its name.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class HeldFluidTooltips {
    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        var stack = event.getItemStack();
        if (stack.is(ModItems.OIL_LAMP.get())) {
            var state = stack.get(DataComponents.BLOCK_STATE);
            Integer oil = state == null ? null : state.get(OilLampBlock.OIL);
            if (oil != null && oil > 0) {
                event.getToolTip().add(Math.min(1, event.getToolTip().size()), Component.translatable("tooltip.nookworks.oil_lamp.oil",
                        oil, OilLampBlock.FULL).withStyle(ChatFormatting.GRAY));
            }
        }
        int at = 1;
        Integer power = stack.get(ModDataComponents.HELD_ENERGY.get());
        if (power != null && power > 0) {
            event.getToolTip().add(Math.min(at++, event.getToolTip().size()),
                    Component.translatable("tooltip.nookworks.held_energy", String.format(Locale.ROOT, "%,d", power)).withStyle(ChatFormatting.GRAY));
        }
        Integer batches = stack.get(ModDataComponents.CATALYST_LEFT.get());
        if (batches != null && batches > 0) {
            event.getToolTip().add(Math.min(at++, event.getToolTip().size()),
                    Component.translatable("tooltip.nookworks.catalyst_left", batches).withStyle(ChatFormatting.GRAY));
        }
        var held = stack.get(ModDataComponents.HELD_FLUIDS.get());
        if (held == null) {
            return;
        }
        for (var fluid : held) {
            event.getToolTip().add(Math.min(at++, event.getToolTip().size()),
                    Component.translatable("tooltip.nookworks.held_fluid", fluid.copy().getHoverName(), fluid.getAmount())
                            .withStyle(ChatFormatting.GRAY));
        }
    }

    private HeldFluidTooltips() {}
}
