package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import dev.nookworks.client.WorldFeaturesClient;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.network.chat.Component;

/**
 * The Create World screen's More tab gets a Nookworks button under vanilla's Game Rules, Experiments and Data Packs,
 * for the world's choice of parts ({@link WorldFeaturesClient#addButton}). The tab is a private class built with the
 * screen, and NeoForge offers no way into it.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
abstract class CreateWorldMoreTabMixin extends GridLayoutTab {
    CreateWorldMoreTabMixin(Component title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void nookworks$addPartsButton(CallbackInfo ci) {
        WorldFeaturesClient.addButton(this.layout);
    }
}
