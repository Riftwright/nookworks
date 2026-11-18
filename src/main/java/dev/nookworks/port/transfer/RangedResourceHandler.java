package dev.nookworks.port.transfer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.RangedResourceHandler}: indices {@code start} (inclusive) to {@code end}
 * (exclusive) of another handler, shown as a handler of their own counted from 0 - a player's 36 main slots out of the
 * whole inventory ({@link PlayerInventoryWrapper#getMainSlots}). The non-indexed insert and extract go index by index
 * over the range alone, never past it.
 */
public class RangedResourceHandler<T extends Resource> extends DelegatingResourceHandler<T> {
    protected int start;
    protected int end;

    protected RangedResourceHandler(ResourceHandler<T> delegate, int start, int end) {
        super(delegate);
        this.setRange(start, end);
    }

    protected RangedResourceHandler(Supplier<ResourceHandler<T>> delegate, int start, int end) {
        super(delegate);
        this.setRange(start, end);
    }

    private void setRange(int start, int end) {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Not a range of indices: " + start + " to " + end);
        }
        this.start = start;
        this.end = end;
    }

    public static <T extends Resource> RangedResourceHandler<T> of(ResourceHandler<T> delegate, int start, int end) {
        Objects.checkFromToIndex(start, end, delegate.size());
        return new RangedResourceHandler<>(delegate, start, end);
    }

    public static <T extends Resource> RangedResourceHandler<T> of(Supplier<ResourceHandler<T>> delegate, int start, int end) {
        return new RangedResourceHandler<>(delegate, start, end);
    }

    public static <T extends Resource> RangedResourceHandler<T> ofSingleIndex(ResourceHandler<T> delegate, int index) {
        return of(delegate, index, index + 1);
    }

    public static <T extends Resource> RangedResourceHandler<T> ofSingleIndex(Supplier<ResourceHandler<T>> delegate, int index) {
        return of(delegate, index, index + 1);
    }

    @Override
    public int size() {
        return this.end - this.start;
    }

    @Override
    protected int convertIndex(int index) {
        return this.start + Objects.checkIndex(index, size());
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ResourceHandler<T> handler = getDelegate();
        int moved = 0;
        for (int i = this.start; i < this.end && moved < amount; i++) {
            moved += handler.insert(i, resource, amount - moved, transaction);
        }
        return moved;
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        ResourceHandler<T> handler = getDelegate();
        int moved = 0;
        for (int i = this.start; i < this.end && moved < amount; i++) {
            moved += handler.extract(i, resource, amount - moved, transaction);
        }
        return moved;
    }
}
