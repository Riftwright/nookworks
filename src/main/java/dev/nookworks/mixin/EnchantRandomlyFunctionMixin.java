package dev.nookworks.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import dev.nookworks.feature.Rootbound;
import net.minecraft.core.Holder;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;

/**
 * Rootbound only on offer while Nookworks' tree felling works (Rootbound#offered), Earthmover while the hammers are on
 * (Earthmover#offered): what a librarian's enchanted book
 * (the {@code enchant_randomly} function over #minecraft:tradeable) is drawn from.
 */
@Mixin(EnchantRandomlyFunction.class)
abstract class EnchantRandomlyFunctionMixin {
    @ModifyArg(method = "run", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"), index = 0)
    private List<Holder<Enchantment>> nookworks$rootboundWithFelling(List<Holder<Enchantment>> candidates) {
        return Rootbound.allOffered() ? candidates : candidates.stream().filter(Rootbound::offerable).toList();
    }
}
