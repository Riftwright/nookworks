package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.client.ChestSortClient;
import dev.nookworks.client.gui.MachineStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;

/**
 * A chest's title, on a screen the chest Sort button was added to ({@link ChestSortClient}), stops short of the button
 * as a Storage Barrel's does - cut with an ellipsis, the whole of it shown on hover - rather than running under it. On
 * every other screen it is drawn as vanilla draws it. Only a look: should another mod change the same call, the title is
 * drawn as before rather than the game not starting ({@code require = 0}).
 */
@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    // 1.21.1: vanilla writes the title in renderLabels, by GuiGraphics.drawString (26.3: extractLabels, by text)
    @WrapOperation(method = "renderLabels", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"))
    private int nookworks$fitTitle(GuiGraphics graphics, Font font, Component title, int x, int y, int colour, boolean shadow,
            Operation<Integer> original) {
        int room = ChestSortClient.titleRoom((AbstractContainerScreen<?>) (Object) this);
        if (room <= 0) {
            return original.call(graphics, font, title, x, y, colour, shadow);
        }
        // PORT: vanilla 1.21.1 gives the title's colour with no alpha (0x404040), which its font makes opaque; the
        // extractor takes colours as 26.3 does (no alpha, not drawn), so the title vanished from every sorted chest
        int argb = (colour & 0xFC000000) == 0 ? colour | 0xFF000000 : colour;
        MachineStyle.fitText(GuiGraphicsExtractor.of(graphics), font, title, x, y, room, argb, shadow);
        return x + font.width(title); // what drawString answers: where the text ends (nobody reads it here)
    }
}
