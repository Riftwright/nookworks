package dev.nookworks.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.datafixers.util.Either;

import dev.nookworks.Nookworks;
import dev.nookworks.compat.jei.CjkLines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/**
 * Keeps a tooltip of ours right of the pointer over an inventory, as vanilla's short ones stay. Vanilla puts a tooltip
 * left of the pointer when it does not fit on the right, and in a narrow window that is most of the creative tab's
 * columns even for a name or a line of 160 px: the tooltips jumped from side to side from item to item (the user,
 * 2026-11-26: 「クリエタブの説明が右に出たり左に出たりして目障りな問題、全然治ってないじゃん」). So every text line wider than
 * the room right of the pointer is broken to that room: Japanese and Chinese between words ({@link CjkLines}), the
 * rest by the font's splitter, each piece in its line's style. One rule with nookTechnology, which does the same for
 * its own; both on one stack is harmless, as a tooltip that fits is left alone.
 * <p>
 * Only while drawn ({@code GatherComponents} is not read by the creative search) and only over the screen's own panel:
 * a recipe list at the screen's edge has no room on the right and is left as the game places it.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class TooltipRoom {
    /** Vanilla moves a tooltip left when {@code x + 12 + width} passes the screen's edge; 4 more for its frame. */
    private static final int MARGIN = 16;
    /** Less room than this and broken lines would be a tall column: left as the game places it. */
    public static final int LEAST = 48;

    @SubscribeEvent
    static void onGather(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!ours(stack)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
            return;
        }
        double mouseX = minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        if (mouseX < screen.getLeftPos() || mouseX >= screen.getLeftPos() + screen.getImageWidth()) {
            return;
        }
        int room = event.getScreenWidth() - Mth.floor(mouseX) - MARGIN;
        List<Either<FormattedText, TooltipComponent>> fitted = fit(event.getTooltipElements(), room, minecraft.font.getSplitter());
        if (fitted != null) {
            event.getTooltipElements().clear();
            event.getTooltipElements().addAll(fitted);
        }
    }

    /** Ours: an item of Nookworks, or a vanilla one we add a hint to (the lodestone). */
    private static boolean ours(ItemStack stack) {
        return !stack.isEmpty() && (Nookworks.MODID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace())
                || HintTooltips.hinted(stack));
    }

    /**
     * {@code elements} with every text line wider than {@code room} broken to it, as {@code splitter} measures; null
     * when nothing is wider, or the room is under {@link #LEAST}. Images are kept as they are.
     */
    public static @org.jspecify.annotations.Nullable List<Either<FormattedText, TooltipComponent>> fit(
            List<Either<FormattedText, TooltipComponent>> elements, int room, StringSplitter splitter) {
        if (room < LEAST || elements.stream().noneMatch(e -> e.left().filter(text -> width(splitter, text) > room).isPresent())) {
            return null;
        }
        List<Either<FormattedText, TooltipComponent>> out = new ArrayList<>();
        for (Either<FormattedText, TooltipComponent> element : elements) {
            Optional<FormattedText> left = element.left();
            if (left.isEmpty() || width(splitter, left.get()) <= room) {
                out.add(element);
                continue;
            }
            for (FormattedText piece : split(left.get(), room, splitter)) {
                out.add(Either.left(piece));
            }
        }
        return out;
    }

    private static List<FormattedText> split(FormattedText text, int room, StringSplitter splitter) {
        String plain = text.getString();
        if (!CjkLines.needed(plain)) {
            return splitter.splitLines(text, room, Style.EMPTY);
        }
        // CjkLines breaks plain text: the pieces take the line's first style (a name's colour, a hint's grey)
        Style style = text.visit((s, part) -> part.isEmpty() ? Optional.empty() : Optional.of(s), Style.EMPTY).orElse(Style.EMPTY);
        List<FormattedText> pieces = new ArrayList<>();
        for (String line : CjkLines.wrap(plain, room, s -> Mth.ceil(splitter.stringWidth(s))).split("\n")) {
            pieces.add(Component.literal(line).withStyle(style));
        }
        return pieces;
    }

    private static int width(StringSplitter splitter, FormattedText text) {
        return Mth.ceil(splitter.stringWidth(text));
    }

    private TooltipRoom() {}
}
