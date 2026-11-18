package dev.nookworks.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import org.jspecify.annotations.Nullable;

/**
 * Players for the tests, in the level as a joining player is. Unlike the helper's own mock (deprecated), whose game mode
 * is creative whatever it is set to - a test that set it to survival or spectator still had a creative player, and
 * passed whether or not the code under test looked - this one is in the game mode it is set to. It is creative to begin
 * with, as the helper's was. And it is taken out of the level when the test ends, passed or failed: one left behind
 * lets vanilla's despawning and spawners near it run.
 */
public final class MockPlayers {
    private MockPlayers() {}

    /**
     * The channels of a mock player's connection: every payload but our own. NeoForge's are needed (it sends a joining
     * player its synced attachments unasked), and in the port's runs beside other mods so are theirs: Create, the
     * Twilight Forest and more send every player payloads as they join, change dimension or each tick without asking
     * whether the client has them, and one refused stopped the whole run. Ours stay out, so the mod's own "has this
     * client our channel" checks answer no, as they do for a mock player on 26.3.
     */
    private static final java.util.Set<String> OURS = java.util.Set.of(dev.nookworks.Nookworks.MODID);
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, net.neoforged.neoforge.network.registration.NetworkChannel>
            NEOFORGE_ONLY = new java.util.HashMap<>() {
                @Override
                public boolean containsKey(Object id) {
                    return id instanceof net.minecraft.resources.ResourceLocation name && !OURS.contains(name.getNamespace());
                }
            };

    public static ServerPlayer inLevel(GameTestHelper helper) {
        return inLevel(helper, GameType.CREATIVE);
    }

    public static ServerPlayer inLevel(GameTestHelper helper, GameType mode) {
        return join(helper, mode, null);
    }

    /** {@link #inLevel}, putting each line it is shown over its hotbar into {@code said} (a refused click's reason). */
    public static ServerPlayer hearing(GameTestHelper helper, GameType mode, List<Component> said) {
        return join(helper, mode, said);
    }

    private static ServerPlayer join(GameTestHelper helper, GameType mode, @Nullable List<Component> said) {
        MinecraftServer server = helper.getLevel().getServer();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = said == null ? new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation())
                : new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
                    // 1.21.1 has no sendOverlayMessage: a line over the hotbar is a client message sent as an overlay
                    @Override
                    public void displayClientMessage(Component message, boolean overlay) {
                        if (overlay) {
                            said.add(message);
                        }
                        super.displayClientMessage(message, overlay);
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        // PORT: NeoForge 21.1 sends a joining player its synced attachments without asking whether the connection takes
        // them, and a connection that negotiated nothing refuses every payload but vanilla's. This one says it takes
        // NeoForge's own, and still none of any mod's - so the mod's own "has this client our channel" checks answer
        // no, as they do for a mock player on 26.3.
        net.neoforged.neoforge.network.registration.ChannelAttributes.setPayloadSetup(connection,
                new net.neoforged.neoforge.network.registration.NetworkPayloadSetup(java.util.Map.of(
                        net.minecraft.network.ConnectionProtocol.PLAY, NEOFORGE_ONLY,
                        net.minecraft.network.ConnectionProtocol.CONFIGURATION, NEOFORGE_ONLY)));
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        AfterTest.undo(helper, () -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                server.getPlayerList().remove(player); // unless the test took it out itself
            }
        });
        return player;
    }

    /**
     * 26.3's {@code player.connection.markClientLoaded()}: a player just joined cannot be hurt until then. 1.21.1 has
     * no such gate; its counterpart is the three seconds of spawn protection a new ServerPlayer starts with
     * ({@code spawnInvulnerableTime}, private, counted down only as the player ticks), so that is set to none.
     */
    public static void markClientLoaded(ServerPlayer player) {
        try {
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("ServerPlayer.spawnInvulnerableTime not found", e);
        }
    }
}
