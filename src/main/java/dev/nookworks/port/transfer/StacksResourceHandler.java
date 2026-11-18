package dev.nookworks.port.transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;

import com.mojang.serialization.Codec;

import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import net.minecraft.core.NonNullList;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.StacksResourceHandler}: a handler over a list of stacks (item stacks,
 * fluid stacks), each index its own stack, each with a journal of its own so a transaction snapshots only what it
 * touches. {@link #onContentsChanged} is called once per changed index after the root transaction commits, and at once
 * for {@link #set}.
 *
 * <p>Saved under {@value #VALUE_IO_KEY} as a list of the stacks, as 26.3 saves it.
 *
 * @param <S> the stack type
 * @param <T> the resource type
 */
public abstract class StacksResourceHandler<S, T extends Resource> implements ResourceHandler<T> {
    public static final String VALUE_IO_KEY = "stacks";

    protected final S emptyStack;
    protected NonNullList<S> stacks;
    protected final Codec<NonNullList<S>> codec;
    private final ArrayList<Slot> journals;

    protected StacksResourceHandler(int size, S emptyStack, Codec<S> stackCodec) {
        this(NonNullList.withSize(size, emptyStack), emptyStack, stackCodec);
    }

    protected StacksResourceHandler(NonNullList<S> stacks, S emptyStack, Codec<S> stackCodec) {
        this.emptyStack = emptyStack;
        this.stacks = copyOf(stacks);
        this.codec = stackCodec.listOf().xmap(this::copyOf, Function.identity());
        this.journals = new ArrayList<>(this.stacks.size());
        fitJournals();
    }

    @SuppressWarnings("unchecked")
    private NonNullList<S> copyOf(Collection<S> list) {
        return NonNullList.of(this.emptyStack, (S[]) list.toArray(Object[]::new));
    }

    /** Replaces the whole list (no change notification). */
    protected void setStacks(NonNullList<S> stacks) {
        this.stacks = copyOf(stacks);
        fitJournals();
    }

    private void fitJournals() {
        while (this.journals.size() < this.stacks.size()) {
            this.journals.add(new Slot(this.journals.size()));
        }
        while (this.journals.size() > this.stacks.size()) {
            this.journals.remove(this.journals.size() - 1);
        }
    }

    public void serialize(ValueOutput output) {
        output.store(VALUE_IO_KEY, this.codec, this.stacks);
    }

    public void deserialize(ValueInput input) {
        input.read(VALUE_IO_KEY, this.codec).ifPresent(read -> {
            this.stacks = read;
            fitJournals();
        });
    }

    /** Sets an index outright, outside any transaction, and reports the change at once. */
    public void set(int index, T resource, int amount) {
        TransferPreconditions.checkNonNegative(amount);
        if (resource.isEmpty() && amount > 0) {
            throw new IllegalArgumentException("An empty resource cannot have an amount: " + amount);
        }
        S before = this.stacks.set(index, getStackFrom(resource, amount));
        onContentsChanged(index, before);
    }

    protected abstract T getResourceFrom(S stack);

    protected abstract int getAmountFrom(S stack);

    protected abstract S getStackFrom(T resource, int amount);

    protected abstract S copyOf(S stack);

    protected boolean matches(S stack, T resource) {
        return getResourceFrom(stack).equals(resource);
    }

    @Override
    public boolean isValid(int index, T resource) {
        return true;
    }

    /** The most index {@code index} holds of {@code resource}. */
    protected abstract int getCapacity(int index, T resource);

    /** Called after an index changed: once per root commit that changed it, or by {@link #set}. */
    protected void onContentsChanged(int index, S previousContents) {}

    /** A copy of the list. */
    public NonNullList<S> copyToList() {
        return copyOf(this.stacks);
    }

    @Override
    public int size() {
        return this.stacks.size();
    }

    @Override
    public T getResource(int index) {
        Objects.checkIndex(index, size());
        return getResourceFrom(this.stacks.get(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        Objects.checkIndex(index, size());
        return getAmountFrom(this.stacks.get(index));
    }

    @Override
    public long getCapacityAsLong(int index, T resource) {
        Objects.checkIndex(index, size());
        return resource.isEmpty() || isValid(index, resource) ? getCapacity(index, resource) : 0;
    }

    @Override
    public int insert(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        S stack = this.stacks.get(index);
        int held = getAmountFrom(stack);
        if ((held == 0 || matches(stack, resource)) && isValid(index, resource)) {
            int moved = Math.min(amount, getCapacity(index, resource) - held);
            if (moved > 0) {
                this.journals.get(index).updateSnapshots(transaction);
                this.stacks.set(index, getStackFrom(resource, held + moved));
                return moved;
            }
        }
        return 0;
    }

    @Override
    public int extract(int index, T resource, int amount, TransactionContext transaction) {
        Objects.checkIndex(index, size());
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        S stack = this.stacks.get(index);
        if (matches(stack, resource)) {
            int held = getAmountFrom(stack);
            int moved = Math.min(amount, held);
            if (moved > 0) {
                this.journals.get(index).updateSnapshots(transaction);
                this.stacks.set(index, getStackFrom(resource, held - moved));
                return moved;
            }
        }
        return 0;
    }

    /** One index's journal: its stack before the change, put back on abort. */
    private final class Slot extends SnapshotJournal<S> {
        private final int index;

        Slot(int index) {
            this.index = index;
        }

        @Override
        protected S createSnapshot() {
            return copyOf(StacksResourceHandler.this.stacks.get(this.index));
        }

        @Override
        protected void revertToSnapshot(S snapshot) {
            StacksResourceHandler.this.stacks.set(this.index, snapshot);
        }

        @Override
        protected void onRootCommit(S originalState) {
            onContentsChanged(this.index, originalState);
        }
    }
}
