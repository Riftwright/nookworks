package dev.nookworks.port.transfer;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

/**
 * Stand-in for NeoForge 26.3's {@code transaction.SnapshotJournal}: the way a handler takes part in transactions. Call
 * {@link #updateSnapshots} before each change; the journal then keeps a snapshot of the state from before the first
 * change at each depth, puts it back if that transaction aborts, and calls {@link #onRootCommit} once the root
 * transaction has committed, with the state from before the whole root transaction.
 *
 * @param <T> a snapshot of the state (it must not be changed after it is taken)
 */
public abstract class SnapshotJournal<T extends @Nullable Object> {
    /** Marks a depth at which no snapshot was taken (a snapshot itself may be null). */
    private static final Object NONE = new Object();

    private final ArrayList<Object> snapshots = new ArrayList<>();
    private int held;
    private boolean rootCommitQueued;
    private @Nullable T beforeRoot;

    /** A snapshot of the state now. */
    protected abstract T createSnapshot();

    /** Puts a snapshot's state back. */
    protected abstract void revertToSnapshot(T snapshot);

    /** Called when a snapshot is no longer needed (to give pooled objects back, say). */
    protected void releaseSnapshot(T snapshot) {}

    /** Called once after a root transaction in which the state changed committed; {@code originalState} is from before it. */
    protected void onRootCommit(T originalState) {}

    /** Whether some open transaction holds a snapshot of this journal. */
    public final boolean isInTransaction() {
        return this.held > 0;
    }

    /** To be called before each change made in {@code transaction}: takes the snapshot of the first change at its depth. */
    public void updateSnapshots(TransactionContext transaction) {
        Transaction tx = (Transaction) transaction;
        int depth = tx.depth();
        while (this.snapshots.size() <= depth) {
            this.snapshots.add(NONE);
        }
        if (this.snapshots.get(depth) == NONE) {
            tx.checkOpen();
            this.snapshots.set(depth, createSnapshot());
            this.held++;
            tx.journals.add(this);
        }
    }

    @SuppressWarnings("unchecked")
    final void close(Transaction tx, boolean aborted) {
        int depth = tx.depth();
        T snapshot = (T) this.snapshots.set(depth, NONE);
        if (aborted) {
            revertToSnapshot(snapshot);
            releaseSnapshot(snapshot);
            this.held--;
        } else if (depth == 0) {
            if (!this.rootCommitQueued) {
                this.rootCommitQueued = true;
                this.beforeRoot = snapshot;
                tx.queueRootCommit(this);
            } else {
                releaseSnapshot(snapshot);
            }
            this.held = 0;
        } else if (this.snapshots.get(depth - 1) == NONE) {
            // the parent had not seen a change yet: the state from before this transaction is the parent's too
            this.snapshots.set(depth - 1, snapshot);
            tx.parent().journals.add(this);
        } else {
            releaseSnapshot(snapshot);
            this.held--;
        }
    }

    final void finishRoot() {
        T before = this.beforeRoot;
        this.beforeRoot = null;
        this.rootCommitQueued = false;
        onRootCommit(before);
        releaseSnapshot(before);
    }
}
