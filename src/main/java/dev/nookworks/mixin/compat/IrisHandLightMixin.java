package dev.nookworks.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import dev.nookworks.feature.UnlitCampfires;
import net.minecraft.world.item.ItemStack;

/**
 * A campfire in the hand that stands for an unlit one ({@link UnlitCampfires#unlit(ItemStack)}) casts no light under a
 * shader pack. Iris reads an item's light from its block's default state, and a campfire's is lit: held, it lit up the
 * ground round the player, then went dark as it was set down unlit (the user, 2026-11-01). A lit one named so, and
 * every campfire with the setting off, shines as before.
 * <p>
 * Applied only when Iris is there ({@link CompatMixins}), and {@code @Pseudo} besides.
 */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.uniforms.IdMapUniforms$HeldItemSupplier", remap = false)
abstract class IrisHandLightMixin {
    @ModifyExpressionValue(method = {"update", "applyOldHandLighting"}, at = @At(value = "INVOKE",
            target = "Lnet/irisshaders/iris/api/v0/item/IrisItemLightProvider;getLightEmission(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)I"))
    private int nookworks$unlitCampfire(int light, @Local ItemStack stack) {
        return light > 0 && UnlitCampfires.unlit(stack) ? 0 : light;
    }
}
