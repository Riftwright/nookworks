package dev.nookworks.port.transfer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Our handlers as 1.21.1's: what another mod's pipe, tank or cable is given when it asks a block of ours for
 * {@code IItemHandler}, {@code IFluidHandler} or {@code IEnergyStorage} (registered by {@link TransferBridges}).
 *
 * <p>Each call is a transaction of its own: a simulated one aborts it, so the handler is left exactly as it was; a real
 * one commits it, so the handler's root commit callbacks (a block entity marked changed) run before the call returns.
 * Called while one of our transactions is open on the thread - another mod's handler that reaches back into ours from
 * inside one of our moves - the call is nested in it instead, and a real call then becomes final only with that
 * transaction (it cannot be otherwise: a second root cannot be opened inside the first).
 */
final class LegacyOut {
    private LegacyOut() {}

    /** A transaction for one legacy call: nested in the thread's open one, else a root. */
    static Transaction open() {
        Transaction current = Transaction.current();
        return current != null ? Transaction.open(current) : Transaction.openRoot();
    }

    /** {@code IItemHandler} over a handler of ours. */
    record Items(ResourceHandler<ItemResource> handler) implements IItemHandler {
        @Override
        public int getSlots() {
            return this.handler.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemUtil.getStack(this.handler, slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int moved;
            try (Transaction tx = open()) {
                moved = this.handler.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
                if (!simulate) {
                    tx.commit();
                }
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemResource resource = this.handler.getResource(slot);
            if (resource.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int moved;
            try (Transaction tx = open()) {
                moved = this.handler.extract(slot, resource, Math.min(amount, resource.getMaxStackSize()), tx);
                if (!simulate) {
                    tx.commit();
                }
            }
            return resource.toStack(moved);
        }

        @Override
        public int getSlotLimit(int slot) {
            ItemResource resource = this.handler.getResource(slot);
            return this.handler.getCapacityAsInt(slot, resource.isEmpty() ? ItemResource.EMPTY : resource);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return !stack.isEmpty() && this.handler.isValid(slot, ItemResource.of(stack));
        }
    }

    /** {@code IFluidHandler} over a handler of ours. */
    record Fluids(ResourceHandler<FluidResource> handler) implements IFluidHandler {
        @Override
        public int getTanks() {
            return this.handler.size();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidUtil.getStack(this.handler, tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            FluidResource resource = this.handler.getResource(tank);
            return this.handler.getCapacityAsInt(tank, resource);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return !stack.isEmpty() && this.handler.isValid(tank, FluidResource.of(stack));
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            try (Transaction tx = open()) {
                int moved = this.handler.insert(FluidResource.of(resource), resource.getAmount(), tx);
                if (action.execute()) {
                    tx.commit();
                }
                return moved;
            }
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return FluidStack.EMPTY;
            }
            FluidResource wanted = FluidResource.of(resource);
            try (Transaction tx = open()) {
                int moved = this.handler.extract(wanted, resource.getAmount(), tx);
                if (action.execute()) {
                    tx.commit();
                }
                return wanted.toStack(moved);
            }
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            if (maxDrain <= 0) {
                return FluidStack.EMPTY;
            }
            try (Transaction tx = open()) {
                FluidResource first = ResourceHandlerUtil.findExtractableResource(this.handler, resource -> true, tx);
                if (first == null) {
                    return FluidStack.EMPTY;
                }
                int moved = this.handler.extract(first, maxDrain, tx);
                if (action.execute()) {
                    tx.commit();
                }
                return first.toStack(moved);
            }
        }
    }

    /** {@code IFluidHandlerItem} over a fluid handler of ours on an item, whose access holds the item as it becomes. */
    static final class ItemFluids implements IFluidHandlerItem {
        private final Fluids fluids;
        private final ItemAccesses.OfStack access;

        ItemFluids(ResourceHandler<FluidResource> handler, ItemAccesses.OfStack access) {
            this.fluids = new Fluids(handler);
            this.access = access;
        }

        @Override
        public ItemStack getContainer() {
            return this.access.current();
        }

        @Override
        public int getTanks() {
            return this.fluids.getTanks();
        }

        @Override
        public FluidStack getFluidInTank(int tank) {
            return this.fluids.getFluidInTank(tank);
        }

        @Override
        public int getTankCapacity(int tank) {
            return this.fluids.getTankCapacity(tank);
        }

        @Override
        public boolean isFluidValid(int tank, FluidStack stack) {
            return this.fluids.isFluidValid(tank, stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return this.fluids.fill(resource, action);
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return this.fluids.drain(resource, action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return this.fluids.drain(maxDrain, action);
        }
    }

    /**
     * {@code IEnergyStorage} over a store of ours. 26.3's stores have no "can extract / can receive": they are asked by
     * trying one unit in a transaction that aborts.
     */
    record Energy(EnergyHandler handler) implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (toReceive <= 0) {
                return 0;
            }
            try (Transaction tx = open()) {
                int moved = this.handler.insert(toReceive, tx);
                if (!simulate) {
                    tx.commit();
                }
                return moved;
            }
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (toExtract <= 0) {
                return 0;
            }
            try (Transaction tx = open()) {
                int moved = this.handler.extract(toExtract, tx);
                if (!simulate) {
                    tx.commit();
                }
                return moved;
            }
        }

        @Override
        public int getEnergyStored() {
            return this.handler.getAmountAsInt();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.handler.getCapacityAsInt();
        }

        @Override
        public boolean canExtract() {
            return extractEnergy(1, true) > 0;
        }

        @Override
        public boolean canReceive() {
            return receiveEnergy(1, true) > 0;
        }
    }
}
