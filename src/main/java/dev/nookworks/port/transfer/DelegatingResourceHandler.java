package dev.nookworks.port.transfer;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.DelegatingResourceHandler}: a handler that passes every call on to
 * another, for a subclass to override the few it changes (a tank's inlet that takes but never gives, say). The handler
 * passed on to is asked for each call ({@link #getDelegate}), so it may be swapped behind a supplier.
 *
 * <p>As 26.3's: the indexed calls check their index against {@link #size} (through {@link #convertIndex}, which a
 * subclass may change to show only some of the indices) and their arguments; the non-indexed insert and extract go to
 * the other handler's own, by its own rule; any empty resource is valid.
 */
public class DelegatingResourceHandler<T extends Resource> implements ResourceHandler<T> {
    protected final Supplier<ResourceHandler<T>> delegate;

    public DelegatingResourceHandler(ResourceHandler<T> delegate) {
        Objects.requireNonNull(delegate, "delegate");
        this.delegate = () -> delegate;
    }

    public DelegatingResourceHandler(Supplier<ResourceHandler<T>> delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    /** The handler calls go to, as it is now. */
    public ResourceHandler<T> getDelegate() {
        return this.delegate.get();
    }

    /** The other handler's index for index {@code index} of this one (the same, once checked against {@link #size}). */
    protected int convertIndex(int index) {
        return Objects.checkIndex(index, size());
    }

    @Override
    public int size() {
        return getDelegate().size();
    }

    @Override
    public T getResource(int index) {
        return getDelegate().getResource(convertIndex(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return getDelegate().getAmountAsLong(convertIndex(index));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        return getDelegate().getCapacityAsLong(convertIndex(index), resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return resource.isEmpty() || getDelegate().isValid(convertIndex(index), resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return getDelegate().insert(convertIndex(index), resource, amount, transaction);
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        return getDelegate().insert(resource, amount, transaction);
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        return getDelegate().extract(convertIndex(index), resource, amount, transaction);
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        return getDelegate().extract(resource, amount, transaction);
    }
}
