package dev.nookworks.client.gui;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import dev.nookworks.machine.RedstoneMode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The redstone mode button every machine screen has in its top right corner. Its icon shows the current mode;
 * hovering names it; clicking steps to the next one (the menu does the stepping on the server).
 */
public class RedstoneButton extends AbstractButton {
    public static final int SIZE = 14;
    /** Where it stands on a 176-wide screen: the same place on every screen of ours (r21: four sat 2 px further left). */
    public static final int X = 156;

    private final Supplier<RedstoneMode> mode;
    private final Runnable press;

    public RedstoneButton(int x, int y, Supplier<RedstoneMode> mode, Runnable press) {
        super(x, y, SIZE, SIZE, Component.translatable("gui.nookworks.redstone"));
        this.mode = mode;
        this.press = press;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.press.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        RedstoneMode current = this.mode.get();
        MachineStyle.redstoneButton(g, this.getX(), this.getY(), SIZE, current, this.isHoveredOrFocused());
        if (this.isHovered()) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(
                    Component.translatable("gui.nookworks.redstone"),
                    Component.translatable(current.translationKey()).withStyle(ChatFormatting.RED),
                    Component.translatable("gui.nookworks.redstone.click").withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
