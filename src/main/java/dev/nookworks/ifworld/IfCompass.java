package dev.nookworks.ifworld;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSetDefaultSpawnPositionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Not lost in the If: there a compass points to the If's mirror, the way home (a compass points at the world's spawn,
 * which the If, being another dimension, would not have). The client is told the mirror is the spawn point while the
 * player is in the If; vanilla tells it the real one again as they leave.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class IfCompass {
    /** The world's spawn as last told to everyone, to see an operator's /setworldspawn. Server thread only. */
    private static LevelData.@Nullable RespawnData toldSpawn;

    private IfCompass() {}

    /** Points {@code player}'s compass at the If's mirror, if they are in the If and it has one. */
    public static void point(ServerPlayer player) {
        if (!player.level().dimension().equals(IfWorld.KEY)) {
            return;
        }
        BlockPos mirror = IfWorld.get(player.level().getServer()).ifMirror();
        // with the mirror gone, the world's spawn (not in the If, so the needle spins) rather than where it stood
        player.connection.send(new ClientboundSetDefaultSpawnPositionPacket(mirror != null ? LevelData.RespawnData.of(IfWorld.KEY, mirror, 0.0F, 0.0F)
                : player.level().getRespawnData()));
    }

    /** Everyone in the If, after its mirror is set down somewhere new or taken away. */
    static void pointAll(MinecraftServer server) {
        ServerLevel level = server.getLevel(IfWorld.KEY);
        if (level != null) {
            level.players().forEach(IfCompass::point);
        }
    }

    /**
     * The world's spawn set anew (/setworldspawn) is told to every player, those in the If too, whose needles then
     * pointed at a place in another dimension and spun until they went out and in again (2026-10-22): told the mirror
     * again at the end of the same tick, the packet straight after vanilla's.
     */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        LevelData.RespawnData spawn = event.getServer().getWorldData().overworldData().getRespawnData();
        if (!spawn.equals(toldSpawn)) {
            boolean changed = toldSpawn != null;
            toldSpawn = spawn;
            if (changed) {
                pointAll(event.getServer());
            }
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        toldSpawn = null;
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            point(player);
        }
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            point(player);
        }
    }

    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            point(player);
        }
    }
}
