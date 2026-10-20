package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.ifworld.IfChunkGenerator;
import dev.nookworks.ifworld.IfGates;
import dev.nookworks.ifworld.IfLevel;
import dev.nookworks.ifworld.IfMirrorBlock;
import dev.nookworks.ifworld.IfMirrors;
import dev.nookworks.ifworld.IfTravel;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.pearl.ThrownLodePearl;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.travel.TravelLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * The If. It is the server's (one If, one pair of mirrors), so these run in batches of their own (their names start
 * {@code if_}, which gives each one).
 */
final class IfTests {
    static void register() {
        test("nook_of_the_dead_is_warned_of_the_if_off", IfTests::nookOfTheDeadWarning);
        test("if_number_makes_the_same_seed", IfTests::numberMakesSeed);
        test("if_weather_is_counted_once", 120, IfTests::weatherCountedOnce);
        test("if_is_made_from_its_number", 600, IfTests::madeFromNumber);
        test("if_mirror_takes_a_player_there_and_home", 600, IfTests::mirrorThereAndHome);
        test("if_gates_stop_ways_out_but_not_moves_within", IfTests::gatesStopWaysOut);
        test("if_mirror_broken_from_its_top_goes_whole", IfTests::mirrorBrokenFromTop);
        test("if_mirror_taken_up_is_set_down_again_from_the_pocket", 300, IfTests::mirrorFromThePocket);
        test("if_lode_pearl_goes_in_and_out", 300, IfTests::lodePearlInAndOut);
        test("if_mirror_set_down_in_tall_grass_stands_whole", 300, IfTests::mirrorInTallGrass);
    }

    /**
     * A Lode Pearl crosses into and out of the If, as its mirror does (the user, 2026-10-10: it was kept out). Bound to a
     * lodestone in the If it takes its thrower there from home, and one bound at home brings them back. Into an If that
     * goes at the next start it takes nobody, and comes back still bound, as the mirror lets nobody in then.
     */
    private static void lodePearlInAndOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        BlockPos there = new BlockPos(4, ifLevel.getMaxY() - 8, 4);
        ifLevel.setBlockAndUpdate(there, Blocks.LODESTONE.defaultBlockState());
        BlockPos home = new BlockPos(1, 1, 1);
        helper.setBlock(home, Blocks.LODESTONE);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        ThrownLodePearl.Trip in = ThrownLodePearl.send(player, LodePearlItem.bound(GlobalPos.of(IfWorld.KEY, there)));
        check(helper, in == ThrownLodePearl.Trip.HOME && player.level() == ifLevel && player.blockPosition().distManhattan(there) <= 3,
                "from home into the If, to its lodestone: " + in + ", in " + player.level().dimension() + " at " + player.blockPosition());
        BlockPos homeAt = helper.absolutePos(home);
        ThrownLodePearl.Trip out = ThrownLodePearl.send(player, LodePearlItem.bound(GlobalPos.of(helper.getLevel().dimension(), homeAt)));
        check(helper, out == ThrownLodePearl.Trip.HOME && player.level() == helper.getLevel() && player.blockPosition().distManhattan(homeAt) <= 3,
                "and from the If home: " + out + ", in " + player.level().dimension() + " at " + player.blockPosition());
        IfWorld world = IfWorld.get(server);
        resetPending(world, true);
        try {
            BlockPos was = player.blockPosition();
            ThrownLodePearl.Trip closed = ThrownLodePearl.send(player, LodePearlItem.bound(GlobalPos.of(IfWorld.KEY, there)));
            check(helper, closed == ThrownLodePearl.Trip.IF_CLOSED && player.level() == helper.getLevel() && player.blockPosition().equals(was),
                    "into an If going at the next start, nobody: " + closed);
        } finally {
            resetPending(world, false);
        }
        ifLevel.setBlockAndUpdate(there, Blocks.AIR.defaultBlockState());
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * The If's mirror set down where tall grass grows (a sunflower, a large fern - any plant two blocks high) stands whole,
     * and the trip goes on. Set down half by half, the grass's top, losing its foot to the mirror's lower half, went - and
     * took that lower half with it, its own top not set down yet: half a mirror, and the trip read the facing of the air
     * where the rest should have stood (the server's error; the If's test failed so now and then, its new If's first step
     * on grass).
     */
    private static void mirrorInTallGrass(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        IfWorld world = IfWorld.get(server);
        if (world.ifMirror() != null) {
            world.forget(IfWorld.KEY, world.ifMirror());
        }
        // a patch of grass in the If's sky, tall grass on it: the nearest spot to where the player last was in the If
        BlockPos spot = new BlockPos(24, ifLevel.getMaxY() - 12, 24);
        BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 3, 3)).forEach(cell -> ifLevel.setBlock(cell,
                (cell.getY() < spot.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS));
        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
        ifLevel.setBlock(spot, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        ifLevel.setBlock(spot.above(), grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        check(helper, ifLevel.getBlockState(spot).is(Blocks.TALL_GRASS) && ifLevel.getBlockState(spot.above()).is(Blocks.TALL_GRASS), "tall grass grows there");
        ServerPlayer player = MockPlayers.inLevel(helper);
        player.setData(TravelLog.TYPE, TravelLog.of(player).ofIf(world.generation()).withLastPos(IfWorld.KEY, spot));
        try {
            IfTravel.through(player, helper.getLevel(), helper.absolutePos(new BlockPos(1, 2, 1)));
            check(helper, spot.equals(world.ifMirror()) && IfMirrors.standsAt(ifLevel, spot)
                    && ifLevel.getBlockState(spot.above()).is(ModTravel.IF_MIRROR.get()),
                    "the mirror stands whole in the grass's place: " + world.ifMirror() + ", " + ifLevel.getBlockState(spot) + " under "
                            + ifLevel.getBlockState(spot.above()));
            check(helper, player.level() == ifLevel && beside(ifLevel, spot, player), "and the player before it: " + player.level().dimension()
                    + " " + player.blockPosition());
        } finally {
            world.forget(IfWorld.KEY, spot);
            BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 3, 3)).forEach(cell -> ifLevel.setBlock(cell,
                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
            server.getPlayerList().remove(player);
        }
        helper.succeed();
    }

    /** Sets or clears the If's reset at the next start, for a test, as an operator's reset sets it and a start clears it. */
    private static void resetPending(IfWorld world, boolean pending) {
        try {
            var field = IfWorld.class.getDeclaredField("resetPending");
            field.setAccessible(true);
            field.setBoolean(world, pending);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The If keeps to its mirror and the door: a player in it is refused a trip to another dimension any other way - a
     * portal, a command, another mod's teleporter. A move within the If (an ender pearl, a /tp there) crosses nothing,
     * and goes: NeoForge asks about a move within a dimension too, and it was refused.
     */
    private static void gatesStopWaysOut(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel ifLevel = rolled(server);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 in = Vec3.atBottomCenterOf(new BlockPos(0, ifLevel.getMaxY() - 8, 0));
        IfGates.passing(() -> player.teleport(new TeleportTransition(ifLevel, in, Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING)));
        check(helper, player.level() == ifLevel, "in the If, let in as the mirror lets them");
        check(helper, CommonHooks.onTravelToDimension(player, IfWorld.KEY), "a move within the If goes");
        check(helper, !CommonHooks.onTravelToDimension(player, Level.OVERWORLD), "a way home but the mirror and the door is refused");
        check(helper, !CommonHooks.onTravelToDimension(player, Level.NETHER), "and to the Nether");
        player.teleport(new TeleportTransition(ifLevel, in.add(8, 0, 0), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
        check(helper, player.level() == ifLevel && player.position().distanceTo(in.add(8, 0, 0)) < 0.5,
                "a teleport within it is made: " + player.position());
        server.getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * Broken from its upper half, the mirror goes whole: in creative both halves at once and nothing dropped, as a door;
     * in survival the lower half breaks with it and drops the mirror, once. (Broken from the top, half a mirror stood.)
     */
    private static void mirrorBrokenFromTop(GameTestHelper helper) {
        BlockState lower = ModTravel.IF_MIRROR.get().defaultBlockState();
        BlockPos byCreative = new BlockPos(1, 1, 3);
        BlockPos bySurvivor = new BlockPos(4, 1, 3);
        for (BlockPos pos : new BlockPos[] {byCreative, bySurvivor}) {
            helper.setBlock(pos, lower);
            helper.setBlock(pos.above(), lower.setValue(IfMirrorBlock.HALF, DoubleBlockHalf.UPPER));
        }
        ServerPlayer creative = MockPlayers.inLevel(helper, GameType.CREATIVE);
        creative.gameMode.destroyBlock(helper.absolutePos(byCreative.above()));
        check(helper, helper.getBlockState(byCreative).isAir() && helper.getBlockState(byCreative.above()).isAir(),
                "in creative, both halves go");
        ServerPlayer survivor = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        survivor.gameMode.destroyBlock(helper.absolutePos(bySurvivor.above()));
        helper.succeedWhen(() -> {
            check(helper, helper.getBlockState(bySurvivor).isAir() && helper.getBlockState(bySurvivor.above()).isAir(),
                    "in survival, both halves go");
            int dropped = helper.getEntities(EntityTypes.ITEM).stream().filter(item -> item.getItem().is(ModTravel.IF_MIRROR_ITEM.get()))
                    .mapToInt(item -> item.getItem().getCount()).sum();
            check(helper, dropped == 1, "and one mirror drops, the survivor's: " + dropped);
        });
    }

    /** The number alone makes the seed: the same on any machine, in any save (a number told to a friend is the same If). */
    private static void numberMakesSeed(GameTestHelper helper) {
        check(helper, IfWorld.seedOf("352614") == IfWorld.seedOf("352614"), "the same number, the same seed");
        check(helper, IfWorld.seedOf("352614") != IfWorld.seedOf("352615"), "another number, another seed");
        check(helper, IfWorld.seedOf("111111") == 2816636484257668951L, "fixed for good: " + IfWorld.seedOf("111111"));
        helper.succeed();
    }

    /** Rolls the If if it is not, with these faces. */
    private static ServerLevel rolled(MinecraftServer server) {
        IfWorld world = IfWorld.get(server);
        for (int face : new int[] {3, 5, 2, 6, 1, 4}) {
            world.addThrow(face);
        }
        return IfLevel.open(server);
    }

    /**
     * Rolled, the If is made: the overworld's kind of dimension, from its number's seed, its biomes vanilla's overworld
     * biomes and decorated with vanilla's own features, its land made.
     */
    private static void madeFromNumber(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = rolled(server);
        IfWorld world = IfWorld.get(server);
        check(helper, level != null && level.dimension().equals(IfWorld.KEY), "the If is made");
        check(helper, level.getSeed() == IfWorld.seedOf(world.number()) && level.getSeed() != server.overworld().getSeed(),
                "from its number's seed, not the world's: " + level.getSeed());
        check(helper, level.getChunkSource().getGenerator() instanceof IfChunkGenerator, "with the If's generator");
        level.getChunk(0, 0);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
        check(helper, surface > level.getMinY() + 8, "land made: " + surface);
        Holder<Biome> biome = level.getBiome(new BlockPos(8, surface, 8));
        check(helper, biome.unwrapKey().orElseThrow().identifier().getNamespace().equals("minecraft"), "a vanilla biome: " + biome);
        IfChunkGenerator generator = (IfChunkGenerator) level.getChunkSource().getGenerator();
        Holder<Biome> plains = server.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        check(helper, generator.getBiomeGenerationSettings(plains) != plains.value().getGenerationSettings(),
                "decorated with vanilla's own plains, not the server's");
        check(helper, IfLevel.open(server) == level, "made once");
        helper.succeed();
    }

    /**
     * Through the home mirror into the If, where its mirror is set down and the player stands before it; through that
     * home; with the home mirror gone, to the respawn point. A second home mirror is refused.
     */
    private static void mirrorThereAndHome(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerLevel ifLevel = rolled(server);
        IfWorld world = IfWorld.get(server);
        GlobalPos oldHome = world.homeMirror();
        if (oldHome != null) {
            world.forget(oldHome.dimension(), oldHome.pos());
        }
        if (world.ifMirror() != null) {
            world.forget(IfWorld.KEY, world.ifMirror());
        }
        world.forgetIfMirrorSetDown(); // the trip sets the first down free
        @SuppressWarnings("removal")
        ServerPlayer player = MockPlayers.inLevel(helper);
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ItemStack stack = new ItemStack(ModTravel.IF_MIRROR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        BlockPos home = floor.above();
        check(helper, IfMirrors.standsAt(level, home) && IfMirrors.isHome(level, home), "the home mirror stands");
        helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
        ItemStack second = new ItemStack(ModTravel.IF_MIRROR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        BlockPos floor2 = helper.absolutePos(new BlockPos(3, 1, 3));
        second.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor2).add(0, 0.5, 0), Direction.UP, floor2, false)));
        check(helper, !IfMirrors.standsAt(level, floor2.above()), "a second home mirror is refused");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // in as a player goes in, by an empty-handed click on the mirror: on its back, nobody goes; on its glass, through
        BlockState mirror = level.getBlockState(home);
        Direction glass = mirror.getValue(IfMirrorBlock.FACING);
        mirror.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(home).relative(glass.getOpposite(), 0.5),
                glass.getOpposite(), home, false));
        check(helper, player.level() == level, "a click on its back takes nobody through");
        mirror.useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(home).relative(glass, 0.5), glass, home, false));
        check(helper, player.level() == ifLevel, "through into the If: " + player.level().dimension());
        BlockPos there = world.ifMirror();
        check(helper, there != null && IfMirrors.standsAt(ifLevel, there), "its mirror set down there: " + there);
        check(helper, beside(ifLevel, there, player), "the player before it, not in it: " + player.blockPosition() + " by " + there);
        check(helper, TravelLog.of(player).hasBeenTo(IfWorld.KEY), "the If counts as visited (the door lists it now)");
        // under the open sky is the mirror's spot, as DaemonTravel.setDown chooses it - a tree's leaves over it count as
        // sky; the step in front of it may be under a big tree's bough (the test once failed on such a step, its own
        // column checked, in an If with a dark oak there)
        check(helper, openOverhead(ifLevel, there.above(2)), "a first step into the If is by a mirror under the open sky: " + there);
        TravelLog forgotten = TravelLog.of(player).withLastPos(IfWorld.KEY, there).ofIf(world.generation() + 1);
        check(helper, !forgotten.hasBeenTo(IfWorld.KEY) && !forgotten.lastPos().containsKey(IfWorld.KEY),
                "after a reset, the old If is forgotten: " + forgotten);
        helper.runAfterDelay(25, () -> {
            IfTravel.through(player, ifLevel, there);
            check(helper, player.level() == level && beside(level, home, player),
                    "home, before the home mirror: " + player.level().dimension() + " " + player.blockPosition() + " by " + home);
            player.teleport(new TeleportTransition(ifLevel, Vec3.atBottomCenterOf(there.north(2)), Vec3.ZERO, 0, 0,
                    TeleportTransition.DO_NOTHING));
            check(helper, player.level() == level, "no other way crosses into the If");
            helper.runAfterDelay(25, () -> {
                IfTravel.through(player, level, home);
                check(helper, player.level() == ifLevel, "in again, by the mirror");
                level.destroyBlock(home, false);
                check(helper, world.homeMirror() == null, "the home mirror taken away is forgotten");
                helper.runAfterDelay(25, () -> {
                    // from inside the If, through its mirror, with none at home to come out by
                    IfTravel.through(player, ifLevel, there);
                    BlockPos spawn = level.getRespawnData().pos();
                    check(helper, player.level().dimension() == Level.OVERWORLD
                            && player.blockPosition().distManhattan(spawn) <= 64,
                            "out of the If with no home mirror: the respawn point, " + player.blockPosition() + " (spawn " + spawn + ")");
                    ifLevel.removeBlock(there.above(), false);
                    ifLevel.removeBlock(there, false);
                    player.connection.disconnect(Component.empty());
                    helper.succeed();
                });
            });
        });
    }

    /**
     * One weather for the whole server, its spells counted down once a tick: the If, a second level of the overworld's
     * kind, leaves the counting to the overworld (ServerLevelWeatherMixin), or every spell would last half as long.
     */
    private static void weatherCountedOnce(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        check(helper, rolled(server) != null, "the If is there to tick");
        var rules = server.overworld().getGameRules();
        var weather = server.getWeatherData();
        boolean was = rules.get(GameRules.ADVANCE_WEATHER);
        int clearWas = weather.getClearWeatherTime();
        AfterTest.undo(helper, () -> {
            rules.set(GameRules.ADVANCE_WEATHER, was, server);
            weather.setClearWeatherTime(clearWas);
        });
        rules.set(GameRules.ADVANCE_WEATHER, true, server);
        weather.setClearWeatherTime(50_000);
        helper.runAfterDelay(80, () -> {
            int counted = 50_000 - weather.getClearWeatherTime();
            check(helper, counted >= 70 && counted <= 90, "80 ticks counted once, not twice: counted " + counted);
            helper.succeed();
        });
    }

    /**
     * The If's mirror is set down free by the first trip only: one taken up since is set down again from the traveller's
     * pocket, or the trip says so and goes nowhere - else every trip would make a mirror from nothing.
     */
    private static void mirrorFromThePocket(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = helper.getLevel();
        ServerLevel ifLevel = rolled(server);
        IfWorld world = IfWorld.get(server);
        GlobalPos oldHome = world.homeMirror();
        if (oldHome != null) {
            world.forget(oldHome.dimension(), oldHome.pos());
        }
        if (world.ifMirror() != null) {
            world.forget(IfWorld.KEY, world.ifMirror());
        }
        world.record(IfWorld.KEY, BlockPos.ZERO); // the If's mirror stood once, and was taken up
        world.forget(IfWorld.KEY, BlockPos.ZERO);
        @SuppressWarnings("removal")
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL); // a creative player sets one down regardless
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        ItemStack stack = new ItemStack(ModTravel.IF_MIRROR_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos floor = helper.absolutePos(new BlockPos(1, 1, 1));
        stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor).add(0, 0.5, 0), Direction.UP, floor, false)));
        BlockPos home = floor.above();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        IfTravel.through(player, level, home);
        check(helper, player.level() == level && world.ifMirror() == null, "no mirror in the pocket: nowhere, and none made");
        player.getInventory().setItem(9, new ItemStack(ModTravel.IF_MIRROR_ITEM.get()));
        helper.runAfterDelay(25, () -> {
            IfTravel.through(player, level, home);
            BlockPos there = world.ifMirror();
            check(helper, player.level() == ifLevel && there != null && IfMirrors.standsAt(ifLevel, there), "with one, into the If, and it stands there");
            check(helper, player.getInventory().getItem(9).isEmpty(), "from the pocket: " + player.getInventory().getItem(9));
            ifLevel.removeBlock(there.above(), false);
            ifLevel.removeBlock(there, false);
            level.removeBlock(home.above(), false);
            level.removeBlock(home, false);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    private IfTests() {}

    /** Standing in the cell before the mirror at {@code lower} (or behind it, should the front be shut): not in it. */
    private static boolean beside(ServerLevel level, BlockPos lower, ServerPlayer player) {
        Direction facing = level.getBlockState(lower).getValue(IfMirrorBlock.FACING);
        BlockPos at = player.blockPosition();
        return at.equals(lower.relative(facing)) || at.equals(lower.relative(facing.getOpposite()));
    }

    /** Whether nothing but leaves (and air) stands from {@code from} up to the top of the world: the sky, as setDown reads it. */
    private static boolean openOverhead(ServerLevel level, BlockPos from) {
        for (BlockPos.MutableBlockPos pos = from.mutable(); pos.getY() <= level.getMaxY(); pos.move(0, 1, 0)) {
            var state = level.getBlockState(pos);
            if ((state.is(BlockTags.BLOCKS_MOTION) || !state.getFluidState().isEmpty()) && !state.is(BlockTags.LEAVES)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Nook of the Dead reaches its world through the If: with it installed and the If off, an operator is told as they
     * join. Without it installed (as here) nothing is said, whatever the If's switch; the words are there in English (the
     * server's only language - check_lang.py checks the other seven).
     */
    private static void nookOfTheDeadWarning(GameTestHelper helper) {
        check(helper, !dev.nookworks.ifworld.NookOfTheDeadWarning.present() && !dev.nookworks.ifworld.NookOfTheDeadWarning.stranded(),
                "Nook of the Dead is not installed here, so nothing is said");
        for (String key : java.util.List.of("message.nookworks.notd_if_off", "nookworks.world_features.notd.title",
                "nookworks.world_features.notd.message")) {
            check(helper, net.minecraft.locale.Language.getInstance().has(key), key + " is written");
        }
        helper.succeed();
    }
}
