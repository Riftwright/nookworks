package dev.nookworks.feature;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.compat.FeatureGate;
import dev.nookworks.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Leaf gathering, a Silk Touch axe's switch: on (the default), a tree it fells drops its leaves as leaves
 * ({@link FastLeafDecay}); off, they decay as after any other axe. Sneak and use the axe in the air to switch it; the
 * axe remembers (the user, 2026-10-26: not everyone with a Silk Touch axe wants every crown on the ground, and a world
 * setting cannot ask each player - nor say "this tree, not that one").
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class LeafGathering {
    private LeafGathering() {}

    /** After a switch the axe rests this long: a held button repeats the use every 4 ticks, and would flip it back. */
    static final int COOLDOWN = 10;
    private static final java.util.Set<String> OURS = java.util.Set.of("minecraft", Nookworks.MODID);

    /** Whether this axe's leaf gathering is on: unless switched off, it is. */
    public static boolean gathers(ItemStack axe) {
        return axe.getOrDefault(ModDataComponents.GATHERS_LEAVES.get(), true);
    }

    /**
     * An axe with Silk Touch, while felling and fast leaf decay are on: the axes the switch means anything on. Vanilla's
     * and ours only: another mod's tool in the axes tag (a paxel) may have a sneak-use of its own.
     */
    public static boolean applies(ItemStack stack) {
        return stack.is(ItemTags.AXES) && OURS.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace()) && hasSilkTouch(stack) && FeatureGate.timber() && NookworksConfig.FAST_LEAF_DECAY.getAsBoolean();
    }

    static boolean hasSilkTouch(ItemStack stack) {
        return stack.getEnchantments().keySet().stream().anyMatch(enchantment -> enchantment.is(Enchantments.SILK_TOUCH));
    }

    /**
     * Sneak-use in the air switches it. Not at a block (a sneak-click there with a block in the other hand places it) or a
     * mob, and not with a thing of use in the other hand - a shield raised, food eaten, a bow drawn go first, as they would
     * without this (blocks and bone meal there do nothing in the air).
     */
    @SubscribeEvent
    static void clickAir(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack axe = event.getItemStack();
        ItemStack other = player.getOffhandItem();
        if (event.getHand() != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !applies(axe)
                || !(other.isEmpty() || other.getItem() instanceof BlockItem || other.is(Items.BONE_MEAL))
                || player.pick(player.blockInteractionRange(), 1.0F, false).getType() != HitResult.Type.MISS
                || aimsAtEntity(player)) {
            return;
        }
        if (!event.getLevel().isClientSide()) {
            boolean on = !gathers(axe);
            if (on) {
                axe.remove(ModDataComponents.GATHERS_LEAVES.get());
            } else {
                axe.set(ModDataComponents.GATHERS_LEAVES.get(), false);
            }
            event.getLevel().playSound(null, player.blockPosition(), SoundEvents.AZALEA_LEAVES_HIT, SoundSource.PLAYERS, 0.8F,
                    on ? 1.2F : 0.8F);
            player.sendOverlayMessage(Component.translatable(on ? "message.nookworks.leaf_gathering.on" : "message.nookworks.leaf_gathering.off"));
            player.getCooldowns().addCooldown(axe, COOLDOWN);
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /** Whether a mob, boat or armour stand is under the crosshair, within reach. */
    private static boolean aimsAtEntity(Player player) {
        double reach = player.entityInteractionRange();
        Vec3 eyes = player.getEyePosition(1.0F);
        Vec3 end = eyes.add(player.getViewVector(1.0F).scale(reach));
        return ProjectileUtil.getEntityHitResult(player, eyes, end, player.getBoundingBox().expandTowards(end.subtract(eyes)).inflate(1.0),
                entity -> !entity.isSpectator() && entity.isPickable(), reach * reach) != null;
    }
}
