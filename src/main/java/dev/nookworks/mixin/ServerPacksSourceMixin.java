package dev.nookworks.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import dev.nookworks.feature.WorldFeatures;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * A world's list of data packs is made from its folder just before they load - by the client opening a world, a
 * dedicated server starting, a world re-created - and the recipe and advancement conditions are tested as they load:
 * the world's choice of the parts of the mod ({@link WorldFeatures}) is read from the same folder first, so they see it.
 */
@Mixin(ServerPacksSource.class)
abstract class ServerPacksSourceMixin {
    @Inject(method = "createPackRepository(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;)Lnet/minecraft/server/packs/repository/PackRepository;",
            at = @At("HEAD"))
    private static void nookworks$readWorldFeatures(LevelStorageSource.LevelStorageAccess world, CallbackInfoReturnable<PackRepository> cir) {
        WorldFeatures.opening(world.getLevelPath(LevelResource.ROOT));
    }
}
