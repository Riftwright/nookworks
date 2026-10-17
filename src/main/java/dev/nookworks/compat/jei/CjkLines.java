package dev.nookworks.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Japanese and Chinese text broken into lines before JEI sees it. JEI 31.10 (26.3) breaks a page's text at spaces and
 * hyphenates any word too long for a line - and Japanese and Chinese have no spaces, so a whole paragraph was one word
 * and every line ended in a "-", read in Japanese as the long-vowel mark (r15: "ビートルー｜ート"). Broken here into
 * lines that fit, JEI leaves each as it is.
 * <p>
 * A line may break between any two characters, except: not before closing punctuation or a small kana (、。」ー っ...),
 * not after an opening bracket, not inside a run of Latin letters and digits, and not between a number and its unit
 * ("250 mB", "40 FE/t", written with a space as DECISIONS has it) or the counter after it ("2個", "100年"). A space at a
 * line's end is dropped.
 */
public final class CjkLines {
    private CjkLines() {}

    /** Not at the start of a line. */
    private static final String NO_START = "、。，．,.・：:；;？?！!）)]｝}」』】〕〉》〙〗”’ー…‥ぁぃぅぇぉっゃゅょゎゕゖァィゥェォッャュョヮヵヶ々〻〜～%％";
    /** Not at the end of a line. */
    private static final String NO_END = "（([｛{「『【〔〈《〘〖“‘";

    /** Whether {@code text} has Japanese or Chinese in it (kana or ideographs), which JEI cannot break. */
    public static boolean needed(String text) {
        return text.codePoints().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN
                || Character.UnicodeScript.of(c) == Character.UnicodeScript.HIRAGANA
                || Character.UnicodeScript.of(c) == Character.UnicodeScript.KATAKANA);
    }

    /** {@code text} broken into lines no wider than {@code width} as {@code measure} measures them, joined by newlines. */
    public static String wrap(String text, int width, ToIntFunction<String> measure) {
        List<String> out = new ArrayList<>();
        for (String paragraph : text.split("\n", -1)) {
            out.addAll(lines(paragraph, width, measure));
        }
        return String.join("\n", out);
    }

    private static List<String> lines(String paragraph, int width, ToIntFunction<String> measure) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String atom : atoms(paragraph)) {
            if (line.isEmpty() && atom.isBlank()) {
                continue; // no space at a line's start
            }
            if (!line.isEmpty() && measure.applyAsInt(line + atom.stripTrailing()) > width) {
                lines.add(line.toString().stripTrailing());
                line.setLength(0);
                if (atom.isBlank()) {
                    continue;
                }
            }
            while (measure.applyAsInt(atom) > width && atom.length() > 1) { // one piece too long for any line: cut it
                int cut = atom.length() - 1;
                while (cut > 1 && measure.applyAsInt(atom.substring(0, cut)) > width) {
                    cut--;
                }
                while (cut > 1 && NO_START.indexOf(atom.charAt(cut)) >= 0) {
                    cut--; // the next line may not start with a closing mark either
                }
                lines.add(atom.substring(0, cut));
                atom = atom.substring(cut);
            }
            line.append(atom);
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(line.toString().stripTrailing());
        }
        return lines;
    }

    /** The pieces a line may break between. */
    static List<String> atoms(String text) {
        List<String> atoms = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int start = i;
            char c = text.charAt(i);
            if (c == ' ') {
                while (i < text.length() && text.charAt(i) == ' ') {
                    i++;
                }
            } else if (latin(c)) {
                while (i < text.length() && latin(text.charAt(i))) {
                    i++;
                }
                // a number and its unit stay together: "250 mB", "40 FE/t"; and its counter: "2個", "100年"
                if (Character.isDigit(text.charAt(i - 1)) && i + 1 < text.length() && text.charAt(i) == ' ' && Character.isLetter(text.charAt(i + 1))
                        && latin(text.charAt(i + 1))) {
                    i++;
                    while (i < text.length() && latin(text.charAt(i))) {
                        i++;
                    }
                } else if (Character.isDigit(text.charAt(i - 1)) && i < text.length() && text.charAt(i) != ' '
                        && NO_END.indexOf(text.charAt(i)) < 0 && NO_START.indexOf(text.charAt(i)) < 0) {
                    i += Character.charCount(text.codePointAt(i));
                }
            } else {
                i += Character.charCount(text.codePointAt(i));
            }
            atoms.add(text.substring(start, i));
        }
        // what may not start a line goes with the piece before; what may not end one, with the piece after
        List<String> joined = new ArrayList<>();
        StringBuilder carry = new StringBuilder();
        for (String atom : atoms) {
            if (!joined.isEmpty() && carry.isEmpty() && NO_START.indexOf(atom.charAt(0)) >= 0) {
                joined.set(joined.size() - 1, joined.getLast() + atom);
                continue;
            }
            carry.append(atom);
            if (NO_END.indexOf(atom.charAt(atom.length() - 1)) >= 0) {
                continue;
            }
            joined.add(carry.toString());
            carry.setLength(0);
        }
        if (!carry.isEmpty()) {
            joined.add(carry.toString());
        }
        return joined;
    }

    /** A letter, digit or mark of the Latin script and ASCII punctuation that runs with it (not the space). */
    private static boolean latin(char c) {
        return c > ' ' && c < 0x2000 && NO_END.indexOf(c) < 0 && NO_START.indexOf(c) < 0 || c == '.' || c == ',' || c == '%';
    }
}
