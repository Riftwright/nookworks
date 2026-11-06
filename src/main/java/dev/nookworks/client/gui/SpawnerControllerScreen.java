package dev.nookworks.client.gui;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.MachineStatus;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerControllerExtensions;
import dev.nookworks.spawner.SpawnerControllerMenu;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Spawner Controller GUI: one row per setting in the bay - its name on the left, its value lined up against the
 * buttons, so no language runs one into the other - the energy bar under it, and a two-line status display: how many
 * spawners it holds and in what state, and what that costs or why it is not running; no inventory there.
 * <p>
 * With add-ons' pages ({@link SpawnerControllerPages}) tabs stand over the panel, the settings first; another tab shows
 * that page - its widgets, its slots, and the player's inventory if its extension asks for it.
 */
public class SpawnerControllerScreen extends AbstractContainerScreen<SpawnerControllerMenu> {
    private static final int ROW_Y = 24;
    private static final int ROW_H = 20;
    private static final int BAY_BOTTOM = ROW_Y + 6 * ROW_H - 4;
    private static final int BAR_Y = BAY_BOTTOM + 5;
    private static final int STATUS_Y = BAR_Y + 13;
    private static final int STATUS_H = 22;
    /** Where the buttons start; the values end a little short of it. */
    private static final int BUTTONS_X = 124;
    private static final int VALUE_RIGHT = BUTTONS_X - 5;
    private static final int TAB_H = 14;
    private boolean statusCut;
    /** The add-on page open, or null for the settings. */
    private @Nullable Identifier page;
    /** The menu's pages left out when the tabs were built: they are built again when that changes. */
    private int hiddenPages;

    public SpawnerControllerScreen(SpawnerControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, STATUS_Y + STATUS_H + 8);
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        Map<Identifier, SpawnerControllerPages.Page> pages = new java.util.LinkedHashMap<>(SpawnerControllerPages.shown());
        pages.keySet().removeIf(id -> !this.menu.pageShown(id)); // an extension's page left out on this controller
        this.hiddenPages = this.menu.hiddenPages();
        if (!pages.isEmpty()) {
            // as wide as their names, shared out to fit over the panel when they would run past it
            int room = (this.imageWidth - 8) / (pages.size() + 1) - 2;
            int x = this.leftPos + 4;
            x += this.tab(x, room, null, Component.translatable("gui.nookworks.spawner_controller.tab.settings"), ItemStack.EMPTY);
            for (Map.Entry<Identifier, SpawnerControllerPages.Page> entry : pages.entrySet()) {
                x += this.tab(x, room, entry.getKey(), entry.getValue().title(), entry.getValue().icon());
            }
        }
        this.menu.showPage(this.page);
        SpawnerControllerPages.Page open = this.page == null ? null : pages.get(this.page);
        if (open != null) {
            open.init(new PageContext(this.page));
            return;
        }
        if (this.page != null) {
            this.press(this.menu.showPageButton(null)); // its page left out while open: back to the settings
        }
        this.page = null;
        this.menu.showPage(null);
        this.toggle(0, SpawnerControllerMenu.TOGGLE_ENABLED);
        this.stepper(1, SpawnerControllerMenu.INTERVAL_DOWN, SpawnerControllerMenu.INTERVAL_UP);
        this.stepper(2, SpawnerControllerMenu.COUNT_DOWN, SpawnerControllerMenu.COUNT_UP);
        this.stepper(3, SpawnerControllerMenu.NEARBY_DOWN, SpawnerControllerMenu.NEARBY_UP);
        this.toggle(4, SpawnerControllerMenu.TOGGLE_LIGHT);
        this.toggle(5, SpawnerControllerMenu.TOGGLE_PLAYER);
        this.addRenderableWidget(new RedstoneButton(this.leftPos + RedstoneButton.X, this.topPos + 3,
                this.menu::redstoneMode, () -> this.press(SpawnerControllerMenu.BUTTON_REDSTONE)));
    }

    /** A page left out or let back in on the server (an add-on's part switched): the tabs built again. */
    @Override
    protected void containerTick() {
        super.containerTick();
        if (this.menu.hiddenPages() != this.hiddenPages) {
            this.rebuildWidgets();
        }
    }

    private void press(int id) {
        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
    }

    /**
     * A tab over the panel: the settings ({@code id} null) or an add-on's page, its icon (if any) before its name, no
     * wider than {@code room} when all would not fit. Its width, with the gap after it.
     */
    private int tab(int x, int room, @Nullable Identifier id, Component title, ItemStack icon) {
        int natural = this.font.width(title) + 12 + (icon.isEmpty() ? 0 : 12);
        int width = Math.max(24, Math.min(Math.min(natural, 80), Math.max(room, 24)));
        this.addRenderableWidget(new Tab(x, width, title, icon, Objects.equals(id, this.page), () -> {
            this.page = id;
            this.menu.showPage(id);
            this.press(this.menu.showPageButton(id));
            this.rebuildWidgets();
        }));
        return width + 2;
    }

    /**
     * A tab over the panel: the open one lit, its name in brass with a brass line under it, and pressing it does nothing;
     * the others plain steel, their names dim until pointed at (drawn as a disabled button, the open tab read as the one
     * not open: nookTechnology's look, 2026-11-03).
     */
    private final class Tab extends SteelButton {
        private final boolean open;
        private final ItemStack icon;

        Tab(int x, int width, Component title, ItemStack icon, boolean open, Runnable action) {
            super(x, SpawnerControllerScreen.this.topPos - TAB_H, width, TAB_H, title, open ? () -> { } : action);
            this.open = open;
            this.icon = icon;
            this.setTooltip(net.minecraft.client.gui.components.Tooltip.create(title));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            boolean hover = !this.open && this.isHoveredOrFocused();
            MachineStyle.button(g, this.getX(), this.getY(), this.getWidth(), this.getHeight(), hover, true);
            if (this.open) {
                g.fill(this.getX() + 2, this.getY() + this.getHeight() - 3, this.getX() + this.getWidth() - 2,
                        this.getY() + this.getHeight() - 2, MachineStyle.TITLE);
            }
            int colour = this.open ? MachineStyle.TITLE : hover ? MachineStyle.TEXT : MachineStyle.DIM;
            Font font = SpawnerControllerScreen.this.font;
            int left = this.getX() + 4;
            if (!this.icon.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(left, this.getY() + 2);
                g.pose().scale(0.625F, 0.625F);
                g.fakeItem(this.icon, 0, 0);
                g.pose().popMatrix();
                left += 12;
            }
            int room = this.getX() + this.getWidth() - 4 - left;
            String text = this.getMessage().getString();
            if (font.width(text) > room) {
                text = font.plainSubstrByWidth(text, Math.max(0, room - font.width("…"))) + "…";
            }
            g.text(font, text, left + (room - font.width(text)) / 2, this.getY() + (this.getHeight() - 8) / 2, colour, false);
        }
    }

    /** What an add-on page is given: this screen's place, its menu, its extension's numbers and buttons. */
    private final class PageContext implements SpawnerControllerPages.Context {
        private final Identifier id;

        PageContext(Identifier id) {
            this.id = id;
        }

        @Override
        public int left() {
            return SpawnerControllerScreen.this.leftPos;
        }

        @Override
        public int top() {
            return SpawnerControllerScreen.this.topPos;
        }

        @Override
        public int width() {
            return SpawnerControllerScreen.this.imageWidth;
        }

        @Override
        public int height() {
            return SpawnerControllerScreen.this.imageHeight;
        }

        @Override
        public Font font() {
            return SpawnerControllerScreen.this.font;
        }

        @Override
        public SpawnerControllerMenu menu() {
            return SpawnerControllerScreen.this.menu;
        }

        @Override
        public @Nullable ContainerData data() {
            return SpawnerControllerScreen.this.menu.extensionData(this.id);
        }

        @Override
        public void press(int button) {
            List<SpawnerControllerExtensions.Extension> all = SpawnerControllerExtensions.all();
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id().equals(this.id) && button >= 0 && button < SpawnerControllerExtensions.BUTTONS) {
                    SpawnerControllerScreen.this.press(SpawnerControllerExtensions.FIRST_BUTTON + i * SpawnerControllerExtensions.BUTTONS + button);
                }
            }
        }

        @Override
        public <T extends AbstractWidget> T add(T widget) {
            return SpawnerControllerScreen.this.addRenderableWidget(widget);
        }
    }

    private void toggle(int row, int id) {
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X, this.topPos + ROW_Y + row * ROW_H - 4, 44, 16,
                Component.translatable("gui.nookworks.spawner_controller.change"), () -> this.press(id)));
    }

    private void stepper(int row, int down, int up) {
        int y = this.topPos + ROW_Y + row * ROW_H - 4;
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X, y, 20, 16, Component.literal("-"), () -> this.press(down)));
        this.addRenderableWidget(new SteelButton(this.leftPos + BUTTONS_X + 24, y, 20, 16, Component.literal("+"), () -> this.press(up)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        MachineStyle.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        SpawnerControllerPages.Page open = this.page == null ? null : SpawnerControllerPages.get(this.page);
        if (open != null) {
            for (Slot slot : this.menu.slots) {
                if (slot.isActive()) {
                    MachineStyle.slot(graphics, this.leftPos + slot.x - 1, this.topPos + slot.y - 1);
                }
            }
            open.render(graphics, new PageContext(this.page), mouseX, mouseY, a);
            return;
        }
        MachineStyle.bay(graphics, this.leftPos + 5, this.topPos + 16, this.imageWidth - 10, BAY_BOTTOM - 16);
        for (int row = 1; row < 6; row++) {
            MachineStyle.divider(graphics, this.leftPos + 8, this.topPos + ROW_Y + row * ROW_H - 7, this.imageWidth - 16);
        }
        int energy = this.menu.energy();
        int cost = this.menu.costPerTick() * SpawnerControllerBlockEntity.PERIOD;
        MachineStatus.Tone tone = this.menu.linked() > 0 && !this.menu.powered() ? MachineStatus.Tone.BAD
                : cost > 0 && energy < cost * 20 ? MachineStatus.Tone.WARN : MachineStatus.Tone.GOOD; // under 10 s left
        MachineStyle.icon(graphics, MachineStyle.Icon.ENERGY, this.leftPos + 8, this.topPos + BAR_Y, energy <= 0);
        MachineStyle.energyBar(graphics, this.leftPos + 19, this.topPos + BAR_Y, this.imageWidth - 27, 8, energy,
                SpawnerLinkReceiverBlockEntity.capacity(), tone);
        MachineStyle.display(graphics, this.leftPos + 7, this.topPos + STATUS_Y, this.imageWidth - 14, STATUS_H, this.statusTone());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 30, MachineStyle.TITLE, true);
        if (this.page != null) {
            if (this.menu.slots.stream().anyMatch(slot -> slot.isActive() && slot.container instanceof Inventory)) {
                graphics.text(this.font, this.playerInventoryTitle, SpawnerControllerMenu.INVENTORY_X, SpawnerControllerMenu.INVENTORY_Y - 10,
                        MachineStyle.DIM, false);
            }
            return;
        }
        String k = "gui.nookworks.spawner_controller.";
        this.row(graphics, 0, k + "state", Component.translatable(k + (this.menu.enabled() ? "on" : "off")));
        this.row(graphics, 1, k + "interval", Component.translatable(k + "seconds", this.menu.intervalSeconds()));
        this.row(graphics, 2, k + "count", Component.literal(String.valueOf(this.menu.count())));
        this.row(graphics, 3, k + "nearby", Component.literal(String.valueOf(this.menu.maxNearby())));
        this.row(graphics, 4, k + "light", Component.translatable(k + (this.menu.anyLight() ? "light.any" : "light.normal")));
        this.row(graphics, 5, k + "player", Component.translatable(k + (this.menu.needsPlayer() ? "player.needed" : "player.not_needed")));
        int width = this.imageWidth - 28;
        boolean first = MachineStyle.fitText(graphics, this.font, this.status(), 18, STATUS_Y + 3, width,
                MachineStyle.toneColour(this.statusTone()), false);
        boolean second = MachineStyle.fitText(graphics, this.font, this.detail(), 18, STATUS_Y + 12, width, MachineStyle.DIM, false);
        this.statusCut = first || second;
    }

    /** A setting: its name, and its value set right, just short of its buttons (the name gives way if both are long). */
    private void row(GuiGraphicsExtractor graphics, int row, String nameKey, Component value) {
        int y = ROW_Y + row * ROW_H;
        int valueX = VALUE_RIGHT - this.font.width(value);
        graphics.text(this.font, value, valueX, y, MachineStyle.TEXT, false);
        MachineStyle.fitText(graphics, this.font, Component.translatable(nameKey), 8, y, valueX - 12, MachineStyle.DIM, false);
    }

    private Component status() {
        String k = "gui.nookworks.spawner_controller.";
        int n = this.menu.linked();
        if (n == 0) {
            return Component.translatable(k + "status.none");
        }
        String state = !this.menu.powered() ? "no_power" : this.menu.heldByRedstone() ? "redstone" : !this.menu.enabled() ? "off" : "active";
        return Component.translatable(k + "status." + state + (n == 1 ? ".one" : ""), n);
    }

    /** The status's second line: what running costs, or what to do about not running. */
    private Component detail() {
        String k = "gui.nookworks.spawner_controller.";
        if (this.menu.linked() == 0) {
            return Component.translatable(k + "none_hint");
        }
        if (!this.menu.powered()) {
            return Component.translatable(k + "vanilla");
        }
        if (this.menu.heldByRedstone()) {
            return Component.translatable(k + "held");
        }
        if (!this.menu.enabled()) {
            return Component.translatable(k + "off_hint");
        }
        return Component.translatable(k + "using", String.format(Locale.ROOT, "%,d", this.menu.costPerTick()));
    }

    private MachineStatus.Tone statusTone() {
        // switched off, as held by redstone: quiet, not the green of spawners at work
        return this.menu.linked() == 0 || (this.menu.heldByRedstone() || !this.menu.enabled()) && this.menu.powered() ? MachineStatus.Tone.QUIET
                : !this.menu.powered() ? MachineStatus.Tone.BAD : MachineStatus.Tone.GOOD;
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.page != null) {
            super.extractTooltip(graphics, mouseX, mouseY);
            return;
        }
        if (this.statusCut && this.isHovering(7, STATUS_Y, this.imageWidth - 14, STATUS_H, mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(MachineStyle.wrap(this.font, List.of(this.status(), this.detail())), mouseX, mouseY);
            return;
        }
        if (this.isHovering(8, BAR_Y, this.imageWidth - 16, 8, mouseX, mouseY)) {
            int perSpawner = this.menu.settings().energyPerTick();
            graphics.setTooltipForNextFrame(this.font, List.of(
                    Component.translatable("gui.nookworks.machine.energy", String.format(Locale.ROOT, "%,d", this.menu.energy()),
                            String.format(Locale.ROOT, "%,d", SpawnerLinkReceiverBlockEntity.capacity())),
                    Component.translatable("gui.nookworks.spawner_controller.cost", String.format(Locale.ROOT, "%,d", perSpawner))
                            .withStyle(ChatFormatting.GRAY)),
                    Optional.empty(), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
