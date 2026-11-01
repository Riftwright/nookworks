package dev.nookworks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import dev.nookworks.trading.TradingCounterBlockEntity;
import dev.nookworks.trading.TradingCounterMenu;
import dev.nookworks.trading.TradingCounterView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * Trading Counter GUI, in vanilla's light grey: on the left the villager's offers as its own trading screen lists them -
 * at the counter owner's price, a struck-out number where that differs from the base - each with a box to tick and how
 * much of it is left in stock; on the right the villager, its level and experience, the payment and goods slots, whether
 * it has a workstation and how many restocks it has left today; under both, what the counter is doing or waits for.
 * A click on a row ticks or unticks it (on the server: the screen shows what comes back). Over the list's top right, a
 * round arrow re-rolls the trades of a villager never traded with - greyed, saying why, for any other.
 */
public class TradingCounterScreen extends AbstractContainerScreen<TradingCounterMenu> {
    private static final Identifier TRADE_ARROW = Identifier.withDefaultNamespace("container/villager/trade_arrow");
    private static final Identifier TRADE_ARROW_OUT = Identifier.withDefaultNamespace("container/villager/trade_arrow_out_of_stock");
    private static final Identifier STRIKE = Identifier.withDefaultNamespace("container/villager/discount_strikethrough");
    private static final Identifier XP_BACK = Identifier.withDefaultNamespace("container/villager/experience_bar_background");
    private static final Identifier XP_CURRENT = Identifier.withDefaultNamespace("container/villager/experience_bar_current");

    private static final int LABEL = 0xFF404040;
    private static final int GOOD = 0xFF3E8E2E;
    private static final int WAIT = 0xFFC08A1E;
    private static final int BAD = 0xFFB0352A;

    private static final int LIST_X = 7;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 126;
    private static final int ROW_H = 20;
    private static final int ROWS = 5;
    private static final int BAR_W = 6;
    private static final int ROW_W = LIST_W - BAR_W - 1;
    /** Where each part of a row starts, from the row's left: vanilla's spacing, after the box. */
    private static final int COST_A = 15;
    private static final int COST_B = 45;
    private static final int ARROW = 64;
    private static final int RESULT = 78;
    private static final int STOCK = 99;
    private static final int STOCK_W = 16;

    private static final int RIGHT_X = 139;
    private static final int RIGHT_W = 130;
    private static final int XP_W = 102;
    private static final int STATUS_Y = 128;

    /** The re-roll button: a vanilla button over the list's top right corner, where a browser's reload sits over its page. */
    private static final int REROLL_SIZE = 13;
    private static final int REROLL_X = LIST_X + LIST_W + 1 - REROLL_SIZE;
    private static final int REROLL_Y = 3;
    /** Its round arrow, a browser's reload: a ring open at the top right, the arrow's head there pointing on round. */
    private static final String[] REROLL_ICON = {
            "...XXX.X.",
            "..X...XX.",
            ".X...XXX.",
            "X........",
            "X.......X",
            "X.......X",
            ".X.....X.",
            "..X...X..",
            "...XXX...",
    };
    /** How wide its tooltip's lines run before they wrap. */
    private static final int TIP_W = 180;
    /** "Stop when prices rise": a tick box and its words over the right column, beside the re-roll button. */
    private static final int PAUSE_Y = 5;
    private static final int PAUSE_TEXT_X = RIGHT_X + 12;

    private int scroll;
    private boolean draggingBar;
    private int barGrab;
    private boolean statusCut;
    /** Ticks before the re-roll button takes another click: one click, not a run of them while the new list comes. */
    private int rerollWait;

    public TradingCounterScreen(TradingCounterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 276, TradingCounterMenu.INVENTORY_Y + 82);
        this.inventoryLabelX = TradingCounterMenu.INVENTORY_X;
        this.inventoryLabelY = TradingCounterMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new RerollButton(this.leftPos + REROLL_X, this.topPos + REROLL_Y));
    }

    private TradingCounterView view() {
        return this.menu.view;
    }

    private int offerCount() {
        return this.view().offers().size();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        this.scroll = Mth.clamp(this.scroll, 0, this.maxScroll());
        if (this.rerollWait > 0) {
            this.rerollWait--;
        }
    }

    // ---- the re-roll button ----

    /**
     * Draws the trades of the villager behind the counter again, as it would on taking its job afresh: only one never
     * traded with, and the server checks all of it again. Greyed while it cannot, its tooltip saying why.
     */
    private final class RerollButton extends AbstractButton {
        RerollButton(int x, int y) {
            super(x, y, REROLL_SIZE, REROLL_SIZE, Component.translatable("gui.nookworks.trading_counter.reroll"));
        }

        @Override
        public void onPress(InputWithModifiers input) {
            TradingCounterScreen screen = TradingCounterScreen.this;
            screen.minecraft.gameMode.handleInventoryButtonClick(screen.menu.containerId, TradingCounterMenu.BUTTON_REROLL);
            screen.rerollWait = TradingCounterBlockEntity.REROLL_PAUSE;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            TradingCounterScreen screen = TradingCounterScreen.this;
            TradingCounterBlockEntity.Reroll why = screen.view().rerollOf();
            this.active = why == TradingCounterBlockEntity.Reroll.OK && screen.rerollWait == 0; // as the view stands this frame
            this.extractDefaultSprite(g);
            int x = this.getX() + 2;
            int y = this.getY() + 2;
            if (this.active) {
                extractIcon(g, x + 1, y + 1, 0xFF3F3F3F); // a shadow, as a button's label has
            }
            extractIcon(g, x, y, this.active ? 0xFFFFFFFF : 0xFFA0A0A0);
            if (this.isHovered()) {
                List<FormattedCharSequence> lines = new ArrayList<>();
                lines.add(this.getMessage().getVisualOrderText());
                Component more = why == TradingCounterBlockEntity.Reroll.OK ? why.text().copy().withStyle(ChatFormatting.GRAY)
                        : why.text().copy().withStyle(ChatFormatting.RED);
                lines.addAll(screen.font.split(more, TIP_W));
                g.setTooltipForNextFrame(screen.font, lines, mouseX, mouseY);
            }
        }

        private static void extractIcon(GuiGraphicsExtractor g, int x, int y, int colour) {
            for (int row = 0; row < REROLL_ICON.length; row++) {
                String line = REROLL_ICON[row];
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == 'X') {
                        g.fill(x + col, y + row, x + col + 1, y + row + 1, colour);
                    }
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    // ---- the list ----

    private int maxScroll() {
        return Math.max(0, this.offerCount() - ROWS);
    }

    private int thumbH() {
        return Math.max(10, ROWS * ROW_H * ROWS / Math.max(ROWS, this.offerCount()));
    }

    private int thumbTop() {
        return this.topPos + LIST_Y + (ROWS * ROW_H - this.thumbH()) * this.scroll / Math.max(1, this.maxScroll());
    }

    private boolean onBar(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos - LIST_X - ROW_W - 1;
        double y = mouseY - this.topPos - LIST_Y;
        return this.maxScroll() > 0 && x >= -1 && x < BAR_W + 1 && y >= 0 && y < ROWS * ROW_H;
    }

    private void dragBar(double mouseY) {
        int free = ROWS * ROW_H - this.thumbH();
        double f = free <= 0 ? 0 : (mouseY - this.barGrab - this.topPos - LIST_Y) / free;
        this.scroll = Mth.clamp((int) Math.round(f * this.maxScroll()), 0, this.maxScroll());
    }

    /** The offer under the pointer, as its index in the villager's list, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos - LIST_X;
        double y = mouseY - this.topPos - LIST_Y;
        if (x < 0 || x >= ROW_W || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = this.scroll + (int) (y / ROW_H);
        return index < this.offerCount() ? index : -1;
    }

    private boolean ticked(int index) {
        return this.view().ticked().contains(index);
    }

    /** What the offer asks first, at the counter owner's price. */
    private int price(int index, MerchantOffer offer) {
        List<Integer> prices = this.view().prices();
        return index < prices.size() ? prices.get(index) : offer.getCostA().getCount();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.onBar(event.x(), event.y())) {
            int top = this.thumbTop();
            if (event.y() >= top && event.y() < top + this.thumbH()) {
                this.barGrab = (int) event.y() - top;
            } else {
                this.barGrab = this.thumbH() / 2;
                this.dragBar(event.y());
            }
            this.draggingBar = true;
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.onPause(event.x(), event.y())) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, TradingCounterMenu.BUTTON_PAUSE_WHEN_DEAR);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.4F));
            return true;
        }
        int row = this.rowAt(event.x(), event.y());
        if (row >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.menu.getCarried().isEmpty()) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, row);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.4F));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.draggingBar) {
            this.dragBar(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingBar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.isHovering(LIST_X, LIST_Y, LIST_W, ROWS * ROW_H, x, y)) {
            this.scroll = Mth.clamp(this.scroll - (int) Math.signum(scrollY), 0, this.maxScroll());
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        GuiDraw.panel(g, x, y, this.imageWidth, this.imageHeight);
        GuiDraw.well(g, x + LIST_X - 1, y + LIST_Y - 1, LIST_W + 2, ROWS * ROW_H + 2);
        for (var slot : this.menu.slots) {
            GuiDraw.slotAt(g, x, y, slot.x, slot.y);
        }
        this.extractArrow(g, x + TradingCounterMenu.PAY_X + 56, y + TradingCounterMenu.GRID_Y + 22);
        this.extractXp(g, x, y);
        this.extractStatusDot(g, x + LIST_X, y + STATUS_Y + 1);
        this.extractBox(g, x + RIGHT_X, y + PAUSE_Y, this.view().pauseWhenDear());
    }

    /** Whether the pointer is over "Stop when prices rise": its box or its words. */
    private boolean onPause(double mouseX, double mouseY) {
        int w = 12 + Math.min(RIGHT_W - 12, this.font.width(Component.translatable("gui.nookworks.trading_counter.pause_when_dear")));
        return this.isHovering(RIGHT_X - 1, PAUSE_Y - 1, w + 2, 11, mouseX, mouseY);
    }

    /**
     * The arrow from payment to goods, drawn grey into the panel as a furnace's is (vanilla's trade arrow is pale, for a
     * darker button, and vanished on the light panel); green while it trades.
     */
    private void extractArrow(GuiGraphicsExtractor g, int x, int y) {
        int colour = this.view().statusOf() == TradingCounterBlockEntity.Status.TRADING ? 0xFF6E9A5E : 0xFF8B8B8B;
        g.fill(x, y + 3, x + 9, y + 6, colour);
        for (int i = 0; i < 5; i++) {
            g.fill(x + 9 + i, y + i, x + 10 + i, y + 9 - i, colour);
        }
    }

    /** The villager's experience toward its next level, as on its own screen; nothing at Master. */
    private void extractXp(GuiGraphicsExtractor g, int x, int y) {
        TradingCounterView view = this.view();
        int level = view.level();
        if (view.villager().isEmpty() || level <= 0 || !VillagerData.canLevelUp(level)) {
            return;
        }
        int bx = x + RIGHT_X + (RIGHT_W - XP_W) / 2;
        int by = y + LIST_Y + 12;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_BACK, bx, by, XP_W, 5);
        int min = VillagerData.getMinXpPerLevel(level);
        int max = VillagerData.getMaxXpPerLevel(level);
        if (view.xp() >= min && max > min) {
            int w = Math.min(Mth.floor((float) XP_W * (view.xp() - min) / (max - min)), XP_W);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, XP_CURRENT, XP_W, 5, 0, 0, bx, by, w, 5);
        }
    }

    private void extractStatusDot(GuiGraphicsExtractor g, int x, int y) {
        int colour = tone(this.view().statusOf());
        g.fill(x, y, x + 7, y + 7, 0xFF373737);
        g.fill(x + 1, y + 1, x + 6, y + 6, colour);
        g.fill(x + 1, y + 1, x + 3, y + 3, 0x60FFFFFF);
    }

    private static int tone(TradingCounterBlockEntity.Status status) {
        return switch (status) {
            case TRADING -> GOOD;
            case NO_VILLAGER, NOT_A_TRADER, NOT_ALLOWED, NO_WORKSTATION -> BAD;
            default -> WAIT;
        };
    }

    @Override
    public void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractContents(g, mouseX, mouseY, a);
        this.extractList(g, mouseX, mouseY);
    }

    /** The offers, or what to do while there are none to show. */
    private void extractList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.leftPos + LIST_X;
        int y = this.topPos + LIST_Y;
        TradingCounterView view = this.view();
        if (view.offers().isEmpty()) {
            Component hint = Component.translatable(view.villager().isEmpty() ? "gui.nookworks.trading_counter.hint"
                    : "gui.nookworks.trading_counter.no_offers");
            List<FormattedCharSequence> lines = this.font.split(hint, ROW_W - 8);
            for (int i = 0; i < lines.size() && i < ROWS * 2; i++) {
                g.text(this.font, lines.get(i), x + 4, y + 4 + i * 10, 0xFFFFFFFF, true);
            }
            return;
        }
        int hovered = this.rowAt(mouseX, mouseY);
        for (int row = 0; row < ROWS && this.scroll + row < view.offers().size(); row++) {
            int index = this.scroll + row;
            this.extractRow(g, index, view.offers().get(index), x, y + row * ROW_H, index == hovered);
        }
        if (this.maxScroll() > 0) {
            int barX = x + ROW_W + 1;
            int top = this.thumbTop();
            GuiDraw.thumb(g, barX, top, BAR_W, this.thumbH(), this.draggingBar);
        }
    }

    /** One offer: its box, what it asks (at the owner's price), the arrow, what it gives, and its stock. */
    private void extractRow(GuiGraphicsExtractor g, int index, MerchantOffer offer, int x, int y, boolean hovered) {
        boolean ticked = this.ticked(index);
        boolean out = offer.isOutOfStock();
        if (ticked) {
            g.fill(x, y, x + ROW_W, y + ROW_H, hovered ? 0xFF9CC28A : 0xFF8AB078);
        } else if (hovered) {
            g.fill(x, y, x + ROW_W, y + ROW_H, 0xFF9E9E9E);
        }
        if (index > this.scroll) {
            g.fill(x + 2, y, x + ROW_W - 2, y + 1, 0x40000000); // a faint rule between rows
        }
        this.extractBox(g, x + 3, y + 5, ticked);

        int top = y + 2;
        ItemStack base = offer.getBaseCostA();
        int price = this.price(index, offer);
        ItemStack costA = base.copyWithCount(price);
        g.fakeItem(costA, x + COST_A, top);
        if (price == base.getCount()) {
            g.itemDecorations(this.font, costA, x + COST_A, top);
        } else {
            // as the villager's own screen shows a changed price: the base struck out, the price beside it
            g.itemDecorations(this.font, base, x + COST_A, top, base.getCount() == 1 ? "1" : null);
            String count = String.valueOf(price);
            g.text(this.font, count, x + COST_A + 14 + 19 - 2 - this.font.width(count), top + 9, 0xFFFFFFFF, true);
            g.blitSprite(RenderPipelines.GUI_TEXTURED, STRIKE, x + COST_A + 7, top + 12, 9, 2);
        }
        ItemStack costB = offer.getCostB();
        if (!costB.isEmpty()) {
            g.fakeItem(costB, x + COST_B, top);
            g.itemDecorations(this.font, costB, x + COST_B, top);
        }
        g.blitSprite(RenderPipelines.GUI_TEXTURED, out ? TRADE_ARROW_OUT : TRADE_ARROW, x + ARROW, top + 3, 10, 9);
        ItemStack result = offer.getResult();
        g.fakeItem(result, x + RESULT, top);
        g.itemDecorations(this.font, result, x + RESULT, top);
        this.extractStock(g, x + STOCK, y + 8, offer);
        if (out) {
            g.fill(x + COST_A - 1, y + 1, x + RESULT + 17, y + ROW_H - 1, 0x60707070); // sold out: greyed
        }
    }

    /** A tick box: a dark edge, a white face, and a green tick when ticked. */
    private void extractBox(GuiGraphicsExtractor g, int x, int y, boolean ticked) {
        g.fill(x, y, x + 9, y + 9, 0xFF373737);
        g.fill(x + 1, y + 1, x + 8, y + 8, 0xFFF2F2F2);
        if (ticked) {
            int green = 0xFF2E7A22;
            int[][] tick = {{1, 4}, {2, 5}, {3, 6}, {4, 5}, {5, 4}, {6, 3}, {7, 2}};
            for (int[] p : tick) {
                g.fill(x + p[0], y + p[1] - 1, x + p[0] + 1, y + p[1] + 1, green);
            }
        }
    }

    /** How much of an offer is left before the villager restocks: a little gauge, green to red. */
    private void extractStock(GuiGraphicsExtractor g, int x, int y, MerchantOffer offer) {
        int max = Math.max(1, offer.getMaxUses());
        int left = Math.max(0, max - offer.getUses());
        g.fill(x, y, x + STOCK_W, y + 4, 0xFF373737);
        g.fill(x + 1, y + 1, x + STOCK_W - 1, y + 3, 0xFF5A5A5A);
        int w = left == 0 ? 0 : Math.max(1, (STOCK_W - 2) * left / max);
        int colour = left == 0 ? BAD : left * 4 <= max ? WAIT : GOOD;
        g.fill(x + 1, y + 1, x + 1 + w, y + 3, colour);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        g.text(this.font, this.title, this.titleLabelX, this.titleLabelY, LABEL, false);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, LABEL, false);
        TradingCounterView view = this.view();
        // the villager: its name and level, as its own screen titles it
        Component who = view.villager().<Component>map(name -> view.level() >= 1 && view.level() <= 5
                ? Component.translatable("merchant.title", name, Component.translatable("merchant.level." + view.level()))
                : name).orElse(Component.translatable("gui.nookworks.trading_counter.nobody").withColor(0x707070));
        fitCentered(g, who, RIGHT_X + RIGHT_W / 2, LIST_Y + 1, RIGHT_W);
        g.text(this.font, Component.translatable("gui.nookworks.trading_counter.payment"), TradingCounterMenu.PAY_X - 1,
                TradingCounterMenu.GRID_Y - 11, LABEL, false);
        g.text(this.font, Component.translatable("gui.nookworks.trading_counter.goods"), TradingCounterMenu.GOODS_X - 1,
                TradingCounterMenu.GRID_Y - 11, LABEL, false);
        if (view.villager().isPresent() && !view.offers().isEmpty()) { // (one with no trades has nothing to restock)
            int y = TradingCounterMenu.GRID_Y + 57;
            Component workstation = view.workstation()
                    ? Component.translatable("gui.nookworks.trading_counter.workstation.yes")
                    : Component.translatable("gui.nookworks.trading_counter.workstation.no").withColor(BAD & 0xFFFFFF);
            MachineStyle.fitText(g, this.font, workstation, RIGHT_X, y, RIGHT_W, LABEL, false);
            Component restocks = view.workstation()
                    ? Component.translatable("gui.nookworks.trading_counter.restocks", view.restocksLeft())
                    : Component.translatable("gui.nookworks.trading_counter.restocks.none");
            MachineStyle.fitText(g, this.font, restocks, RIGHT_X, y + 10, RIGHT_W, LABEL, false);
        }
        this.statusCut = MachineStyle.fitText(g, this.font, view.statusOf().text(), LIST_X + 11, STATUS_Y, this.imageWidth - LIST_X * 2 - 11,
                LABEL, false);
        MachineStyle.fitText(g, this.font, Component.translatable("gui.nookworks.trading_counter.pause_when_dear"), PAUSE_TEXT_X, PAUSE_Y + 1,
                RIGHT_W - 12, LABEL, false);
    }

    private void fitCentered(GuiGraphicsExtractor g, Component text, int middle, int y, int width) {
        int w = Math.min(width, this.font.width(text));
        MachineStyle.fitText(g, this.font, text, middle - w / 2, y, width, LABEL, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int row = this.rowAt(mouseX, mouseY);
        if (row >= 0 && this.menu.getCarried().isEmpty()) {
            MerchantOffer offer = this.view().offers().get(row);
            double dx = mouseX - this.leftPos - LIST_X;
            ItemStack shown = ItemStack.EMPTY;
            if (dx >= COST_A && dx < COST_A + 16) {
                shown = offer.getBaseCostA().copyWithCount(this.price(row, offer));
            } else if (dx >= COST_B && dx < COST_B + 16) {
                shown = offer.getCostB();
            } else if (dx >= RESULT && dx < RESULT + 16) {
                shown = offer.getResult();
            }
            if (!shown.isEmpty()) {
                g.setTooltipForNextFrame(this.font, shown, mouseX, mouseY);
                return;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(this.ticked(row) ? "gui.nookworks.trading_counter.ticked" : "gui.nookworks.trading_counter.tick"));
            int left = Math.max(0, offer.getMaxUses() - offer.getUses());
            lines.add(left == 0 ? Component.translatable("gui.nookworks.trading_counter.sold_out").withStyle(ChatFormatting.RED)
                    : Component.translatable("gui.nookworks.trading_counter.stock", left, offer.getMaxUses()).withStyle(ChatFormatting.GRAY));
            g.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.onPause(mouseX, mouseY) && this.menu.getCarried().isEmpty()) {
            // what each way does, the one it is set to in white
            boolean on = this.view().pauseWhenDear();
            List<FormattedCharSequence> lines = new ArrayList<>();
            lines.add(Component.translatable("gui.nookworks.trading_counter.pause_when_dear").getVisualOrderText());
            lines.addAll(this.font.split(Component.translatable("gui.nookworks.trading_counter.pause_when_dear.on")
                    .withStyle(on ? ChatFormatting.WHITE : ChatFormatting.GRAY), TIP_W));
            lines.addAll(this.font.split(Component.translatable("gui.nookworks.trading_counter.pause_when_dear.off")
                    .withStyle(on ? ChatFormatting.GRAY : ChatFormatting.WHITE), TIP_W));
            g.setTooltipForNextFrame(this.font, lines, mouseX, mouseY);
            return;
        }
        if (this.statusCut && this.isHovering(LIST_X, STATUS_Y - 2, this.imageWidth - LIST_X * 2, 12, mouseX, mouseY)) {
            g.setTooltipForNextFrame(this.font, this.view().statusOf().text(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }
}
