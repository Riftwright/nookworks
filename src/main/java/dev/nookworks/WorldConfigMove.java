package dev.nookworks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.mojang.logging.LogUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * A world's own settings from before 26.3 carried over (r14, R14-CXC-003): NeoForge read a world's copy of
 * nookworks-server.toml from its serverconfig/ folder and now reads it from syncedconfig/, before any mod is told the
 * server is starting. So the first start on 26.3 copies the old file across (never over one already there) and says
 * so - in the log and to whoever joins - for the settings take effect from the next start; until then the global
 * config/nookworks-server.toml holds.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WorldConfigMove {
    static final String FILE = "nookworks-server.toml";
    private static boolean moved;

    @SubscribeEvent
    static void onAboutToStart(ServerAboutToStartEvent event) {
        moved = move(event.getServer().getWorldPath(LevelResource.ROOT));
    }

    /** Copies serverconfig/nookworks-server.toml to syncedconfig/ when only the old one is there; true if it did. Public for tests. */
    public static boolean move(Path world) {
        Path old = world.resolve("serverconfig").resolve(FILE);
        Path now = world.resolve("syncedconfig").resolve(FILE);
        if (!Files.isRegularFile(old) || Files.exists(now)) {
            return false;
        }
        try {
            Files.createDirectories(now.getParent());
            Files.copy(old, now);
            // set aside, so a world that later deletes the new copy to fall back on the global file gets no second one
            Files.move(old, old.resolveSibling(FILE + ".moved-to-syncedconfig"));
            LogUtils.getLogger().warn("Nookworks: this world's own settings ({}) were copied to syncedconfig/, where NeoForge 26.3 "
                    + "reads them; they take effect from the next start", old);
            return true;
        } catch (IOException e) {
            LogUtils.getLogger().error("Nookworks: could not copy this world's settings to syncedconfig/; copy {} there by hand", old, e);
            return false;
        }
    }

    @SubscribeEvent
    static void onLogIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (moved) {
            event.getEntity().sendSystemMessage(Component.translatable("message.nookworks.world_config_moved").withStyle(ChatFormatting.YELLOW));
        }
    }

    private WorldConfigMove() {}
}
