package dev.nookworks.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModItems;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterGear;
import dev.nookworks.sorter.SorterGearItem;
import dev.nookworks.sorter.SorterMenu;
import dev.nookworks.sorter.SorterSettings;
import dev.nookworks.sorter.SorterTags;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Sorter GUI: the filter slots, which outlet registered items use - in words and in a little picture of the sorter
 * seen from its front - the gear slot, brass-framed as the Sifter's mesh slot is, and a button to swap the outlets. A
 * Precision Sorter also gets a second row of filter slots, its Exclude and Exact switches (each with a lamp that is lit
 * while it is on), and a Tags button that opens the tag picker in the filter's place: a sample slot, the sample's mod
 * and then its tags to tick - each with how many items it holds, and what they are on hover - Register and Back, and under
 * them Keep Out, which wears the row's no-entry sign and puts what is ticked in the Keep Out row. A tag entry wears a
 * brass "#", a mod entry a brass "@", in the filter and in the row alike, and each says on hover what it catches - or,
 * in the row, keeps out. Under it all, the Keep Out row: red-framed ghost slots behind a no-entry sign, for what the
 * sorter never takes in.
 */
public class SorterScreen extends AbstractContainerScreen<SorterMenu> {
    private static final String[] FLAG_KEYS = {"blacklist", "tags", "components"};
    /** The picker's list: where it sits, and its rows. */
    private static final int LIST_X = 30;
    private static final int LIST_Y = 19;
    private static final int LIST_W = 138;
    private static final int ROW_H = 11;
    private static final int ROWS = 6;
    private static final int BAR_W = 4;

    private final SteelButton[] flagButtons = new SteelButton[SorterSettings.COUNT];
    private final List<SteelButton> sortingWidgets = new ArrayList<>();
    private final List<SteelButton> pickerWidgets = new ArrayList<>();
    private SteelButton register;
    private SteelButton registerDeny;
    private int scroll;
    /** The list's scrollbar is held, this far below its thumb's top. */
    private boolean draggingBar;
    /** The last press was taken by one of the screen's buttons: its release is not a slot's click. */
    private boolean pressTaken;
    private int barGrab;

    public SorterScreen(SorterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, SorterMenu.playerY(menu.tier()) + 82);
        this.inventoryLabelY = SorterMenu.playerY(menu.tier()) - 11;
    }

    private int rowsBottom() {
        return SorterMenu.FILTER_Y + (this.menu.tier().filterSlots > SorterMenu.COLUMNS ? 36 : 18);
    }

    @Override
    protected void init() {
        super.init();
        this.sortingWidgets.clear();
        this.pickerWidgets.clear();
        // The swap button wears a picture of the outlets it swaps; the words are its tooltip.
        SteelButton swap = this.addRenderableWidget(new SteelButton(this.leftPos + 130, this.topPos + this.rowsBottom() + 5, 38, 20,
                Component.empty(), () -> this.press(SorterMenu.BUTTON_SWAP)) {
            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
                super.extractContents(g, mouseX, mouseY, a);
                SorterScreen.this.drawOutlets(g, this.getX() + 7, this.getY() + 3);
            }
        });
        swap.setTooltip(Tooltip.create(Component.translatable("gui.nookworks.sorter.swap")));
        this.sortingWidgets.add(swap);
        if (!this.menu.tier().configurable) {
            return;
        }
        for (int i = 0; i < SorterSettings.COUNT; i++) {
            int flag = i;
            int x = this.leftPos + 8 + flag * 54;
            int y = this.topPos + this.rowsBottom() + 30;
            SteelButton button;
            if (flag == SorterSettings.MATCH_TAGS) {
                // tags are entries of their own now: this opens the picker that makes them
                button = new SteelButton(x, y, 52, 18, Component.translatable("gui.nookworks.sorter.pick_tags"),
                        () -> this.pick(SorterMenu.BUTTON_PICKER_OPEN));
                button.setTooltip(Tooltip.create(Component.translatable("gui.nookworks.sorter.pick_tags.tooltip")));
            } else {
                button = new SteelButton(x, y, 52, 18, this.flagLabel(flag), () -> this.press(SorterMenu.BUTTON_FLAG + flag));
                button.setTooltip(Tooltip.create(Component.translatable("gui.nookworks.sorter." + FLAG_KEYS[flag] + ".tooltip")));
                this.flagButtons[i] = button;
            }
            this.sortingWidgets.add(this.addRenderableWidget(button));
        }
        int buttonsY = this.topPos + LIST_Y + ROWS * ROW_H + 5;
        this.register = this.addRenderableWidget(new SteelButton(this.leftPos + LIST_X, buttonsY, 66, 16,
                Component.translatable("gui.nookworks.sorter.picker.register"), () -> this.pick(SorterMenu.BUTTON_REGISTER_TAGS)));
        this.pickerWidgets.add(this.register);
        this.pickerWidgets.add(this.addRenderableWidget(new SteelButton(this.leftPos + LIST_X + LIST_W - 66, buttonsY, 66, 16,
                Component.translatable("gui.nookworks.sorter.picker.back"), () -> this.pick(SorterMenu.BUTTON_PICKER_CLOSE))));
        // under them, the list's width: the Keep Out row's sign beside its words, so it reads as that row's
        this.registerDeny = this.addRenderableWidget(new SteelButton(this.leftPos + LIST_X, buttonsY + 19, LIST_W, 16,
                Component.empty(), () -> this.pick(SorterMenu.BUTTON_REGISTER_DENY)) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return wrapDefaultNarrationMessage(Component.translatable("gui.nookworks.sorter.picker.deny"));
            }

            @Override
            protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
                super.extractContents(g, mouseX, mouseY, a);
                Component label = Component.translatable("gui.nookworks.sorter.picker.deny");
                int w = 14 + 4 + SorterScreen.this.font.width(label);
                int x = this.getX() + (this.getWidth() - w) / 2;
                SorterScreen.this.drawKeepOut(g, x, this.getY() + 1);
                g.text(SorterScreen.this.font, label, x + 18, this.getY() + 4, this.active ? MachineStyle.TEXT : MachineStyle.DIM, false);
            }
        });
        this.pickerWidgets.add(this.registerDeny);
        this.showMode();
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    /** A picker button: done here at once, on this screen's copy, and sent for the server's. */
    private void pick(int id) {
        this.menu.pickerButton(this.minecraft.player, id);
        this.press(id);
        if (id == SorterMenu.BUTTON_PICKER_OPEN || id == SorterMenu.BUTTON_PICKER_CLOSE) {
            this.scroll = 0;
        }
        this.showMode();
    }

    /** The widgets of whichever the screen shows: the sorter's own, or the picker's. */
    private void showMode() {
        boolean picking = this.menu.picking();
        this.sortingWidgets.forEach(w -> w.visible = !picking);
        this.pickerWidgets.forEach(w -> w.visible = picking);
        if (this.register != null) {
            this.register.active = this.menu.anyChosen() && this.menu.freeSlots() > 0;
            this.registerDeny.active = this.menu.anyChosen() && this.menu.freeDenySlots() > 0;
        }
    }

    private Component flagLabel(int flag) {
        String key = "gui.nookworks.sorter." + FLAG_KEYS[flag];
        boolean on = this.menu.flag(flag);
        return Component.literal("● ").withColor(on ? 0x7EDC7E : 0x3A3F46)
                .append(Component.translatable(key + ".name").withColor(on ? 0xE6E0CE : 0xA3A9B2));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        for (int i = 0; i < SorterSettings.COUNT; i++) {
            if (this.flagButtons[i] != null) {
                this.flagButtons[i].setMessage(this.flagLabel(i));
            }
        }
        this.showMode();
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
    }

    // ---- the picker's list ----

    /** The list's lines: the sample's mod, then its tags; none without a sample. */
    private int lines() {
        return this.menu.sample().isEmpty() ? 0 : 1 + this.menu.choices().size();
    }

    private int maxScroll() {
        return Math.max(0, this.lines() - ROWS);
    }

    private int thumbH() {
        return Math.max(8, ROWS * ROW_H * ROWS / Math.max(1, this.lines()));
    }

    /** The thumb's top on the screen, for the rows shown. */
    private int thumbTop() {
        return this.topPos + LIST_Y + (ROWS * ROW_H - this.thumbH()) * this.scroll / Math.max(1, this.maxScroll());
    }

    /** On the scrollbar, a pixel wider each side than it is drawn: it is thin. */
    private boolean onBar(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos - LIST_X;
        double y = mouseY - this.topPos - LIST_Y;
        return this.menu.picking() && this.maxScroll() > 0 && x >= LIST_W - BAR_W - 1 && x < LIST_W + 1 && y >= 0 && y < ROWS * ROW_H;
    }

    /** Puts the thumb's top {@link #barGrab} above the pointer, as near as a row allows. */
    private void dragBar(double mouseY) {
        int free = ROWS * ROW_H - this.thumbH();
        double f = free <= 0 ? 0 : (mouseY - this.barGrab - this.topPos - LIST_Y) / free;
        this.scroll = Math.max(0, Math.min((int) Math.round(f * this.maxScroll()), this.maxScroll()));
    }

    /** The list line under the pointer - 0 for the mod, 1 on for the sample's tags in order - or -1. */
    private int rowAt(double mouseX, double mouseY) {
        double x = mouseX - this.leftPos - LIST_X;
        double y = mouseY - this.topPos - LIST_Y;
        if (!this.menu.picking() || x < 0 || x >= LIST_W - BAR_W - 1 || y < 0 || y >= ROWS * ROW_H) {
            return -1;
        }
        int index = this.scroll + (int) (y / ROW_H);
        return index < this.lines() ? index : -1;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.onBar(event.x(), event.y())) {
            // held where it is taken, so a click on the thumb moves nothing; a click beside it brings it there
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
            this.pick(row == 0 ? SorterMenu.BUTTON_CHOOSE_MOD : SorterMenu.BUTTON_CHOOSE + row - 1);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(
                    SoundEvents.UI_BUTTON_CLICK.value(), 1.2F, 0.4F));
            return true;
        }
        // a press one of its buttons takes is that button's: its release is no click on the slot it comes up over (the
        // picker's buttons lie over the Keep Out row, whose slots are back the moment a button closes the picker, and
        // the release put the sample carried into one)
        this.pressTaken = this.getChildAt(event.x(), event.y()).isPresent();
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
        if (this.pressTaken) {
            this.pressTaken = false;
            // the button let go of, as a screen does, and nothing more
            if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && this.isDragging()) {
                this.setDragging(false);
                if (this.getFocused() != null) {
                    this.getFocused().mouseReleased(event);
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.menu.picking() && this.isHovering(LIST_X, LIST_Y, LIST_W, ROWS * ROW_H, x, y)) {
            this.scroll = Math.max(0, Math.min(this.scroll - (int) Math.signum(scrollY), this.maxScroll()));
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        int bottom = SorterMenu.denyY(this.menu.tier()) + 22;
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, bottom - 16);
        if (!this.menu.picking()) {
            this.drawKeepOut(graphics, this.leftPos + 8, this.topPos + SorterMenu.denyY(this.menu.tier()) + 2);
        }
        if (this.menu.picking()) {
            this.extractPicker(graphics, mouseX, mouseY);
        }
        int usable = this.menu.tier().filterSlots;
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (!slot.isActive()) {
                continue; // unused filter slots, and whichever of the filter and the picker is not shown
            }
            boolean filter = i < SorterMenu.COLUMNS * 2;
            if (filter && i >= usable) {
                continue;
            }
            if (i >= SorterMenu.DENY_SLOT && i < SorterMenu.DENY_SLOT + SorterBlockEntity.DENY_SLOTS) {
                MachineStyle.denySlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            } else if (i == SorterMenu.GEAR_SLOT) {
                MachineStyle.fittingSlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, slot.hasItem());
                if (!slot.hasItem()) {
                    MachineStyle.ghost(graphics, new ItemStack(ModItems.GOLD_SORTER_GEAR.get()),
                            this.leftPos + slot.x, this.topPos + slot.y);
                }
            } else if (filter || i == SorterMenu.SAMPLE_SLOT) {
                MachineStyle.filterSlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            } else {
                MachineStyle.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
    }

    /**
     * The list: a sunken well, a line for the sample's mod and one for each of its tags - its box, its name, how many
     * items - and a scrollbar.
     */
    private void extractPicker(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = this.leftPos + LIST_X;
        int y = this.topPos + LIST_Y;
        int h = ROWS * ROW_H;
        MachineStyle.well(g, x - 1, y - 1, LIST_W + 2, h + 2);
        String mod = this.menu.sampleMod();
        if (mod == null) {
            this.extractHint(g, Component.translatable("gui.nookworks.sorter.picker.hint"), x, y + 4);
            return;
        }
        List<TagKey<Item>> choices = this.menu.choices();
        int lines = 1 + choices.size();
        int hovered = this.rowAt(mouseX, mouseY);
        int textRight = x + LIST_W - BAR_W - 3;
        for (int row = 0; row < ROWS && this.scroll + row < lines; row++) {
            int index = this.scroll + row;
            TagKey<Item> tag = index == 0 ? null : choices.get(index - 1);
            int ry = y + row * ROW_H;
            boolean taken = this.taken(mod, tag);
            boolean ticked = tag == null ? this.menu.modChosen() : this.menu.chosen(index - 1);
            if (index == hovered) {
                g.fill(x, ry, x + LIST_W - BAR_W - 1, ry + ROW_H, 0xFF2C3037);
            }
            // the box: brass edged, ticked in green; a tag or mod already in the filter or the Keep Out row is shown
            // ticked and dim
            g.fill(x + 2, ry + 2, x + 9, ry + 9, 0xFFB08A3E);
            g.fill(x + 3, ry + 3, x + 8, ry + 8, 0xFF15171B);
            if (ticked || taken) {
                // ticked shows over taken: one kept out may still be ticked for the filter, and the other way round
                g.fill(x + 4, ry + 4, x + 7, ry + 7, ticked ? 0xFF7EDC7E : 0xFF5A616B);
            }
            String count = String.valueOf(tag == null ? SorterTags.modSize(mod) : SorterTags.size(tag));
            int countX = textRight - this.font.width(count);
            g.text(this.font, count, countX, ry + 2, MachineStyle.DIM, false);
            // the mod's line short ("Mod: Minecraft"), for the room beside its count; its tooltip says what it catches
            Component name = tag == null ? Component.translatable("gui.nookworks.sorter.mod", SorterTags.modName(mod))
                    : SorterTags.name(tag);
            MachineStyle.fitText(g, this.font, name, x + 12, ry + 2, countX - x - 16,
                    taken ? MachineStyle.DIM : MachineStyle.TEXT, false);
        }
        if (choices.isEmpty()) {
            // under the mod's line, which every sample has
            this.extractHint(g, Component.translatable("gui.nookworks.sorter.picker.no_tags"), x, y + ROW_H + 4);
        }
        if (this.maxScroll() > 0) {
            int barX = x + LIST_W - BAR_W;
            g.fill(barX, y, barX + BAR_W, y + h, 0xFF23262B);
            int top = this.thumbTop();
            g.fill(barX, top, barX + BAR_W, top + this.thumbH(), this.draggingBar ? 0xFF8A919B : 0xFF6A717B);
        }
    }

    /** Words in the list's well, dim, wrapped to its width from {@code top} down, as far as it goes. */
    private void extractHint(GuiGraphicsExtractor g, Component hint, int x, int top) {
        List<FormattedCharSequence> lines = this.font.split(hint, LIST_W - 8);
        for (int i = 0; i < lines.size() && top + i * 10 + 8 <= this.topPos + LIST_Y + ROWS * ROW_H; i++) {
            g.text(this.font, lines.get(i), x + 4, top + i * 10, MachineStyle.DIM, false);
        }
    }

    private static boolean isDenySlot(@Nullable Slot slot) {
        return slot != null && slot.index >= SorterMenu.DENY_SLOT && slot.index < SorterMenu.DENY_SLOT + SorterBlockEntity.DENY_SLOTS;
    }

    /** Whether the mod ({@code tag} null) or the tag is in the filter or the Keep Out row already. */
    private boolean taken(String mod, @Nullable TagKey<Item> tag) {
        return tag == null ? this.menu.hasModEntryFor(mod) || this.menu.hasDenyModEntryFor(mod)
                : this.menu.hasEntryFor(tag) || this.menu.hasDenyEntryFor(tag);
    }

    /** A tag entry, in the filter or the Keep Out row: its sample, marked with a brass "#"; a mod entry, with a brass "@". */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);
        if (slot.index < SorterMenu.GEAR_SLOT || isDenySlot(slot)) {
            String mark = SorterTags.of(slot.getItem()) != null ? "#" : SorterTags.modOf(slot.getItem()) != null ? "@" : null;
            if (mark != null) {
                graphics.text(this.font, mark, slot.x, slot.y - 1, 0xFFE2C27A, true);
            }
        }
    }

    /** What a tag or mod entry catches - or in the Keep Out row, keeps out - in place of its sample's own tooltip. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        String entry = isDenySlot(this.hoveredSlot) ? "gui.nookworks.sorter.tag_entry.deny" : "gui.nookworks.sorter.tag_entry";
        TagKey<Item> tag = SorterTags.of(stack);
        if (tag != null) {
            return tagTooltip(tag, entry);
        }
        String mod = SorterTags.modOf(stack);
        return mod != null ? modTooltip(mod, entry) : super.getTooltipFromContainerItem(stack);
    }

    private static List<Component> tagTooltip(TagKey<Item> tag, String sizeKey) {
        return groupTooltip(SorterTags.name(tag), "#" + tag.location(), SorterTags.size(tag), SorterTags.members(tag, 8), sizeKey);
    }

    private static List<Component> modTooltip(String mod, String sizeKey) {
        return groupTooltip(Component.translatable("gui.nookworks.sorter.mod.all", SorterTags.modName(mod)), "@" + mod,
                SorterTags.modSize(mod), SorterTags.modMembers(mod, 8), sizeKey);
    }

    /**
     * A tag's or a mod's tooltip: its name, its id, how many items it holds (in the words of {@code sizeKey}: what an
     * entry sorts, keeps out, or what a picker line holds) and the first of them.
     */
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

    /** The empty gear slot's hint, what the gear fitted does, and in the picker, the items of each tag's or the mod's line. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int row = this.rowAt(mouseX, mouseY);
        String mod = this.menu.sampleMod();
        if (row >= 0 && mod != null && this.menu.getCarried().isEmpty()) {
            TagKey<Item> tag = row == 0 ? null : this.menu.choices().get(row - 1);
            String members = "gui.nookworks.sorter.picker.members";
            List<Component> lines = tag == null ? modTooltip(mod, members) : tagTooltip(tag, members);
            if (tag == null ? this.menu.hasModEntryFor(mod) : this.menu.hasEntryFor(tag)) {
                lines.add(Component.translatable("gui.nookworks.sorter.picker.taken").withStyle(ChatFormatting.DARK_GRAY));
            }
            if (tag == null ? this.menu.hasDenyModEntryFor(mod) : this.menu.hasDenyEntryFor(tag)) {
                lines.add(Component.translatable("gui.nookworks.sorter.picker.taken_deny").withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        if (this.menu.picking() && this.register.isHovered() && !this.register.active) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable(this.menu.freeSlots() == 0
                    ? "gui.nookworks.sorter.picker.full" : "gui.nookworks.sorter.picker.choose"), mouseX, mouseY);
            return;
        }
        if (this.menu.picking() && this.registerDeny.isHovered()) {
            // what it does; switched off, why
            String key = this.registerDeny.active ? "gui.nookworks.sorter.picker.deny.tooltip"
                    : this.menu.freeDenySlots() == 0 ? "gui.nookworks.sorter.picker.deny_full" : "gui.nookworks.sorter.picker.choose";
            graphics.setTooltipForNextFrame(this.font, this.font.split(Component.translatable(key), 200), mouseX, mouseY);
            return;
        }
        // the Keep Out row: what it is, on its mark and on an empty slot of it
        int denyY = SorterMenu.denyY(this.menu.tier());
        boolean onMark = this.isHovering(7, denyY + 1, 16, 16, mouseX, mouseY);
        boolean onEmptyDeny = this.hoveredSlot != null && this.hoveredSlot.index >= SorterMenu.DENY_SLOT
                && this.hoveredSlot.index < SorterMenu.DENY_SLOT + SorterBlockEntity.DENY_SLOTS && !this.hoveredSlot.hasItem();
        if (!this.menu.picking() && this.menu.getCarried().isEmpty() && (onMark || onEmptyDeny)) {
            List<Component> lines = new ArrayList<>(List.of(Component.translatable("gui.nookworks.sorter.deny"),
                    Component.translatable("gui.nookworks.sorter.deny.tooltip").withStyle(ChatFormatting.GRAY)));
            if (this.menu.tier().configurable) { // a whole tag or mod, from the picker
                lines.add(Component.translatable("gui.nookworks.sorter.deny.tooltip.tags").withStyle(ChatFormatting.GRAY));
            }
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        Slot gear = this.menu.getSlot(SorterMenu.GEAR_SLOT);
        if (gear.isActive() && !gear.hasItem() && this.menu.getCarried().isEmpty()
                && this.isHovering(gear.x - 1, gear.y - 1, 18, 18, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, List.of(Component.translatable("gui.nookworks.sorter.gear"),
                    Component.translatable("gui.nookworks.sorter.gear.speed", SorterGearItem.speed(SorterGear.NONE))
                            .withStyle(ChatFormatting.GRAY)), Optional.empty(), mouseX, mouseY);
            return;
        }
        Slot sample = this.menu.getSlot(SorterMenu.SAMPLE_SLOT);
        if (sample.isActive() && !sample.hasItem() && this.menu.getCarried().isEmpty()
                && this.isHovering(sample.x - 1, sample.y - 1, 18, 18, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(this.font, Component.translatable("gui.nookworks.sorter.picker.sample.tooltip"), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 16, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        if (this.menu.picking()) {
            // under the slot, in the room between the screen's edge and the list: a word too wide for it (English's
            // "Sample" ran into the list's text) is left out, the hint beside the slot naming it already
            Component caption = Component.translatable("gui.nookworks.sorter.picker.sample");
            if (this.font.width(caption) <= LIST_X - 6) {
                graphics.centeredText(this.font, caption, SorterMenu.SAMPLE_X + 9, SorterMenu.SAMPLE_Y + 21, MachineStyle.DIM);
            }
            return;
        }
        Component registered = this.side(this.registeredRight() ? "right" : "left");
        Component others = this.side(this.registeredRight() ? "left" : "right");
        int y = this.rowsBottom() + 5;
        graphics.text(this.font, Component.translatable("gui.nookworks.sorter.registered", registered), 8, y, MachineStyle.TEXT, false);
        graphics.text(this.font, Component.translatable("gui.nookworks.sorter.others", others), 8, y + 12, MachineStyle.TEXT, false);
    }

    /**
     * The sorter from its front, the way the words mean left and right: a box with its intake on top, and an outlet
     * each side - the one registered items leave by in brass, with a dot for the filter, the other in grey.
     */
    void drawOutlets(GuiGraphicsExtractor g, int x, int y) {
        int brass = 0xFFE2C27A;
        int grey = 0xFF6A717B;
        boolean right = this.registeredRight();
        g.fill(x + 7, y + 3, x + 17, y + 13, 0xFF121418);
        g.fill(x + 8, y + 4, x + 16, y + 12, 0xFF4A5059);
        g.fill(x + 11, y, x + 13, y + 4, grey); // the intake, on top
        for (int side = 0; side < 2; side++) {
            boolean registered = (side == 1) == right;
            int colour = registered ? brass : grey;
            int from = side == 0 ? x + 1 : x + 17;
            g.fill(from, y + 7, from + 6, y + 9, colour);
            int tip = side == 0 ? x : x + 23; // an arrowhead pointing out
            g.fill(tip, y + 6, tip + 1, y + 10, colour);
            if (registered) {
                g.fill(x + 11, y + 7, x + 13, y + 9, brass); // the filter's mark on the box
            }
        }
    }

    /**
     * The Keep Out row's mark, left of it: a red no-entry sign, a ring with a bar across it from top left to bottom right,
     * fourteen pixels wide.
     */
    void drawKeepOut(GuiGraphicsExtractor g, int x, int y) {
        int red = 0xFFD2483A;
        int shade = 0xFF7A2A22;
        for (int py = 0; py < 14; py++) {
            for (int px = 0; px < 14; px++) {
                double dx = px - 6.5;
                double dy = py - 6.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                boolean ring = r >= 4.9 && r <= 7.0;
                boolean bar = r < 5.0 && Math.abs(dx - dy) <= 1.3;
                if (ring || bar) {
                    // the lower right of the ring a shade darker, as the slots' edges are lit from the top left
                    g.fill(x + px, y + py, x + px + 1, y + py + 1, ring && dx + dy > 6.5 ? shade : red);
                }
            }
        }
    }

    /**
     * Whether registered items leave by the right outlet: they do unless the outlets are swapped - or Exclude is on,
     * which sends them out of the other one ({@link SorterSettings#sortsAsRegistered}).
     */
    private boolean registeredRight() {
        return this.menu.isSwapped() == this.menu.flag(SorterSettings.BLACKLIST);
    }

    /** "Left" or "right", in brass so the way each kind of item goes stands out of the sentence. */
    private Component side(String which) {
        return Component.translatable("gui.nookworks.sorter." + which).withColor(MachineStyle.TITLE & 0xFFFFFF);
    }
}
