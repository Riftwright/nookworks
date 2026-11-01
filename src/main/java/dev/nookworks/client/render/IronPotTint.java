package dev.nookworks.client.render;

import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModCooking;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** Water standing in an Iron Pot is tinted like water where it stands (the model's tintindex 0); a Sight Glass's, WindowTint. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class IronPotTint {
    @SubscribeEvent
    static void blocks(RegisterColorHandlersEvent.BlockTintSources event) {
        event.register(List.of(BlockTintSources.water()), ModCooking.IRON_POT.get());
    }

    private IronPotTint() {}
}
