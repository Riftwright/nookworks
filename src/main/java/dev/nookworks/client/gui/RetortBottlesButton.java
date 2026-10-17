package dev.nookworks.client.gui;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import dev.nookworks.mercury.RetortBottles;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModOil;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Beside the Sealed Retort's redstone button: where a glass bottle a hopper or pipe puts in goes ({@link RetortBottles}).
 * Its icon is the bottle that comes of it - a plain one for the old choice, Liquid Experience, or Wood Vinegar.
 */
public class RetortBottlesButton extends AbstractButton {
    public static final int SIZE = RedstoneButton.SIZE;

    private final Supplier<RetortBottles> mode;
    private final Runnable press;

    public RetortBottlesButton(int x, int y, Supplier<RetortBottles> mode, Runnable press) {
        super(x, y, SIZE, SIZE, Component.translatable("gui.nookworks.retort.bottles"));
        this.mode = mode;
        this.press = press;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.press.run();
    }

    private static ItemStack icon(RetortBottles mode) {
        return new ItemStack(switch (mode) {
            case AUTO -> Items.GLASS_BOTTLE;
            case EXPERIENCE -> ModItems.LIQUID_EXPERIENCE_BOTTLE.get();
            case VINEGAR -> ModOil.WOOD_VINEGAR.get();
        });
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        RetortBottles current = this.mode.get();
        MachineStyle.button(g, this.getX(), this.getY(), SIZE, SIZE, this.isHoveredOrFocused(), true);
        // the bottle at three quarters size, to sit inside the 14-pixel button as the redstone button's marks do
        g.pose().pushMatrix();
        g.pose().translate(this.getX() + 1, this.getY() + 1);
        g.pose().scale(0.75F, 0.75F);
        g.fakeItem(icon(current), 0, 0);
        g.pose().popMatrix();
        if (this.isHovered()) {
            g.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(
                    Component.translatable("gui.nookworks.retort.bottles"),
                    Component.translatable(current.translationKey()).withStyle(ChatFormatting.AQUA),
                    Component.translatable(current.translationKey() + ".detail").withStyle(ChatFormatting.GRAY),
                    Component.translatable("gui.nookworks.redstone.click").withStyle(ChatFormatting.DARK_GRAY)),
                    Optional.empty(), mouseX, mouseY);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
