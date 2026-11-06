package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.UnlitCampfires;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;

/**
 * A campfire item looks unlit while campfires are set down unlit ({@link UnlitCampfires#unlit(ItemStack)}): the
 * condition items/campfire.json and soul_campfire.json switch on, between vanilla's unlit campfire block and its own
 * burning icon. The setting is the server's, sent to the client as it joins.
 */
public final class UnlitCampfireLook {
    public static final Identifier TYPE = Nookworks.id("campfire_unlit");

    public record Unlit() implements ConditionalItemModelProperty {
        public static final MapCodec<Unlit> MAP_CODEC = MapCodec.unit(new Unlit());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
            return UnlitCampfires.unlit(stack);
        }

        @Override
        public MapCodec<Unlit> type() {
            return MAP_CODEC;
        }
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterConditionalItemModelPropertyEvent event) -> event.register(TYPE, Unlit.MAP_CODEC));
    }

    private UnlitCampfireLook() {}
}
