package dev.nookworks.port.transfer;

import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.energy.SimpleEnergyHandler}: a store with a capacity and a most it takes
 * in and gives out per call. {@link #onEnergyChanged} is called after a root commit that changed the amount, and at once
 * for {@link #set}. Saved as an int {@code energy}, as 26.3 saves it.
 */
public class SimpleEnergyHandler implements EnergyHandler {
    protected int energy;
    protected int capacity;
    protected int maxInsert;
    protected int maxExtract;
    private final Journal journal = new Journal();

    public SimpleEnergyHandler(int capacity) {
        this(capacity, capacity);
    }

    public SimpleEnergyHandler(int capacity, int maxTransfer) {
        this(capacity, maxTransfer, maxTransfer);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract) {
        this(capacity, maxInsert, maxExtract, 0);
    }

    public SimpleEnergyHandler(int capacity, int maxInsert, int maxExtract, int energy) {
        TransferPreconditions.checkNonNegative(capacity);
        TransferPreconditions.checkNonNegative(maxInsert);
        TransferPreconditions.checkNonNegative(maxExtract);
        TransferPreconditions.checkNonNegative(energy);
        this.capacity = capacity;
        this.maxInsert = maxInsert;
        this.maxExtract = maxExtract;
        this.energy = energy;
    }

    public void serialize(ValueOutput output) {
        output.putInt("energy", this.energy);
    }

    public void deserialize(ValueInput input) {
        this.energy = Math.max(0, input.getIntOr("energy", 0));
    }

    /** Sets the amount outright, outside any transaction, and reports the change at once. */
    public void set(int amount) {
        TransferPreconditions.checkNonNegative(amount);
        if (this.energy != amount) {
            int before = this.energy;
            this.energy = amount;
            onEnergyChanged(before);
        }
    }

    /** Called after the amount changed: once per root commit that changed it, or by {@link #set}. */
    protected void onEnergyChanged(int previousAmount) {}

    @Override
    public long getAmountAsLong() {
        return this.energy;
    }

    @Override
    public long getCapacityAsLong() {
        return this.capacity;
    }

    @Override
    public int insert(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        int moved = Math.min(this.capacity - this.energy, Math.min(amount, this.maxInsert));
        if (moved > 0) {
            this.journal.updateSnapshots(transaction);
            this.energy += moved;
            return moved;
        }
        return 0;
    }

    @Override
    public int extract(int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonNegative(amount);
        int moved = Math.min(this.energy, Math.min(amount, this.maxExtract));
        if (moved > 0) {
            this.journal.updateSnapshots(transaction);
            this.energy -= moved;
            return moved;
        }
        return 0;
    }

    private final class Journal extends SnapshotJournal<Integer> {
        @Override
        protected Integer createSnapshot() {
            return SimpleEnergyHandler.this.energy;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            SimpleEnergyHandler.this.energy = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            if (SimpleEnergyHandler.this.energy != originalState) {
                onEnergyChanged(originalState);
            }
        }
    }
}
