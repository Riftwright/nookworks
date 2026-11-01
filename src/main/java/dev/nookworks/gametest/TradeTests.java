package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;
import static dev.nookworks.gametest.TransportTests.countIn;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.logging.LogUtils;

import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModTrading;
import dev.nookworks.trading.TradingCounterBlock;
import dev.nookworks.trading.TradingCounterBlockEntity;
import dev.nookworks.trading.TradingCounterBlockEntity.Status;
import dev.nookworks.trading.TradingCounterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * The Trading Counter (the user, 2026-10-04: "念入りにチェックしながら"): the trades it makes and the ones it does not -
 * ticked, paid, in stock, with room for the goods, one at a time; the price, at its owner's reputation; what a trade
 * gives the villager and the owner; when it waits, and says why; another villager or a changed list leaving no stale
 * ticks; hoppers in and out; the villager let out and taken back by bottle; a fence's height to climb; saving.
 */
final class TradeTests {
    /** The counter faces north, so its villager stands south of it, at {@link #CELL}. */
    private static final BlockPos COUNTER = new BlockPos(3, 1, 2);
    private static final BlockPos CELL = new BlockPos(3, 1, 3);

    static void register() {
        test("trading_counter_makes_a_ticked_trade_paid_for", TradeTests::makesATrade);
        test("trading_counter_waits_for_a_tick_payment_and_room", TradeTests::waits);
        test("trading_counter_stops_sold_out_and_says_why", TradeTests::soldOut);
        test("trading_counter_prices_at_its_owners_reputation", TradeTests::prices);
        test("trading_counter_stops_while_prices_are_up_when_told_to", TradeTests::pausesWhenDear);
        test("trading_counter_trades_level_its_villager", 120, TradeTests::levelsUp);
        test("trading_counter_starts_another_villager_with_nothing_ticked", TradeTests::anotherVillager);
        test("trading_counter_drops_ticks_whose_trade_changed", TradeTests::changedList);
        test("trading_counter_waits_while_a_player_trades_sleeps_or_redstone", TradeTests::waitsForOthers);
        test("trading_counter_trades_nothing_on_land_its_owner_may_not", TradeTests::claimedLand);
        test("trading_counter_takes_payment_from_above_and_gives_goods_below", 300, TradeTests::hoppers);
        test("trading_counter_lets_a_villager_out_and_takes_it_back_by_bottle", TradeTests::bottles);
        test("trading_counter_is_a_fence_high_to_climb", TradeTests::fenceHigh);
        test("trading_counter_keeps_its_ticks_and_owner_saved_and_trusts_no_others", TradeTests::saved);
        test("trading_counter_serves_only_a_villager_standing_in_its_cell", TradeTests::onlyInTheCell);
        test("trading_counter_broken_drops_what_it_holds", TradeTests::brokenDrops);
    }

    // ---- setting up ----

    /** A counter facing north at {@code pos}, set down by {@code owner} (or by no one: a machine, a command). */
    private static TradingCounterBlockEntity counter(GameTestHelper helper, BlockPos pos, @Nullable UUID owner) {
        helper.setBlock(pos, ModTrading.TRADING_COUNTER.get().defaultBlockState().setValue(TradingCounterBlock.FACING, Direction.NORTH));
        TradingCounterBlockEntity counter = helper.getBlockEntity(pos, TradingCounterBlockEntity.class);
        if (owner != null) {
            counter.setOwner(owner);
        }
        return counter;
    }

    /** Two emeralds for three bread, four times, two experience each. */
    private static MerchantOffer bread(int maxUses, int xp) {
        return new MerchantOffer(new ItemCost(Items.EMERALD, 2), Optional.empty(), new ItemStack(Items.BREAD, 3), 0, maxUses, xp, 0.05F, 0);
    }

    /** An armorer standing in {@code at} with no will of its own (it stays put), selling only {@code offers}. */
    private static Villager villager(GameTestHelper helper, BlockPos at, MerchantOffer... offers) {
        Villager v = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, at);
        v.setVillagerData(v.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.ARMORER).withLevel(1));
        MerchantOffers list = v.getOffers();
        list.clear();
        list.addAll(List.of(offers));
        return v;
    }

    private static void turn(GameTestHelper helper, TradingCounterBlockEntity counter) {
        counter.work(helper.getLevel(), counter.getBlockPos(), counter.getBlockState());
    }

    // ---- trading ----

    /**
     * Ticked from its screen (the menu's button, as a click sends it) and paid for, it makes the trade: the payment
     * taken, the goods put out, the stock used, the villager's experience and orbs, the owner's reputation - one trade a
     * turn, until the payment runs out.
     */
    private static void makesATrade(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradingCounterBlockEntity counter = counter(helper, COUNTER, owner);
        Villager v = villager(helper, CELL, bread(4, 2));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        TradingCounterMenu menu = new TradingCounterMenu(0, player.getInventory(), counter, counter);
        check(helper, menu.clickMenuButton(player, 0), "the button is taken");
        turn(helper, counter);
        check(helper, counter.ticked().equals(List.of(0)), "the offer is ticked: " + counter.ticked());
        check(helper, counter.status() == Status.NO_PAYMENT, "and it waits for payment: " + counter.status());
        counter.setItem(0, new ItemStack(Items.EMERALD, 5));
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING, "paid, it trades: " + counter.status());
        check(helper, countIn(counter, Items.EMERALD) == 3 && countIn(counter, Items.BREAD) == 3, "two emeralds for three bread");
        check(helper, counter.getItem(TradingCounterBlockEntity.FIRST_GOODS).is(Items.BREAD), "the bread among the goods");
        check(helper, v.getOffers().get(0).getUses() == 1, "a use of the stock");
        check(helper, v.getVillagerXp() == 2, "the villager's experience: " + v.getVillagerXp());
        check(helper, v.getGossips().getReputation(owner, t -> t == GossipType.TRADING) == 2, "the owner's reputation for a trade");
        check(helper, !helper.getEntities(EntityTypes.EXPERIENCE_ORB).isEmpty(), "and orbs, as by hand");
        turn(helper, counter);
        check(helper, countIn(counter, Items.EMERALD) == 1 && countIn(counter, Items.BREAD) == 6, "one trade a turn: the second");
        turn(helper, counter);
        check(helper, countIn(counter, Items.EMERALD) == 1 && counter.status() == Status.NO_PAYMENT, "one emerald pays for nothing");
        check(helper, menu.clickMenuButton(player, 0) && counter.ticked().isEmpty(), "a second click unticks it");
        check(helper, !menu.clickMenuButton(player, TradingCounterMenu.MAX_OFFERS), "a button past any offer is refused");
        helper.succeed();
    }

    /** Nothing ticked, nothing paid, no room for the goods: no trade, and each time it says which. */
    private static void waits(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        villager(helper, CELL, bread(4, 2));
        counter.setItem(0, new ItemStack(Items.EMERALD, 2));
        turn(helper, counter);
        check(helper, counter.status() == Status.NOTHING_TICKED && countIn(counter, Items.EMERALD) == 2, "nothing ticked: " + counter.status());
        counter.toggle(0);
        for (int i = 0; i < TradingCounterBlockEntity.GOODS; i++) {
            counter.setItem(TradingCounterBlockEntity.FIRST_GOODS + i, new ItemStack(Items.DIRT, 64));
        }
        turn(helper, counter);
        check(helper, counter.status() == Status.GOODS_FULL && countIn(counter, Items.EMERALD) == 2, "no room: nothing paid: " + counter.status());
        counter.setItem(TradingCounterBlockEntity.FIRST_GOODS + 4, new ItemStack(Items.BREAD, 62));
        turn(helper, counter);
        check(helper, counter.status() == Status.GOODS_FULL && countIn(counter, Items.EMERALD) == 2,
                "two loaves of room is not room for three");
        counter.setItem(TradingCounterBlockEntity.FIRST_GOODS + 4, new ItemStack(Items.BREAD, 61));
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING && countIn(counter, Items.BREAD) == 64, "three loaves of room is");
        helper.succeed();
    }

    /** The stock runs out: it stops, and says whether the villager can restock - and how often more today. */
    private static void soldOut(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        Villager v = villager(helper, CELL, bread(1, 2));
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 4));
        turn(helper, counter);
        turn(helper, counter);
        check(helper, countIn(counter, Items.BREAD) == 3 && countIn(counter, Items.EMERALD) == 2, "one trade, and no more");
        check(helper, counter.status() == Status.NO_WORKSTATION, "with no workstation it never restocks: " + counter.status());
        check(helper, !counter.view().workstation(), "its screen says so");
        v.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CELL.east())));
        turn(helper, counter);
        check(helper, counter.status() == Status.OUT_OF_STOCK && counter.view().workstation(), "with one, it waits for it: " + counter.status());
        check(helper, counter.view().restocksLeft() == 2, "two restocks left today: " + counter.view().restocksLeft());
        v.restock();
        check(helper, counter.view().restocksLeft() == 1, "then one: " + counter.view().restocksLeft());
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING && countIn(counter, Items.BREAD) == 6, "restocked, it trades again");
        v.restock();
        check(helper, counter.view().restocksLeft() == 0, "then none");
        helper.succeed();
    }

    /**
     * Its owner's price: their reputation with the villager lowers it as it would by hand, another player's does not,
     * and demand raises it - and what it takes is that price.
     */
    private static void prices(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradingCounterBlockEntity counter = counter(helper, COUNTER, owner);
        MerchantOffer dear = new MerchantOffer(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.BREAD, 1), 0, 12, 1, 0.05F, 0);
        Villager v = villager(helper, CELL, dear);
        v.getGossips().add(UUID.randomUUID(), GossipType.MAJOR_POSITIVE, 20);
        check(helper, counter.view().prices().get(0) == 8, "another's reputation is theirs alone: " + counter.view().prices());
        v.getGossips().add(owner, GossipType.MAJOR_POSITIVE, 20); // cured: 20 x 5 = 100, a 5 emerald discount at 0.05
        check(helper, counter.view().prices().get(0) == 3, "the owner's cuts it: " + counter.view().prices());
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 3));
        turn(helper, counter);
        check(helper, countIn(counter, Items.EMERALD) == 0 && countIn(counter, Items.BREAD) == 1, "three emeralds do");
        MerchantOffer wanted = new MerchantOffer(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.BREAD, 1), 0, 12, 1, 0.05F, 4);
        check(helper, TradingCounterBlockEntity.price(wanted, 0) == 9, "demand raises it as vanilla does: 8 + 8 x 4 x 0.05");
        check(helper, TradingCounterBlockEntity.price(wanted, 1000) == 1, "never below one");
        helper.succeed();
    }

    /**
     * "Stop when prices rise" (the user, 2026-10-31): unticked, a trade demand has raised is made at its raised price,
     * as by hand; ticked, it waits - saying so - until demand falls at a restock and the price is back to its own (the
     * owner's discount still counting), then trades again. A trade at its price that wants payment is said first.
     * Kept when saved.
     */
    private static void pausesWhenDear(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradingCounterBlockEntity counter = counter(helper, COUNTER, owner);
        MerchantOffer raised = new MerchantOffer(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.BREAD, 1), 0, 12, 1, 0.05F, 4);
        MerchantOffer cheap = new MerchantOffer(new ItemCost(Items.EMERALD, 30), Optional.empty(), new ItemStack(Items.APPLE, 1), 0, 12, 1, 0.05F, 0);
        Villager v = villager(helper, CELL, raised, cheap);
        check(helper, TradingCounterBlockEntity.dear(raised, 0) && TradingCounterBlockEntity.ownPrice(raised, 0) == 8
                && !TradingCounterBlockEntity.dear(cheap, 0), "demand has raised the bread to 9 from its own 8; the apple not");
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 20));
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING && countIn(counter, Items.EMERALD) == 11, "unticked, it pays the raised 9");
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        TradingCounterMenu menu = new TradingCounterMenu(0, player.getInventory(), counter, counter);
        check(helper, menu.clickMenuButton(player, TradingCounterMenu.BUTTON_PAUSE_WHEN_DEAR) && counter.pausesWhenDear()
                && counter.view().pauseWhenDear(), "the screen's box ticks it, and the screen shows it ticked");
        turn(helper, counter);
        check(helper, counter.status() == Status.PRICE_UP && countIn(counter, Items.EMERALD) == 11, "ticked, it waits: " + counter.status());
        counter.toggle(1); // the apple at its own 30, with 11 emeralds: payment is what it waits for now, and says so
        turn(helper, counter);
        check(helper, counter.status() == Status.NO_PAYMENT, "a trade at its price wanting payment is said first: " + counter.status());
        counter.toggle(1);
        v.getOffers().get(0).updateDemand(); // a restock with one use of twelve: demand 4 + 1 - 11, below nothing
        check(helper, !TradingCounterBlockEntity.dear(v.getOffers().get(0), 0), "back at its own price");
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING && countIn(counter, Items.EMERALD) == 3, "it trades again, at 8");
        try (var reporter = new ProblemReporter.ScopedCollector(counter.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            counter.saveWithoutMetadata(output);
            var copy = new TradingCounterBlockEntity(counter.getBlockPos(), counter.getBlockState());
            copy.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            check(helper, copy.pausesWhenDear(), "kept when saved");
        }
        helper.succeed();
    }

    /** Its trades bring the villager up a level, as trades by hand do; the tick stays on its trade, the new ones come in after. */
    private static void levelsUp(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        Villager v = villager(helper, CELL, bread(4, 10));
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 2));
        turn(helper, counter);
        check(helper, v.getVillagerXp() == 10, "ten experience: enough for Apprentice");
        helper.runAfterDelay(60, () -> {
            check(helper, v.getVillagerData().level() == 2, "an Apprentice now: " + v.getVillagerData().level());
            check(helper, v.getOffers().size() > 1, "with the new trades a level brings");
            turn(helper, counter);
            check(helper, counter.ticked().equals(List.of(0)), "the tick still on the bread: " + counter.ticked());
            helper.succeed();
        });
    }

    /** Another villager in its place: its own list, nothing of the last one's ticked. */
    private static void anotherVillager(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        Villager first = villager(helper, CELL, bread(4, 2));
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 4));
        turn(helper, counter);
        check(helper, countIn(counter, Items.BREAD) == 3, "the first trades");
        first.discard();
        villager(helper, CELL, bread(4, 2));
        check(helper, counter.view().ticked().isEmpty(), "the screen shows the newcomer nothing ticked");
        turn(helper, counter);
        check(helper, counter.ticked().isEmpty() && counter.status() == Status.NOTHING_TICKED && countIn(counter, Items.BREAD) == 3,
                "and it makes none of its trades unticked: " + counter.status());
        check(helper, countIn(counter, Items.EMERALD) == 2, "the payment is left alone");
        helper.getEntities(EntityTypes.VILLAGER).forEach(Villager::discard);
        Villager baby = villager(helper, CELL, bread(4, 2));
        baby.setAge(-24000);
        turn(helper, counter);
        check(helper, counter.status() == Status.NOT_A_TRADER && countIn(counter, Items.EMERALD) == 2, "a child does not trade: " + counter.status());
        baby.discard();
        Villager nitwit = villager(helper, CELL, bread(4, 2));
        nitwit.setVillagerData(nitwit.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.NITWIT));
        turn(helper, counter);
        check(helper, counter.status() == Status.NOT_A_TRADER && countIn(counter, Items.EMERALD) == 2, "nor a nitwit: " + counter.status());
        // its screen names it, with no trades - not "no villager", which would contradict the status line
        check(helper, counter.view().villager().isPresent() && counter.view().offers().isEmpty(), "the screen names the nitwit, no trades");
        helper.succeed();
    }

    /** A list that changed under a tick - a trade gone, another in its place - drops the tick rather than make the new trade. */
    private static void changedList(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        Villager v = villager(helper, CELL, bread(4, 2), bread(4, 2));
        counter.toggle(1);
        counter.setItem(0, new ItemStack(Items.EMERALD, 8));
        counter.setItem(1, new ItemStack(Items.IRON_INGOT, 8));
        v.getOffers().set(1, new MerchantOffer(new ItemCost(Items.IRON_INGOT, 1), new ItemStack(Items.IRON_HELMET), 4, 2, 0.05F));
        turn(helper, counter);
        check(helper, counter.ticked().isEmpty() && countIn(counter, Items.IRON_HELMET) == 0, "another trade there: the tick dropped");
        counter.toggle(1);
        v.getOffers().remove(1);
        turn(helper, counter);
        check(helper, counter.ticked().isEmpty() && countIn(counter, Items.IRON_INGOT) == 8, "a trade gone: the tick dropped");
        // a librarian's book re-rolled: the same item for the same emeralds, another enchantment on it
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        v.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD, 2),
                EnchantmentHelper.createBook(new EnchantmentInstance(enchantments.getOrThrow(Enchantments.MENDING), 1)), 4, 2, 0.05F));
        counter.toggle(1);
        turn(helper, counter);
        check(helper, counter.ticked().equals(List.of(1)) && countIn(counter, Items.ENCHANTED_BOOK) == 1, "the Mending book is made");
        v.getOffers().set(1, new MerchantOffer(new ItemCost(Items.EMERALD, 2),
                EnchantmentHelper.createBook(new EnchantmentInstance(enchantments.getOrThrow(Enchantments.BANE_OF_ARTHROPODS), 1)), 4, 2, 0.05F));
        turn(helper, counter);
        check(helper, counter.ticked().isEmpty() && countIn(counter, Items.ENCHANTED_BOOK) == 1, "re-rolled to Bane: not made on Mending's tick");
        helper.succeed();
    }

    /** A player trading with the villager by hand, the villager asleep, a redstone signal: it waits, and says which. */
    private static void waitsForOthers(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        Villager v = villager(helper, CELL, bread(4, 2));
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 8));
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        v.setTradingPlayer(player);
        turn(helper, counter);
        check(helper, counter.status() == Status.BUSY && countIn(counter, Items.BREAD) == 0, "a player trading: " + counter.status());
        v.setTradingPlayer(null);
        // 26.3 lays a sleeper down in a bed only
        BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        helper.setBlock(CELL.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        helper.setBlock(CELL, bed.setValue(BedBlock.PART, BedPart.HEAD));
        v.startSleeping(helper.absolutePos(CELL));
        turn(helper, counter);
        check(helper, counter.status() == Status.SLEEPING && countIn(counter, Items.BREAD) == 0, "asleep: " + counter.status());
        v.stopSleeping();
        // up from the bed it stands beside it: the bed taken away, it is put back in the cell
        helper.setBlock(CELL, Blocks.AIR);
        helper.setBlock(CELL.south(), Blocks.AIR);
        v.snapTo(helper.absoluteVec(Vec3.atBottomCenterOf(CELL)));
        helper.setBlock(COUNTER.west(), Blocks.REDSTONE_BLOCK);
        turn(helper, counter);
        check(helper, counter.status() == Status.HELD && countIn(counter, Items.BREAD) == 0, "a signal: " + counter.status());
        helper.setBlock(COUNTER.west(), Blocks.AIR);
        turn(helper, counter);
        check(helper, counter.status() == Status.TRADING && countIn(counter, Items.BREAD) == 3, "then it trades");
        helper.succeed();
    }

    /** Where a claim mod refuses its owner the land the villager stands on, it makes no trade, and says so. */
    private static void claimedLand(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        villager(helper, CELL, bread(4, 2));
        counter.toggle(0);
        counter.setItem(0, new ItemStack(Items.EMERALD, 2));
        AABB land = helper.getBounds();
        Consumer<BlockEvent.EntityPlaceEvent> placing = event -> {
            if (land.contains(Vec3.atCenterOf(event.getPos()))) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityPlaceEvent.class, placing);
        AfterTest.undo(helper, () -> NeoForge.EVENT_BUS.unregister(placing));
        turn(helper, counter);
        check(helper, counter.status() == Status.NOT_ALLOWED && countIn(counter, Items.EMERALD) == 2, "no trade: " + counter.status());
        // one no one set down asks as an anonymous machine, not as no one at all
        TradingCounterBlockEntity nobodys = counter(helper, new BlockPos(1, 1, 5), null);
        villager(helper, new BlockPos(1, 1, 6), bread(4, 2));
        nobodys.toggle(0);
        nobodys.setItem(0, new ItemStack(Items.EMERALD, 2));
        turn(helper, nobodys);
        check(helper, nobodys.status() == Status.NOT_ALLOWED && countIn(nobodys, Items.EMERALD) == 2, "no one's: no trade either: " + nobodys.status());
        helper.succeed();
    }

    /** Payment by hopper from above, the goods drawn out below by another: the counter as it is built in a trading hall. */
    private static void hoppers(GameTestHelper helper) {
        BlockPos counterAt = COUNTER.above();
        TradingCounterBlockEntity counter = counter(helper, counterAt, UUID.randomUUID());
        helper.assertTrue(helper.getBlockState(counterAt).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(counterAt),
                Direction.NORTH) == 0, "empty, a comparator reads nothing");
        helper.setBlock(CELL, Blocks.STONE);
        villager(helper, CELL.above(), bread(4, 2));
        helper.setBlock(COUNTER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        helper.setBlock(counterAt.above(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity in = helper.getBlockEntity(counterAt.above(), HopperBlockEntity.class);
        HopperBlockEntity out = helper.getBlockEntity(COUNTER, HopperBlockEntity.class);
        in.setItem(0, new ItemStack(Items.EMERALD, 4));
        in.setItem(1, new ItemStack(Items.BREAD, 1)); // pushed in as payment; the hopper below takes only goods
        counter.toggle(0);
        helper.succeedWhen(() -> {
            helper.assertTrue(countIn(out, Items.BREAD) == 6, "six bread out below");
            helper.assertTrue(countIn(counter, Items.EMERALD) == 0 && countIn(in, Items.EMERALD) == 0, "all four emeralds paid");
            helper.assertTrue(countIn(counter, Items.BREAD) == 1, "the loaf put in from above stays in the payment slots");
            // a comparator reads its slots as a chest's: the loaf left in, a little
            BlockState state = helper.getBlockState(counterAt);
            helper.assertTrue(state.hasAnalogOutputSignal()
                    && state.getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(counterAt), Direction.NORTH) > 0,
                    "a comparator reads the loaf left in its slots");
        });
    }

    /**
     * A Mob Bottle with a villager in it, clicked on the counter, lets it out behind it - not where one stands already -
     * and an empty bottle takes it back; a bottle with anything else in it is not let out there.
     */
    private static void bottles(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        Villager template = villager(helper, new BlockPos(5, 1, 5), bread(4, 2));
        ItemStack bottle = MobBottleItem.filled(template);
        ItemStack second = bottle.copy();
        template.discard();
        BlockPos at = helper.absolutePos(COUNTER);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false);

        player.setItemInHand(InteractionHand.MAIN_HAND, bottle);
        player.gameMode.useItemOn(player, helper.getLevel(), bottle, InteractionHand.MAIN_HAND, hit);
        Villager behind = TradingCounterBlockEntity.behind(helper.getLevel(), at, counter.getBlockState());
        check(helper, behind != null && behind.getOffers().get(0).getResult().is(Items.BREAD), "let out behind the counter, its trades with it");
        check(helper, MobBottleItem.captured(player.getMainHandItem()) == null, "the bottle emptied");

        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        player.gameMode.useItemOn(player, helper.getLevel(), second, InteractionHand.MAIN_HAND, hit);
        check(helper, helper.getEntities(EntityTypes.VILLAGER).size() == 1 && MobBottleItem.captured(player.getMainHandItem()) == EntityTypes.VILLAGER,
                "a second is not let out where one stands");

        ItemStack empty = new ItemStack(ModItems.MOB_BOTTLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        player.gameMode.useItemOn(player, helper.getLevel(), empty, InteractionHand.MAIN_HAND, hit);
        check(helper, helper.getEntities(EntityTypes.VILLAGER).size() == 1, "not at full health: the bottle's own rule");
        behind.setHealth(behind.getMaxHealth() / 2);
        player.gameMode.useItemOn(player, helper.getLevel(), empty, InteractionHand.MAIN_HAND, hit);
        check(helper, helper.getEntities(EntityTypes.VILLAGER).isEmpty(), "at half, an empty bottle takes it back");
        boolean held = false;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            held |= MobBottleItem.captured(player.getInventory().getItem(i)) == EntityTypes.VILLAGER;
        }
        check(helper, held, "into the player's hands");

        ItemStack pig = MobBottleItem.filled(EntityTypes.PIG);
        player.setItemInHand(InteractionHand.MAIN_HAND, pig);
        player.gameMode.useItemOn(player, helper.getLevel(), pig, InteractionHand.MAIN_HAND, hit);
        check(helper, helper.getEntities(EntityTypes.PIG).stream().noneMatch(p -> p.blockPosition().equals(helper.absolutePos(CELL))),
                "a pig is not let out behind it");
        helper.succeed();
    }

    /** As high as a fence to climb, so the villager behind it does not hop over; waist high to look at. */
    private static void fenceHigh(GameTestHelper helper) {
        counter(helper, COUNTER, UUID.randomUUID());
        BlockPos at = helper.absolutePos(COUNTER);
        BlockState state = helper.getLevel().getBlockState(at);
        check(helper, state.getCollisionShape(helper.getLevel(), at).max(Direction.Axis.Y) == 1.5, "a fence's height");
        helper.succeed();
    }

    /** Saved and loaded, it keeps its owner, its villager and the ticks for it, and its slots. */
    private static void saved(GameTestHelper helper) {
        UUID owner = UUID.randomUUID();
        TradingCounterBlockEntity counter = counter(helper, COUNTER, owner);
        Villager v = villager(helper, CELL, bread(4, 2), bread(4, 2));
        counter.toggle(1);
        turn(helper, counter);
        counter.setItem(0, new ItemStack(Items.EMERALD, 7));
        try (var reporter = new ProblemReporter.ScopedCollector(counter.problemPath(), LogUtils.getLogger())) {
            var output = TagValueOutput.createWithContext(reporter, helper.getLevel().registryAccess());
            counter.saveWithoutMetadata(output);
            var copy = new TradingCounterBlockEntity(counter.getBlockPos(), counter.getBlockState());
            copy.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), output.buildResult()));
            check(helper, owner.equals(copy.owner()) && copy.ticked().equals(List.of(1)), "owner and ticks: " + copy.ticked());
            check(helper, countIn(copy, Items.EMERALD) == 7 && copy.status() == counter.status(), "slots and status");
            // what any creative player could put in an item's block_entity_data: places no offer has, and one twice
            CompoundTag forged = new CompoundTag();
            forged.putIntArray("Ticked", new int[] {-1, 3, 1, 3, Integer.MAX_VALUE});
            ListTag what = new ListTag();
            for (int i = 0; i < 5; i++) {
                what.add(StringTag.valueOf("x"));
            }
            forged.put("TickedWhat", what);
            var bad = new TradingCounterBlockEntity(counter.getBlockPos(), counter.getBlockState());
            bad.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), forged));
            check(helper, bad.ticked().equals(List.of(1, 3)), "only places an offer can have, each once, in order: " + bad.ticked());
        }
        helper.setBlock(COUNTER, Blocks.AIR);
        TradingCounterBlockEntity placed = counter(helper, COUNTER, owner);
        try (var reporter = new ProblemReporter.ScopedCollector(placed.problemPath(), LogUtils.getLogger())) {
            CompoundTag forged = new CompoundTag();
            forged.putIntArray("Villager", UUIDUtil.uuidToIntArray(v.getUUID())); // the villager there: no fresh start
            forged.putIntArray("Ticked", new int[] {-1, 0});
            ListTag what = new ListTag();
            what.add(StringTag.valueOf("x"));
            what.add(StringTag.valueOf("x"));
            forged.put("TickedWhat", what);
            placed.loadWithComponents(TagValueInput.create(reporter, helper.getLevel().registryAccess(), forged));
        }
        placed.setOwner(owner);
        turn(helper, placed);
        check(helper, placed.ticked().isEmpty(), "and a tick for another trade is dropped at the first turn, without a stop");
        helper.succeed();
    }

    /**
     * A villager in the next cell, its shoulder over the line, is not the one it serves - it would wipe the ticks - nor
     * is one standing on top of the counter; one on a slab in the cell is.
     */
    private static void onlyInTheCell(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        BlockPos at = helper.absolutePos(COUNTER);
        Villager passing = villager(helper, CELL.east(), bread(4, 2));
        Vec3 shoulder = helper.absoluteVec(new Vec3(CELL.getX() + 1.2, 1, CELL.getZ() + 0.5));
        passing.snapTo(shoulder.x, shoulder.y, shoulder.z);
        check(helper, passing.getBoundingBox().intersects(new AABB(helper.absolutePos(CELL))), "its shoulder is over the line");
        check(helper, TradingCounterBlockEntity.behind(helper.getLevel(), at, counter.getBlockState()) == null, "but it is not behind the counter");
        passing.discard();
        helper.setBlock(CELL, Blocks.SMOOTH_STONE_SLAB);
        Villager raised = villager(helper, CELL, bread(4, 2));
        Vec3 onSlab = helper.absoluteVec(new Vec3(CELL.getX() + 0.5, 1.5, CELL.getZ() + 0.5));
        raised.snapTo(onSlab.x, onSlab.y, onSlab.z);
        check(helper, TradingCounterBlockEntity.behind(helper.getLevel(), at, counter.getBlockState()) == raised, "one on a slab in the cell is");
        helper.setBlock(CELL, Blocks.STONE);
        Vec3 onTop = helper.absoluteVec(new Vec3(CELL.getX() + 0.5, 2, CELL.getZ() + 0.5));
        raised.snapTo(onTop.x, onTop.y, onTop.z);
        check(helper, TradingCounterBlockEntity.behind(helper.getLevel(), at, counter.getBlockState()) == null,
                "one walking on a wall's top behind it is not");
        helper.succeed();
    }

    /** Broken, it drops its payment and goods, as a chest drops what it holds, and itself. */
    private static void brokenDrops(GameTestHelper helper) {
        TradingCounterBlockEntity counter = counter(helper, COUNTER, UUID.randomUUID());
        counter.setItem(0, new ItemStack(Items.EMERALD, 5));
        counter.setItem(TradingCounterBlockEntity.FIRST_GOODS, new ItemStack(Items.BREAD, 7));
        helper.getLevel().destroyBlock(helper.absolutePos(COUNTER), true);
        int emeralds = 0;
        int bread = 0;
        int counters = 0;
        for (var item : helper.getEntities(EntityTypes.ITEM)) {
            ItemStack stack = item.getItem();
            emeralds += stack.is(Items.EMERALD) ? stack.getCount() : 0;
            bread += stack.is(Items.BREAD) ? stack.getCount() : 0;
            counters += stack.is(ModTrading.TRADING_COUNTER_ITEM.get()) ? stack.getCount() : 0;
        }
        check(helper, emeralds == 5 && bread == 7 && counters == 1, "dropped: " + emeralds + " emeralds, " + bread + " bread, " + counters + " counter");
        helper.succeed();
    }

    private TradeTests() {}
}
