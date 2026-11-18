package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.systems.RenderSystem;

import dev.nookworks.client.render.MirrorLight;
import net.minecraft.client.renderer.LightTexture;

/**
 * While a Standing Mirror's picture is drawn under a shader pack, the game's lightmap is read as the table measured from
 * the pack ({@link MirrorLight}): the terrain's draws and the entities' both have it bound here. PORT: 26.3 asks the
 * game renderer for its lightmap (hence this hook's name, kept); 1.21.1 binds it as the third shader texture in
 * {@code LightTexture#turnOnLightLayer}, which every lit render type calls as it is set up. Missing (another mod having
 * changed the call), the picture is lit by the game's own table, as it was ({@code require = 0}).
 */
@Mixin(LightTexture.class)
abstract class GameRendererMirrorLightMixin {
    @Inject(method = "turnOnLightLayer", require = 0, at = @At("HEAD"), cancellable = true)
    private void nookworks$mirrorLight(CallbackInfo ci) {
        int table = MirrorLight.inUse();
        if (table != 0) {
            RenderSystem.setShaderTexture(2, table);
            ci.cancel();
        }
    }
}
