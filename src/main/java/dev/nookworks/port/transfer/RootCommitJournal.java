package dev.nookworks.port.transfer;

import org.jspecify.annotations.Nullable;

/**
 * Stand-in for NeoForge 26.3's {@code transaction.RootCommitJournal}: runs a callback once the root transaction it was
 * entered in commits, and never if it aborts (a sound played, a block entity marked changed).
 */
public final class RootCommitJournal extends SnapshotJournal<@Nullable Void> {
    private final Runnable callback;

    public RootCommitJournal(Runnable callback) {
        this.callback = callback;
    }

    @Override
    protected @Nullable Void createSnapshot() {
        return null;
    }

    @Override
    protected void revertToSnapshot(@Nullable Void snapshot) {}

    @Override
    protected void onRootCommit(@Nullable Void originalState) {
        this.callback.run();
    }
}
