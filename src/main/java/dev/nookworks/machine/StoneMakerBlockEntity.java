package dev.nookworks.machine;

import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.item.ItemUtil;

/**
 * Stone Maker: makes one cobblestone every 20 ticks at 20 FE/t (400 FE each). Cobblestone goes into the
 * container below; without one it collects inside (up to a stack) instead of littering the floor.
 */
public class StoneMakerBlockEntity extends MachineBlockEntity implements MachineInfo {
    public static final int FE_PER_TICK = 20;
    public static final int TICKS_PER_ITEM = 20;
    public static final int CAPACITY = 10_000;
    private static final int[] SLOTS = {0};

    private int progress;
    /** When what it holds is next passed on below: a hopper's beat after a try that found no room. */
    private final OutputRetry outputRetry = new OutputRetry();

    public StoneMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STONE_MAKER.get(), pos, state, 1, CAPACITY, 200, 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StoneMakerBlockEntity maker) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        maker.flush(server, pos);
        ItemStack held = maker.items.get(0);
        if (held.getCount() >= held.getMaxStackSize()) {
            maker.status = MachineStatus.OUTPUT_BLOCKED;
            return;
        }
        if (!maker.redstoneAllows()) {
            maker.status = MachineStatus.REDSTONE;
            return;
        }
        if (maker.energyStored() < maker.workCost(FE_PER_TICK)) {
            maker.status = MachineStatus.NO_POWER;
            return;
        }
        maker.status = MachineStatus.RUNNING;
        maker.payWork(FE_PER_TICK);
        if (++maker.progress >= MachineTuning.time(TICKS_PER_ITEM)) {
            maker.progress = 0;
            if (held.isEmpty()) {
                maker.items.set(0, new ItemStack(Items.COBBLESTONE));
            } else {
                held.grow(1);
            }
            maker.flush(server, pos);
            maker.setChanged();
        } else {
            savePower(maker); // only its progress to keep: no comparator change to tell the neighbours of every tick
        }
    }

    /** Into the container below, if there is one - with no room at the last try, a hopper's beat later ({@link OutputRetry}). */
    private void flush(ServerLevel level, BlockPos pos) {
        ItemStack held = this.items.get(0);
        if (held.isEmpty() || !this.outputRetry.due(level)) {
            return;
        }
        boolean moved = false;
        var handler = level.getCapability(Capabilities.Item.BLOCK, pos.below(), Direction.UP);
        if (handler != null) {
            ItemStack rest = ItemUtil.insertItemReturnRemaining(handler, held.copy(), false, null);
            if (rest.getCount() != held.getCount()) { // a full chest below: nothing moved, nothing to tell anyone
                this.items.set(0, rest);
                this.setChanged();
                moved = true;
            }
        }
        this.outputRetry.tried(level, !moved);
    }

    @Override
    public MachineLayout layout() {
        return MachineLayout.STONE_MAKER;
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
        return Component.translatable("nookworks.machine.stone_maker.status", this.energyText(), this.items.get(0).getCount());
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
        return SLOTS;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return false;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return true;
    }
}
