package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.logging.LogUtils;

import dev.nookworks.barter.BarterRoom;
import dev.nookworks.barter.BarterTerminalBlockEntity;
import dev.nookworks.machine.MachineStatus;
import dev.nookworks.mobcapture.MobBottleItem;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModTrading;
import dev.nookworks.spawner.ControlledSpawner;
import dev.nookworks.spawner.SpawnerControllerBlockEntity;
import dev.nookworks.spawner.SpawnerHold;
import dev.nookworks.spawner.SpawnerLinkReceiverBlockEntity;
import dev.nookworks.spawner.SpawnerLinkSenderBlockEntity;
import dev.nookworks.spawner.SpawnerSettings;
import dev.nookworks.trading.TradingCounterBlock;
import dev.nookworks.trading.TradingCounterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.entity.ComparatorBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Review round r7, mobs: a Spawner Link Sender beside a spawner on land claimed by someone else, a villager bottled
 * while another player trades with it, the Trading Counter's comparator and its room for a villager on a slab, carpet
 * or rail, and the Barter Terminal's bottles, comparator and air while it waits to see its room after a load.
 */
final class R7MobsTests {
    private R7MobsTests() {}

    static void register() {
        test("spawner_link_steers_no_spawner_on_land_its_owner_may_not_take", R7MobsTests::spawnerLinkAsksTheClaim);
        test("mob_bottle_ends_a_villagers_trade_before_it_is_bottled", R7MobsTests::bottleEndsTheTradeFirst);
        test("trading_counter_broken_tells_a_comparator_through_a_block", R7MobsTests::counterBrokenTellsTheComparator);
        test("trading_counter_lets_a_villager_out_onto_a_slab_rail_or_carpet", R7MobsTests::counterLetsOutOntoTheFloorPiece);
        test("barter_terminal_takes_no_piglin_a_pack_keeps_out_of_bottles", 200, R7MobsTests::terminalTakesNoUnbottleablePiglin);
        test("barter_terminal_tells_a_comparator_when_it_hands_gold", 200, R7MobsTests::terminalTellsTheComparator);
        test("barter_terminal_holds_its_air_on_power_while_it_waits_to_see_the_room", 200, R7MobsTests::waitingRoomKeepsItsAir);
    }

    /**
     * A sender set down on its owner's side of a claim's edge reached the spawner on the other side - someone else's
     * dungeon - and held it off or ran it as its owner liked. It steers a spawner only where its owner could take the
     * spawner away: on their own land it holds it off, on another's it reaches nothing, and the hold it set there is let go.
     */
    private static void spawnerLinkAsksTheClaim(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spawnerPos = new BlockPos(2, 1, 2);
        helper.setBlock(spawnerPos, Blocks.SPAWNER);
        BlockPos senderPos = spawnerPos.above();
        helper.setBlock(senderPos, ModBlocks.SPAWNER_LINK_SENDER.get());
        BlockPos senderAt = helper.absolutePos(senderPos);
        Player owner = helper.makeMockPlayer(GameType.SURVIVAL);
        ModBlocks.SPAWNER_LINK_SENDER.get().setPlacedBy(level, senderAt, level.getBlockState(senderAt), owner, ItemStack.EMPTY);
        long stamp = helper.getBlockEntity(senderPos, SpawnerLinkSenderBlockEntity.class).stamp();
        BlockPos receiverPos = new BlockPos(5, 1, 5);
        helper.setBlock(receiverPos, ModBlocks.SPAWNER_LINK_RECEIVER.get());
        helper.setBlock(receiverPos.above(), ModBlocks.SPAWNER_CONTROLLER.get());
        var receiver = helper.getBlockEntity(receiverPos, SpawnerLinkReceiverBlockEntity.class);
        var controller = helper.getBlockEntity(receiverPos.above(), SpawnerControllerBlockEntity.class);
        controller.setSettings(SpawnerSettings.DEFAULT.withEnabled(false)); // off: held at the spawner, and free
        GlobalPos link = GlobalPos.of(level.dimension(), senderAt);
        BlockPos spawnerAt = helper.absolutePos(spawnerPos);
        ControlledSpawner control = ControlledSpawner.of(helper.getBlockEntity(spawnerPos, SpawnerBlockEntity.class).getSpawner());

        BlockTests.underClaim(owner.getUUID(), () -> {
            receiver.setLink(link, stamp);
            controller.update(level);
        });
        check(helper, controller.linked() == 1 && control.nookworks$heldBy() != null,
                "on its owner's own land the sender reaches the spawner, and switched off holds it off");
        GlobalPos held = control.nookworks$heldBy();
        BlockTests.underClaim(UUID.randomUUID(), () -> {
            receiver.setLink(link, stamp); // paired again: the claims asked afresh
            controller.update(level);
            check(helper, controller.linked() == 0, "on land claimed by someone else it reaches no spawner: " + controller.linked());
            check(helper, !SpawnerHold.stillHeld(level, spawnerAt, held), "and the hold it set there is let go at the spawner's next look");
        });
        helper.succeed();
    }

    /**
     * A villager's offers carry the discount of whoever has its trade screen open (a cured villager's, the Hero of the
     * Village's) until the screen is closed. Bottled meanwhile by another player, the bottle kept it: let out, the
     * villager sold at that player's discount, on top of the next one's own. The trade is ended first.
     */
    private static void bottleEndsTheTradeFirst(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Villager villager = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(3, 1, 3));
        villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.ARMORER).withLevel(1));
        var offers = villager.getOffers();
        offers.clear();
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 10), Optional.empty(), new ItemStack(Items.BREAD, 3), 0, 4, 2, 0.05F, 0));
        // what opening its screen does (Villager.startTrading): the trader's discount on each offer, while they trade
        offers.get(0).addToSpecialPriceDiff(-5);
        villager.setTradingPlayer(helper.makeMockPlayer(GameType.SURVIVAL));
        villager.setHealth(villager.getMaxHealth() / 2);
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        other.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.MOB_BOTTLE.get()));
        MobBottleItem.tryCapture(other, InteractionHand.MAIN_HAND, villager);
        ItemStack bottle = other.getMainHandItem();
        check(helper, MobBottleItem.captured(bottle) == EntityTypes.VILLAGER && !villager.isAlive(), "the villager is bottled");
        Entity out = MobBottleItem.letOut(level, bottle, helper.absolutePos(new BlockPos(1, 1, 1)));
        check(helper, out instanceof Villager again && again.getOffers().get(0).getSpecialPriceDiff() == 0,
                "let out, its offers carry no one's discount: "
                        + (out instanceof Villager again ? again.getOffers().get(0).getSpecialPriceDiff() : out));
        helper.succeed();
    }

    /**
     * A comparator reading the counter through a solid block - a "goods filling up" line in a trading hall - kept its
     * signal when the counter was broken: nothing told it. It reads nothing once the counter is gone.
     */
    private static void counterBrokenTellsTheComparator(GameTestHelper helper) {
        BlockPos counterPos = new BlockPos(1, 1, 2);
        helper.setBlock(counterPos, ModTrading.TRADING_COUNTER.get().defaultBlockState().setValue(TradingCounterBlock.FACING, Direction.NORTH));
        helper.setBlock(counterPos.east(), Blocks.STONE);
        BlockPos comparatorPos = counterPos.east(2);
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        helper.getBlockEntity(counterPos, TradingCounterBlockEntity.class).setItem(0, new ItemStack(Items.EMERALD, 64));
        ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class);
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, comparator.getOutputSignal() > 0, "the comparator reads the counter through the stone"))
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(counterPos), true))
                .thenExecuteAfter(6, () -> check(helper, comparator.getOutputSignal() == 0,
                        "the counter broken, the comparator reads nothing: " + comparator.getOutputSignal()))
                .thenSucceed();
    }

    /**
     * Trading halls stand their villagers on a carpet, a bottom slab or the rail they came by, and the counter serves a
     * villager standing on one. Bottled out and clicked back, it said there was no room behind the counter: two blocks
     * of open air were asked for. It is let out standing on the floor piece; a whole block in the cell is still no room.
     */
    private static void counterLetsOutOntoTheFloorPiece(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        Villager template = helper.spawnWithNoFreeWill(EntityTypes.VILLAGER, new BlockPos(3, 1, 6));
        template.setVillagerData(template.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.ARMORER).withLevel(1));
        ItemStack bottle = MobBottleItem.filled(template);
        template.discard();
        record Floor(int x, BlockState piece, double height) {}
        Floor[] floors = {
            new Floor(0, Blocks.OAK_SLAB.defaultBlockState(), 0.5),
            new Floor(2, Blocks.RAIL.defaultBlockState(), 0.0),
            new Floor(4, Blocks.CARPET.pick(DyeColor.WHITE).defaultBlockState(), 1.0 / 16),
            new Floor(6, Blocks.STONE.defaultBlockState(), -1)};
        try {
            for (Floor floor : floors) {
                BlockPos counterPos = new BlockPos(floor.x(), 1, 2);
                BlockPos cell = counterPos.south();
                helper.setBlock(counterPos, ModTrading.TRADING_COUNTER.get().defaultBlockState().setValue(TradingCounterBlock.FACING, Direction.NORTH));
                helper.setBlock(cell, floor.piece());
                BlockPos at = helper.absolutePos(counterPos);
                ItemStack held = bottle.copy();
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.gameMode.useItemOn(player, level, held, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
                Villager behind = TradingCounterBlockEntity.behind(level, at, level.getBlockState(at));
                if (floor.height() < 0) {
                    check(helper, behind == null && MobBottleItem.captured(player.getMainHandItem()) == EntityTypes.VILLAGER,
                            "a whole block in the cell: no room, the villager stays in its bottle");
                    continue;
                }
                double y = helper.absolutePos(cell).getY() + floor.height();
                check(helper, behind != null && Math.abs(behind.getY() - y) < 1.0E-6,
                        "let out behind the counter onto " + floor.piece() + ", standing on it: " + (behind == null ? "none" : behind.getY()));
                check(helper, MobBottleItem.captured(player.getMainHandItem()) == null, "and the bottle emptied");
            }
        } finally {
            level.getServer().getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /**
     * A pack that puts piglins in #nookworks:not_bottleable (a modpack update, rooms already full) made the terminal's
     * empty bottle a trap: it took a piglin out into a bottle nothing could empty, and the piglin was lost. Such a piglin
     * is left in its room, and such a bottle is not taken in.
     */
    private static void terminalTakesNoUnbottleablePiglin(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fan = BarterTests.room(helper, 1, 1, 2);
        var terminal = BarterTests.terminal(helper, fan.east());
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            // the terminal looks at its bottle every ten ticks, its room whole and kept: that look, run here with the tag on
            if (done[0] || !terminal.room().whole() || !terminal.sheltering() || level.getGameTime() % 10 != 0) {
                return;
            }
            done[0] = true;
            boolean[] takesTheirBottle = {true};
            notBottleable(EntityTypes.PIGLIN, () -> {
                takesTheirBottle[0] = BarterTerminalBlockEntity.takesBottle(MobBottleItem.filled(EntityTypes.PIGLIN));
                terminal.setItem(BarterTerminalBlockEntity.BOTTLE, new ItemStack(ModItems.MOB_BOTTLE.get()));
                BarterTerminalBlockEntity.serverTick(level, terminal.getBlockPos(), terminal.getBlockState(), terminal);
            });
            ItemStack left = terminal.getItem(BarterTerminalBlockEntity.BOTTLE);
            terminal.setItem(BarterTerminalBlockEntity.BOTTLE, ItemStack.EMPTY); // not left for a look with the tag gone
            check(helper, !takesTheirBottle[0], "a bottle with such a piglin in it is not taken in");
            check(helper, piglin.isAlive() && left.is(ModItems.MOB_BOTTLE.get()) && MobBottleItem.captured(left) == null,
                    "and an empty bottle takes no such piglin out of the room");
            check(helper, BarterTerminalBlockEntity.takesBottle(MobBottleItem.filled(EntityTypes.PIGLIN)),
                    "bottleable again, a piglin's bottle goes in as ever");
            helper.succeed();
        });
    }

    /**
     * The terminal handed its gold, drew in what was thrown and passed it below without telling a comparator: a "gold
     * running low" line went on reading the old count until the terminal's status changed. Gold handed over, the
     * comparator reads the empty slot within a few ticks - checked while the piglin still admires it, status unchanged.
     */
    private static void terminalTellsTheComparator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fan = BarterTests.room(helper, 1, 1, 2);
        BlockPos at = fan.east();
        var terminal = BarterTests.terminal(helper, at);
        Piglin piglin = helper.spawn(EntityTypes.PIGLIN, new BlockPos(1, 2, 1));
        piglin.setBaby(false);
        piglin.setPersistenceRequired();
        BlockPos comparatorPos = at.east();
        helper.setBlock(comparatorPos.below(), Blocks.STONE);
        helper.setBlock(comparatorPos, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.WEST));
        ComparatorBlockEntity comparator = helper.getBlockEntity(comparatorPos, ComparatorBlockEntity.class);
        boolean[] put = {false};
        long[] handed = {-1};
        helper.onEachTick(() -> {
            long time = level.getGameTime();
            if (!put[0]) {
                // put in clear of a hand-over tick (every fifth), so the gold goes in the tick after the status turns to running
                if (terminal.room().whole() && terminal.guiStatus() == MachineStatus.IDLE && time % 5 == 2) {
                    terminal.setItem(BarterTerminalBlockEntity.GOLD, new ItemStack(Items.GOLD_INGOT));
                    put[0] = true;
                }
            } else if (handed[0] < 0) {
                if (terminal.getItem(BarterTerminalBlockEntity.GOLD).isEmpty()) {
                    handed[0] = time;
                }
            } else if (time - handed[0] == 6) {
                int signal = AbstractContainerMenu.getRedstoneSignalFromBlockEntity(terminal);
                check(helper, terminal.guiStatus() == MachineStatus.RUNNING, "the piglin still admires its gold: " + terminal.guiStatus());
                check(helper, comparator.getOutputSignal() == signal,
                        "the gold handed over, the comparator reads " + signal + ", not " + comparator.getOutputSignal());
                helper.succeed();
            }
        });
    }

    /**
     * After a load the terminal waits to see its room whole before making air for it, and a room whose far side stayed
     * unloaded (a forceloaded room, the terminal a chunk off) was never seen: its air ran out meanwhile, the room was
     * forgotten and its piglins let go to turn. Waiting, the air is held on power, neither made nor run down; off power
     * it runs down as ever.
     */
    private static void waitingRoomKeepsItsAir(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fan = BarterTests.room(helper, 1, 1, 2);
        var terminal = BarterTests.terminal(helper, fan.east());
        BlockPos pos = terminal.getBlockPos();
        boolean[] done = {false};
        helper.onEachTick(() -> {
            // a tick it does not look at its room in: what it does while it cannot look, as with a chunk not loaded
            if (done[0] || !terminal.room().whole() || terminal.air() <= 0 || level.getGameTime() % 20 == Math.floorMod(pos.hashCode(), 20)) {
                return;
            }
            done[0] = true;
            try (var reporter = new ProblemReporter.ScopedCollector(terminal.problemPath(), LogUtils.getLogger())) {
                var output = TagValueOutput.createWithContext(reporter, level.registryAccess());
                terminal.saveWithoutMetadata(output);
                var loaded = new BarterTerminalBlockEntity(pos, terminal.getBlockState());
                loaded.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), output.buildResult()));
                int air = loaded.air();
                check(helper, loaded.room().fault() == BarterRoom.Fault.UNSEEN && air > 0, "loaded, the room is waiting to be seen: " + loaded.room());
                for (int i = 0; i < 5; i++) {
                    BarterTerminalBlockEntity.serverTick(level, pos, terminal.getBlockState(), loaded);
                }
                check(helper, loaded.air() == air, "on power its air is held while it waits: " + air + " -> " + loaded.air());
                loaded.energy().set(0);
                BarterTerminalBlockEntity.serverTick(level, pos, terminal.getBlockState(), loaded);
                check(helper, loaded.air() == air - 1, "off power it runs down: " + air + " -> " + loaded.air());
            }
            helper.succeed();
        });
    }

    /**
     * Runs {@code body} as though a pack had put {@code type} in #nookworks:not_bottleable, put back as it was after.
     * Tags are bound when data loads; this binds the one for the length of {@code body}, run within one tick, so no other
     * test sees it.
     */
    @SuppressWarnings("unchecked")
    private static void notBottleable(EntityType<?> type, Runnable body) {
        Holder.Reference<EntityType<?>> holder = type.builtInRegistryHolder();
        try {
            Field tags = Holder.Reference.class.getDeclaredField("tags");
            tags.setAccessible(true);
            Object was = tags.get(holder);
            Set<TagKey<EntityType<?>>> with = new HashSet<>((Set<TagKey<EntityType<?>>>) was);
            with.add(MobBottleItem.NOT_BOTTLEABLE);
            tags.set(holder, Set.copyOf(with));
            try {
                body.run();
            } finally {
                tags.set(holder, was);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not tag " + type + " for the test", e);
        }
    }
}
