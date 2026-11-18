package dev.nookworks.machine;

import org.jspecify.annotations.Nullable;

import dev.nookworks.menu.tab.LiquidTab;
import dev.nookworks.mercury.MercuryFluids;
import dev.nookworks.mercury.MercuryMachineBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ResourceHandler;
import dev.nookworks.port.transfer.FluidResource;

/**
 * A Nookworks machine's tanks as its screen's Liquid tab works them: one for each gauge, in the gauges' order. A filled
 * bucket or bottle goes only into a tank its screen shows flowing in (a machine takes its inputs, not what it makes:
 * the Sealed Retort's and Distiller's mercury, the Electric Furnace's experience); an empty one draws from any tank
 * that holds a liquid (not the Barter Terminal's air). What each tank holds is read off the menu's numbers, which the
 * server reads live and the client is sent, so the bottom slot's rule is the same on both sides.
 */
public final class MachineLiquids implements LiquidTab.Tanks {
    private final MachineMenu menu;

    public MachineLiquids(MachineMenu menu) {
        this.menu = menu;
    }

    /** Whether a machine laid out as {@code layout} has a Liquid tab: a tank of liquid on its screen. */
    public static boolean any(MachineLayout layout) {
        return layout.tanks().stream().anyMatch(tank -> tank.flow() != MachineLayout.Flow.NONE);
    }

    @Override
    public int count() {
        return Math.min(this.menu.layout().tanks().size(), LiquidTab.MAX_TANKS);
    }

    private boolean liquid(int tank) {
        return tank >= 0 && tank < this.count() && this.menu.layout().tanks().get(tank).flow() != MachineLayout.Flow.NONE
                && this.menu.tankKind(tank) != GuiTank.Kind.NETHER_AIR;
    }

    private boolean flowsIn(int tank) {
        return this.liquid(tank) && this.menu.layout().tanks().get(tank).flow() == MachineLayout.Flow.IN;
    }

    /** Whether a tank of {@code kind} holds {@code fluid}: by its gauge's rule, mercury by its own. */
    private static boolean holds(GuiTank.Kind kind, Fluid fluid) {
        return kind == GuiTank.Kind.MERCURY ? fluid.isSame(MercuryFluids.SOURCE.get()) : GaugePouring.holds(kind, fluid);
    }

    /** Only a machine with a tank shown flowing in takes anything (not the Electric Furnace, which gives experience). */
    @Override
    public boolean pours() {
        return this.menu.layout().tanks().stream().anyMatch(tank -> tank.flow() == MachineLayout.Flow.IN);
    }

    @Override
    public boolean drawable(int tank) {
        return this.liquid(tank) && this.menu.tankAmount(tank) > 0;
    }

    @Override
    public boolean takes(ItemStack filled) {
        Fluid fluid = LiquidTab.fluidIn(filled);
        if (fluid == null) {
            return false;
        }
        for (int i = 0; i < this.count(); i++) {
            if (this.flowsIn(i) && holds(this.menu.tankKind(i), fluid)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack draw(int tank, ItemStack empty, boolean simulate) {
        ItemStack out = this.drawn(tank, empty, simulate);
        if (!out.isEmpty() && !simulate && this.menu.machine() != null) {
            this.menu.machine().setChanged();
        }
        return out;
    }

    private ItemStack drawn(int tank, ItemStack empty, boolean simulate) {
        if (!this.liquid(tank)) {
            return ItemStack.EMPTY;
        }
        GuiTank.Kind kind = this.menu.tankKind(tank);
        if (kind == GuiTank.Kind.MERCURY) {
            return this.menu.machine() instanceof MercuryMachineBlockEntity mercury
                    ? GaugePouring.drawMercury(empty, mercury.mercuryStore(), simulate) : ItemStack.EMPTY;
        }
        ResourceHandler<FluidResource> handler = this.handler(tank);
        return handler == null ? ItemStack.EMPTY : GaugePouring.drawItem(empty, handler, fluid -> GaugePouring.holds(kind, fluid), simulate);
    }

    @Override
    public ItemStack pour(ItemStack filled, boolean simulate) {
        for (int i = 0; i < this.count(); i++) {
            if (!this.flowsIn(i)) {
                continue;
            }
            GuiTank.Kind kind = this.menu.tankKind(i);
            ItemStack out;
            if (kind == GuiTank.Kind.MERCURY) {
                out = this.menu.machine() instanceof MercuryMachineBlockEntity mercury
                        ? GaugePouring.pourMercury(filled, mercury.mercuryStore(), simulate) : ItemStack.EMPTY;
            } else {
                ResourceHandler<FluidResource> handler = this.handler(i);
                out = handler == null ? ItemStack.EMPTY : GaugePouring.pourItem(filled, handler, fluid -> GaugePouring.holds(kind, fluid), simulate);
            }
            if (!out.isEmpty()) {
                if (!simulate && this.menu.machine() != null) {
                    this.menu.machine().setChanged();
                }
                return out;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The tank behind gauge {@code tank}: the machine's own, else its block's fluid capability. */
    private @Nullable ResourceHandler<FluidResource> handler(int tank) {
        if (!(this.menu.machine() instanceof BlockEntity machine) || !(machine instanceof MachineInfo info)) {
            return null;
        }
        ResourceHandler<FluidResource> own = info.gaugeTank(tank);
        if (own != null || machine.getLevel() == null) {
            return own;
        }
        return machine.getLevel().getCapability(Capabilities.Fluid.BLOCK, machine.getBlockPos(), null);
    }
}
