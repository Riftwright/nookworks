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
import net.minecraft.ChatFormatting;
import dev.nookworks.port.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import dev.nookworks.port.gui.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Sorter GUI: the filter slots, which outlet registered items use - in words and in a little picture of the sorter
 * seen from its front - the gear slot, brass-framed as the Sifter's mesh slot is, and a button to swap the outlets. A
 * Precision Sorter also gets a second row of filter slots, its Exclude and Exact switches (each with a lamp that is lit
 * while it is on), and a Tags button that opens the tag picker in the filter's place ({@link TagPickerView}, the one
 * add-ons' screens share): a sample slot, the sample's mod and then its tags to tick - each with how many items it holds,
 * and what they are on hover - Register, under it Keep Out, which wears the row's no-entry sign and puts what is ticked
 * in the Keep Out row, and Back. A tag entry wears a
 * brass "#", a mod entry a brass "@", in the filter and in the row alike, and each says on hover what it catches - or,
 * in the row, keeps out. Under it all, the Keep Out row: red-framed ghost slots behind a no-entry sign, for what the
 * sorter never takes in.
 */
public class SorterScreen extends dev.nookworks.port.gui.PortContainerScreen<SorterMenu> implements dev.nookworks.client.gui.tab.TabbedScreen {
    /** The tabs on its right edge, as a machine's: Settings, the redstone mode (the user, 2026-11-14). */
    private dev.nookworks.client.gui.tab.ScreenTabs tabs;
    private static final String[] FLAG_KEYS = {"blacklist", "tags", "components"};

    private final SteelButton[] flagButtons = new SteelButton[SorterSettings.COUNT];
    private final List<SteelButton> sortingWidgets = new ArrayList<>();
    /** The Precision Sorter's tag picker: Register into the filter, Keep Out into the row; none on an Item Sorter. */
    private final @Nullable TagPickerView picker;

    /** Where the screen's left edge is: JEI's drop targets are its slots' places from here. */
    public int left() {
        return this.leftPos;
    }

    /** Where the screen's top edge is. */
    public int top() {
        return this.topPos;
    }

    public SorterScreen(SorterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, SorterMenu.playerY(menu.tier()) + 82);
        this.inventoryLabelY = SorterMenu.playerY(menu.tier()) - 11;
        TagPickerView.Destination filter = TagPickerView.Destination.plain(
                () -> Component.translatable("gui.nookworks.sorter.picker.register"),
                () -> Component.translatable("gui.nookworks.sorter.picker.full"),
                () -> Component.translatable("gui.nookworks.sorter.picker.taken"));
        // the Keep Out row's sign beside its words, so it reads as that row's; what it does said on hover
        TagPickerView.Destination keepOut = new TagPickerView.Destination(
                () -> Component.translatable(this.row("gui.nookworks.sorter.picker.deny")),
                new TagPickerView.Sign() {
                    @Override
                    public int width() {
                        return 14;
                    }

                    @Override
                    public void draw(GuiGraphicsExtractor g, int x, int y) {
                        SorterScreen.this.drawKeepOut(g, x, y);
                    }
                },
                () -> Component.translatable(this.row("gui.nookworks.sorter.picker.deny.tooltip")),
                () -> Component.translatable(this.row("gui.nookworks.sorter.picker.deny_full")),
                () -> Component.translatable(this.row("gui.nookworks.sorter.picker.taken_deny")));
        this.picker = menu.tier().configurable
                ? new TagPickerView(this, menu.picker(), List.of(filter, keepOut), this::showMode) : null;
    }

    private int rowsBottom() {
        return SorterMenu.FILTER_Y + (this.menu.tier().filterSlots > SorterMenu.COLUMNS ? 36 : 18);
    }

    @Override
    protected void init() {
        super.init();
        this.sortingWidgets.clear();
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
        if (this.menu.tier().configurable) {
            this.addSwitches();
        }
        // the tabs on the right edge, as a machine's: its redstone mode in Settings, the mode named beside it
        this.tabs = new dev.nookworks.client.gui.tab.ScreenTabs(this, this.menu.tabs());
        this.tabs.addSetting(this.addRenderableWidget(new RedstoneButton(0, 0, this.menu::redstoneMode,
                () -> this.press(SorterMenu.BUTTON_REDSTONE))), () -> Component.translatable(this.menu.redstoneMode().translationKey()));
        this.leftPos -= this.tabs.fit(this.children()); // an open tab kept inside a small window
    }

    @Override
    public dev.nookworks.client.gui.tab.ScreenTabs tabs() {
        return this.tabs;
    }

    /** A click on a tab is no click outside the screen: it throws nothing. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top) && !this.tabs.inside(mouseX, mouseY);
    }

    /** A Precision Sorter's switches along the bottom, and its tag picker. */
    private void addSwitches() {
        // the three switches along the bottom: the Keep Out row's turning round is its sign's, not a button here (r19)
        for (int i = 0; i < FLAG_KEYS.length; i++) {
            int flag = i;
            int x = this.leftPos + 8 + flag * 54;
            int y = this.topPos + this.rowsBottom() + 30;
            SteelButton button;
            if (flag == SorterSettings.MATCH_TAGS) {
                // tags are entries of their own now: this opens the picker that makes them
                button = new SteelButton(x, y, 52, 18, Component.translatable("gui.nookworks.sorter.pick_tags"), () -> {
                    if (this.picker != null) {
                        this.picker.open();
                    }
                });
                button.setTooltip(Tooltip.create(Component.translatable("gui.nookworks.sorter.pick_tags.tooltip")));
            } else {
                button = new SteelButton(x, y, 52, 18, this.flagLabel(flag), () -> this.press(SorterMenu.BUTTON_FLAG + flag));
                button.setTooltip(Tooltip.create(Component.translatable("gui.nookworks.sorter." + FLAG_KEYS[flag] + ".tooltip")));
                this.flagButtons[i] = button;
            }
            this.sortingWidgets.add(this.addRenderableWidget(button));
        }
        if (this.picker != null) {
            this.picker.init(this.leftPos, this.topPos, this::addRenderableWidget);
        }
        this.showMode();
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    /** The sorter's own widgets, hidden while the picker is open (it shows its own). */
    private void showMode() {
        boolean picking = this.menu.picking();
        this.sortingWidgets.forEach(w -> w.visible = !picking);
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
        if (this.picker != null) {
            this.picker.tick();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.tabs.mouseClicked(event)) {
            return true;
        }
        if (this.picker != null && this.picker.mouseClicked(event)) {
            return true;
        }
        // the Keep Out row's sign: the row turned round, letting in only what is in it, or back
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !this.menu.picking() && this.menu.getCarried().isEmpty()
                && this.isHovering(7, SorterMenu.denyY(this.menu.tier()) + 1, 16, 16, event.x(), event.y())) {
            this.press(SorterMenu.BUTTON_FLAG + SorterSettings.KEEP_OUT_OTHERS);
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.25F));
            return true;
        }
        // a press one of its buttons takes is that button's: its release is no click on the slot it comes up over (the
        // picker notes it: its buttons lie over the Keep Out row, whose slots are back the moment one closes it)
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (this.picker != null && this.picker.mouseDragged(event)) {
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.picker != null && this.picker.mouseReleased(event)) {
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (this.picker != null && this.picker.mouseScrolled(x, y, scrollY)) {
            return true;
        }
        return super.mouseScrolled(x, y, scrollX, scrollY);
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.leftPos -= this.tabs.fit(this.children()); // first: all that follows is drawn from the left edge
        super.extractBackground(graphics, mouseX, mouseY, a);
        this.tabs.extractBackground(graphics, mouseX, mouseY); // under the panel, which covers their inner edge
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        int bottom = SorterMenu.denyY(this.menu.tier()) + 22;
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, bottom - 16);
        if (!this.menu.picking()) {
            this.drawKeepOut(graphics, this.leftPos + 8, this.topPos + SorterMenu.denyY(this.menu.tier()) + 2);
        }
        if (this.picker != null) {
            this.picker.extractBackground(graphics, mouseX, mouseY); // only while it is open
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
                if (this.lettingIn()) {
                    MachineStyle.allowSlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
                } else {
                    MachineStyle.denySlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
                }
            } else if (i == SorterMenu.GEAR_SLOT) {
                MachineStyle.fittingSlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1, slot.hasItem());
                if (!slot.hasItem()) {
                    MachineStyle.ghost(graphics, new ItemStack(ModItems.GOLD_SORTER_GEAR.get()),
                            this.leftPos + slot.x, this.topPos + slot.y);
                }
            } else if (i == SorterMenu.SAMPLE_SLOT) {
                continue; // the picker draws its sample's frame
            } else if (filter) {
                MachineStyle.filterSlot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            } else {
                MachineStyle.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
            }
        }
    }

    private static boolean isDenySlot(@Nullable Slot slot) {
        return slot != null && slot.index >= SorterMenu.DENY_SLOT && slot.index < SorterMenu.DENY_SLOT + SorterBlockEntity.DENY_SLOTS;
    }

    /** A tag entry, in the filter or the Keep Out row: its sample, marked with a brass "#"; a mod entry, with a brass "@". */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);
        if (slot.index < SorterMenu.GEAR_SLOT || isDenySlot(slot)) {
            TagPickerView.markEntry(graphics, slot);
        }
    }

    /** What a tag or mod entry catches - or in the Keep Out row, keeps out - in place of its sample's own tooltip. */
    @Override
    protected List<Component> getTooltipFromContainerItem(ItemStack stack) {
        String entry = isDenySlot(this.hoveredSlot) ? this.row("gui.nookworks.sorter.tag_entry.deny") : "gui.nookworks.sorter.tag_entry";
        List<Component> lines = TagPickerView.entryTooltip(stack, entry);
        return lines != null ? lines : super.getTooltipFromContainerItem(stack);
    }

    /** The empty gear slot's hint, what the gear fitted does, and in the picker, the items of each tag's or the mod's line. */
    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty()) {
            List<Component> tabbed = this.tabs.tooltip(mouseX, mouseY);
            if (!tabbed.isEmpty()) {
                graphics.setTooltipForNextFrame(MachineStyle.wrap(this.font, tabbed), mouseX, mouseY);
                return;
            }
        }
        if (this.picker != null && this.picker.extractTooltip(graphics, mouseX, mouseY)) {
            return; // its lines, its buttons, its empty sample slot
        }
        // the Keep Out row: what it is, on its mark and on an empty slot of it
        int denyY = SorterMenu.denyY(this.menu.tier());
        boolean onMark = this.isHovering(7, denyY + 1, 16, 16, mouseX, mouseY);
        boolean onEmptyDeny = this.hoveredSlot != null && this.hoveredSlot.index >= SorterMenu.DENY_SLOT
                && this.hoveredSlot.index < SorterMenu.DENY_SLOT + SorterBlockEntity.DENY_SLOTS && !this.hoveredSlot.hasItem();
        if (!this.menu.picking() && this.menu.getCarried().isEmpty() && (onMark || onEmptyDeny)) {
            String rowKey = this.row("gui.nookworks.sorter.deny");
            List<Component> lines = new ArrayList<>(List.of(Component.translatable(rowKey),
                    Component.translatable(rowKey + ".tooltip").withStyle(ChatFormatting.GRAY)));
            if (this.menu.tier().configurable) { // a whole tag or mod, from the picker
                lines.add(Component.translatable(rowKey + ".tooltip.tags").withStyle(ChatFormatting.GRAY));
            }
            if (onMark) { // the sign turns the row round
                lines.add(Component.translatable(rowKey + ".switch").withStyle(ChatFormatting.YELLOW));
            }
            graphics.setTooltipForNextFrame(this.font, lines, Optional.empty(), mouseX, mouseY);
            return;
        }
        ItemStack held = this.menu.held();
        Component holding = Component.translatable("gui.nookworks.sorter.holding");
        int heldY = SorterMenu.playerY(this.menu.tier()) - 17;
        int heldLeft = SorterMenu.HELD_X - 3 - this.font.width(holding);
        if (!held.isEmpty() && !this.menu.picking() && this.menu.getCarried().isEmpty()
                && this.isHovering(heldLeft, heldY, SorterMenu.HELD_X + 16 - heldLeft, 16, mouseX, mouseY)) {
            List<Component> lines = new ArrayList<>(getTooltipFromItem(this.minecraft, held));
            lines.add(Component.translatable("gui.nookworks.sorter.holding.tooltip").withStyle(ChatFormatting.GRAY));
            graphics.setTooltipForNextFrame(this.font, lines, held.getTooltipImage(), held, mouseX, mouseY);
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
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 16, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        ItemStack held = this.menu.held();
        if (!held.isEmpty()) {
            // what it holds, at the caption row's right end: one batch, waiting there while its outlet is full or blocked
            // - a sorter that stopped says why without Jade (the 1.1 low items, 48)
            int y = SorterMenu.playerY(this.menu.tier()) - 17;
            Component holding = Component.translatable("gui.nookworks.sorter.holding");
            graphics.text(this.font, holding, SorterMenu.HELD_X - 3 - this.font.width(holding), this.inventoryLabelY, MachineStyle.DIM, false);
            graphics.item(held, SorterMenu.HELD_X, y);
            graphics.itemDecorations(this.font, held, SorterMenu.HELD_X, y);
        }
        if (this.menu.picking()) {
            if (this.picker != null) {
                this.picker.extractLabels(graphics); // "Sample" under its slot
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
        // turned round, the row lets in only what is in it: a green ring with a tick in it, not a no-entry sign
        boolean in = this.lettingIn();
        int colour = in ? 0xFF3E9A34 : 0xFFD2483A;
        int shade = in ? 0xFF24601E : 0xFF7A2A22;
        for (int py = 0; py < 14; py++) {
            for (int px = 0; px < 14; px++) {
                double dx = px - 6.5;
                double dy = py - 6.5;
                double r = Math.sqrt(dx * dx + dy * dy);
                boolean ring = r >= 4.9 && r <= 7.0;
                boolean mark = in ? tick(px, py) : r < 5.0 && Math.abs(dx - dy) <= 1.3;
                if (ring || mark) {
                    // the lower right of the ring a shade darker, as the slots' edges are lit from the top left
                    g.fill(x + px, y + py, x + px + 1, y + py + 1, ring && dx + dy > 6.5 ? shade : colour);
                }
            }
        }
    }

    /** The tick in the letting-in sign: a short stroke down to the right, then a long one up, two pixels thick. */
    private static boolean tick(int px, int py) {
        return px >= 3 && px <= 5 && (py == px + 3 || py == px + 4) || px >= 6 && px <= 10 && (py == 14 - px || py == 15 - px);
    }

    /** Whether the Keep Out row is turned round: what is in it is all that is drawn in. */
    private boolean lettingIn() {
        return this.menu.flag(SorterSettings.KEEP_OUT_OTHERS);
    }

    /** A Keep Out text's key as it reads for the row's way round: its "allow" one when the row lets in only what is in it. */
    private String row(String denyKey) {
        return this.lettingIn() ? denyKey.replace("deny", "allow") : denyKey;
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
