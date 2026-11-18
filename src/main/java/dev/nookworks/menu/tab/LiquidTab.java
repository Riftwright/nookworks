package dev.nookworks.menu.tab;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import dev.nookworks.fluid.Bottles;
import dev.nookworks.mercury.MercuryBucketItem;
import dev.nookworks.mercury.MercuryFluids;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import dev.nookworks.port.transfer.Capabilities;
import dev.nookworks.port.transfer.ItemAccess;

/**
 * The Liquid tab of a machine screen (the user, 2026-11-08: 「液体スロットから、手で出し入れする。ホッパーとかに対応させちゃ
 * 駄目。手で出し入れしたいとき用のやつだから」): two slots, each with a round timer beside it. The top one takes an empty
 * bucket or glass bottle and, after {@link #TICKS} ticks, fills it from the machine's tank (of several, the one chosen:
 * 「取り出せる液体が複数あるときは液体の種類を選択する…ボタンも出る」); the bottom one takes a filled bucket or bottle of
 * a liquid the machine takes and, as long, empties it into the machine (「入らない液体は受け付けない」). The result stays
 * in its slot for the player to take. The slots are the menu's own - not the machine's, never saved, reached by no
 * hopper, pipe or conveyor - and what is left in them goes back to the player as the screen closes, as a crafting
 * grid's does. One container a slot.
 * <p>
 * What it works on is a {@link Tanks}: ours is {@code dev.nookworks.machine.MachineLiquids}; an add-on gives its own
 * (dev.nookworks.machine.GaugePouring's pourItem / drawItem do the work on any fluid handler).
 */
public final class LiquidTab {
    /** About a second each way, the dial filling clockwise. */
    public static final int TICKS = 20;
    /** The top slot (empty in, filled out) and the bottom one (filled in, emptied out). */
    public static final int DRAW = 0;
    public static final int POUR = 1;
    /** The most tanks a selector button can stand for (menu buttons 30-39). */
    public static final int MAX_TANKS = 10;

    /** The machine's tanks as the tab works them. {@link #takes} runs on both sides; the rest only on the server. */
    public interface Tanks {
        /** How many tanks; the indices are the selector's (and, for ours, the gauges'). At most {@link #MAX_TANKS}. */
        int count();

        /** Server: whether tank {@code tank} holds something a bucket or bottle could be filled with now. */
        boolean drawable(int tank);

        /** Whether the bottom slot takes {@code filled}: a filled container of a liquid the machine takes, room or not. */
        boolean takes(ItemStack filled);

        /** Server: what {@code empty} becomes filled from tank {@code tank}, or empty when it cannot be. */
        ItemStack draw(int tank, ItemStack empty, boolean simulate);

        /** Server: what {@code filled} becomes poured into the machine, or empty when it cannot be (no room, say). */
        ItemStack pour(ItemStack filled, boolean simulate);

        /**
         * Whether anything can be poured in at all (both sides, fixed for the menu): without, the bottom slot is not shown
         * - an Electric Furnace only gives its experience.
         */
        default boolean pours() {
            return true;
        }
    }

    private final Tanks tanks;
    private final SimpleContainer items = new SimpleContainer(2) {
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };
    private final TabSlot draw;
    private final TabSlot pour;
    private final DataSlot drawProgress = DataSlot.standalone();
    private final DataSlot pourProgress = DataSlot.standalone();
    /** The tank the player picked, or -1 for none yet. */
    private final DataSlot choice = DataSlot.standalone();
    /** Which tanks hold something to draw, a bit each: worked out on the server, sent for the selector. */
    private final DataSlot drawable = DataSlot.standalone();

    private LiquidTab(MenuTabs tabs, Tanks tanks) {
        this.tanks = tanks;
        int x = tabs.contentX();
        int y = tabs.contentY(MachineTab.LIQUID);
        this.draw = new TabSlot(tabs, MachineTab.LIQUID, this.items, DRAW, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return emptyContainer(stack);
            }
        };
        this.pour = new TabSlot(tabs, MachineTab.LIQUID, this.items, POUR, x, y + 20) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return LiquidTab.this.tanks.takes(stack);
            }

            @Override
            public boolean isActive() {
                return super.isActive() && LiquidTab.this.tanks.pours();
            }
        };
        this.choice.set(-1);
    }

    /**
     * Gives the menu of {@code tabs} its Liquid tab: the two slots and four data slots, added by the menu's own
     * {@code addSlot} and {@code addDataSlot} (pass {@code this::addSlot}, {@code this::addDataSlot}), after the player's
     * inventory. The same on both sides.
     */
    public static LiquidTab add(MenuTabs tabs, Tanks tanks, Function<Slot, Slot> addSlot, Function<DataSlot, DataSlot> addDataSlot) {
        LiquidTab tab = new LiquidTab(tabs, tanks);
        tabs.setLiquid(tab);
        addSlot.apply(tabs.add(tab.draw));
        addSlot.apply(tabs.add(tab.pour));
        addDataSlot.apply(tab.drawProgress);
        addDataSlot.apply(tab.pourProgress);
        addDataSlot.apply(tab.choice);
        addDataSlot.apply(tab.drawable);
        return tab;
    }

    /** Whether the bottom slot is there: the machine takes some liquid in ({@link Tanks#pours}). */
    public boolean pours() {
        return this.tanks.pours();
    }

    public TabSlot slot(int which) {
        return which == DRAW ? this.draw : this.pour;
    }

    /** How far along slot {@code which}'s work is, 0 to {@link #TICKS}. */
    public int progress(int which) {
        return (which == DRAW ? this.drawProgress : this.pourProgress).get();
    }

    /** Whether tank {@code tank} holds something to draw, as the server last said. */
    public boolean drawable(int tank) {
        return tank >= 0 && tank < MAX_TANKS && (this.drawable.get() >> tank & 1) != 0;
    }

    /** How many tanks hold something to draw: the selector shows with two or more. */
    public int drawableCount() {
        return Integer.bitCount(this.drawable.get() & (1 << MAX_TANKS) - 1);
    }

    /** The tank the top slot draws from: the player's pick while it holds something, else the first that does; -1: none. */
    public int chosen() {
        int pick = this.choice.get();
        if (this.drawable(pick)) {
            return pick;
        }
        for (int i = 0; i < MAX_TANKS; i++) {
            if (this.drawable(i)) {
                return i;
            }
        }
        return -1;
    }

    /** The player picked tank {@code tank} to draw from (a menu button; the client sets it as it sends it). */
    public boolean choose(int tank) {
        if (tank < 0 || tank >= this.tanks.count()) {
            return false;
        }
        this.choice.set(tank);
        return true;
    }

    // ---- the server ----

    void tick(ServerPlayer player) {
        int bits = 0;
        for (int i = 0; i < Math.min(this.tanks.count(), MAX_TANKS); i++) {
            if (this.tanks.drawable(i)) {
                bits |= 1 << i;
            }
        }
        this.drawable.set(bits);
        this.step(DRAW, player);
        this.step(POUR, player);
    }

    /** One tick of a slot's work: counted up while it can be done, done at {@link #TICKS}, back to naught when it cannot. */
    private void step(int which, ServerPlayer player) {
        DataSlot progress = which == DRAW ? this.drawProgress : this.pourProgress;
        ItemStack in = this.items.getItem(which);
        if (in.isEmpty() || this.work(which, in, true).isEmpty()) {
            progress.set(0);
            return;
        }
        if (progress.get() + 1 < TICKS) {
            progress.set(progress.get() + 1);
            return;
        }
        progress.set(0);
        ItemStack result = this.work(which, in, false);
        if (!result.isEmpty()) {
            this.items.setItem(which, result);
            boolean bottle = in.is(Items.GLASS_BOTTLE) || result.is(Items.GLASS_BOTTLE);
            SoundEvent sound = which == DRAW ? bottle ? SoundEvents.BOTTLE_FILL : SoundEvents.BUCKET_FILL
                    : bottle ? SoundEvents.BOTTLE_EMPTY : SoundEvents.BUCKET_EMPTY;
            player.level().playSound(null, player.blockPosition(), sound, SoundSource.PLAYERS, 0.8F, 1.0F);
        }
    }

    private ItemStack work(int which, ItemStack in, boolean simulate) {
        if (which == POUR) {
            return this.tanks.pour(in.copyWithCount(1), simulate);
        }
        int tank = this.chosen();
        return tank < 0 ? ItemStack.EMPTY : this.tanks.draw(tank, in.copyWithCount(1), simulate);
    }

    /** What is left in the slots back to {@code player}, or dropped where they stood if they are gone. */
    void giveBack(Player player) {
        for (int i = 0; i < this.items.getContainerSize(); i++) {
            ItemStack stack = this.items.removeItemNoUpdate(i);
            if (stack.isEmpty() || !(player instanceof ServerPlayer server)) {
                continue;
            }
            boolean gone = server.isRemoved() && server.getRemovalReason() != Entity.RemovalReason.CHANGED_DIMENSION;
            if (gone || server.hasDisconnected()) {
                server.drop(stack, false);
            } else {
                server.getInventory().placeItemBackInInventory(stack);
            }
        }
    }

    // ---- containers ----

    /** Whether {@code stack} is what the top slot takes: an empty bucket or glass bottle. */
    public static boolean emptyContainer(ItemStack stack) {
        return stack.is(Items.BUCKET) || stack.is(Items.GLASS_BOTTLE);
    }

    /**
     * The liquid a filled bucket or bottle holds, or null: bottles by {@link Bottles} (250 mB), the Mercury Bucket as
     * mercury, other containers by their fluid capability (a bucket, 1,000 mB).
     */
    public static @Nullable Fluid fluidIn(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        Fluid bottled = Bottles.fluidOf(stack);
        if (bottled != null) {
            return bottled;
        }
        if (stack.getItem() instanceof MercuryBucketItem) {
            return MercuryFluids.SOURCE.get();
        }
        var held = ItemAccess.forStack(stack.copyWithCount(1)).getCapability(Capabilities.Fluid.ITEM);
        if (held == null) {
            return null;
        }
        for (int i = 0; i < held.size(); i++) {
            if (!held.getResource(i).isEmpty() && held.getAmountAsLong(i) > 0) {
                return held.getResource(i).getFluid();
            }
        }
        return null;
    }
}
