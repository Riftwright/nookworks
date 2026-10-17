package dev.nookworks.photo;

import java.util.List;
import java.util.Optional;
import java.util.function.IntFunction;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.poster.PosterVariant;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A photo printed as a poster goes by a poster id of its own, {@code nookworks:photo/<number>/<width>x<height>}, in the
 * same component, entity and item as the designs from the data pack: placing, dropping, picking and stacking work as for
 * any poster. Its {@link PosterVariant} is made up from the id rather than looked up, and its picture is the photo's,
 * cropped to that size (the client draws it from the pixels the server sends, under the id's texture name).
 */
public final class PhotoPrints {
    private static final String PREFIX = "photo/";

    /** Photo {@code photo}, printed at {@code size}. */
    public record Print(int photo, Photo.Size size) {}

    /**
     * The tooltip's line for photo {@code number}, as far as this game knows: who took it, or why it shows nothing (gone,
     * not to be seen yet, photos off); null while it does not know. Set by the client, which hears it with the pixels.
     */
    public static IntFunction<@Nullable Component> describe = number -> null;

    private PhotoPrints() {}

    public static Identifier id(int photo, Photo.Size size) {
        return Nookworks.id(PREFIX + photo + "/" + size);
    }

    /** The print a poster id names, or null for a design (or anything else). */
    public static @Nullable Print parse(@Nullable Identifier id) {
        if (id == null || !id.getNamespace().equals(Nookworks.MODID) || !id.getPath().startsWith(PREFIX)) {
            return null;
        }
        String[] parts = id.getPath().substring(PREFIX.length()).split("/");
        if (parts.length != 2) {
            return null;
        }
        String[] size = parts[1].split("x");
        try {
            int photo = Integer.parseInt(parts[0]);
            Photo.Size kind = size.length == 2 ? Photo.size(Integer.parseInt(size[0]), Integer.parseInt(size[1])) : null;
            return photo > 0 && kind != null ? new Print(photo, kind) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** What a poster of the print is: its size, and as its picture the texture the client makes of the photo. */
    public static PosterVariant variant(Print print) {
        return new PosterVariant(id(print.photo(), print.size()), print.size().width(), print.size().height(), Optional.empty(), List.of());
    }
}
