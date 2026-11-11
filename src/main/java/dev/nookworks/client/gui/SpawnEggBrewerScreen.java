package dev.nookworks.client.gui;

import java.util.List;

import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.registry.ModItems;
import dev.nookworks.client.gui.tab.ScreenTabs;
import dev.nookworks.client.gui.tab.TabbedScreen;
import dev.nookworks.menu.tab.TabSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Spawn Egg Brewer GUI: in the bay, bottle plus egg, an arrow towards the spawn egg, and the blaze powder with a gauge
 * of what is left of it; empty slots show what goes in them, faded. A status display says what it waits for.
 */
public class SpawnEggBrewerScreen extends AbstractContainerScreen<SpawnEggBrewerMenu> implements TabbedScreen {
    private ScreenTabs tabs;
    private static final int ARROW_X = 80;
    private static final int ARROW_Y = 34;
    private static final int INVENTORY_Y = SpawnEggBrewerMenu.INVENTORY_Y;
    private static final int STATUS_Y = 70;

    public SpawnEggBrewerScreen(SpawnEggBrewerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, INVENTORY_Y + 82);
        this.inventoryLabelY = INVENTORY_Y - 11;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.tabs = new ScreenTabs(this, this.menu.tabs());
        // the redstone mode lives in the Settings tab since 1.5 (the user's design, 2026-11-08), its mode named beside it
        this.tabs.addSetting(this.addRenderableWidget(new RedstoneButton(0, 0, this.menu::redstoneMode,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SpawnEggBrewerMenu.BUTTON_REDSTONE))), () -> Component.translatable(this.menu.redstoneMode().translationKey()));
        this.leftPos -= this.tabs.fit(this.children()); // an open tab kept inside a small window
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.leftPos -= this.tabs.fit(this.children()); // first: all that follows is drawn from the left edge
        super.extractBackground(graphics, mouseX, mouseY, a);
        this.tabs.extractBackground(graphics, mouseX, mouseY); // under the panel, which covers their inner edge
        int x = this.leftPos;
        int y = this.topPos;
        MachineStyle.panel(graphics, x, y, this.imageWidth, this.imageHeight);
        MachineStyle.bay(graphics, x + 5, y + 16, this.imageWidth - 10, STATUS_Y - 2 - 16);
        MachineStyle.display(graphics, x + 7, y + STATUS_Y, this.imageWidth - 14, 12, this.status().tone());
        for (Slot slot : this.menu.slots) {
            if (!(slot instanceof TabSlot)) { // the tabs frame their own
                MachineStyle.slot(graphics, x + slot.x - 1, y + slot.y - 1);
            }
        }
        // "+" between the bottle and the egg
        graphics.fill(x + 50, y + 39, x + 56, y + 41, MachineStyle.DIM);
        graphics.fill(x + 52, y + 37, x + 54, y + 43, MachineStyle.DIM);
        MachineStyle.arrow(graphics, x + ARROW_X, y + ARROW_Y, (float) this.menu.progress() / SpawnEggBrewerBlockEntity.BREW_TIME);
        MachineStyle.bar(graphics, x + 7, y + 38, 18, 5, (float) this.menu.fuel() / SpawnEggBrewerBlockEntity.BREWS_PER_FUEL);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (slot.index < 3 && !slot.hasItem()) {
            ItemStack hint = switch (slot.index) {
                case SpawnEggBrewerBlockEntity.BOTTLE -> MobBottleItem.filled(EntityTypes.PIG);
                case SpawnEggBrewerBlockEntity.EGG -> new ItemStack(Items.EGG);
                default -> new ItemStack(Items.BLAZE_POWDER);
            };
            MachineStyle.ghost(graphics, hint, slot.x, slot.y);
            return;
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    private MachineStatus status() {
        return SpawnEggBrewerBlockEntity.status(this.menu.heldByRedstone(),
                SpawnEggBrewerBlockEntity.isFilledBottle(this.menu.slots.get(SpawnEggBrewerBlockEntity.BOTTLE).getItem()),
                this.menu.slots.get(SpawnEggBrewerBlockEntity.EGG).hasItem(), this.menu.fuel(),
                this.menu.slots.get(SpawnEggBrewerBlockEntity.FUEL).hasItem(), this.menu.outputBlocked());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 16, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        graphics.text(this.font, Component.translatable("gui.nookworks.spawn_egg_brewer.fuel"), 8, 28, MachineStyle.DIM, false);
        MachineStatus status = this.status();
        Component line = SpawnEggBrewerBlockEntity.statusLine(status, this.menu.progress());
        MachineStyle.fitText(graphics, this.font, line, 18, STATUS_Y + 2, this.imageWidth - 28, MachineStyle.toneColour(status.tone()), false);
    }

    @Override
    public ScreenTabs tabs() {
        return this.tabs;
    }

    /** A click on a tab is no click outside the screen: it throws nothing. */
    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top) && !this.tabs.inside(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return this.tabs.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menu.getCarried().isEmpty()) {
            List<Component> tabbed = this.tabs.tooltip(mouseX, mouseY);
            if (!tabbed.isEmpty()) {
                graphics.setTooltipForNextFrame(MachineStyle.wrap(this.font, tabbed), mouseX, mouseY);
                return;
            }
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }
}
