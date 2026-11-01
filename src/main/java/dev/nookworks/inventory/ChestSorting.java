package dev.nookworks.inventory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.nookworks.storage.SortOrder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.PlayerEnderChestContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.transfer.item.ItemResource;

/**
 * The Sort button on vanilla's chests (the user, 2026-10-04: "普通のラージチェストも大容量樽みたいにソートできたら"):
 * a chest, a large chest, a barrel, an Ender Chest, a chest minecart or boat, a shulker box - anything vanilla opens
 * as its chest screen or its shulker box screen - gathers each kind of item into as few stacks as it fits in, in the
 * order chosen, as a Storage Barrel's Sort does. Only the container's own slots are touched, never the player's.
 * <p>
 * Vanilla's menus only, by their exact class: another mod's menu built on them may have slots with rules of their own.
 * And over real storage only - a block's, an entity's (a minecart, a boat), a large chest's two, the Ender Chest - not a
 * container made up for a screen: a mod's shop or settings screen drawn as a chest, its items buttons. And it is all or
 * nothing: should any slot not let its item be taken, or not take the stack it would be given, nothing
 * moves.
 */
public final class ChestSorting {
    /** The orders a chest sorts in: a barrel's, but for "last added", which a chest does not keep. The first is the default. */
    public static final List<SortOrder> ORDERS = List.of(SortOrder.ID, SortOrder.COUNT, SortOrder.MOD);

    /** Whether this menu is one of vanilla's that the button sorts. */
    public static boolean sortable(AbstractContainerMenu menu) {
        return menu.getClass() == ChestMenu.class || menu.getClass() == ShulkerBoxMenu.class;
    }

    /** How many of the menu's first slots are the container's (the player's inventory follows them). */
    private static int containerSlots(AbstractContainerMenu menu) {
        return menu instanceof ChestMenu chest ? chest.getRowCount() * 9 : menu instanceof ShulkerBoxMenu ? 27 : 0;
    }

    /** An order a chest takes: "last added" asked of one (by an old client, a made-up packet) is the default. */
    public static SortOrder orderOf(int ordinal) {
        SortOrder order = SortOrder.byOrdinal(ordinal);
        return ORDERS.contains(order) ? order : ORDERS.getFirst();
    }

    /** A sort asked for from a client's screen: only of the menu it has open, by the menu's own rules of reach. */
    public static boolean sortOpen(ServerPlayer player, int containerId, int order) {
        return sortOpen(player, containerId, order, false);
    }

    /** As {@link #sortOpen(ServerPlayer, int, int)}, the order turned round if {@code reversed}. */
    public static boolean sortOpen(ServerPlayer player, int containerId, int order, boolean reversed) {
        AbstractContainerMenu menu = player.containerMenu;
        if (player.isSpectator() || menu.containerId != containerId || !menu.stillValid(player)) {
            return false;
        }
        return sort(menu, player, orderOf(order), reversed);
    }

    /** Whether the menu's container is something that stores things, not a screen's stand-in (see the class). */
    static boolean storage(AbstractContainerMenu menu) {
        if (menu.slots.isEmpty()) {
            return false;
        }
        Container container = menu.slots.getFirst().container;
        return container instanceof BlockEntity || container instanceof Entity || container instanceof CompoundContainer
                || container instanceof PlayerEnderChestContainer;
    }

    /** Sorts the container of {@code menu} in {@code order}. False, with nothing moved, if it may not be done. */
    public static boolean sort(AbstractContainerMenu menu, Player player, SortOrder order) {
        return sort(menu, player, order, false);
    }

    /** As {@link #sort(AbstractContainerMenu, Player, SortOrder)}, the order turned round if {@code reversed}. */
    public static boolean sort(AbstractContainerMenu menu, Player player, SortOrder order, boolean reversed) {
        if (!sortable(menu) || !storage(menu)) {
            return false;
        }
        int size = containerSlots(menu);
        List<Slot> slots = menu.slots.subList(0, size);
        Map<ItemResource, Long> counts = new LinkedHashMap<>();
        for (Slot slot : slots) {
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            if (!slot.mayPickup(player)) {
                return false;
            }
            counts.merge(ItemResource.of(stack), (long) stack.getCount(), Long::sum);
        }
        List<SortOrder.Lot> lots = new ArrayList<>();
        counts.forEach((resource, count) -> lots.add(new SortOrder.Lot(resource, count, 0L)));
        lots.sort(order.comparator(reversed));

        List<ItemStack> sorted = new ArrayList<>(size);
        for (SortOrder.Lot lot : lots) {
            long left = lot.count();
            while (left > 0) {
                ItemStack stack = lot.resource().toStack(1);
                int room = Math.min(stack.getMaxStackSize(), slots.get(Math.min(sorted.size(), size - 1)).getMaxStackSize(stack));
                int n = (int) Math.min(left, Math.max(1, room));
                sorted.add(lot.resource().toStack(n));
                left -= n;
            }
        }
        if (sorted.size() > size) {
            return false; // never: as many stacks as there were at most, each as full as it can be
        }
        for (int i = 0; i < sorted.size(); i++) {
            if (!slots.get(i).mayPlace(sorted.get(i))) {
                return false;
            }
        }
        for (int i = 0; i < size; i++) {
            slots.get(i).set(i < sorted.size() ? sorted.get(i) : ItemStack.EMPTY);
        }
        menu.broadcastChanges();
        return true;
    }

    private ChestSorting() {}
}
