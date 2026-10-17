package dev.nookworks.client;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.feature.FoodPoisoning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Raw meat and fish say what eating them as they are risks, and how likely, while the server makes it so; and our dishes
 * that give a boost when eaten say which and for how long, as a potion does (vanilla's foods with effects say nothing).
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class FoodTooltips {
    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        FoodPoisoning.Risk risk = FoodPoisoning.risk(event.getItemStack());
        if (risk != null && NookworksConfig.SPEC.isLoaded() && NookworksConfig.RAW_FOOD_SICKNESS.get()) {
            int percent = Math.max(1, Math.round(risk.chance() * 100));
            event.getToolTip().add(Component.translatable("tooltip.nookworks.raw_food." + risk.illness().getSerializedName(), percent)
                    .withStyle(ChatFormatting.GOLD));
        }
        ItemStack stack = event.getItemStack();
        Consumable eaten = stack.get(DataComponents.CONSUMABLE);
        if (eaten == null || !BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Nookworks.MODID)) {
            return;
        }
        List<MobEffectInstance> boosts = new ArrayList<>();
        for (var effect : eaten.onConsumeEffects()) {
            if (effect instanceof ApplyStatusEffectsConsumeEffect given && given.probability() >= 1.0F) {
                boosts.addAll(given.effects());
            }
        }
        if (!boosts.isEmpty()) {
            PotionContents.addPotionTooltip(boosts, event.getToolTip()::add, 1.0F, event.getContext().tickRate());
        }
    }

    private FoodTooltips() {}
}
