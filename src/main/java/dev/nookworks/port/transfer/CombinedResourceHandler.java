package dev.nookworks.port.transfer;

import java.util.SequencedCollection;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.CombinedResourceHandler}: several handlers shown as one, their indices one
 * after another. The non-indexed insert and extract go handler by handler, each by its own rule.
 */
public class CombinedResourceHandler<T extends Resource> implements ResourceHandler<T> {
    private final ResourceHandler<T>[] handlers;
    private final int[] start;
    private final int size;

    @SuppressWarnings("unchecked")
    public CombinedResourceHandler(SequencedCollection<? extends ResourceHandler<T>> handlers) {
        this(handlers.toArray(ResourceHandler[]::new));
    }

    @SafeVarargs
    public CombinedResourceHandler(ResourceHandler<T>... handlers) {
        this.handlers = handlers;
        this.start = new int[handlers.length];
        int at = 0;
        for (int i = 0; i < handlers.length; i++) {
            this.start[i] = at;
            at += handlers[i].size();
        }
        this.size = at;
    }

    /** Which handler holds combined index {@code index}. */
    protected int getHandlerIndex(int index) {
        if (index < 0 || index >= this.size) {
            throw new IndexOutOfBoundsException("Index " + index + " is out of a combined handler of size " + this.size);
        }
        for (int h = 0; h < this.start.length - 1; h++) {
            if (index < this.start[h + 1]) {
                return h;
            }
        }
        return this.start.length - 1;
    }

    protected ResourceHandler<T> getHandlerFromIndex(int handlerIndex) {
        return this.handlers[handlerIndex];
    }

    protected int getSlotFromIndex(int index, int handlerIndex) {
        return index - this.start[handlerIndex];
    }

    @Override
    public final int size() {
        return this.size;
    }

    @Override
    public T getResource(int index) {
        int h = getHandlerIndex(index);
        return getHandlerFromIndex(h).getResource(getSlotFromIndex(index, h));
    }

    @Override
    public long getAmountAsLong(int index) {
        int h = getHandlerIndex(index);
        return getHandlerFromIndex(h).getAmountAsLong(getSlotFromIndex(index, h));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        int h = getHandlerIndex(index);
        return getHandlerFromIndex(h).getCapacityAsLong(getSlotFromIndex(index, h), resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        int h = getHandlerIndex(index);
        return resource.isEmpty() || getHandlerFromIndex(h).isValid(getSlotFromIndex(index, h), resource);
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int h = getHandlerIndex(index);
        return getHandlerFromIndex(h).insert(getSlotFromIndex(index, h), resource, amount, transaction);
    }

    @Override
    public int insert(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int moved = 0;
        for (ResourceHandler<T> handler : this.handlers) {
            if (moved >= amount) {
                break;
            }
            moved += handler.insert(resource, amount - moved, transaction);
        }
        return moved;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int h = getHandlerIndex(index);
        return getHandlerFromIndex(h).extract(getSlotFromIndex(index, h), resource, amount, transaction);
    }

    @Override
    public int extract(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int moved = 0;
        for (ResourceHandler<T> handler : this.handlers) {
            if (moved >= amount) {
                break;
            }
            moved += handler.extract(resource, amount - moved, transaction);
        }
        return moved;
    }
}
