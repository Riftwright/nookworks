package dev.nookworks.client.render;

import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.registry.GlazedPots;
import net.minecraft.client.color.block.BlockTintSources;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/**
 * A fern in a glazed pot is tinted like vanilla's potted fern: by the grass colour where it stands. (Nourished Soil was
 * tinted here too, over vanilla's dirt, until it had textures of its own: tools/textures/gen_soil.py.)
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class GlazedPotTint {
    @SubscribeEvent
    static void blocks(RegisterColorHandlersEvent.BlockTintSources event) {
        GlazedPots.FULL.stream()
                .filter(pot -> pot.getId().getPath().equals("glazed_potted_fern"))
                .forEach(pot -> event.register(List.of(BlockTintSources.grass()), pot.get()));
    }

    private GlazedPotTint() {}
}
