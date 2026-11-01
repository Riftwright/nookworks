package dev.nookworks.machine;

import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import org.jspecify.annotations.Nullable;
import java.util.List;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/**
 * A machine as its screen sees it: which {@link MachineLayout} it uses, the container behind that layout's slots,
 * and the numbers and state the screen draws. Implemented by the machine's block entity.
 */
public interface MachineInfo {
    MachineLayout layout();

    /** The container whose slots, in order, are the layout's slots. */
    Container slots();

    /**
     * The slot a shift-click puts {@code stack} into before the others that would take it, or -1 for none: the first
     * input that takes it.
     */
    default int shiftClickSlot(ItemStack stack) {
        return -1;
    }

    int guiEnergy();

    /** 0 for a machine that needs no power: the screen then has no energy gauge. */
    int guiEnergyCapacity();

    /** FE per tick the machine uses while it works (for a generator, what it makes). */
    int guiEnergyRate();

    default int guiProgress() {
        return 0;
    }

    default int guiProgressMax() {
        return 0;
    }

    /**
     * One more number for a machine whose screen shows something the others do not: the Barter Terminal's piglins in the
     * room (low half) and how many it takes (high half).
     */
    default int guiExtra() {
        return 0;
    }

    /** The tanks the screen shows, in the order its layout places them ({@link MachineLayout#tanks()}); at most four. */
    /**
     * The tank behind gauge {@code index} of {@link #guiTanks()}, whole, for a bucket or bottle clicked on the gauge
     * ({@link GaugePouring}): in and out of any tank, the ones a pipe may not reach too. Null for the block's own fluid
     * capability; a mercury gauge goes by the machine's mercury store instead.
     */
    default @Nullable ResourceHandler<FluidResource> gaugeTank(int index) {
        return null;
    }

    default List<GuiTank> guiTanks() {
        return List.of();
    }

    MachineStatus guiStatus();

    RedstoneMode redstoneMode();

    void setRedstoneMode(RedstoneMode mode);
}
