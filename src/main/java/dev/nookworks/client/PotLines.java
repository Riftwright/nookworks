package dev.nookworks.client;

import java.util.List;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.cooking.PotLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

/**
 * An Iron Pot's contents on the action bar, fitted to the screen it is shown on (the action bar does not wrap: a line
 * wider than the screen is cut at both ends). Whole if it fits: the liquid and its room, each thing with its count.
 * Else shorter: the liquid without the pot's room, a count only where there are two or more. Else as many things as fit
 * and how many kinds more (Jade and the Jade-less readout still list them all). Why a pot over the fire cooks nothing
 * goes last, and is always kept.
 */
public final class PotLines {
    /** Room left either side of the line, so it does not touch the screen's edges. */
    private static final int MARGIN = 8;

    static {
        PotLine.shown = PotLines::show;
    }

    private PotLines() {}

    /** Hooks the pot's line up to what the server sends. */
    public static void init() {}

    private static void show(NookworksNetwork.PotContents pot) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> minecraft.gui.hud.setOverlayMessage(fit(pot, minecraft.font,
                minecraft.getWindow().getGuiScaledWidth() - 2 * MARGIN), false));
    }

    /** The line for {@code pot}, as wide as {@code room} at most if it can be (a pot's liquid alone always shows). */
    static Component fit(NookworksNetwork.PotContents pot, Font font, int room) {
        String liquid = switch (pot.liquid()) {
            case 1 -> "water";
            case 2 -> "oil";
            default -> "empty";
        };
        Component whole = noted(line(Component.translatable("nookworks.iron_pot." + liquid, pot.amount(), pot.capacity()), pot.items(),
                pot.items().size(), false), pot);
        if (font.width(whole) <= room) {
            return whole;
        }
        Component head = pot.liquid() == 0 ? Component.translatable("nookworks.iron_pot.empty")
                : Component.translatable("nookworks.iron_pot." + liquid + ".short", pot.amount());
        for (int shown = pot.items().size(); shown > 0; shown--) {
            Component line = noted(line(head, pot.items(), shown, true), pot);
            if (font.width(line) <= room) {
                return line;
            }
        }
        return noted(line(head, pot.items(), 0, true), pot);
    }

    /** {@code line} with why the pot cooks nothing after it, when it says. */
    private static Component noted(Component line, NookworksNetwork.PotContents pot) {
        return pot.note().<Component>map(note -> line.copy().append("  ").append(note)).orElse(line);
    }

    /** {@code head}, then the first {@code shown} of {@code items} ({@code brief}: a count only from two up), then the rest's number. */
    private static Component line(Component head, List<ItemStack> items, int shown, boolean brief) {
        MutableComponent line = head.copy();
        for (int i = 0; i < shown; i++) {
            ItemStack stack = items.get(i);
            line.append(Component.translatable("nookworks.list.separator")).append(brief && stack.getCount() == 1 ? stack.getHoverName()
                    : Component.translatable("nookworks.iron_pot.item", stack.getHoverName(), stack.getCount()));
        }
        if (shown < items.size()) {
            line.append(Component.translatable("nookworks.list.separator"))
                    .append(Component.translatable("nookworks.iron_pot.more", items.size() - shown));
        }
        return line;
    }
}
