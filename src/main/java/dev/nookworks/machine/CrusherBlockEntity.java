package dev.nookworks.machine;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crusher: turns one item into its {@link CrushingRecipe} result every 60 ticks at 20 FE/t (1,200 FE per item).
 * Input from any side; results go into the container below or drop out.
 */
public class CrusherBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    public static final int TICKS_PER_ITEM = 60;
    public static final int CAPACITY = 10_000;
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUTPUT};

    private int progress;
    /** When the output is next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    public CrusherBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CRUSHER.get(), pos, state, 2, CAPACITY, 200, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CrusherBlockEntity crusher) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!crusher.flush(server, pos)) {
            if (CrushingRecipe.of(crusher.items.get(INPUT)) == null) {
                // what it was crushing is gone while it waited for room: the next thing put in starts from nothing
                crusher.progress = 0;
            }
            crusher.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        CrushingRecipe recipe = CrushingRecipe.of(crusher.items.get(INPUT));
        if (recipe == null) {
            crusher.progress = 0;
            crusher.status = MachineStatus.IDLE;
            return;
        }
        if (!crusher.redstoneAllows()) {
            crusher.status = MachineStatus.REDSTONE;
            return;
        }
        if (crusher.energyStored() < crusher.workCost(FE_PER_TICK)) {
            crusher.status = MachineStatus.NO_POWER;
            return;
        }
        crusher.status = MachineStatus.RUNNING;
        crusher.payWork(FE_PER_TICK);
        if (++crusher.progress >= MachineTuning.time(TICKS_PER_ITEM)) {
            crusher.progress = 0;
            ItemStack out = recipe.outputFor(crusher.items.get(INPUT), level.getRandom());
            crusher.items.get(INPUT).shrink(1);
            crusher.items.set(OUTPUT, out);
            crusher.flush(server, pos);
        }
        crusher.setChanged();
    }

    @Override
    protected void swapped(int slot) {
        if (slot == INPUT) {
            this.progress = 0;
        }
    }

    /**
     * Moves the finished item out - with no room below at the last try, not until a hopper's beat later
     * ({@link OutputRetry}). Returns true when the output slot is free.
     */
    private boolean flush(ServerLevel level, BlockPos pos) {
        ItemStack out = this.items.get(OUTPUT);
        if (out.isEmpty()) {
            return true;
        }
        if (!this.outputRetry.due(level)) {
            return false;
        }
        ItemStack rest = MachineIO.deliverBelow(level, pos, out);
        boolean moved = rest.getCount() != out.getCount();
        if (moved) { // a full chest below: nothing moved, nothing to save or tell a comparator
            this.items.set(OUTPUT, rest);
            this.setChanged();
        }
        this.outputRetry.tried(level, !moved);
        return rest.isEmpty();
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.CRUSHER;
    }

    @Override
    public int guiEnergyRate() {
        return MachineTuning.workEnergy(FE_PER_TICK);
    }

    @Override
    public int guiProgress() {
        return this.progress;
    }

    @Override
    public int guiProgressMax() {
        return MachineTuning.time(TICKS_PER_ITEM);
    }

    @Override
    public Component status() {
        return this.progress > 0
                ? Component.translatable("nookworks.machine.crusher.working", this.energyText(), this.progress * 100 / MachineTuning.time(TICKS_PER_ITEM))
                : Component.translatable("nookworks.machine.idle", this.energyText());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUTPUT_SLOTS : INPUT_SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == INPUT && CrushingRecipe.of(stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT && side == Direction.DOWN;
    }
}
