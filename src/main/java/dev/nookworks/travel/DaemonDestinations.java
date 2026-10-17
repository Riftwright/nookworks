package dev.nookworks.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import dev.nookworks.ifworld.IfWorld;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * The dimensions a Dock-a-Daemon Door can open onto, as the panel lists them, names them and colours them. A dimension
 * is listed for a player once they have been there ({@link TravelLog}) and while the server has it.
 */
public final class DaemonDestinations {
    /** No ground along the foot of the view: an unknown dimension's, or one that floats. */
    public static final int NONE = -1;
    /**
     * How each dimension looks through the door (drawn by DaemonView): vanilla's, the If's, and two mods' well-known
     * ones; any other is given its own fog and sky ({@link #look}).
     */
    private static final Map<ResourceKey<Level>, Look> LOOKS = Map.of(
            Level.OVERWORLD, new Look(0x5D8FE8, 0xC9E1FF, Kind.SKY, 0x6FAE4E, 0x3F7A34, 1),
            IfWorld.KEY, new Look(0x7466C8, 0xFFC79A, Kind.SKY, 0x8FAE6A, 0x4E6E4A, 2), // the same hills, at dawn
            Level.NETHER, new Look(0x3A0906, 0xD2501C, Kind.EMBERS, 0x6E2420, 0x40100E, 3),
            Level.END, new Look(0x07050E, 0x2C2150, Kind.STARS, 0xE6E2A6, 0xBDB77E, 4),
            dimension("twilightforest", "twilight_forest"), new Look(0x0B1426, 0x1E4A48, Kind.FIREFLIES, 0x1E3A2A, 0x0E2016, 5),
            dimension("aether", "the_aether"), new Look(0x8CC8F5, 0xFFFFFF, Kind.SKY, NONE, NONE, 6));

    /** Path ends that name no place ({@code limbo:dimension}): such a dimension goes by its namespace instead. */
    private static final Set<String> NAMELESS = Set.of("dimension", "dim", "world", "level", "main", "default");

    /** The looks worked out for other mods' dimensions, for the registries they were worked out from. */
    private static final Map<ResourceKey<Level>, Look> FOUND = new ConcurrentHashMap<>();
    private static @Nullable RegistryAccess foundIn;

    /** What the view holds: drifting clouds, a churning haze with embers rising, twinkling stars, or fireflies. */
    public enum Kind { SKY, EMBERS, STARS, FIREFLIES }

    /**
     * A dimension through the door: its sky from {@code top} to {@code bottom}, what moves in it, and the ground along
     * the foot ({@code groundLit} its top row, {@code ground} below; {@link #NONE} for none), all 0xRRGGBB; {@code seed}
     * shapes its clouds and its ground.
     */
    public record Look(int top, int bottom, Kind kind, int groundLit, int ground, int seed) {}

    private DaemonDestinations() {}

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    /**
     * Where {@code player} may choose to go from a door in {@code here}: the dimensions they have been to that the server
     * has, other than {@code here} - the overworld, the Nether, the End and the If first, then the rest by name.
     */
    public static List<ResourceKey<Level>> choices(ServerPlayer player, ResourceKey<Level> here) {
        MinecraftServer server = player.level().getServer();
        List<ResourceKey<Level>> out = new ArrayList<>();
        for (ResourceKey<Level> key : TravelLog.of(player).visited()) {
            if (!key.equals(here) && server.getLevel(key) != null) {
                out.add(key);
            }
        }
        out.sort(Comparator.comparingInt(DaemonDestinations::rank).thenComparing(k -> k.identifier().toString()));
        return out;
    }

    private static int rank(ResourceKey<Level> key) {
        if (key.equals(Level.OVERWORLD)) {
            return 0;
        }
        if (key.equals(Level.NETHER)) {
            return 1;
        }
        if (key.equals(Level.END)) {
            return 2;
        }
        return key.equals(IfWorld.KEY) ? 3 : 4;
    }

    /**
     * A dimension's name: {@code dimension.<namespace>.<path>} where a language file has it (ours give vanilla's, the
     * If's and a few mods'), else its id made readable ({@code twilight_forest} as "Twilight Forest"; {@code
     * limbo:dimension} as "Limbo", by its namespace, as "Dimension" names no place).
     */
    public static Component name(ResourceKey<Level> key) {
        String path = key.identifier().getPath();
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder readable = new StringBuilder();
        for (String word : (NAMELESS.contains(last) ? key.identifier().getNamespace() : last).split("_")) {
            if (!word.isEmpty()) {
                readable.append(readable.isEmpty() ? "" : " ").append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return Component.translatableWithFallback("dimension." + key.identifier().getNamespace() + "." + path.replace('/', '.'),
                readable.toString());
    }

    /**
     * Whose gate a door from {@code here} to {@code there} shows: the other world's - the Nether's swirl, the End's
     * stars, the If's mirror, another mod's own portal - and on the way back to the overworld, the gate of the world
     * being left, as vanilla's portals are the same gate both ways.
     */
    public static ResourceKey<Level> gate(ResourceKey<Level> here, ResourceKey<Level> there) {
        return there.equals(Level.OVERWORLD) ? here : there;
    }

    /**
     * How {@code key} looks in the panel's window onto it (and in its gate's swirl, when it has no portal of its own).
     * Another mod's dimension takes its colours from what the client was sent of it ({@code registries}): the fog and
     * sky of its dimension type and of its mod's biomes, and their water ({@link #lookOf}); one with none of those a hue
     * from its name.
     */
    public static Look look(ResourceKey<Level> key, RegistryAccess registries) {
        Look known = LOOKS.get(key);
        if (known != null) {
            return known;
        }
        if (registries != foundIn) {  // another world, whose mods may give the same name another place
            FOUND.clear();
            foundIn = registries;
        }
        return FOUND.computeIfAbsent(key, k -> find(k, registries));
    }

    private static Look find(ResourceKey<Level> key, RegistryAccess registries) {
        int seed = key.identifier().toString().hashCode();
        Sent sent = colours(key, registries);
        if (sent.air().isEmpty() && sent.water().isEmpty()) {
            // a hue of its own from its name, a dusk sky over a lighter haze
            float hue = (seed & 0xFFFF) / 65536.0F;
            return new Look(Mth.hsvToRgb(hue, 0.55F, 0.35F), Mth.hsvToRgb((hue + 0.04F) % 1.0F, 0.35F, 0.85F), Kind.SKY, NONE, NONE, seed);
        }
        return lookOf(sent, seed);
    }

    /** What a dimension was sent with, 0xRRGGBB: the colours of its air (fog and sky) and of its water. */
    public record Sent(List<Integer> air, List<Integer> water) {}

    /**
     * A place's window and swirl from what it was sent: the hue of its most colourful air light enough for a hue to show
     * - or of its water, where the air is grey or black (Deeper Dark's black fog and near-black red skies over a deep
     * blue sea: its skies' red, picked first, made it rust) - from a shade as dark as its darkest air to a light
     * as light as its lightest, never so dark the swirl is a hole nor so pale it is white. (Its air's darkest and lightest
     * as they were made Deeper Dark's swirl grey and Limbo's barely purple, r7, seen in the game.) Public for tests.
     */
    public static Look lookOf(Sent sent, int seed) {
        List<Integer> air = sent.air().isEmpty() ? sent.water() : sent.air();
        int hued = mostColourful(air.stream().filter(DaemonDestinations::showsAHue).toList());
        if (chroma(hued) < 12 && chroma(mostColourful(sent.water())) > chroma(hued)) {
            hued = mostColourful(sent.water());
        }
        float[] hsv = hsv(hued);
        float saturation = chroma(hued) < 6 ? 0.0F : Mth.clamp(hsv[1], 0.35F, 0.8F); // a grey place stays grey
        float deep = Mth.clamp(air.stream().mapToInt(DaemonDestinations::luma).min().orElse(40) / 255.0F, 0.14F, 0.35F);
        float lightest = air.stream().mapToInt(DaemonDestinations::luma).max().orElse(160) / 255.0F;
        float light = Mth.clamp(Math.max(lightest, deep + 0.35F), 0.55F, 0.9F);
        return new Look(Mth.hsvToRgb(hsv[0], Math.min(1.0F, saturation * 1.1F), deep),
                Mth.hsvToRgb((hsv[0] + 0.02F) % 1.0F, saturation * 0.8F, light), Kind.SKY, NONE, NONE, seed);
    }

    /** Whether a colour is light enough for its hue to be seen: a near-black red is black to the eye. */
    private static boolean showsAHue(int colour) {
        return Math.max(ARGB.red(colour), Math.max(ARGB.green(colour), ARGB.blue(colour))) >= 64;
    }

    private static int mostColourful(List<Integer> colours) {
        return colours.stream().max(Comparator.comparingInt(DaemonDestinations::chroma)).orElse(0x808080);
    }

    /** How far a colour is from grey: its strongest channel less its weakest. */
    private static int chroma(int colour) {
        int r = ARGB.red(colour);
        int g = ARGB.green(colour);
        int b = ARGB.blue(colour);
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b));
    }

    /** Hue, saturation and value, each 0..1. */
    private static float[] hsv(int colour) {
        float r = ARGB.red(colour) / 255.0F;
        float g = ARGB.green(colour) / 255.0F;
        float b = ARGB.blue(colour) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        float range = max - Math.min(r, Math.min(g, b));
        float hue = range == 0 ? 0 : max == r ? (g - b) / range : max == g ? (b - r) / range + 2 : (r - g) / range + 4;
        hue /= 6.0F;
        return new float[] {hue < 0 ? hue + 1.0F : hue, max == 0 ? 0 : range / max, max};
    }

    /**
     * The colours another mod's dimension was sent with: the fog and sky of its dimension type (the one of the same id,
     * else the only one its namespace has) and the average fog and sky of its namespace's biomes; and their water, the
     * type's water fog and the biomes' average water.
     */
    public static Sent colours(ResourceKey<Level> key, RegistryAccess registries) {
        Identifier id = key.identifier();
        List<Integer> air = new ArrayList<>();
        List<Integer> water = new ArrayList<>();
        var types = registries.lookupOrThrow(Registries.DIMENSION_TYPE);
        DimensionType type = types.getValue(id);
        if (type == null) {
            List<DimensionType> ofMod = types.entrySet().stream().filter(e -> e.getKey().identifier().getNamespace().equals(id.getNamespace()))
                    .map(Map.Entry::getValue).toList();
            type = ofMod.size() == 1 ? ofMod.getFirst() : null;
        }
        if (type != null) {
            colour(type.attributes(), EnvironmentAttributes.FOG_COLOR, air);
            colour(type.attributes(), EnvironmentAttributes.SKY_COLOR, air);
            colour(type.attributes(), EnvironmentAttributes.WATER_FOG_COLOR, water);
        }
        List<Integer> fogs = new ArrayList<>();
        List<Integer> skies = new ArrayList<>();
        List<Integer> waters = new ArrayList<>();
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            for (var entry : registries.lookupOrThrow(Registries.BIOME).entrySet()) {
                if (entry.getKey().identifier().getNamespace().equals(id.getNamespace())) {
                    Biome biome = entry.getValue();
                    colour(biome.getAttributes(), EnvironmentAttributes.FOG_COLOR, fogs);
                    colour(biome.getAttributes(), EnvironmentAttributes.SKY_COLOR, skies);
                    waters.add(biome.getWaterColor() & 0xFFFFFF);
                }
            }
        }
        mean(fogs, air);
        mean(skies, air);
        mean(waters, water);
        return new Sent(air, water);
    }

    private static void colour(EnvironmentAttributeMap attributes, EnvironmentAttribute<Vector3fc> attribute, List<Integer> into) {
        if (attributes.contains(attribute)) {
            into.add(ARGB.colorFromVector3f(attributes.applyModifier(attribute, ARGB.vector3fFromRGB24(0))) & 0xFFFFFF);
        }
    }

    private static void mean(List<Integer> colours, List<Integer> into) {
        if (!colours.isEmpty()) {
            int r = 0;
            int g = 0;
            int b = 0;
            for (int colour : colours) {
                r += ARGB.red(colour);
                g += ARGB.green(colour);
                b += ARGB.blue(colour);
            }
            into.add(ARGB.color(r / colours.size(), g / colours.size(), b / colours.size()) & 0xFFFFFF);
        }
    }

    private static int luma(int colour) {
        return (ARGB.red(colour) * 299 + ARGB.green(colour) * 587 + ARGB.blue(colour) * 114) / 1000;
    }
}
