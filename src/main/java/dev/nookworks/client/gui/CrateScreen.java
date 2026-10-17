package dev.nookworks.client.gui;

import dev.nookworks.menu.CrateMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/** Crate GUI: a 3x3 grid, laid out like a dispenser. The slots refuse crates and shulker boxes. */
public class CrateScreen extends AbstractContainerScreen<CrateMenu> {
    public CrateScreen(CrateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        GuiDraw.panel(graphics, this.leftPos, this.topPos, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            GuiDraw.slotAt(graphics, this.leftPos, this.topPos, slot.x, slot.y);
        }
    }
}
