package dev.nookworks.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
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
                    @Override
                    public void sendOverlayMessage(Component message) {
                        said.add(message);
                        super.sendOverlayMessage(message);
                    }
                };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(mode);
        AfterTest.undo(helper, () -> {
            if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                server.getPlayerList().remove(player); // unless the test took it out itself
            }
        });
        return player;
    }
}
