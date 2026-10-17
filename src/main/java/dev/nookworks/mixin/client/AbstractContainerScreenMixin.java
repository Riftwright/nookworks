package dev.nookworks.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import dev.nookworks.client.ChestSortClient;
import dev.nookworks.client.gui.MachineStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    @WrapOperation(method = "extractLabels", require = 0, at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private void nookworks$fitTitle(GuiGraphicsExtractor graphics, Font font, Component title, int x, int y, int colour, boolean shadow,
            Operation<Void> original) {
        int room = ChestSortClient.titleRoom((AbstractContainerScreen<?>) (Object) this);
        if (room <= 0) {
            original.call(graphics, font, title, x, y, colour, shadow);
            return;
        }
        MachineStyle.fitText(graphics, font, title, x, y, room, colour, shadow);
    }
}
