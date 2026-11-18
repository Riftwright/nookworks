package dev.nookworks.photo;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;

/**
 * A photograph taken with the Camera (the user, 2026-10-11: カメラを持ってスクショ, then the Printing Press prints it as a
 * poster). {@link #WIDTH} x {@link #HEIGHT} pixels of the view - the largest print, 4 x 3 blocks at the 16 pixels a block
 * the posters have, so no print is ever enlarged - as red, green and blue bytes, row by row from the top left; with who
 * took it, the day it was taken, and whether it was ever printed (an unprinted one pushed out of the camera is let go).
 */
public record Photo(byte[] pixels, String author, UUID authorId, long day, boolean printed) {
    public static final int WIDTH = 64;
    public static final int HEIGHT = 48;
    public static final int BYTES = WIDTH * HEIGHT * 3;
    public static final int PIXELS_PER_BLOCK = 16;

    /** A print's size in blocks. */
    public record Size(int width, int height) {
        @Override
        public String toString() {
            return this.width + "x" + this.height;
        }
    }

    /**
     * What a photo prints at, in the order the press offers them: the whole photo first, then the rest of the landscape
     * ones, the squares and the tall ones. None asks for more pixels than the photo has.
     */
    public static final List<Size> SIZES = List.of(new Size(4, 3), new Size(3, 2), new Size(2, 1), new Size(1, 1), new Size(2, 2),
            new Size(3, 3), new Size(1, 2), new Size(2, 3));

    public static @Nullable Size size(int width, int height) {
        return SIZES.stream().filter(size -> size.width() == width && size.height() == height).findFirst().orElse(null);
    }

    private static final Codec<byte[]> BYTES_CODEC = Codec.BYTE_BUFFER.xmap(buffer -> {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }, ByteBuffer::wrap);

    public static final Codec<Photo> CODEC = RecordCodecBuilder.create(i -> i.group(
            BYTES_CODEC.validate(bytes -> bytes.length == BYTES ? DataResult.success(bytes)
                    : DataResult.error(() -> "a photo is " + BYTES + " bytes, not " + bytes.length))
                    .fieldOf("pixels").forGetter(Photo::pixels),
            Codec.STRING.fieldOf("author").forGetter(Photo::author),
            UUIDUtil.CODEC.fieldOf("author_id").forGetter(Photo::authorId),
            Codec.LONG.fieldOf("day").forGetter(Photo::day),
            Codec.BOOL.optionalFieldOf("printed", false).forGetter(Photo::printed))
            .apply(i, Photo::new));

    public Photo asPrinted() {
        return this.printed ? this : new Photo(this.pixels, this.author, this.authorId, this.day, true);
    }

    /**
     * The photo as it is printed and shown (the user, 2026-10-14: 撮れた写真が微妙に眠い…コントラストが低くてぼんやり): what
     * the camera read, finished as a print is. Each of the photo's pixels is the average of a dozen or more of the
     * screen's, which leaves every edge between two and the picture soft. So: the lightest pixels (the brightest channel
     * of the top half percent) up to white, but a dark picture only a fifth lighter, so a night or a cave stays dark; the
     * darkest down by half, ten levels at most, so a snowfield keeps its light shadows; a gentle S-curve; a touch more
     * colour; and a mild sharpening of each pixel against the four beside it. Made from the photo's bytes alone, so every
     * game shows the same, and photos taken before are finished as well; the photo kept is what the camera read.
     */
    public static byte[] finish(byte[] pixels) {
        int count = WIDTH * HEIGHT;
        int[] brightest = new int[256];
        int[] darkest = new int[256];
        for (int i = 0; i < count; i++) {
            int r = pixels[i * 3] & 0xFF;
            int g = pixels[i * 3 + 1] & 0xFF;
            int b = pixels[i * 3 + 2] & 0xFF;
            brightest[Math.max(r, Math.max(g, b))]++;
            darkest[Math.min(r, Math.min(g, b))]++;
        }
        double hi = level(brightest, count * 0.995);
        double lo = level(darkest, count * 0.005);
        double gain = 1;
        double hiTo = hi;
        double loTo = lo;
        if (hi - lo > 8) { // a picture all of one shade is left be
            hiTo = Math.min(255, hi * 1.2);
            loTo = lo - Math.min(lo / 2, 10);
            gain = (hiTo - loTo) / (hi - lo);
        }
        double[] tone = new double[count * 3];
        for (int i = 0; i < count; i++) {
            double[] rgb = new double[3];
            for (int c = 0; c < 3; c++) {
                double v = Math.clamp(((pixels[i * 3 + c] & 0xFF) - lo) * gain + loTo, 0, 255) / 255;
                rgb[c] = v + (v * v * (3 - 2 * v) - v) * 0.2;
            }
            double luma = 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
            for (int c = 0; c < 3; c++) {
                tone[i * 3 + c] = Math.clamp(luma + (rgb[c] - luma) * 1.06, 0, 1);
            }
        }
        byte[] out = new byte[BYTES];
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int at = (x + y * WIDTH) * 3;
                for (int c = 0; c < 3; c++) {
                    double v = tone[at + c];
                    double around = tone[(Math.max(x - 1, 0) + y * WIDTH) * 3 + c] + tone[(Math.min(x + 1, WIDTH - 1) + y * WIDTH) * 3 + c]
                            + tone[(x + Math.max(y - 1, 0) * WIDTH) * 3 + c] + tone[(x + Math.min(y + 1, HEIGHT - 1) * WIDTH) * 3 + c];
                    out[at + c] = (byte) Math.round(Math.clamp(v + (v - around / 4) * 0.3, 0, 1) * 255);
                }
            }
        }
        return out;
    }

    /** The least level with at least {@code reached} of the counts at or below it. */
    private static int level(int[] histogram, double reached) {
        int sum = 0;
        for (int level = 0; level < 256; level++) {
            sum += histogram[level];
            if (sum >= reached) {
                return level;
            }
        }
        return 255;
    }

    /**
     * The picture a print of {@code width} x {@code height} blocks shows, as ARGB at 16 pixels a block: the middle of the
     * photo, as wide or as tall as it goes for that shape, each pixel the average of the part of the photo it covers.
     */
    public static int[] print(byte[] pixels, int width, int height) {
        int outW = width * PIXELS_PER_BLOCK;
        int outH = height * PIXELS_PER_BLOCK;
        double cropW = Math.min(WIDTH, (double) HEIGHT * width / height);
        double cropH = cropW * height / width;
        double left = (WIDTH - cropW) / 2.0;
        double top = (HEIGHT - cropH) / 2.0;
        double stepX = cropW / outW;
        double stepY = cropH / outH;
        int[] out = new int[outW * outH];
        for (int y = 0; y < outH; y++) {
            double y0 = top + y * stepY;
            double y1 = y0 + stepY;
            for (int x = 0; x < outW; x++) {
                double x0 = left + x * stepX;
                double x1 = x0 + stepX;
                double r = 0;
                double g = 0;
                double b = 0;
                double area = 0;
                for (int sy = (int) Math.floor(y0); sy < Math.ceil(y1) && sy < HEIGHT; sy++) {
                    double h = Math.min(y1, sy + 1) - Math.max(y0, sy);
                    for (int sx = (int) Math.floor(x0); sx < Math.ceil(x1) && sx < WIDTH; sx++) {
                        double weight = h * (Math.min(x1, sx + 1) - Math.max(x0, sx));
                        if (weight <= 0) {
                            continue;
                        }
                        int at = (sx + sy * WIDTH) * 3;
                        r += (pixels[at] & 0xFF) * weight;
                        g += (pixels[at + 1] & 0xFF) * weight;
                        b += (pixels[at + 2] & 0xFF) * weight;
                        area += weight;
                    }
                }
                out[x + y * outW] = 0xFF000000 | (int) Math.round(r / area) << 16 | (int) Math.round(g / area) << 8 | (int) Math.round(b / area);
            }
        }
        return out;
    }
}
