package dev.nookworks.port.transfer;

/**
 * Stand-in for NeoForge 26.3's {@code transaction.TransactionContext}: an open transaction as the handlers see it, to
 * take part in (a journal snapshots its state at the transaction's depth) or to open a nested one under.
 */
public sealed interface TransactionContext permits Transaction {
    /** 0 for a root transaction, one more for each level of nesting. */
    int depth();
}
