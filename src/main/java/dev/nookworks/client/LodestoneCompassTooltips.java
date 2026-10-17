package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.pearl.LodestoneNames;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** A lodestone compass says which lodestone it points to, by the lodestone's name ({@link LodestoneNames}), as a Lode Pearl does. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class LodestoneCompassTooltips {
    private LodestoneCompassTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof dev.nookworks.pearl.LodePearlItem) {
            return; // a pearl says it itself
        }
        LodestoneTracker tracker = event.getItemStack().get(DataComponents.LODESTONE_TRACKER);
        if (tracker != null) {
            tracker.target().flatMap(LodestoneNames::of).ifPresent(name -> event.getToolTip().add(Math.min(1, event.getToolTip().size()),
                    Component.translatable("tooltip.nookworks.lodestone_compass", name).withStyle(ChatFormatting.GRAY)));
        }
    }
}
