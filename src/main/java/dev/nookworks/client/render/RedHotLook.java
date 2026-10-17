package dev.nookworks.client.render;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.quench.Quenching;
import dev.nookworks.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * How a red-hot Velarium Ingot looks as it cools where nothing touches it - in a chest or a hopper the server does not
 * look at it, so the client works it out from the stamp: glowing while hot (items/red_hot_velarium_ingot.json's
 * condition), a plain ingot's look once cool, and the seconds of heat left under its name.
 */
public final class RedHotLook {
    public static final Identifier TYPE = Nookworks.id("still_hot");

    public record StillHot() implements ConditionalItemModelProperty {
        public static final MapCodec<StillHot> MAP_CODEC = MapCodec.unit(new StillHot());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
            ClientLevel world = level != null ? level : Minecraft.getInstance().level;
            return world == null || Quenching.isHot(stack, world.getGameTime());
        }

        @Override
        public MapCodec<StillHot> type() {
            return MAP_CODEC;
        }
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterConditionalItemModelPropertyEvent event) -> event.register(TYPE, StillHot.MAP_CODEC));
        NeoForge.EVENT_BUS.addListener(RedHotLook::tooltip);
    }

    private static void tooltip(ItemTooltipEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !event.getItemStack().is(ModItems.RED_HOT_VELARIUM_INGOT.get())) {
            return;
        }
        long left = Quenching.heatLeft(event.getItemStack(), level.getGameTime());
        event.getToolTip().add(left > 0
                ? Component.translatable("tooltip.nookworks.red_hot.hot", (left + 19) / 20).withStyle(ChatFormatting.GOLD)
                : Component.translatable("tooltip.nookworks.red_hot.cooled").withStyle(ChatFormatting.GRAY));
    }

    private RedHotLook() {}
}
