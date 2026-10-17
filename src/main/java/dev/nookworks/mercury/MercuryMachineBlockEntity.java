package dev.nookworks.mercury;

import java.util.List;
import java.util.function.Consumer;

import dev.nookworks.machine.GuiTank;
import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.OutputRetry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Shared base of the mercury machines: FE-powered, slot 0 is the input, slots 1.. are outputs that are pushed
 * into the container below (or dropped), and a mercury buffer reachable through Mercury Pipes.
 */
public abstract class MercuryMachineBlockEntity extends MachineBlockEntity implements MercuryHandler {
    public static final int INPUT = 0;
    public static final int MERCURY_CAPACITY = 4_000;
    /** mB moved to or from the network per tick. */
    public static final int PIPE_RATE = 100;

    private final int[] inputSlots;
    private final int[] outputSlots;
    protected final MercuryStore mercury = new MercuryStore(MERCURY_CAPACITY, this::setChanged);
    protected int progress;
    /** When the outputs are next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    protected MercuryMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int outputs, int energyCapacity) {
        this(type, pos, state, 1, outputs, energyCapacity);
    }

    protected MercuryMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputs, int outputs,
            int energyCapacity) {
        this(type, pos, state, inputs, outputs, 0, energyCapacity);
    }

    /** {@code extra} slots after the outputs, the machine's own to use (the Retort's fill box). */
    protected MercuryMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int inputs, int outputs, int extra,
            int energyCapacity) {
        super(type, pos, state, inputs + outputs + extra, energyCapacity, 500, 0);
        this.inputSlots = new int[inputs];
        for (int i = 0; i < inputs; i++) {
            this.inputSlots[i] = i;
        }
        this.outputSlots = new int[outputs];
        for (int i = 0; i < outputs; i++) {
            this.outputSlots[i] = inputs + i;
        }
    }

    protected int[] inputSlots() {
        return this.inputSlots;
    }

    /** The work in hand belonged to what was in the input (or a reagent) before: start it again. */
    @Override
    protected void swapped(int slot) {
        if (slot < this.inputSlots.length) {
            this.progress = 0;
        }
    }

    public int progress() {
        return this.progress;
    }

    // ---- what the screen shows ----

    public int guiProgress() {
        return this.progress;
    }

    /** The mercury tank; machines with more tanks list them before it, as their layouts place them. */
    public GuiTank mercuryTank() {
        return new GuiTank(GuiTank.Kind.MERCURY, this.mercury.amount(), this.mercury.capacity());
    }

    public List<GuiTank> guiTanks() {
        return List.of(this.mercuryTank());
    }

    /**
     * Moves outputs out below - with no room there at the last try, not until a hopper's beat later
     * ({@link OutputRetry}). Returns true when every output slot is free.
     */
    protected boolean flush(ServerLevel level, BlockPos pos) {
        boolean due = this.outputRetry.due(level);
        boolean clear = true;
        boolean moved = false;
        for (int slot : this.outputSlots) {
            ItemStack out = this.items.get(slot);
            if (!out.isEmpty()) {
                ItemStack rest = due ? MachineIO.deliverBelow(level, pos, out) : out;
                if (rest.getCount() != out.getCount()) { // nothing moved: nothing to save or tell a comparator
                    this.items.set(slot, rest);
                    this.setChanged();
                    moved = true;
                }
                clear &= rest.isEmpty();
            }
        }
        if (due) {
            this.outputRetry.tried(level, !clear && !moved);
        }
        return clear;
    }

    /** Puts a result into the first output slot that takes it; false if none does (nothing is changed then). */
    protected boolean addOutput(ItemStack stack) {
        for (int slot : this.outputSlots) {
            ItemStack out = this.items.get(slot);
            if (out.isEmpty()) {
                this.items.set(slot, stack.copy());
                return true;
            }
            if (ItemStack.isSameItemSameComponents(out, stack) && out.getCount() + stack.getCount() <= out.getMaxStackSize()) {
                out.grow(stack.getCount());
                return true;
            }
        }
        return false;
    }

    /** How much mercury it holds, for a look at it (Jade). */
    public Component mercuryLine() {
        return this.mercuryText();
    }

    protected Component mercuryText() {
        return Component.translatable("nookworks.mercury.amount", this.mercury.amount(), this.mercury.capacity());
    }

    // ---- MercuryHandler ----

    @Override
    public int mercury() {
        return this.mercury.amount();
    }

    @Override
    public int mercuryCapacity() {
        return this.mercury.capacity();
    }

    @Override
    public int fillMercury(int amount, boolean simulate) {
        return this.mercuryRole() == Role.PRODUCER ? 0 : this.mercury.fill(amount, simulate);
    }

    @Override
    public int drainMercury(int amount, boolean simulate) {
        return this.mercuryRole() == Role.CONSUMER ? 0 : this.mercury.drain(amount, simulate);
    }

    @Override
    protected void heldFluids(Consumer<FluidStack> out) {
        super.heldFluids(out);
        if (this.mercury.amount() > 0) {
            out.accept(new FluidStack(MercuryFluids.SOURCE.get(), this.mercury.amount()));
        }
    }

    @Override
    protected void restoreFluid(FluidStack fluid) {
        if (fluid.getFluid() == MercuryFluids.SOURCE.get()) {
            this.mercury.set(fluid.getAmount());
        } else {
            super.restoreFluid(fluid);
        }
    }

    // ---- saving and sides ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        this.mercury.save(output);
        output.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.mercury.load(input);
        this.progress = input.getIntOr("Progress", 0);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? this.outputSlots : this.inputSlots;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != INPUT && side == Direction.DOWN;
    }
}
