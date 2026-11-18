package dev.nookworks.port.transfer;

import com.google.common.primitives.Ints;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.ResourceHandler}: a fixed row of indices (slots, tanks), each holding one
 * resource in some amount. Inserts and extracts take part in a transaction and return the amount moved; nothing is final
 * until the root transaction commits.
 *
 * <p>Other mods on 1.21.1 see a handler of ours through {@code IItemHandler} / {@code IFluidHandler} (see
 * {@link TransferBridges}), and theirs reach us as one of these.
 */
public interface ResourceHandler<T extends Resource> {
    int size();

    /** What index {@code index} holds (the empty resource if nothing). */
    T getResource(int index);

    long getAmountAsLong(int index);

    default int getAmountAsInt(int index) {
        return Ints.saturatedCast(getAmountAsLong(index));
    }

    /** The most index {@code index} could hold of {@code resource} (of anything, asked with the empty resource). */
    long getCapacityAsLong(int index, T resource);

    default int getCapacityAsInt(int index, T resource) {
        return Ints.saturatedCast(getCapacityAsLong(index, resource));
    }

    /** Whether index {@code index} could ever hold {@code resource}, ignoring what it holds now. */
    boolean isValid(int index, T resource);

    /** Puts up to {@code amount} of {@code resource} into index {@code index}; returns how much went in. */
    int insert(int index, T resource, int amount, TransactionContext transaction);

    /** Puts up to {@code amount} of {@code resource} into the handler wherever it goes, index by index. */
    default int insert(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int moved = 0;
        for (int i = 0, n = size(); i < n && moved < amount; i++) {
            moved += insert(i, resource, amount - moved, transaction);
        }
        return moved;
    }

    /** Takes up to {@code amount} of {@code resource} out of index {@code index}; returns how much came out. */
    int extract(int index, T resource, int amount, TransactionContext transaction);

    /** Takes up to {@code amount} of {@code resource} out of the handler, index by index. */
    default int extract(T resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int moved = 0;
        for (int i = 0, n = size(); i < n && moved < amount; i++) {
            moved += extract(i, resource, amount - moved, transaction);
        }
        return moved;
    }

    /** The generic class, for declaring capabilities. */
    @SuppressWarnings("unchecked")
    static <T extends Resource> Class<ResourceHandler<T>> asClass() {
        return (Class<ResourceHandler<T>>) (Object) ResourceHandler.class;
    }
}
