package dev.nookworks.client.gui;

import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.mobcapture.SpawnEggBrewerBlockEntity;
import dev.nookworks.mobcapture.SpawnEggBrewerMenu;
import dev.nookworks.registry.ModItems;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
public class SpawnEggBrewerScreen extends AbstractContainerScreen<SpawnEggBrewerMenu> {
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
        this.addRenderableWidget(new RedstoneButton(this.leftPos + RedstoneButton.X, this.topPos + 3,
                this.menu::redstoneMode,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, SpawnEggBrewerMenu.BUTTON_REDSTONE)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        MachineStyle.panel(graphics, x, y, this.imageWidth, this.imageHeight);
        MachineStyle.bay(graphics, x + 5, y + 16, this.imageWidth - 10, STATUS_Y - 2 - 16);
        MachineStyle.display(graphics, x + 7, y + STATUS_Y, this.imageWidth - 14, 12, this.status().tone());
        for (Slot slot : this.menu.slots) {
            MachineStyle.slot(graphics, x + slot.x - 1, y + slot.y - 1);
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
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 30, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        graphics.text(this.font, Component.translatable("gui.nookworks.spawn_egg_brewer.fuel"), 8, 28, MachineStyle.DIM, false);
        MachineStatus status = this.status();
        Component line = SpawnEggBrewerBlockEntity.statusLine(status, this.menu.progress());
        MachineStyle.fitText(graphics, this.font, line, 18, STATUS_Y + 2, this.imageWidth - 28, MachineStyle.toneColour(status.tone()), false);
    }
}
