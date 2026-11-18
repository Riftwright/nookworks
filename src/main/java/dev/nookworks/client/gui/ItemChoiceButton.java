package dev.nookworks.client.gui;

import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import dev.nookworks.port.gui.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A Settings-tab button that shows what a machine is set to make, as its item, and steps to the next choice when
 * clicked: the Stone Maker's cobblestone and stone (the user, 2026-11-14: 「石と丸石は、右側の設定タブから切り替える」).
 * In the machine screens' steel, the size of a slot.
 */
public class ItemChoiceButton extends dev.nookworks.port.gui.PortButton {
    public static final int SIZE = 18;

    private final Supplier<ItemStack> shown;
    private final Supplier<List<Component>> tooltip;
    private final Runnable press;
    private final BooleanSupplier enabled;

    public ItemChoiceButton(int x, int y, Component name, Supplier<ItemStack> shown, Supplier<List<Component>> tooltip, Runnable press) {
        this(x, y, name, shown, tooltip, press, () -> true);
    }

    /**
     * With {@code enabled} false the button shows what is made but takes no click: the Stone Maker's choice while blue
     * ice is in, which makes basalt whatever the choice says (the user, 2026-11-14).
     */
    public ItemChoiceButton(int x, int y, Component name, Supplier<ItemStack> shown, Supplier<List<Component>> tooltip, Runnable press,
            BooleanSupplier enabled) {
        super(x, y, SIZE, SIZE, name);
        this.shown = shown;
        this.tooltip = tooltip;
        this.press = press;
        this.enabled = enabled;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.press.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        this.active = this.enabled.getAsBoolean();
        MachineStyle.button(g, this.getX(), this.getY(), SIZE, SIZE, this.active && this.isHoveredOrFocused(), this.active);
        g.item(this.shown.get(), this.getX() + 1, this.getY() + 1);
        if (this.isHovered()) {
            List<Component> lines = new java.util.ArrayList<>();
            lines.add(this.getMessage());
            for (Component line : this.tooltip.get()) {
                lines.add(line.copy().withStyle(ChatFormatting.GRAY));
            }
            g.setTooltipForNextFrame(Minecraft.getInstance().font, lines, Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
