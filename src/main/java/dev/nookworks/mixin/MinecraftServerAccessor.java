package dev.nookworks.mixin;

import java.util.Map;
import java.util.concurrent.Executor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelStorageSource;

/**
 * What a level needs to be made with, for the If ({@code dev.nookworks.ifworld.IfLevel}), which is made while the
 * server runs - when its mirror is rolled - and again at each start, rather than from the world's dimension list.
 */
@Mixin(MinecraftServer.class)
public interface MinecraftServerAccessor {
    @Accessor("executor")
    Executor nookworks$getExecutor();

    @Accessor("storageSource")
    LevelStorageSource.LevelStorageAccess nookworks$getStorageSource();

    /**
     * The server's levels, the If put among them (NeoForge's own way in, forgeGetWorldMap, is gone in 26.3). The server
     * ticks this map's levels without a copy, so the If goes in between ticks: at the start, or in a task the server runs.
     */
    @Accessor("levels")
    Map<ResourceKey<Level>, ServerLevel> nookworks$getLevels();
}
