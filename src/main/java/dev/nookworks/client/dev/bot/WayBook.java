package dev.nookworks.client.dev.bot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.dev.bot.Ways;
import net.minecraft.client.Minecraft;

/**
 * The stairs and tunnels the walker dug ({@link Ways}), kept in {@code bot_ways/<world>.txt} beside the other bot files:
 * a client start, or a death, does not lose them. One file a world and dimension, so a test copy of a world has its own.
 */
final class WayBook {
    private WayBook() {}

    private static @Nullable String loadedFor;
    private static Ways ways = new Ways();

    /** This world's ways (read from its file the first time). */
    static Ways ways(Minecraft mc) {
        String key = key(mc);
        if (!key.equals(loadedFor)) {
            loadedFor = key;
            ways = new Ways();
            Path file = file(key);
            if (Files.exists(file)) {
                try {
                    ways = Ways.read(Files.readAllLines(file, StandardCharsets.UTF_8));
                } catch (IOException | RuntimeException e) {
                    Nookworks.LOGGER.warn("[bot] cannot read {}: {}", file, e.toString());
                }
            }
        }
        return ways;
    }

    static void save(Minecraft mc) {
        Path file = file(key(mc));
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, ways(mc).lines(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Nookworks.LOGGER.warn("[bot] cannot write {}: {}", file, e.toString());
        }
    }

    private static Path file(String key) {
        return Bot.dir().resolve("bot_ways").resolve(key + ".txt");
    }

    /** The server's address (or the singleplayer world's name) and the dimension, as a file name. */
    private static String key(Minecraft mc) {
        String world;
        if (mc.getSingleplayerServer() != null) {
            world = mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            world = mc.getCurrentServer().ip;
        } else {
            world = "unknown";
        }
        String dim = mc.level == null ? "" : "_" + mc.level.dimension().identifier().getPath();
        return (world + dim).replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}
