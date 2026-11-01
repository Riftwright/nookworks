package dev.nookworks.trading;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.Permission;
import dev.nookworks.machine.Readout;
import dev.nookworks.mixin.VillagerAccessor;
import dev.nookworks.registry.ModTrading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.timeline.Timelines;

/**
 * Trading Counter (取引カウンター): a shop counter set in front of a villager, over which the villager sells. The villager
 * stands behind it at full size, in the world, working at its own workstation; the counter takes payment put into it
 * and passes the goods out of its bottom.
 * <ul>
 * <li>It serves the grown villager with a trade standing in the cell behind it. The trades it makes are the ones ticked
 * in its screen, for that villager; another villager there starts with none ticked.</li>
 * <li>Once every {@link #INTERVAL} ticks it makes the first ticked trade, in the villager's own order, that is in stock,
 * paid for, and has room for its goods: one trade, as one made by hand - the stock used, the villager's experience
 * (and a level, in time), its experience orbs, and the reputation a trade gives to whoever set the counter down.</li>
 * <li>At that one's price: their reputation counts (a cured villager's discount), as it would by hand; the Hero of the
 * Village's does not, being theirs only while they are there. Demand raises prices as by hand - and with "Stop when
 * prices rise" ticked in its screen, a trade so raised waits until its price is back down to its own (the user,
 * 2026-10-31).</li>
 * <li>Stock comes back as vanilla's does: the villager restocks at its workstation in working hours, twice a day at most.
 * Nothing here hurries it. It says when it waits for that, and when the villager has no workstation to restock at.</li>
 * <li>It waits while the villager sleeps, while a player trades with it by hand, under a redstone signal, and where its
 * owner may not deal on the land (a claim mod asked as for placing a block where the villager stands).</li>
 * <li>A villager never traded with may have its trades drawn again from the screen (the librarian lottery, without
 * breaking its workstation): see {@link #reroll}.</li>
 * </ul>
 * Payment in by hopper from the top and sides; goods out of the bottom. No power: the villager does the work.
 */
public class TradingCounterBlockEntity extends BlockEntity implements WorldlyContainer, MenuProvider, Readout {
    public static final int PAYMENT = 9;
    public static final int GOODS = 9;
    public static final int FIRST_GOODS = PAYMENT;
    /** One trade a second at most. */
    public static final int INTERVAL = 20;
    /** How long a claim's answer is trusted before it is asked again. */
    private static final int ASK_AGAIN = 600;
    /** Not yet asked (for this villager): a time no game reaches, so no subtraction from it can overflow. */
    private static final long NEVER = -1L;
    /** Half a second between re-rolls at most, whoever clicks: a double click, or a click held on the button, is one. */
    public static final int REROLL_PAUSE = 10;
    private static final int[] IN = range(0, PAYMENT);
    private static final int[] OUT = range(FIRST_GOODS, FIRST_GOODS + GOODS);

    /** What it is doing, or what it waits for: its screen and Jade say so. */
    public enum Status {
        TRADING, NO_VILLAGER, NOT_A_TRADER, SLEEPING, BUSY, HELD, NOT_ALLOWED, NOTHING_TICKED, OUT_OF_STOCK, NO_WORKSTATION,
        NO_PAYMENT, GOODS_FULL, PRICE_UP;

        public Component text() {
            return Component.translatable("gui.nookworks.trading_counter.status." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    /** Whether the villager behind it may have its trades re-rolled, or why not: the screen's button says so. */
    public enum Reroll {
        OK, NO_VILLAGER, NO_PROFESSION, TRADED, SLEEPING, BUSY;

        public Component text() {
            return Component.translatable("gui.nookworks.trading_counter.reroll." + this.name().toLowerCase(Locale.ROOT));
        }
    }

    private final NonNullList<ItemStack> items = NonNullList.withSize(PAYMENT + GOODS, ItemStack.EMPTY);
    /**
     * Whether a trade whose price demand has raised waits until it is back at its own price (the user, 2026-10-31:
     * 「現在の値段より高くなったら取引をやめる（もとの値段以下になったら取引を再開）」); off, as by hand, it trades at
     * whatever the price is. Its own price is the base less the owner's discount: only demand's rise counts.
     */
    private boolean pauseWhenDear;
    private @Nullable UUID owner;
    /** The villager it serves, and the offers ticked for it - by place in its list, and what each was, to catch a list that changed. */
    private @Nullable UUID villager;
    private final List<Integer> ticked = new ArrayList<>();
    private final List<String> tickedWhat = new ArrayList<>();
    private Status status = Status.NO_VILLAGER;
    private int villagerLevel;
    private long askedAt = NEVER;
    private boolean allowed;
    /** When its villager's trades were last re-rolled (not saved: a reload only lets the next click through sooner). */
    private long rerolledAt = NEVER;

    public TradingCounterBlockEntity(BlockPos pos, BlockState state) {
        super(ModTrading.TRADING_COUNTER_ENTITY.get(), pos, state);
    }

    private static int[] range(int from, int to) {
        int[] out = new int[to - from];
        for (int i = 0; i < out.length; i++) {
            out[i] = from + i;
        }
        return out;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
        this.setChanged();
    }

    public @Nullable UUID owner() {
        return this.owner;
    }

    public Status status() {
        return this.status;
    }

    public List<Integer> ticked() {
        return List.copyOf(this.ticked);
    }

    // ---- the villager ----

    /** The cell the villager stands in: behind the counter, away from its front. */
    public static BlockPos stand(BlockPos counter, BlockState state) {
        return counter.relative(state.getValue(TradingCounterBlock.FACING).getOpposite());
    }

    /**
     * The villager standing behind {@code pos}'s counter, or null: one whose feet are in the cell (on its floor, or on a
     * slab or carpet in it) - not one brushing past in the next cell, nor one walking along a wall's top behind the
     * counter, either of which would wipe the ticks; the nearest the middle, should more crowd in.
     */
    public static @Nullable Villager behind(Level level, BlockPos pos, BlockState state) {
        BlockPos cell = stand(pos, state);
        Vec3 middle = Vec3.atBottomCenterOf(cell);
        AABB feet = new AABB(cell);
        Villager best = null;
        for (Villager v : level.getEntitiesOfClass(Villager.class, feet, v -> v.isAlive() && feet.contains(v.position()))) {
            if (best == null || v.distanceToSqr(middle) < best.distanceToSqr(middle)) {
                best = v;
            }
        }
        return best;
    }

    /** A villager that trades: grown, with a profession other than none or nitwit. */
    public static boolean trades(Villager v) {
        var profession = v.getVillagerData().profession();
        return !v.isBaby() && !profession.is(VillagerProfession.NONE) && !profession.is(VillagerProfession.NITWIT);
    }

    /** {@code v}'s reputation with whoever set the counter down: what makes their price, as by hand. */
    public int reputation(Villager v) {
        return this.owner == null ? 0 : v.getGossips().getReputation(this.owner, t -> true);
    }

    public boolean pausesWhenDear() {
        return this.pauseWhenDear;
    }

    /** Ticks or unticks "Stop when prices rise": from the screen. */
    public void togglePauseWhenDear() {
        this.pauseWhenDear = !this.pauseWhenDear;
        this.setChanged();
    }

    /** What {@code offer} asks first at {@code reputation} with no rise from demand: the price it comes back down to. */
    public static int ownPrice(MerchantOffer offer, int reputation) {
        ItemCost cost = offer.getItemCostA();
        int special = -Mth.floor(reputation * offer.getPriceMultiplier());
        return Mth.clamp(cost.count() + special, 1, cost.itemStack().getMaxStackSize());
    }

    /** Whether demand has raised {@code offer}'s price above its own at {@code reputation}. */
    public static boolean dear(MerchantOffer offer, int reputation) {
        return price(offer, reputation) > ownPrice(offer, reputation);
    }

    /**
     * What {@code offer} asks first at {@code reputation}: vanilla's price - its base, raised by demand - lowered by the
     * reputation as vanilla lowers it for a player who comes to trade, between one and a stack.
     */
    public static int price(MerchantOffer offer, int reputation) {
        ItemCost cost = offer.getItemCostA();
        int base = cost.count();
        int demand = Math.max(0, Mth.floor(base * offer.getDemand() * offer.getPriceMultiplier()));
        int special = -Mth.floor(reputation * offer.getPriceMultiplier());
        return Mth.clamp(base + demand + special, 1, cost.itemStack().getMaxStackSize());
    }

    /**
     * What an offer is, to tell it from another: all it asks (at its base price) and all it gives, components and all -
     * a librarian's book re-rolled to another enchantment is another trade, and is not made on the old one's tick. Its
     * uses, demand and the price they make are left out: they change while it stays the same trade. Written out with
     * its keys in order, so the same trade reads the same after a reload.
     */
    private static String what(MerchantOffer offer, HolderLookup.Provider registries) {
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        StringBuilder out = new StringBuilder();
        canonical(ItemCost.CODEC.encodeStart(ops, offer.getItemCostA()).result().orElse(null), out);
        out.append('+');
        offer.getItemCostB().ifPresent(b -> canonical(ItemCost.CODEC.encodeStart(ops, b).result().orElse(null), out));
        out.append('>');
        canonical(ItemStack.CODEC.encodeStart(ops, offer.getResult()).result().orElse(null), out);
        return out.toString();
    }

    private static void canonical(@Nullable Tag tag, StringBuilder out) {
        if (tag instanceof CompoundTag compound) {
            out.append('{');
            compound.keySet().stream().sorted().forEach(key -> {
                out.append(key).append(':');
                canonical(compound.get(key), out);
                out.append(',');
            });
            out.append('}');
        } else if (tag instanceof ListTag list) {
            out.append('[');
            for (Tag each : list) {
                canonical(each, out);
                out.append(',');
            }
            out.append(']');
        } else {
            out.append(tag);
        }
    }

    // ---- working ----

    public static void serverTick(Level level, BlockPos pos, BlockState state, TradingCounterBlockEntity counter) {
        if (Math.floorMod(level.getGameTime() + pos.asLong(), INTERVAL) == 0 && level instanceof ServerLevel server) {
            counter.work(server, pos, state);
        }
    }

    /** A turn of its work: at most one trade. Public for the tests, which turn it when they like. */
    public void work(ServerLevel level, BlockPos pos, BlockState state) {
        Status now = this.turn(level, pos, state);
        if (now != this.status) {
            this.status = now;
            this.setChanged();
        }
    }

    private Status turn(ServerLevel level, BlockPos pos, BlockState state) {
        Villager v = behind(level, pos, state);
        if (v == null) {
            return Status.NO_VILLAGER;
        }
        if (!trades(v)) {
            return Status.NOT_A_TRADER;
        }
        if (!v.getUUID().equals(this.villager)) {
            this.serve(v);
        }
        MerchantOffers offers = v.getOffers();
        this.forgetChanged(offers, level.registryAccess()); // before any waiting, so the screen never shows a stale tick long
        int level0 = v.getVillagerData().level();
        if (level0 > this.villagerLevel) {
            // a level up: the little bell on the counter rings (once: the level is saved, so a reload does not ring it again)
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.6F, 1.6F);
        }
        if (level0 != this.villagerLevel) {
            this.villagerLevel = level0;
            this.setChanged();
        }
        if (level.hasNeighborSignal(pos)) {
            return Status.HELD;
        }
        if (v.isSleeping()) {
            return Status.SLEEPING;
        }
        if (v.isTrading()) {
            return Status.BUSY;
        }
        if (!this.allowed(level, v)) {
            return Status.NOT_ALLOWED;
        }
        if (this.ticked.isEmpty()) {
            return Status.NOTHING_TICKED;
        }
        int reputation = this.reputation(v);
        boolean inStock = false;
        boolean full = false;
        boolean unpaid = false;
        for (int index : this.ticked) {
            MerchantOffer offer = offers.get(index);
            if (offer.isOutOfStock()) {
                continue;
            }
            inStock = true;
            if (this.pauseWhenDear && dear(offer, reputation)) {
                continue; // dearer than its own price: it waits for demand to fall at the villager's restocks
            }
            int priceA = price(offer, reputation);
            if (!this.pay(offer, priceA, false)) {
                unpaid = true;
                continue;
            }
            ItemStack goods = offer.assemble();
            if (!this.store(goods, false)) {
                full = true;
                continue;
            }
            this.pay(offer, priceA, true);
            this.store(goods, true);
            offer.increaseUses();
            ((VillagerAccessor) v).nookworks$rewardTradeXp(offer); // its experience, a level waiting, its orbs: as by hand
            if (this.owner != null) {
                v.getGossips().add(this.owner, GossipType.TRADING, 2); // the reputation a trade gives (ReputationEventType.TRADE)
            }
            level.broadcastEntityEvent(v, (byte) 14); // the sparkle a trade gives
            level.playSound(null, v, SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 0.6F, 1.0F);
            this.setChanged();
            return Status.TRADING;
        }
        if (!inStock) {
            return v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent() ? Status.OUT_OF_STOCK : Status.NO_WORKSTATION;
        }
        // what holds it up: goods with no room, else payment missing for a trade at its price, else every trade dear
        return full ? Status.GOODS_FULL : unpaid ? Status.NO_PAYMENT : Status.PRICE_UP;
    }

    /** Another villager behind it: its own offers, none ticked, the claim asked again where it stands. */
    private void serve(Villager v) {
        this.villager = v.getUUID();
        this.ticked.clear();
        this.tickedWhat.clear();
        this.villagerLevel = v.getVillagerData().level();
        this.askedAt = NEVER;
        this.setChanged();
    }

    /** Ticks that no longer point at what was ticked - the list grew short, or another trade stands there - dropped. */
    private void forgetChanged(MerchantOffers offers, HolderLookup.Provider registries) {
        for (int i = this.ticked.size() - 1; i >= 0; i--) {
            int index = this.ticked.get(i);
            if (index < 0 || index >= offers.size() || !what(offers.get(index), registries).equals(this.tickedWhat.get(i))) {
                this.ticked.remove(i);
                this.tickedWhat.remove(i);
                this.setChanged();
            }
        }
    }

    /**
     * Whether its owner may deal with a villager where {@code v} stands: asked of claim mods as placing a block there
     * would be, for its owner - with no owner (set down by a machine or a command), for an anonymous one, as other
     * machines ask. Asked again now and then, and for a new villager.
     */
    private boolean allowed(ServerLevel level, Villager v) {
        long now = level.getGameTime();
        if (this.askedAt == NEVER || now < this.askedAt || now - this.askedAt >= ASK_AGAIN) {
            this.askedAt = now;
            this.allowed = Permission.mayPlace(level, v.blockPosition(), Direction.UP, Permission.actor(level, this.owner));
        }
        return this.allowed;
    }

    /**
     * Whether the payment slots hold {@code priceA} of what {@code offer} asks first and all it asks second (counted out
     * of the same stacks, should both ask for the same thing); with {@code take}, taken from them.
     */
    private boolean pay(MerchantOffer offer, int priceA, boolean take) {
        List<ItemStack> purse = new ArrayList<>(PAYMENT);
        for (int slot = 0; slot < PAYMENT; slot++) {
            purse.add(this.items.get(slot).copy());
        }
        boolean paid = takeFrom(purse, offer.getItemCostA(), priceA)
                && offer.getItemCostB().map(b -> takeFrom(purse, b, b.count())).orElse(true);
        if (paid && take) {
            for (int slot = 0; slot < PAYMENT; slot++) {
                this.items.set(slot, purse.get(slot).isEmpty() ? ItemStack.EMPTY : purse.get(slot));
            }
        }
        return paid;
    }

    private static boolean takeFrom(List<ItemStack> purse, ItemCost cost, int count) {
        int need = count;
        for (ItemStack stack : purse) {
            if (need > 0 && !stack.isEmpty() && cost.test(stack)) {
                int n = Math.min(need, stack.getCount());
                stack.shrink(n);
                need -= n;
            }
        }
        return need <= 0;
    }

    /** Whether {@code goods} fit in the goods slots (filling a like stack first); with {@code put}, put there. */
    private boolean store(ItemStack goods, boolean put) {
        ItemStack left = goods.copy();
        List<ItemStack> shelf = new ArrayList<>(GOODS);
        for (int slot = FIRST_GOODS; slot < FIRST_GOODS + GOODS; slot++) {
            shelf.add(this.items.get(slot).copy());
        }
        for (ItemStack stack : shelf) {
            if (!left.isEmpty() && !stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, left)) {
                int n = Math.min(left.getCount(), stack.getMaxStackSize() - stack.getCount());
                stack.grow(n);
                left.shrink(n);
            }
        }
        for (int i = 0; i < shelf.size() && !left.isEmpty(); i++) {
            if (shelf.get(i).isEmpty()) {
                shelf.set(i, left.copy());
                left.setCount(0);
            }
        }
        if (left.isEmpty() && put) {
            for (int i = 0; i < GOODS; i++) {
                this.items.set(FIRST_GOODS + i, shelf.get(i));
            }
        }
        return left.isEmpty();
    }

    /**
     * Ticks or unticks the villager's offer at {@code index}, for the villager now behind it: from the screen. Ticks are
     * kept in the villager's own order, which is the order they are tried in.
     */
    public void toggle(int index) {
        if (!(this.level instanceof ServerLevel level)) {
            return;
        }
        Villager v = behind(level, this.worldPosition, this.getBlockState());
        if (v == null || !trades(v)) {
            return;
        }
        if (!v.getUUID().equals(this.villager)) {
            this.serve(v);
        }
        MerchantOffers offers = v.getOffers();
        this.forgetChanged(offers, level.registryAccess()); // a stale tick at this place is gone, not unticked in its stead
        if (index < 0 || index >= offers.size()) {
            return;
        }
        int at = this.ticked.indexOf(index);
        if (at >= 0) {
            this.ticked.remove(at);
            this.tickedWhat.remove(at);
        } else {
            int insert = 0;
            while (insert < this.ticked.size() && this.ticked.get(insert) < index) {
                insert++;
            }
            this.ticked.add(insert, index);
            this.tickedWhat.add(insert, what(offers.get(index), level.registryAccess()));
        }
        this.setChanged();
    }

    // ---- re-rolling ----

    /**
     * Whether {@code v}, the villager behind it, may have its trades re-rolled, or why not. Vanilla's own test for a
     * villager that has not settled into its job ({@code ResetProfession}, which lets one go that loses its workstation):
     * a profession, not none or nitwit, no experience and the first level - once traded with, its trades stay. Nor while
     * it sleeps, or while a player trades with it: their screen shows the old list, its offers carrying their discount.
     */
    public static Reroll rerollable(@Nullable Villager v) {
        if (v == null) {
            return Reroll.NO_VILLAGER;
        }
        if (!trades(v)) {
            return Reroll.NO_PROFESSION;
        }
        if (v.getVillagerXp() > 0 || v.getVillagerData().level() > 1) {
            return Reroll.TRADED;
        }
        if (v.isSleeping()) {
            return Reroll.SLEEPING;
        }
        return v.isTrading() ? Reroll.BUSY : Reroll.OK;
    }

    /**
     * The screen's re-roll button, clicked by {@code player}: the villager behind it draws its first level's trades
     * again, as vanilla draws them when it takes its job - what breaking its workstation and setting it back does, without
     * the waiting (a librarian's book rolls again). Only for one {@link #rerollable}, and only where {@code player} may
     * deal with it as the counter's Mob Bottle does: asked of claim mods as placing a block where it stands - and not in
     * adventure mode, where a workstation could not be broken either. Once every {@link #REROLL_PAUSE} ticks at most. The
     * ticks for it go, the offers they were made on being gone. Returns whether it re-rolled.
     */
    public boolean reroll(Player player) {
        if (!(this.level instanceof ServerLevel level)) {
            return false;
        }
        Villager v = behind(level, this.worldPosition, this.getBlockState());
        Reroll why = rerollable(v);
        if (why != Reroll.OK || v == null) {
            player.sendOverlayMessage(why.text()); // the screen greys its button, but what it shows may be a moment old
            return false;
        }
        long now = level.getGameTime();
        if (this.rerolledAt != NEVER && now >= this.rerolledAt && now - this.rerolledAt < REROLL_PAUSE) {
            return false;
        }
        if (!player.mayBuild() || !Permission.mayPlace(level, v.blockPosition(), Direction.UP, player)) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.trading_counter.not_yours"));
            return false;
        }
        this.rerolledAt = now;
        v.setOffers(new MerchantOffers());
        ((VillagerAccessor) v).nookworks$updateTrades(level);
        this.serve(v); // nothing ticked
        if (this.status != Status.HELD && this.status != Status.NOT_ALLOWED) {
            this.status = Status.NOTHING_TICKED; // as its next turn would say, not "trading" a second longer
        }
        SoundEvent work = v.getVillagerData().profession().value().workSound(); // a librarian's turning pages
        level.playSound(null, v, work != null ? work : SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.broadcastEntityEvent(v, (byte) 14); // a green sparkle, as a trade gives
        return true;
    }

    // ---- what the screen shows ----

    /**
     * How many more times {@code v} may restock today, worked out as vanilla's {@code Villager.shouldRestock} would see
     * it at its next look - a new day (or half a day since the last restock) starts the count again - without making
     * that look, which would change the villager's bookkeeping: vanilla only counts again when the villager checks.
     */
    public static int restocksLeft(ServerLevel level, Villager v) {
        VillagerAccessor books = (VillagerAccessor) v;
        boolean newDay = level.getGameTime() > books.nookworks$lastRestockGameTime() + 12000L;
        long checked = books.nookworks$lastRestockCheckDay();
        long today = level.registryAccess().get(Timelines.OVERWORLD_DAY)
                .map(timeline -> timeline.value().getPeriodCount(level.clockManager())).orElse(0);
        newDay |= checked > 0L && today > checked;
        return newDay ? 2 : Math.max(0, 2 - books.nookworks$restocksToday());
    }

    /** As its screen shows it, for the player looking: the villager and its offers, at the owner's prices, and what is ticked. */
    public TradingCounterView view() {
        if (!(this.level instanceof ServerLevel level)) {
            return TradingCounterView.EMPTY;
        }
        Villager v = behind(level, this.worldPosition, this.getBlockState());
        if (v == null) {
            return new TradingCounterView(Optional.empty(), 0, 0, new MerchantOffers(), List.of(), List.of(), this.status.ordinal(), 0, false,
                    Reroll.NO_VILLAGER.ordinal(), this.pauseWhenDear);
        }
        if (!trades(v)) {
            // a child, a nitwit or one with no job yet: named, as it stands there, with no trades - not "no villager"
            return new TradingCounterView(Optional.of(v.getDisplayName()), 0, 0, new MerchantOffers(), List.of(), List.of(),
                    this.status.ordinal(), 0, false, Reroll.NO_PROFESSION.ordinal(), this.pauseWhenDear);
        }
        MerchantOffers offers = v.getOffers();
        int reputation = this.reputation(v);
        List<Integer> prices = new ArrayList<>(offers.size());
        for (MerchantOffer offer : offers) {
            prices.add(price(offer, reputation));
        }
        boolean same = v.getUUID().equals(this.villager);
        return new TradingCounterView(Optional.of(v.getDisplayName()), v.getVillagerData().level(), v.getVillagerXp(), offers.copy(), prices,
                same ? List.copyOf(this.ticked) : List.of(), this.status.ordinal(), restocksLeft(level, v),
                v.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(), rerollable(v).ordinal(), this.pauseWhenDear);
    }

    @Override
    public void readout(Consumer<Component> lines) {
        lines.accept(this.status.text());
    }

    // ---- menu ----

    @Override
    public Component getDisplayName() {
        return this.getBlockState().getBlock().getName();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new TradingCounterMenu(containerId, inventory, this, this);
    }

    // ---- saving ----

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.storeNullable("Owner", UUIDUtil.CODEC, this.owner);
        output.storeNullable("Villager", UUIDUtil.CODEC, this.villager);
        output.putIntArray("Ticked", this.ticked.stream().mapToInt(Integer::intValue).toArray());
        output.store("TickedWhat", com.mojang.serialization.Codec.STRING.listOf(), List.copyOf(this.tickedWhat));
        output.putInt("Status", this.status.ordinal());
        output.putInt("VillagerLevel", this.villagerLevel);
        if (this.pauseWhenDear) {
            output.putBoolean("pause_when_dear", true);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items.clear();
        ContainerHelper.loadAllItems(input, this.items);
        this.owner = input.read("Owner", UUIDUtil.CODEC).orElse(null);
        this.villager = input.read("Villager", UUIDUtil.CODEC).orElse(null);
        this.pauseWhenDear = input.getBooleanOr("pause_when_dear", false);
        this.ticked.clear();
        this.tickedWhat.clear();
        // As saved - or as an item's block_entity_data, which any creative player can write - so only places an offer
        // can have, each once, in order: a -1 here once stopped the server on the counter's next tick.
        int[] ticks = input.getIntArray("Ticked").orElse(new int[0]);
        List<String> what = input.read("TickedWhat", com.mojang.serialization.Codec.STRING.listOf()).orElse(List.of());
        for (int i = 0; i < ticks.length && i < what.size(); i++) {
            int index = ticks[i];
            if (index < 0 || index >= TradingCounterMenu.MAX_OFFERS || this.ticked.contains(index)) {
                continue;
            }
            int at = 0;
            while (at < this.ticked.size() && this.ticked.get(at) < index) {
                at++;
            }
            this.ticked.add(at, index);
            this.tickedWhat.add(at, what.get(i));
        }
        Status[] all = Status.values();
        this.status = all[Mth.clamp(input.getIntOr("Status", Status.NO_VILLAGER.ordinal()), 0, all.length - 1)];
        this.villagerLevel = input.getIntOr("VillagerLevel", 0);
    }

    // ---- container: payment in from the top and sides, goods out of the bottom ----

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.DOWN ? OUT : IN;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return slot < PAYMENT && side != Direction.DOWN;
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot >= FIRST_GOODS && side == Direction.DOWN;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot < PAYMENT;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        return this.items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int count) {
        ItemStack taken = ContainerHelper.removeItem(this.items, slot, count);
        if (!taken.isEmpty()) {
            this.setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        this.items.set(slot, stack);
        this.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return Container.stillValidBlockEntity(this, player);
    }

    @Override
    public void clearContent() {
        this.items.clear();
        this.setChanged();
    }
}
