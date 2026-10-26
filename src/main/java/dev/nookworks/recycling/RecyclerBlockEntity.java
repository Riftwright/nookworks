package dev.nookworks.recycling;

import dev.nookworks.machine.MachineBlockEntity;
import dev.nookworks.machine.MachineIO;
import dev.nookworks.machine.MachineInfo;
import dev.nookworks.machine.MachineLayout;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.machine.MachineTuning;
import dev.nookworks.machine.OutputRetry;
import dev.nookworks.registry.ModRecycling;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * Recycler (the lower block of the two): metal junk ({@link RecyclingRecipe}) into scrap - or gold into gold nuggets -
 * at 40 FE/t, a piece taking as long as its metal asks ({@link RecyclingRecipe#ticks()}: a rail a second, a chestplate
 * six). What goes in comes through the intake on top (or any side of either block); what
 * comes out goes into the container below or drops out. Two output slots, so scrap and gold nuggets can wait side by side.
 */
public class RecyclerBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 40;
    public static final int TICKS_PER_PIECE = 80;
    public static final int CAPACITY = 20_000;
    public static final int INPUT = 0;
    public static final int OUT_A = 1;
    public static final int OUT_B = 2;
    private static final int[] INPUT_SLOTS = {INPUT};
    private static final int[] OUTPUT_SLOTS = {OUT_A, OUT_B};

    private int progress;
    private final OutputRetry outputRetry = new OutputRetry();

    public RecyclerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRecycling.RECYCLER_ENTITY.get(), pos, state, 3, CAPACITY, 400, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RecyclerBlockEntity recycler) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        recycler.flush(server, pos);
        RecyclingRecipe recipe = RecyclingRecipe.of(recycler.items.get(INPUT));
        if (recipe == null) {
            recycler.progress = 0;
            recycler.status = MachineStatus.IDLE;
            return;
        }
        if (!recycler.roomFor(new ItemStack(recipe.result(), Math.min(64, (int) Math.ceil(recipe.expected()) + 1)))) {
            recycler.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        if (!recycler.redstoneAllows()) {
            recycler.status = MachineStatus.REDSTONE;
            return;
        }
        if (recycler.energyStored() < recycler.workCost(FE_PER_TICK)) {
            recycler.status = MachineStatus.NO_POWER;
            return;
        }
        recycler.status = MachineStatus.RUNNING;
        recycler.payWork(FE_PER_TICK);
        if (++recycler.progress >= MachineTuning.time(recipe.ticks())) {
            recycler.progress = 0;
            ItemStack out = recipe.outputFor(recycler.items.get(INPUT), level.getRandom());
            recycler.items.get(INPUT).shrink(1);
            recycler.store(out);
            recycler.flush(server, pos);
            recycler.setChanged(); // its contents changed: the comparators by either half are told
        } else {
            // only its progress and power to keep: what a comparator reads, the contents, is as it was, so the comparators
            // round both halves are not told every tick it works (the recycler review's cost, dropped 2026-10-22)
            savePower(recycler);
        }
    }

    /** Whether an output slot is free, or holds the same item with room for as many as this could make. */
    private boolean roomFor(ItemStack most) {
        for (int slot : OUTPUT_SLOTS) {
            ItemStack held = this.items.get(slot);
            if (held.isEmpty() || ItemStack.isSameItemSameComponents(held, most) && held.getCount() + most.getCount() <= held.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void store(ItemStack out) {
        for (int slot : OUTPUT_SLOTS) {
            if (out.isEmpty()) {
                return;
            }
            ItemStack held = this.items.get(slot);
            if (held.isEmpty()) {
                this.items.set(slot, out);
                return;
            }
            if (ItemStack.isSameItemSameComponents(held, out)) {
                int move = Math.min(out.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(move);
                out.shrink(move);
            }
        }
    }

    /** Passes what is made on below (a hopper's beat after a try that found no room, as the Crusher does). */
    private void flush(ServerLevel level, BlockPos pos) {
        if (this.items.get(OUT_A).isEmpty() && this.items.get(OUT_B).isEmpty() || !this.outputRetry.due(level)) {
            return;
        }
        boolean moved = false;
        for (int slot : OUTPUT_SLOTS) {
            ItemStack out = this.items.get(slot);
            if (!out.isEmpty()) {
                ItemStack rest = deliver(level, pos, out);
                if (rest.getCount() != out.getCount()) {
                    this.items.set(slot, rest);
                    moved = true;
                }
            }
        }
        if (moved) {
            this.setChanged();
        }
        this.outputRetry.tried(level, !moved);
    }

    /**
     * Into the container below, or dropped where there is room for it: below the body, else on top of the intake (not
     * at the body's top as a one-block machine's, which is inside the intake), else - a hopper feeding the intake from
     * above - beside the body, the first side open; with none, on top all the same.
     */
    private static ItemStack deliver(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemStack rest = MachineIO.intoBelow(level, pos, stack);
        if (rest.getCount() != stack.getCount() || level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP) != null) {
            return rest;
        }
        if (open(level, pos.below())) {
            MachineIO.popOut(level, pos, stack.copy());
            return ItemStack.EMPTY;
        }
        BlockPos at = pos.above(2);
        if (!open(level, at)) {
            for (Direction side : Direction.Plane.HORIZONTAL) {
                if (open(level, pos.relative(side))) {
                    at = pos.relative(side);
                    break;
                }
            }
        }
        level.addFreshEntity(new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, stack.copy(), 0.0, 0.0, 0.0));
        return ItemStack.EMPTY;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The intake's handlers are this block entity's (ModCapabilities): they come and go with it, so the intake's place
     * is told too, as NeoForge asks, or a cache of them (a pipe's, a network's) would keep one gone.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (this.level != null) {
            this.level.invalidateCapabilities(this.worldPosition.above());
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (this.level != null) {
            this.level.invalidateCapabilities(this.worldPosition.above());
        }
    }

    /** A comparator by the intake reads the body too, so it hears of the body's changes. */
    @Override
    public void setChanged() {
        super.setChanged();
        if (this.level != null && !this.level.isClientSide()) {
            this.level.updateNeighbourForOutputSignal(this.worldPosition.above(), this.getBlockState().getBlock());
        }
    }

    /** The time the piece in the input takes, or the longest. */
    private int pieceTicks() {
        RecyclingRecipe recipe = RecyclingRecipe.of(this.items.get(INPUT));
        return MachineTuning.time(recipe == null ? TICKS_PER_PIECE : recipe.ticks());
    }

    @Override
    protected void swapped(int slot) {
        if (slot == INPUT) {
            this.progress = 0;
        }
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.RECYCLER;
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
        return this.pieceTicks();
    }

    @Override
    public Component status() {
        return this.progress > 0
                ? Component.translatable("nookworks.machine.recycler.working", this.energyText(),
                        MachineTuning.percent(this.progress, this.pieceTicks()))
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
        return slot == INPUT && RecyclingRecipe.of(stack) != null;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot != INPUT && side == Direction.DOWN;
    }
}
