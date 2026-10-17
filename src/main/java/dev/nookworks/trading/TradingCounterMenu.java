package dev.nookworks.trading;

import org.jspecify.annotations.Nullable;

import dev.nookworks.NookworksNetwork;
import dev.nookworks.registry.ModMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Trading Counter's screen: its payment slots (3x3) and goods slots (3x3), the player's inventory, and - sent as it
 * changes, as a {@link TradingCounterView} - the villager's offers to tick. Button {@code i} ticks or unticks the
 * {@code i}th offer; {@link #BUTTON_REROLL} re-rolls the villager's trades.
 */
public class TradingCounterMenu extends AbstractContainerMenu {
    public static final int PAY_X = 140;
    public static final int GOODS_X = 212;
    public static final int GRID_Y = 48;
    public static final int INVENTORY_X = 58;
    public static final int INVENTORY_Y = 152;
    /** No villager has near this many offers: a button past it is not ours. */
    public static final int MAX_OFFERS = 4096;
    /**
     * Re-roll the trades of a villager never traded with ({@link TradingCounterBlockEntity#reroll}). Negative, so it is
     * never taken for an offer's button, however many offers there are.
     */
    public static final int BUTTON_REROLL = -1;

    private final Container container;
    private final Inventory inventory;
    private final @Nullable TradingCounterBlockEntity counter;
    /** On the client, the counter as last sent; on the server, as last sent to the player looking. */
    public TradingCounterView view = TradingCounterView.EMPTY;
    private String sent = "";

    /** Client side. */
    public TradingCounterMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(TradingCounterBlockEntity.PAYMENT + TradingCounterBlockEntity.GOODS), null);
    }

    public TradingCounterMenu(int containerId, Inventory inventory, Container container, @Nullable TradingCounterBlockEntity counter) {
        super(ModMenus.TRADING_COUNTER.get(), containerId);
        this.container = container;
        this.inventory = inventory;
        this.counter = counter;
        for (int i = 0; i < TradingCounterBlockEntity.PAYMENT; i++) {
            this.addSlot(new Slot(container, i, PAY_X + (i % 3) * 18, GRID_Y + (i / 3) * 18));
        }
        for (int i = 0; i < TradingCounterBlockEntity.GOODS; i++) {
            this.addSlot(new Slot(container, TradingCounterBlockEntity.FIRST_GOODS + i, GOODS_X + (i % 3) * 18, GRID_Y + (i / 3) * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        this.addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_REROLL) {
            if (this.counter != null) {
                this.counter.reroll(player);
                this.broadcastChanges(); // the new offers shown at once
            }
            return true;
        }
        if (id < 0 || id >= MAX_OFFERS) {
            return false;
        }
        if (this.counter != null) {
            this.counter.toggle(id);
            this.broadcastChanges();
        }
        return true;
    }

    /** The view sent to the player looking whenever what it shows changes. */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (this.counter != null && this.inventory.player instanceof ServerPlayer player
                && player.connection.hasChannel(NookworksNetwork.TradingCounterViewPayload.TYPE)) {
            TradingCounterView now = this.counter.view();
            String print = now.fingerprint();
            if (!print.equals(this.sent)) {
                this.sent = print;
                this.view = now;
                PacketDistributor.sendToPlayer(player, new NookworksNetwork.TradingCounterViewPayload(this.containerId, now));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int counterSlots = TradingCounterBlockEntity.PAYMENT + TradingCounterBlockEntity.GOODS;
        if (index < counterSlots) {
            if (!this.moveItemStackTo(stack, counterSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, TradingCounterBlockEntity.PAYMENT, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.container.stillValid(player);
    }
}
