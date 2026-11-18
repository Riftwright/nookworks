package dev.nookworks.port.core;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.portal.DimensionTransition;

/**
 * 26.3's {@code ServerPlayer.teleport(TeleportTransition)}: once no mod has refused the trip, it takes the player off
 * whatever they ride and moves them. 1.21.1's {@code changeDimension} moves a rider and leaves them riding a mount left
 * behind (its own portals move the mount, which carries its riders): here they are taken off it, as in 26.3. Returns the
 * player moved, or null when the trip was refused (they then stay riding, as in 26.3).
 */
public final class Teleports {
    public static @Nullable ServerPlayer teleport(ServerPlayer player, DimensionTransition transition) {
        ServerPlayer moved = (ServerPlayer) player.changeDimension(transition);
        if (moved != null && moved.isPassenger()) {
            moved.removeVehicle(); // not stopRiding: that would set them down at the mount, where they were
        }
        return moved;
    }

    private Teleports() {}
}
