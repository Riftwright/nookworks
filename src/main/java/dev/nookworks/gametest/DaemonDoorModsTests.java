package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import dev.nookworks.Nookworks;
import dev.nookworks.mixin.MinecraftServerAccessor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import dev.nookworks.port.test.GameTestHelper;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonDoors;
import dev.nookworks.travel.DaemonTravel;
import dev.nookworks.travel.OtherPortals;
import dev.nookworks.travel.TravelLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * The Dock-a-Daemon Door into other mods' dimensions, with the real mods loaded: the Twilight Forest
 * ({@code twilightforest:twilight_forest}) and TofuCraft's Tofu World ({@code tofucraft:tofu_world}). Registered only with
 * either in run-gametest/mods. Each test runs alone (its name begins daemon_door_), the doors being the server's; the
 * places it sets doors down at are far apart and far from any arena, in dimensions no other test uses.
 */
final class DaemonDoorModsTests {
    private DaemonDoorModsTests() {}

    /**
     * The mod's dimension on the test server, made as a server makes it from the mod's data. A world of the game has
     * every datapack's dimensions; the GameTest server bakes its flat world's dimensions from an empty list
     * (GameTestServer.create), so it has only vanilla's three, and a test of the door into another mod's world makes
     * that world here first: from the mod's own {@code data/<mod>/dimension/<name>.json}, as a level beside the others
     * (as IfLevel makes the If), its seed the world's.
     */
    static ServerLevel open(MinecraftServer server, ResourceKey<Level> key) {
        ServerLevel existing = server.getLevel(key);
        if (existing != null) {
            return existing;
        }
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(key.location().getNamespace(),
                "dimension/" + key.location().getPath() + ".json");
        LevelStem stem;
        try (var reader = server.getResourceManager().getResourceOrThrow(file).openAsReader()) {
            stem = LevelStem.CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
                    JsonParser.parseReader(reader)).getOrThrow();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("no " + file, e);
        }
        MinecraftServerAccessor access = (MinecraftServerAccessor) server;
        ServerLevel level = new ServerLevel(server, access.nookworks$getExecutor(), access.nookworks$getStorageSource(),
                new DerivedLevelData(server.getWorldData(), server.getWorldData().overworldData()), key, stem, QUIET, false,
                BiomeManager.obfuscateSeed(server.getWorldData().worldGenOptions().seed()), List.of(), false,
                server.overworld().getRandomSequences());
        access.nookworks$getLevels().put(key, level);
        server.markWorldsDirty();
        level.getWorldBorder().applySettings(server.overworld().getWorldBorder().createSettings());
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.LevelEvent.Load(level));
        Nookworks.LOGGER.info("Made {} on the test server from its mod's data", key.location());
        return level;
    }

    private static final ChunkProgressListener QUIET = new ChunkProgressListener() {
        @Override
        public void updateSpawnPos(net.minecraft.world.level.ChunkPos center) {}

        @Override
        public void onStatusChange(net.minecraft.world.level.ChunkPos chunk, @org.jspecify.annotations.Nullable ChunkStatus status) {}

        @Override
        public void start() {}

        @Override
        public void stop() {}
    };

    static boolean any() {
        return ModList.get().isLoaded("twilightforest") || ModList.get().isLoaded("tofucraft");
    }

    static void register() {
        List<ResourceKey<Level>> worlds = new ArrayList<>();
        if (ModList.get().isLoaded("twilightforest")) {
            worlds.add(OtherPortals.TWILIGHT_FOREST);
        }
        if (ModList.get().isLoaded("tofucraft")) {
            worlds.add(OtherPortals.TOFU_WORLD);
        }
        for (ResourceKey<Level> world : worlds) {
            String name = world.location().getNamespace();
            NookworksGameTests.test("daemon_door_" + name + "_is_named_and_drawn", h -> namedAndDrawn(h, world));
            NookworksGameTests.test("daemon_door_" + name + "_return_doors_stand_on_the_ground", 400, h -> onTheGround(h, world));
            NookworksGameTests.test("daemon_door_" + name + "_there_and_back", 600, h -> thereAndBack(h, world));
            NookworksGameTests.test("daemon_door_" + name + "_opens_only_from_its_portals_world", 400, h -> onlyFromItsPortalsWorld(h, world));
        }
        NookworksGameTests.test("daemon_door_a_dimension_gone_strands_nobody", 200, DaemonDoorModsTests::goneDimension);
    }

    /** Far apart and far from the arenas (which stand near the overworld's origin), as the door would first open onto them. */
    private static final List<BlockPos> BASES = List.of(new BlockPos(1200, 64, -900), new BlockPos(-2600, 64, 2100),
            new BlockPos(4100, 40, 3900), new BlockPos(-5200, 90, -4700), new BlockPos(7300, 64, -6100));

    /**
     * Listed by a name of its own in every language (the mod's own key where the mod gives one - the Twilight Forest's -
     * and ours for the Tofu World, which gives none), and drawn through the door with a look of its own, not the hue
     * worked out for an unknown dimension.
     */
    private static void namedAndDrawn(GameTestHelper helper, ResourceKey<Level> world) {
        ServerLevel level = open(helper.getLevel().getServer(), world);
        check(helper, level != null, "the server has " + world.location());
        Component name = DaemonDestinations.name(world);
        String key = name.getContents() instanceof TranslatableContents t ? t.getKey() : "";
        check(helper, key.equals("dimension." + world.location().getNamespace() + "." + world.location().getPath()), "named by " + key);
        DaemonDestinations.Look look = DaemonDestinations.look(world, helper.getLevel().registryAccess());
        check(helper, look.seed() == (world.equals(OtherPortals.TWILIGHT_FOREST) ? 5 : 7), "its own look: " + look);
        helper.succeed();
    }

    /**
     * A return door as a first trip sets one down, at five places far apart, both as where the player last stood there
     * (the search as near as can be) and as somewhere they never stood (under the open sky): every one on ground that
     * holds, not on a tree's crown, with nothing flowing in it or in front of it (no soy-milk sea), and outside every
     * structure of the dimension's mod (a Twilight Forest boss's locked tower). Where the floor was is written to the log.
     */
    private static void onTheGround(GameTestHelper helper, ResourceKey<Level> world) {
        ServerLevel level = open(helper.getLevel().getServer(), world);
        List<String> seen = new ArrayList<>();
        int offset = 0;
        for (BlockPos base : BASES) {
            for (boolean underSky : new boolean[] {true, false}) {
                BlockPos at = base.offset(underSky ? 0 : 200, 0, offset);
                BlockPos door = DaemonTravel.setDown(level, at, underSky);
                check(helper, door != null && DaemonDoors.standsAt(level, door), "a door set down near " + at + ": " + door);
                Direction facing = level.getBlockState(door).getValue(DoorBlock.FACING);
                BlockPos front = door.relative(facing.getOpposite());
                BlockState floor = level.getBlockState(front.below());
                for (BlockPos cell : List.of(door, door.above(), front, front.above())) {
                    check(helper, level.getFluidState(cell).isEmpty(), "nothing flows at " + cell + " by the door at " + door);
                }
                check(helper, floor.isFaceSturdy(level, front.below(), Direction.UP), "ground that holds in front of " + door + ": " + floor);
                check(helper, !floor.is(BlockTags.LEAVES) && !level.getBlockState(door.below()).is(BlockTags.LEAVES),
                        "not on a tree's crown: " + door + " on " + floor);
                check(helper, !OtherPortals.inTheirStructure(level, door) && !OtherPortals.inTheirStructure(level, front),
                        "outside the mod's structures: " + door);
                check(helper, DaemonDoorBlock.framed(level, door, facing), "set into a wall: " + door);
                seen.add(door.toShortString() + " on " + BuiltInRegistries.BLOCK.getKey(floor.getBlock()) + (underSky ? " (never there)" : " (last there)"));
                level.removeBlock(door.above(), false);
                level.removeBlock(door, false);
            }
            offset += 50;
        }
        Nookworks.LOGGER.info("Return doors set down in {}: {}", world.location(), seen);
        helper.succeed();
    }

    /**
     * Through the home door to the dimension, the return door set down where the player was last there, the player
     * standing on solid ground in front of it; back home through it; out again by the same door; and with that door taken
     * up, out again setting one down from the pocket, where the player last stood.
     */
    private static void thereAndBack(GameTestHelper helper, ResourceKey<Level> world) {
        ServerLevel home = helper.getLevel();
        ServerLevel there = open(home.getServer(), world);
        TravelTests.forgetAllDoors(home);
        List<Component> said = new ArrayList<>();
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        // the home door near the world's middle, not in the arena: the test arenas stand millions of blocks out, and the
        // Twilight Forest is eight times the Overworld (its coordinate_scale 0.125), so a door from there would come out
        // past the world border
        BlockPos homeDoor = DaemonTravel.setDown(home, new BlockPos(300, 64, -200), false);
        check(helper, homeDoor != null, "a home door set down in the Overworld");
        DaemonDoors.get(home.getServer()).record(Level.OVERWORLD, homeDoor);
        Direction homeFacing = home.getBlockState(homeDoor).getValue(DoorBlock.FACING);
        BlockPos inFront = homeDoor.relative(homeFacing.getOpposite());
        player.teleportTo(home, inFront.getX() + 0.5, inFront.getY(), inFront.getZ() + 0.5, java.util.Set.of(), 0, 0);
        BlockPos last = new BlockPos(2300, 64, -3100);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(world).withDestination(Optional.of(world)).withLastPos(world, last));
        // where the door first opens: where the player last was, or in a world of another measure where a portal from the
        // home door would come out (the Twilight Forest's own portal scales so too)
        double scale = net.minecraft.world.level.dimension.DimensionType.getTeleportationScale(home.dimensionType(), there.dimensionType());
        BlockPos expected = scale == 1.0 ? last : BlockPos.containing(homeDoor.getX() * scale, homeDoor.getY(), homeDoor.getZ() * scale);
        DaemonTravel.through(player, homeDoor);
        BlockPos door = DaemonDoors.get(home.getServer()).doorIn(world);
        check(helper, player.level() == there, "through to " + world.location() + ": " + player.level().dimension().location() + ", told " + said);
        check(helper, door != null && DaemonDoors.standsAt(there, door), "a return door set down there: " + door);
        check(helper, Math.abs(door.getX() - expected.getX()) <= 17 && Math.abs(door.getZ() - expected.getZ()) <= 17,
                "near where it should first open: " + door + " for " + expected);
        Nookworks.LOGGER.info("The return door in {} came out at {}, on {}", world.location(), door,
                BuiltInRegistries.BLOCK.getKey(there.getBlockState(player.blockPosition().below()).getBlock()));
        BlockPos feet = player.blockPosition();
        check(helper, there.getBlockState(feet.below()).isFaceSturdy(there, feet.below(), Direction.UP)
                && !there.getBlockState(feet.below()).is(BlockTags.LEAVES) && there.getFluidState(feet).isEmpty(),
                "standing on solid ground: " + there.getBlockState(feet.below()) + " at " + feet);
        check(helper, TravelTests.beside(there, door, player), "in front of the door");
        helper.runAfterDelay(45, () -> {
            DaemonTravel.through(player, door);
            check(helper, player.level() == home && TravelTests.beside(home, homeDoor, player), "back home: " + player.level().dimension().location());
            helper.runAfterDelay(45, () -> {
                DaemonTravel.through(player, homeDoor);
                check(helper, player.level() == there && door.equals(DaemonDoors.get(home.getServer()).doorIn(world)),
                        "out again, by the same door: " + DaemonDoors.get(home.getServer()).doorIn(world));
                helper.runAfterDelay(45, () -> {
                    DaemonTravel.through(player, door);
                    // the return door taken up (as by hand), and the traveller carrying a door: the next trip sets it down again
                    there.removeBlock(door.above(), false);
                    there.removeBlock(door, false);
                    BlockPos moved = door.offset(30, 0, 30);
                    player.setData(TravelLog.TYPE, TravelLog.of(player).withLastPos(world, moved));
                    player.getInventory().add(new ItemStack(ModTravel.DAEMON_DOOR_ITEM.get()));
                    helper.runAfterDelay(45, () -> {
                        DaemonTravel.through(player, homeDoor);
                        BlockPos again = DaemonDoors.get(home.getServer()).doorIn(world);
                        check(helper, player.level() == there && again != null && DaemonDoors.standsAt(there, again),
                                "a door taken up is set down again from the pocket: " + again);
                        check(helper, DaemonTravel.carried(player, ModTravel.DAEMON_DOOR_ITEM.get()).isEmpty(), "and the pocket's door used");
                        there.removeBlock(again.above(), false);
                        there.removeBlock(again, false);
                        player.changeDimension(new DimensionTransition(home, Vec3.atBottomCenterOf(inFront), Vec3.ZERO, 0, 0,
                                DimensionTransition.DO_NOTHING));
                        home.removeBlock(homeDoor.above(), false);
                        home.removeBlock(homeDoor, false);
                        TravelTests.forgetAllDoors(home);
                        player.connection.disconnect(Component.empty());
                        helper.succeed();
                    });
                });
            });
        });
    }

    /**
     * The Twilight Forest's portal joins it to its origin only (the Overworld by default; anywhere else "may be
     * considered an exploit", its config says), and TofuCraft's lights only in the Overworld or the Tofu World: a home
     * door in the Nether does not open onto either, and says so; from the Overworld it does (tried above).
     */
    private static void onlyFromItsPortalsWorld(GameTestHelper helper, ResourceKey<Level> world) {
        check(helper, Level.OVERWORLD.equals(OtherPortals.onlyFrom(world)), "reached from the Overworld only: " + OtherPortals.onlyFrom(world));
        ServerLevel overworld = helper.getLevel();
        open(overworld.getServer(), world);
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        TravelTests.forgetAllDoors(overworld);
        BlockPos homeDoor = DaemonTravel.setDown(nether, new BlockPos(-700, 70, 900), false);
        check(helper, homeDoor != null, "a door set down in the Nether");
        DaemonDoors doors = DaemonDoors.get(overworld.getServer());
        doors.record(Level.NETHER, homeDoor); // the first door: the home door
        check(helper, doors.isHome(Level.NETHER, homeDoor), "the home door is in the Nether");
        List<Component> said = new ArrayList<>();
        ServerPlayer player = MockPlayers.hearing(helper, GameType.SURVIVAL, said);
        player.changeDimension(new DimensionTransition(nether, Vec3.atBottomCenterOf(homeDoor.north()), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(world).withDestination(Optional.of(world)));
        helper.runAfterDelay(45, () -> {
            DaemonTravel.through(player, homeDoor);
            check(helper, player.level() == nether, "not through: still in the Nether");
            check(helper, said.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t
                    && t.getKey().equals("message.nookworks.daemon_door.only_from")), "and told why: " + said);
            nether.removeBlock(homeDoor.above(), false);
            nether.removeBlock(homeDoor, false);
            TravelTests.forgetAllDoors(overworld);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /**
     * A world opened without a mod whose dimension a player had been to, chosen at the panel, and the home door's: the
     * panel does not list it, the home door says it is gone and sends nobody, and a return door with its home door's
     * dimension gone sends its traveller to their respawn point - nobody stranded, nothing thrown.
     */
    private static void goneDimension(GameTestHelper helper) {
        ResourceKey<Level> gone = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("nookworks_test", "gone_world"));
        ServerLevel level = helper.getLevel();
        TravelTests.forgetAllDoors(level);
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        TravelTests.placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos homeDoor = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState shut = level.getBlockState(homeDoor);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, homeDoor, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(gone).withDestination(Optional.of(gone)).withLastPos(gone, BlockPos.ZERO));
        check(helper, !DaemonDestinations.choices(player, Level.OVERWORLD).contains(gone), "the panel does not list it");
        DaemonTravel.through(player, homeDoor);
        check(helper, player.level() == level, "the home door sends nobody to it");
        // the home door's own dimension gone: a return door elsewhere sends its traveller home to their respawn point
        DaemonDoors doors = DaemonDoors.get(level.getServer());
        TravelTests.forgetAllDoors(level);
        doors.record(gone, new BlockPos(5, 64, 5));
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        BlockPos returnDoor = DaemonTravel.setDown(nether, new BlockPos(900, 70, -700), false);
        doors.record(Level.NETHER, returnDoor);
        player.changeDimension(new DimensionTransition(nether, Vec3.atBottomCenterOf(returnDoor.north()), Vec3.ZERO, 0, 0, DimensionTransition.DO_NOTHING));
        helper.runAfterDelay(45, () -> {
            DaemonTravel.through(player, returnDoor);
            check(helper, player.level().dimension().equals(Level.OVERWORLD), "a return door with its home gone: to the respawn point, "
                    + player.level().dimension().location());
            check(helper, doors.home() == null || !doors.home().dimension().equals(gone), "the gone home forgotten");
            nether.removeBlock(returnDoor.above(), false);
            nether.removeBlock(returnDoor, false);
            level.destroyBlock(homeDoor, false);
            TravelTests.forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }
}
