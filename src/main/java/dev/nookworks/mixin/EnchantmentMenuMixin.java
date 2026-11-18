package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.registry.ModItems;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A Liquid Experience Bottle on the Enchanting Table becomes a Bottle o' Enchanting for one lapis and no levels: the
 * first option asks one level (the table will not offer a choice with none) and takes none of it. Making the thing
 * that gives experience ought not to cost experience; the bottle's 250 mB (twelve points) already pays more than the
 * seven or so the Bottle o' Enchanting gives back.
 */
@Mixin(EnchantmentMenu.class)
abstract class EnchantmentMenuMixin {
    @Shadow
    @Final
    private Container enchantSlots;
    @Shadow
    @Final
    private ContainerLevelAccess access;

    @Inject(method = "slotsChanged(Lnet/minecraft/world/Container;)V", at = @At("HEAD"), cancellable = true)
    private void nookworks$bottleOffer(Container container, CallbackInfo ci) {
        if (container == this.enchantSlots && container.getItem(0).is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get())) {
            EnchantmentMenu menu = (EnchantmentMenu) (Object) this;
            for (int i = 0; i < 3; i++) {
                menu.costs[i] = i == 0 ? 1 : 0;
                menu.enchantClue[i] = -1;
                menu.levelClue[i] = -1;
            }
            menu.broadcastChanges();
            ci.cancel();
        }
    }

    @Inject(method = "clickMenuButton(Lnet/minecraft/world/entity/player/Player;I)Z", at = @At("HEAD"), cancellable = true)
    private void nookworks$bottleEnchant(Player player, int buttonId, CallbackInfoReturnable<Boolean> cir) {
        ItemStack bottle = this.enchantSlots.getItem(0);
        if (!bottle.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get())) {
            return;
        }
        ItemStack lapis = this.enchantSlots.getItem(1);
        if (buttonId != 0 || (lapis.isEmpty() && !player.hasInfiniteMaterials())) {
            cir.setReturnValue(false);
            return;
        }
        this.access.execute((level, pos) -> {
            this.enchantSlots.setItem(0, new ItemStack(Items.EXPERIENCE_BOTTLE));
            lapis.consume(1, player);
            if (lapis.isEmpty()) {
                this.enchantSlots.setItem(1, ItemStack.EMPTY);
            }
            this.enchantSlots.setChanged();
            level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1.0F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        });
        cir.setReturnValue(true);
    }
}
