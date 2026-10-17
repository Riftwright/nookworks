package dev.nookworks.feature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksStartupConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Each world's own choice of the parts of the mod ({@link Features}): made on the Create World screen (a Nookworks
 * button on its More tab) and kept in the world's folder, in {@link #FILE}. A part is on in a world when the start-up
 * config leaves it on - the modpack's word, over every world - and the world did not leave it out. A world without the
 * file (made before there was a choice, or by a dedicated server, which has no such screen) follows the start-up config
 * alone, and so do all of them when the pack switches the choice off ({@code worldMenu}).
 * <p>
 * The choice has to be known before the world's data packs load: the recipe and advancement conditions are tested
 * then, and only then. It is read from the world's folder as the world's list of packs is made
 * ({@code ServerPacksSourceMixin}), which every way of opening a world - the client's, a dedicated server's, a
 * re-created world's - does just before it loads them; {@code /reload} loads them again with what was read. The Create
 * World screen loads its data packs before anything is chosen: a change made on the Nookworks screen loads them again,
 * as a change of data packs does, and is written into the new world's folder before the world opens
 * ({@code CreateWorldScreenMixin}). It is read once, as the world opens: the file is changed with the world closed.
 * <p>
 * A client playing on another's server goes by the server's word instead, sent on joining ({@link #heard}): its creative
 * tabs and JEI show what the server has, whatever the client's own settings say.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WorldFeatures {
    /** Where in a world's folder its choice is kept, beside NeoForge's per-world config files. */
    public static final String FILE = "serverconfig/nookworks-features.json";

    /**
     * What a world chose, part by part (false: left out), and its folder - null while it is still on the Create World
     * screen. A part missing from the map was not left out.
     */
    public record Choice(@Nullable Path world, Map<String, Boolean> parts) {
        public Choice {
            parts = Collections.unmodifiableMap(new LinkedHashMap<>(parts));
        }

        boolean leftOut(String part) {
            return !this.parts.getOrDefault(part, true);
        }
    }

    /** The choice of the world whose data loads or is played here, or of the one being made; null for none. */
    private static volatile @Nullable Choice current;
    /** On a client playing on another's server: which parts that server has on. Null otherwise. */
    private static volatile @Nullable Map<String, Boolean> server;

    private WorldFeatures() {}

    /** Whether the pack lets each new world choose its parts: the Nookworks button on the Create World screen, and the files read. */
    public static boolean menuShown() {
        return NookworksStartupConfig.WORLD_MENU.getAsBoolean();
    }

    /** Whether a world may have part {@code part}: the start-up config leaves it on. One it switches off is off in every world. */
    public static boolean offered(String part) {
        var setting = NookworksStartupConfig.feature(part);
        return setting == null || setting.getAsBoolean();
    }

    /** Whether part {@code name} is switched on here, before what it needs ({@link Features#enabled(String)}). */
    static boolean switchedOn(String name) {
        Map<String, Boolean> said = server;
        if (said != null) {
            return said.getOrDefault(name, true);
        }
        if (!offered(name)) {
            return false;
        }
        Choice chosen = current;
        return chosen == null || !menuShown() || !chosen.leftOut(name);
    }

    /** Which parts are on here, by name: what a server tells its players. */
    public static Map<String, Boolean> effective() {
        Map<String, Boolean> on = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> on.put(part, switchedOn(part)));
        return on;
    }

    /** The choice in force (for tests, which put it back as it was), or null for none. */
    public static @Nullable Choice current() {
        return current;
    }

    /** Puts {@code choice} in force: for tests, and for the Create World screen ({@link #choose}). */
    public static void restore(@Nullable Choice choice) {
        current = choice;
    }

    /**
     * What the Create World screen starts from, part by part: the world it re-creates chose, or nothing was left out
     * yet. A part the start-up config switches off is shown off whatever this says.
     */
    public static Map<String, Boolean> choosing() {
        Choice chosen = current;
        Map<String, Boolean> parts = new LinkedHashMap<>();
        Features.PARTS.keySet().forEach(part -> parts.put(part, chosen == null || !chosen.leftOut(part)));
        return parts;
    }

    /** The Create World screen is opening: its data packs load with nothing left out, whatever the last world chose. */
    public static void creatingWorld() {
        current = null;
    }

    /** What the Nookworks screen chose, for the world still being made: its data packs are loaded again with it. */
    public static void choose(Map<String, Boolean> parts) {
        current = new Choice(null, parts);
    }

    /** A world's data packs are about to load: what it chose, from its folder {@code world}, is in force from now on. */
    public static void opening(Path world) {
        current = menuShown() ? read(world) : null;
    }

    /**
     * The world being made from the Create World screen has its folder, {@code world}: what was chosen is written into
     * it before the world opens (and reads it back). Nothing left out is written too, so each world made with the
     * choice on has its file to change later.
     */
    public static void made(Path world) {
        if (!menuShown()) {
            return;
        }
        Choice chosen = current;
        try {
            write(world, chosen != null ? chosen.parts() : Map.of());
        } catch (IOException e) {
            // the world opens all the same, as chosen; only a later opening would not know it
            Nookworks.LOGGER.error("Could not write the world's choice of Nookworks parts to {}", world.resolve(FILE), e);
        }
    }

    /** A world's choice from its folder, or null for none (no file, or one that cannot be read, which is logged). */
    public static @Nullable Choice read(Path world) {
        Path file = world.resolve(FILE);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (NoSuchFileException e) {
            return null;
        } catch (IOException e) {
            Nookworks.LOGGER.warn("Cannot read {}: the world follows the start-up config", file, e);
            return null;
        }
        try {
            JsonObject json = JsonParser.parseString(text).getAsJsonObject();
            Map<String, Boolean> parts = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                if (entry.getKey().startsWith("_")) {
                    continue; // the note for whoever opens the file
                }
                if (!Features.PARTS.containsKey(entry.getKey())) {
                    Nookworks.LOGGER.warn("{} names no part of the mod: {}", file, entry.getKey());
                } else if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    parts.put(entry.getKey(), entry.getValue().getAsBoolean());
                } else {
                    Nookworks.LOGGER.warn("{}: {} is neither true nor false, and is left on", file, entry.getKey());
                }
            }
            return new Choice(world.toAbsolutePath().normalize(), parts);
        } catch (RuntimeException e) {
            Nookworks.LOGGER.warn("Cannot read {}: the world follows the start-up config", file, e);
            return null;
        }
    }

    /** Writes a world's choice into its folder, every part named (a part missing from {@code parts} was not left out). */
    public static void write(Path world, Map<String, Boolean> parts) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("_comment", "The parts of Nookworks this world has (false: left out), chosen when it was made. "
                + "Read as the world opens: change it with the world closed, or the server stopped. A part switched off in "
                + "config/nookworks-startup.toml ([features]) is off in every world, whatever this says.");
        Features.PARTS.keySet().forEach(part -> json.addProperty(part, parts.getOrDefault(part, true)));
        Path file = world.resolve(FILE);
        Files.createDirectories(file.getParent());
        Path written = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(written, new GsonBuilder().setPrettyPrinting().create().toJson(json) + "\n", StandardCharsets.UTF_8);
        Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /**
     * On a client: the server's word on which parts it has, {@code parts}. A client hosting the world itself (single
     * player, or opened to LAN) already goes by the same choice and keeps to it.
     */
    public static void heard(Map<String, Boolean> parts, boolean ownServer) {
        server = ownServer ? null : Map.copyOf(parts);
    }

    /** On a client: left the server; its word goes with it. */
    public static void leftServer() {
        server = null;
    }

    /**
     * A world closed: its choice goes with it. Only its own - not one already read for the next world, nor one being
     * made on the Create World screen.
     */
    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        closed(event.getServer());
    }

    /** {@code server}'s world has closed. Public for tests. */
    public static void closed(MinecraftServer server) {
        Choice chosen = current;
        if (chosen != null && sameWorld(chosen.world(), server)) {
            current = null;
        }
    }

    private static boolean sameWorld(@Nullable Path world, MinecraftServer server) {
        return world != null && world.equals(server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize());
    }
}
