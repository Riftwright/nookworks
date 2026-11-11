package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mojang.renderpearl.api.textures.GpuTextureView;

import dev.nookworks.client.render.MirrorLight;
import net.minecraft.client.renderer.GameRenderer;

/**
 * While a Standing Mirror's picture is drawn under a shader pack, the game's lightmap is read as the table measured from
 * the pack ({@link MirrorLight}): the terrain's draws and the entities' both ask the game renderer for it here. Missing
 * (another mod having changed the call), the picture is lit by the game's own table, as it was ({@code require = 0}).
 */
@Mixin(GameRenderer.class)
abstract class GameRendererMirrorLightMixin {
    @Inject(method = "lightmap", require = 0, at = @At("HEAD"), cancellable = true)
    private void nookworks$mirrorLight(CallbackInfoReturnable<GpuTextureView> cir) {
        GpuTextureView table = MirrorLight.inUse();
        if (table != null) {
            cir.setReturnValue(table);
        }
    }
}
