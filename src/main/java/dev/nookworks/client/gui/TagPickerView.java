package dev.nookworks.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import dev.nookworks.sorter.SorterTags;
import dev.nookworks.sorter.TagPicker;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.nookworks.port.gui.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * The tag picker on a screen ({@link TagPicker} for what it holds): the same in every screen that has one - the
 * Precision Sorter's, and an add-on's (nookTechnology's Precision Sorting Tunnel) - so a player who learnt one knows the
 * other (the user, 2026-11-10: 「そこがTechと食い違うと、大きくユーザー体験を損なう」).
 * <p>
 * Over the screen's own slots while open: the sample's slot at the menu's place for it, brass-edged, "Sample" under it;
 * beside it a sunken list at ({@link #LIST_X}, {@link #LIST_Y}), {@link #LIST_W} wide and {@link #ROWS} lines high - the
 * sample's mod, then its tags, each with a box to tick, its name and how many items it holds, the line under the pointer
 * lit, a scrollbar to drag or the wheel - and under it the buttons: Register for each destination first, two that fit
 * sharing a row (left to right as given), one too wide for half a row taking a row of its own, and Back always last,
 * the list's width. A line in any destination already shows ticked and dim, and its tooltip says where it is.
 * <p>
 * The screen makes one in {@code init} ({@link #init}), hands it its mouse ({@link #mouseClicked} and the rest - each
 * returns whether it took the event), draws it ({@link #extractBackground}, {@link #extractLabels}), asks it first for a
 * tooltip ({@link #tooltip}) and ticks it ({@link #tick}). It opens with {@link #open}; while open, the screen hides its
 * own widgets ({@link #isOpen}).
 */
public final class TagPickerView {
    /** The list: where it sits on the screen, how wide it is, its lines. */
    public static final int LIST_X = 30;
    public static final int LIST_Y = 19;
    public static final int LIST_W = 138;
    public static final int ROW_H = 11;
    public static final int ROWS = 5;
    /** The buttons: how high, and a row's pitch; the first row three pixels under the list. */
    public static final int BUTTON_H = 16;
    public static final int BUTTON_PITCH = 18;
    public static final int BUTTONS_Y = LIST_Y + ROWS * ROW_H + 3;
    /** A button's width when two share a row. */
    public static final int HALF_W = (LIST_W - 2) / 2;
    /** Room a label leaves on a button: four pixels in from each edge. */
    private static final int PAD = 8;
    private static final int BAR_W = 4;
    private static final int BRASS = 0xFFE2C27A;

    /**
     * A drawn sign a Register button wears left of its label (the Precision Sorter's Keep Out row's no-entry sign), and
     * how wide it is.
     */
    public interface Sign {
        int width();

        void draw(GuiGraphicsExtractor g, int x, int y);
    }

    /**
     * A Register button: its label, a sign left of it or none, what it does said on hover (or null: it says what it is),
     * what it says switched off with no slot free (the destination is full), and what a list line's tooltip says when the
     * destination holds that line's tag or mod already. Read when shown, so they may follow a switch.
     */
    public record Destination(Supplier<Component> label, @Nullable Sign sign, @Nullable Supplier<Component> about,
            Supplier<Component> full, Supplier<Component> taken) {
        /** A plain one, no sign and nothing said on hover while it may be pressed. */
        public static Destination plain(Supplier<Component> label, Supplier<Component> full, Supplier<Component> taken) {
            return new Destination(label, null, null, full, taken);
        }
    }

    private final AbstractContainerScreen<?> screen;
    private final TagPicker picker;
    private final List<Destination> destinations;
    private final Runnable changed;
    private final List<SteelButton> registers = new ArrayList<>();
    private @Nullable SteelButton back;
    private int left;
    private int top;
    private int scroll;
    private boolean draggingBar;
    /** The scrollbar is held this far below its thumb's top. */
    private int barGrab;
    /** The last press was taken by one of the screen's widgets: its release is not a slot's click. */
    private boolean pressTaken;

    /**
     * For {@code screen}, whose menu holds {@code picker}: a Register button for each of the picker's destinations, in
     * the same order. {@code changed} is run when the picker opens or closes, for the screen to show its own widgets or
     * hide them.
     */
    public TagPickerView(AbstractContainerScreen<?> screen, TagPicker picker, List<Destination> destinations, Runnable changed) {
        if (destinations.size() != picker.destinations().size()) {
            throw new IllegalArgumentException("a button for each destination: " + destinations.size() + " for " + picker.destinations().size());
        }
        this.screen = screen;
        this.picker = picker;
        this.destinations = List.copyOf(destinations);
        this.changed = changed;
    }

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    /** Its buttons made again, for a screen at ({@code left}, {@code top}): each handed to {@code add} (addRenderableWidget). */
    public void init(int left, int top, Consumer<AbstractWidget> add) {
        this.left = left;
        this.top = top;
        this.registers.clear();
        for (int i = 0; i < this.destinations.size(); i++) {
            int destination = i;
            Destination d = this.destinations.get(i);
            SteelButton button = new SteelButton(0, 0, LIST_W, BUTTON_H, d.label().get(), () -> this.press(this.picker.registerButton(destination))) {
                @Override
                protected MutableComponent createNarrationMessage() {
                    return wrapDefaultNarrationMessage(d.label().get());
                }

                @Override
                protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
                    if (d.sign() == null) {
                        super.extractContents(g, mouseX, mouseY, a);
                        return;
                    }
                    // the steel without its words, then the sign and the label as one, in the middle
                    Component label = this.getMessage();
                    this.setMessage(Component.empty());
                    super.extractContents(g, mouseX, mouseY, a);
                    this.setMessage(label);
                    int w = d.sign().width() + 4 + font().width(label);
                    int x = this.getX() + (this.getWidth() - w) / 2;
                    d.sign().draw(g, x, this.getY() + 1);
                    g.text(font(), label, x + d.sign().width() + 4, this.getY() + 4, this.active ? MachineStyle.TEXT : MachineStyle.DIM, false);
                }
            };
            this.registers.add(button);
            add.accept(button);
        }
        this.back = new SteelButton(0, 0, LIST_W, BUTTON_H, Component.translatable("gui.nookworks.sorter.picker.back"),
                () -> this.press(this.picker.closeButton()));
        add.accept(this.back);
        this.tick();
    }

    /** Opens it, from the screen's own button (the Precision Sorter's "Tags..."). */
    public void open() {
        this.press(this.picker.openButton());
    }

    public boolean isOpen() {
        return this.picker.open();
    }

    /** A press of its own: done here at once, on this screen's copy, and sent for the server's. */
    private void press(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean was = this.picker.open();
        this.picker.button(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(this.screen.getMenu().containerId, id);
        if (was != this.picker.open()) {
            this.scroll = 0;
            this.draggingBar = false;
            this.changed.run();
        }
        this.tick();
    }

    // ---- its buttons ----

    /** Whether destination {@code i}'s button shares a row: its label, and its sign, fit half a row. */
    private boolean fitsHalf(int i) {
        Destination d = this.destinations.get(i);
        int sign = d.sign() == null ? 0 : d.sign().width() + 4;
        return sign + font().width(d.label().get()) <= HALF_W - PAD;
    }

    /** The buttons laid out under the list by the one rule ({@link TagPicker#layout}), and switched on as what is ticked and the room left allow. */
    public void tick() {
        boolean open = this.picker.open();
        List<TagPicker.Place> places = this.places();
        for (int i = 0; i <= this.registers.size(); i++) {
            SteelButton button = i < this.registers.size() ? this.registers.get(i) : this.back;
            if (button == null) {
                continue;
            }
            TagPicker.Place place = places.get(i);
            int x = this.left + LIST_X + (place.column() == TagPicker.Place.RIGHT ? LIST_W - HALF_W : 0);
            button.setX(x);
            button.setY(this.top + BUTTONS_Y + place.row() * BUTTON_PITCH);
            button.setWidth(place.column() == TagPicker.Place.WHOLE ? LIST_W : HALF_W);
            button.visible = open;
        }
        for (int i = 0; i < this.registers.size(); i++) {
            SteelButton button = this.registers.get(i);
            button.setMessage(this.destinations.get(i).label().get());
            button.active = this.picker.anyChosen() && this.picker.free(i) > 0;
        }
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
    }

    private List<TagPicker.Place> places() {
        List<Boolean> fits = new ArrayList<>(this.destinations.size());
        for (int i = 0; i < this.destinations.size(); i++) {
            fits.add(this.fitsHalf(i));
        }
        return TagPicker.layout(fits);
    }

    /** How many rows of buttons it has under the list, Back's included: for a screen to leave room for them. */
    public int buttonRows() {
        return this.places().getLast().row() + 1;
    }

    // ---- the list ----

    /** The list's lines: the sample's mod, then its tags; none without a sample. */
    private int lines() {
        return this.picker.sample().isEmpty() ? 0 : 1 + this.picker.choices().size();
    }

    private int maxScroll() {
        return Math.max(0, this.lines() - ROWS);
    }

    private int thumbH() {
        return Math.max(8, ROWS * ROW_H * ROWS / Math.max(1, this.lines()));
    }

    /** The thumb's top on the screen, for the lines shown. */
    private int thumbTop() {
        return this.top + LIST_Y + (ROWS * ROW_H - this.thumbH()) * this.scroll / Math.max(1, this.maxScroll());
    }

    /** On the scrollbar, a pixel wider each side than it is drawn: it is thin. */
    private boolean onBar(double mouseX, double mouseY) {
        double x = mouseX - this.left - LIST_X;
        double y = mouseY - this.top - LIST_Y;
        return this.picker.open() && this.maxScroll() > 0 && x >= LIST_W - BAR_W - 1 && x < LIST_W + 1 && y >= 0 && y < ROWS * ROW_H;
    }

    /** Puts the thumb's top {@link #barGrab} above the pointer, as near as a line allows. */
    private void dragBar(double mouseY) {
        int free = ROWS * ROW_H - this.thumbH();
        double f = free <= 0 ? 0 : (mouseY - this.barGrab - this.top - LIST_Y) / free;
        this.scroll = Math.max(0, Math.min((int) Math.round(f * this.maxScroll()), this.maxScroll()));
    }

    /** The list line under the pointer - 0 for the mod, 1 on for the sample's tags in order - or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - this.left - LIST_X;
        double y = mouseY - this.top - LIST_Y;
        if (!this.picker.open() || x < 0 || x >= LIST_W - BAR_W - 1 || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = this.scroll + (int) (y / ROW_H);
        return index < this.lines() ? index : -1;
    }

    // ---- the mouse ----

    /**
     * Whether it took the press: on the scrollbar (held where it is taken, so a press on the thumb moves nothing, and
     * beside it brings it there) or on a line (ticked or unticked). Any other press a widget of the screen takes is noted,
     * so its release comes to {@link #mouseReleased} and no slot under it: Register and Back close the list, and the
     * screen's slots under them are back at once - the release put the sample carried into one (r10).
     */
    public boolean mouseClicked(MouseButtonEvent event) {
        this.pressTaken = false;
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
        int row = this.rowAt(event.x(), event.y());
        if (row >= 0 && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            this.press(row == 0 ? this.picker.modButton() : this.picker.tickButton(row - 1));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.25F));
            return true;
        }
        this.pressTaken = this.screen.getChildAt(event.x(), event.y()).isPresent();
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event) {
        if (this.draggingBar) {
            this.dragBar(event.y());
            return true;
        }
        return false;
    }

    /** Whether it took the release: the bar let go of, or a press a widget took let go of as a screen does, and nothing more. */
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean bar = this.draggingBar;
        this.draggingBar = false;
        if (this.pressTaken) {
            this.pressTaken = false;
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.screen.isDragging()) {
                this.screen.setDragging(false);
                GuiEventListener focused = this.screen.getFocused();
                if (focused != null) {
                    focused.mouseReleased(event.x(), event.y(), event.button());
                }
            }
            return true;
        }
        return bar;
    }

    /** Whether it took the wheel: over the list while open, a line a notch. */
    public boolean mouseScrolled(double x, double y, double scrollY) {
        double lx = x - this.left - LIST_X;
        double ly = y - this.top - LIST_Y;
        if (this.picker.open() && lx >= 0 && lx < LIST_W && ly >= 0 && ly < ROWS * ROW_H) {
            this.scroll = Math.max(0, Math.min(this.scroll - (int) Math.signum(scrollY), this.maxScroll()));
            return true;
        }
        return false;
    }

    // ---- drawing ----

    /**
     * While open: the sample's brass-edged slot, and the list - a sunken well, a line for the sample's mod and one for
     * each of its tags (its box, its name, how many items), the line under the pointer lit - and its scrollbar.
     */
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (!this.picker.open()) {
            return;
        }
        Slot sample = this.sampleSlot();
        if (sample != null) {
            MachineStyle.filterSlot(g, this.left + sample.x - 1, this.top + sample.y - 1);
        }
        Font font = font();
        int x = this.left + LIST_X;
        int y = this.top + LIST_Y;
        int h = ROWS * ROW_H;
        MachineStyle.well(g, x - 1, y - 1, LIST_W + 2, h + 2);
        String mod = this.picker.sampleMod();
        if (mod == null) {
            this.hint(g, Component.translatable("gui.nookworks.sorter.picker.hint"), x, y + 4);
            return;
        }
        List<TagKey<Item>> choices = this.picker.choices();
        int lines = 1 + choices.size();
        int hovered = this.rowAt(mouseX, mouseY);
        int textRight = x + LIST_W - BAR_W - 3;
        for (int row = 0; row < ROWS && this.scroll + row < lines; row++) {
            int index = this.scroll + row;
            TagKey<Item> tag = index == 0 ? null : choices.get(index - 1);
            int ry = y + row * ROW_H;
            boolean taken = this.picker.taken(tag, mod);
            boolean ticked = tag == null ? this.picker.modChosen() : this.picker.chosen(index - 1);
            if (index == hovered) {
                g.fill(x, ry, x + LIST_W - BAR_W - 1, ry + ROW_H, 0xFF2C3037);
            }
            // the box: brass edged, ticked in green; one already in a destination is shown ticked and dim - ticked shows
            // over taken, as one kept out may still be ticked for the filter, and the other way round
            g.fill(x + 2, ry + 2, x + 9, ry + 9, 0xFFB08A3E);
            g.fill(x + 3, ry + 3, x + 8, ry + 8, 0xFF15171B);
            if (ticked || taken) {
                g.fill(x + 4, ry + 4, x + 7, ry + 7, ticked ? 0xFF7EDC7E : 0xFF5A616B);
            }
            String count = String.valueOf(tag == null ? SorterTags.modSize(mod) : SorterTags.size(tag));
            int countX = textRight - font.width(count);
            g.text(font, count, countX, ry + 2, MachineStyle.DIM, false);
            // the mod's line short ("Mod: Minecraft"), for the room beside its count; its tooltip says what it catches
            Component name = tag == null ? Component.translatable("gui.nookworks.sorter.mod", SorterTags.modName(mod)) : SorterTags.name(tag);
            MachineStyle.fitText(g, font, name, x + 12, ry + 2, countX - x - 16, taken ? MachineStyle.DIM : MachineStyle.TEXT, false);
        }
        if (choices.isEmpty()) {
            // under the mod's line, which every sample has
            this.hint(g, Component.translatable("gui.nookworks.sorter.picker.no_tags"), x, y + ROW_H + 4);
        }
        if (this.maxScroll() > 0) {
            int barX = x + LIST_W - BAR_W;
            g.fill(barX, y, barX + BAR_W, y + h, 0xFF23262B);
            int thumb = this.thumbTop();
            g.fill(barX, thumb, barX + BAR_W, thumb + this.thumbH(), this.draggingBar ? 0xFF8A919B : 0xFF6A717B);
        }
    }

    /** Words in the list's well, dim, wrapped to its width from {@code from} down (Japanese between words), as far as it goes. */
    private void hint(GuiGraphicsExtractor g, Component hint, int x, int from) {
        List<FormattedCharSequence> lines = MachineStyle.wrap(font(), hint, LIST_W - 8);
        for (int i = 0; i < lines.size() && from + i * 10 + 8 <= this.top + LIST_Y + ROWS * ROW_H; i++) {
            g.text(font(), lines.get(i), x + 4, from + i * 10, MachineStyle.DIM, false);
        }
    }

    /**
     * While open, in the screen's own coordinates (from {@code extractLabels}): "Sample" under its slot, in the room between
     * the screen's edge and the list - a word too wide for it is left out, the hint beside the slot naming it already.
     */
    public void extractLabels(GuiGraphicsExtractor g) {
        Slot sample = this.sampleSlot();
        if (!this.picker.open() || sample == null) {
            return;
        }
        Component caption = Component.translatable("gui.nookworks.sorter.picker.sample");
        if (font().width(caption) <= LIST_X - 6) {
            g.centeredText(font(), caption, sample.x + 8, sample.y + 20, MachineStyle.DIM);
        }
    }

    /** The sample's slot, while it is shown. */
    private @Nullable Slot sampleSlot() {
        Slot slot = this.picker.slot();
        return slot != null && slot.isActive() ? slot : null;
    }

    // ---- what the pointer is told ----

    /**
     * What the pointer is told, or nothing (empty): over a line, what its tag or mod holds and where it is already; over a
     * Register button switched off, why - or switched on, what it does if it says; over the empty sample slot, what it
     * is for. Lines, for the screen's tooltip; {@link #extractTooltip} sets them itself.
     */
    public List<Component> tooltip(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        if (!this.picker.open() || !this.screen.getMenu().getCarried().isEmpty()) {
            return lines;
        }
        int row = this.rowAt(mouseX, mouseY);
        String mod = this.picker.sampleMod();
        if (row >= 0 && mod != null) {
            TagKey<Item> tag = row == 0 ? null : this.picker.choices().get(row - 1);
            String members = "gui.nookworks.sorter.picker.members";
            lines.addAll(tag == null ? modTooltip(mod, members) : tagTooltip(tag, members));
            for (int i = 0; i < this.destinations.size(); i++) {
                if (this.picker.holds(i, tag, mod)) {
                    lines.add(this.destinations.get(i).taken().get().copy().withStyle(ChatFormatting.DARK_GRAY));
                }
            }
            return lines;
        }
        for (int i = 0; i < this.registers.size(); i++) {
            SteelButton button = this.registers.get(i);
            if (!button.visible || !button.isHovered()) {
                continue;
            }
            Destination d = this.destinations.get(i);
            if (button.active) {
                if (d.about() != null) {
                    lines.add(d.about().get());
                }
            } else {
                lines.add(this.picker.free(i) == 0 ? d.full().get() : Component.translatable("gui.nookworks.sorter.picker.choose"));
            }
            return lines;
        }
        Slot sample = this.sampleSlot();
        if (sample != null && !sample.hasItem() && mouseX >= this.left + sample.x - 1 && mouseX < this.left + sample.x + 17
                && mouseY >= this.top + sample.y - 1 && mouseY < this.top + sample.y + 17) {
            lines.add(Component.translatable("gui.nookworks.sorter.picker.sample.tooltip"));
        }
        return lines;
    }

    /** Sets {@link #tooltip}'s lines as this frame's tooltip, broken as the machines' are; whether there were any. */
    public boolean extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Component> lines = this.tooltip(mouseX, mouseY);
        if (lines.isEmpty()) {
            return false;
        }
        g.setTooltipForNextFrame(MachineStyle.wrap(font(), lines), mouseX, mouseY);
        return true;
    }

    // ---- entries, wherever they are shown ----

    /** A tag entry's brass "#", a mod entry's brass "@", over the slot it is drawn in (from {@code extractSlot}). */
    public static void markEntry(GuiGraphicsExtractor g, Slot slot) {
        ItemStack stack = slot.getItem();
        String mark = SorterTags.of(stack) != null ? "#" : SorterTags.modOf(stack) != null ? "@" : null;
        if (mark != null) {
            g.text(font(), mark, slot.x, slot.y - 1, BRASS, true);
        }
    }

    /**
     * What a tag or mod entry catches, in place of its sample's own tooltip, how many items in the words of
     * {@code sizeKey} (one {@code %s}: "Sorts 12 items", "Keeps out 12 items"); null for a plain item.
     */
    public static @Nullable List<Component> entryTooltip(ItemStack stack, String sizeKey) {
        TagKey<Item> tag = SorterTags.of(stack);
        if (tag != null) {
            return tagTooltip(tag, sizeKey);
        }
        String mod = SorterTags.modOf(stack);
        return mod != null ? modTooltip(mod, sizeKey) : null;
    }

    private static List<Component> tagTooltip(TagKey<Item> tag, String sizeKey) {
        return groupTooltip(SorterTags.name(tag), "#" + tag.location(), SorterTags.size(tag), SorterTags.members(tag, 8), sizeKey);
    }

    private static List<Component> modTooltip(String mod, String sizeKey) {
        return groupTooltip(Component.translatable("gui.nookworks.sorter.mod.all", SorterTags.modName(mod)), "@" + mod,
                SorterTags.modSize(mod), SorterTags.modMembers(mod, 8), sizeKey);
    }

    /** A tag's or a mod's lines: its name, its id, how many items it holds, and the first of them. */
    private static List<Component> groupTooltip(Component name, String id, int size, List<ItemStack> first, String sizeKey) {
        List<Component> lines = new ArrayList<>();
        lines.add(name.copy().withStyle(ChatFormatting.WHITE));
        lines.add(Component.literal(id).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(sizeKey, size).withStyle(ChatFormatting.GRAY));
        for (ItemStack member : first) {
            lines.add(Component.literal("  ").append(member.getHoverName()).withStyle(ChatFormatting.GRAY));
        }
        if (size > first.size()) {
            lines.add(Component.translatable("gui.nookworks.sorter.picker.more", size - first.size()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return lines;
    }
}
