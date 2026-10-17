package dev.nookworks.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.NativeImage;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.photo.Photos;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The photos this game has been sent, and their pictures: one texture for each size a photo is shown at, under the name
 * its poster's variant gives ({@link PhotoPrints#variant}), made when first wanted. A photo not yet here is asked for -
 * again after a while if no answer came - and meanwhile a poster shows {@link #blank} paper.
 * <p>
 * The server says why a photo is not shown, and that is kept apart (review r12): one gone stays gone; one hidden from
 * this player - not printed, in no camera of theirs - is asked for again every few seconds while it is in view, so it
 * shows once it is printed or its camera handed over; and with photos turned off on the server
 * ({@link NookworksNetwork.PhotosAllowed}) every picture is let go and none asked for until they are on again.
 * Everything is let go of on leaving the world.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class PhotoTextures {
    /**
     * How long to wait for an answer before asking again, in milliseconds: soon, for the server drops asks past a game's
     * budget unanswered (a wall of more photos than that comes in a few at a time).
     */
    private static final long ASK_AGAIN = 3_000;
    /** How often a photo hidden from this player is asked for again while it is in view, in milliseconds. */
    private static final long LOOK_AGAIN = 3_000;
    private static final int PAPER = 0xFFEDE9DD;
    private static final int PAPER_EDGE = 0xFFD9D4C6;
    private static final Identifier BLANK = Nookworks.id("textures/poster/photo/blank.png");
    /** Not yet answered. */
    private static final int UNKNOWN = -1;

    private static final class Entry {
        /** As the server sent them: what the camera read. */
        byte @Nullable [] pixels;
        /** The picture shown of them ({@link Photo#finish}). */
        byte @Nullable [] finished;
        String author = "";
        int status = UNKNOWN;
        long askedAt = Long.MIN_VALUE / 2;
        final Map<Photo.Size, Identifier> textures = new HashMap<>();
    }

    private static final Map<Integer, Entry> PHOTOS = new HashMap<>();
    private static boolean blankMade;
    /** Whether this server shows photos, as it last said. */
    private static boolean allowed = true;

    static {
        Photos.received = PhotoTextures::receive;
        Photos.allowedHere = PhotoTextures::allowedHere;
        PhotoPrints.describe = PhotoTextures::describe;
    }

    private PhotoTextures() {}

    /** Hooks the store up to what the server sends. */
    public static void init() {}

    /** Photo {@code photo}'s picture at {@code size}, or null while it is not here (asked for, if need be). */
    public static @Nullable Identifier texture(int photo, Photo.Size size) {
        Entry entry = entry(photo);
        if (entry.finished == null) {
            return null;
        }
        byte[] finished = entry.finished;
        return entry.textures.computeIfAbsent(size, kind -> upload(photo, finished, kind));
    }

    /** As {@link #texture}, with blank paper while there is no picture. */
    public static Identifier textureOrBlank(int photo, Photo.Size size) {
        Identifier texture = texture(photo, size);
        return texture != null ? texture : blank();
    }

    /** What the server last said of photo {@code photo} ({@link NookworksNetwork.PhotoPixels#SHOWN} and so on), or -1 for nothing yet. */
    public static int status(int photo) {
        return allowed ? entry(photo).status : NookworksNetwork.PhotoPixels.OFF;
    }

    /** The line a photo's tooltip has: who took it, or why it shows nothing; null while the server has not said. */
    public static @Nullable Component describe(int photo) {
        Entry entry = entry(photo);
        return switch (allowed ? entry.status : NookworksNetwork.PhotoPixels.OFF) {
            case NookworksNetwork.PhotoPixels.SHOWN -> Component.translatable("item.nookworks.photo.by", entry.author);
            case NookworksNetwork.PhotoPixels.GONE -> Component.translatable("item.nookworks.photo.gone");
            case NookworksNetwork.PhotoPixels.HIDDEN -> Component.translatable("item.nookworks.photo.hidden");
            case NookworksNetwork.PhotoPixels.OFF -> Component.translatable("item.nookworks.photo.off");
            default -> null;
        };
    }

    /** Paper, plain: what a poster of a photo shows until the photo is here, or when it never will be. */
    public static Identifier blank() {
        if (!blankMade) {
            NativeImage image = new NativeImage(16, 16, false);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    image.setPixel(x, y, x == 0 || y == 0 || x == 15 || y == 15 ? PAPER_EDGE : PAPER);
                }
            }
            Minecraft.getInstance().getTextureManager().register(BLANK, new DynamicTexture(() -> "Blank photo", image));
            blankMade = true;
        }
        return BLANK;
    }

    private static Entry entry(int photo) {
        Entry entry = PHOTOS.computeIfAbsent(photo, number -> new Entry());
        long now = Util.getMillis();
        boolean ask = entry.status == UNKNOWN ? now - entry.askedAt > ASK_AGAIN
                : entry.status == NookworksNetwork.PhotoPixels.HIDDEN && now - entry.askedAt > LOOK_AGAIN;
        if (allowed && ask && Minecraft.getInstance().getConnection() != null) {
            entry.askedAt = now;
            ClientPacketDistributor.sendToServer(new NookworksNetwork.AskPhoto(photo));
        }
        return entry;
    }

    private static void receive(NookworksNetwork.PhotoPixels payload) {
        Entry entry = PHOTOS.computeIfAbsent(payload.id(), number -> new Entry());
        boolean shown = payload.status() == NookworksNetwork.PhotoPixels.SHOWN && payload.pixels().length == Photo.BYTES;
        if (shown && entry.pixels != null && Arrays.equals(entry.pixels, payload.pixels())) {
            entry.status = payload.status();
            return; // the same again: the pictures made of it stand
        }
        release(entry);
        entry.status = shown ? NookworksNetwork.PhotoPixels.SHOWN : payload.status() == NookworksNetwork.PhotoPixels.SHOWN
                ? NookworksNetwork.PhotoPixels.GONE : payload.status();
        entry.pixels = shown ? payload.pixels() : null;
        entry.finished = shown ? Photo.finish(payload.pixels()) : null;
        entry.author = payload.author();
    }

    /** The server's word on photos: off, every picture goes; on again, every photo is asked for afresh. */
    private static void allowedHere(boolean on) {
        if (on == allowed) {
            return; // said again, as it is on every reload of the settings: nothing to undo
        }
        allowed = on;
        PHOTOS.values().forEach(PhotoTextures::release);
        PHOTOS.clear();
    }

    private static Identifier upload(int photo, byte[] pixels, Photo.Size size) {
        int width = size.width() * Photo.PIXELS_PER_BLOCK;
        int height = size.height() * Photo.PIXELS_PER_BLOCK;
        int[] argb = Photo.print(pixels, size.width(), size.height());
        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixel(x, y, argb[x + y * width]);
            }
        }
        Identifier id = PhotoPrints.variant(new PhotoPrints.Print(photo, size)).texture();
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Photo " + photo + " " + size, image));
        return id;
    }

    private static void release(Entry entry) {
        entry.textures.values().forEach(Minecraft.getInstance().getTextureManager()::release);
        entry.textures.clear();
    }

    @SubscribeEvent
    static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        PHOTOS.values().forEach(PhotoTextures::release);
        PHOTOS.clear();
        allowed = true;
    }
}
