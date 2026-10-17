package dev.nookworks.cooking;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * The hoppers' and pipes' way into a kitchen block that holds one thing to a place - a Grill, a Wooden Tray, a
 * Nukadoko: a thing goes into an empty place if the block would take it from a hand, and comes out only once it is
 * done - cooked, risen, pickled - never part way. So a hopper above feeds it and one below empties it, and there is
 * always a way to automate a kitchen for whoever looks for one (the user, 2026-10-01).
 * <p>
 * Every move is a transaction: a pipe that tries a move and thinks better of it changes nothing.
 *
 * @param <S> what the block's places are saved as, to be put back if a move is undone
 */
public final class PlacesHandler<S> extends SnapshotJournal<S> implements ResourceHandler<ItemResource> {
    /** The block's side of it. */
    public interface Places<S> {
        int size();

        ItemStack at(int place);

        /** Whether a thing like {@code resource} goes in at all. */
        boolean takes(ItemResource resource);

        /** Puts {@code one} (a single thing) into the empty {@code place}, starting whatever it does there. */
        void put(int place, ItemStack one);

        /** Whether what is in {@code place} is done and may be taken out. */
        boolean done(int place);

        /** Empties {@code place}. */
        void clear(int place);

        S save();

        void restore(S saved);

        /** After a move went through: saved, and shown to whoever can see the block. */
        void changed();
    }

    private final Places<S> places;

    public PlacesHandler(Places<S> places) {
        this.places = places;
    }

    @Override
    public int size() {
        return this.places.size();
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(this.places.at(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return this.places.at(index).getCount();
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return 1;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return !resource.isEmpty() && this.places.takes(resource);
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || resource.isEmpty() || !this.places.at(index).isEmpty() || !this.places.takes(resource)) {
            return 0;
        }
        this.updateSnapshots(transaction);
        this.places.put(index, resource.toStack(1));
        return 1;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        ItemStack held = this.places.at(index);
        if (amount <= 0 || held.isEmpty() || !resource.matches(held) || !this.places.done(index)) {
            return 0;
        }
        this.updateSnapshots(transaction);
        int taken = Math.min(amount, held.getCount());
        if (taken >= held.getCount()) {
            this.places.clear(index);
        } else {
            held.shrink(taken); // two from another mod's campfire recipe: one taken, the other still there
        }
        return taken;
    }

    @Override
    protected S createSnapshot() {
        return this.places.save();
    }

    @Override
    protected void revertToSnapshot(S snapshot) {
        this.places.restore(snapshot);
    }

    @Override
    protected void onRootCommit(S originalState) {
        this.places.changed();
    }
}
