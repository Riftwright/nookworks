package dev.nookworks.storage;

import java.util.Optional;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Prediction;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.neoforged.neoforge.world.inventory.StackCopySlot;

/**
 * Storage Barrel GUI: up to six visible rows of oversized slots that scroll over the whole inventory. A barrel with
 * fewer rows than that shows all of them and a shorter screen.
 *
 * <p>Scrolling happens on the server only (a menu button), and the client's copy holds what each visible slot shows,
 * not the barrel's slots: the server sends a slot's new contents before the new scroll row, and diffs them against what
 * that visible slot showed last, so a client copy kept by barrel slot filed them under the old row and showed rows
 * that were not there - most of all after a sort, when rows look alike and are not sent again.
 */
public class StorageMenu extends AbstractContainerMenu {
    public static final int MAX_VISIBLE_ROWS = 6;
    public static final int GRID_X = 8;
    public static final int GRID_Y = 18;

    private final StorageInventory inventory;
    private final BlockPos pos;
    private @Nullable StorageBlockEntity barrel;
    private final DataSlot scroll = DataSlot.standalone();
    private final int visibleRows;
    /** The client's copy: slot i is visible slot i, whatever row the server has scrolled to. */
    private final boolean mirror;
    /**
     * How full it is, for the screen. Read from the barrel on the server; the client keeps what was last sent. A data
     * slot travels as a short, and a Velarium barrel holds 138,240 stacks, so it goes as two: the low 15 bits and the rest.
     */
    private final DataSlot stored = new ServerValue(() -> (int) (Math.min(STORED_MAX, this.barrel.stacksToSpill()) & 0x7FFF));
    private final DataSlot storedHigh = new ServerValue(() -> (int) (Math.min(STORED_MAX, this.barrel.stacksToSpill()) >> 15));
    private static final long STORED_MAX = (1L << 30) - 1;
    /** Within a right-click lifting from a barrel slot: what it lifts is held to half a stack (see StorageSlot.tryRemove). */
    private boolean halfStack;
    /** What a shift-click last moved out of the barrel, and when: what a shift + double-click gathers (BUTTON_GATHER). */
    private ItemStack lastMoved = ItemStack.EMPTY;
    private long lastMovedAt;
    /** How long after that shift-click a gather is taken as its double-click, in ticks: a second, to be safe. */
    public static final int GATHER_TICKS = 20;
    /** The barrel's sort order: the barrel's own on the server, the last sent on the client (or in a test's bare menu). */
    private final DataSlot order = new DataSlot() {
        private int local = SortOrder.DEFAULT.ordinal();

        @Override
        public int get() {
            return StorageMenu.this.barrel == null ? this.local : StorageMenu.this.barrel.sortOrder().ordinal();
        }

        @Override
        public void set(int value) {
            this.local = value;
            if (StorageMenu.this.barrel != null) {
                StorageMenu.this.barrel.setSortOrder(SortOrder.byOrdinal(value));
            }
        }
    };

    /** Whether Sort turns the order round: the barrel's own on the server, the last sent on the client. */
    private final DataSlot reversed = new DataSlot() {
        private int local;

        @Override
        public int get() {
            return StorageMenu.this.barrel == null ? this.local : StorageMenu.this.barrel.sortReversed() ? 1 : 0;
        }

        @Override
        public void set(int value) {
            this.local = value;
            if (StorageMenu.this.barrel != null) {
                StorageMenu.this.barrel.setSortReversed(value != 0);
            }
        }
    };

    /** Client side: the tier is sent by the server when the menu opens. */
    public static StorageMenu client(int containerId, Inventory playerInventory, FriendlyByteBuf data) {
        StorageTier tier = data.readEnum(StorageTier.class);
        return new StorageMenu(containerId, playerInventory, new StorageInventory(tier, () -> {}), BlockPos.ZERO, true);
    }

    /** Told to the barrel so it can open and close its lid with the screen. */
    public StorageMenu watching(StorageBlockEntity barrel, Player player) {
        this.barrel = barrel;
        barrel.startOpen(player);
        return this;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (this.barrel != null) {
            this.barrel.stopOpen(player);
        }
    }

    /** The backing inventory, so a barrel can tell its own screen from someone else's. */
    public StorageInventory inventory() {
        return this.inventory;
    }

    public StorageMenu(int containerId, Inventory playerInventory, StorageInventory inventory, BlockPos pos) {
        this(containerId, playerInventory, inventory, pos, false);
    }

    private StorageMenu(int containerId, Inventory playerInventory, StorageInventory inventory, BlockPos pos, boolean mirror) {
        super(ModMenus.STORAGE.get(), containerId);
        this.inventory = inventory;
        this.pos = pos;
        this.mirror = mirror;
        this.visibleRows = Math.min(MAX_VISIBLE_ROWS, inventory.tier().rows());
        for (int i = 0; i < this.visibleRows * 9; i++) {
            this.addSlot(new StorageSlot(i, GRID_X + (i % 9) * 18, GRID_Y + (i / 9) * 18));
        }
        this.addStandardInventorySlots(playerInventory, 8, this.playerY());
        this.addDataSlot(this.scroll);
        this.addDataSlot(this.stored);
        this.addDataSlot(this.storedHigh);
        this.addDataSlot(this.order);
        this.addDataSlot(this.reversed);
    }

    /** A number only the server can work out: from the barrel there, from the last sync on the client. */
    private final class ServerValue extends DataSlot {
        private final IntSupplier read;
        private int last;

        private ServerValue(IntSupplier read) {
            this.read = read;
        }

        @Override
        public int get() {
            return StorageMenu.this.barrel == null ? this.last : this.read.getAsInt();
        }

        @Override
        public void set(int value) {
            this.last = value;
        }
    }

    /**
     * A slot's count shortened to fit it: 999, 6.4K, 12K, 1.2M, 34M, 1.0B. Always rounded down, so the screen
     * never shows more than is there.
     */
    public static String shortCount(int count) {
        if (count < 1_000) {
            return Integer.toString(count);
        }
        if (count < 10_000) {
            return tenths(count / 100) + "K";
        }
        if (count < 1_000_000) {
            return count / 1_000 + "K";
        }
        if (count < 10_000_000) {
            return tenths(count / 100_000) + "M";
        }
        if (count < 1_000_000_000) {
            return count / 1_000_000 + "M";
        }
        return tenths(count / 100_000_000) + "B";
    }

    private static String tenths(int tenths) {
        return tenths / 10 + "." + tenths % 10;
    }

    /** Stacks the contents come to, out of {@link StorageTier#stackCapacity()}. */
    public int storedStacks() {
        return this.storedHigh.get() << 15 | this.stored.get();
    }


    public StorageTier tier() {
        return this.inventory.tier();
    }

    /** Rows of barrel slots on screen at once. */
    public int visibleRows() {
        return this.visibleRows;
    }

    /** Where the player's inventory starts: under the barrel's rows and the strip saying how full it is. */
    public int playerY() {
        return GRID_Y + this.visibleRows * 18 + 26;
    }

    public int scrollRow() {
        return this.scroll.get();
    }

    public int maxScroll() {
        return Math.max(0, this.inventory.tier().rows() - this.visibleRows);
    }

    /**
     * Button ids are target scroll rows, or one of the negative ids below. The client only asks; the row changes when
     * the server says so.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        switch (id) {
            case BUTTON_SORT -> this.inventory.sort(this.sortOrder(), this.sortReversed());
            case BUTTON_ORDER_NEXT -> this.setOrder(this.sortOrder().next());
            case BUTTON_ORDER_BACK -> this.setOrder(this.sortOrder().previous());
            case BUTTON_REVERSE -> this.reversed.set(this.sortReversed() ? 0 : 1);
            case BUTTON_GATHER -> this.gather(player);
            default -> this.scroll.set(Math.max(0, Math.min(this.maxScroll(), id)));
        }
        return true;
    }

    /** The Sort button. Other button ids are the row to scroll to, so these are negative. */
    public static final int BUTTON_SORT = -1;
    /** The sort order button, clicked (the next order) and right-clicked (the one before). */
    public static final int BUTTON_ORDER_NEXT = -2;
    public static final int BUTTON_ORDER_BACK = -3;
    /** Shift and a double-click on a barrel slot: every stack of that kind in the barrel goes to the player's inventory. */
    public static final int BUTTON_GATHER = -4;
    /** The button between the order's and Sort: the order turned round (ascending, descending), or back. */
    public static final int BUTTON_REVERSE = -5;

    /** Another order, which runs its own way: turned round no more. */
    private void setOrder(SortOrder order) {
        this.order.set(order.ordinal());
        this.reversed.set(0);
    }

    /**
     * Vanilla's shift + double-click moves every stack of a kind from one inventory to the other, but passes over a slot
     * holding more than a stack - most of a barrel's - and sees only the rows on screen. The screen asks for this after
     * it: every slot of the barrel, scrolled away or not, holding what a shift-click has just moved out of it goes to the
     * player's inventory, as far as there is room. Nothing happens unless that shift-click was this moment.
     */
    private void gather(Player player) {
        // a player who has just died has dropped what they carried: a gather now would put the barrel's stacks into an
        // inventory that is gone at the respawn
        if (!player.isAlive() || this.lastMoved.isEmpty() || player.level().getGameTime() - this.lastMovedAt > GATHER_TICKS) {
            return;
        }
        int visible = this.visibleRows * 9;
        for (int i = 0; i < this.inventory.size(); i++) {
            ItemResource resource = this.inventory.getResource(i);
            if (resource.isEmpty() || !resource.matches(this.lastMoved)) {
                continue;
            }
            int held = this.inventory.getAmountAsInt(i);
            ItemStack chunk = resource.toStack(Math.min(held, resource.getMaxStackSize() * 36));
            int before = chunk.getCount();
            int last;
            do { // moveItemStackTo fills at most one empty slot per call
                last = chunk.getCount();
                this.moveItemStackTo(chunk, visible, this.slots.size(), true);
            } while (!chunk.isEmpty() && chunk.getCount() < last);
            int moved = before - chunk.getCount();
            if (moved > 0) {
                this.inventory.set(i, resource, held - moved);
            }
            if (!chunk.isEmpty()) {
                return; // the inventory is full
            }
        }
    }

    /** How Sort orders this barrel. */
    public SortOrder sortOrder() {
        return SortOrder.byOrdinal(this.order.get());
    }

    /** Whether Sort turns that order round. */
    public boolean sortReversed() {
        return this.reversed.get() != 0;
    }

    /**
     * Every other way an item leaves a barrel slot is held to one normal stack too. A slot can hold thousands, and
     * vanilla's number-key / off-hand swap and its "swap with a different item on the cursor" move the slot's whole
     * contents at once - which would put a stack of 4096 into a player's inventory, where a stack cannot be saved.
     * A slot holding more than one stack lends one stack to an empty hotbar or off-hand slot, and refuses a swap.
     * Ctrl+Q, which in vanilla throws a slot's stacks one after another until it is empty, throws one stack: a barrel
     * slot can hold a thousand. A right-click with nothing held lifts half, as vanilla's does, but half a stack at most
     * (vanilla's half of a thousand would be one stack, the same as a left-click).
     */
    @Override
    public void clicked(int slotIndex, int button, ContainerInput input, Player player) {
        // A slot hands the same copy out again only within one click (see StorageSlot.getStackCopy). Kept past it, a copy
        // a number key swapped into the hotbar stayed the slot's own: what was then added to one was added to both.
        this.forgetCopies();
        this.halfStack = input == ContainerInput.PICKUP && button == 1 && this.getCarried().isEmpty();
        try {
            this.click(slotIndex, button, input, player);
        } finally {
            this.halfStack = false;
            this.forgetCopies();
        }
    }

    private void forgetCopies() {
        for (Slot slot : this.slots) {
            if (slot instanceof StorageSlot storage) {
                storage.forget();
            }
        }
    }

    private void click(int slotIndex, int button, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < this.visibleRows * 9 && this.slots.get(slotIndex) instanceof StorageSlot slot) {
            ItemStack held = slot.getItem();
            int oneStack = held.getMaxStackSize();
            if (held.getCount() > oneStack) {
                if (input == ContainerInput.SWAP) {
                    if (!(button >= 0 && button < 9 || button == 40)) {
                        return; // no such key: a client that sends one is not asked what is in slot -1
                    }
                    ItemStack source = player.getInventory().getItem(button);
                    if (source.isEmpty()) {
                        player.getInventory().setItem(button, held.copyWithCount(oneStack));
                        slot.set(held.copyWithCount(held.getCount() - oneStack));
                    }
                    return;
                }
                if (input == ContainerInput.PICKUP && !this.getCarried().isEmpty()
                        && !ItemStack.isSameItemSameComponents(held, this.getCarried())) {
                    return;
                }
                if (input == ContainerInput.THROW && button == 1 && this.getCarried().isEmpty()) {
                    if (player.canDropItems()) {
                        ItemStack thrown = slot.safeTake(oneStack, Integer.MAX_VALUE, player);
                        player.drop(thrown, true, Prediction.PREDICTED);
                        player.handleCreativeModeItemDrop(thrown);
                    }
                    return;
                }
            }
        }
        super.clicked(slotIndex, button, input, player);
    }

    /**
     * Shift-click: a storage slot's stack to the player's inventory - one stack, 64 at most, as a chest's slot gives
     * (the user, 2026-10-12: "shift押しながら左クリック→64個インベントリに移動"; it moved the slot's whole hundreds,
     * up to a full inventory) - or a stack of the player's into storage. Every stack of a kind is shift and a
     * double-click (BUTTON_GATHER).
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        int visible = this.visibleRows * 9;
        if (index < visible) {
            this.lastMoved = stack.copyWithCount(1);
            this.lastMovedAt = player.level().getGameTime();
            int held = stack.getCount(); // read before moving: the inventory's stacks are worked on in place
            ItemStack chunk = stack.copyWithCount(Math.min(held, stack.getMaxStackSize()));
            int before = chunk.getCount();
            // moveItemStackTo fills at most one empty slot per call, so repeat until nothing moves (a stack topping up
            // part-filled ones may still need an empty one for the rest)
            int last;
            do {
                last = chunk.getCount();
                this.moveItemStackTo(chunk, visible, this.slots.size(), true);
            } while (!chunk.isEmpty() && chunk.getCount() < last);
            int moved = before - chunk.getCount();
            if (moved > 0) {
                slot.set(stack.copyWithCount(held - moved));
            }
        } else {
            ItemStack moving = stack.copy();
            ItemStack rest = ItemUtil.insertItemReturnRemaining(this.inventory, moving, false, null);
            slot.set(rest);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        // The barrel itself, not just the spot it stood on: once it is broken - blown up, say, while the screen
        // is open - its contents have already dropped inside the item, and a screen left open on them would let
        // the same items be taken out a second time.
        if (this.barrel != null) {
            return Container.stillValidBlockEntity(this.barrel, player);
        }
        return this.pos.equals(BlockPos.ZERO) || player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * A visible slot that shows storage slot (base + scroll * 9) and may hold far more than 64. On the client it is
     * simply visible slot base of the copy, filled by the server.
     */
    public class StorageSlot extends StackCopySlot {
        private final int base;
        /** The copy last handed out, and the slot it was of: handed out again while the slot still holds the same. */
        private @Nullable ItemStack lastCopy;
        private int lastIndex = -1;

        void forget() {
            this.lastCopy = null;
            this.lastIndex = -1;
        }

        StorageSlot(int base, int x, int y) {
            super(base, x, y);
            this.base = base;
        }

        @Override
        public int getSlotIndex() {
            return StorageMenu.this.mirror ? this.base : this.base + StorageMenu.this.scrollRow() * 9;
        }

        /**
         * The same copy again while the slot is unchanged, within one click (the menu forgets it before and after each).
         * A click asks for the slot's item twice before working on it,
         * and an item that changes what it is clicked on - another mod's pouch taking the cursor's stack in - works on
         * the first copy it was given: with a new copy each time, what was written back was the second, untouched, and
         * the stack it took in was lost.
         */
        @Override
        protected ItemStack getStackCopy() {
            int i = this.getSlotIndex();
            ItemStack now = StorageMenu.this.inventory.getResource(i).toStack(StorageMenu.this.inventory.getAmountAsInt(i));
            if (this.lastCopy != null && this.lastIndex == i && ItemStack.matches(this.lastCopy, now)) {
                return this.lastCopy;
            }
            this.lastCopy = now;
            this.lastIndex = i;
            return now;
        }

        @Override
        protected void setStackCopy(ItemStack stack) {
            StorageMenu.this.inventory.set(this.getSlotIndex(), ItemResource.of(stack), stack.getCount());
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && StorageMenu.this.inventory.isValid(this.getSlotIndex(), ItemResource.of(stack));
        }

        @Override
        public int getMaxStackSize() {
            return StorageMenu.this.inventory.tier().slotCapacity(64);
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return StorageMenu.this.inventory.tier().slotCapacity(stack.getMaxStackSize());
        }

        /** Clicking never lifts more than one normal stack onto the cursor, and a right-click no more than half of one. */
        @Override
        public Optional<ItemStack> tryRemove(int amount, int maxAmount, Player player) {
            int stack = this.getItem().getMaxStackSize();
            return super.tryRemove(Math.min(amount, StorageMenu.this.halfStack ? (stack + 1) / 2 : stack), maxAmount, player);
        }
    }
}
