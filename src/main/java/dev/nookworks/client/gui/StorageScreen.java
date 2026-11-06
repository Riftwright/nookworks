package dev.nookworks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.storage.StorageMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Storage Barrel GUI, in the light grey of vanilla's own containers - a barrel is a barrel, not a machine: six rows at
 * a time with a scrollbar (mouse wheel or drag), item counts shortened to fit a slot (1.2K, 3.4M, 1.0B), a strip under
 * the rows saying how full the barrel is, and a Sort button with the order it sorts in beside it, and which way round.
 */
public class StorageScreen extends AbstractContainerScreen<StorageMenu> {
    private static final int BAR_X = 176;
    private static final int BAR_W = 12;
    private static final int FILL_H = 6;
    private static final int TEXT = 0xFF404040;
    /** The sort order icons, one 8x8 glyph per order in {@link dev.nookworks.storage.SortOrder}'s order. */
    private static final Identifier ORDER_ICONS = Nookworks.id("textures/gui/sort_orders.png");
    private static final int ORDER_W = 13;
    /** Long counts are drawn in this: digits a size up on vanilla's, and a bold K (tools/textures/gen_count_font.py). */
    private static final FontDescription COUNT_FONT = new FontDescription.Resource(Nookworks.id("count"));
    /** How far left of a slot's right edge its count may reach: across its own slot, short of the next one's count. */
    private static final int COUNT_ROOM = 17;
    private boolean dragging;
    /** How far below the thumb's top the pointer took hold of it, kept while it is dragged. */
    private int grab;
    private OrderButton order;
    /**
     * The row last asked for, or -1 when none is outstanding. The scrollbar follows it at once; the slots show the
     * server's row, and are not clicked until it has arrived, so a click never lands on a row not yet on screen.
     */
    private int asked = -1;

    public StorageScreen(StorageMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 194, menu.playerY() + 84);
        this.inventoryLabelY = menu.playerY() - 11;
    }

    @Override
    protected void init() {
        super.init();
        Component label = Component.translatable("gui.nookworks.storage.sort");
        int w = this.font.width(label) + 10;
        int sortX = this.leftPos + this.imageWidth - 7 - w;
        this.addRenderableWidget(Button.builder(label,
                b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, StorageMenu.BUTTON_SORT))
                .bounds(sortX, this.topPos + 3, w, 13).build());
        int turnX = sortX - SortDirectionButton.WIDTH - 2;
        this.addRenderableWidget(new SortDirectionButton(turnX, this.topPos + 3, this.menu::sortOrder, this.menu::sortReversed,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, StorageMenu.BUTTON_REVERSE)));
        this.order = this.addRenderableWidget(new OrderButton(turnX - ORDER_W - 2, this.topPos + 3));
    }

    /**
     * The order Sort sorts in, as an icon: most first, the game's item order, by mod, last put in first. A click moves
     * to the next order and a right-click back; the tooltip names it.
     */
    private final class OrderButton extends AbstractButton {
        OrderButton(int x, int y) {
            super(x, y, ORDER_W, 13, Component.empty());
        }

        @Override
        public Component getMessage() {
            return Component.translatable("gui.nookworks.storage.order", Component.translatable(
                    StorageScreen.this.menu.sortOrder().translationKey(StorageScreen.this.menu.sortReversed())));
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo info) {
            return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            StorageScreen.this.minecraft.gameMode.handleInventoryButtonClick(StorageScreen.this.menu.containerId,
                    event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? StorageMenu.BUTTON_ORDER_BACK : StorageMenu.BUTTON_ORDER_NEXT);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            StorageScreen.this.minecraft.gameMode.handleInventoryButtonClick(StorageScreen.this.menu.containerId, StorageMenu.BUTTON_ORDER_NEXT);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            int icon = StorageScreen.this.menu.sortOrder().ordinal();
            graphics.blit(RenderPipelines.GUI_TEXTURED, ORDER_ICONS, this.getX() + 3, this.getY() + 3,
                    icon * 8, 0, 8, 8, 32, 8);
            if (this.isHovered()) {
                graphics.setTooltipForNextFrame(StorageScreen.this.font, List.of(this.getMessage().getVisualOrderText(),
                        Component.translatable("gui.nookworks.storage.order.hint").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                        mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        GuiDraw.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            GuiDraw.slotAt(graphics, this.leftPos, this.topPos, slot.x, slot.y);
        }
        // a scroll track only where there is something to scroll: on the Iron and Gold barrels, which show all they hold,
        // an empty track with no thumb read as a control that had failed to draw (r21, 2026-11-06)
        if (this.menu.maxScroll() > 0) {
            GuiDraw.well(graphics, this.leftPos + BAR_X, this.trackY(), BAR_W, this.trackH());
            GuiDraw.thumb(graphics, this.leftPos + BAR_X + 1, this.thumbY(), BAR_W - 2, this.thumbH(), this.dragging);
        }
        GuiDraw.gauge(graphics, this.leftPos + this.fillX(), this.topPos + this.fillY(), this.fillW(), FILL_H, this.fullness());
    }

    private int trackY() {
        return this.topPos + StorageMenu.GRID_Y - 1;
    }

    private int trackH() {
        return this.menu.visibleRows() * 18;
    }

    private int thumbH() {
        return Math.max(12, (this.trackH() - 2) * this.menu.visibleRows() / this.menu.tier().rows());
    }

    /** The thumb's top on the screen, for the row shown. */
    private int thumbY() {
        return this.trackY() + 1 + (this.trackH() - 2 - this.thumbH()) * this.row() / Math.max(1, this.menu.maxScroll());
    }

    private float fullness() {
        return (float) this.menu.storedStacks() / Math.max(1, this.menu.tier().stackCapacity());
    }

    /** The fill gauge runs along the strip under the rows, after its label. */
    private int fillX() {
        return 8 + this.font.width(this.fillLabel()) + 6;
    }

    private int fillW() {
        return BAR_X + BAR_W - this.fillX();
    }

    private int fillY() {
        return StorageMenu.GRID_Y + this.menu.visibleRows() * 18 + 4;
    }

    private Component fillLabel() {
        return Component.translatable("gui.nookworks.storage.fill_label", (int) Math.floor(this.fullness() * 100));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.titleRoom(), TEXT, false);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, TEXT, false);
        graphics.text(this.font, this.fillLabel(), 8, this.fillY() - 1, TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menu.maxScroll() > 0 && this.isHovering(BAR_X, StorageMenu.GRID_Y, BAR_W, this.menu.visibleRows() * 18, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.nookworks.storage.rows", this.row() + 1,
                    this.row() + this.menu.visibleRows(), this.menu.tier().rows()), mouseX, mouseY);
            return;
        }
        // a name too long for the room before the buttons is cut short: the pointer on it shows it whole
        if (this.font.width(this.title) > this.titleRoom()
                && this.isHovering(this.titleLabelX, this.titleLabelY - 1, this.titleRoom(), this.font.lineHeight + 1, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, this.title, mouseX, mouseY);
            return;
        }
        if (this.isHovering(8, this.fillY() - 2, BAR_X + BAR_W - 8, FILL_H + 4, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.nookworks.storage.fill",
                    String.format(Locale.ROOT, "%,d", this.menu.storedStacks()),
                    String.format(Locale.ROOT, "%,d", this.menu.tier().stackCapacity())), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    /** The width the barrel's name has, up to a few pixels short of the order button. */
    private int titleRoom() {
        return this.order.getX() - this.leftPos - 3 - this.titleLabelX;
    }

    /**
     * The kind of the drag under way, as vanilla takes it when the drag starts (its own is private): 0 shares out (left),
     * 1 one each (right), 2 a stack each (middle, creative). The mouse handler's pressed flags cannot be read for it:
     * they are not kept while a screen is open.
     */
    private int dragKind;

    /**
     * Oversized stacks: draw the item ourselves with a shortened count. Anything longer than vanilla's two digits is
     * drawn smaller, in a font of its own: three characters at full size are wider than a slot and run into the count
     * beside them. While a stack is dragged across slots to share it out, the slot shows what it would hold after, on
     * the light ground vanilla gives a dragged-over slot - its count yellow where the barrel could take no more (review
     * r5 A08: vanilla draws that preview in the slot drawing this replaces).
     */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (slot instanceof StorageMenu.StorageSlot) {
            ItemStack stack = slot.getItem();
            int colour = -1;
            ItemStack carried = this.menu.getCarried();
            if (this.isQuickCrafting && this.quickCraftSlots.size() > 1 && this.quickCraftSlots.contains(slot) && !carried.isEmpty()
                    && AbstractContainerMenu.canItemQuickReplace(slot, carried, true) && this.menu.canDragTo(slot)) {
                // the drag's kind, kept from the button that started it (dragKind): left shares out, right puts one in
                // each, middle (creative) fills each; at most a stack, as the drop itself gives
                int kind = this.dragKind;
                int max = Math.min(carried.getMaxStackSize(), slot.getMaxStackSize(carried));
                int count = AbstractContainerMenu.getQuickCraftPlaceCount(this.quickCraftSlots.size(), kind, carried)
                        + (stack.isEmpty() ? 0 : stack.getCount());
                if (count > max) {
                    count = max;
                    colour = 0xFFFFFF55; // vanilla's yellow for a slot that is full
                }
                stack = carried.copyWithCount(count);
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, -2130706433);
            }
            if (!stack.isEmpty()) {
                graphics.item(stack, slot.x, slot.y);
                graphics.itemDecorations(this.font, stack, slot.x, slot.y, ""); // bars and cooldowns, but not vanilla's count
                if (stack.getCount() > 1) {
                    String count = StorageMenu.shortCount(stack.getCount());
                    graphics.pose().pushMatrix();
                    graphics.pose().translate(slot.x + 17, slot.y + 17);
                    if (count.length() > 2) {
                        Component text = Component.literal(count).withStyle(style -> style.withFont(COUNT_FONT));
                        int width = this.font.width(text);
                        float scale = this.countScale(width);
                        graphics.pose().scale(scale, scale);
                        graphics.text(this.font, text, -width, -8, colour, true);
                    } else {
                        graphics.text(this.font, count, -this.font.width(count), -8, colour, true);
                    }
                    graphics.pose().popMatrix();
                }
            }
            return;
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    /**
     * How small a long count {@code width} font pixels wide is drawn: as many whole screen pixels to each of its
     * pixels as there are up to three quarters of a GUI pixel (a half at GUI scale 2, two thirds at 3), so its glyphs
     * stay crisp instead of smeared - fewer if it would otherwise reach past its slot.
     */
    private float countScale(int width) {
        int gui = (int) Math.round(this.minecraft.getWindow().getGuiScale());
        for (int pixels = gui * 3 / 4; pixels >= 1; pixels--) {
            if (width * pixels <= COUNT_ROOM * gui) {
                return (float) pixels / gui;
            }
        }
        return Math.min(0.75F, (float) COUNT_ROOM / width); // GUI scale 1: no whole screen pixel is small enough
    }

    /** The row the scrollbar shows: the one asked for, until the server has scrolled there. */
    private int row() {
        if (this.asked >= 0 && this.asked == this.menu.scrollRow()) {
            this.asked = -1;
        }
        return this.asked >= 0 ? this.asked : this.menu.scrollRow();
    }

    /** A row asked for is still on its way: the slots on screen are the old row's. */
    private boolean scrolling() {
        return this.row() != this.menu.scrollRow();
    }

    /** For the inventory tweaks, which click slots themselves: they wait for the row too. */
    public boolean waitingForRow() {
        return this.scrolling();
    }

    private void scrollTo(int row) {
        int target = Math.max(0, Math.min(this.menu.maxScroll(), row));
        if (target != this.row()) {
            this.asked = target;
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, target);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.menu.maxScroll() > 0 && mouseY < this.topPos + StorageMenu.GRID_Y + this.menu.visibleRows() * 18) {
            this.scrollTo(this.row() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /** The slot clicked last, and whether this press is a shift + double-click on a barrel slot (see mouseReleased). */
    private @Nullable Slot lastClicked;
    private boolean gathering;

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Slot slot = this.hoveredSlot;
        this.gathering = doubleClick && event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown()
                && slot instanceof StorageMenu.StorageSlot && slot == this.lastClicked;
        this.lastClicked = slot;
        if (this.onBar(event.x(), event.y())) {
            // the thumb is taken where it is held, so a click on it moves nothing; a click beside it brings it there
            int top = this.thumbY();
            if (event.y() >= top && event.y() < top + this.thumbH()) {
                this.grab = (int) event.y() - top;
            } else {
                this.grab = this.thumbH() / 2;
                this.dragTo(event.y());
            }
            this.dragging = true;
            return true;
        }
        boolean wasDragging = this.isQuickCrafting;
        boolean handled = super.mouseClicked(event, doubleClick);
        if (this.isQuickCrafting && !wasDragging) { // a drag starts: its kind, as vanilla takes it (dragKind)
            this.dragKind = event.button() == InputConstants.MOUSE_BUTTON_LEFT ? 0
                    : event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 2;
        }
        return handled;
    }

    /**
     * Clicks, key swaps and drops on a barrel slot wait the moment it takes a row asked for to arrive, and so does a
     * double-click gathering a stack, which takes from the barrel's rows on screen too.
     */
    @Override
    protected void slotClicked(Slot slot, int slotId, int button, ContainerInput input) {
        if ((slot instanceof StorageMenu.StorageSlot || input == ContainerInput.PICKUP_ALL) && this.scrolling()) {
            return;
        }
        super.slotClicked(slot, slotId, button, input);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    /**
     * A shift + double-click on a barrel slot: vanilla moves the stacks of that kind it can (none of more than a stack,
     * none off screen), then the server is asked to move the rest (StorageMenu.BUTTON_GATHER).
     */
    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        boolean gather = this.gathering && event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.hasShiftDown() && this.hoveredSlot == this.lastClicked
                && !this.scrolling();
        this.gathering = false;
        boolean handled = super.mouseReleased(event);
        if (gather) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, StorageMenu.BUTTON_GATHER);
        }
        return handled;
    }

    private boolean onBar(double x, double y) {
        return this.menu.maxScroll() > 0 && x >= this.leftPos + BAR_X && x < this.leftPos + BAR_X + BAR_W
                && y >= this.topPos + StorageMenu.GRID_Y && y < this.topPos + StorageMenu.GRID_Y + this.menu.visibleRows() * 18;
    }

    /** Puts the thumb's top {@link #grab} above the pointer, as near as a row allows. */
    private void dragTo(double y) {
        int free = this.trackH() - 2 - this.thumbH();
        double f = free <= 0 ? 0 : (y - this.grab - this.trackY() - 1) / free;
        this.scrollTo((int) Math.round(f * this.menu.maxScroll()));
    }
}
