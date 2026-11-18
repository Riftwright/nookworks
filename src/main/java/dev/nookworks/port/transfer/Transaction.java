package dev.nookworks.port.transfer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Stand-in for NeoForge 26.3's {@code transaction.Transaction}, which 1.21.1 lacks: everything a handler changes inside
 * a transaction is undone when the transaction is closed without {@link #commit()}, and only becomes final when the root
 * transaction commits. Used as 26.3's is, in try-with-resources:
 *
 * <pre>
 * try (Transaction tx = Transaction.openRoot()) {
 *     int moved = from.extract(resource, 10, tx);
 *     if (to.insert(resource, moved, tx) == moved) tx.commit(); // else both are rolled back
 * }
 * </pre>
 *
 * One stack of transactions per thread; only the innermost open one may be used, committed or closed. Handlers take part
 * through {@link SnapshotJournal}: a journal saves its state the first time it is changed at a depth, and when that
 * transaction closes the snapshot is put back (aborted), handed down to the parent (a nested commit), or kept until the
 * root's commit is through, when each journal's {@code onRootCommit} runs once (the place for side effects: marking a
 * block entity changed, the real calls of another mod's handler).
 *
 * <p>An abort undoes the journals in the reverse order of their first change in the transaction, so two views over the
 * same storage (two wrappers of one chest) unwind to the state from before either changed it.
 */
public final class Transaction implements AutoCloseable, TransactionContext {
    /** Where a thread's transactions stand. */
    public enum Lifecycle {
        /** No transaction is open, and no root commit is being finished. */
        NONE,
        /** A transaction is open: {@link #current()} gives it. */
        OPEN,
        /** The innermost transaction is closing (its journals are being closed). */
        CLOSING,
        /** The root transaction has closed and the journals' {@code onRootCommit} callbacks are running. */
        ROOT_CLOSING
    }

    /** One thread's stack of transactions (reused from one root to the next, as they are opened and closed often). */
    private static final class Stack {
        final Thread thread = Thread.currentThread();
        final List<Transaction> levels = new ArrayList<>();
        int top = -1;
        final ArrayDeque<SnapshotJournal<?>> rootCommits = new ArrayDeque<>();
        boolean finishingRoot;
    }

    private static final ThreadLocal<Stack> STACKS = ThreadLocal.withInitial(Stack::new);

    private final Stack stack;
    private final int depth;
    boolean open;
    /** The journals that hold a snapshot at this depth, in the order they were first changed in it. */
    final List<SnapshotJournal<?>> journals = new ArrayList<>();

    private Transaction(Stack stack, int depth) {
        this.stack = stack;
        this.depth = depth;
    }

    /** Opens a root transaction; throws if one is already open on this thread. */
    public static Transaction openRoot() {
        return open(null);
    }

    /**
     * Opens a transaction nested in {@code parent}, which must be this thread's innermost open one, or a root
     * transaction when {@code parent} is null.
     */
    public static Transaction open(@Nullable TransactionContext parent) {
        Stack stack = STACKS.get();
        if (parent != null) {
            Transaction outer = (Transaction) parent;
            outer.checkCurrent();
            outer.checkOpen();
        } else if (stack.top >= 0) {
            throw new IllegalStateException("A root transaction is already open on thread " + stack.thread.getName());
        }
        int depth = ++stack.top;
        Transaction tx;
        if (depth == stack.levels.size()) {
            tx = new Transaction(stack, depth);
            stack.levels.add(tx);
        } else {
            tx = stack.levels.get(depth);
        }
        tx.open = true;
        return tx;
    }

    /** Where this thread's transactions stand. */
    public static Lifecycle getLifecycle() {
        Stack stack = STACKS.get();
        if (stack.top < 0) {
            return stack.finishingRoot ? Lifecycle.ROOT_CLOSING : Lifecycle.NONE;
        }
        return stack.levels.get(stack.top).open ? Lifecycle.OPEN : Lifecycle.CLOSING;
    }

    /**
     * This thread's innermost open transaction, or null if none is open. Not in 26.3's public surface the mod uses: the
     * bridges to 1.21.1's handlers ask it, so that a call of another mod's that reaches our handlers while one of our
     * transactions is open takes part in it instead of trying to open a second root.
     */
    public static @Nullable Transaction current() {
        Stack stack = STACKS.get();
        if (stack.top < 0) {
            return null;
        }
        Transaction tx = stack.levels.get(stack.top);
        if (!tx.open) {
            throw new IllegalStateException("No transaction may be used while one is closing");
        }
        return tx;
    }

    /** Keeps everything changed in this transaction: final if it is the root, else handed to the parent. */
    public void commit() {
        close(false);
    }

    /** Closes the transaction; if it was not committed, everything changed in it is put back. */
    @Override
    public void close() {
        if (this.open && this.stack.top >= this.depth) {
            close(true);
        }
    }

    @Override
    public int depth() {
        checkThread();
        return this.depth;
    }

    @Override
    public String toString() {
        return "Transaction[depth=" + this.depth + ", open=" + this.open + "]";
    }

    void checkOpen() {
        if (!this.open) {
            throw new IllegalStateException("The transaction is closed or closing");
        }
    }

    private void checkThread() {
        if (Thread.currentThread() != this.stack.thread) {
            throw new IllegalStateException("A transaction of thread " + this.stack.thread.getName() + " was used on "
                    + Thread.currentThread().getName());
        }
    }

    private void checkCurrent() {
        checkThread();
        if (this.stack.top < 0 || this.stack.levels.get(this.stack.top) != this) {
            throw new IllegalStateException("Only the innermost open transaction may be used (depth " + this.depth
                    + ", innermost " + this.stack.top + ")");
        }
    }

    /** The open transaction one level out, for a journal handing its snapshot down. */
    Transaction parent() {
        return this.stack.levels.get(this.depth - 1);
    }

    /** Queues a journal's root commit callback (a journal is queued once per root). */
    void queueRootCommit(SnapshotJournal<?> journal) {
        this.stack.rootCommits.add(journal);
    }

    private void close(boolean aborted) {
        checkCurrent();
        checkOpen();
        this.open = false;
        RuntimeException failure = null;
        int n = this.journals.size();
        for (int i = 0; i < n; i++) {
            // undone newest first; kept in order
            SnapshotJournal<?> journal = this.journals.get(aborted ? n - 1 - i : i);
            try {
                journal.close(this, aborted);
            } catch (RuntimeException e) {
                failure = add(failure, e, "closing a transaction");
            }
        }
        this.journals.clear();
        this.stack.top--;
        if (this.stack.top < 0) {
            failure = finishRoot(this.stack, failure);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Runs the queued root commit callbacks. A callback may open and commit roots of its own (a real call of another mod's
     * handler that reaches back into ours): those queue more journals, which this same loop runs.
     */
    private static @Nullable RuntimeException finishRoot(Stack stack, @Nullable RuntimeException failure) {
        if (stack.finishingRoot) {
            return failure;
        }
        stack.finishingRoot = true;
        try {
            SnapshotJournal<?> journal;
            while ((journal = stack.rootCommits.poll()) != null) {
                try {
                    journal.finishRoot();
                } catch (RuntimeException e) {
                    failure = add(failure, e, "finishing a root commit");
                }
            }
        } finally {
            stack.finishingRoot = false;
        }
        return failure;
    }

    private static RuntimeException add(@Nullable RuntimeException failure, RuntimeException e, String what) {
        if (failure == null) {
            return new RuntimeException("A transaction journal failed while " + what, e);
        }
        failure.addSuppressed(e);
        return failure;
    }
}
