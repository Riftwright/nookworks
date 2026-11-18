package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.google.gson.JsonObject;

import net.minecraft.util.GsonHelper;

/**
 * A block model element turned by any angle, as 26.3 reads one: 1.21.1's reader takes only 0, 22.5 and 45 degrees
 * either way, and the Pebbles' stones lie at 8, 18, -30 and -56 (models/block/pebbles_*.json). The baking turns a
 * vertex by whatever angle it is given (FaceBakery.applyElementRotation, a quaternion of the angle), so only the reader
 * stands in the way; an element that asks to be rescaled is left to vanilla's check, as its rescaling is worked out for
 * 22.5 and 45 alone.
 */
@Mixin(targets = "net.minecraft.client.renderer.block.model.BlockElement$Deserializer")
abstract class BlockElementAngleMixin {
    @Inject(method = "getAngle", at = @At("HEAD"), cancellable = true)
    private void nookworks$anyAngle(JsonObject rotation, CallbackInfoReturnable<Float> cir) {
        if (!GsonHelper.getAsBoolean(rotation, "rescale", false)) {
            cir.setReturnValue(GsonHelper.getAsFloat(rotation, "angle"));
        }
    }
}
