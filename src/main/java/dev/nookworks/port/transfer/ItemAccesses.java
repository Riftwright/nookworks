package dev.nookworks.port.transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.MapMaker;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.CommonHooks;

/** The kinds of {@link ItemAccess} (26.3 has them as classes of its own; the mod reaches them only through ItemAccess). */
final class ItemAccesses {
    private ItemAccesses() {}

    /** Puts {@code amount} into a handler, onto indices that already hold the resource first, then anywhere. */
    static int insertStacking(ResourceHandler<ItemResource> handler, ItemResource resource, int amount, TransactionContext transaction) {
        int moved = 0;
        int n = handler.size();
        for (int i = 0; i < n && moved < amount; i++) {
            if (resource.equals(handler.getResource(i))) {
                moved += handler.insert(i, resource, amount - moved, transaction);
            }
        }
        for (int i = 0; i < n && moved < amount; i++) {
            if (handler.getResource(i).isEmpty()) {
                moved += handler.insert(i, resource, amount - moved, transaction);
            }
        }
        return moved;
    }

    /** One index of a handler shown as a handler of one index (26.3's {@code RangedResourceHandler.ofSingleIndex}). */
    static final class OneIndex implements ResourceHandler<ItemResource> {
        private final ResourceHandler<ItemResource> handler;
        private final int index;

        OneIndex(ResourceHandler<ItemResource> handler, int index) {
            Objects.checkIndex(index, handler.size());
            this.handler = handler;
            this.index = index;
        }

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, 1);
            return this.handler.getResource(this.index);
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, 1);
            return this.handler.getAmountAsLong(this.index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, 1);
            return this.handler.getCapacityAsLong(this.index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, 1);
            return this.handler.isValid(this.index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            return this.handler.insert(this.index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, 1);
            return this.handler.extract(this.index, resource, amount, transaction);
        }
    }

    /** A menu's cursor stack (one wrapper per menu, so lookups in one transaction share its journal). */
    static final class Cursor extends StackSlot {
        private static final Map<AbstractContainerMenu, Cursor> CURSORS = new MapMaker().weakKeys().weakValues().makeMap();

        private final AbstractContainerMenu menu;

        private Cursor(AbstractContainerMenu menu) {
            this.menu = menu;
        }

        static Cursor of(AbstractContainerMenu menu) {
            return CURSORS.computeIfAbsent(menu, Cursor::new);
        }

        @Override
        protected ItemStack read() {
            return this.menu.getCarried();
        }

        @Override
        protected void write(ItemStack stack) {
            this.menu.setCarried(stack);
        }
    }

    /**
     * A loose stack: a copy of it, which the access may change, read back by {@link #current()}. Made by
     * {@link ItemAccess#forStack} it may only become another stack of its own item (26.3's rule: it is for reading what
     * an item holds); made for another mod's {@code IFluidHandlerItem} over a handler of ours, it may become anything.
     */
    static final class OfStack implements ItemAccess {
        private final @org.jspecify.annotations.Nullable Item item;
        private final SimpleContainer holder;
        private final ResourceHandler<ItemResource> slot;

        OfStack(ItemStack stack) {
            this(stack, true);
        }

        OfStack(ItemStack stack, boolean sameItemOnly) {
            this.item = sameItemOnly ? stack.getItem() : null;
            this.holder = new SimpleContainer(stack.copy());
            this.slot = VanillaContainerWrapper.of(this.holder);
        }

        /** The stack as it is now, after what the access was asked to do. */
        ItemStack current() {
            return this.holder.getItem(0);
        }

        @Override
        public ItemResource getResource() {
            return this.slot.getResource(0);
        }

        @Override
        public int getAmount() {
            return this.slot.getAmountAsInt(0);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            // the stack may change into another of its own item only (a filled bottle stays a bottle), as 26.3's
            return this.item == null || resource.is(this.item) ? this.slot.insert(resource, amount, transaction) : 0;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return this.slot.extract(resource, amount, transaction);
        }
    }

    /** One index of a handler; what does not fit back in it goes anywhere in the handler. */
    static final class OfHandler implements ItemAccess {
        private final ResourceHandler<ItemResource> handler;
        private final int index;

        OfHandler(ResourceHandler<ItemResource> handler, int index) {
            Objects.checkIndex(index, handler.size());
            this.handler = handler;
            this.index = index;
        }

        @Override
        public ItemResource getResource() {
            return this.handler.getResource(this.index);
        }

        @Override
        public int getAmount() {
            return this.handler.getAmountAsInt(this.index);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            int moved = this.handler.insert(this.index, resource, amount, transaction);
            if (moved < amount) {
                moved += this.handler.insert(resource, amount - moved, transaction);
            }
            return moved;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return this.handler.extract(this.index, resource, amount, transaction);
        }
    }

    /** At most one item of another access. */
    record OneByOne(ItemAccess delegate) implements ItemAccess {
        @Override
        public ItemResource getResource() {
            return this.delegate.getResource();
        }

        @Override
        public int getAmount() {
            return Math.min(1, this.delegate.getAmount());
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            return this.delegate.insert(resource, Math.min(amount, 1), transaction);
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return this.delegate.extract(resource, Math.min(amount, 1), transaction);
        }
    }

    /**
     * A slot of a player's (the hand, the cursor): what does not fit back in it goes into the inventory - onto the
     * hands' stacks first, then the main slots, stacking first - and what is left is dropped when the root commits.
     */
    static final class OfPlayer implements ItemAccess {
        private final Player player;
        private final ResourceHandler<ItemResource> slot;
        private final Drops drops = new Drops();

        OfPlayer(Player player, ResourceHandler<ItemResource> slot) {
            this.player = player;
            this.slot = slot;
        }

        @Override
        public ItemResource getResource() {
            return this.slot.getResource(0);
        }

        @Override
        public int getAmount() {
            return this.slot.getAmountAsInt(0);
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            int moved = this.slot.insert(0, resource, amount, transaction);
            if (moved < amount) {
                placeBack(resource, amount - moved, transaction);
            }
            return amount;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            return this.slot.extract(0, resource, amount, transaction);
        }

        private void placeBack(ItemResource resource, int amount, TransactionContext transaction) {
            Inventory inventory = this.player.getInventory();
            ResourceHandler<ItemResource> all = VanillaContainerWrapper.of(inventory);
            int moved = 0;
            for (InteractionHand hand : InteractionHand.values()) {
                int index = hand == InteractionHand.MAIN_HAND ? inventory.selected : Inventory.SLOT_OFFHAND;
                if (moved < amount && resource.equals(all.getResource(index))) {
                    moved += all.insert(index, resource, amount - moved, transaction);
                }
            }
            if (moved < amount) {
                moved += insertStacking(new Range(all, Inventory.INVENTORY_SIZE), resource, amount - moved, transaction);
            }
            if (moved < amount && !this.player.level().isClientSide()) {
                this.drops.add(resource, amount - moved, transaction);
            }
        }

        /** Items to drop at the player's feet once the root commits (an abort takes them back). */
        private final class Drops extends SnapshotJournal<Integer> {
            private final List<ItemStack> pending = new ArrayList<>();

            void add(ItemResource resource, int amount, TransactionContext transaction) {
                updateSnapshots(transaction);
                for (int left = amount; left > 0; ) {
                    int part = Math.min(left, resource.getMaxStackSize());
                    this.pending.add(resource.toStack(part));
                    left -= part;
                }
            }

            @Override
            protected Integer createSnapshot() {
                return this.pending.size();
            }

            @Override
            protected void revertToSnapshot(Integer snapshot) {
                while (this.pending.size() > snapshot) {
                    this.pending.remove(this.pending.size() - 1);
                }
            }

            @Override
            protected void onRootCommit(Integer originalState) {
                List<ItemStack> drop = new ArrayList<>(this.pending);
                this.pending.clear();
                for (ItemStack stack : drop) {
                    CommonHooks.onPlayerTossEvent(OfPlayer.this.player, stack, false);
                }
            }
        }
    }

    /** The first {@code size} indices of a handler (a player's main slots). */
    record Range(ResourceHandler<ItemResource> handler, int size) implements ResourceHandler<ItemResource> {
        @Override
        public ItemResource getResource(int index) {
            Objects.checkIndex(index, this.size);
            return this.handler.getResource(index);
        }

        @Override
        public long getAmountAsLong(int index) {
            Objects.checkIndex(index, this.size);
            return this.handler.getAmountAsLong(index);
        }

        @Override
        public long getCapacityAsLong(int index, ItemResource resource) {
            Objects.checkIndex(index, this.size);
            return this.handler.getCapacityAsLong(index, resource);
        }

        @Override
        public boolean isValid(int index, ItemResource resource) {
            Objects.checkIndex(index, this.size);
            return this.handler.isValid(index, resource);
        }

        @Override
        public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, this.size);
            return this.handler.insert(index, resource, amount, transaction);
        }

        @Override
        public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
            Objects.checkIndex(index, this.size);
            return this.handler.extract(index, resource, amount, transaction);
        }
    }

    /** A creative player's item: taking costs nothing; what it becomes is added to the main slots once if not held. */
    static final class Infinite implements ItemAccess {
        private final ResourceHandler<ItemResource> mainSlots;
        private final ItemResource resource;
        private final int amount;

        Infinite(Player player, ItemResource resource, int amount) {
            this.mainSlots = new Range(VanillaContainerWrapper.of(player.getInventory()), Inventory.INVENTORY_SIZE);
            this.resource = resource;
            this.amount = amount;
        }

        @Override
        public ItemResource getResource() {
            return this.resource;
        }

        @Override
        public int getAmount() {
            return this.amount;
        }

        @Override
        public int insert(ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            if (amount > 0 && !ResourceHandlerUtil.contains(this.mainSlots, resource)) {
                this.mainSlots.insert(resource, 1, transaction);
            }
            return amount;
        }

        @Override
        public int extract(ItemResource resource, int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
            return amount;
        }
    }
}
