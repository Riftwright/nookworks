package dev.nookworks.port.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Stands in for 26.3's {@code net.minecraft.client.gui.components.ScrollableLayout}, which 1.21.1 lacks: a layout shown
 * in a box no taller than its maximum height, scrolled with the wheel or by dragging its scrollbar (vanilla's scroller
 * sprites, 6 wide, a notch moving 10), room for the scrollbar kept on both sides (26.3's default) so the contents stay
 * centred. Its widgets are drawn clipped to the box; a widget focused from the keyboard is scrolled into view.
 * Written from 26.3's behaviour over 1.21.1's container widget.
 */
public class ScrollableLayout implements Layout {
    private static final int DEFAULT_SCROLLBAR_SPACING = 4;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLER_MIN_HEIGHT = 32;
    private static final int SCROLL_RATE = 10;
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");

    private final Layout content;
    private final Container container;
    private int scrollbarSpacing = DEFAULT_SCROLLBAR_SPACING;
    private int minWidth;
    private int minHeight;
    private int maxHeight;

    public ScrollableLayout(Minecraft minecraft, Layout content, int maxHeight) {
        this.content = content;
        this.maxHeight = maxHeight;
        this.container = new Container(minecraft, 0, maxHeight);
    }

    public void setScrollbarSpacing(int scrollbarSpacing) {
        this.scrollbarSpacing = scrollbarSpacing;
    }

    public void setMinWidth(int minWidth) {
        this.minWidth = minWidth;
        this.container.setWidth(Math.max(this.content.getWidth(), minWidth));
    }

    public void setMinHeight(int minHeight) {
        this.minHeight = minHeight;
        this.container.setHeight(Math.max(this.content.getHeight(), minHeight));
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
        this.container.setHeight(Math.min(this.content.getHeight(), maxHeight));
        this.container.refreshScrollAmount();
    }

    @Override
    public void arrangeElements() {
        this.content.arrangeElements();
        int contentWidth = this.content.getWidth();
        this.container.setWidth(Math.max(contentWidth, this.minWidth) + 2 * this.container.scrollbarReserve());
        this.container.setHeight(Mth.clamp(this.container.getHeight(), this.minHeight, this.maxHeight));
        this.container.refreshChildren();
        this.container.refreshScrollAmount();
    }

    @Override
    public void visitChildren(Consumer<LayoutElement> visitor) {
        visitor.accept(this.container);
    }

    public void removeChildren() {
        this.container.children.clear();
    }

    @Override
    public void setX(int x) {
        this.container.setX(x);
    }

    @Override
    public void setY(int y) {
        this.container.setY(y);
    }

    @Override
    public int getX() {
        return this.container.getX();
    }

    @Override
    public int getY() {
        return this.container.getY();
    }

    @Override
    public int getWidth() {
        return this.container.getWidth();
    }

    @Override
    public int getHeight() {
        return this.container.getHeight();
    }

    public double getScrollAmount() {
        return this.container.scrollAmount;
    }

    /** The box: the layout's widgets, clipped to it, and the scrollbar at its right. */
    private final class Container extends AbstractContainerWidget {
        private final Minecraft minecraft;
        private final List<AbstractWidget> children = new ArrayList<>();
        private double scrollAmount;
        private boolean scrolling;

        Container(Minecraft minecraft, int width, int height) {
            super(0, 0, width, height, CommonComponents.EMPTY);
            this.minecraft = minecraft;
            this.refreshChildren();
        }

        void refreshChildren() {
            this.children.clear();
            ScrollableLayout.this.content.visitWidgets(this.children::add);
        }

        private int contentHeight() {
            return ScrollableLayout.this.content.getHeight();
        }

        int scrollbarReserve() {
            return ScrollableLayout.this.scrollbarSpacing + SCROLLBAR_WIDTH;
        }

        private int maxScrollAmount() {
            return Math.max(0, this.contentHeight() - this.height);
        }

        private boolean scrollable() {
            return this.maxScrollAmount() > 0;
        }

        void refreshScrollAmount() {
            this.setScrollAmount(this.scrollAmount);
        }

        void setScrollAmount(double amount) {
            this.scrollAmount = Mth.clamp(amount, 0.0, this.maxScrollAmount());
            ScrollableLayout.this.content.setY(this.getRectangle().top() - (int) this.scrollAmount);
        }

        private int scrollBarX() {
            return this.getRight() - SCROLLBAR_WIDTH;
        }

        private int scrollerHeight() {
            return Mth.clamp((int) ((float) (this.height * this.height) / this.contentHeight()), SCROLLER_MIN_HEIGHT, this.height - 8);
        }

        private int scrollBarY() {
            return this.maxScrollAmount() == 0 ? this.getY()
                    : Math.max(this.getY(), (int) this.scrollAmount * (this.height - this.scrollerHeight()) / this.maxScrollAmount() + this.getY());
        }

        private boolean isOverScrollbar(double x, double y) {
            return x >= this.scrollBarX() && x <= this.scrollBarX() + SCROLLBAR_WIDTH && y >= this.getY() && y < this.getBottom();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float a) {
            GuiGraphicsExtractor g = GuiGraphicsExtractor.of(graphics);
            graphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
            for (AbstractWidget child : this.children) {
                g.beforeVanilla(child.getX(), child.getY(), child.getRight(), child.getBottom());
                child.render(graphics, mouseX, mouseY, a);
            }
            graphics.disableScissor();
            if (this.scrollable()) {
                int x = this.scrollBarX();
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_BACKGROUND_SPRITE, x, this.getY(), SCROLLBAR_WIDTH, this.getHeight());
                g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_SPRITE, x, this.scrollBarY(), SCROLLBAR_WIDTH, this.scrollerHeight());
                if (this.isOverScrollbar(mouseX, mouseY)) {
                    g.requestCursor(this.scrolling ? CursorTypes.RESIZE_NS : CursorTypes.POINTING_HAND);
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }

        @Override
        public boolean mouseClicked(double x, double y, int button) {
            this.scrolling = this.scrollable() && button == InputConstants.MOUSE_BUTTON_LEFT && this.isOverScrollbar(x, y);
            return super.mouseClicked(x, y, button) || this.scrolling;
        }

        @Override
        public boolean mouseReleased(double x, double y, int button) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                this.scrolling = false;
            }
            return super.mouseReleased(x, y, button);
        }

        @Override
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (this.scrolling) {
                if (y < this.getY()) {
                    this.setScrollAmount(0.0);
                } else if (y > this.getBottom()) {
                    this.setScrollAmount(this.maxScrollAmount());
                } else {
                    double max = Math.max(1, this.maxScrollAmount());
                    double yDragScale = Math.max(1.0, max / (this.height - this.scrollerHeight()));
                    this.setScrollAmount(this.scrollAmount + dy * yDragScale);
                }
            }
            return super.mouseDragged(x, y, button, dx, dy);
        }

        @Override
        public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            if (!this.visible) {
                return false;
            }
            this.setScrollAmount(this.scrollAmount - scrollY * SCROLL_RATE);
            return true;
        }

        @Override
        public void setFocused(@Nullable GuiEventListener focused) {
            super.setFocused(focused);
            if (focused != null && this.minecraft.getLastInputType().isKeyboard()) {
                ScreenRectangle area = this.getRectangle();
                ScreenRectangle focusedRect = focused.getRectangle();
                int topDelta = focusedRect.top() - area.top();
                int bottomDelta = focusedRect.bottom() - area.bottom();
                if (topDelta < 0) {
                    this.setScrollAmount(this.scrollAmount + topDelta - SCROLL_RATE);
                } else if (bottomDelta > 0) {
                    this.setScrollAmount(this.scrollAmount + bottomDelta + SCROLL_RATE);
                }
            }
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            ScrollableLayout.this.content.setX(x + this.scrollbarReserve());
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            ScrollableLayout.this.content.setY(y - (int) this.scrollAmount);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.children;
        }
    }
}
