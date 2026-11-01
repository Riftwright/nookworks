package dev.nookworks.client.gui;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

import dev.nookworks.Nookworks;
import dev.nookworks.storage.SortOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * The button between a Sort button and its order's: which way round the order runs, ascending or descending (the user,
 * 2026-10-31: 「昇順」「降順」のボタンもあったほうが親切だよね). Drawn as an arrow beside bars growing or shrinking down the
 * icon; its tooltip says which, and what that order then is. Each order runs its own way by itself - count and last
 * added descending, the game's order and the mods' ascending - so a click turns it round, and choosing another order
 * puts it back. On a Storage Barrel's screen and a chest's alike.
 */
public final class SortDirectionButton extends AbstractButton {
    public static final int WIDTH = 13;
    /** Two 8x8 glyphs: ascending, descending (tools/textures/gen_gui.py). */
    private static final Identifier ICONS = Nookworks.id("textures/gui/sort_directions.png");

    private final Supplier<SortOrder> order;
    private final BooleanSupplier reversed;
    private final Runnable turn;

    public SortDirectionButton(int x, int y, Supplier<SortOrder> order, BooleanSupplier reversed, Runnable turn) {
        super(x, y, WIDTH, 13, Component.empty());
        this.order = order;
        this.reversed = reversed;
        this.turn = turn;
    }

    /** Whether the order now runs descending: its own way, or turned round. */
    private boolean descending() {
        return this.order.get().descending() != this.reversed.getAsBoolean();
    }

    @Override
    public Component getMessage() {
        return Component.translatable(this.descending() ? "gui.nookworks.storage.direction.descending" : "gui.nookworks.storage.direction.ascending");
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.turn.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractDefaultSprite(graphics);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICONS, this.getX() + 3, this.getY() + 3, this.descending() ? 8 : 0, 0, 8, 8, 16, 8);
        if (this.isHovered()) {
            Minecraft mc = Minecraft.getInstance();
            graphics.setTooltipForNextFrame(mc.font, List.of(this.getMessage().getVisualOrderText(),
                    Component.translatable(this.order.get().translationKey(this.reversed.getAsBoolean())).withStyle(ChatFormatting.GRAY)
                            .getVisualOrderText(),
                    Component.translatable("gui.nookworks.storage.direction.hint").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                    mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
