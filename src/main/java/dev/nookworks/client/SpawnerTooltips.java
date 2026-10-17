package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Names the mob of a spawner item picked up with a Silk Touch hammer. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class SpawnerTooltips {
    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        EntityType<?> type = event.getItemStack().get(ModDataComponents.SPAWNER_ENTITY.get());
        if (type != null) {
            event.getToolTip().add(Math.min(1, event.getToolTip().size()), Component.translatable("tooltip.nookworks.spawner.entity", type.getDescription())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    private SpawnerTooltips() {}
}
