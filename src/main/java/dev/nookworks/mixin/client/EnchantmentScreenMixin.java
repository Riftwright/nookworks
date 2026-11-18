package dev.nookworks.mixin.client;

import java.util.List;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.nookworks.port.gui.CursorTypes;

import dev.nookworks.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import dev.nookworks.port.gui.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import dev.nookworks.port.util.ARGB;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * The Enchanting Table's first option with a Liquid Experience Bottle in it ({@code mixin/EnchantmentMenuMixin} makes
 * the bottle): drawn as an option to take, not the greyed-out one vanilla shows for a choice with no enchantment in
 * it. It says what it makes, in plain letters rather than the table's runes, and its tooltip what it costs - a lapis,
 * and no levels. Only a look (the menu makes the bottle): should another mod change the same calls, vanilla's drawing is
 * left rather than the game not starting ({@code require = 0}).
 */
@Mixin(EnchantmentScreen.class)
abstract class EnchantmentScreenMixin extends AbstractContainerScreen<EnchantmentMenu> {
    @Shadow
    @Final
    private static ResourceLocation[] ENABLED_LEVEL_SPRITES;
    @Shadow
    @Final
    private static ResourceLocation[] DISABLED_LEVEL_SPRITES;
    @Shadow
    @Final
    private static ResourceLocation ENCHANTMENT_SLOT_DISABLED_SPRITE;
    @Shadow
    @Final
    private static ResourceLocation ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE;
    @Shadow
    @Final
    private static ResourceLocation ENCHANTMENT_SLOT_SPRITE;

    private EnchantmentScreenMixin(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    private boolean nookworks$bottle() {
        return this.menu.getSlot(0).getItem().is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get());
    }

    /**
     * The screen's drawing and its tooltip see no first option for the bottle - vanilla would draw it greyed out, in
     * runes, and say it has no enchantment - and this mixin draws it instead. The menu's own costs (the book opens for
     * the bottle, the button works) are left as they are.
     */
    // 1.21.1: the background is renderBg, the rest render (26.3: extractBackground, extractRenderState)
    @ModifyExpressionValue(method = {"renderBg", "render"}, require = 0,
            at = @At(value = "FIELD", target = "Lnet/minecraft/world/inventory/EnchantmentMenu;costs:[I"))
    private int[] nookworks$noVanillaOption(int[] costs) {
        return this.nookworks$bottle() ? new int[] {0, costs[1], costs[2]} : costs;
    }

    /** Whether the bottle can be made now: a lapis in its slot, or a creative player. */
    private boolean nookworks$ready() {
        return this.menu.getGoldCount() >= 1 || this.minecraft.player.getAbilities().instabuild;
    }

    @Inject(method = "renderBg", at = @At("TAIL"), require = 0)
    private void nookworks$bottleOption(GuiGraphics guiGraphics, float a, int mouseX, int mouseY, CallbackInfo ci) {
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(guiGraphics);
        if (!this.nookworks$bottle()) {
            return;
        }
        int x = this.leftPos + 60;
        int y = this.topPos + 14;
        boolean ready = this.nookworks$ready();
        boolean hover = ready && mouseX >= x && mouseY >= y && mouseX < x + 108 && mouseY < y + 19;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, !ready ? ENCHANTMENT_SLOT_DISABLED_SPRITE
                : hover ? ENCHANTMENT_SLOT_HIGHLIGHTED_SPRITE : ENCHANTMENT_SLOT_SPRITE, x, y, 108, 19);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, (ready ? ENABLED_LEVEL_SPRITES : DISABLED_LEVEL_SPRITES)[0], x + 1, y + 1, 16, 16);
        int colour = !ready ? ARGB.opaque((0xFF685E4A & 0xFEFEFE) >> 1) : hover ? 0xFFFFFF80 : 0xFF685E4A; // vanilla's own
        graphics.textWithWordWrap(this.font, Component.translatable("gui.nookworks.enchanting.bottle"), x + 20, y + 2, 86, colour, false);
        if (hover) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void nookworks$bottleTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        GuiGraphicsExtractor graphics = GuiGraphicsExtractor.of(guiGraphics);
        if (!this.nookworks$bottle() || !this.isHovering(60, 14, 108, 17, mouseX, mouseY)) {
            return;
        }
        boolean creative = this.minecraft.player.getAbilities().instabuild;
        List<Component> lines = creative
                ? List.of(Component.translatable("gui.nookworks.enchanting.bottle").withStyle(ChatFormatting.WHITE))
                : List.of(Component.translatable("gui.nookworks.enchanting.bottle").withStyle(ChatFormatting.WHITE), CommonComponents.EMPTY,
                        Component.translatable("container.enchant.lapis.one")
                                .withStyle(this.menu.getGoldCount() >= 1 ? ChatFormatting.GRAY : ChatFormatting.RED),
                        Component.translatable("gui.nookworks.enchanting.no_levels").withStyle(ChatFormatting.GRAY));
        graphics.setComponentTooltipForNextFrame(this.font, lines, mouseX, mouseY);
    }
}
