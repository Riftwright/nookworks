package dev.nookworks.feature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A world's choice of parts changed while it is open, from the mod's settings (the user, 2026-10-31: 「ゲーム中のNWの
 * コンフィグにも入れていいじゃない？」). The server writes the world's file ({@link WorldFeatures#FILE}), which is read as
 * the world opens, so the change takes effect the next time it does: switching a part's recipes and loot in and out
 * under a running world would leave it half one and half the other. Only the single player who owns the world, or an
 * operator, may change it; anyone may look.
 */
public final class WorldPartsEditing {
    /**
     * What a server shows a player of its world's parts: its file's choice; which parts its start-up config lets a world
     * have, and whether it lets worlds choose at all (the server's, not the player's own config: r19); and whether this
     * player may change it.
     */
    public record View(Map<String, Boolean> parts, Map<String, Boolean> offered, boolean menu, boolean editable) {}

    /** On a client: shows the server's {@link View} (set by the client). */
    public static volatile Consumer<View> shown = view -> {};

    private WorldPartsEditing() {}

    /** Whether {@code player} may change the world's parts: its single player owner, or an operator, and the pack lets worlds choose. */
    public static boolean mayChange(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return WorldFeatures.menuShown() && (server.isSingleplayerOwner(player.nameAndId())
                || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    }

    /**
     * What the world's file says now, every part named: what it was made with, or (no file) what is in force. With the
     * pack's worldMenu off the file is not read, so what is in force (r19 re-check).
     */
    public static Map<String, Boolean> inFile(MinecraftServer server) {
        WorldFeatures.Choice chosen = WorldFeatures.menuShown() ? WorldFeatures.read(world(server)) : null;
        Map<String, Boolean> parts = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> parts.put(part, chosen != null ? !chosen.leftOut(part) : WorldFeatures.switchedOn(part)));
        return parts;
    }

    /** {@code player} asked to see the world's parts. */
    public static void asked(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new NookworksNetwork.WorldPartsFile(view(player)));
    }

    /** What {@code player} is shown of the world's parts, by this server's config. */
    public static View view(ServerPlayer player) {
        Map<String, Boolean> offered = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> offered.put(part, WorldFeatures.offered(part)));
        return new View(inFile(player.level().getServer()), offered, WorldFeatures.menuShown(), mayChange(player));
    }

    /** {@code player} sent a new choice, {@code parts}: written for the next opening if they may, and said so. */
    public static void set(ServerPlayer player, Map<String, Boolean> parts) {
        if (!mayChange(player)) {
            player.sendSystemMessage(Component.translatable("nookworks.world_features.not_allowed"));
            return;
        }
        Map<String, Boolean> known;
        try {
            known = save(world(player.level().getServer()), parts);
        } catch (IOException e) {
            Nookworks.LOGGER.error("Could not write the world's choice of Nookworks parts", e);
            player.sendSystemMessage(Component.translatable("nookworks.world_features.not_saved"));
            return;
        }
        Nookworks.LOGGER.info("{} changed this world's Nookworks parts, for its next opening: {}", player.getName().getString(), known);
        player.sendSystemMessage(Component.translatable("nookworks.world_features.saved"));
    }

    /** Writes {@code parts} into the world folder {@code world}'s file, every part named (one not sent is on); what was written. */
    public static Map<String, Boolean> save(Path world, Map<String, Boolean> parts) throws IOException {
        Map<String, Boolean> known = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> known.put(part, parts.getOrDefault(part, true)));
        WorldFeatures.write(world, known);
        return known;
    }

    private static Path world(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT);
    }
}
