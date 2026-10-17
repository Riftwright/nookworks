package dev.nookworks.mercury;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** A plain mercury buffer for block entities; calls {@code onChanged} whenever the amount changes. */
public final class MercuryStore {
    private final int capacity;
    private final Runnable onChanged;
    private int amount;

    public MercuryStore(int capacity, Runnable onChanged) {
        this.capacity = capacity;
        this.onChanged = onChanged;
    }

    public int amount() {
        return this.amount;
    }

    public int capacity() {
        return this.capacity;
    }

    public int space() {
        return this.capacity - this.amount;
    }

    public void set(int amount) {
        this.amount = Math.max(0, Math.min(this.capacity, amount));
        this.onChanged.run();
    }

    public int fill(int amount, boolean simulate) {
        int moved = Math.max(0, Math.min(amount, this.space()));
        if (!simulate && moved > 0) {
            this.amount += moved;
            this.onChanged.run();
        }
        return moved;
    }

    public int drain(int amount, boolean simulate) {
        int moved = Math.max(0, Math.min(amount, this.amount));
        if (!simulate && moved > 0) {
            this.amount -= moved;
            this.onChanged.run();
        }
        return moved;
    }

    public void save(ValueOutput output) {
        output.putInt("Mercury", this.amount);
    }

    public void load(ValueInput input) {
        this.amount = Math.max(0, Math.min(this.capacity, input.getIntOr("Mercury", 0)));
    }
}
