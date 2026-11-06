package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.jei.CjkLines;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.RedstoneMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

/**
 * The look shared by Nookworks machine screens: a panel of brushed steel ({@code textures/gui/steel.png}, which a
 * resource pack can repaint) in a bevelled brass rim held by four rivets, like the machines themselves; a recessed bay
 * for the machine's slots and gauges, each gauge headed by an icon saying what it measures ({@code gui/icons.png}); dark
 * sunken slots; and a status display under the bay. The shapes are drawn in code so every screen lines up with its
 * layout; the surfaces come from the textures.
 */
public final class MachineStyle {
    public static final Identifier STEEL = Nookworks.id("textures/gui/steel.png");
    public static final Identifier ICONS = Nookworks.id("textures/gui/icons.png");
    private static final int STEEL_SIZE = 64;

    /** The icons in {@link #ICONS}, left to right. */
    public enum Icon {
        ENERGY, WATER, VEGETABLE_OIL, BIODIESEL, MERCURY, EXPERIENCE, LIQUID_FUEL, NETHER_AIR
    }
    public static final int TITLE = 0xFFE2C27A;
    public static final int TEXT = 0xFFE6E0CE;
    public static final int DIM = 0xFFA3A9B2;

    private static final int EDGE = 0xFF121418;
    private static final int BRASS = 0xFFB08A3E;
    private static final int BRASS_DARK = 0xFF6E5424;
    private static final int BAY_TINT = 0xFFB4B8C0;
    private static final int DISPLAY = 0xFF0C0E11;
    private static final int MESH_HINT = 0x40C8A050;
    private static final int BRASS_LIT = 0xFFE2C27A;
    private static final int FACE = 0xFF3B4048;
    private static final int FACE_LIT = 0xFF4A5059;
    private static final int FACE_DARK = 0xFF2B2F35;
    private static final int WELL = 0xFF15171B;
    private static final int SLOT = 0xFF23262B;
    private static final int SLOT_LIT = 0xFF5A616B;
    private static final int EMPTY = 0xFF30353C;

    public static final int MERCURY = 0xFFB8C0CC;
    public static final int MERCURY_LIT = 0xFFDDE3EA;
    public static final int WATER = 0xFF3F6FD0;
    public static final int WATER_LIT = 0xFF7FA8F0;
    // the liquids' own colours, as they are drawn in the world (tools/textures/gen_liquids.py): a middle shade, and the
    // lit edge; biodiesel is the darker amber there, and here too
    public static final int OIL = 0xFFDEB442;
    public static final int OIL_LIT = 0xFFF1D270;
    public static final int BIODIESEL = 0xFF86521A;
    public static final int BIODIESEL_LIT = 0xFFB2782C;
    public static final int EXPERIENCE = 0xFF5CA820;
    public static final int EXPERIENCE_LIT = 0xFFB0EC56;
    // the Nether's air: crimson, as the motes of a crimson forest
    public static final int NETHER_AIR = 0xFFA8302A;
    public static final int NETHER_AIR_LIT = 0xFFE4724E;

    /** How wide a screen's hover text runs before it breaks: nookTechnology's screens' width, so both read alike (r21). */
    public static final int TOOLTIP_W = 200;

    /**
     * {@code lines} broken at {@link #TOOLTIP_W}, Japanese and Chinese between words (CjkLines), each piece in its line's
     * own style: handed over whole, the game broke them only at the screen's edge, a Japanese line between any two
     * characters (r21, 2026-11-06).
     */
    public static List<FormattedCharSequence> wrap(Font font, List<Component> lines) {
        List<FormattedCharSequence> broken = new ArrayList<>();
        for (Component line : lines) {
            String text = line.getString();
            if (!CjkLines.needed(text)) {
                broken.addAll(font.split(line, TOOLTIP_W));
                continue;
            }
            for (String piece : CjkLines.wrap(text, TOOLTIP_W, font::width).split("\n")) {
                broken.add(Component.literal(piece).withStyle(line.getStyle()).getVisualOrderText());
            }
        }
        return broken;
    }

    public static final int ARROW_W = 24;
    public static final int ARROW_H = 16;

    /**
     * A screen's finish: the machines' brushed steel, or the Repair Box's navy enamel with brass corner brackets, as the
     * box itself wears (the user, 2026-10-10: it is not one of the machines, and its screen may say so). The face and
     * bay colours (0: the steel texture as it is), the shaded and lit edges, the slots' well.
     */
    public record Finish(int face, int bay, int dark, int lit, int slot, boolean brackets) {
        public static final Finish STEEL = new Finish(0, 0, FACE_DARK, FACE_LIT, SLOT, false);
        public static final Finish ENAMEL = new Finish(0xFF374760, 0xFF2F3D52, 0xFF1F2838, 0xFF50647E, 0xFF1B2330, true);
    }

    /**
     * The background: a black outline with its corners cut, a brass rim lit from the top left, the steel face, and a
     * rivet in each corner.
     */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        panel(g, x, y, w, h, Finish.STEEL);
    }

    /** The background in {@code finish}: on enamel, brass brackets in the corners in place of the rivets. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h, Finish finish) {
        g.fill(x + 1, y, x + w - 1, y + h, EDGE);
        g.fill(x, y + 1, x + w, y + h - 1, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BRASS_DARK);  // the rim's shaded side, bottom and right
        g.fill(x + 1, y + 1, x + w - 2, y + h - 2, BRASS_LIT);   // its lit side, top and left
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, BRASS);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, finish.dark()); // the face sits a little below the rim
        face(g, x + 4, y + 4, w - 7, h - 7, finish.face(), -1);
        g.fill(x + 4, y + h - 4, x + w - 3, y + h - 3, finish.lit());
        g.fill(x + w - 4, y + 4, x + w - 3, y + h - 3, finish.lit());
        if (finish.brackets()) {
            bracket(g, x + 4, y + 4, 1, 1);
            bracket(g, x + w - 5, y + 4, -1, 1);
            bracket(g, x + 4, y + h - 5, 1, -1);
            bracket(g, x + w - 5, y + h - 5, -1, -1);
            return;
        }
        for (int[] corner : new int[][] {{x + 6, y + 6}, {x + w - 8, y + 6}, {x + 6, y + h - 8}, {x + w - 8, y + h - 8}}) {
            rivet(g, corner[0], corner[1]);
        }
    }

    /**
     * A brass corner bracket on the panel's face: an L two pixels thick and six long each way inwards from the corner
     * pixel (x, y), a rivet in its angle.
     */
    private static void bracket(GuiGraphicsExtractor g, int x, int y, int dx, int dy) {
        for (int k = 0; k < 6; k++) {
            for (int t = 0; t < 2; t++) {
                pixel(g, x + dx * k, y + dy * t, BRASS);
                pixel(g, x + dx * t, y + dy * k, BRASS);
            }
        }
        pixel(g, x, y, dx > 0 && dy > 0 ? BRASS_LIT : BRASS_DARK);
        pixel(g, x + dx, y + dy, BRASS_LIT);
    }

    private static void pixel(GuiGraphicsExtractor g, int x, int y, int colour) {
        g.fill(x, y, x + 1, y + 1, colour);
    }

    /** A face: the steel texture in {@code tint}, or a {@code colour} with the steel's grain faintly over it. */
    private static void face(GuiGraphicsExtractor g, int x, int y, int w, int h, int colour, int tint) {
        if (colour == 0) {
            steel(g, x, y, w, h, tint);
            return;
        }
        g.fill(x, y, x + w, y + h, colour);
        steel(g, x, y, w, h, 0x38FFFFFF);
    }

    /** A domed rivet head, 2x2, lit from the top left. */
    public static void rivet(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 2, y + 2, FACE_DARK);
        g.fill(x, y, x + 1, y + 1, SLOT_LIT);
        g.fill(x + 1, y + 1, x + 2, y + 2, EDGE);
    }

    /** Steel from the texture over a rectangle, tiled, in {@code tint} (-1: as it is). */
    public static void steel(GuiGraphicsExtractor g, int x, int y, int w, int h, int tint) {
        for (int ty = 0; ty < h; ty += STEEL_SIZE) {
            for (int tx = 0; tx < w; tx += STEEL_SIZE) {
                int bw = Math.min(STEEL_SIZE, w - tx);
                int bh = Math.min(STEEL_SIZE, h - ty);
                g.blit(RenderPipelines.GUI_TEXTURED, STEEL, x + tx, y + ty, tx % STEEL_SIZE, ty % STEEL_SIZE,
                        bw, bh, STEEL_SIZE, STEEL_SIZE, tint);
            }
        }
    }

    /**
     * The machine's bay: a recess in the panel, a shade darker, its top and left edges in shadow and its bottom and
     * right catching the light - the slots and gauges are set into it.
     */
    public static void bay(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        bay(g, x, y, w, h, Finish.STEEL);
    }

    public static void bay(GuiGraphicsExtractor g, int x, int y, int w, int h, Finish finish) {
        g.fill(x, y, x + w, y + 1, finish.dark());
        g.fill(x, y, x + 1, y + h, finish.dark());
        g.fill(x, y + h - 1, x + w, y + h, finish.lit());
        g.fill(x + w - 1, y, x + w, y + h, finish.lit());
        face(g, x + 1, y + 1, w - 2, h - 2, finish.bay(), BAY_TINT);
    }

    /**
     * The status display under the bay: a dark glass strip with a lamp at its left end in the colour of the machine's
     * state. The text goes on it at {@code x + 11}, {@code y + 2}.
     */
    public static void display(GuiGraphicsExtractor g, int x, int y, int w, int h, MachineStatus.Tone tone) {
        g.fill(x, y, x + w, y + h, FACE_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, FACE_LIT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, EDGE);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, DISPLAY);
        int lamp = toneColour(tone);
        int cy = y + h / 2;
        g.fill(x + 4, cy - 2, x + 8, cy + 2, darker(lamp));
        g.fill(x + 5, cy - 1, x + 7, cy + 1, lamp);
        g.fill(x + 5, cy - 1, x + 6, cy, 0xFFFFFFFF & (lamp | 0xFF606060));
    }

    private static int darker(int argb) {
        int r = (argb >> 16 & 0xFF) * 2 / 5;
        int gr = (argb >> 8 & 0xFF) * 2 / 5;
        int b = (argb & 0xFF) * 2 / 5;
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    /** One of the gauge icons, 8x8; dimmed when what it stands for is empty or missing. */
    public static void icon(GuiGraphicsExtractor g, Icon icon, int x, int y, boolean dim) {
        g.blit(RenderPipelines.GUI_TEXTURED, ICONS, x, y, icon.ordinal() * 8, dim ? 8 : 0, 8, 8, 64, 16);
    }

    /**
     * An item shown faded in an empty slot, saying what goes there. Drawn, then veiled in the slot's own colour on the
     * stratum above: in 26.2 a fill in the same stratum as an item is drawn under it, not over it.
     */
    public static void ghost(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        ghost(g, stack, x, y, 0xB0);
    }

    /**
     * {@link #ghost} with a veil of {@code veilAlpha} (0-255; the usual is 0xB0). A strongly coloured or block-shaped
     * hint reads better under a lighter one, about 0x90: an orange pumpkin under the usual veil came out brown, taken for
     * a log (r21, for nookTechnology's Pumpkin Carver).
     */
    public static void ghost(GuiGraphicsExtractor g, ItemStack stack, int x, int y, int veilAlpha) {
        g.fakeItem(stack, x, y);
        g.nextStratum();
        g.fill(x, y, x + 16, y + 16, (Math.clamp(veilAlpha, 0, 255) << 24) | 0x232629);
    }

    /**
     * Draws {@code text} at most {@code width} wide: cut short with an ellipsis if it is longer. Returns whether it
     * was cut, so the caller can show the whole of it on hover. No text is ever shrunk: small type on a scaled screen
     * falls between pixels.
     */
    public static boolean fitText(GuiGraphicsExtractor g, Font font, Component text,
            int x, int y, int width, int colour, boolean shadow) {
        if (font.width(text) <= width) {
            g.text(font, text, x, y, colour, shadow);
            return false;
        }
        String ellipsis = "\u2026";
        var cut = font.substrByWidth(text, width - font.width(ellipsis));
        g.text(font, Language.getInstance().getVisualOrder(FormattedText.composite(cut,
                FormattedText.of(ellipsis))), x, y, colour, shadow);
        return true;
    }

    /** A groove across the panel, between the machine and the player's inventory. */
    public static void divider(GuiGraphicsExtractor g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, FACE_DARK);
        g.fill(x, y + 1, x + w, y + 2, FACE_LIT);
    }

    /** An 18x18 sunken slot whose item area starts at (x+1, y+1): in shadow at the top left, lit at the bottom right. */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        slot(g, x, y, Finish.STEEL);
    }

    public static void slot(GuiGraphicsExtractor g, int x, int y, Finish finish) {
        g.fill(x, y, x + 18, y + 18, finish == Finish.STEEL ? SLOT_LIT : finish.lit());
        g.fill(x, y, x + 17, y + 17, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, finish.slot());
        g.fill(x + 1, y + 1, x + 17, y + 2, 0xFF1A1C20); // the lip's shadow along the top
    }

    /** A raised steel button face; pressed-in look when disabled. */
    public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover, boolean active) {
        g.fill(x, y, x + w, y + h, EDGE);
        if (!active) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, FACE_DARK);
            return;
        }
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? BRASS_LIT : FACE_LIT);
        g.fill(x + 2, y + 2, x + w - 1, y + h - 1, FACE_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + h - 2, hover ? SLOT_LIT : FACE_LIT);
    }

    /** A thin horizontal bar that fills with brass. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float done) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
        int filled = Math.round((w - 2) * Math.max(0.0F, Math.min(1.0F, done)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, BRASS);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, BRASS_LIT);
        }
    }

    /** An empty frame for things that are drawn rather than slotted (poster designs). */
    public static void well(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, WELL);
    }

    /**
     * A filter slot: sunken like any slot but edged in brass, since what sits in it is a pattern the machine
     * compares against rather than something stored.
     */
    public static void filterSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, BRASS);
        g.fill(x, y, x + 17, y + 17, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, SLOT);
    }

    /** A ghost slot for what a machine keeps out (the Sorters' Keep Out row): framed in a dull red, its well a touch warm. */
    public static void denySlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF9A3B32);
        g.fill(x, y, x + 17, y + 17, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF2A2326);
    }

    /** A slot of the Keep Out row turned round, what alone is let in: as {@link #denySlot}, in green. */
    public static void allowSlot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF3B7A32);
        g.fill(x, y, x + 17, y + 17, EDGE);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF232A24);
    }

    /** A slot for a part the machine is fitted with (the Sifter's mesh): framed in brass; empty, it shows the part faded. */
    public static void fittingSlot(GuiGraphicsExtractor g, int x, int y, boolean filled) {
        filterSlot(g, x, y);
    }

    /** A tank's colour and its lit side's, by what is in it. */
    public static int colour(GuiTank.Kind kind) {
        return switch (kind) {
            case MERCURY -> MERCURY;
            case WATER -> WATER;
            case VEGETABLE_OIL, LIQUID_FUEL -> OIL;
            case BIODIESEL -> BIODIESEL;
            case EXPERIENCE -> EXPERIENCE;
            case NETHER_AIR -> NETHER_AIR;
        };
    }

    public static int lit(GuiTank.Kind kind) {
        return switch (kind) {
            case MERCURY -> MERCURY_LIT;
            case WATER -> WATER_LIT;
            case VEGETABLE_OIL, LIQUID_FUEL -> OIL_LIT;
            case BIODIESEL -> BIODIESEL_LIT;
            case EXPERIENCE -> EXPERIENCE_LIT;
            case NETHER_AIR -> NETHER_AIR_LIT;
        };
    }

    /**
     * Beside a tank's icon, which way it moves through the pipes: a little arrow down into the tank for one that fills
     * from them, up out of it for one that empties into them.
     */
    public static void flowMark(GuiGraphicsExtractor g, int x, int y, MachineLayout.Flow flow) {
        if (flow == MachineLayout.Flow.NONE) {
            return; // nothing a pipe carries
        }
        boolean in = flow == MachineLayout.Flow.IN;
        // Two chevrons, pointing down into the tank or up out of it.
        for (int c = 0; c < 2; c++) {
            for (int r = 0; r < 3; r++) {
                int row = y + c * 3 + (in ? r : 2 - r);
                g.fill(x + r, row, x + r + 1, row + 1, BRASS_LIT);
                g.fill(x + 4 - r, row, x + 5 - r, row + 1, BRASS_LIT);
            }
        }
    }

    /** How full a store is, filling with brass; anything in it shows as at least a sliver. */
    public static void fill(GuiGraphicsExtractor g, int x, int y, int w, int h, float done) {
        g.fill(x, y, x + w, y + h, EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
        int filled = done <= 0 ? 0 : Math.max(1, Math.round((w - 2) * Math.min(1.0F, done)));
        if (filled > 0) {
            g.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, BRASS);
            g.fill(x + 1, y + 1, x + 1 + filled, y + 2, BRASS_LIT);
        }
    }

    public static int toneColour(MachineStatus.Tone tone) {
        return switch (tone) {
            case GOOD -> 0xFF7EDC7E;
            case QUIET -> DIM;
            case WARN -> 0xFFF0C050;
            case BAD -> 0xFFFF6A5A;
        };
    }

    /** The energy gauge: a column of cells like a battery meter, coloured by whether the machine can run. */
    public static void energy(GuiGraphicsExtractor g, int x, int y, int h, int energy, int capacity, MachineStatus.Tone tone) {
        // Framed in red when the machine cannot run on what it has, so an empty gauge still says something.
        g.fill(x, y, x + 10, y + h, tone == MachineStatus.Tone.BAD ? 0xFF7A2420 : EDGE);
        g.fill(x + 1, y + 1, x + 9, y + h - 1, WELL);
        int cells = (h - 3) / 4;
        int lit = capacity <= 0 ? 0 : (int) Math.ceil((double) cells * energy / capacity);
        int colour = switch (tone) {
            case GOOD -> 0xFF62D262;
            case WARN -> 0xFFECB030;
            case BAD, QUIET -> 0xFFD8382E;
        };
        for (int i = 0; i < cells; i++) {
            int bottom = y + h - 2 - i * 4;
            g.fill(x + 2, bottom - 3, x + 8, bottom, i < lit ? colour : EMPTY);
        }
    }

    /** The energy gauge laid on its side, for screens with a long row to spare. */
    public static void energyBar(GuiGraphicsExtractor g, int x, int y, int w, int h, int energy, int capacity, MachineStatus.Tone tone) {
        g.fill(x, y, x + w, y + h, tone == MachineStatus.Tone.BAD ? 0xFF7A2420 : EDGE);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, WELL);
        int cells = (w - 3) / 4;
        int lit = capacity <= 0 ? 0 : (int) Math.ceil((double) cells * energy / capacity);
        int colour = switch (tone) {
            case GOOD -> 0xFF62D262;
            case WARN -> 0xFFECB030;
            case BAD, QUIET -> 0xFFD8382E;
        };
        for (int i = 0; i < cells; i++) {
            int left = x + 2 + i * 4;
            g.fill(left, y + 2, left + 3, y + h - 2, i < lit ? colour : EMPTY);
        }
    }

    /**
     * An arrow from the inputs to the outputs that fills with brass as the work goes on. Its head closes at its last
     * column: a head begun two sooner closed short of the width, and the columns after it - their top below their bottom,
     * which fill draws all the same - left a stray dot past the point.
     */
    public static void arrow(GuiGraphicsExtractor g, int x, int y, float done) {
        int filled = Math.round(ARROW_W * Math.max(0.0F, Math.min(1.0F, done)));
        int head = ARROW_W - 7; // the head's seven columns, from 14 high to 2
        for (int i = 0; i < ARROW_W; i++) {
            int top;
            int bottom;
            if (i < head) {
                top = y + 6;
                bottom = y + 10;
            } else {
                int t = i - head;
                top = y + 1 + t;
                bottom = y + 15 - t;
            }
            g.fill(x + i, top, x + i + 1, bottom, i < filled ? BRASS : WELL);
            if (i < filled) {
                g.fill(x + i, top, x + i + 1, top + 1, BRASS_LIT);
            }
        }
    }

    private static final String[] FLAME = {
            "......#.......",
            ".....##.......",
            ".....###......",
            "....####..#...",
            "....#####.##..",
            "...##########.",
            "..###########.",
            "..############",
            ".#############",
            ".#############",
            ".############.",
            "..###########.",
            "...#########..",
            ".....#####....",
    };

    /** The generator's fire, burning down from the top as its fuel is spent. */
    public static void flame(GuiGraphicsExtractor g, int x, int y, float left) {
        int rows = FLAME.length;
        int shown = left <= 0 ? 0 : Math.max(1, (int) Math.ceil(rows * Math.min(1.0F, left)));
        for (int r = 0; r < rows; r++) {
            boolean burning = r >= rows - shown;
            // unlit in the well's dark, as an empty arrow is (r21: on the bay it was all but invisible)
            int colour = !burning ? WELL : r < 5 ? 0xFFFFD050 : r < 10 ? 0xFFF08A24 : 0xFFE0501C;
            String row = FLAME[r];
            for (int c = 0; c < row.length(); c++) {
                if (row.charAt(c) == '#') {
                    g.fill(x + 1 + c, y + 1 + r, x + 2 + c, y + 2 + r, colour);
                }
            }
        }
    }

    /** The laser: its lens on the left and the beam running out as far as it reaches. */
    public static void beam(GuiGraphicsExtractor g, int x, int y, int w, float reach, boolean firing) {
        g.fill(x, y - 5, x + 12, y + 5, EDGE);
        g.fill(x + 1, y - 4, x + 11, y + 4, FACE_LIT);
        g.fill(x + 9, y - 2, x + 12, y + 2, firing ? 0xFFFF6A5A : 0xFF6A2A26);
        int start = x + 13;
        int span = w - 13;
        if (!firing) {
            for (int i = 0; i < span; i += 4) {
                g.fill(start + i, y - 1, start + i + 2, y + 1, WELL); // idle: the well's dark, as the flame and arrow
            }
            return;
        }
        int end = start + Math.round(span * Math.max(0.0F, Math.min(1.0F, reach)));
        g.fill(start, y - 2, end, y + 2, 0xFFE0302A);
        g.fill(start, y - 1, end, y + 1, 0xFFFFE0D0);
        g.fill(end - 1, y - 3, end + 2, y + 3, 0xFFFFB199);
    }

    /** A glass tank of mercury with a scale beside it. */
    public static void tank(GuiGraphicsExtractor g, int x, int y, int h, int amount, int capacity) {
        tank(g, x, y, h, amount, capacity, MERCURY, MERCURY_LIT);
    }

    /** A glass tank of any liquid, in its colour and a lighter one for the lit side. */
    public static void tank(GuiGraphicsExtractor g, int x, int y, int h, int amount, int capacity, int colour, int lit) {
        g.fill(x, y, x + 14, y + h, EDGE);
        g.fill(x + 1, y + 1, x + 13, y + h - 1, WELL);
        int level = capacity <= 0 ? 0 : (int) ((long) (h - 2) * amount / capacity);
        if (level > 0) {
            int top = y + h - 1 - level;
            g.fill(x + 1, top, x + 13, y + h - 1, colour);
            g.fill(x + 2, top, x + 4, y + h - 1, lit);
            g.fill(x + 1, top, x + 13, top + 1, 0xFFF2F5F8);
        }
        for (int t = y + 6; t < y + h - 2; t += 8) {
            g.fill(x + 9, t, x + 13, t + 1, SLOT_LIT);
        }
        g.fill(x + 5, y + 2, x + 6, y + h - 2, 0x40FFFFFF);
    }

    /** Under the outputs: they leave through the bottom. */
    public static void below(GuiGraphicsExtractor g, int x, int y) {
        for (int r = 0; r < 4; r++) {
            g.fill(x + r, y + r, x + 7 - r, y + r + 1, BRASS_LIT);
        }
    }

    /** The redstone mode button, its icon saying the mode: dust, a lit torch, an unlit torch, a stop sign. */
    public static void redstoneButton(GuiGraphicsExtractor g, int x, int y, int size, RedstoneMode mode, boolean hover) {
        g.fill(x, y, x + size, y + size, EDGE);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, hover ? SLOT_LIT : FACE_LIT);
        g.fill(x + 2, y + 2, x + size - 1, y + size - 1, FACE_DARK);
        g.fill(x + 2, y + 2, x + size - 2, y + size - 2, hover ? FACE_LIT : FACE);
        int cx = x + size / 2;
        int cy = y + size / 2;
        switch (mode) {
            case ALWAYS -> {
                int dust = 0xFF9A9A9A;
                for (int[] p : new int[][] {{0, -3}, {-3, 0}, {0, 0}, {3, 0}, {0, 3}, {-2, -2}, {2, 2}}) {
                    g.fill(cx + p[0] - 1, cy + p[1] - 1, cx + p[0] + 1, cy + p[1] + 1, dust);
                }
            }
            case WITH_SIGNAL, WITHOUT_SIGNAL -> {
                boolean lit = mode == RedstoneMode.WITH_SIGNAL;
                g.fill(cx - 1, cy - 1, cx + 1, cy + 5, 0xFF7A5630);
                g.fill(cx - 1, cy - 4, cx + 1, cy - 1, lit ? 0xFFFF3A2A : 0xFF5A1A14);
                if (lit) {
                    g.fill(cx - 2, cy - 3, cx - 1, cy - 2, 0xFFFFA080);
                    g.fill(cx + 1, cy - 3, cx + 2, cy - 2, 0xFFFFA080);
                    g.fill(cx - 1, cy - 5, cx + 1, cy - 4, 0xFFFFA080);
                }
            }
            case NEVER -> {
                int red = 0xFFD8382E;
                g.fill(cx - 2, cy - 4, cx + 2, cy - 3, red);
                g.fill(cx - 2, cy + 3, cx + 2, cy + 4, red);
                g.fill(cx - 4, cy - 2, cx - 3, cy + 2, red);
                g.fill(cx + 3, cy - 2, cx + 4, cy + 2, red);
                g.fill(cx - 3, cy - 3, cx - 2, cy - 2, red);
                g.fill(cx + 2, cy - 3, cx + 3, cy - 2, red);
                g.fill(cx - 3, cy + 2, cx - 2, cy + 3, red);
                g.fill(cx + 2, cy + 2, cx + 3, cy + 3, red);
                for (int i = -2; i <= 1; i++) {
                    g.fill(cx + i, cy - i - 1, cx + i + 1, cy - i, red);
                }
            }
        }
    }

    private MachineStyle() {}
}
