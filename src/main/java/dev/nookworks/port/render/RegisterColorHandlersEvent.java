package dev.nookworks.port.render;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.color.block.BlockColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * 26.3 NeoForge's {@code RegisterColorHandlersEvent}, its two kinds the mod uses. The port posts each on every mod's
 * bus from 1.21.1's event of the same purpose ({@link PortClientEvents}).
 */
public final class RegisterColorHandlersEvent {
    private RegisterColorHandlersEvent() {}

    /**
     * A block's tints, a source for each tint index (the first for index 0). Registered as a 1.21.1 {@link BlockColor}
     * that asks the source for the index it is given: with no world (a block held, or drawn as an item), its plain
     * colour; in the world, its colour there. An index past the list is untinted (white).
     */
    public static final class BlockTintSources extends Event implements IModBusEvent {
        private final net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event;

        BlockTintSources(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
            this.event = event;
        }

        public void register(List<? extends BlockTintSource> sources, Block... blocks) {
            List<BlockTintSource> byIndex = List.copyOf(sources);
            BlockColor colour = (state, level, pos, tintIndex) -> {
                if (tintIndex < 0 || tintIndex >= byIndex.size()) {
                    return -1;
                }
                BlockTintSource source = byIndex.get(tintIndex);
                return level == null || pos == null ? source.color(state) : source.colorInWorld(state, level, pos);
            };
            this.event.register(colour, blocks);
        }
    }

    /**
     * The kinds of item tint, by the id an item definition names them by. 1.21.1 has no item definitions: the tints each
     * item takes, by index, are listed in {@link ItemModelProperties#TINTS} (another mod's through
     * {@link ItemModelProperties#addTints}), which makes them from these.
     */
    public static final class ItemTintSources extends Event implements IModBusEvent {
        static final Map<ResourceLocation, MapCodec<? extends ItemTintSource>> TYPES = new ConcurrentHashMap<>();

        ItemTintSources() {}

        public void register(ResourceLocation location, MapCodec<? extends ItemTintSource> source) {
            TYPES.put(location, source);
        }
    }
}
