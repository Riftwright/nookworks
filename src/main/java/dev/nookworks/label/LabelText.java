package dev.nookworks.label;

/**
 * What a letter label may say: up to {@link #MAX} of the letters A-Z and a-z, the digits and spaces. So few that each
 * fits a six-bit code ({@link #code}), which is how the Printing Press's screen sends the text, a letter a button, and
 * how the press sends it back, two letters a data slot.
 */
public final class LabelText {
    public static final int MAX = 8;
    private static final String CHARS = " ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private LabelText() {}

    public static boolean allowed(char c) {
        return CHARS.indexOf(c) >= 0;
    }

    /** {@code text} with every other character left out, cut to {@link #MAX} and trimmed of spaces at the ends. */
    public static String clean(String text) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < text.length() && out.length() < MAX; i++) {
            char c = text.charAt(i);
            if (allowed(c)) {
                out.append(c);
            }
        }
        return out.toString().strip();
    }

    /** 1 to 63 for an allowed character, 0 for any other. */
    public static int code(char c) {
        return CHARS.indexOf(c) + 1;
    }

    /** The character of a code from {@link #code}, or 0 for none. */
    public static char of(int code) {
        return code >= 1 && code <= CHARS.length() ? CHARS.charAt(code - 1) : 0;
    }

    /** Two letters of {@code text} from {@code from}, as one number of twelve bits (it goes over the wire as a short). */
    public static int pack(String text, int from) {
        int a = from < text.length() ? code(text.charAt(from)) : 0;
        int b = from + 1 < text.length() ? code(text.charAt(from + 1)) : 0;
        return a | b << 6;
    }

    /** The letters {@link #pack} made of {@code packed}, added to {@code out}. */
    public static void unpack(int packed, StringBuilder out) {
        for (int shift = 0; shift <= 6; shift += 6) {
            char c = of(packed >> shift & 63);
            if (c != 0) {
                out.append(c);
            }
        }
    }
}
