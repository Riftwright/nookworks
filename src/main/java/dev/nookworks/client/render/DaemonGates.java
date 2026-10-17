package dev.nookworks.client.render;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.travel.DaemonDestinations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;

/**
 * The gate a Dock-a-Daemon Door shows, by whose gate it is ({@link DaemonDestinations#gate}): the Nether's portal swirl,
 * the End portal's stars, the If's mirror - silvered glass, a sheen crossing it now and then - and for another mod's
 * dimension that mod's own portal, its block's picture (the block of the dimension's mod named for a portal, as
 * {@code undergarden:undergarden_portal} is); for one with no such block, a swirl in the place's own colours.
 */
public final class DaemonGates {
    /** How a gate is drawn: a block's picture, the End portal's starfield, or pixel by pixel (the mirror, a swirl). */
    public enum Kind { SPRITE, END, MIRROR, SWIRL }

    /** The gate of {@code dimension}: drawn as {@code kind}, from {@code block}'s picture when it is a block's. */
    public record Gate(Kind kind, ResourceKey<Level> dimension, @Nullable Block block) {}

    // silvered glass, as the If Mirror's (tools/textures/gen_if_mirror.py), deep to bright
    private static final int SILVER_DEEP = 0x8C98A4;
    private static final int SILVER = 0xA8B6C0;
    private static final int SILVER_LIT = 0xC4D0D8;
    private static final int SILVER_BRIGHT = 0xE2EAF0;

    private static final Map<ResourceKey<Level>, Gate> GATES = new ConcurrentHashMap<>();

    private DaemonGates() {}

    public static Gate of(ResourceKey<Level> dimension) {
        return GATES.computeIfAbsent(dimension, DaemonGates::find);
    }

    private static Gate find(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.END)) {
            return new Gate(Kind.END, dimension, null);
        }
        if (dimension.equals(IfWorld.KEY)) {
            return new Gate(Kind.MIRROR, dimension, null);
        }
        if (dimension.equals(Level.NETHER) || dimension.equals(Level.OVERWORLD)) {
            return new Gate(Kind.SPRITE, dimension, Blocks.NETHER_PORTAL);
        }
        Block portal = portalOf(dimension);
        return portal == null ? new Gate(Kind.SWIRL, dimension, null) : new Gate(Kind.SPRITE, dimension, portal);
    }

    /**
     * Another mod's portal for its dimension: a block of the same mod whose name says portal and not a portal's frame,
     * best one that ends in "portal" and shares a word with the dimension's name, shortest first. Null with none.
     */
    static @Nullable Block portalOf(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            return null;
        }
        String word = id.getPath().substring(id.getPath().lastIndexOf('/') + 1).split("_")[0];
        Block best = null;
        int bestScore = Integer.MIN_VALUE;
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            Identifier block = entry.getKey().identifier();
            String path = block.getPath();
            if (!block.getNamespace().equals(id.getNamespace()) || !path.contains("portal") || path.contains("frame")) {
                continue;
            }
            int score = (path.endsWith("portal") ? 1000 : 0) + (path.contains(word) ? 100 : 0) - path.length();
            if (score > bestScore) {
                best = entry.getValue();
                bestScore = score;
            }
        }
        return best;
    }

    /**
     * The picture a block-drawn gate shows: its block's own (the one it breaks into), animated as it is in the world.
     * Sodium animates only the pictures it saw drawn this frame, and a door's gate is drawn past it: it is told this one
     * is, or the swirl stood still on one frame wherever no real portal was in view (r7 review).
     */
    public static TextureAtlasSprite sprite(Gate gate) {
        Block block = gate.block() == null ? Blocks.NETHER_PORTAL : gate.block();
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager().getBlockStateModelSet().getParticleMaterial(block.defaultBlockState()).sprite();
        if (SODIUM_ACTIVE != null) {
            try {
                SODIUM_ACTIVE.invoke(sprite);
            } catch (Throwable ignored) {
                // Sodium's API answered when looked up; a call it throws from leaves the picture as Sodium draws it
            }
        }
        return sprite;
    }

    /** Sodium's {@code SpriteUtil.INSTANCE.markSpriteActive}, bound, where Sodium is installed; null without it. */
    private static final @Nullable MethodHandle SODIUM_ACTIVE = sodiumActive();

    private static @Nullable MethodHandle sodiumActive() {
        if (!ModList.get().isLoaded("sodium")) {
            return null;
        }
        try {
            Class<?> api = Class.forName("net.caffeinemc.mods.sodium.api.texture.SpriteUtil");
            return MethodHandles.publicLookup().findVirtual(api, "markSpriteActive", MethodType.methodType(void.class, TextureAtlasSprite.class))
                    .bindTo(api.getField("INSTANCE").get(null));
        } catch (ReflectiveOperationException | LinkageError e) {
            Nookworks.LOGGER.warn("Sodium's sprite API was not found; an open Dock-a-Daemon Door's gate may stand still", e);
            return null;
        }
    }

    /**
     * A pixel-drawn gate's colour (0xAARRGGBB, opaque) at column {@code x} (0-15), row {@code y} (0-31, from the top of the
     * doorway) at {@code time} ticks.
     */
    public static int pixel(Gate gate, int x, int y, float time) {
        return 0xFF000000 | (gate.kind() == Kind.MIRROR ? mirror(x, y, time) : swirl(DaemonDestinations.look(gate.dimension(), Minecraft.getInstance().getConnection().registryAccess()), x, y, time));
    }

    /**
     * Silvered glass, lighter towards the top, with the If Mirror's glints running down from the upper right at a slope of
     * one - a broad pair and a thin one high up, a fainter pair lower - and every few seconds a sheen sliding down across it.
     */
    private static int mirror(int x, int y, float time) {
        int diagonal = x + y;
        int base = y < 12 ? SILVER_LIT : SILVER;
        int colour = diagonal == 18 || diagonal == 19 || diagonal == 21 ? SILVER_BRIGHT
                : y >= 12 && (diagonal == 33 || diagonal == 35) ? SILVER_LIT : base;
        float sheen = diagonal - Mth.positiveModulo(time * 0.25F, 90.0F) + 6;
        if (sheen >= 0 && sheen < 3) {
            colour = ARGB.srgbLerp(sheen < 1 || sheen >= 2 ? 0.35F : 0.6F, colour, 0xFFFFFF) & 0xFFFFFF;
        }
        return y == 31 ? SILVER_DEEP : colour;
    }

    /**
     * A gate of no known kind: a swirl in its place's colours, streaming upwards as a nether portal's does - its sky's
     * top colour deepest, its horizon's lightest, a glow on the brightest streaks.
     */
    private static int swirl(DaemonDestinations.Look look, int x, int y, float time) {
        float bend = 0.9F * Mth.sin(time * 0.035F + y * 0.22F);
        float c = DaemonView.fbm(x * 0.3F + bend, y * 0.07F - time * 0.02F, look.seed());
        if (c > 0.66F) {
            return ARGB.srgbLerp(0.45F, look.bottom(), 0xFFFFFF) & 0xFFFFFF;
        }
        return c > 0.52F ? look.bottom() : c > 0.4F ? ARGB.srgbLerp(0.5F, look.top(), look.bottom()) & 0xFFFFFF : look.top();
    }

    /** {@code gate} filling a screen {@code width} by {@code height}, as vanilla fills the screen while one goes through a portal. */
    public static void fill(GuiGraphicsExtractor g, Gate gate, int width, int height, float time) {
        switch (gate.kind()) {
            case SPRITE -> g.blitSprite(RenderPipelines.GUI_OPAQUE_TEXTURED_BACKGROUND, sprite(gate), 0, 0, width, height);
            case END -> {
                var textures = Minecraft.getInstance().getTextureManager();
                AbstractTexture sky = textures.getTexture(AbstractEndPortalRenderer.END_SKY_LOCATION);
                AbstractTexture portal = textures.getTexture(AbstractEndPortalRenderer.END_PORTAL_LOCATION);
                g.fill(RenderPipelines.END_PORTAL, TextureSetup.doubleTexture(sky.getTextureView(), sky.getSampler(), portal.getTextureView(),
                        portal.getSampler()), 0, 0, width, height);
            }
            case MIRROR, SWIRL -> {
                // the door's own pixels, grown to the screen: 32 to its height
                int cell = Math.max(1, Mth.ceil(height / 32.0F));
                for (int cy = 0; cy * cell < height; cy++) {
                    for (int cx = 0; cx * cell < width; cx++) {
                        g.fill(cx * cell, cy * cell, cx * cell + cell, cy * cell + cell, pixel(gate, cx % 16, Math.min(cy, 31), time + cx / 16 * 7));
                    }
                }
            }
        }
    }
}
