package dev.nookworks.mixin;

import java.util.stream.Stream;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import dev.nookworks.feature.Rootbound;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * Rootbound only on offer while Nookworks' tree felling works (Rootbound#offered), Earthmover while the hammers are on
 * (Earthmover#offered): what the enchanting table (and an
 * {@code enchant_with_levels} loot function) picks from. It cannot be left out of the tags, which a setting switched in
 * play does not reload.
 */
@Mixin(EnchantmentHelper.class)
abstract class EnchantmentHelperMixin {
    @ModifyVariable(method = "getAvailableEnchantmentResults", at = @At("HEAD"), argsOnly = true)
    private static Stream<Holder<Enchantment>> nookworks$rootboundWithFelling(Stream<Holder<Enchantment>> source) {
        return Rootbound.allOffered() ? source : source.filter(Rootbound::offerable);
    }
}
