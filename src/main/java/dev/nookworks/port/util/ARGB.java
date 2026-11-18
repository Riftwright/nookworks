package dev.nookworks.port.util;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import net.minecraft.util.Mth;

/**
 * 26.3's {@code net.minecraft.util.ARGB}: colours packed as 0xAARRGGBB ints. 1.21.1 has some of this in
 * {@code FastColor.ARGB32}; these are the methods the mod calls, each behaving as 26.3's does.
 */
public final class ARGB {
    private ARGB() {}

    public static int alpha(int color) {
        return color >>> 24;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    public static int color(int alpha, int red, int green, int blue) {
        return (alpha & 0xFF) << 24 | (red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF;
    }

    public static int color(int red, int green, int blue) {
        return color(255, red, green, blue);
    }

    /** An alpha over a 0xRRGGBB colour. */
    public static int color(int alpha, int rgb) {
        return alpha << 24 | rgb & 0xFFFFFF;
    }

    public static int color(float alpha, int rgb) {
        return as8BitChannel(alpha) << 24 | rgb & 0xFFFFFF;
    }

    /** Channel by channel; white (-1) on either side leaves the other as it is. */
    public static int multiply(int lhs, int rhs) {
        if (lhs == -1) {
            return rhs;
        }
        if (rhs == -1) {
            return lhs;
        }
        return color(alpha(lhs) * alpha(rhs) / 255, red(lhs) * red(rhs) / 255, green(lhs) * green(rhs) / 255,
                blue(lhs) * blue(rhs) / 255);
    }

    /** Each channel, alpha too, lerped as the stored (sRGB) numbers are. */
    public static int srgbLerp(float alpha, int p0, int p1) {
        return color(Mth.lerpInt(alpha, alpha(p0), alpha(p1)), Mth.lerpInt(alpha, red(p0), red(p1)),
                Mth.lerpInt(alpha, green(p0), green(p1)), Mth.lerpInt(alpha, blue(p0), blue(p1)));
    }

    public static int opaque(int color) {
        return color | 0xFF000000;
    }

    public static int transparent(int color) {
        return color & 0xFFFFFF;
    }

    public static int white(float alpha) {
        return as8BitChannel(alpha) << 24 | 0xFFFFFF;
    }

    public static int white(int alpha) {
        return alpha << 24 | 0xFFFFFF;
    }

    public static int black(float alpha) {
        return as8BitChannel(alpha) << 24;
    }

    public static int black(int alpha) {
        return alpha << 24;
    }

    public static int colorFromFloat(float alpha, float red, float green, float blue) {
        return color(as8BitChannel(alpha), as8BitChannel(red), as8BitChannel(green), as8BitChannel(blue));
    }

    public static int colorFromVector3f(Vector3fc color) {
        return color(255, as8BitChannel(color.x()), as8BitChannel(color.y()), as8BitChannel(color.z()));
    }

    /** A 0xRRGGBB colour as three 0-1 channels. */
    public static Vector3f vector3fFromRGB24(int color) {
        return new Vector3f(red(color) / 255.0F, green(color) / 255.0F, blue(color) / 255.0F);
    }

    /** Red and blue swapped: 0xAARRGGBB to 0xAABBGGRR, the order 1.21.1's NativeImage keeps a pixel in. */
    public static int toABGR(int color) {
        return color & 0xFF00FF00 | (color & 0xFF0000) >> 16 | (color & 0xFF) << 16;
    }

    public static int fromABGR(int color) {
        return toABGR(color);
    }

    public static int as8BitChannel(float value) {
        return Mth.floor(value * 255.0F);
    }

    public static float alphaFloat(int color) {
        return alpha(color) / 255.0F;
    }

    public static float redFloat(int color) {
        return red(color) / 255.0F;
    }

    public static float greenFloat(int color) {
        return green(color) / 255.0F;
    }

    public static float blueFloat(int color) {
        return blue(color) / 255.0F;
    }
}
