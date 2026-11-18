package dev.nookworks.client.gui.tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.nookworks.client.gui.MachineStyle;
import dev.nookworks.menu.tab.LiquidTab;
import dev.nookworks.menu.tab.MachineTab;
import dev.nookworks.menu.tab.MenuTabs;
import dev.nookworks.menu.tab.TabSlot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.nookworks.port.gui.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The screen's side of the machine tabs (the user's design, 2026-11-08/10, and their drawing): square tabs on the
 * panel's right edge, stacked - Settings (sliders), Liquid (a bucket), Add-on (a puzzle piece). A click on one opens it
 * as a panel growing out to the right, the tabs under it moving down; one is open at a time and a second click shuts
 * it. A tab with nothing in it is not drawn ({@link MenuTabs#has}).
 * <p>
 * Settings holds widgets the screen gives it ({@link #addSetting}: our redstone button, an add-on's slider), a row each
 * with its label after it; the Liquid tab its two slots, each with a timer dial beside it, and - with two liquids or
 * more in the machine - a button for each to say which the top slot draws, the chosen one lit; the Add-on tab the
 * slots an add-on registers (framed in brass as fittings) and its widgets. The tabs move and hide the widgets given
 * them.
 * <p>
 * The screen makes it in {@code init()} after {@code super.init()} and calls {@link #extractBackground} first in its
 * background (its panel, drawn after, covers the tabs' inner edge), {@link #mouseClicked} first in its
 * {@code mouseClicked}, {@link #inside} in {@code hasClickedOutside}, {@link #tooltip} with its own tooltips and
 * {@link #hint} for an empty Liquid slot. Public for add-ons' own screens (docs/compat.md).
 */
public final class ScreenTabs {
    /**
     * The most a Settings label is drawn wide, in at most {@link #LABEL_LINES} lines: more is cut short, whole on hover.
     * Kept narrow so a screen with the tab open still fits a 1280x720 window at the third GUI scale.
     */
    public static final int LABEL_ROOM = 76;
    public static final int LABEL_LINES = 2;
    /** The Liquid tab's choice buttons: their size, and how many to a row. */
    private static final int CHOICE = 13;
    private static final int CHOICES_PER_ROW = 3;

    /** What the Liquid tab's selector shows for each tank. */
    public interface LiquidChoices {
        /** Tank {@code tank}'s icon, 8x8, at (x, y). */
        void icon(GuiGraphicsExtractor g, int tank, int x, int y);

        /** Tank {@code tank}'s liquid, by name. */
        Component name(int tank);

        /** The faded item in the empty top slot. */
        default ItemStack drawHint() {
            return new ItemStack(Items.BUCKET);
        }

        /** The faded item in the empty bottom slot: a container of what the machine takes; empty for none. */
        default ItemStack pourHint() {
            return ItemStack.EMPTY;
        }
    }

    private record Row(AbstractWidget widget, @Nullable Supplier<Component> label) {}

    /** A tab as laid out this frame, relative to the panel: its top and its outer size (from the panel's edge). */
    private record Placed(MachineTab tab, int top, int width, int height) {}

    private final AbstractContainerScreen<?> screen;
    private final MenuTabs tabs;
    private final List<Row> settings = new ArrayList<>();
    private final List<AbstractWidget> addon = new ArrayList<>();
    private @Nullable LiquidChoices choices;
    private MachineStyle.Finish finish = MachineStyle.Finish.STEEL;
    private List<Placed> placed = List.of();
    /** How far left of where it would stand the screen's panel is, moved so for the open tab to fit ({@link #fit}). */
    private int shifted;
    /** Set while drawing: a Settings label cut short, and where, for its tooltip. */
    private final List<Rect2i> cutLabels = new ArrayList<>();
    private final List<Component> cutTexts = new ArrayList<>();

    public ScreenTabs(AbstractContainerScreen<?> screen, MenuTabs tabs) {
        this.screen = screen;
        this.tabs = tabs;
    }

    public MenuTabs menuTabs() {
        return this.tabs;
    }

    /**
     * Puts {@code widget}, which the screen has added ({@code addRenderableWidget}), in the Settings tab: a row of its
     * own, {@code label} (or null) drawn after it. The tabs place it and hide it while Settings is shut.
     */
    public <W extends AbstractWidget> W addSetting(W widget, @Nullable Supplier<Component> label) {
        this.tabs.enable(MachineTab.SETTINGS);
        this.settings.add(new Row(widget, label));
        widget.visible = false;
        return widget;
    }

    /** Puts {@code widget}, which the screen has added, in the Add-on tab under its slots; shown while it is open. */
    public <W extends AbstractWidget> W addAddonWidget(W widget) {
        this.tabs.enable(MachineTab.ADDON);
        this.addon.add(widget);
        widget.visible = false;
        return widget;
    }

    /** The tabs in the screen's own finish (the Repair Box's enamel), steel by default. */
    public ScreenTabs finish(MachineStyle.Finish finish) {
        this.finish = finish;
        return this;
    }

    /** The icons and names of the machine's tanks, for the Liquid tab's selector and hints. */
    public void liquidChoices(LiquidChoices choices) {
        this.choices = choices;
    }

    // ---- layout ----

    private int left() {
        return this.screen.getGuiLeft();
    }

    private int top() {
        return this.screen.getGuiTop();
    }

    private Font font() {
        return Minecraft.getInstance().font;
    }

    /** The tanks the selector shows: those with something to draw, when there are two or more. */
    private List<Integer> choiceTanks() {
        LiquidTab liquid = this.tabs.liquid();
        List<Integer> out = new ArrayList<>();
        if (liquid == null || this.choices == null || liquid.drawableCount() < 2) {
            return out;
        }
        for (int i = 0; i < LiquidTab.MAX_TANKS; i++) {
            if (liquid.drawable(i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** Where every tab stands this frame, and where the open one's widgets go (moved, and shown or hidden). */
    private void layout() {
        List<Placed> out = new ArrayList<>();
        int y = MenuTabs.TOP;
        int cx = this.tabs.contentX();
        for (MachineTab tab : this.tabs.shown()) {
            int width = MenuTabs.TAB;
            int height = MenuTabs.TAB;
            boolean open = this.tabs.isOpen(tab);
            int cy = y + 22;
            int bottom = y + MenuTabs.TAB - 5;
            int right = 0;
            switch (tab) {
                case SETTINGS -> {
                    int rowY = cy;
                    for (Row row : this.settings) {
                        AbstractWidget widget = row.widget();
                        widget.visible = open;
                        widget.setX(this.left() + cx);
                        widget.setY(this.top() + rowY);
                        int w = widget.getWidth();
                        int h = widget.getHeight();
                        if (row.label() != null) {
                            List<FormattedCharSequence> lines = this.lines(row.label().get());
                            int widest = 0;
                            for (FormattedCharSequence line : lines) {
                                widest = Math.max(widest, this.font().width(line));
                            }
                            w += 4 + widest;
                            h = Math.max(h, Math.min(lines.size(), LABEL_LINES) * 9);
                        }
                        widget.setY(this.top() + rowY + (h - widget.getHeight()) / 2);
                        right = Math.max(right, w);
                        bottom = Math.max(bottom, rowY + h);
                        rowY += h + 3;
                    }
                }
                case LIQUID -> {
                    right = 34;
                    LiquidTab liquid = this.tabs.liquid();
                    bottom = liquid != null && !liquid.pours() ? cy + 17 : cy + 37;
                    List<Integer> choices = this.choiceTanks();
                    if (!choices.isEmpty()) {
                        int columns = Math.min(CHOICES_PER_ROW, choices.size());
                        int rows = (choices.size() + CHOICES_PER_ROW - 1) / CHOICES_PER_ROW;
                        right = Math.max(right, columns * (CHOICE + 1) - 1);
                        bottom = (liquid.pours() ? cy + 40 : cy + 20) + rows * (CHOICE + 1) - 1;
                    }
                }
                case ADDON -> {
                    List<TabSlot> slots = this.tabs.slots(MachineTab.ADDON);
                    int rowY = cy;
                    if (!slots.isEmpty()) {
                        int columns = Math.min(MenuTabs.ADDON_COLUMNS, slots.size());
                        int rows = (slots.size() + MenuTabs.ADDON_COLUMNS - 1) / MenuTabs.ADDON_COLUMNS;
                        right = columns * 18 - 2;
                        rowY = cy + rows * 18 + 2;
                        bottom = rowY - 3;
                    }
                    for (AbstractWidget widget : this.addon) {
                        widget.visible = open;
                        widget.setX(this.left() + cx);
                        widget.setY(this.top() + rowY);
                        right = Math.max(right, widget.getWidth());
                        bottom = Math.max(bottom, rowY + widget.getHeight());
                        rowY += widget.getHeight() + 3;
                    }
                }
            }
            if (open) {
                width = Math.max(MenuTabs.TAB, right + 7);
                height = Math.max(MenuTabs.TAB, bottom - y + 5);
            }
            out.add(new Placed(tab, y, width, height));
            y += height + MenuTabs.GAP;
        }
        this.placed = out;
    }

    /**
     * Keeps the open tab inside the window. A tab opens out to the right of the panel, which stands in the middle: in a
     * small window, or at a large GUI scale, its far side ran off the screen, cut short (r22 CXP-003: 640x480 at the
     * second scale). Then the panel moves left as far as needs be - as vanilla's recipe book moves it - to the window's
     * left edge at most, and back as the tab shuts. Returns how far the panel is to move left now (less than nothing:
     * back right), {@code widgets} - the screen's own, set from its left edge - moved so far already: the screen takes
     * it off its left edge, {@code leftPos -= tabs.fit(children())}, at the end of {@code init} and first in its
     * background each frame. Public for add-ons' screens (docs/compat.md).
     */
    public int fit(List<? extends GuiEventListener> widgets) {
        this.layout();
        int open = 0;
        for (Placed tab : this.placed) {
            if (this.tabs.isOpen(tab.tab())) {
                open = Math.max(open, tab.width());
            }
        }
        int centred = this.left() + this.shifted;
        int over = centred + this.screen.getXSize() + open + 2 - this.screen.width;
        int want = open == 0 ? 0 : Math.max(0, Math.min(centred, over));
        int move = want - this.shifted;
        if (move != 0) {
            for (GuiEventListener widget : widgets) {
                if (widget instanceof AbstractWidget w) {
                    w.setX(w.getX() - move);
                }
            }
            this.shifted = want;
        }
        return move;
    }

    // ---- drawing ----

    /** Draws the tabs and what the open one holds. Call first in the screen's background, before its panel. */
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        this.layout();
        this.cutLabels.clear();
        this.cutTexts.clear();
        int x0 = this.left();
        int y0 = this.top();
        int edge = this.screen.getXSize();
        for (Placed tab : this.placed) {
            boolean open = this.tabs.isOpen(tab.tab());
            boolean overHead = this.overHead(tab, mouseX, mouseY);
            MachineStyle.tab(g, x0 + edge - 4, y0 + tab.top(), tab.width() + 4, tab.height(), this.finish, overHead);
            MachineStyle.tabIcon(g, tab.tab().ordinal(), x0 + edge + 2, y0 + tab.top() + 4);
            if (!open) {
                continue;
            }
            for (TabSlot slot : this.tabs.slots(tab.tab())) {
                if (!slot.isActive()) {
                    continue;
                }
                if (tab.tab() == MachineTab.ADDON) {
                    MachineStyle.fittingSlot(g, x0 + slot.x - 1, y0 + slot.y - 1, slot.hasItem());
                } else {
                    MachineStyle.slot(g, x0 + slot.x - 1, y0 + slot.y - 1, this.finish);
                }
            }
            switch (tab.tab()) {
                case SETTINGS -> this.drawLabels(g);
                case LIQUID -> this.drawLiquid(g, mouseX, mouseY);
                case ADDON -> {}
            }
        }
    }

    private void drawLabels(GuiGraphicsExtractor g) {
        for (Row row : this.settings) {
            if (row.label() == null) {
                continue;
            }
            AbstractWidget widget = row.widget();
            Component label = row.label().get();
            List<FormattedCharSequence> lines = this.lines(label);
            int shown = Math.min(lines.size(), LABEL_LINES);
            int x = widget.getX() + widget.getWidth() + 4;
            int y = widget.getY() + widget.getHeight() / 2 - shown * 9 / 2 + 1;
            for (int i = 0; i < shown; i++) {
                g.text(this.font(), lines.get(i), x, y + i * 9, MachineStyle.TEXT, false);
            }
            if (lines.size() > LABEL_LINES) {
                this.cutLabels.add(new Rect2i(x, y, LABEL_ROOM, shown * 9));
                this.cutTexts.add(label);
            }
        }
    }

    private void drawLiquid(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        LiquidTab liquid = this.tabs.liquid();
        if (liquid == null) {
            return;
        }
        for (int which : new int[] {LiquidTab.DRAW, LiquidTab.POUR}) {
            Slot slot = liquid.slot(which);
            if (!slot.isActive()) {
                continue;
            }
            MachineStyle.dial(g, this.left() + slot.x + 19, this.top() + slot.y + 1, liquid.progress(which) / (float) LiquidTab.TICKS);
        }
        List<Integer> tanks = this.choiceTanks();
        int chosen = liquid.chosen();
        for (int i = 0; i < tanks.size(); i++) {
            int[] at = this.choiceAt(i);
            boolean hover = mouseX >= at[0] && mouseX < at[0] + CHOICE && mouseY >= at[1] && mouseY < at[1] + CHOICE;
            MachineStyle.button(g, at[0], at[1], CHOICE, CHOICE, hover, true);
            if (tanks.get(i) == chosen) {
                MachineStyle.chosen(g, at[0], at[1], CHOICE, CHOICE);
            }
            this.choices.icon(g, tanks.get(i), at[0] + 3, at[1] + 3);
        }
    }

    /** A Settings label broken into lines {@link #LABEL_ROOM} wide. */
    private List<FormattedCharSequence> lines(Component label) {
        return this.font().split(label, LABEL_ROOM);
    }

    /** Where the {@code i}th choice button stands, on the screen. */
    private int[] choiceAt(int i) {
        LiquidTab liquid = this.tabs.liquid();
        int x = this.left() + this.tabs.contentX() + i % CHOICES_PER_ROW * (CHOICE + 1);
        int y = this.top() + liquid.slot(liquid.pours() ? LiquidTab.POUR : LiquidTab.DRAW).y + 20 + i / CHOICES_PER_ROW * (CHOICE + 1);
        return new int[] {x, y};
    }

    private boolean overHead(Placed tab, double mouseX, double mouseY) {
        int x = this.left() + this.screen.getXSize();
        int y = this.top() + tab.top();
        return mouseX >= x && mouseX < x + tab.width() && mouseY >= y && mouseY < y + MenuTabs.TAB;
    }

    // ---- input ----

    /**
     * A click on a tab's head opens it (or shuts it, open), on a choice button picks the liquid drawn; true if it was
     * the tabs'. Call first in the screen's {@code mouseClicked}.
     */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        this.layout();
        for (Placed tab : this.placed) {
            if (this.overHead(tab, event.x(), event.y())) {
                MachineTab now = this.tabs.isOpen(tab.tab()) ? null : tab.tab();
                this.tabs.setOpen(now);
                this.press(MenuTabs.button(now));
                this.layout();
                Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        LiquidTab liquid = this.tabs.liquid();
        if (liquid != null && this.tabs.isOpen(MachineTab.LIQUID)) {
            List<Integer> tanks = this.choiceTanks();
            for (int i = 0; i < tanks.size(); i++) {
                int[] at = this.choiceAt(i);
                if (event.x() >= at[0] && event.x() < at[0] + CHOICE && event.y() >= at[1] && event.y() < at[1] + CHOICE) {
                    liquid.choose(tanks.get(i));
                    this.press(MenuTabs.BUTTON_LIQUID_CHOICE + tanks.get(i));
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }
        }
        return false;
    }

    private void press(int button) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(this.screen.getMenu().containerId, button);
        }
    }

    /** Whether (x, y) on the screen is on a tab: a click there is not a click outside the screen (no item thrown). */
    public boolean inside(double x, double y) {
        for (Rect2i area : this.areas()) {
            if (x >= area.getX() && x < area.getX() + area.getWidth() && y >= area.getY() && y < area.getY() + area.getHeight()) {
                return true;
            }
        }
        return false;
    }

    /** The tabs as they stand, on the screen: for JEI to keep its list off, and for {@link #inside}. */
    public List<Rect2i> areas() {
        this.layout();
        List<Rect2i> out = new ArrayList<>();
        for (Placed tab : this.placed) {
            out.add(new Rect2i(this.left() + this.screen.getXSize(), this.top() + tab.top(), tab.width(), tab.height()));
        }
        return out;
    }

    // ---- tooltips and hints ----

    /** What the tabs have to say at (mouseX, mouseY): empty for nothing. */
    public List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        for (Placed tab : this.placed) {
            if (this.overHead(tab, mouseX, mouseY)) {
                lines.add(Component.translatable(tab.tab().translationKey()));
                lines.add(Component.translatable(this.tabs.isOpen(tab.tab()) ? "gui.nookworks.tab.close" : "gui.nookworks.tab.open")
                        .withStyle(ChatFormatting.GRAY));
                return lines;
            }
        }
        for (int i = 0; i < this.cutLabels.size(); i++) {
            Rect2i area = this.cutLabels.get(i);
            if (mouseX >= area.getX() && mouseX < area.getX() + area.getWidth() && mouseY >= area.getY() && mouseY < area.getY() + area.getHeight()) {
                lines.add(this.cutTexts.get(i));
                return lines;
            }
        }
        LiquidTab liquid = this.tabs.liquid();
        if (liquid == null || !this.tabs.isOpen(MachineTab.LIQUID)) {
            return lines;
        }
        List<Integer> tanks = this.choiceTanks();
        for (int i = 0; i < tanks.size(); i++) {
            int[] at = this.choiceAt(i);
            if (mouseX >= at[0] && mouseX < at[0] + CHOICE && mouseY >= at[1] && mouseY < at[1] + CHOICE) {
                lines.add(Component.translatable("gui.nookworks.tab.liquid.choose", this.choices.name(tanks.get(i))));
                return lines;
            }
        }
        for (int which : new int[] {LiquidTab.DRAW, LiquidTab.POUR}) {
            Slot slot = liquid.slot(which);
            if (!slot.isActive()) {
                continue;
            }
            int x = this.left() + slot.x;
            int y = this.top() + slot.y;
            boolean overSlot = mouseX >= x - 1 && mouseX < x + 17 && mouseY >= y - 1 && mouseY < y + 17;
            boolean overDial = mouseX >= x + 19 && mouseX < x + 19 + MachineStyle.DIAL && mouseY >= y + 1 && mouseY < y + 1 + MachineStyle.DIAL;
            if (overDial) {
                String key = which == LiquidTab.DRAW ? "gui.nookworks.tab.liquid.filling" : "gui.nookworks.tab.liquid.pouring";
                lines.add(Component.translatable(key, String.format(Locale.ROOT, "%d", liquid.progress(which) * 100 / LiquidTab.TICKS)));
                return lines;
            }
            if (overSlot && !slot.hasItem()) {
                lines.add(Component.translatable(which == LiquidTab.DRAW ? "gui.nookworks.tab.liquid.draw" : "gui.nookworks.tab.liquid.pour"));
                if (which == LiquidTab.DRAW && this.choices != null) {
                    int tank = liquid.chosen();
                    lines.add((tank < 0 ? Component.translatable("gui.nookworks.tab.liquid.nothing")
                            : Component.translatable("gui.nookworks.tab.liquid.draws", this.choices.name(tank))).withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("gui.nookworks.tab.liquid.by_hand").withStyle(ChatFormatting.DARK_GRAY));
                return lines;
            }
        }
        return lines;
    }

    /** The faded item an empty slot of the Liquid tab shows; empty for any other slot. */
    public ItemStack hint(Slot slot) {
        LiquidTab liquid = this.tabs.liquid();
        if (liquid == null || this.choices == null || slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        if (slot == liquid.slot(LiquidTab.DRAW)) {
            return this.choices.drawHint();
        }
        return slot == liquid.slot(LiquidTab.POUR) ? this.choices.pourHint() : ItemStack.EMPTY;
    }
}
