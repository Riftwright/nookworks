package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import dev.nookworks.feature.Features;
import dev.nookworks.ifworld.IfGates;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfMirrors;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.pearl.ThrownLodePearl;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonDoors;
import dev.nookworks.travel.DaemonPanelMenu;
import dev.nookworks.travel.DaemonTravel;
import dev.nookworks.travel.TravelLog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerSynchronizer;
import net.minecraft.world.inventory.RemoteSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * The r7 review's findings on the Dock-a-Daemon Door, the If, the travel log and the Lode Pearl, each fixed and kept so.
 * Those that touch where the doors stand or the If (the whole server's) run alone: their names start {@code daemon_door_}
 * or {@code if_}.
 */
final class R7TravelIfTests {
    static void register() {
        test("daemon_door_comes_up_with_an_axe_or_by_hand", R7TravelIfTests::doorComesUpByHand);
        test("mobs_do_not_break_the_daemon_door", R7TravelIfTests::mobsDoNotBreakTheDoor);
        test("daemon_door_refused_trip_leaves_the_rider_mounted", 200, R7TravelIfTests::refusedTripLeavesTheRider);
        // r15 (two players): last seen on the End's platform, a return door went down on it, and the next arrival broke it
        test("daemon_door_end_return_never_on_the_platform", 200, R7TravelIfTests::endDoorOffThePlatform);
        // r15 (two players): an operator's /tp of someone else into the If was refused, though /tp said it was done
        test("if_operator_tp_takes_another_player_in", 200, R7TravelIfTests::operatorTpTakesAnotherIn);
        // r15 (two players): a bed in an If waiting for its reset took a respawning player in
        test("if_bed_takes_nobody_in_while_the_if_waits_for_its_reset", R7TravelIfTests::bedWhileResetPending);
        test("daemon_door_refused_second_door_is_sent_back_to_the_hand", R7TravelIfTests::refusedSecondDoorSentBack);
        test("if_mirror_refused_second_mirror_is_sent_back_to_the_hand", R7TravelIfTests::refusedSecondMirrorSentBack);
        test("if_sleepers_count_with_the_overworlds_players", 400, R7TravelIfTests::ifSleepersCountWithTheOverworld);
        test("if_reset_while_away_is_forgotten_on_coming_back", R7TravelIfTests::resetWhileAwayForgotten);
        test("if_keeps_the_monster_spawning_rules", R7TravelIfTests::ifKeepsSpawnRules);
        test("if_forceloaded_chunks_stay_loaded_after_a_restart", R7TravelIfTests::ifForceloadedChunksComeBack);
        test("lode_pearl_is_not_bound_in_adventure_mode", R7TravelIfTests::pearlNotBoundInAdventure);
        test("lode_pearl_binding_hears_claim_mods_first", R7TravelIfTests::pearlBindingHearsClaimsFirst);
        test("lode_pearl_drops_where_it_landed_when_its_thrower_left", R7TravelIfTests::pearlDropsWhenItsThrowerLeft);
        test("travel_log_and_panel_carry_any_number_of_places", R7TravelIfTests::anyNumberOfPlaces);
    }

    private R7TravelIfTests() {}

    // ---- the Dock-a-Daemon Door ----

    /**
     * Oiled Dawnwood, taken up with an axe or by hand: broken from its top by hand in survival, it drops. It wanted a
     * pickaxe (from when it was a steel hatch), and a wooden door taken up otherwise was lost, zinc, pearls and all.
     */
    private static void doorComesUpByHand(GameTestHelper helper) {
        BlockState lower = ModTravel.DAEMON_DOOR.get().defaultBlockState();
        check(helper, lower.is(BlockTags.MINEABLE_WITH_AXE) && !lower.requiresCorrectToolForDrops(), "an axe's block, and a hand gets it too");
        check(helper, new ItemStack(Items.IRON_AXE).getDestroySpeed(lower) > 1, "an axe is quicker than a hand");
        BlockPos at = new BlockPos(2, 1, 2);
        helper.setBlock(at, lower);
        helper.setBlock(at.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        ServerPlayer survivor = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        survivor.gameMode.destroyBlock(helper.absolutePos(at.above()));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(at).isAir() && helper.getBlockState(at.above()).isAir(), "both halves go");
            int dropped = helper.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(ModTravel.DAEMON_DOOR_ITEM.get()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            check(helper, dropped == 1, "and the door drops, once, to a bare hand: " + dropped);
        });
    }

    /**
     * A zombie at the door on Hard, a vindicator in a raid: to them it was a wooden door, and broken it took the home
     * door's record with it. No mob may break it; an oak door stays theirs to break, as in vanilla.
     */
    private static void mobsDoNotBreakTheDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos at = helper.absolutePos(new BlockPos(2, 1, 2));
        Zombie zombie = new Zombie(level);
        BlockState door = ModTravel.DAEMON_DOOR.get().defaultBlockState();
        check(helper, !door.canEntityDestroy(level, at, zombie), "a zombie does not break its lower half");
        check(helper, !door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER).canEntityDestroy(level, at.above(), zombie), "nor its upper");
        check(helper, Blocks.OAK_DOOR.defaultBlockState().canEntityDestroy(level, at, zombie), "an oak door it may, as in vanilla");
        helper.succeed();
    }

    /**
     * A trip another mod refuses (a dimension lock) says so and leaves the player as they were - in the cart they rode in,
     * not set down beside it; the next trip, not refused, goes, and takes them off it.
     */
    private static void refusedTripLeavesTheRider(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = MockPlayers.inLevel(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        var cart = helper.spawn(EntityTypes.MINECART, new BlockPos(4, 1, 4));
        player.startRiding(cart, true, false);
        check(helper, player.getVehicle() == cart, "riding a cart");
        Consumer<EntityTravelToDimensionEvent> lock = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(EntityTravelToDimensionEvent.class, lock);
        try {
            DaemonTravel.through(player, home);
        } finally {
            NeoForge.EVENT_BUS.unregister(lock);
        }
        check(helper, player.level() == level, "refused, nobody goes: " + player.level().dimension());
        check(helper, player.getVehicle() == cart, "and the rider is still in the cart: " + player.getVehicle());
        helper.runAfterDelay(45, () -> { // past the door's pause after a try
            DaemonTravel.through(player, home);
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            check(helper, player.level() == nether, "not refused, through: " + player.level().dimension());
            check(helper, player.getVehicle() == null, "and off the cart");
            BlockPos there = DaemonDoors.get(level.getServer()).doorIn(Level.NETHER);
            if (there != null) {
                nether.removeBlock(there.above(), false);
                nether.removeBlock(there, false);
            }
            cart.discard();
            forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /**
     * A second door set down where one stands is refused on the server, but the client, which cannot know, has set it down
     * and used it up: its inventory is sent again, or the door was gone from its hand until something else resent it.
     */
    private static void refusedSecondDoorSentBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        check(helper, DaemonDoors.standsAt(level, helper.absolutePos(new BlockPos(1, 2, 1))), "the first door stands");
        int[] resent = recordResends(player);
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        ItemStack second = new ItemStack(ModTravel.DAEMON_DOOR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        BlockPos floor = helper.absolutePos(new BlockPos(4, 1, 4));
        second.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        check(helper, helper.getBlockState(new BlockPos(4, 2, 4)).isAir(), "the second is refused");
        check(helper, !player.getMainHandItem().isEmpty(), "and kept on the server");
        check(helper, resent[0] > 0, "and the client is sent its inventory again");
        forgetAllDoors(level);
        helper.succeed();
    }

    /** The same for a second home mirror. */
    private static void refusedSecondMirrorSentBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        IfWorld world = IfWorld.get(level.getServer());
        GlobalPos oldHome = world.homeMirror();
        if (oldHome != null) {
            world.forget(oldHome.dimension(), oldHome.pos());
        }
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BlockPos home = setMirrorDown(helper, player, new BlockPos(1, 1, 1));
        check(helper, IfMirrors.standsAt(level, home) && IfMirrors.isHome(level, home), "the home mirror stands");
        int[] resent = recordResends(player);
        BlockPos second = setMirrorDown(helper, player, new BlockPos(4, 1, 4));
        check(helper, !IfMirrors.standsAt(level, second), "a second home mirror is refused");
        check(helper, !player.getMainHandItem().isEmpty(), "and kept on the server");
        check(helper, resent[0] > 0, "and the client is sent its inventory again");
        level.removeBlock(home.above(), false);
        level.removeBlock(home, false);
        helper.succeed();
    }

    // ---- the If ----

    /**
     * The If's nights are the overworld's, and so is the vote to sleep through one: one player asleep in the If while
     * another is awake at home (all of them needed, as by default) stays asleep, the night going on; with both asleep the
     * night passes and both wake. By its own vote one sleeper in the If skipped the night for the whole server.
     */
    private static void ifSleepersCountWithTheOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerLevel ifLevel = rolled(server);
        var rules = server.overworld().getGameRules();
        int percentWas = rules.get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
        AfterTest.undo(helper, () -> rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, percentWas, server));
        rules.set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 100, server);
        level.dimensionTypeRegistration().value().defaultClock().ifPresent(clock -> {
            long was = server.clockManager().getInstance(clock).totalTicks();
            AfterTest.undo(helper, () -> server.clockManager().setTotalTicks(clock, was));
            server.clockManager().setTotalTicks(clock, 18_000); // midnight
        });
        ServerPlayer awake = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        awake.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        ServerPlayer sleeper = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        BlockPos ifBed = new BlockPos(8, ifLevel.getMaxY() - 8, 8);
        for (BlockPos floor : List.of(ifBed.below(), ifBed.south().below(), ifBed.east().below())) {
            ifLevel.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        }
        setBed(ifLevel, ifBed);
        IfGates.passing(() -> sleeper.teleport(new TeleportTransition(ifLevel, Vec3.atBottomCenterOf(ifBed.east()), Vec3.ZERO, 0, 0,
                TeleportTransition.DO_NOTHING)));
        check(helper, sleeper.level() == ifLevel, "one player in the If");
        // a player is ticked by their connection, which a test's player has none of: a sleeper's is ticked here, or they
        // would never sleep long enough to count
        helper.onEachTick(() -> {
            for (ServerPlayer player : List.of(sleeper, awake)) {
                if (player.isSleeping()) {
                    player.doTick();
                }
            }
        });
        // lain down a few ticks after the clock is set: the If works out how light its sky is in its own tick, after the
        // overworld's, and a sleeper ticked before that saw the day it was before midnight was set and woke at once
        // (now and then, by the time the batch before left it)
        helper.runAfterDelay(5, () -> {
            sleeper.startSleeping(ifBed);
            ifLevel.updateSleepingPlayerList();
            helper.runAfterDelay(120, () -> {
                check(helper, sleeper.isSleeping(), "asleep in the If, with the overworld's player awake: no night skipped, still asleep");
                BlockPos bed = helper.absolutePos(new BlockPos(3, 1, 3));
                setBed(level, bed);
                awake.startSleeping(bed);
                level.updateSleepingPlayerList();
                helper.runAfterDelay(120, () -> {
                    check(helper, !sleeper.isSleeping() && !awake.isSleeping(),
                            "both asleep, the night passes and both wake: " + sleeper.isSleeping() + " / " + awake.isSleeping());
                    ifLevel.removeBlock(ifBed, false);
                    ifLevel.removeBlock(ifBed.south(), false);
                    helper.succeed();
                });
            });
        });
    }

    /**
     * Logged out in an If that was reset while they were away, and a new If rolled since: set down in the new If by its
     * name as they come back, then sent to their respawn point - the new If is not counted as been to, nor their place
     * in the old one kept as where they last were in it (where its first mirror or door would have gone, in a cave).
     */
    private static void resetWhileAwayForgotten(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        int generation = IfWorld.get(server).generation();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 in = Vec3.atBottomCenterOf(new BlockPos(0, ifLevel.getMaxY() - 8, 0));
        IfGates.passing(() -> player.teleport(new TeleportTransition(ifLevel, in, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)));
        check(helper, player.level() == ifLevel, "set down in the If by its name");
        // what they bring back from the If before: having been there, where they were in it, and which If it was
        player.setData(TravelLog.TYPE, new TravelLog(Set.of(Level.OVERWORLD, IfWorld.KEY), Map.of(IfWorld.KEY, new BlockPos(16, -30, 16)),
                Optional.empty(), generation - 1));
        player.getPersistentData().putInt("nookworks:left_in_if", generation - 1);
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(player));
        helper.runAfterDelay(10, () -> {
            check(helper, player.level().dimension().equals(Level.OVERWORLD), "sent to their respawn point: " + player.level().dimension());
            TravelLog log = TravelLog.of(player);
            check(helper, !log.hasBeenTo(IfWorld.KEY), "the new If is not one they have been to: " + log);
            check(helper, !log.lastPos().containsKey(IfWorld.KEY), "nor is their place in the old one kept: " + log);
            helper.succeed();
        });
    }

    /**
     * The If spawns monsters as the rules say, as every other level does (the game tests' server spawns none): made after
     * the server set each level's spawning at its start, it spawned them with spawn_monsters off, phantoms and patrols too.
     */
    private static void ifKeepsSpawnRules(GameTestHelper helper) {
        ServerLevel ifLevel = rolled(helper.getLevel().getServer());
        boolean enemies;
        try {
            Field field = ServerChunkCache.class.getDeclaredField("spawnEnemies");
            field.setAccessible(true);
            enemies = field.getBoolean(ifLevel.getChunkSource());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        check(helper, enemies == ifLevel.isSpawningMonsters(),
                "monsters spawn in the If as the rules say (" + ifLevel.isSpawningMonsters() + "): " + enemies);
        helper.succeed();
    }

    /**
     * Chunks kept loaded in the If (/forceload, another mod's chunk loader) are set aside as the server stops, and kept
     * loaded again as the If is made at the next start - every other level has it done by the server as it starts, and
     * in the If they stopped for good at every restart.
     */
    private static void ifForceloadedChunksComeBack(GameTestHelper helper) {
        ServerLevel ifLevel = rolled(helper.getLevel().getServer());
        ChunkPos chunk = new ChunkPos(2, 2);
        TicketStorage tickets = ifLevel.getDataStorage().get(TicketStorage.TYPE);
        check(helper, tickets != null, "the If keeps its chunks' tickets");
        ifLevel.setChunkForced(chunk.x(), chunk.z(), true);
        check(helper, ifLevel.getForceLoadedChunks().contains(chunk.pack()), "a chunk force-loaded");
        tickets.deactivateTicketsOnClosing(); // as the server stops: so saved, and so loaded at the next start
        check(helper, !ifLevel.getForceLoadedChunks().contains(chunk.pack()), "set aside at the stop");
        IfLevel.prepare(ifLevel); // as the If is made at the next start
        check(helper, ifLevel.getForceLoadedChunks().contains(chunk.pack()), "kept loaded again after the restart");
        ifLevel.setChunkForced(chunk.x(), chunk.z(), false);
        helper.succeed();
    }

    // ---- the Lode Pearl ----

    /** In adventure mode a pearl is not bound to a lodestone, as a compass is not (it was, a click on any lodestone). */
    private static void pearlNotBoundInAdventure(GameTestHelper helper) {
        if (!Features.enabled(ModItems.LODE_PEARL.get())) {
            helper.succeed(); // the part switched off: nothing to try
            return;
        }
        BlockPos lodestone = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.LODESTONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.ADVENTURE);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
        player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(lodestone), Direction.UP, lodestone, false));
        check(helper, player.getMainHandItem().is(Items.ENDER_PEARL), "in adventure mode, still an ender pearl: " + player.getMainHandItem());
        check(helper, !holdsLodePearl(player), "and no Lode Pearl made");
        helper.succeed();
    }

    /**
     * A claim mod at the usual priority, heard after Nookworks among equals, refuses the click on a lodestone: no pearl is
     * bound. The binding waits at low priority, as the Wool Shears do; it was done before the claim mod was heard.
     */
    private static void pearlBindingHearsClaimsFirst(GameTestHelper helper) {
        if (!Features.enabled(ModItems.LODE_PEARL.get())) {
            helper.succeed(); // the part switched off: nothing to try
            return;
        }
        BlockPos lodestone = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.LODESTONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_PEARL));
        Consumer<PlayerInteractEvent.RightClickBlock> claim = event -> {
            if (event.getEntity() == player) {
                event.setCanceled(true);
            }
        };
        NeoForge.EVENT_BUS.addListener(PlayerInteractEvent.RightClickBlock.class, claim);
        try {
            player.gameMode.useItemOn(player, helper.getLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(lodestone), Direction.UP, lodestone, false));
        } finally {
            NeoForge.EVENT_BUS.unregister(claim);
        }
        check(helper, player.getMainHandItem().is(Items.ENDER_PEARL) && !holdsLodePearl(player),
                "the claim refused the click: nothing bound, " + player.getMainHandItem());
        helper.succeed();
    }

    /**
     * Its thrower gone from the game while it flew (someone else keeping the chunk going), a Lode Pearl drops where it
     * landed, still bound. It was lost, and no trip made.
     */
    private static void pearlDropsWhenItsThrowerLeft(GameTestHelper helper) {
        if (!Features.enabled(ModItems.LODE_PEARL.get())) {
            helper.succeed(); // the part switched off: nothing to try
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer thrower = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        GlobalPos lodestone = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(5, 1, 5)));
        ThrownLodePearl pearl = new ThrownLodePearl(level, thrower, LodePearlItem.bound(lodestone));
        pearl.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 4, 2))));
        pearl.setDeltaMovement(0, -0.5, 0);
        level.addFreshEntity(pearl);
        level.getServer().getPlayerList().remove(thrower); // gone while it flies
        helper.succeedWhen(() -> {
            check(helper, pearl.isRemoved(), "the pearl has landed");
            var dropped = helper.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(ModItems.LODE_PEARL.get())).toList();
            check(helper, dropped.size() == 1 && LodePearlItem.lodestone(dropped.get(0).getItem()).equals(Optional.of(lodestone)),
                    "it lies where it landed, still bound: " + dropped);
        });
    }

    // ---- many places ----

    /**
     * A player who has been to more places than a packet of them holds (a pack making dimensions by the hundred): their
     * travel log is still sent, its destination whole, and the panel lists the first of them, its screen sent the same
     * places. Past the caps neither went at all, and the player was disconnected.
     */
    private static void anyNumberOfPlaces(GameTestHelper helper) {
        List<ResourceKey<Level>> places = new ArrayList<>();
        Map<ResourceKey<Level>, BlockPos> last = new HashMap<>();
        for (int i = 0; i < 1500; i++) {
            ResourceKey<Level> place = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("pockets", "pocket_" + i));
            places.add(place);
            last.put(place, BlockPos.ZERO);
        }
        TravelLog log = new TravelLog(new LinkedHashSet<>(places), last, Optional.of(Level.NETHER), 0);
        ByteBuf sent = Unpooled.buffer();
        TravelLog.STREAM_CODEC.encode(sent, log);
        TravelLog received = TravelLog.STREAM_CODEC.decode(sent);
        check(helper, received.destination().equals(Optional.of(Level.NETHER)), "the destination gets through: " + received.destination());
        check(helper, log.visited().size() == 1500 && log.lastPos().size() == 1500, "and the server keeps every place");
        DaemonPanelMenu menu = new DaemonPanelMenu(0, places, null);
        check(helper, menu.choices().equals(places.subList(0, DaemonPanelMenu.MOST)), "the panel lists the first " + DaemonPanelMenu.MOST
                + ": " + menu.choices().size());
        RegistryFriendlyByteBuf data = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        DaemonPanelMenu.write(data, places);
        ServerPlayer player = MockPlayers.inLevel(helper);
        DaemonPanelMenu screen = DaemonPanelMenu.client(1, player.getInventory(), data);
        check(helper, screen.choices().equals(menu.choices()), "and its screen is sent the same: " + screen.choices().size());
        helper.succeed();
    }

    /**
     * A player whose last place in the End was on its platform goes through their door: the return door goes down off
     * the platform (and a block clear of it, frame and all), which an end portal makes afresh.
     */
    private static void endDoorOffThePlatform(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = MockPlayers.inLevel(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        BlockPos onIt = ServerLevel.END_SPAWN_POINT.below(); // where one stands on the platform
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.END).withLastPos(Level.END, onIt)
                .withDestination(Optional.of(Level.END)));
        ServerLevel end = level.getServer().getLevel(Level.END);
        // the End round its platform as it was, put back after: other door tests build there too
        BlockPos spawn = ServerLevel.END_SPAWN_POINT;
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        for (BlockPos p : BlockPos.betweenClosed(spawn.offset(-12, -6, -12), spawn.offset(12, 6, 12))) {
            before.put(p.immutable(), end.getBlockState(p));
        }
        try {
            net.minecraft.world.level.levelgen.feature.EndPlatformFeature.createEndPlatform(end, spawn.below(), true);
            DaemonTravel.through(player, home);
            BlockPos there = DaemonDoors.get(level.getServer()).doorIn(Level.END);
            check(helper, there != null, "a return door went down in the End");
            var keptClear = DaemonTravel.endPlatform();
            check(helper, !keptClear.isInside(there) && !keptClear.isInside(there.above()), "the door at " + there + " is off the platform");
            // what the platform clears when it is made again: its floor and the three blocks over it
            var platform = new net.minecraft.world.level.levelgen.structure.BoundingBox(spawn.getX() - 2, spawn.getY() - 2, spawn.getZ() - 2,
                    spawn.getX() + 2, spawn.getY() + 1, spawn.getZ() + 2);
            BlockState door = end.getBlockState(there);
            if (door.getBlock() instanceof DaemonDoorBlock) {
                for (BlockPos cell : DaemonDoorBlock.frame(there, door.getValue(DoorBlock.FACING))) {
                    check(helper, !platform.isInside(cell), "and its frame at " + cell + " too");
                }
            }
        } finally {
            DaemonDoors.get(level.getServer()).forgetDimension(Level.END);
            before.forEach((p, state) -> {
                if (end.getBlockState(p) != state) {
                    end.setBlock(p, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                }
            });
            if (player.level() != level) {
                player.teleportTo(level, home.getX() + 0.5, home.getY(), home.getZ() + 2.5, java.util.Set.of(), 0, 0, true);
            }
        }
        helper.succeed();
    }

    /** An operator's /tp moves a player who is no operator into the If, and back out. */
    private static void operatorTpTakesAnotherIn(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        ServerPlayer player = MockPlayers.inLevel(helper, net.minecraft.world.level.GameType.SURVIVAL);
        check(helper, !player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER), "the one moved is no operator");
        var op = server.createCommandSourceStack().withSuppressedOutput();
        String id = player.getUUID().toString();
        server.getCommands().performPrefixedCommand(op, "execute in " + IfWorld.KEY.identifier() + " run tp " + id + " 0 100 0");
        check(helper, player.level() == ifLevel, "/tp took them into the If: " + player.level().dimension());
        server.getCommands().performPrefixedCommand(op, "execute in minecraft:overworld run tp " + id + " " + helper.absolutePos(BlockPos.ZERO).getX()
                + " " + helper.absolutePos(BlockPos.ZERO).getY() + " " + helper.absolutePos(BlockPos.ZERO).getZ());
        check(helper, player.level() == helper.getLevel(), "and out again: " + player.level().dimension());
        helper.succeed();
    }

    /** A respawn at a bed in the If while it waits for its reset goes to the world's spawn; with no reset, to the bed. */
    private static void bedWhileResetPending(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        ServerPlayer player = MockPlayers.inLevel(helper, net.minecraft.world.level.GameType.SURVIVAL);
        var toBed = new net.minecraft.world.level.portal.TeleportTransition(ifLevel, new Vec3(0.5, 100, 0.5), Vec3.ZERO, 0, 0,
                net.minecraft.world.level.portal.TeleportTransition.DO_NOTHING);
        var plain = new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(player, toBed, false);
        NeoForge.EVENT_BUS.post(plain);
        check(helper, plain.getTeleportTransition().newLevel() == ifLevel, "with no reset waiting, the bed in the If is where one wakes");
        IfWorld world = IfWorld.get(server);
        world.requestReset();
        try {
            var waiting = new net.neoforged.neoforge.event.entity.player.PlayerRespawnPositionEvent(player, toBed, false);
            NeoForge.EVENT_BUS.post(waiting);
            check(helper, waiting.getTeleportTransition().newLevel() != ifLevel, "with a reset waiting, not in the If: "
                    + waiting.getTeleportTransition().newLevel().dimension());
        } finally {
            world.forgetReset(); // within the tick: no other test sees the If waiting
        }
        helper.succeed();
    }

    // ---- helpers ----

    /** Rolls the If if it is not, with the faces the If tests use. */
    private static ServerLevel rolled(MinecraftServer server) {
        IfWorld world = IfWorld.get(server);
        for (int face : new int[] {3, 5, 2, 6, 1, 4}) {
            world.addThrow(face);
        }
        return IfLevel.open(server);
    }

    private static void forgetAllDoors(ServerLevel level) {
        DaemonDoors doors = DaemonDoors.get(level.getServer());
        doors.all().forEach(doors::forget);
        for (ServerLevel each : level.getServer().getAllLevels()) {
            doors.forgetDimension(each.dimension());
        }
    }

    /** {@code player} setting a door down on {@code floor} (made stone first) as a click does, and walling it in. */
    private static void placeDoor(GameTestHelper helper, ServerPlayer player, BlockPos floor) {
        helper.setBlock(floor, Blocks.STONE);
        ItemStack stack = new ItemStack(ModTravel.DAEMON_DOOR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(floor);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false)));
        BlockState door = helper.getLevel().getBlockState(at.above());
        if (door.getBlock() instanceof DaemonDoorBlock) {
            for (BlockPos cell : DaemonDoorBlock.frame(at.above(), door.getValue(DoorBlock.FACING))) {
                helper.getLevel().setBlockAndUpdate(cell, Blocks.STONE.defaultBlockState());
            }
        }
    }

    /** {@code player} setting an If Mirror down on {@code floor} (made stone first) as a click does; where it would stand. */
    private static BlockPos setMirrorDown(GameTestHelper helper, ServerPlayer player, BlockPos floor) {
        helper.setBlock(floor, Blocks.STONE);
        ItemStack stack = new ItemStack(ModTravel.IF_MIRROR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(floor);
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).add(0, 0.5, 0), Direction.UP, at, false)));
        return at.above();
    }

    /** A bed, its head at {@code head}, its foot to the south. */
    private static void setBed(ServerLevel level, BlockPos head) {
        BlockState bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(head.south(), bed.setValue(BedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(head, bed.setValue(BedBlock.PART, BedPart.HEAD));
    }

    private static boolean holdsLodePearl(ServerPlayer player) {
        for (ItemStack stack : player.getInventory()) {
            if (stack.is(ModItems.LODE_PEARL.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Counts each time {@code player}'s inventory is sent whole to their client (as the server does when it knows the
     * client's view is wrong); the count is in the array returned.
     */
    private static int[] recordResends(ServerPlayer player) {
        int[] resent = {0};
        player.containerMenu.setSynchronizer(new ContainerSynchronizer() {
            @Override
            public void sendInitialData(AbstractContainerMenu container, List<ItemStack> slotItems, ItemStack carried, int[] dataSlots) {
                resent[0]++;
            }

            @Override
            public void sendSlotChange(AbstractContainerMenu container, int slotIndex, ItemStack itemStack) {}

            @Override
            public void sendCarriedChange(AbstractContainerMenu container, ItemStack itemStack) {}

            @Override
            public void sendDataChange(AbstractContainerMenu container, int id, int value) {}

            @Override
            public RemoteSlot createSlot() {
                return RemoteSlot.PLACEHOLDER;
            }
        });
        resent[0] = 0; // setting it sends everything once
        return resent;
    }
}
