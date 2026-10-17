package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.UUID;

import dev.nookworks.registry.ModTrading;
import dev.nookworks.trading.TradingCounterBlock;
import dev.nookworks.trading.TradingCounterBlockEntity;
import dev.nookworks.trading.TradingCounterBlockEntity.Reroll;
import dev.nookworks.trading.TradingCounterBlockEntity.Status;
import dev.nookworks.trading.TradingCounterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Round r8: the Trading Counter's re-roll button (the user, 2026-10-06: "司書ガチャできる機能が欲しいな ... まだ取引したことの
 * ない村人限定で"). A villager never traded with draws its first trades again, as on taking its job afresh, and the
 * counter's ticks for it go; once each half second at most. One that has traded keeps its trades, as do one with no
 * job, one asleep or trading with a player, and the cell with no one in it; and a player the land is not open to (a
 * claim mod's no, or adventure mode) changes nothing.
 */
final class R8CounterRerollTests {
    private R8CounterRerollTests() {}

    /** The counter faces north, so its villager stands south of it, at {@link #CELL}. */
    private static final BlockPos COUNTER = new BlockPos(3, 1, 2);
    private static final BlockPos CELL = new BlockPos(3, 1, 3);

    static void register() {
        test("trading_counter_rerolls_a_never_traded_librarians_trades", 300, R8CounterRerollTests::rerollsALibrarian);
        test("trading_counter_rerolls_no_villager_that_has_traded", R8CounterRerollTests::keepsATradedVillagersTrades);
        test("trading_counter_rerolls_nothing_for_a_player_the_land_is_not_open_to", R8CounterRerollTests::refusesAStranger);
    }

    // ---- setting up ----

    private static TradingCounterBlockEntity counter(GameTestHelper helper, UUID owner) {
        helper.setBlock(COUNTER, ModTrading.TRADING_COUNTER.get().defaultBlockState().setValue(TradingCounterBlock.FACING, Direction.NORTH));
        TradingCounterBlockEntity counter = helper.getBlockEntity(COUNTER, TradingCounterBlockEntity.class);
        counter.setOwner(owner);
        return counter;
    }

    /** A librarian new to its job behind the counter, with no will of its own (it stays put): its first trades vanilla's. */
    private static Villager librarian(GameTestHelper helper) {
        Villager v = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, CELL);
        v.setVillagerData(v.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.LIBRARIAN).withLevel(1));
        return v;
    }

    /** A player in {@code mode} standing in front of the counter. */
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
        Vec3 front = helper.absoluteVec(new Vec3(3.5, 1, 0.5));
        player.snapTo(front.x, front.y, front.z);
        return player;
    }

    /** The re-roll button, clicked by {@code player} in the counter's screen. */
    private static void click(TradingCounterBlockEntity counter, ServerPlayer player) {
        new TradingCounterMenu(0, player.getInventory(), counter, counter).clickMenuButton(player, TradingCounterMenu.BUTTON_REROLL);
    }

    /**
     * {@code offers} marked as sold from, so a list drawn afresh (none sold) is told from the same one kept. Only the
     * uses: the villager's experience, which would make it a villager that has traded, is left as it is.
     */
    private static MerchantOffers marked(MerchantOffers offers) {
        offers.forEach(MerchantOffer::increaseUses);
        return offers;
    }

    private static boolean fresh(MerchantOffers offers) {
        return !offers.isEmpty() && offers.stream().allMatch(o -> o.getUses() == 0);
    }

    /** What the offers are - what each asks and gives, components and all - to tell one draw from another. */
    private static String print(MerchantOffers offers) {
        StringBuilder out = new StringBuilder();
        for (MerchantOffer o : offers) {
            out.append(ItemStack.hashItemAndComponents(o.getBaseCostA())).append('x').append(o.getBaseCostA().getCount()).append('+')
                    .append(ItemStack.hashItemAndComponents(o.getCostB())).append('x').append(o.getCostB().getCount()).append('>')
                    .append(ItemStack.hashItemAndComponents(o.getResult())).append('x').append(o.getResult().getCount()).append(';');
        }
        return out.toString();
    }

    // ---- the tests ----

    /**
     * A librarian never traded with: clicked, it draws its first trades again - none sold from, the counter's tick for
     * the old list gone, the screen's view showing the new one at once. A second click in the same half second is the
     * same click; after it, another goes through. And clicked on, its trades come out otherwise in time (the book rolls
     * again): the draw is vanilla's, not the old list put back.
     */
    private static void rerollsALibrarian(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        TradingCounterBlockEntity counter = counter(helper, player.getUUID());
        Villager v = librarian(helper);
        MerchantOffers first = v.getOffers();
        check(helper, first.size() == 2, "a librarian new to its job has two trades: " + first.size());
        String firstPrint = print(first);
        check(helper, counter.view().rerollOf() == Reroll.OK, "the screen offers the re-roll: " + counter.view().rerollOf());
        counter.toggle(0);
        check(helper, counter.ticked().equals(List.of(0)), "a trade ticked: " + counter.ticked());
        marked(first);

        click(counter, player);
        MerchantOffers second = v.getOffers();
        check(helper, fresh(second) && second.size() == 2, "its trades drawn again, as on taking its job: " + second.size());
        check(helper, counter.ticked().isEmpty() && counter.status() == Status.NOTHING_TICKED,
                "the tick for the old list gone: " + counter.ticked() + " " + counter.status());
        check(helper, print(counter.view().offers()).equals(print(second)), "the screen shows the new list");
        check(helper, v.getVillagerXp() == 0 && v.getVillagerData().level() == 1, "still a villager never traded with");

        marked(second);
        click(counter, player);
        check(helper, v.getOffers() == second && !fresh(v.getOffers()), "a second click in the same half second is the same click");

        helper.runAfterDelay(TradingCounterBlockEntity.REROLL_PAUSE, () -> {
            click(counter, player);
            check(helper, fresh(v.getOffers()), "half a second on, another click re-rolls");
            // clicked on, the trades come out otherwise: at most a run of draws alike, by chance
            helper.succeedWhen(() -> {
                click(counter, player);
                check(helper, !print(v.getOffers()).equals(firstPrint), "re-rolled again and again, the same trades every time");
            });
        });
    }

    /**
     * Vanilla's own test for a villager settled in its job: with experience, or past the first level, its trades stay.
     * Nor one with no job, one asleep or trading with a player, or an empty cell - and the screen says which.
     */
    private static void keepsATradedVillagersTrades(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.SURVIVAL);
        TradingCounterBlockEntity counter = counter(helper, player.getUUID());
        check(helper, counter.view().rerollOf() == Reroll.NO_VILLAGER, "no villager: " + counter.view().rerollOf());
        click(counter, player); // nothing to do, and nothing goes wrong

        Villager v = librarian(helper);
        MerchantOffers offers = marked(v.getOffers());
        v.setVillagerXp(1);
        check(helper, counter.view().rerollOf() == Reroll.TRADED, "traded once: " + counter.view().rerollOf());
        click(counter, player);
        check(helper, v.getOffers() == offers && !fresh(offers), "a villager with experience keeps its trades");

        v.setVillagerXp(0);
        v.setVillagerData(v.getVillagerData().withLevel(2));
        check(helper, counter.view().rerollOf() == Reroll.TRADED, "past the first level: " + counter.view().rerollOf());
        click(counter, player);
        check(helper, v.getOffers() == offers, "a villager past the first level keeps its trades");

        v.setVillagerData(v.getVillagerData().withLevel(1));
        v.setTradingPlayer(player(helper, GameType.SURVIVAL));
        check(helper, counter.view().rerollOf() == Reroll.BUSY, "a player trading with it: " + counter.view().rerollOf());
        click(counter, player);
        check(helper, v.getOffers() == offers, "not while a player trades with it: their screen shows the list");
        v.setTradingPlayer(null);

        v.setVillagerData(v.getVillagerData().withProfession(helper.getLevel().registryAccess(), VillagerProfession.NITWIT));
        check(helper, counter.view().rerollOf() == Reroll.NO_PROFESSION, "a nitwit: " + counter.view().rerollOf());
        click(counter, player);
        check(helper, v.getVillagerData().profession().is(VillagerProfession.NITWIT) && v.getOffers().isEmpty(),
                "a nitwit stays one, with nothing to trade");
        helper.succeed();
    }

    /**
     * On land claimed by the counter's owner, a stranger who can open its screen re-rolls nothing - asked as the counter's
     * Mob Bottle asks, as placing a block where the villager stands; the owner does. Nor does a player in adventure mode,
     * who could not break the workstation either.
     */
    private static void refusesAStranger(GameTestHelper helper) {
        ServerPlayer owner = player(helper, GameType.SURVIVAL);
        ServerPlayer stranger = player(helper, GameType.SURVIVAL);
        TradingCounterBlockEntity counter = counter(helper, owner.getUUID());
        Villager v = librarian(helper);
        MerchantOffers offers = marked(v.getOffers());
        counter.toggle(0);
        BlockTests.underClaim(owner.getUUID(), () -> {
            click(counter, stranger);
            check(helper, v.getOffers() == offers && !fresh(offers) && counter.ticked().equals(List.of(0)),
                    "a stranger to the claim changes nothing, ticks and all");
            click(counter, owner);
            check(helper, fresh(v.getOffers()) && counter.ticked().isEmpty(), "its owner re-rolls");
        });
        ServerPlayer adventurer = player(helper, GameType.ADVENTURE);
        helper.runAfterDelay(TradingCounterBlockEntity.REROLL_PAUSE, () -> {
            MerchantOffers drawn = marked(v.getOffers());
            click(counter, adventurer);
            check(helper, v.getOffers() == drawn, "a player in adventure mode re-rolls nothing");
            click(counter, owner);
            check(helper, fresh(v.getOffers()), "with no claim asked, its owner still re-rolls");
            helper.succeed();
        });
    }
}
