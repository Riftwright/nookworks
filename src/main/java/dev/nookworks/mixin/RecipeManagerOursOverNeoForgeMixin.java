package dev.nookworks.mixin;

import java.util.Map;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.gson.JsonElement;

import dev.nookworks.port.core.OursOverNeoForge;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.crafting.RecipeManager;

/** Our copies of vanilla recipes over NeoForge's, before they load ({@link OursOverNeoForge}). */
@Mixin(RecipeManager.class)
abstract class RecipeManagerOursOverNeoForgeMixin {
    @Inject(method = "apply(Ljava/util/Map;Lnet/minecraft/server/packs/resources/ResourceManager;Lnet/minecraft/util/profiling/ProfilerFiller;)V",
            at = @At("HEAD"))
    private void nookworks$oursOverNeoForge(Map<ResourceLocation, JsonElement> recipes, ResourceManager manager, ProfilerFiller profiler,
            CallbackInfo ci) {
        OursOverNeoForge.apply(recipes, manager, "recipe");
    }
}
