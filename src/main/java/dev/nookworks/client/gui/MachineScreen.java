package dev.nookworks.client.gui;

import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksConfig;
import dev.nookworks.machine.Electrolysis;
import dev.nookworks.machine.ElectrolyticCellBlockEntity;
import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineMenu;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.mercury.RetortBlockEntity;
import dev.nookworks.mercury.RetortBottles;
import dev.nookworks.mercury.StirringTankBlockEntity;
import dev.nookworks.registry.ModItems;
import dev.nookworks.NookworksNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * The screen of every FE machine, drawn from its {@link MachineLayout}: a recessed bay holding power down the left, the
 * work in the middle (an arrow from inputs to outputs, a flame, a beam) and the machine's tanks on the right, each gauge
 * headed by an icon for what it measures; the redstone mode in the corner; and under the bay a status display saying
 * what the machine is doing - or why it is not. Empty slots that take one particular thing show it, faded.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int ENERGY_X = 8;
    /** Icons stand in the top of the bay; gauges run from under them to its bottom. */
    private static final int ICON_Y = MachineLayout.BAY_TOP + 3;
    private static final int GAUGE_Y = ICON_Y + 10;
    private static final int GAUGE_H = MachineLayout.BAY_BOTTOM - 3 - GAUGE_Y;
    private static final int TANK_W = 14;
    private static final int DISPLAY_X = 7;
    private static final int DISPLAY_H = 12;
    private static final int RS_X = RedstoneButton.X;
    private static final int RS_Y = 3;

    /** Set while drawing when the status or the title had to be cut short: its tooltip shows the whole. */
    private boolean statusCut;
    private boolean titleCut;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, MachineLayout.WIDTH, MachineLayout.HEIGHT);
        this.inventoryLabelY = MachineLayout.INVENTORY_Y - 11;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        this.addRenderableWidget(new RedstoneButton(this.leftPos + RS_X, this.topPos + RS_Y, this.menu::redstoneMode,
                () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_REDSTONE)));
        if (this.layout() == MachineLayout.RETORT) {
            this.addRenderableWidget(new RetortBottlesButton(this.leftPos + this.titleEnd(), this.topPos + RS_Y,
                    () -> RetortBottles.byId(this.menu.extra()),
                    () -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MachineMenu.BUTTON_RETORT_BOTTLES)));
        }
    }

    /** Where the title has to stop: at the buttons in the top right corner. */
    private int titleEnd() {
        return this.layout() == MachineLayout.RETORT ? RS_X - RetortBottlesButton.SIZE - 2 : RS_X;
    }

    /** Where the Laser's beam ends: the screen's edge, or short of a tank (its hover area as long as it is drawn). */
    private int beamEnd(MachineLayout layout) {
        return layout.tanks().isEmpty() ? this.imageWidth - 10 : layout.tanks().get(0).x() - 4;
    }

    private MachineLayout layout() {
        return this.menu.layout();
    }

    // ---- drawing ----

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractBackground(g, mouseX, mouseY, a);
        int x = this.leftPos;
        int y = this.topPos;
        MachineLayout layout = this.layout();
        MachineStyle.panel(g, x, y, this.imageWidth, this.imageHeight);
        MachineStyle.bay(g, x + 5, y + MachineLayout.BAY_TOP, this.imageWidth - 10, MachineLayout.BAY_BOTTOM - MachineLayout.BAY_TOP);
        MachineStyle.display(g, x + DISPLAY_X, y + MachineLayout.STATUS_Y, this.imageWidth - 2 * DISPLAY_X, DISPLAY_H,
                this.menu.status().tone());
        for (int i = 0; i < this.menu.slots.size(); i++) {
            Slot slot = this.menu.slots.get(i);
            if (i < layout.slotCount() && layout.spots().get(i).fitting()) {
                MachineStyle.fittingSlot(g, x + slot.x - 1, y + slot.y - 1, slot.hasItem());
            } else {
                MachineStyle.slot(g, x + slot.x - 1, y + slot.y - 1);
            }
        }
        if (this.menu.energyCapacity() > 0) {
            MachineStyle.icon(g, MachineStyle.Icon.ENERGY, x + ENERGY_X + 1, y + ICON_Y, this.menu.energy() <= 0);
            MachineStyle.energy(g, x + ENERGY_X, y + GAUGE_Y, GAUGE_H, this.menu.energy(), this.menu.energyCapacity(),
                    this.energyTone());
        }
        int gx = x + layout.gaugeX();
        int gy = y + layout.gaugeY();
        float done = this.menu.progressMax() > 0 ? Math.min(1.0F, (float) this.menu.progress() / this.menu.progressMax()) : 0.0F;
        int beamEnd = this.beamEnd(layout);
        switch (layout.gauge()) {
            case ARROW -> MachineStyle.arrow(g, gx, gy, done);
            case FLAME -> MachineStyle.flame(g, gx, gy, done);
            case BEAM -> MachineStyle.beam(g, gx, gy, beamEnd - layout.gaugeX(), done, this.menu.status() == MachineStatus.RUNNING);
            case NONE -> {}
        }
        for (int i = 0; i < layout.tanks().size(); i++) {
            MachineLayout.TankSpot spot = layout.tanks().get(i);
            GuiTank.Kind kind = this.menu.tankKind(i);
            int amount = this.menu.tankAmount(i);
            MachineStyle.icon(g, icon(kind), x + spot.x() + 1, y + ICON_Y, amount <= 0);
            MachineStyle.flowMark(g, x + spot.x() + 10, y + ICON_Y + 1, spot.flow());
            MachineStyle.tank(g, x + spot.x(), y + GAUGE_Y, GAUGE_H, amount, this.menu.tankCapacity(i), MachineStyle.colour(kind),
                    MachineStyle.lit(kind));
        }
        if (layout.belowX() >= 0) {
            MachineStyle.below(g, x + layout.belowX(), y + layout.belowY());
        }
    }

    private static MachineStyle.Icon icon(GuiTank.Kind kind) {
        return switch (kind) {
            case MERCURY -> MachineStyle.Icon.MERCURY;
            case WATER -> MachineStyle.Icon.WATER;
            case VEGETABLE_OIL -> MachineStyle.Icon.VEGETABLE_OIL;
            case BIODIESEL -> MachineStyle.Icon.BIODIESEL;
            case LIQUID_FUEL -> MachineStyle.Icon.LIQUID_FUEL;
            case EXPERIENCE -> MachineStyle.Icon.EXPERIENCE;
            case NETHER_AIR -> MachineStyle.Icon.NETHER_AIR;
        };
    }

    /** Empty slots that take one thing show that thing, faded: the Sifter's mesh, the Stirring Tank's caustic soda. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
        if (slot.index < this.layout().slotCount() && !slot.hasItem()) {
            ItemStack hint = MachineHints.hint(this.layout(), slot.index, this.menu);
            if (!hint.isEmpty()) {
                MachineStyle.ghost(g, hint, slot.x, slot.y);
                return;
            }
        }
        super.extractSlot(g, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int xm, int ym) {
        int titleRoom = this.titleEnd() - 4 - this.titleLabelX;
        this.titleCut = MachineStyle.fitText(g, this.font, this.title, this.titleLabelX, this.titleLabelY, titleRoom, MachineStyle.TITLE, true);
        g.text(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, MachineStyle.DIM, false);
        if (this.layout().gauge() == MachineLayout.Gauge.BEAM) {
            // the beam's reach written under it, not only on hover: the Laser's bay read as empty (r21, 2026-11-06)
            g.text(this.font, Component.translatable("gui.nookworks.machine.beam", this.menu.progress(), this.menu.progressMax()),
                    this.layout().gaugeX(), this.layout().gaugeY() + 10, MachineStyle.DIM, false);
        }
        MachineStatus status = this.menu.status();
        int room = this.imageWidth - 2 * DISPLAY_X - 14;
        this.statusCut = MachineStyle.fitText(g, this.font, this.statusLine(), DISPLAY_X + 11, MachineLayout.STATUS_Y + 2, room,
                MachineStyle.toneColour(status.tone()), false);
    }

    private Component statusLine() {
        MachineStatus status = this.menu.status();
        if (this.layout() == MachineLayout.BARTER_TERMINAL && (status == MachineStatus.RUNNING || status == MachineStatus.IDLE
                || status == MachineStatus.CROWDED)) {
            // how many piglins the room holds, of how many it takes
            int piglins = this.menu.extra() & 0xFFFF;
            int room = this.menu.extra() >>> 16;
            return Component.translatable("gui.nookworks.machine.status.barter_terminal." + status.name().toLowerCase(Locale.ROOT),
                    piglins, room);
        }
        if (status == MachineStatus.RUNNING && this.layout().gauge() == MachineLayout.Gauge.ARROW && this.menu.progressMax() > 0) {
            return Component.translatable("gui.nookworks.machine.running_percent", MachineTuning.percent(this.menu.progress(), this.menu.progressMax()));
        }
        if (status == MachineStatus.NO_MERCURY && this.layout() == MachineLayout.ELECTROLYTIC_CELL) {
            // A mercury cell runs on a pool of mercury standing in it and loses only a little of it a run: say both, or
            // "not enough mercury" reads as though a run took the whole 250 mB.
            var recipe = Electrolysis.of(this.menu.slots.get(0).getItem());
            return Component.translatable("gui.nookworks.machine.status.no_mercury.cell",
                    Math.max(ElectrolyticCellBlockEntity.MERCURY_NEEDED, recipe == null ? 0 : recipe.mercury()), recipe == null ? 0 : recipe.mercury());
        }
        return Component.translatable(status.translationKey());
    }

    /** More about the status than its line has room for, shown when the pointer is on it; null for most. */
    private @Nullable Component statusDetail() {
        if (this.menu.status() == MachineStatus.TOO_MANY_CABLES) {
            return Component.translatable("gui.nookworks.machine.status.too_many_cables.detail",
                    NookworksConfig.ENERGY_CABLE_REACH.getAsInt());
        }
        if (this.menu.status() == MachineStatus.NO_REAGENT) {
            // which reagent, as the cell says how much mercury: "Missing a reagent" alone left it to be worked out
            Component needed = switch (this.layout()) {
                case RETORT -> RetortBlockEntity.missingReagentNames(this.menu.slots.get(RetortBlockEntity.INPUT).getItem(),
                        List.of(this.menu.slots.get(RetortBlockEntity.FIRST_REAGENT).getItem(),
                                this.menu.slots.get(RetortBlockEntity.FIRST_REAGENT + 1).getItem()));
                case STIRRING_TANK -> new ItemStack(ModItems.CAUSTIC_SODA.get()).getHoverName();
                default -> null;
            };
            if (needed != null) {
                return Component.translatable("gui.nookworks.machine.status.no_reagent.detail", needed);
            }
        }
        if (this.menu.status() == MachineStatus.NO_POWER && this.menu.energyRate() > 0) {
            // a broken cable or too few Generators: both read "Not enough power" (one Generator under a mercury line, the
            // play-test of 2026-10-19, looked like a broken cable)
            return Component.translatable("gui.nookworks.machine.status.no_power.detail", this.menu.energyRate());
        }
        if (this.menu.status() == MachineStatus.NO_POWER) {
            return null; // its detail needs the rate, which this machine does not show
        }
        if (this.menu.status() == MachineStatus.INCOMPLETE) {
            // what the tower lacks: "Tower incomplete" alone left a player who built it wrong guessing (play-test, 2026-10-19)
            return Component.translatable("nookworks.machine.distiller.incomplete");
        }
        if (this.menu.status() == MachineStatus.LOW_OIL) {
            // how much a batch takes, as the cell says how much mercury it wants
            return Component.translatable("gui.nookworks.machine.status.low_oil.detail", StirringTankBlockEntity.OIL_PER_BATCH);
        }
        // what more a status has to say: the Barter Terminal's own lines first (its crowded room), then the status's
        String key = this.menu.status().translationKey() + ".detail";
        String own = "gui.nookworks.machine.status." + this.layout().name().toLowerCase(Locale.ROOT) + "."
                + this.menu.status().name().toLowerCase(Locale.ROOT) + ".detail";
        return Language.getInstance().has(own) ? Component.translatable(own)
                : Language.getInstance().has(key) ? Component.translatable(key) : null;
    }

    /** Green while there is power for the work, amber when it is running low, red when it cannot run. */
    private MachineStatus.Tone energyTone() {
        int energy = this.menu.energy();
        int capacity = this.menu.energyCapacity();
        if (this.layout().generates()) {
            return energy > 0 ? MachineStatus.Tone.GOOD : MachineStatus.Tone.WARN;
        }
        if (energy < Math.max(1, this.menu.energyRate())) {
            return MachineStatus.Tone.BAD;
        }
        return energy < capacity / 5 ? MachineStatus.Tone.WARN : MachineStatus.Tone.GOOD;
    }

    // ---- tooltips ----

    @Override
    protected void extractTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        List<Component> lines = this.tooltipAt(mouseX, mouseY);
        if (!lines.isEmpty() && this.menu.getCarried().isEmpty()) {
            g.setTooltipForNextFrame(MachineStyle.wrap(this.font, lines), mouseX, mouseY);
            return;
        }
        super.extractTooltip(g, mouseX, mouseY);
    }

    private List<Component> tooltipAt(int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();
        MachineLayout layout = this.layout();
        String k = "gui.nookworks.machine.";
        if (this.menu.energyCapacity() > 0 && this.isHovering(ENERGY_X, ICON_Y, 10, GAUGE_Y + GAUGE_H - ICON_Y, mouseX, mouseY)) {
            lines.add(Component.translatable(k + "energy", number(this.menu.energy()), number(this.menu.energyCapacity())));
            int rate = this.menu.energyRate();
            if (layout.generates()) {
                lines.add(Component.translatable(k + "makes", number(rate)).withStyle(ChatFormatting.GRAY));
            } else if (layout == MachineLayout.PLANTER) {
                lines.add(Component.translatable(k + "uses_per_plant", number(rate)).withStyle(ChatFormatting.GRAY));
            } else if (rate > 0) {
                lines.add(Component.translatable(k + "uses", number(rate)).withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable(k + "lasts", number(this.menu.energy() / rate / 20)).withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (layout.gauge() != MachineLayout.Gauge.NONE && this.overGauge(layout, mouseX, mouseY)) {
            int max = Math.max(1, this.menu.progressMax());
            switch (layout.gauge()) {
                case ARROW -> lines.add(Component.translatable(k + "progress", MachineTuning.percent(this.menu.progress(), max)));
                case FLAME -> lines.add(Component.translatable(k + "burning", number(this.menu.progress() / 20)));
                case BEAM -> lines.add(Component.translatable(k + "beam", this.menu.progress(), this.menu.progressMax()));
                case NONE -> {}
            }
            return lines;
        }
        for (int i = 0; i < layout.tanks().size(); i++) {
            MachineLayout.TankSpot spot = layout.tanks().get(i);
            if (this.isHovering(spot.x(), ICON_Y, TANK_W, GAUGE_Y + GAUGE_H - ICON_Y, mouseX, mouseY)) {
                if (this.menu.tankKind(i) == GuiTank.Kind.NETHER_AIR) {
                    // air is not measured out in buckets: how long it lasts with nothing making more
                    int seconds = this.menu.tankAmount(i) / 20;
                    lines.add(Component.translatable(k + "air", Component.translatable(this.menu.tankKind(i).translationKey()),
                            seconds / 60, String.format(Locale.ROOT, "%02d", seconds % 60)));
                } else {
                    lines.add(Component.translatable(k + "tank", Component.translatable(this.menu.tankKind(i).translationKey()),
                            number(this.menu.tankAmount(i)), number(this.menu.tankCapacity(i))));
                }
                if (spot.flow() != MachineLayout.Flow.NONE) {
                    lines.add(Component.translatable(k + "flow." + spot.flow().name().toLowerCase(Locale.ROOT)).withStyle(ChatFormatting.GRAY));
                }
                if (this.menu.tankKind(i) != GuiTank.Kind.NETHER_AIR) {
                    lines.add(Component.translatable(k + "pour").withStyle(ChatFormatting.GRAY));
                }
                String hint = "gui.nookworks.machine.hint." + layout.name().toLowerCase(Locale.ROOT) + "." + i;
                if (Language.getInstance().has(hint)) {
                    lines.add(Component.translatable(hint).withStyle(ChatFormatting.GRAY));
                }
                return lines;
            }
        }
        if (layout.belowX() >= 0 && this.isHovering(layout.belowX() - 4, layout.belowY() - 2, 15, 9, mouseX, mouseY)) {
            // a machine whose results do not drop out (the Stone Maker keeps its cobblestone) says so in its own line
            String own = k + "below." + layout.name().toLowerCase(Locale.ROOT);
            lines.add(Component.translatable(Language.getInstance().has(own) ? own : k + "below"));
            return lines;
        }
        Component detail = this.statusDetail();
        if ((this.statusCut || detail != null)
                && this.isHovering(DISPLAY_X, MachineLayout.STATUS_Y, this.imageWidth - 2 * DISPLAY_X, DISPLAY_H, mouseX, mouseY)) {
            lines.add(this.statusLine());
            if (detail != null) {
                lines.add(detail.copy().withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        if (this.titleCut && this.isHovering(this.titleLabelX, this.titleLabelY, this.titleEnd() - 4 - this.titleLabelX, 9, mouseX, mouseY)) {
            lines.add(this.title);
            return lines;
        }
        if (this.hoveredSlot != null && this.hoveredSlot.index < layout.slotCount() && !this.hoveredSlot.hasItem()) {
            ItemStack hint = MachineHints.hint(layout, this.hoveredSlot.index, this.menu);
            if (!hint.isEmpty()) {
                lines.add(Component.translatable(k + "slot_takes", hint.getHoverName()).withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    /**
     * A bucket or bottle on the cursor clicked on a tank's gauge pours into it or draws out of it (GaugePouring), the
     * server deciding. Only on the gauge itself, as drawn - not its icon, nor anything round it.
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !this.menu.getCarried().isEmpty()) {
            MachineLayout layout = this.layout();
            for (int i = 0; i < layout.tanks().size(); i++) {
                if (this.menu.tankKind(i) != GuiTank.Kind.NETHER_AIR
                        && this.isHovering(layout.tanks().get(i).x(), GAUGE_Y, TANK_W, GAUGE_H, event.x(), event.y())) {
                    ClientPacketDistributor.sendToServer(new NookworksNetwork.PourGauge(this.menu.containerId, i));
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private boolean overGauge(MachineLayout layout, int mouseX, int mouseY) {
        return switch (layout.gauge()) {
            case ARROW -> this.isHovering(layout.gaugeX(), layout.gaugeY(), MachineStyle.ARROW_W, MachineStyle.ARROW_H, mouseX, mouseY);
            case FLAME -> this.isHovering(layout.gaugeX(), layout.gaugeY(), 16, 16, mouseX, mouseY);
            case BEAM -> this.isHovering(layout.gaugeX(), layout.gaugeY() - 6, this.beamEnd(layout) - layout.gaugeX(), 14, mouseX, mouseY);
            case NONE -> false;
        };
    }

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }
}
