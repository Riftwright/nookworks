package dev.nookworks.mixin.dev;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.nookworks.client.AgentKeys;

/**
 * Development run only ({@link DevMixins}): a key the input bridge holds counts as down, so a shift-click it sends is
 * a shift-click to the screens, which on 1.21.1 ask the keyboard's live state for the modifier keys.
 */
@Mixin(InputConstants.class)
abstract class HeldKeysMixin {
    @Inject(method = "isKeyDown(JI)Z", at = @At("HEAD"), cancellable = true)
    private static void nookworks$heldByBridge(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (AgentKeys.held.test(key)) {
            cir.setReturnValue(true);
        }
    }
}
