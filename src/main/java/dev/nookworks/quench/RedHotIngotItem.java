package dev.nookworks.quench;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A Velarium Ingot fresh out of the fire (see {@link Quenching}). Too hot to hold: in a survival player's inventory it
 * burns them and is dropped, and on the ground it is not picked up while it glows, smoking. Cooled, it is a plain ingot
 * again, wherever it is looked at - on the ground, or in a hand that has it.
 */
public class RedHotIngotItem extends Item {
    public RedHotIngotItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player) || player.isCreative() || player.isSpectator() || stack.isEmpty()) {
            return;
        }
        ItemStack held = stack.copy();
        stack.setCount(0);
        // dropped by the server alone (no client foresaw it): SERVER_ONLY, so the player's own arm is seen to let go
        if (Quenching.isHot(held, level.getGameTime())) {
            player.hurtServer(level, level.damageSources().source(Quenching.BURN), 2.0F);
            player.sendOverlayMessage(Component.translatable("message.nookworks.red_hot.too_hot").withStyle(ChatFormatting.RED));
            player.drop(held, false, Prediction.SERVER_ONLY);
        } else if (!player.getInventory().add(Quenching.cooled(held))) {
            player.drop(Quenching.cooled(held), false, Prediction.SERVER_ONLY);
        }
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        boolean hot = Quenching.isHot(stack, entity.level().getGameTime());
        if (entity.level() instanceof ServerLevel) {
            if (!hot) {
                entity.setItem(Quenching.cooled(stack));
            } else if (!stack.has(ModDataComponents.HEATED_AT.get())) {
                // one made by a command or out of the creative tab: it starts cooling the moment it lies on the ground
                entity.setItem(Quenching.stamp(stack.copy(), entity.level().getGameTime()));
            }
        } else if (hot && entity.tickCount % 5 == 0) {
            entity.level().addParticle(ParticleTypes.SMOKE, entity.getX(), entity.getY() + 0.25, entity.getZ(), 0, 0.04, 0);
        }
        return false;
    }
}
