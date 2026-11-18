package dev.nookworks.compat;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * A text as it reads in English, whatever language the game is in: what JEI is given to search by besides the shown
 * name (the user, 2026-10-11: 日本語のまま英語での検索でも出てくるように - "sword" finds every sword with the game in
 * Japanese). Translated as the game translates - a key looked up, its %s and %1$s filled with the arguments, also in
 * English - but from a table of the English texts rather than the game's current language.
 */
public final class EnglishNames {
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");

    private EnglishNames() {}

    /** {@code text} in English, from {@code english} (key to text); a key it lacks reads as the key's fallback, or the key. */
    public static String of(Component text, Map<String, String> english) {
        StringBuilder out = new StringBuilder();
        append(out, text, english);
        return out.toString();
    }

    private static void append(StringBuilder out, Component text, Map<String, String> english) {
        if (text.getContents() instanceof TranslatableContents translatable) {
            String pattern = english.get(translatable.getKey());
            if (pattern == null) {
                pattern = translatable.getFallback() != null ? translatable.getFallback() : translatable.getKey();
            }
            Object[] args = translatable.getArgs();
            Matcher m = ARGUMENT.matcher(pattern);
            int next = 0;
            int at = 0;
            while (m.find()) {
                out.append(pattern, at, m.start());
                at = m.end();
                if (m.group(2).equals("%")) {
                    out.append('%');
                    continue;
                }
                int index = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                if (index >= 0 && index < args.length) {
                    Object arg = args[index];
                    if (arg instanceof Component component) {
                        append(out, component, english);
                    } else {
                        out.append(arg);
                    }
                }
            }
            out.append(pattern, at, pattern.length());
        } else if (text.getContents() instanceof PlainTextContents plain) {
            out.append(plain.text());
        } else {
            out.append(Component.empty().append(text.plainCopy()).getString());
        }
        for (Component sibling : text.getSiblings()) {
            append(out, sibling, english);
        }
    }
}
