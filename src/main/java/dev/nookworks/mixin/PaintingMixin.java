package dev.nookworks.mixin;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.nookworks.poster.PaintingFrames;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.painting.Painting;

/**
 * A framed painting broken - hit, its wall gone - drops its frame beside the painting ({@link PaintingFrames}, the user,
 * 2026-11-13: 「バニラの絵画にも額縁付けられるようにしてほしい」). No event comes when a hanging entity drops what it is, so
 * this is the one way in.
 */
@Mixin(Painting.class)
abstract class PaintingMixin {
    @Inject(method = "dropItem", at = @At("HEAD"))
    private void nookworks$dropFrame(ServerLevel level, @Nullable Entity causedBy, CallbackInfo ci) {
        PaintingFrames.dropFrame((Painting) (Object) this, level, causedBy);
    }
}
