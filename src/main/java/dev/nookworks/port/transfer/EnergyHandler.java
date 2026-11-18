package dev.nookworks.port.transfer;

import com.google.common.primitives.Ints;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.energy.EnergyHandler}: a store of energy whose inserts and extracts take
 * part in a transaction. Other mods on 1.21.1 see one of ours as an {@code IEnergyStorage} (see {@link TransferBridges}).
 */
public interface EnergyHandler {
    long getAmountAsLong();

    default int getAmountAsInt() {
        return Ints.saturatedCast(getAmountAsLong());
    }

    long getCapacityAsLong();

    default int getCapacityAsInt() {
        return Ints.saturatedCast(getCapacityAsLong());
    }

    /** Puts up to {@code amount} in; returns how much went in. */
    int insert(int amount, TransactionContext transaction);

    /** Takes up to {@code amount} out; returns how much came out. */
    int extract(int amount, TransactionContext transaction);
}
