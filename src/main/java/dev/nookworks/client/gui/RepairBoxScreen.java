package dev.nookworks.client.gui;

import java.util.List;
import java.util.Locale;

import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Repair Box GUI: the gear slots in the machine bay between a power gauge and an experience gauge, and a status display
 * under them saying whether it is mending, and if not, what it is waiting for. In the box's own finish, navy enamel with
 * brass corners, where the machines are steel with rivets.
 */
public class RepairBoxScreen extends AbstractContainerScreen<RepairBoxMenu> {
    private static final int ENERGY_X = 12;
    private static final int TANK_X = 150;
    private static final int ICON_Y = MachineLayout.BAY_TOP + 3;
    private static final int GAUGE_Y = ICON_Y + 10;
    private static final int GAUGE_H = MachineLayout.BAY_BOTTOM - 3 - GAUGE_Y;
    private boolean statusCut;

    public RepairBoxScreen(RepairBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MachineLayout.WIDTH, RepairBoxMenu.INVENTORY_Y + 82);
        this.inventoryLabelY = RepairBoxMenu.INVENTORY_Y - 11;
        this.titleLabelY = 6;
        this.titleLabelX = 11; // clear of the brass bracket on the corner (its arm runs to 10)
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new RedstoneButton(this.leftPos + RedstoneButton.X, this.topPos + 3,
                this.menu::redstoneMode,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, RepairBoxMenu.BUTTON_REDSTONE)));
    }

    private int energyCapacity() {
        return RepairBoxBlockEntity.CAPACITY * this.menu.boxes();
    }

    private int tankCapacity() {
        return RepairBoxBlockEntity.TANK * this.menu.boxes();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        // the box's own navy enamel and brass, not the machines' steel
        MachineStyle.panel(graphics, x, y, this.imageWidth, this.imageHeight, MachineStyle.Finish.ENAMEL);
        MachineStyle.bay(graphics, x + 5, y + MachineLayout.BAY_TOP, this.imageWidth - 10, MachineLayout.BAY_BOTTOM - MachineLayout.BAY_TOP,
                MachineStyle.Finish.ENAMEL);
        MachineStyle.display(graphics, x + 7, y + MachineLayout.STATUS_Y, this.imageWidth - 14, 12, this.status().tone());
        for (Slot slot : this.menu.slots) {
            MachineStyle.slot(graphics, x + slot.x - 1, y + slot.y - 1, MachineStyle.Finish.ENAMEL);
        }
        int energy = this.menu.energy();
        MachineStatus.Tone tone = energy < MachineTuning.energy(RepairBoxBlockEntity.FE_PER_STEP) ? MachineStatus.Tone.BAD
                : energy < this.energyCapacity() / 5 ? MachineStatus.Tone.WARN : MachineStatus.Tone.GOOD;
        MachineStyle.icon(graphics, MachineStyle.Icon.ENERGY, x + ENERGY_X + 1, y + ICON_Y, energy <= 0);
        MachineStyle.energy(graphics, x + ENERGY_X, y + GAUGE_Y, GAUGE_H, energy, this.energyCapacity(), tone);
        MachineStyle.icon(graphics, MachineStyle.Icon.EXPERIENCE, x + TANK_X + 1, y + ICON_Y, this.menu.experience() <= 0);
        MachineStyle.flowMark(graphics, x + TANK_X + 10, y + ICON_Y + 1, MachineLayout.Flow.IN);
        MachineStyle.tank(graphics, x + TANK_X, y + GAUGE_Y, GAUGE_H, this.menu.experience(), this.tankCapacity(),
                MachineStyle.EXPERIENCE, MachineStyle.EXPERIENCE_LIT);
    }

    /** What it is doing, read off the slots and gauges: mending, or what it waits for. */
    private MachineStatus status() {
        if (this.menu.heldByRedstone()) {
            return MachineStatus.REDSTONE;
        }
        boolean worn = false;
        for (int i = 0; i < this.menu.slots.size() - 36; i++) {
            ItemStack stack = this.menu.slots.get(i).getItem();
            worn |= !stack.isEmpty() && stack.isDamaged();
        }
        if (!worn) {
            return MachineStatus.IDLE;
        }
        if (this.menu.energy() < MachineTuning.energy(RepairBoxBlockEntity.FE_PER_STEP)) {
            return MachineStatus.NO_POWER;
        }
        return this.menu.experience() < RepairBoxBlockEntity.MB_PER_STEP ? MachineStatus.NO_EXPERIENCE : MachineStatus.RUNNING;
    }

    private Component statusLine() {
        MachineStatus status = this.status();
        return status == MachineStatus.IDLE ? Component.translatable("gui.nookworks.repair_box.hint")
                : status == MachineStatus.RUNNING ? Component.translatable("gui.nookworks.repair_box.mending")
                : Component.translatable(status.translationKey());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        MachineStyle.fitText(graphics, this.font, this.title, this.titleLabelX, this.titleLabelY, this.imageWidth - 30, MachineStyle.TITLE, true);
        graphics.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        this.statusCut = MachineStyle.fitText(graphics, this.font, this.statusLine(), 18, MachineLayout.STATUS_Y + 2, this.imageWidth - 28,
                MachineStyle.toneColour(this.status().tone()), false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        String k = "gui.nookworks.machine.";
        List<Component> lines = null;
        if (this.isHovering(ENERGY_X, ICON_Y, 10, GAUGE_Y + GAUGE_H - ICON_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable(k + "energy", number(this.menu.energy()), number(this.energyCapacity())),
                    Component.translatable("gui.nookworks.repair_box.cost", MachineTuning.energy(RepairBoxBlockEntity.FE_PER_STEP),
                            RepairBoxBlockEntity.MB_PER_STEP, RepairBoxBlockEntity.REPAIR_PER_STEP).withStyle(ChatFormatting.GRAY));
        } else if (this.isHovering(TANK_X, ICON_Y, 14, GAUGE_Y + GAUGE_H - ICON_Y, mouseX, mouseY)) {
            lines = List.of(Component.translatable("gui.nookworks.repair_box.experience", number(this.menu.experience()),
                    number(this.tankCapacity())), Component.translatable(k + "flow.in").withStyle(ChatFormatting.GRAY));
        } else if (this.statusCut && this.isHovering(7, MachineLayout.STATUS_Y, this.imageWidth - 14, 12, mouseX, mouseY)) {
            lines = List.of(this.statusLine());
        }
        if (lines != null) {
            graphics.setTooltipForNextFrame(MachineStyle.wrap(this.font, lines), mouseX, mouseY);
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
    }

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
