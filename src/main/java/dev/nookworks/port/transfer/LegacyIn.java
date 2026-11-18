package dev.nookworks.port.transfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.google.common.collect.MapMaker;
import com.mojang.logging.LogUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Another mod's 1.21.1 handlers ({@code IItemHandler}, {@code IFluidHandler}, {@code IEnergyStorage}) as ours, for our
 * pipes, sorters, cables and machines (registered by {@link TransferBridges}).
 *
 * <p>1.21.1's handlers have no transactions: a call is either a simulation or final. So inside a transaction these
 * wrappers make no real call at all. They answer each insert and extract from simulated calls, made against the real
 * state together with what is already pending in the transaction (so a second insert into a slot asks the handler about
 * both at once), keep what is pending in a journal (a nested transaction that aborts takes its part back), and make the
 * real calls when the root transaction commits. A transaction that aborts so leaves the other mod's storage untouched.
 *
 * <p>The limits, where 1.21.1 cannot be asked what 26.3's handlers can:
 * <ul>
 * <li>A slot with something pending takes only what it already shows: an item slot emptied in the transaction takes
 *     back only the item it held (the real slot still holds it while nothing is final); a fluid handler takes one
 *     fluid in per transaction.</li>
 * <li>Each slot (each fluid) is simulated on its own. A storage whose slots share one capacity (a network, a drawer
 *     controller) may take less, when the root commits, than the simulations promised: what an item insert has left
 *     over is offered to the handler's other slots, then dropped at the block (or entity) - never lost; a fluid or
 *     energy insert's rest is lost, and logged. An extract that comes up short when the root commits cannot be taken
 *     back from where it went (logged).</li>
 * <li>A fluid handler is filled and drained as a whole (1.21.1 cannot aim at one tank): an indexed insert or extract
 *     goes to the handler, and the tanks shown in the transaction carry the pending amounts on the first tank of that
 *     fluid (or the first empty one).</li>
 * <li>Two wrappers of one storage in one transaction (two lookups whose handlers are different objects) do not see
 *     each other's pending amounts. One wrapper is kept per handler object, so a handler a block entity keeps is shared.</li>
 * </ul>
 */
final class LegacyIn {
    private static final Logger LOGGER = LogUtils.getLogger();

    private LegacyIn() {}

    private static final Map<IItemHandler, Items> ITEMS = new MapMaker().weakKeys().weakValues().makeMap();
    private static final Map<IFluidHandler, Fluids> FLUIDS = new MapMaker().weakKeys().weakValues().makeMap();
    private static final Map<IEnergyStorage, Energy> ENERGY = new MapMaker().weakKeys().weakValues().makeMap();

    /** Ours over another mod's item handler; {@code spillAt} is where an insert's leftovers are dropped (null: nowhere). */
    static ResourceHandler<ItemResource> items(IItemHandler real, @Nullable Level level, @Nullable Vec3 spillAt) {
        if (real instanceof LegacyOut.Items out) {
            return out.handler(); // one of ours, shown to another mod and handed back
        }
        return ITEMS.computeIfAbsent(real, r -> new Items(r, level, spillAt));
    }

    static ResourceHandler<FluidResource> fluids(IFluidHandler real) {
        if (real instanceof LegacyOut.Fluids out) {
            return out.handler();
        }
        if (real instanceof LegacyOut.ItemFluids) {
            throw new IllegalArgumentException("An item's fluid handler goes through an ItemAccess");
        }
        return FLUIDS.computeIfAbsent(real, Fluids::new);
    }

    static EnergyHandler energy(IEnergyStorage real) {
        if (real instanceof LegacyOut.Energy out) {
            return out.handler();
        }
        return ENERGY.computeIfAbsent(real, Energy::new);
    }

    /** What is pending on one item slot: {@code delta} more (or, below 0, fewer) of {@code resource} than it holds. */
    private record Pending(ItemResource resource, int delta) {}

    /** Items: pending per slot. */
    static final class Items extends SnapshotJournal<Map<Integer, Pending>> implements ResourceHandler<ItemResource> {
        private final IItemHandler real;
        private final @Nullable Level level;
        private final @Nullable Vec3 spillAt;
        private Map<Integer, Pending> pending = new HashMap<>();

        Items(IItemHandler real, @Nullable Level level, @Nullable Vec3 spillAt) {
            this.real = real;
            this.level = level;
            this.spillAt = spillAt;
        }

        IItemHandler real() {
            return this.real;
        }

        @Override
        public int size() {
            return this.real.getSlots();
        }

        private int delta(int index) {
            Pending p = this.pending.get(index);
            return p == null ? 0 : p.delta();
        }

        /** What the slot holds as the transaction sees it. */
        private ItemResource shown(int index, ItemStack now) {
            Pending p = this.pending.get(index);
            if (p == null) {
                return ItemResource.of(now);
            }
            int amount = now.getCount() + p.delta();
            return amount <= 0 ? ItemResource.EMPTY : now.isEmpty() ? p.resource() : ItemResource.of(now);
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, size());
            return shown(index, this.real.getStackInSlot(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, size());
            return Math.max(0, this.real.getStackInSlot(index).getCount() + delta(index));
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            int limit = this.real.getSlotLimit(index);
            if (resource.isEmpty()) {
                return limit;
            }
            if (!this.real.isItemValid(index, resource.toStack())) {
                return 0;
            }
            // a slot of more than a stack (a drawer) holds what it says; an ordinary one a stack of the item
            return limit > Item.ABSOLUTE_MAX_STACK_SIZE ? limit : Math.min(limit, resource.getMaxStackSize());
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, size());
            return this.real.isItemValid(index, resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            ItemStack now = this.real.getStackInSlot(index);
            int delta = delta(index);
            ItemResource shown = shown(index, now);
            if (!shown.isEmpty() ? !shown.equals(resource) : !now.isEmpty() && !resource.matches(now)) {
                return 0; // another item there (or one whose leaving is not final yet)
            }
            long wanted = (long) delta + amount;
            int after;
            if (wanted <= 0) {
                after = (int) wanted; // only takes back part of what is leaving
            } else {
                int asked = (int) Math.min(wanted, Integer.MAX_VALUE);
                ItemStack left = this.real.insertItem(index, resource.toStack(asked), true);
                int fits = asked - Math.min(asked, left.getCount());
                // what is already promised stays; past it, what the slot takes with it (the real slot still holds
                // anything leaving, so a slot being emptied is asked conservatively)
                after = Math.max(Math.max(delta, 0), fits);
            }
            int moved = after - delta;
            if (moved <= 0) {
                return 0;
            }
            moved = Math.min(moved, amount);
            updateSnapshots(transaction);
            set(index, resource, delta + moved);
            return moved;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            ItemStack now = this.real.getStackInSlot(index);
            int delta = delta(index);
            if (!shown(index, now).equals(resource)) {
                return 0;
            }
            long wanted = (long) delta - amount;
            int after;
            if (wanted >= 0) {
                after = (int) wanted; // only takes back part of what is pending in
            } else {
                int asked = (int) Math.min(-wanted, Integer.MAX_VALUE);
                ItemStack got = now.isEmpty() ? ItemStack.EMPTY : this.real.extractItem(index, asked, true);
                int out = resource.matches(got) ? got.getCount() : 0;
                after = Math.max((int) wanted, Math.min(delta, -out));
            }
            int moved = delta - after;
            if (moved <= 0) {
                return 0;
            }
            moved = Math.min(moved, amount);
            updateSnapshots(transaction);
            set(index, resource, delta - moved);
            return moved;
        }

        private void set(int index, ItemResource resource, int delta) {
            if (delta == 0) {
                this.pending.remove(index);
            } else {
                this.pending.put(index, new Pending(resource, delta));
            }
        }

        @Override
        protected Map<Integer, Pending> createSnapshot() {
            return new HashMap<>(this.pending);
        }

        @Override
        protected void revertToSnapshot(Map<Integer, Pending> snapshot) {
            this.pending = snapshot;
        }

        /** The real calls: what leaves first, then what comes in. */
        @Override
        protected void onRootCommit(Map<Integer, Pending> originalState) {
            Map<Integer, Pending> done = new TreeMap<>(this.pending);
            this.pending = new HashMap<>();
            for (var e : done.entrySet()) {
                int slot = e.getKey();
                Pending p = e.getValue();
                if (p.delta() < 0) {
                    ItemStack got = this.real.extractItem(slot, -p.delta(), false);
                    if (!p.resource().matches(got) || got.getCount() < -p.delta()) {
                        LOGGER.warn("{} gave {} out of slot {} where {} x{} was taken in a transaction", this.real, got, slot, p.resource(), -p.delta());
                        if (!got.isEmpty() && !p.resource().matches(got)) {
                            place(slot, got); // not what was taken: put it back
                        }
                    }
                }
            }
            for (var e : done.entrySet()) {
                Pending p = e.getValue();
                if (p.delta() > 0) {
                    place(e.getKey(), p.resource().toStack(p.delta()));
                }
            }
        }

        /** Puts a stack in for real: its slot, then any other, then onto the ground. */
        private void place(int slot, ItemStack stack) {
            ItemStack left = this.real.insertItem(slot, stack, false);
            for (int i = 0, n = this.real.getSlots(); i < n && !left.isEmpty(); i++) {
                left = this.real.insertItem(i, left, false);
            }
            if (!left.isEmpty()) {
                if (this.level != null && this.spillAt != null) {
                    LOGGER.warn("{} took less than a transaction promised; {} dropped", this.real, left);
                    Containers.dropItemStack(this.level, this.spillAt.x, this.spillAt.y, this.spillAt.z, left);
                } else {
                    LOGGER.warn("{} took less than a transaction promised; {} lost", this.real, left);
                }
            }
        }

        @Override
        public String toString() {
            return "Legacy[" + this.real + "]";
        }
    }

    /** Fluids: pending per fluid, the handler filled and drained as a whole. */
    static final class Fluids extends SnapshotJournal<Map<FluidResource, Integer>> implements ResourceHandler<FluidResource> {
        private final IFluidHandler real;
        private Map<FluidResource, Integer> pending = new LinkedHashMap<>();

        Fluids(IFluidHandler real) {
            this.real = real;
        }

        IFluidHandler real() {
            return this.real;
        }

        @Override
        public int size() {
            return this.real.getTanks();
        }

        /** The tanks as the transaction sees them: the real ones with the pending amounts laid on. */
        private List<FluidStack> shown() {
            int n = this.real.getTanks();
            List<FluidStack> tanks = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                tanks.add(this.real.getFluidInTank(i).copy());
            }
            for (var e : this.pending.entrySet()) {
                FluidResource resource = e.getKey();
                int delta = e.getValue();
                if (delta < 0) {
                    for (int i = 0; i < n && delta < 0; i++) {
                        FluidStack tank = tanks.get(i);
                        if (resource.matches(tank)) {
                            int take = Math.min(tank.getAmount(), -delta);
                            tank.shrink(take);
                            delta += take;
                        }
                    }
                } else if (delta > 0) {
                    int at = -1;
                    for (int i = 0; i < n && at < 0; i++) {
                        if (resource.matches(tanks.get(i))) {
                            at = i;
                        }
                    }
                    for (int i = 0; i < n && at < 0; i++) {
                        if (tanks.get(i).isEmpty()) {
                            at = i;
                        }
                    }
                    if (at >= 0) {
                        FluidStack tank = tanks.get(at);
                        tanks.set(at, resource.toStack(tank.getAmount() + delta));
                    }
                }
            }
            return tanks;
        }

        @Override
        public FluidResource getResource(int index) {
            Objects.checkIndex(index, size());
            return this.pending.isEmpty() ? FluidResource.of(this.real.getFluidInTank(index)) : FluidResource.of(shown().get(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, size());
            return this.pending.isEmpty() ? this.real.getFluidInTank(index).getAmount() : shown().get(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return resource.isEmpty() || isValid(index, resource) ? this.real.getTankCapacity(index) : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            Objects.checkIndex(index, size());
            return this.real.isFluidValid(index, resource.toStack(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            return insert(resource, amount, transaction);
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            for (var e : this.pending.entrySet()) {
                if (e.getValue() > 0 && !e.getKey().equals(resource)) {
                    return 0; // one fluid in per transaction
                }
            }
            int delta = this.pending.getOrDefault(resource, 0);
            long wanted = (long) delta + amount;
            int after;
            if (wanted <= 0) {
                after = (int) wanted;
            } else {
                int fits = this.real.fill(resource.toStack((int) Math.min(wanted, Integer.MAX_VALUE)), IFluidHandler.FluidAction.SIMULATE);
                after = Math.max(Math.max(delta, 0), fits);
            }
            int moved = Math.min(after - delta, amount);
            if (moved <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            set(resource, delta + moved);
            return moved;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, size());
            return extract(resource, amount, transaction);
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount == 0) {
                return 0;
            }
            int delta = this.pending.getOrDefault(resource, 0);
            long wanted = (long) delta - amount;
            int after;
            if (wanted >= 0) {
                after = (int) wanted;
            } else {
                FluidStack got = this.real.drain(resource.toStack((int) Math.min(-wanted, Integer.MAX_VALUE)), IFluidHandler.FluidAction.SIMULATE);
                int out = resource.matches(got) ? got.getAmount() : 0;
                after = Math.max((int) wanted, Math.min(Math.max(delta, 0), -out));
            }
            int moved = Math.min(delta - after, amount);
            if (moved <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            set(resource, delta - moved);
            return moved;
        }

        private void set(FluidResource resource, int delta) {
            if (delta == 0) {
                this.pending.remove(resource);
            } else {
                this.pending.put(resource, delta);
            }
        }

        @Override
        protected Map<FluidResource, Integer> createSnapshot() {
            return new LinkedHashMap<>(this.pending);
        }

        @Override
        protected void revertToSnapshot(Map<FluidResource, Integer> snapshot) {
            this.pending = snapshot;
        }

        @Override
        protected void onRootCommit(Map<FluidResource, Integer> originalState) {
            Map<FluidResource, Integer> done = this.pending;
            this.pending = new LinkedHashMap<>();
            for (var e : done.entrySet()) {
                if (e.getValue() < 0) {
                    FluidStack got = this.real.drain(e.getKey().toStack(-e.getValue()), IFluidHandler.FluidAction.EXECUTE);
                    if (!e.getKey().matches(got) || got.getAmount() < -e.getValue()) {
                        LOGGER.warn("{} gave {} where {} x{} was drained in a transaction", this.real, got, e.getKey(), -e.getValue());
                    }
                }
            }
            for (var e : done.entrySet()) {
                if (e.getValue() > 0) {
                    int filled = this.real.fill(e.getKey().toStack(e.getValue()), IFluidHandler.FluidAction.EXECUTE);
                    if (filled < e.getValue()) {
                        LOGGER.warn("{} took {} of {} x{} promised in a transaction; the rest is lost", this.real, filled, e.getKey(), e.getValue());
                    }
                }
            }
        }

        @Override
        public String toString() {
            return "Legacy[" + this.real + "]";
        }
    }

    /** Energy: one pending amount. */
    static final class Energy extends SnapshotJournal<Integer> implements EnergyHandler {
        private final IEnergyStorage real;
        private int pending;

        Energy(IEnergyStorage real) {
            this.real = real;
        }

        IEnergyStorage real() {
            return this.real;
        }

        @Override
        public long getAmountAsLong() {
            return Math.max(0L, (long) this.real.getEnergyStored() + this.pending);
        }

        @Override
        public long getCapacityAsLong() {
            return this.real.getMaxEnergyStored();
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0) {
                return 0;
            }
            long wanted = (long) this.pending + amount;
            int after;
            if (wanted <= 0) {
                after = (int) wanted;
            } else {
                int fits = this.real.canReceive() ? this.real.receiveEnergy((int) Math.min(wanted, Integer.MAX_VALUE), true) : 0;
                after = Math.max(Math.max(this.pending, 0), fits);
            }
            int moved = Math.min(after - this.pending, amount);
            if (moved <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            this.pending += moved;
            return moved;
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            if (amount == 0) {
                return 0;
            }
            long wanted = (long) this.pending - amount;
            int after;
            if (wanted >= 0) {
                after = (int) wanted;
            } else {
                int out = this.real.canExtract() ? this.real.extractEnergy((int) Math.min(-wanted, Integer.MAX_VALUE), true) : 0;
                after = Math.max((int) wanted, Math.min(Math.max(this.pending, 0), -out));
            }
            int moved = Math.min(this.pending - after, amount);
            if (moved <= 0) {
                return 0;
            }
            updateSnapshots(transaction);
            this.pending -= moved;
            return moved;
        }

        @Override
        protected Integer createSnapshot() {
            return this.pending;
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            this.pending = snapshot;
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            int done = this.pending;
            this.pending = 0;
            if (done > 0) {
                int in = this.real.receiveEnergy(done, false);
                if (in < done) {
                    LOGGER.debug("{} took {} of {} energy promised in a transaction", this.real, in, done);
                }
            } else if (done < 0) {
                int out = this.real.extractEnergy(-done, false);
                if (out < -done) {
                    LOGGER.debug("{} gave {} of {} energy taken in a transaction", this.real, out, -done);
                }
            }
        }
    }

    /**
     * An item's fluid handler of another mod's (a bucket, a tank item) as ours, through an {@link ItemAccess}. Each call
     * asks a fresh copy of one of the items for its handler, fills or drains that copy for real (the copy is thrown away),
     * and swaps one item at the access for what the copy became - inside the transaction, so the hand's bucket changes
     * only if it commits. The access is ours and transactional, so nothing here needs a journal: the next call in the
     * same transaction already sees the swapped item.
     */
    static final class ItemFluids implements ResourceHandler<FluidResource> {
        private final ItemAccess access;

        ItemFluids(ItemAccess access) {
            this.access = access;
        }

        private @Nullable IFluidHandlerItem probe() {
            ItemResource held = this.access.getResource();
            if (held.isEmpty() || this.access.getAmount() <= 0) {
                return null;
            }
            return net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM.getCapability(held.toStack(), null);
        }

        @Override
        public int size() {
            IFluidHandlerItem h = probe();
            return h == null ? 0 : h.getTanks();
        }

        @Override
        public FluidResource getResource(int index) {
            IFluidHandlerItem h = probe();
            return h == null || index >= h.getTanks() ? FluidResource.EMPTY : FluidResource.of(h.getFluidInTank(index));
        }

        @Override
        public long getAmountAsLong(int index) {
            IFluidHandlerItem h = probe();
            return h == null || index >= h.getTanks() ? 0 : h.getFluidInTank(index).getAmount();
        }

        @Override
        public long getCapacityAsLong(int index, FluidResource resource) {
            IFluidHandlerItem h = probe();
            if (h == null || index >= h.getTanks()) {
                return 0;
            }
            return resource.isEmpty() || isValid(index, resource) ? h.getTankCapacity(index) : 0;
        }

        @Override
        public boolean isValid(int index, FluidResource resource) {
            IFluidHandlerItem h = probe();
            return h != null && index < h.getTanks() && h.isFluidValid(index, resource.toStack(net.neoforged.neoforge.fluids.FluidType.BUCKET_VOLUME));
        }

        @Override
        public int insert(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return insert(resource, amount, transaction);
        }

        @Override
        public int insert(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            IFluidHandlerItem h = probe();
            if (h == null || amount == 0) {
                return 0;
            }
            int filled = h.fill(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            return filled > 0 && swap(h.getContainer(), transaction) ? filled : 0;
        }

        @Override
        public int extract(int index, FluidResource resource, int amount, TransactionContext transaction) {
            return extract(resource, amount, transaction);
        }

        @Override
        public int extract(FluidResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            IFluidHandlerItem h = probe();
            if (h == null || amount == 0) {
                return 0;
            }
            FluidStack drained = h.drain(resource.toStack(amount), IFluidHandler.FluidAction.EXECUTE);
            return resource.matches(drained) && drained.getAmount() > 0 && swap(h.getContainer(), transaction) ? drained.getAmount() : 0;
        }

        /** One item at the access becomes {@code after} (nothing, if the item was used up), all or nothing. */
        private boolean swap(ItemStack after, TransactionContext transaction) {
            ItemResource before = this.access.getResource();
            try (Transaction tx = Transaction.open(transaction)) {
                if (this.access.extract(before, 1, tx) != 1) {
                    return false;
                }
                if (!after.isEmpty() && this.access.insert(ItemResource.of(after), after.getCount(), tx) != after.getCount()) {
                    return false;
                }
                tx.commit();
                return true;
            }
        }
    }
}
