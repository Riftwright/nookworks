package dev.nookworks.port.transfer;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.google.common.collect.MapMaker;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;

/**
 * Stand-in for NeoForge 26.3's {@code transfer.item.PlayerInventoryWrapper}: a player's inventory as a transactional
 * handler - the 36 main slots (0-8 the hotbar), the four armour slots (36 feet to 39 head) and the offhand (40) - with
 * views of parts of it, an insert that fills it as the game gives a player an item back, and drops that happen only if
 * the root transaction commits.
 *
 * <p>As 26.3's: an armour slot takes only what can be worn there, one at a time, and gives nothing with Curse of Binding
 * to a player not in creative; once a root transaction in which the inventory changed through this wrapper commits, the
 * server sends the player's open menu its changed slots.
 *
 * <p>PORT: 26.3's is the {@code VanillaContainerWrapper} of the inventory itself, so
 * {@code VanillaContainerWrapper.of(inventory)} hands one out. Here {@link VanillaContainerWrapper#of} stays the plain
 * wrapper it was (the mod's own code relies on it), and this one stands beside it, doing every slot's work through it:
 * the two share their journals, so they can be used in one transaction and an abort puts back what either did. The one
 * difference to a caller is that {@code instanceof VanillaContainerWrapper} is false for this.
 */
public final class PlayerInventoryWrapper implements ResourceHandler<ItemResource> {
    private static final Map<Inventory, PlayerInventoryWrapper> WRAPPERS = new MapMaker().weakKeys().weakValues().makeMap();

    /** The wrapper of {@code player}'s inventory (the same one each time while the inventory lives). */
    public static PlayerInventoryWrapper of(Player player) {
        return of(player.getInventory());
    }

    /** The wrapper of a player's inventory. */
    public static PlayerInventoryWrapper of(Inventory inventory) {
        return WRAPPERS.computeIfAbsent(inventory, PlayerInventoryWrapper::new);
    }

    private final Inventory inventory;
    private final Changed changed = new Changed();
    private final Drops drops = new Drops();

    private PlayerInventoryWrapper(Inventory inventory) {
        this.inventory = inventory;
    }

    /** The plain wrapper of the inventory, which does the slots' work (asked anew: it keeps its size up to date). */
    private ResourceHandler<ItemResource> slots() {
        return VanillaContainerWrapper.of(this.inventory);
    }

    // ---- the slots

    @Override
    public int size() {
        return Math.min(slots().size(), Inventory.SLOT_OFFHAND + 1);
    }

    /** The armour slot at {@code index}, or null for any other slot. */
    private static @Nullable EquipmentSlot armourAt(int index) {
        if (index < Inventory.INVENTORY_SIZE || index >= Inventory.SLOT_OFFHAND) {
            return null;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR && slot.getIndex(Inventory.INVENTORY_SIZE) == index) {
                return slot;
            }
        }
        return null;
    }

    private boolean wearable(EquipmentSlot armour, ItemResource resource) {
        return resource.toStack().canEquip(armour, this.inventory.player);
    }

    @Override
    public ItemResource getResource(int index) {
        return slots().getResource(Objects.checkIndex(index, size()));
    }

    @Override
    public long getAmountAsLong(int index) {
        return slots().getAmountAsLong(Objects.checkIndex(index, size()));
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        EquipmentSlot armour = armourAt(index);
        if (armour == null) {
            return slots().getCapacityAsLong(index, resource);
        }
        return resource.isEmpty() || wearable(armour, resource) ? Math.min(1, slots().getCapacityAsLong(index, resource)) : 0;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        Objects.checkIndex(index, size());
        EquipmentSlot armour = armourAt(index);
        return slots().isValid(index, resource) && (armour == null || wearable(armour, resource));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        EquipmentSlot armour = armourAt(index);
        if (armour != null) {
            if (!wearable(armour, resource)) {
                return 0;
            }
            amount = (int) Math.max(0, Math.min(amount, 1 - slots().getAmountAsLong(index)));
            if (amount == 0) {
                return 0;
            }
        }
        int moved = slots().insert(index, resource, amount, transaction);
        if (moved > 0) {
            this.changed.touch(transaction);
        }
        return moved;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        Objects.checkIndex(index, size());
        if (armourAt(index) != null && !this.inventory.player.isCreative()
                && EnchantmentHelper.has(resource.toStack(), EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)) {
            return 0; // Curse of Binding keeps it on
        }
        int moved = slots().extract(index, resource, amount, transaction);
        if (moved > 0) {
            this.changed.touch(transaction);
        }
        return moved;
    }

    // ---- views

    /** Slot {@code slot} alone, as a handler of one index. */
    public ResourceHandler<ItemResource> getSlot(int slot) {
        return RangedResourceHandler.ofSingleIndex(this, slot);
    }

    /** The slot of the hotbar the player has selected. */
    public ResourceHandler<ItemResource> getMainHandSlot() {
        int selected = this.inventory.selected;
        if (!Inventory.isHotbarSlot(selected)) {
            throw new IllegalStateException("Unexpected selected slot of a player: " + selected);
        }
        return getSlot(selected);
    }

    /** The slot held in {@code hand}. */
    public ResourceHandler<ItemResource> getHandSlot(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? getMainHandSlot() : getSlot(Inventory.SLOT_OFFHAND);
    }

    /** Both hands' slots, the main hand's first. */
    public ResourceHandler<ItemResource> getHandSlots() {
        return new CombinedResourceHandler<>(getMainHandSlot(), getHandSlot(InteractionHand.OFF_HAND));
    }

    /** The 36 main slots (the hotbar first). */
    public ResourceHandler<ItemResource> getMainSlots() {
        return RangedResourceHandler.of(this, 0, Inventory.INVENTORY_SIZE);
    }

    /** The armour slot {@code slot} (feet, legs, chest or head). */
    public ResourceHandler<ItemResource> getArmorSlot(EquipmentSlot slot) {
        if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
            throw new IllegalArgumentException("Not an armour slot: " + slot);
        }
        return getSlot(slot.getIndex(Inventory.INVENTORY_SIZE));
    }

    /** The four armour slots, feet first. */
    public ResourceHandler<ItemResource> getArmorSlots() {
        return RangedResourceHandler.of(this, Inventory.INVENTORY_SIZE, Inventory.SLOT_OFFHAND);
    }

    // ---- as the game gives a player an item

    /**
     * Puts {@code resource} where the game puts an item back into a player's inventory: onto the stack in the main hand,
     * then the offhand, if either is of it; then into the main slots, topping up stacks before the empty slots. Returns
     * how much went in.
     */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        int moved = 0;
        for (InteractionHand hand : InteractionHand.values()) {
            ResourceHandler<ItemResource> held = getHandSlot(hand);
            if (moved < amount && held.getResource(0).equals(resource)) {
                moved += held.insert(resource, amount - moved, transaction);
            }
        }
        if (moved < amount) {
            moved += ResourceHandlerUtil.insertStacking(getMainSlots(), resource, amount - moved, transaction);
        }
        return moved;
    }

    /** {@link #insert}, and what does not fit is dropped at the player's feet (if the root commits). */
    public void placeItemBackInInventory(ItemResource resource, int amount, TransactionContext transaction) {
        int moved = insert(resource, amount, transaction);
        if (moved < amount) {
            drop(resource, amount - moved, false, false, transaction);
        }
    }

    /**
     * Drops {@code amount} of {@code resource} as the player throwing it would ({@code dropAround}: scattered about, as
     * on death), once the root transaction commits; nothing if it aborts. On the server only; a client drops nothing.
     */
    public void drop(ItemResource resource, int amount, boolean dropAround, boolean includeThrowerName, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount > 0 && !this.inventory.player.level().isClientSide()) {
            this.drops.add(new Drop(resource, amount, dropAround, includeThrowerName), transaction);
        }
    }

    @Override
    public String toString() {
        return "PlayerInventoryWrapper[" + this.inventory.player + "]";
    }

    /** Tells the player's open menu of the changed slots once a root transaction that changed them commits. */
    private final class Changed extends SnapshotJournal<Boolean> {
        void touch(TransactionContext transaction) {
            updateSnapshots(transaction);
        }

        @Override
        protected Boolean createSnapshot() {
            return Boolean.TRUE;
        }

        @Override
        protected void revertToSnapshot(Boolean snapshot) {}

        @Override
        protected void onRootCommit(Boolean originalState) {
            Player player = PlayerInventoryWrapper.this.inventory.player;
            if (!player.level().isClientSide()) {
                player.containerMenu.broadcastChanges();
            }
        }
    }

    private record Drop(ItemResource resource, int amount, boolean dropAround, boolean includeThrowerName) {}

    /** The drops asked for, thrown once the root commits (an abort takes back those asked for in it). */
    private final class Drops extends SnapshotJournal<Integer> {
        private final Deque<Drop> pending = new ArrayDeque<>();

        void add(Drop drop, TransactionContext transaction) {
            updateSnapshots(transaction);
            this.pending.addLast(drop);
        }

        @Override
        protected Integer createSnapshot() {
            return this.pending.size();
        }

        @Override
        protected void revertToSnapshot(Integer snapshot) {
            while (this.pending.size() > snapshot) {
                this.pending.removeLast();
            }
        }

        @Override
        protected void onRootCommit(Integer originalState) {
            Player player = PlayerInventoryWrapper.this.inventory.player;
            // one at a time from the front: a drop's events may ask for more drops
            while (!this.pending.isEmpty()) {
                Drop drop = this.pending.removeFirst();
                for (int left = drop.amount(); left > 0; ) {
                    int part = Math.min(left, drop.resource().getMaxStackSize());
                    toss(player, drop.resource().toStack(part), drop.dropAround(), drop.includeThrowerName());
                    left -= part;
                }
            }
        }
    }

    /**
     * A player throwing {@code stack}, with NeoForge's {@link ItemTossEvent}: 1.21.1's {@link CommonHooks#onPlayerTossEvent}
     * for a stack thrown ahead; for one scattered about, the same steps with the game's scattering drop.
     */
    private static void toss(Player player, ItemStack stack, boolean dropAround, boolean includeThrowerName) {
        if (!dropAround) {
            CommonHooks.onPlayerTossEvent(player, stack, includeThrowerName);
            return;
        }
        player.captureDrops(new java.util.ArrayList<>());
        ItemEntity thrown = player.drop(stack, true, includeThrowerName);
        player.captureDrops(null);
        if (thrown == null) {
            return;
        }
        ItemTossEvent event = new ItemTossEvent(thrown, player);
        if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
            player.level().addFreshEntity(event.getEntity());
        }
    }
}
