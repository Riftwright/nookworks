package dev.nookworks.port.core;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.world.entity.player.Player;

/**
 * 26.3's {@code net.minecraft.server.permissions.Permissions} as the mod uses it: the command permission levels.
 * 26.3 asks {@code player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}; 1.21.1 asks
 * {@code player.hasPermissions(2)} (a command source: {@code source.hasPermission(2)}). tx/core.py turns the 26.3
 * call into {@code Permissions.has(player, Permissions.COMMANDS_GAMEMASTER)}.
 */
public final class Permissions {
    private Permissions() {}

    public static final int COMMANDS_MODERATOR = 1;
    public static final int COMMANDS_GAMEMASTER = 2;
    public static final int COMMANDS_ADMIN = 3;
    public static final int COMMANDS_OWNER = 4;

    public static boolean has(Player player, int level) {
        return player.hasPermissions(level);
    }

    public static boolean has(CommandSourceStack source, int level) {
        return source.hasPermission(level);
    }
}
