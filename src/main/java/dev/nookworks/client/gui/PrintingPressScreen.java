package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.client.PhotoTextures;
import dev.nookworks.label.LabelText;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.PosterVariant;
import dev.nookworks.poster.Posters;
import dev.nookworks.poster.PrintingPressBlockEntity;
import dev.nookworks.poster.PrintingPressMenu;
import dev.nookworks.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Printing Press GUI. Print starts the job, and each ink slot is locked to one process colour so the hopper and the
 * player put the same things in the same places. Under them, two pages, Posters and Labels, one of them chosen at a
 * time: posters are a scrolling grid of their own artwork; labels are a sample slot (what the labels will show) with,
 * beside it, a label stuck on the side of a barrel as it will look, how many a print makes, and a box to type letters
 * in - typed, the labels say them instead of showing the sample.
 * <p>
 * Between them, Photos: the photos in the cameras the player carries, newest first, a click choosing one; beside them the
 * print as it will come out, and the sizes it prints at. With no camera, or none with a photo, the page says so.
 */
public class PrintingPressScreen extends AbstractContainerScreen<PrintingPressMenu> {
    private static final int CELL = 24;
    private static final int COLUMNS = 6;
    /** Two rows, so the whole screen stays 224 px tall and fits a 240 px GUI (a small window at GUI scale 2 or 3). */
    private static final int ROWS = 2;
    private static final int GRID_X = 8;
    /** The page tabs, over the page. */
    private static final int TAB_Y = 60;
    private static final int TAB_W = 52;
    private static final int TAB_H = 14;
    private static final int GRID_Y = TAB_Y + TAB_H + 2;
    private static final int BAY_BOTTOM = GRID_Y + ROWS * 24 + 4;
    /** The labels page: the preview of a barrel side, with the label on it, at three pixels a pixel. */
    private static final int MOCK_X = 60;
    private static final int MOCK_Y = GRID_Y + 1;
    private static final int MOCK = 48;
    private static final Identifier BARREL_SIDE = Identifier.withDefaultNamespace("textures/block/barrel_side.png");
    private static final Identifier CARD = Nookworks.id("textures/entity/label.png");
    /** The Print button and, beside it, the display saying what the press is doing (or what it lacks). */
    private static final int ROW_Y = 40;
    private static final int DISPLAY_X = 66;
    private static final int DISPLAY_W = 88;
    private static final int DISPLAY_H = 18;
    private static final int BAR_X = GRID_X + COLUMNS * CELL + 2;
    private static final int BAR_W = 12;

    private static final int SELECTED = 0xFFE2C27A;
    private static final int HOVER = 0x60FFFFFF;
    private static final int DIM = 0x99202020;
    private static final int ENERGY_H = 30;
    /** The box for letter labels, right of the preview, at its foot. */
    private static final int TEXT_X = MOCK_X + MOCK + 6;
    private static final int TEXT_Y = MOCK_Y + MOCK - 14;
    private static final int TEXT_W = 54;
    private static final int INK = 0xFF26221E;
    /** The photos page: three columns of the camera's photos, then the print as it will be, then its sizes. */
    private static final int THUMB_W = 16;
    private static final int THUMB_H = 12;
    private static final int THUMB_COLUMNS = 3;
    private static final int THUMB_ROWS = 3;
    private static final int THUMB_PITCH_X = THUMB_W + 3;
    private static final int THUMB_PITCH_Y = THUMB_H + 4;
    private static final int PREVIEW_X = GRID_X + THUMB_COLUMNS * THUMB_PITCH_X + 5;
    private static final int PREVIEW_W = 44;
    private static final int PREVIEW_H = ROWS * CELL;
    private static final int SIZE_X = PREVIEW_X + PREVIEW_W + 4;
    private static final int SIZE_W = 24;
    private static final int SIZE_H = 11;
    private static final int PAPER = 0xFFEDE9DD;
    private boolean statusCut;

    /** The pages, in the order of their tabs. */
    private enum Page {
        POSTERS("gui.nookworks.printing_press.page.posters"),
        PHOTOS("gui.nookworks.printing_press.page.photos"),
        LABELS("gui.nookworks.printing_press.page.labels");

        final String name;

        Page(String name) {
            this.name = name;
        }
    }

    private int scroll;
    private boolean dragging;
    /** How far below the thumb's top the pointer took hold of it, kept while it is dragged. */
    private int grab;
    private SteelButton printButton;
    private EditBox letters;
    /** Set while the box is being filled in from the press, so that is not sent back. */
    private boolean syncing;
    /** Which page is open. Follows what the press is set to; a tab changes both. */
    private Page page = Page.POSTERS;
    /** Rows of photos scrolled past (more than nine with two cameras). */
    private int photoScroll;
    /** Ticks left before the page follows the press again after a tab was clicked (the server has yet to answer). */
    private int settling;

    public PrintingPressScreen(PrintingPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, PrintingPressMenu.INVENTORY_Y + 82);
        this.inventoryLabelY = PrintingPressMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void init() {
        super.init();
        this.page = pageOf(this.menu.selected());
        this.menu.labelPage = this.page == Page.LABELS;
        this.printButton = this.addRenderableWidget(new SteelButton(this.leftPos + 8, this.topPos + ROW_Y, 54, DISPLAY_H, this.printLabel(),
                () -> this.press(PrintingPressMenu.BUTTON_PRINT)));
        this.letters = new EditBox(this.font, this.leftPos + TEXT_X, this.topPos + TEXT_Y, TEXT_W, 14,
                Component.translatable("gui.nookworks.printing_press.letters"));
        this.letters.setMaxLength(LabelText.MAX);
        this.letters.setHint(Component.literal("ABC"));
        this.letters.setResponder(this::typed);
        this.syncing = true;
        this.letters.setValue(this.menu.labelText());
        this.syncing = false;
        this.letters.visible = this.page == Page.LABELS;
        this.addRenderableWidget(this.letters);
        this.addRenderableWidget(new RedstoneButton(this.leftPos + this.imageWidth - 8 - RedstoneButton.SIZE, this.topPos + 3,
                this.menu::redstoneMode, () -> this.press(PrintingPressMenu.BUTTON_REDSTONE)));
    }

    /** The box changed: keep what it may say, and send it to the press, a letter a button. */
    private void typed(String value) {
        StringBuilder kept = new StringBuilder();
        for (char c : value.toCharArray()) {
            if (LabelText.allowed(c) && kept.length() < LabelText.MAX) {
                kept.append(c);
            }
        }
        if (!kept.toString().equals(value)) {
            this.letters.setValue(kept.toString()); // comes back here with the kept letters
            return;
        }
        if (this.syncing) {
            return;
        }
        this.press(PrintingPressMenu.BUTTON_TEXT_CLEAR);
        for (char c : value.toCharArray()) {
            this.press(PrintingPressMenu.letterButton(c));
        }
    }

    /** What the labels will say: what is in the box, as the press will keep it. */
    private String text() {
        return this.letters == null ? this.menu.labelText() : LabelText.clean(this.letters.getValue());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!event.isEscape() && this.letters.visible && (this.letters.keyPressed(event) || this.letters.canConsumeInput())) {
            return true; // typing in the box: E is a letter, not "close the inventory"
        }
        return super.keyPressed(event);
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    private Component printLabel() {
        return Component.translatable(this.menu.running() ? "gui.nookworks.printing_press.stop" : "gui.nookworks.printing_press.print");
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        Identifier selected = this.menu.selected();
        if (this.settling > 0) {
            this.settling--;
        } else if (selected != null) { // someone else chose, or a click was taken: the page follows the press
            this.page = pageOf(selected);
        }
        this.menu.labelPage = this.page == Page.LABELS;
        this.letters.visible = this.page == Page.LABELS;
        if (!this.letters.isFocused() && !this.text().equals(this.menu.labelText())) {
            this.syncing = true; // someone else typed, or the screen has just opened
            this.letters.setValue(this.menu.labelText());
            this.syncing = false;
        }
        this.printButton.setMessage(this.printLabel());
        // off for a photo that cannot print, whatever is loaded: kept from this player, gone, or photos turned off
        this.printButton.active = this.menu.running()
                || this.menu.selected() != null && !this.photoHidden() && !this.photoGone() && !this.photosOff();
    }

    /** The posters page's designs: every entry but labels and the photo. */
    private List<Identifier> designs() {
        List<Identifier> all = this.menu.designs();
        return all.stream().filter(id -> !isLabels(id) && !PrintingPressBlockEntity.PHOTO.equals(id)).toList();
    }

    private static Page pageOf(@Nullable Identifier selected) {
        return isLabels(selected) ? Page.LABELS : PrintingPressBlockEntity.PHOTO.equals(selected) ? Page.PHOTOS : Page.POSTERS;
    }

    /** Selects (or, chosen already, unselects) the entry {@code id}: button ids are the menu's index, plus one. */
    private void choose(Identifier id) {
        this.press(this.menu.designs().indexOf(id) + 1);
    }

    private void openPage(Page page) {
        if (page == this.page) {
            return;
        }
        this.page = page;
        this.menu.labelPage = page == Page.LABELS;
        this.settling = 10;
        Identifier selected = this.menu.selected();
        if (page == Page.LABELS) {
            if (!isLabels(selected)) {
                this.choose(PrintingPressBlockEntity.LABEL); // labels chosen on their page
            }
        } else if (selected != null && pageOf(selected) != page) {
            this.choose(selected); // what another page chose let go of: chosen again, it is unchosen
        }
    }

    private boolean onTab(double mouseX, double mouseY, int tab) {
        int x = this.leftPos + GRID_X + tab * (TAB_W + 2);
        int y = this.topPos + TAB_Y;
        return mouseX >= x && mouseX < x + TAB_W && mouseY >= y && mouseY < y + TAB_H;
    }

    private int maxScroll() {
        return this.page != Page.POSTERS ? 0 : Math.max(0, (this.designs().size() + COLUMNS - 1) / COLUMNS - ROWS);
    }

    /** Index of the design under the mouse, or -1. */
    private int cellAt(double mouseX, double mouseY) {
        if (this.page != Page.POSTERS) {
            return -1;
        }
        int x = (int) (mouseX - this.leftPos - GRID_X);
        int y = (int) (mouseY - this.topPos - GRID_Y);
        if (x < 0 || y < 0 || x >= COLUMNS * CELL || y >= ROWS * CELL) {
            return -1;
        }
        int index = (this.scroll + y / CELL) * COLUMNS + x / CELL;
        return index < this.designs().size() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.onBar(event.x(), event.y())) {
            this.dragging = true;
            // the thumb is taken where it is held, so a click on it moves nothing; a click beside it brings it there
            int top = this.thumbY();
            if (event.y() >= top && event.y() < top + this.thumbH()) {
                this.grab = (int) event.y() - top;
            } else {
                this.grab = this.thumbH() / 2;
                this.dragTo(event.y());
            }
            return true;
        }
        for (Page tab : Page.values()) {
            if (this.onTab(event.x(), event.y(), tab.ordinal())) {
                if (tab != this.page) {
                    this.clickSound();
                }
                this.openPage(tab);
                return true;
            }
        }
        if (this.page == Page.PHOTOS) {
            int photo = this.photoAt(event.x(), event.y());
            if (photo > 0) {
                this.pickSound();
                this.press(PrintingPressMenu.photoButton(photo));
                return true;
            }
            Photo.Size size = this.sizeAt(event.x(), event.y());
            if (size != null) {
                this.clickSound();
                this.press(PrintingPressMenu.sizeButton(size));
                return true;
            }
        }
        int index = this.cellAt(event.x(), event.y());
        if (index >= 0) {
            this.pickSound();
            this.choose(this.designs().get(index));
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.dragging) {
            this.dragTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.page == Page.PHOTOS && this.maxPhotoScroll() > 0 && this.isHovering(GRID_X, GRID_Y, THUMB_COLUMNS * THUMB_PITCH_X,
                PREVIEW_H, mouseX, mouseY)) {
            this.photoScroll = Math.max(0, Math.min(this.maxPhotoScroll(), this.photoScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (this.maxScroll() > 0 && this.overDesigns(mouseX, mouseY)) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean overDesigns(double mouseX, double mouseY) {
        int x = (int) (mouseX - this.leftPos - GRID_X);
        int y = (int) (mouseY - this.topPos - GRID_Y);
        return x >= 0 && y >= 0 && x < COLUMNS * CELL + BAR_W + 4 && y < ROWS * CELL;
    }

    private boolean onBar(double x, double y) {
        return this.maxScroll() > 0 && x >= this.leftPos + BAR_X && x < this.leftPos + BAR_X + BAR_W
                && y >= this.topPos + GRID_Y && y < this.topPos + GRID_Y + ROWS * CELL;
    }

    /** Puts the thumb's top {@link #grab} above the pointer, as near as a row allows. */
    private void dragTo(double y) {
        int free = ROWS * CELL - this.thumbH();
        double f = free <= 0 ? 0 : (y - this.grab - this.topPos - GRID_Y) / free;
        this.scroll = Math.max(0, Math.min(this.maxScroll(), (int) Math.round(f * this.maxScroll())));
    }

    private int thumbH() {
        int trackH = ROWS * CELL;
        int rows = Math.max(1, (this.designs().size() + COLUMNS - 1) / COLUMNS);
        return this.maxScroll() == 0 ? trackH : Math.max(12, trackH * ROWS / rows);
    }

    private int thumbY() {
        int max = this.maxScroll();
        return this.topPos + GRID_Y + (max == 0 ? 0 : (ROWS * CELL - this.thumbH()) * this.scroll / max);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, BAY_BOTTOM - 16);
        for (Slot slot : this.menu.slots) {
            if (slot.isActive()) { // the label page's sample slot is not framed among the poster page's pictures
                MachineStyle.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
        int dx = this.leftPos + DISPLAY_X;
        int dy = this.topPos + ROW_Y;
        MachineStyle.display(graphics, dx, dy, DISPLAY_W, DISPLAY_H, this.tone());
        if (this.menu.running()) { // the display fills with brass as the copy comes off the press
            int filled = (DISPLAY_W - 4) * this.menu.progressPercent() / 100;
            graphics.fill(dx + 2, dy + DISPLAY_H - 4, dx + 2 + filled, dy + DISPLAY_H - 2, 0xFF8A6A2C);
        }
        Item missing = this.missingNow();
        if (missing != null) {
            graphics.item(new ItemStack(missing), dx + 11, dy + 1);
        }
        int energy = this.menu.energy();
        MachineStatus.Tone tone = energy < MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK) ? MachineStatus.Tone.BAD
                : energy < PrintingPressBlockEntity.CAPACITY / 5 ? MachineStatus.Tone.WARN : MachineStatus.Tone.GOOD;
        MachineStyle.icon(graphics, MachineStyle.Icon.ENERGY, this.leftPos + this.energyX() + 1, this.topPos + this.energyY() - 10, energy <= 0);
        MachineStyle.energy(graphics, this.leftPos + this.energyX(), this.topPos + this.energyY(), ENERGY_H, energy,
                PrintingPressBlockEntity.CAPACITY, tone);

        this.drawTabs(graphics, mouseX, mouseY);
        switch (this.page) {
            case LABELS -> this.drawLabelPage(graphics);
            case PHOTOS -> this.drawPhotoPage(graphics, mouseX, mouseY);
            case POSTERS -> {
                this.drawDesigns(graphics, mouseX, mouseY);
                this.drawScrollBar(graphics);
            }
        }
    }

    /** Posters, Photos and Labels, the open one in brass and joined to its page. */
    private void drawTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (int tab = 0; tab < Page.values().length; tab++) {
            int x = this.leftPos + GRID_X + tab * (TAB_W + 2);
            int y = this.topPos + TAB_Y;
            boolean open = tab == this.page.ordinal();
            if (open) {
                graphics.fill(x - 1, y - 1, x + TAB_W + 1, y + TAB_H + 1, SELECTED);
            }
            MachineStyle.button(graphics, x, y, TAB_W, TAB_H, !open && this.onTab(mouseX, mouseY, tab), true);
        }
    }

    /** The labels page: the sample's frame, an arrow, and the label on a barrel's side as the sample would print it. */
    private void drawLabelPage(GuiGraphicsExtractor graphics) {
        int x = this.leftPos + MOCK_X;
        int y = this.topPos + MOCK_Y;
        String text = this.text();
        // an arrow from the sample to the preview, unless letters are printed instead
        int ax = this.leftPos + PrintingPressMenu.SAMPLE_X + 22;
        int ay = this.topPos + PrintingPressMenu.SAMPLE_Y + 7;
        if (text.isEmpty()) {
            graphics.fill(ax, ay, ax + 12, ay + 2, MachineStyle.DIM);
            for (int i = 0; i < 4; i++) {
                graphics.fill(ax + 12 + i, ay - 3 + i, ax + 13 + i, ay + 5 - i, MachineStyle.DIM);
            }
        }
        graphics.fill(x - 2, y - 2, x + MOCK + 2, y + MOCK + 2, 0xFF1A1C20);
        graphics.blit(BARREL_SIDE, x, y, x + MOCK, y + MOCK, 0.0F, 1.0F, 0.0F, 1.0F);
        ItemStack sample = this.menu.container().getItem(PrintingPressBlockEntity.SAMPLE);
        // the card is 8x6 of the barrel's 16 pixels, in the middle of its side, its picture 5 pixels across
        int cx = x + 12;
        int cy = y + 15;
        graphics.blit(CARD, cx, cy, cx + 24, cy + 18, 0.0F, 8 / 16F, 0.0F, 6 / 16F);
        if (!text.isEmpty()) { // as the card in the world prints it: 7 of its 8 pixels across at most, 3.5 high
            int width = this.font.width(text);
            int guiScale = Math.max(1, this.minecraft.getWindow().getGuiScale()); // whole screen pixels, or letters lose columns
            float scale = Math.max(1, (int) Math.min(21F * guiScale / width, guiScale * 1.5F)) / (float) guiScale;
            graphics.nextStratum();
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx + 12, cy + 9);
            graphics.pose().scale(scale, scale);
            graphics.text(this.font, text, -width / 2, -4, INK, false);
            graphics.pose().popMatrix();
        } else if (!sample.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(cx + 12 - 7.5F, cy + 9 - 7.5F);
            graphics.pose().scale(15 / 16F, 15 / 16F);
            graphics.fakeItem(sample, 0, 0);
            graphics.pose().popMatrix();
        } else {
            graphics.nextStratum();
            graphics.fill(x, y, x + MOCK, y + MOCK, DIM); // nothing to print from yet
        }
    }

    /** The photos the cameras in the player's inventory hold, newest first. */
    private List<Integer> photos() {
        return PrintingPressMenu.photos(this.minecraft.player.getInventory());
    }

    private int maxPhotoScroll() {
        return Math.max(0, (this.photos().size() + THUMB_COLUMNS - 1) / THUMB_COLUMNS - THUMB_ROWS);
    }

    /** The photo under the mouse, by number, or 0. */
    private int photoAt(double mouseX, double mouseY) {
        int x = (int) (mouseX - this.leftPos - GRID_X);
        int y = (int) (mouseY - this.topPos - GRID_Y);
        if (x < 0 || y < 0 || x % THUMB_PITCH_X >= THUMB_W + 2 || y % THUMB_PITCH_Y >= THUMB_H + 2) {
            return 0;
        }
        int column = x / THUMB_PITCH_X;
        int row = y / THUMB_PITCH_Y;
        if (column >= THUMB_COLUMNS || row >= THUMB_ROWS) {
            return 0;
        }
        List<Integer> photos = this.photos();
        int index = (this.photoScroll + row) * THUMB_COLUMNS + column;
        return index < photos.size() ? photos.get(index) : 0;
    }

    /** The size button under the mouse, or null. */
    private Photo.@Nullable Size sizeAt(double mouseX, double mouseY) {
        for (int i = 0; i < Photo.SIZES.size(); i++) {
            if (this.isHovering(SIZE_X + i % 2 * (SIZE_W + 1), GRID_Y + i / 2 * (SIZE_H + 1), SIZE_W, SIZE_H, mouseX, mouseY)) {
                return Photo.SIZES.get(i);
            }
        }
        return null;
    }

    /** The photo the press is set to print, by number, or 0. */
    private int chosenPhoto() {
        return this.menu.printingPhoto() ? this.menu.photo() : 0;
    }

    /** The photos page: the camera's photos, the print as it will come out, and the sizes. */
    private void drawPhotoPage(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Integer> photos = this.photos();
        int chosen = this.chosenPhoto();
        this.photoScroll = Math.min(this.photoScroll, this.maxPhotoScroll());
        if (photos.isEmpty() && chosen == 0) {
            return; // the page says why, in its labels
        }
        int hovered = this.photoAt(mouseX, mouseY);
        for (int row = 0; row < THUMB_ROWS; row++) {
            for (int column = 0; column < THUMB_COLUMNS; column++) {
                int index = (this.photoScroll + row) * THUMB_COLUMNS + column;
                int x = this.leftPos + GRID_X + column * THUMB_PITCH_X;
                int y = this.topPos + GRID_Y + row * THUMB_PITCH_Y;
                int photo = index < photos.size() ? photos.get(index) : 0;
                if (photo != 0 && photo == chosen) {
                    graphics.fill(x - 1, y - 1, x + THUMB_W + 3, y + THUMB_H + 3, SELECTED);
                }
                MachineStyle.well(graphics, x, y, THUMB_W + 2, THUMB_H + 2);
                if (photo != 0) {
                    graphics.blit(PhotoTextures.textureOrBlank(photo, Photo.SIZES.getFirst()), x + 1, y + 1, x + 1 + THUMB_W, y + 1 + THUMB_H,
                            0.0F, 1.0F, 0.0F, 1.0F);
                }
            }
        }
        // the print as it will be: the photo cropped to the size, on its paper
        int px = this.leftPos + PREVIEW_X;
        int py = this.topPos + GRID_Y;
        MachineStyle.well(graphics, px, py, PREVIEW_W, PREVIEW_H);
        Photo.Size size = this.menu.photoSize();
        if (chosen != 0) {
            int room = PREVIEW_W - 6;
            int w = Math.min(room, (PREVIEW_H - 6) * size.width() / size.height());
            int h = w * size.height() / size.width();
            int x0 = px + (PREVIEW_W - w) / 2;
            int y0 = py + (PREVIEW_H - h) / 2;
            graphics.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + h + 1, PAPER);
            graphics.blit(PhotoTextures.textureOrBlank(chosen, size), x0, y0, x0 + w, y0 + h, 0.0F, 1.0F, 0.0F, 1.0F);
        }
        Photo.Size hoveredSize = this.sizeAt(mouseX, mouseY);
        for (int i = 0; i < Photo.SIZES.size(); i++) {
            Photo.Size each = Photo.SIZES.get(i);
            int x = this.leftPos + SIZE_X + i % 2 * (SIZE_W + 1);
            int y = this.topPos + GRID_Y + i / 2 * (SIZE_H + 1);
            if (each.equals(size)) {
                graphics.fill(x - 1, y - 1, x + SIZE_W + 1, y + SIZE_H + 1, SELECTED);
            }
            MachineStyle.button(graphics, x, y, SIZE_W, SIZE_H, each.equals(hoveredSize) && !each.equals(size), true);
        }
        graphics.nextStratum();
        for (int row = 0; row < THUMB_ROWS; row++) {
            for (int column = 0; column < THUMB_COLUMNS; column++) {
                int index = (this.photoScroll + row) * THUMB_COLUMNS + column;
                if (index < photos.size() && photos.get(index) == hovered) {
                    int x = this.leftPos + GRID_X + column * THUMB_PITCH_X + 1;
                    int y = this.topPos + GRID_Y + row * THUMB_PITCH_Y + 1;
                    graphics.fill(x, y, x + THUMB_W, y + THUMB_H, HOVER);
                }
            }
        }
    }

    private int energyX() {
        return this.imageWidth - 17;
    }

    private int energyY() {
        return 29;
    }

    /** The ink the chosen design lacks right now, shown on the display; null when nothing is missing. */
    private @Nullable Item missingNow() {
        Identifier selected = this.menu.selected();
        if (selected == null || this.menu.running()) {
            return null;
        }
        List<Item> missing = this.missingFor(selected);
        return missing.isEmpty() ? null : missing.get(0);
    }

    private static boolean isLabels(@Nullable Identifier id) {
        return PrintingPressBlockEntity.LABEL.equals(id);
    }

    /** Every ink a copy of the entry {@code id} lacks; none for a design the pack no longer has. */
    private List<Item> missingFor(Identifier id) {
        if (PrintingPressBlockEntity.PHOTO.equals(id)) {
            return PrintingPressBlockEntity.missing(this.menu.container(), PosterVariant.PROCESS_INKS);
        }
        if (isLabels(id)) {
            return PrintingPressBlockEntity.missing(this.menu.container(), PrintingPressBlockEntity.labelInks(this.menu.container(), this.text()));
        }
        PosterVariant design = this.design(id);
        return design == null ? List.of() : PrintingPressBlockEntity.missing(this.menu.container(), design);
    }

    /** What a copy of the entry {@code id} would put in the output; empty for labels with no sample. */
    private ItemStack copyOf(Identifier id) {
        if (PrintingPressBlockEntity.PHOTO.equals(id)) {
            return this.menu.photo() > 0 ? Posters.item(PhotoPrints.id(this.menu.photo(), this.menu.photoSize())) : ItemStack.EMPTY;
        }
        return isLabels(id) ? PrintingPressBlockEntity.labelsOf(this.menu.container(), this.text()) : Posters.item(id);
    }

    /** Whether the photo the press is set to print is gone - let go of, or taken out - as the server says. */
    private boolean photoGone() {
        return this.menu.printingPhoto() && PhotoTextures.status(this.menu.photo()) == NookworksNetwork.PhotoPixels.GONE;
    }

    /** Whether the photo the press is set to print is not this player's to see, nor so to print (another's, left chosen). */
    private boolean photoHidden() {
        return this.menu.printingPhoto() && PhotoTextures.status(this.menu.photo()) == NookworksNetwork.PhotoPixels.HIDDEN;
    }

    /** Whether the server shows no photos at all, with a photo chosen. */
    private boolean photosOff() {
        return this.menu.printingPhoto() && PhotoTextures.status(this.menu.photo()) == NookworksNetwork.PhotoPixels.OFF;
    }

    /** The sound of a pick in the press's own lists, as the loom's pattern list makes. */
    private void pickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
    }

    /** The sound of one of its own buttons, as any button makes. */
    private void clickSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** Whether what is in the output is in the way of a copy of {@code id}. */
    private boolean outputBlocked(Identifier id) {
        ItemStack copy = this.copyOf(id);
        return !copy.isEmpty() && !PrintingPressBlockEntity.roomFor(this.menu.container(), copy);
    }

    private MachineStatus.Tone tone() {
        if (this.menu.running()) {
            return this.menu.heldByRedstone() ? MachineStatus.Tone.QUIET
                    : this.menu.energy() < MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK) ? MachineStatus.Tone.BAD : MachineStatus.Tone.GOOD;
        }
        Identifier selected = this.menu.selected();
        if (selected == null) {
            return MachineStatus.Tone.QUIET;
        }
        return this.missingNow() != null || this.menu.container().getItem(PrintingPressBlockEntity.PAPER).isEmpty()
                || isLabels(selected) && this.copyOf(selected).isEmpty() || this.photoGone() || this.photoHidden() || this.photosOff()
                || this.outputBlocked(selected)
                ? MachineStatus.Tone.WARN
                : this.menu.energy() < MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK) ? MachineStatus.Tone.BAD : MachineStatus.Tone.GOOD;
    }

    private void drawScrollBar(GuiGraphicsExtractor graphics) {
        int max = this.maxScroll();
        int trackX = this.leftPos + BAR_X;
        int trackY = this.topPos + GRID_Y;
        int trackH = ROWS * CELL;
        MachineStyle.well(graphics, trackX, trackY, BAR_W, trackH);
        int thumbH = this.thumbH();
        int thumbY = this.thumbY();
        if (max == 0) {
            return; // everything fits: no thumb to grab
        }
        MachineStyle.button(graphics, trackX + 1, thumbY + 1, BAR_W - 2, thumbH - 2, this.dragging, true);
        for (int i = -2; i <= 2; i += 2) { // grip lines across the thumb
            graphics.fill(trackX + 3, thumbY + thumbH / 2 + i, trackX + BAR_W - 3, thumbY + thumbH / 2 + i + 1, 0xFF2B2F35);
        }
    }

    private void drawDesigns(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Identifier> designs = this.designs();
        Identifier selected = this.menu.selected();
        int hovered = this.cellAt(mouseX, mouseY);
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (this.scroll + row) * COLUMNS + column;
                int x = this.leftPos + GRID_X + column * CELL;
                int y = this.topPos + GRID_Y + row * CELL;
                Identifier id = index < designs.size() ? designs.get(index) : null;
                PosterVariant design = id == null ? null : this.design(id);
                if (design != null && id.equals(selected)) {
                    graphics.fill(x - 1, y - 1, x + CELL - 1, y + CELL - 1, SELECTED);
                }
                MachineStyle.well(graphics, x, y, CELL - 2, CELL - 2);
                if (design != null) {
                    this.drawArtwork(graphics, design, x + 1, y + 1, CELL - 4);
                }
            }
        }
        // The veils go on the stratum above the artwork: drawn in the same one, a fill ends up under the textures.
        graphics.nextStratum();
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = (this.scroll + row) * COLUMNS + column;
                int x = this.leftPos + GRID_X + column * CELL;
                int y = this.topPos + GRID_Y + row * CELL;
                Identifier id = index < designs.size() ? designs.get(index) : null;
                if (id == null || this.design(id) == null) {
                    continue;
                }
                if (!this.missingFor(id).isEmpty()) {
                    graphics.fill(x, y, x + CELL - 2, y + CELL - 2, DIM);
                    graphics.fill(x + CELL - 7, y + 1, x + CELL - 3, y + 5, 0xFFD8A030); // a corner mark: short of ink
                    graphics.fill(x + CELL - 6, y + 2, x + CELL - 4, y + 4, 0xFF2B1A08);
                }
                if (index == hovered) {
                    graphics.fill(x, y, x + CELL - 2, y + CELL - 2, HOVER);
                }
            }
        }
    }

    private PosterVariant design(Identifier id) {
        return this.minecraft.level == null ? null : Posters.get(this.minecraft.level, id);
    }

    /** Draws the poster's own texture, scaled into the cell and keeping its aspect. */
    private void drawArtwork(GuiGraphicsExtractor graphics, PosterVariant design, int x, int y, int size) {
        int w = design.width();
        int h = design.height();
        int drawW = w >= h ? size : Math.max(1, size * w / h);
        int drawH = h >= w ? size : Math.max(1, size * h / w);
        int x0 = x + (size - drawW) / 2;
        int y0 = y + (size - drawH) / 2;
        graphics.blit(design.texture(), x0, y0, x0 + drawW, y0 + drawH, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (!slot.isActive()) {
            return; // the sample slot, on the posters page
        }
        if (slot.index < PrintingPressBlockEntity.SLOTS && !slot.hasItem()) {
            ItemStack ghost = PrintingPressBlockEntity.ghost(slot.index);
            if (!ghost.isEmpty()) {
                MachineStyle.ghost(graphics, ghost, slot.x, slot.y);
                return;
            }
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 30, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        int textX = DISPLAY_X + (this.missingNow() != null ? 29 : 11);
        this.statusCut = MachineStyle.fitText(graphics, this.font, this.statusLine(), textX, ROW_Y + 5, DISPLAY_X + DISPLAY_W - 3 - textX,
                MachineStyle.toneColour(this.tone()), false);
        for (Page tab : Page.values()) {
            MachineStyle.fitText(graphics, this.font, Component.translatable(tab.name), GRID_X + tab.ordinal() * (TAB_W + 2) + 2, TAB_Y + 3,
                    TAB_W - 4, tab == this.page ? MachineStyle.TITLE : MachineStyle.DIM, true);
        }
        if (this.page == Page.PHOTOS) {
            if (this.photos().isEmpty() && this.chosenPhoto() == 0) {
                boolean camera = this.minecraft.player.getInventory().hasAnyMatching(stack -> stack.is(ModItems.CAMERA.get()));
                Component why = Component.translatable(camera ? "gui.nookworks.printing_press.no_photos" : "gui.nookworks.printing_press.no_camera");
                int y = GRID_Y + 6;
                for (var line : this.font.split(why, COLUMNS * CELL + BAR_W)) {
                    graphics.text(this.font, line, GRID_X + 2, y, MachineStyle.DIM, false);
                    y += this.font.lineHeight + 1;
                }
            } else {
                for (int i = 0; i < Photo.SIZES.size(); i++) {
                    Photo.Size each = Photo.SIZES.get(i);
                    graphics.centeredText(this.font, Component.literal(each.width() + "\u00d7" + each.height()),
                            SIZE_X + i % 2 * (SIZE_W + 1) + SIZE_W / 2, GRID_Y + i / 2 * (SIZE_H + 1) + 2,
                            each.equals(this.menu.photoSize()) ? MachineStyle.TITLE : MachineStyle.DIM);
                }
            }
        }
        if (this.page == Page.LABELS) {
            graphics.text(this.font, Component.translatable("gui.nookworks.printing_press.sample.short"),
                    PrintingPressMenu.SAMPLE_X - 2, PrintingPressMenu.SAMPLE_Y - 11, MachineStyle.DIM, false);
            graphics.text(this.font, Component.literal("\u00d7" + PrintingPressBlockEntity.LABELS_PER_COPY),
                    MOCK_X + MOCK + 8, MOCK_Y + 4, MachineStyle.TITLE, false);
            graphics.text(this.font, Component.translatable("gui.nookworks.printing_press.letters"),
                    TEXT_X, TEXT_Y - 11, MachineStyle.DIM, false);
        }
    }

    private Component statusLine() {
        Identifier selected = this.menu.selected();
        if (selected == null) {
            return Component.translatable("gui.nookworks.printing_press.none");
        }
        if (this.menu.running() && this.menu.heldByRedstone()) {
            return Component.translatable(MachineStatus.REDSTONE.translationKey());
        }
        if (this.menu.running() && this.menu.energy() < MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK)) {
            return Component.translatable("gui.nookworks.printing_press.no_energy"); // started, and stalled for power
        }
        if (this.menu.running()) {
            return Component.translatable("gui.nookworks.printing_press.printing", this.menu.progressPercent());
        }
        if (isLabels(selected) && this.copyOf(selected).isEmpty()) {
            return Component.translatable("gui.nookworks.printing_press.no_sample");
        }
        if (this.photoGone()) {
            return Component.translatable("gui.nookworks.printing_press.photo_gone");
        }
        if (this.photoHidden()) {
            return Component.translatable("gui.nookworks.printing_press.photo_hidden");
        }
        if (this.photosOff()) {
            return Component.translatable("gui.nookworks.printing_press.photos_off");
        }
        if (!this.missingFor(selected).isEmpty()) {
            return Component.translatable("gui.nookworks.printing_press.short"); // the ink itself is on the display
        }
        if (this.menu.container().getItem(PrintingPressBlockEntity.PAPER).isEmpty()) {
            return Component.translatable("gui.nookworks.printing_press.no_paper");
        }
        if (this.outputBlocked(selected)) {
            return Component.translatable("gui.nookworks.printing_press.output_full");
        }
        if (this.menu.energy() < MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK)) {
            return Component.translatable("gui.nookworks.printing_press.no_energy");
        }
        return Component.translatable("gui.nookworks.printing_press.ready");
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.statusCut && this.isHovering(DISPLAY_X, ROW_Y, DISPLAY_W, DISPLAY_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(this.statusLine()), Optional.empty(), mouseX, mouseY);
            return;
        }
        Item short1 = this.missingNow();
        if (short1 != null && this.isHovering(DISPLAY_X, ROW_Y, DISPLAY_W, DISPLAY_H, mouseX, mouseY)) {
            Identifier selected = this.menu.selected();
            graphics.setTooltipForNextFrame(this.font, this.missingLines(selected), Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.isHovering(this.energyX(), this.energyY() - 10, 10, ENERGY_H + 10, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(
                    Component.translatable("gui.nookworks.machine.energy", String.format(Locale.ROOT, "%,d", this.menu.energy()),
                            String.format(Locale.ROOT, "%,d", PrintingPressBlockEntity.CAPACITY)),
                    Component.translatable("gui.nookworks.machine.uses", MachineTuning.workEnergy(PrintingPressBlockEntity.FE_PER_TICK)).withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.page == Page.PHOTOS) {
            int photo = this.photoAt(mouseX, mouseY);
            if (photo == 0 && this.isHovering(PREVIEW_X, GRID_Y, PREVIEW_W, PREVIEW_H, mouseX, mouseY)) {
                photo = this.chosenPhoto();
            }
            if (photo > 0) {
                graphics.setTooltipForNextFrame(this.font, this.photoTooltip(photo), Optional.empty(), mouseX, mouseY);
                return;
            }
            Photo.Size size = this.sizeAt(mouseX, mouseY);
            if (size != null) {
                graphics.setTooltipForNextFrame(this.font, List.of(Component.translatable("gui.nookworks.printing_press.size",
                        size.width(), size.height())), Optional.empty(), mouseX, mouseY);
                return;
            }
        }
        if (this.page == Page.LABELS && this.isHovering(MOCK_X, MOCK_Y, MOCK, MOCK, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.labelTooltip().stream().map(Component::getVisualOrderText).toList(), mouseX, mouseY);
            return;
        }
        int index = this.cellAt(mouseX, mouseY);
        if (index >= 0) {
            Identifier id = this.designs().get(index);
            PosterVariant design = this.design(id);
            if (design != null) {
                graphics.setTooltipForNextFrame(this.tooltipFor(id, design).stream()
                        .map(Component::getVisualOrderText).toList(), mouseX, mouseY);
                return;
            }
        }
        if (this.hoveredSlot != null && this.hoveredSlot.index < PrintingPressBlockEntity.SLOTS && !this.hoveredSlot.hasItem()) {
            ItemStack ghost = PrintingPressBlockEntity.ghost(this.hoveredSlot.index);
            if (this.hoveredSlot.index == PrintingPressBlockEntity.SAMPLE) {
                // as components, so the tooltip wraps them: the hint in one line ran off the screen in English
                graphics.setTooltipForNextFrame(this.font, List.of(Component.translatable("gui.nookworks.printing_press.sample"),
                        Component.translatable("gui.nookworks.printing_press.sample.hint").withStyle(ChatFormatting.GRAY)),
                        Optional.empty(), mouseX, mouseY);
                return;
            }
            if (!ghost.isEmpty()) {
                graphics.setTooltipForNextFrame(List.of(ghost.getHoverName().getVisualOrderText()), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    private List<Component> tooltipFor(Identifier id, PosterVariant design) {
        Component name = Posters.item(id).getHoverName();
        Component size = Component.translatable("gui.nookworks.printing_press.size", design.width(), design.height())
                .withStyle(ChatFormatting.GRAY);
        List<Component> lines = new ArrayList<>(List.of(name, size));
        lines.addAll(this.missingLines(id));
        return lines;
    }

    /** A photo's tooltip: what it is, who took it, and what printing it still needs. */
    private List<Component> photoTooltip(int photo) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("item.nookworks.photo"));
        Component about = PhotoTextures.describe(photo);
        if (about != null) {
            lines.add(about.copy().withStyle(ChatFormatting.GRAY));
        }
        if (photo == this.chosenPhoto()) {
            Photo.Size size = this.menu.photoSize();
            lines.add(Component.translatable("gui.nookworks.printing_press.size", size.width(), size.height()).withStyle(ChatFormatting.GRAY));
            lines.addAll(this.missingLines(PrintingPressBlockEntity.PHOTO));
        }
        // who will see it, said before it is printed: a print is anybody's to see (review r12) - for a photo that can be
        // printed, not one gone or kept from this player
        if (PhotoTextures.status(photo) == NookworksNetwork.PhotoPixels.SHOWN) {
            lines.add(Component.translatable("gui.nookworks.printing_press.photo_public").withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private List<Component> labelTooltip() {
        ItemStack labels = PrintingPressBlockEntity.labelsOf(this.menu.container(), this.text());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.nookworks.printing_press.labels", PrintingPressBlockEntity.LABELS_PER_COPY));
        if (labels.isEmpty()) {
            lines.add(Component.translatable("gui.nookworks.printing_press.no_sample").withStyle(ChatFormatting.RED));
        } else {
            lines.add(labels.getHoverName().copy().withStyle(ChatFormatting.GRAY));
        }
        lines.addAll(this.missingLines(PrintingPressBlockEntity.LABEL));
        return lines;
    }

    /** One line for each ink a copy of the entry {@code id} lacks, and for the paper. */
    private List<Component> missingLines(Identifier id) {
        List<Component> lines = new ArrayList<>();
        for (Item ink : this.missingFor(id)) {
            lines.add(Component.translatable("gui.nookworks.printing_press.missing", new ItemStack(ink).getHoverName()).withStyle(ChatFormatting.RED));
        }
        if (this.menu.container().getItem(PrintingPressBlockEntity.PAPER).isEmpty()) {
            lines.add(Component.translatable("gui.nookworks.printing_press.no_paper").withStyle(ChatFormatting.RED));
        }
        return lines;
    }
}
