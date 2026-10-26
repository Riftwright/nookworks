package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.LeafGathering;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A Silk Touch axe says whether its leaf gathering is on, and how to switch it (LeafGathering). */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class LeafGatheringTooltips {
    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (LeafGathering.applies(stack)) {
            event.getToolTip().add(Component.translatable("tooltip.nookworks.leaf_gathering",
                    Component.translatable(LeafGathering.gathers(stack) ? "tooltip.nookworks.leaf_gathering.on" : "tooltip.nookworks.leaf_gathering.off"))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private LeafGatheringTooltips() {}
}
