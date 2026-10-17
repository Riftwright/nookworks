package dev.nookworks.client.render;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.decor.BottleLook;
import dev.nookworks.decor.PlacedBottlesBlock;
import dev.nookworks.decor.PlacedBottlesBlockEntity;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

/** What is in each set-down bottle, in its colour: tint index i is the liquid of the bottle in place i. */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class BottleTint {
    @SubscribeEvent
    static void blocks(RegisterColorHandlersEvent.BlockTintSources event) {
        List<BlockTintSource> places = new ArrayList<>();
        for (int i = 0; i < PlacedBottlesBlock.MAX; i++) {
            int place = i;
            places.add(new BlockTintSource() {
                @Override
                public int color(BlockState state) {
                    return -1;
                }

                @Override
                public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                    return level.getBlockEntity(pos) instanceof PlacedBottlesBlockEntity bottles
                            ? BottleLook.liquid(bottles.bottle(place)) : -1;
                }
            });
        }
        event.register(places, ModBlocks.PLACED_BOTTLES.get());
    }

    private BottleTint() {}
}
