package dev.nookworks.machine;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.block.ElectrolyticCellBlock;
import dev.nookworks.fluid.Tanks;
import dev.nookworks.mercury.MercuryMachineBlockEntity;
import dev.nookworks.mercury.MercuryNetwork;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidStacksResourceHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * Electrolytic Cell: a current through water, and what it carries does the work ({@link Electrolysis}).
 * <ul>
 * <li>As a mercury cell - the way caustic soda was made for a century: brine run over a floor of mercury, the current
 * drives the sodium into the mercury as amalgam, and the amalgam, washed with water in the cell's decomposer, gives
 * up caustic soda and goes back round as mercury. So the cell keeps a pool of mercury (at least
 * {@link #MERCURY_NEEDED} mB, drawn from its pipes) and loses only a little a run.</li>
 * <li>Electrowinning: zinc comes out of its ore on the cathode, as it does in a real zinc works - more of it than a
 * furnace gets.</li>
 * <li>And the tarnish comes off copper: weathered, oxidized copper of every shape, statues included, back to bright
 * metal one piece a run.</li>
 * </ul>
 * Water in by Copper Pipe (or a bucket), the results into the container below. It draws a lot of power.
 */
public class ElectrolyticCellBlockEntity extends MercuryMachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 80;
    public static final int TICKS = 100;
    public static final int WATER_TANK = 4_000;
    /** The pool of mercury a mercury-cell recipe needs to be standing in the cell. */
    public static final int MERCURY_NEEDED = 250;

    private final FluidStacksResourceHandler water = Tanks.tank(WATER_TANK, fluid -> fluid == Fluids.WATER,
            true, false, this::setChanged);

    public ElectrolyticCellBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ELECTROLYTIC_CELL.get(), pos, state, 1, 2, 20_000);
    }

    public FluidStacksResourceHandler waterHandler() {
        return this.water;
    }

    public int waterStored() {
        return this.water.getAmountAsInt(0);
    }

    /** Pours in a bucket's worth of water if there is room; whether it did. */
    public boolean pourBucket() {
        if (WATER_TANK - this.waterStored() < 1_000) {
            return false;
        }
        Tanks.set(this.water, Fluids.WATER, this.waterStored() + 1_000);
        return true;
    }

    @Override
    public Role mercuryRole() {
        return Role.CONSUMER;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ElectrolyticCellBlockEntity cell) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean lit = cell.showsLit(cell.work(server, pos));
        if (state.getValue(ElectrolyticCellBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(ElectrolyticCellBlock.LIT, lit), 3);
        }
    }

    private boolean work(ServerLevel level, BlockPos pos) {
        Electrolysis recipe = Electrolysis.of(this.items.get(INPUT));
        if (this.mercury.amount() < Math.max(this.mercury.capacity() / 2, recipe == null ? 0 : Math.max(MERCURY_NEEDED, recipe.mercury()))) {
            MercuryNetwork.pull(level, pos, this, PIPE_RATE);
        }
        MachineStatus blocked = !this.flush(level, pos) ? MachineStatus.OUTPUT_BLOCKED
                : recipe == null ? MachineStatus.IDLE
                : this.waterStored() < recipe.water() ? MachineStatus.LOW_WATER
                : recipe.needsMercury() && this.mercury.amount() < Math.max(MERCURY_NEEDED, recipe.mercury()) ? MachineStatus.NO_MERCURY
                : !this.redstoneAllows() ? MachineStatus.REDSTONE
                : this.energyStored() < this.workCost(FE_PER_TICK) ? MachineStatus.NO_POWER : null;
        if (blocked != null) {
            if (blocked == MachineStatus.IDLE || recipe == null) {
                this.progress = 0; // nothing to work on, even while it waits for room
            }
            this.status = blocked;
            return false;
        }
        this.status = MachineStatus.RUNNING;
        this.payWork(FE_PER_TICK);
        if (++this.progress >= MachineTuning.time(TICKS)) {
            this.progress = 0;
            ItemStack out = recipe.output(this.items.get(INPUT).copyWithCount(1));
            this.items.get(INPUT).shrink(1);
            Tanks.set(this.water, Fluids.WATER, this.waterStored() - recipe.water());
            if (recipe.mercury() > 0) {
                this.mercury.drain(recipe.mercury(), false);
            }
            if (level.getRandom().nextFloat() < recipe.bonusChance()) {
                out.grow(Math.min(recipe.bonus(), out.getMaxStackSize() - out.getCount())); // a stack at most
            }
            this.addOutput(out);
            this.flush(level, pos);
        }
        this.setChanged();
        return true;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && Electrolysis.of(stack) != null;
    }

    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        out.accept(Tanks.held(this.water));
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (!Tanks.restore(this.water, fluid)) {
            super.restoreFluid(fluid);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.water.serialize(output.child("Water"));
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        input.child("Water").ifPresent(this.water::deserialize);
    }

    // ---- the screen ----

    @Override
    public MachineLayout layout() {
        return MachineLayout.ELECTROLYTIC_CELL;
    }

    @Override
    public ResourceHandler<FluidResource> gaugeTank(int index) {
        return switch (index) {
            case 0 -> this.water;
            default -> null;
        };
    }

    @Override
    public List<GuiTank> guiTanks() {
        return List.of(new GuiTank(GuiTank.Kind.WATER, this.waterStored(), WATER_TANK), this.mercuryTank());
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS);
    }

    @Override
    public Component status() {
        return Component.translatable("nookworks.machine.electrolytic_cell.working", this.energyText(), MachineTuning.percent(this.progress, MachineTuning.time(TICKS)));
    }
}
