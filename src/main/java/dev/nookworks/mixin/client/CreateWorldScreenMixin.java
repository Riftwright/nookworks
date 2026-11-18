package dev.nookworks.mixin.client;

import java.nio.file.Path;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.datafixers.util.Pair;

import dev.nookworks.client.gui.WorldFeaturesScreen;
import dev.nookworks.feature.WorldFeatures;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;

/**
 * The Create World screen and the world's choice of parts ({@link WorldFeatures}). It loads the data packs - and tests
 * the recipe and advancement conditions - as it opens, before anything is chosen: then nothing is left out, whatever the
 * last world chose. A choice made on the Nookworks screen loads them again by vanilla's own way for a change of data
 * packs, and the world made is given its choice in its folder before it opens, where it reads it back.
 */
@Mixin(CreateWorldScreen.class)
abstract class CreateWorldScreenMixin extends Screen implements WorldFeaturesScreen.Host {
    protected CreateWorldScreenMixin(Component title) {
        super(title);
    }

    @Shadow
    public abstract WorldCreationUiState getUiState();

    @Shadow
    private @Nullable Pair<Path, PackRepository> getDataPackSelectionSettings(WorldDataConfiguration dataConfiguration) {
        throw new AssertionError();
    }

    @Shadow
    private void applyNewPackConfig(PackRepository packRepository, WorldDataConfiguration newConfig, Consumer<WorldDataConfiguration> onAbort) {
        throw new AssertionError();
    }

    // 1.21.1 opens a fresh Create World screen by openFresh alone (26.3: openFresh and testWorld, by openCreateWorldScreen)
    @Inject(method = "openFresh", at = @At("HEAD"))
    private static void nookworks$nothingChosenYet(CallbackInfo ci) {
        WorldFeatures.creatingWorld();
    }

    // 1.21.1 hands the new world's data over as WorldData, without 26.3's game rules apart
    @WrapOperation(method = "createNewWorld", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/worldselection/WorldOpenFlows;createLevelFromExistingSettings(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/ReloadableServerResources;Lnet/minecraft/core/LayeredRegistryAccess;Lnet/minecraft/world/level/storage/WorldData;)V"))
    private void nookworks$giveTheWorldItsChoice(WorldOpenFlows flows, LevelStorageSource.LevelStorageAccess world, ReloadableServerResources resources,
            LayeredRegistryAccess<RegistryLayer> registries, WorldData data, Operation<Void> original) {
        WorldFeatures.made(world.getLevelPath(LevelResource.ROOT));
        original.call(flows, world, resources, registries, data);
    }

    @Override
    public void nookworks$reloadDataPacks(Runnable failed) {
        WorldDataConfiguration packs = this.getUiState().getSettings().dataConfiguration();
        Pair<Path, PackRepository> selection = this.getDataPackSelectionSettings(packs);
        if (selection == null) {
            failed.run(); // no folder for the packs: vanilla has already left the screen, saying so
            return;
        }
        // the same packs: only the conditions answer otherwise. Should they fail to load, back to this screen with the
        // packs - and the choice - as they were.
        this.applyNewPackConfig(selection.getSecond(), packs, aborted -> {
            failed.run();
            this.minecraft.setScreen(this);
        });
    }
}
