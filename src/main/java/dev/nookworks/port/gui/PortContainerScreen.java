package dev.nookworks.port.gui;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * What the mod's container screens extend in place of {@link AbstractContainerScreen} ({@code tools/port1211/tx/gui.py}
 * changes their {@code extends}): 1.21.1's container screen - its slots, clicks, drags and carried item as they are -
 * drawn and driven through 26.3's methods, so the screens keep their bodies.
 * <p>
 * A frame goes as 26.3's does: {@link #extractBackground} (by default the dimmed world behind, as 26.3's in-game
 * screens have it), then {@link #extractRenderState}: 1.21.1's {@code render} - the widgets, then, in the panel's
 * coordinates, {@link #extractLabels} (26.3 writes them before the slots: here at the first slot), each active slot by
 * {@link #extractSlot} (by default 1.21.1's slot, with its highlight after it), NeoForge's foreground event and the
 * carried item - and last {@link #extractTooltip}, kept and drawn over everything with the frame's other tooltip
 * (the first set wins, as in 26.3).
 * <p>
 * Input from 1.21.1's calls goes to the 26.3 methods taking events; their defaults hand it back to 1.21.1's own
 * handling (never to the 26.3 method again).
 */
public abstract class PortContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    /** How deep a 1.21.1 slot stands in front of where it is drawn: its count (the item is drawn into the slab under it). */
    private static final float SLOT_DEPTH = 61.0F;
    /** While 1.21.1 shares a dragged stack out over a slot, the slot's veil needs the slot drawn as 1.21.1 draws it. */
    private static final float DRAGGED_SLOT_DEPTH = 201.0F;

    /** Set while 1.21.1's {@code render} draws the contents: its background is drawn already. */
    private boolean drawingContents;
    private boolean labelsDrawn;
    private int mouseX;
    private int mouseY;
    /** The button of the click being asked {@link #hasClickedOutside(double, double, int, int)}. */
    private int outsideButton;

    protected PortContainerScreen(T menu, Inventory inventory, Component title) {
        this(menu, inventory, title, 176, 166);
    }

    /** As 26.3's: the panel's size given, the player's inventory label put 94 above its bottom. */
    protected PortContainerScreen(T menu, Inventory inventory, Component title, int imageWidth, int imageHeight) {
        super(menu, inventory, title);
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.inventoryLabelY = imageHeight - 94;
    }

    // ---- drawing ----

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        GuiGraphicsExtractor g = GuiGraphicsExtractor.of(graphics).begin();
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.extractBackground(g, mouseX, mouseY, a);
        g.nextStratum();
        this.extractRenderState(g, mouseX, mouseY, a);
        g.finishFrame();
    }

    /** The screen's background, before anything else: by default the world behind dimmed. */
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.beforeVanilla();
        this.renderTransparentBackground(graphics.graphics());
    }

    /** 1.21.1 asks for the background from inside {@code render}: drawn already, at the frame's start, by the port. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float a) {
        if (this.drawingContents) {
            return;
        }
        this.extractBackground(GuiGraphicsExtractor.of(graphics), mouseX, mouseY, a);
    }

    /** Never asked for: {@link #renderBackground} is the port's, and draws {@link #extractBackground}. */
    @Override
    protected void renderBg(GuiGraphics graphics, float a, int mouseX, int mouseY) {
    }

    /** The screen's contents over its background ({@link #extractContents}), and then its tooltip. */
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractContents(graphics, mouseX, mouseY, a);
        this.extractTooltip(graphics, mouseX, mouseY);
    }

    /**
     * Widgets, labels and slots, through 1.21.1's {@code render} - which draws the carried item in the same go, where
     * 26.3 draws it after this. What a screen draws after calling this still goes under the carried item: 1.21.1
     * stands that item far in front of the panel.
     */
    // PORT: 26.3 draws the carried item after the contents; a screen drawing much over many items after this could
    // climb in front of it (TradingCounterScreen's list, only while an item is carried over it)
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        this.labelsDrawn = false;
        graphics.beforeVanilla();
        this.drawingContents = true;
        try {
            super.render(graphics.graphics(), mouseX, mouseY, a);
        } finally {
            this.drawingContents = false;
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        GuiGraphicsExtractor g = GuiGraphicsExtractor.of(graphics);
        this.labelsFirst(g);
        this.extractSlot(g, slot, this.mouseX, this.mouseY);
    }

    /** One active slot, in the panel's coordinates: by default as 1.21.1 draws it. */
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        graphics.beforeVanilla(slot.x, slot.y, slot.x + 16, slot.y + 16);
        PoseStack pose = graphics.graphics().pose();
        // 1.21.1 draws a slot 100 in front and its item 150 more: pushed back so the item lies in the drawing's slab -
        // except where 1.21.1 draws something flat at the slot's own depth, which must not go behind the panel: the veil
        // over a slot a stack is being shared out over, and an empty slot's icon (an armour slot's outline)
        boolean dragged = this.isQuickCrafting && this.quickCraftSlots.contains(slot) && !this.menu.getCarried().isEmpty()
                || slot.getItem().isEmpty() && slot.getNoItemIcon() != null;
        pose.pushPose();
        pose.translate(0.0F, 0.0F, dragged ? -100.0F : -240.0F);
        super.renderSlot(graphics.graphics(), slot);
        pose.popPose();
        graphics.afterVanilla(slot.x, slot.y, slot.x + 17, slot.y + 17, dragged ? DRAGGED_SLOT_DEPTH : SLOT_DEPTH);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        this.labelsFirst(GuiGraphicsExtractor.of(graphics));
    }

    /** 26.3 writes the labels before the slots: at the first slot drawn, or where 1.21.1 writes them if none is. */
    private void labelsFirst(GuiGraphicsExtractor graphics) {
        if (!this.labelsDrawn) {
            this.labelsDrawn = true;
            this.extractLabels(graphics, this.mouseX, this.mouseY);
        }
    }

    /** The title and the inventory's label, in the panel's coordinates: by default 1.21.1's (26.3's colour, 0xFF404040). */
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.beforeVanilla();
        super.renderLabels(graphics.graphics(), xm, ym);
    }

    /** The tooltip for the frame, kept: by default the hovered slot's item's, with nothing carried. */
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem() && this.menu.getCarried().isEmpty()) {
            ItemStack item = this.hoveredSlot.getItem();
            graphics.setTooltipForNextFrame(this.font, this.getTooltipFromContainerItem(item), item.getTooltipImage(), item, mouseX, mouseY);
        }
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        this.extractTooltip(GuiGraphicsExtractor.of(graphics), mouseX, mouseY);
    }

    /** 26.3's name for the slot under the pointer. */
    public @Nullable Slot getHoveredSlot() {
        return this.hoveredSlot;
    }

    // ---- input: 1.21.1's calls to 26.3's methods, whose defaults are 1.21.1's handling ----

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return this.mouseClicked(Inputs.event(x, y, button), Inputs.doubleClick(x, y, button));
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return this.mouseReleased(Inputs.event(x, y, button));
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return this.mouseDragged(Inputs.event(x, y, button), dx, dy);
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return this.keyPressed(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean keyReleased(int key, int scanCode, int modifiers) {
        return this.keyReleased(new KeyEvent(key, scanCode, modifiers));
    }

    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return this.charTyped(new CharacterEvent(codePoint, modifiers));
    }

    public boolean charTyped(CharacterEvent event) {
        return super.charTyped((char) event.codepoint(), event.modifiers());
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        this.outsideButton = button;
        return this.hasClickedOutside(mouseX, mouseY, left, top);
    }

    /** 26.3's form, without the button (1.21.1's own test does not read it either). */
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top, this.outsideButton);
    }
}
