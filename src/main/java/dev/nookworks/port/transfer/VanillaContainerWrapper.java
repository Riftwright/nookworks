package dev.nookworks.port.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.MapMaker;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.item.VanillaContainerWrapper}: a vanilla {@link Container} as a
 * transactional handler, slot for slot. A slot's stack from before a transaction is kept and put back if it aborts, so
 * a simulated move leaves the container as it was; the container is marked changed once, after the root commit.
 *
 * <p>One wrapper per container (weakly kept), so two lookups of the same chest in one transaction share their journals.
 *
 * <p>What 1.21.1 cannot do as 26.3 does: 26.3 sets a slot inside a transaction through a form of {@code setItem} that
 * skips the container's side effects, and journals those it wants (a furnace's cooking reset, a hopper's cooldown).
 * Here a count change of the same item is made on the live stack, with no side effect; a slot emptied or filled with
 * another item goes through {@code setItem}, side effects and all, and again when an abort puts it back - so an aborted
 * move that empties a furnace's input slot resets its cooking, and a jukebox or chiseled bookshelf updates its block
 * twice. A hopper given its first item gets its cooldown of 8 when the root commits, as 1.21.1's hopper handler does.
 */
public class VanillaContainerWrapper implements ResourceHandler<ItemResource> {
    private static final Map<Container, VanillaContainerWrapper> WRAPPERS = new MapMaker().weakKeys().weakValues().makeMap();

    /** The wrapper of a container (the same one each time while the container lives). */
    public static ResourceHandler<ItemResource> of(Container container) {
        return internalOf(container);
    }

    static VanillaContainerWrapper internalOf(Container container) {
        VanillaContainerWrapper wrapper = WRAPPERS.computeIfAbsent(container, VanillaContainerWrapper::new);
        wrapper.resize();
        return wrapper;
    }

    private final Container container;
    private int size;
    private final List<Slot> slots = new ArrayList<>();
    private final Committed committed = new Committed();

    VanillaContainerWrapper(Container container) {
        this.container = container;
    }

    private void resize() {
        this.size = this.container.getContainerSize();
        while (this.slots.size() < this.size) {
            this.slots.add(new Slot(this.slots.size()));
        }
    }

    private Slot slot(int index) {
        Objects.checkIndex(index, this.size);
        return this.slots.get(index);
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return slot(index).insert(0, resource, amount, transaction);
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        return slot(index).extract(0, resource, amount, transaction);
    }

    @Override
    public ItemResource getResource(int index) {
        return slot(index).getResource(0);
    }

    @Override
    public long getAmountAsLong(int index) {
        return slot(index).getAmountAsLong(0);
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        return slot(index).getCapacityAsLong(0, resource);
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return slot(index).isValid(0, resource);
    }

    @Override
    public String toString() {
        return "VanillaContainerWrapper[" + this.container + "]";
    }

    /** The wrapper of the other half of a double chest this one is half of, or null. */
    private @org.jspecify.annotations.Nullable VanillaContainerWrapper otherHalf() {
        if (!(this.container instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) || chest.getLevel() == null) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState state = chest.getBlockState();
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.ChestBlock)
                || state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE) == net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
            return null;
        }
        net.minecraft.core.BlockPos otherPos = chest.getBlockPos().relative(net.minecraft.world.level.block.ChestBlock.getConnectedDirection(state));
        net.minecraft.world.level.Level level = chest.getLevel();
        if (!level.isLoaded(otherPos)) {
            return null;
        }
        net.minecraft.world.level.block.state.BlockState otherState = level.getBlockState(otherPos);
        if (otherState.is(state.getBlock()) && otherState.getValue(net.minecraft.world.level.block.ChestBlock.TYPE)
                == state.getValue(net.minecraft.world.level.block.ChestBlock.TYPE).getOpposite()
                && level.getBlockEntity(otherPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity half && half != chest) {
            return internalOf(half);
        }
        return null;
    }

    /** One slot of the container. */
    private final class Slot extends StackSlot {
        private final int index;

        Slot(int index) {
            this.index = index;
        }

        @Override
        protected ItemStack read() {
            return VanillaContainerWrapper.this.container.getItem(this.index);
        }

        @Override
        protected void write(ItemStack stack) {
            Container container = VanillaContainerWrapper.this.container;
            ItemStack live = container.getItem(this.index);
            if (live != stack && !live.isEmpty() && !stack.isEmpty() && ItemStack.isSameItemSameComponents(live, stack)) {
                live.setCount(stack.getCount());
                if (container.getItem(this.index).getCount() == stack.getCount()) {
                    return; // the container hands out its live stack: changed in place, with no side effect
                }
            }
            container.setItem(this.index, stack);
        }

        @Override
        protected boolean isValid(ItemResource resource) {
            return VanillaContainerWrapper.this.container.canPlaceItem(this.index, resource.toStack());
        }

        @Override
        protected int getCapacity(ItemResource resource) {
            Container container = VanillaContainerWrapper.this.container;
            if (this.index == 1 && resource.is(Items.BUCKET) && container instanceof AbstractFurnaceBlockEntity) {
                return 1; // a furnace's fuel slot takes one empty bucket, as vanilla's furnace does
            }
            if (this.index < 3 && container instanceof BrewingStandBlockEntity) {
                return 1; // one bottle a place
            }
            return resource.isEmpty() ? container.getMaxStackSize() : container.getMaxStackSize(resource.toStack());
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            boolean hopperWasEmpty = VanillaContainerWrapper.this.container instanceof HopperBlockEntity hopper && hopper.isEmpty();
            int moved = super.insert(index, resource, amount, transaction);
            if (moved > 0) {
                VanillaContainerWrapper.this.committed.touch(transaction, hopperWasEmpty);
            }
            return moved;
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            int moved = super.extract(index, resource, amount, transaction);
            if (moved > 0) {
                VanillaContainerWrapper.this.committed.touch(transaction, false);
            }
            return moved;
        }
    }

    /**
     * The container's own part in a transaction: marks it changed after the root commit, and starts a hopper's cooldown
     * if an item went into it while it was empty.
     */
    private final class Committed extends SnapshotJournal<Boolean> {
        private boolean cooldown;

        void touch(TransactionContext transaction, boolean startsCooldown) {
            updateSnapshots(transaction);
            if (startsCooldown) {
                this.cooldown = true;
            }
            // For chests: also schedule a setChanged call for the other half, as 26.3's wrapper does (and as vanilla's
            // hopper does through CompoundContainer.setChanged), so a comparator against either half is told.
            VanillaContainerWrapper other = otherHalf();
            if (other != null) {
                other.committed.updateSnapshots(transaction);
            }
        }

        @Override
        protected Boolean createSnapshot() {
            return this.cooldown;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {
            this.cooldown = snapshot;
        }

        @Override
        protected void onRootCommit(Boolean originalState) {
            Container container = VanillaContainerWrapper.this.container;
            if (this.cooldown && container instanceof HopperBlockEntity hopper && !hopper.isOnCustomCooldown()) {
                hopper.setCooldown(8);
            }
            this.cooldown = false;
            container.setChanged();
        }
    }
}
