package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import java.util.List;
import java.util.Optional;

import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.registry.ModTravel;
import dev.nookworks.travel.DaemonDestinations;
import dev.nookworks.travel.DaemonDoorBlock;
import dev.nookworks.travel.DaemonDoors;
import dev.nookworks.travel.DaemonPanelBlock;
import dev.nookworks.travel.DaemonPanelMenu;
import dev.nookworks.travel.DaemonTravel;
import dev.nookworks.travel.TravelLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Dock-a-Daemon Door. Where the doors stand is kept for the whole world, so each of these runs in a batch of its own
 * (their names start {@code daemon_door_}) and starts by forgetting every door.
 */
final class TravelTests {
    static void register() {
        test("daemon_door_one_stands_in_each_dimension", TravelTests::oneInEachDimension);
        test("daemon_door_panel_lists_where_you_have_been", TravelTests::panelLists);
        test("daemon_door_takes_a_player_there_and_back", 400, TravelTests::thereAndBack);
        test("daemon_door_walked_into_takes_the_player_through", 100, TravelTests::walkedInto);
        test("daemon_door_return_door_stays_out_of_lava", 400, TravelTests::outOfLava);
        test("daemon_door_return_door_set_down_in_tall_grass_stands_whole", 200, TravelTests::inTallGrass);
        test("daemon_door_ends_first_door_faces_the_platform", TravelTests::endLanding);
        test("daemon_door_comes_out_on_a_floor", TravelTests::comesOutOnAFloor);
        test("daemon_door_goes_nowhere_unless_set_into_a_wall", 400, TravelTests::onlyInAWall);
        test("daemon_door_from_behind_is_a_door", 100, TravelTests::fromBehind);
        test("daemon_door_set_off_is_a_door", TravelTests::setOff);
        test("daemon_door_shows_the_gate_of_the_world_beyond", TravelTests::gates);
        test("daemon_door_names_and_colours_other_mods_worlds", TravelTests::otherModsWorlds);
        test("daemon_door_bare_return_door_is_walled_in_on_arrival", 400, TravelTests::bareReturnDoor);
        test("daemon_door_taken_up_is_set_down_again_from_the_pocket", 400, TravelTests::fromThePocket);
        test("daemon_door_slides_away_from_its_panel", TravelTests::slidesAwayFromItsPanel);
        test("daemon_door_return_door_keeps_off_land_its_traveller_may_not_build_on", 400, TravelTests::keepsOffClaims);
        test("daemon_door_sends_nobody_to_a_door_it_may_not_wall_in", 200, TravelTests::noTripToAnUnframedDoor);
    }

    private static void forgetAllDoors(ServerLevel level) {
        DaemonDoors doors = DaemonDoors.get(level.getServer());
        doors.all().forEach(doors::forget);
        for (ServerLevel each : level.getServer().getAllLevels()) {
            doors.forgetDimension(each.dimension()); // and that any door stood there: a trip sets the first down free
        }
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper) {
        return MockPlayers.inLevel(helper);
    }

    /** Land a claim mod would keep a test's player off, as a claim mod refuses a placing: while set, placing in it is refused. */
    private static volatile net.minecraft.world.level.levelgen.structure.BoundingBox claimed;
    private static volatile ServerPlayer claimant;
    private static boolean claimsHeard;

    private static void claim(net.minecraft.world.level.levelgen.structure.BoundingBox box, ServerPlayer player) {
        if (!claimsHeard) {
            claimsHeard = true;
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGH,
                    (net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) -> {
                        var box2 = claimed;
                        if (box2 != null && event.getEntity() == claimant && box2.isInside(event.getPos())) {
                            event.setCanceled(true);
                        }
                    });
        }
        claimant = player;
        claimed = box;
    }
    /**
     * {@code player} setting a door down on {@code floor} (made stone first), as a click with it in hand does, and walling
     * it in: the ten blocks round its doorway made stone, as a door is a gate only set into a wall.
     */
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

    /** {@code player} stepping towards the back of the door at {@code lower} (as the client says it moves): going in from its front. */
    private static void stepIn(ServerLevel level, ServerPlayer player, BlockPos lower, boolean fromFront) {
        Direction facing = level.getBlockState(lower).getValue(DoorBlock.FACING);
        Direction towards = fromFront ? facing : facing.getOpposite(); // the front is the side opposite its facing
        player.setKnownMovement(new Vec3(towards.getStepX() * 0.2, 0, towards.getStepZ() * 0.2));
    }

    /** The first door set down is the home door; a second in the same dimension is refused; one taken away is forgotten. */
    private static void oneInEachDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        DaemonDoors doors = DaemonDoors.get(level.getServer());
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos first = helper.absolutePos(new BlockPos(1, 2, 1));
        check(helper, DaemonDoors.standsAt(level, first), "the first door stands");
        check(helper, GlobalPos.of(Level.OVERWORLD, first).equals(doors.home()), "and is the home door: " + doors.home());
        placeDoor(helper, player, new BlockPos(3, 1, 3));
        check(helper, helper.getBlockState(new BlockPos(3, 2, 3)).isAir(), "a second door in the same dimension is refused");
        check(helper, first.equals(doors.doorIn(Level.OVERWORLD)), "the first is still the one");
        level.destroyBlock(first, false);
        check(helper, doors.home() == null && doors.doorIn(Level.OVERWORLD) == null, "taken away, it is forgotten");
        placeDoor(helper, player, new BlockPos(3, 1, 3));
        BlockPos second = helper.absolutePos(new BlockPos(3, 2, 3));
        check(helper, DaemonDoors.standsAt(level, second) && GlobalPos.of(Level.OVERWORLD, second).equals(doors.home()),
                "with none standing, the next is the home door");
        forgetAllDoors(level);
        helper.succeed();
    }

    /**
     * The panel lists the dimensions the player has been to, other than the door's own, and a click on one makes it theirs;
     * it opens only beside the home door.
     */
    private static void panelLists(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        check(helper, DaemonDestinations.choices(player, Level.OVERWORLD).isEmpty(), "nowhere else yet: " + TravelLog.of(player));
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER));
        List<?> choices = DaemonDestinations.choices(player, Level.OVERWORLD);
        check(helper, choices.equals(List.of(Level.NETHER)), "been to the Nether: " + choices);
        check(helper, DaemonDestinations.choices(player, Level.NETHER).equals(List.of(Level.OVERWORLD)), "from the Nether, home");
        DaemonPanelMenu menu = new DaemonPanelMenu(0, DaemonDestinations.choices(player, Level.OVERWORLD), null);
        check(helper, menu.clickMenuButton(player, 1) && TravelLog.of(player).destination().equals(Optional.of(Level.NETHER)),
                "a click chooses it (button 0 is Off, the places after it)");
        check(helper, menu.clickMenuButton(player, 0) && TravelLog.of(player).destination().isEmpty(), "Off sets the door off");
        check(helper, !menu.clickMenuButton(player, 2), "a button past the list does nothing");
        // it is the door's beside it, within reach; with the door gone, it has none (its screen opens only with one: a mock
        // player cannot be sent the screen)
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos door = helper.absolutePos(new BlockPos(1, 2, 1));
        helper.setBlock(new BlockPos(3, 3, 2), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 3, 1), ModTravel.DAEMON_PANEL.get().defaultBlockState().setValue(DaemonPanelBlock.FACING, Direction.NORTH));
        check(helper, door.equals(DaemonPanelBlock.door(level, helper.absolutePos(new BlockPos(3, 3, 1)))), "the panel is the door's");
        check(helper, DaemonPanelBlock.door(level, helper.absolutePos(new BlockPos(4, 3, 1))) == null, "three across is too far");
        level.destroyBlock(door, false);
        check(helper, DaemonPanelBlock.door(level, helper.absolutePos(new BlockPos(3, 3, 1))) == null, "with the door gone, it has none");
        forgetAllDoors(level);
        helper.succeed();
    }

    /**
     * Walked into, as the game checks a player's blocks: one position handed to each block in turn, moved on from the
     * door's lower half to its upper half and past it. The trip is made after that check, at the door, not wherever the
     * position has got to by then (it once was the upper half, and the door took nobody anywhere, silently).
     */
    private static void walkedInto(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        stepIn(level, player, home, true);
        BlockPos.MutableBlockPos cursor = home.mutable();
        for (int i = 0; i < 2; i++, cursor.move(Direction.UP)) {
            level.getBlockState(cursor).entityInside(level, cursor, player, InsideBlockEffectApplier.NOOP, true);
        }
        cursor.move(Direction.UP, 3);
        helper.runAfterDelay(5, () -> {
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            BlockPos there = DaemonDoors.get(level.getServer()).doorIn(Level.NETHER);
            check(helper, player.level() == nether, "through to the Nether: " + player.level().dimension());
            if (there != null) {
                nether.removeBlock(there.above(), false);
                nether.removeBlock(there, false);
            }
            forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /**
     * Through the open home door to the Nether, where a return door is set down and the player stands in front of it;
     * through that back home; and, with the home door gone, through it to the respawn point.
     */
    private static void thereAndBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        DaemonTravel.through(player, home);
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        BlockPos there = DaemonDoors.get(level.getServer()).doorIn(Level.NETHER);
        check(helper, player.level() == nether, "through to the Nether: " + player.level().dimension());
        check(helper, there != null && DaemonDoors.standsAt(nether, there), "a return door set down there: " + there);
        check(helper, nether.getBlockState(there).getValue(DoorBlock.OPEN), "standing open");
        Direction facing = nether.getBlockState(there).getValue(DoorBlock.FACING);
        check(helper, DaemonDoorBlock.framed(nether, there, facing), "set into a wall");
        check(helper, nether.getBlockState(there.above(2)).is(Blocks.STONE_BRICKS) || nether.getBlockState(there.above(2)).isCollisionShapeFullBlock(nether, there.above(2)),
                "its lintel stone bricks (or what was there): " + nether.getBlockState(there.above(2)));
        check(helper, beside(nether, there, player), "the player in front of it, not in it: "
                + player.blockPosition() + " / " + there);
        check(helper, Math.abs(there.getX() - home.getX() / 8) <= 17 && Math.abs(there.getZ() - home.getZ() / 8) <= 17,
                "where a portal would come out: " + there + " for " + home);
        DaemonTravel.through(player, there);
        check(helper, player.level() == nether, "not straight back: the door pauses after a trip");
        helper.runAfterDelay(45, () -> { // past the door's pause after a trip
            DaemonTravel.through(player, there);
            check(helper, player.level() == level && beside(level, home, player),
                    "back home, in front of the home door: " + player.level().dimension() + " " + player.blockPosition() + " / " + home);
            level.destroyBlock(home, false);
            player.teleport(new TeleportTransition(nether, Vec3.atBottomCenterOf(there.north()), Vec3.ZERO, 0, 0, TeleportTransition.DO_NOTHING));
            helper.runAfterDelay(45, () -> {
                DaemonTravel.through(player, there);
                check(helper, player.level().dimension() == Level.OVERWORLD, "no home door: to the respawn point");
                nether.removeBlock(there.above(), false);
                nether.removeBlock(there, false);
                forgetAllDoors(level);
                player.connection.disconnect(Component.empty());
                helper.succeed();
            });
        });
    }

    /**
     * A return door set down where tall grass grows stands whole: set down half by half, the grass's top, losing its foot
     * to the door's lower half, went and took that half with it (a door's lower half goes without its top), leaving
     * half a door - as the If's mirror was left (see IfTests).
     */
    private static void inTallGrass(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos spot = helper.absolutePos(new BlockPos(3, 40, 3)); // in the sky over the arena, out of anyone's way
        BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 3, 3)).forEach(cell -> level.setBlock(cell,
                (cell.getY() < spot.getY() ? Blocks.GRASS_BLOCK : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS));
        BlockState grass = Blocks.TALL_GRASS.defaultBlockState();
        level.setBlock(spot, grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        level.setBlock(spot.above(), grass.setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
        check(helper, level.getBlockState(spot).is(Blocks.TALL_GRASS) && level.getBlockState(spot.above()).is(Blocks.TALL_GRASS), "tall grass grows there");
        BlockPos door = DaemonTravel.setDown(level, spot, false);
        try {
            check(helper, spot.equals(door) && DaemonDoors.standsAt(level, spot) && level.getBlockState(spot.above()).is(ModTravel.DAEMON_DOOR.get()),
                    "the door stands whole in the grass's place: " + door + ", " + level.getBlockState(spot) + " under " + level.getBlockState(spot.above()));
        } finally {
            BlockPos.betweenClosed(spot.offset(-3, -1, -3), spot.offset(3, 3, 3)).forEach(cell -> level.setBlock(cell,
                    Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS));
        }
        helper.succeed();
    }

    /**
     * Asked for under the Nether's lava sea (where a player who sank in it last was), a return door comes out dry: its
     * cell and the one in front of it free of anything that flows, on a floor that holds.
     */
    private static void outOfLava(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        BlockPos under = new BlockPos(48, 20, 48);
        BlockPos door = DaemonTravel.setDown(nether, under, false);
        check(helper, door != null && DaemonDoors.standsAt(nether, door), "a door set down: " + door);
        BlockPos front = door.relative(nether.getBlockState(door).getValue(DoorBlock.FACING).getOpposite());
        for (BlockPos cell : List.of(door, door.above(), front, front.above())) {
            check(helper, nether.getFluidState(cell).isEmpty(), "nothing flows at " + cell);
        }
        check(helper, nether.getBlockState(front.below()).isFaceSturdy(nether, front.below(), Direction.UP), "a floor in front of it");
        check(helper, DaemonDoorBlock.framed(nether, door, nether.getBlockState(door).getValue(DoorBlock.FACING)), "set into a wall");
        nether.removeBlock(door.above(), false);
        nether.removeBlock(door, false);
        helper.succeed();
    }

    /**
     * A return door is set down only where its traveller may build: with the spot it would have taken claimed (a claim
     * mod refusing them a placing there), it goes to the next spot along, none of the blocks it changes in the claim.
     */
    private static void keepsOffClaims(GameTestHelper helper) {
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        ServerPlayer player = player(helper);
        BlockPos base = new BlockPos(200, 70, 200);
        BlockPos first = DaemonTravel.setDown(nether, base, false);
        check(helper, first != null, "a door set down where nobody asks: " + first);
        nether.removeBlock(first.above(), false);
        nether.removeBlock(first, false);
        var box = new net.minecraft.world.level.levelgen.structure.BoundingBox(first.getX() - 3, first.getY() - 3, first.getZ() - 3,
                first.getX() + 3, first.getY() + 4, first.getZ() + 3);
        java.util.Map<BlockPos, BlockState> before = new java.util.HashMap<>();
        BlockPos.betweenClosedStream(box).forEach(cell -> before.put(cell.immutable(), nether.getBlockState(cell)));
        claim(box, player);
        try {
            BlockPos second = DaemonTravel.setDown(nether, base, false, DaemonTravel.DOOR, player);
            check(helper, second != null, "set down again, the spot claimed: " + second);
            check(helper, !box.isInside(second) && !box.isInside(second.above()), "not in the claim: " + second);
            before.forEach((cell, state) -> check(helper, nether.getBlockState(cell) == state, "nothing in the claim changed, at " + cell));
            nether.removeBlock(second.above(), false);
            nether.removeBlock(second, false);
        } finally {
            claimed = null;
            claimant = null;
            player.connection.disconnect(Component.empty());
        }
        helper.succeed();
    }

    /**
     * A return door standing out of any wall, on land the traveller may not build on, cannot be walled in for the trip:
     * nobody is sent to it (arrived, they would find an ordinary door and no way back). Let build there, the trip walls it
     * in and goes.
     */
    private static void noTripToAnUnframedDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(1, 1, 1));
        BlockPos home = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        BlockPos far = new BlockPos(300, 80, 300);
        for (BlockPos cell : BlockPos.betweenClosed(far.offset(-3, -1, -3), far.offset(3, 4, 3))) {
            nether.setBlockAndUpdate(cell, cell.getY() == far.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        BlockState lower = ModTravel.DAEMON_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER);
        nether.setBlock(far, lower, 3);
        nether.setBlock(far.above(), lower.setValue(DoorBlock.HALF, net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        DaemonDoors.get(level.getServer()).record(Level.NETHER, far);
        check(helper, !DaemonDoorBlock.framed(nether, far, Direction.NORTH), "the far door stands out of any wall");
        claim(new net.minecraft.world.level.levelgen.structure.BoundingBox(far.getX() - 2, far.getY() - 2, far.getZ() - 2,
                far.getX() + 2, far.getY() + 3, far.getZ() + 2), player);
        try {
            DaemonTravel.through(player, home);
            check(helper, player.level() == level, "not sent: " + player.level().dimension());
            check(helper, !DaemonDoorBlock.framed(nether, far, Direction.NORTH), "and nothing built on the claim");
        } finally {
            claimed = null;
            claimant = null;
        }
        helper.runAfterDelay(45, () -> { // past the door's pause
            DaemonTravel.through(player, home);
            check(helper, player.level() == nether, "the land free, sent: " + player.level().dimension());
            check(helper, DaemonDoorBlock.framed(nether, far, Direction.NORTH), "the far door walled in for the trip");
            nether.removeBlock(far.above(), false);
            nether.removeBlock(far, false);
            forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /**
     * The End's first door stands on a ledge joined level to the obsidian platform's east edge, a rim round the ledge's
     * open sides, its doorway towards the platform: one comes out facing the platform, not at the edge of the void.
     * With something built where the ledge would be (a chest), the ledge is not made over it: the door goes elsewhere,
     * off the platform, and the chest stays.
     */
    private static void endLanding(GameTestHelper helper) {
        forgetAllDoors(helper.getLevel());
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        BlockPos door = DaemonTravel.endLanding(end);
        check(helper, DaemonDoors.standsAt(end, door), "a door stands: " + door);
        Direction facing = end.getBlockState(door).getValue(DoorBlock.FACING);
        BlockPos front = door.relative(facing.getOpposite());
        check(helper, facing.getOpposite() == Direction.WEST, "its doorway towards the platform, west: " + facing);
        check(helper, DaemonTravel.standingRoom(end, door, facing.getOpposite()).equals(front) && DaemonTravel.standable(end, front),
                "one comes out in front of it, on its floor");
        int y = ServerLevel.END_SPAWN_POINT.getY() - 1;
        check(helper, front.getY() == y && end.getBlockState(new BlockPos(ServerLevel.END_SPAWN_POINT.getX() + 2, y - 1, 0)).is(Blocks.OBSIDIAN),
                "level with the platform, whose floor it joins");
        for (int x = door.getX() - 3; x <= door.getX() + 1; x++) {
            for (int z : new int[] {-2, 2}) {
                check(helper, end.getBlockState(new BlockPos(x, y, z)).is(Blocks.STONE_BRICKS), "a rim along its side at " + x + ", " + z);
            }
        }
        check(helper, end.getBlockState(door.east()).is(Blocks.STONE_BRICKS), "and behind the door");
        check(helper, DaemonDoorBlock.framed(end, door, facing) && end.getBlockState(door.above(2)).is(Blocks.STONE_BRICKS),
                "set into a frame of stone bricks");
        end.removeBlock(door.above(), false);
        end.removeBlock(door, false);
        // the ledge's door taken up, its frame left: the next first door goes on the ledge again, facing the platform
        BlockPos again = DaemonTravel.endLanding(end);
        check(helper, door.equals(again) && end.getBlockState(again).getValue(DoorBlock.FACING).getOpposite() == Direction.WEST,
                "on the ledge again, where the first one stood: " + again + " / " + door);
        end.removeBlock(again.above(), false);
        end.removeBlock(again, false);
        // a chest in that door's frame (beside its doorway): the door does not frame over it, and goes elsewhere
        BlockPos inFrame = door.north();
        end.setBlockAndUpdate(inFrame, Blocks.CHEST.defaultBlockState());
        BlockPos besideIt = DaemonTravel.endLanding(end);
        check(helper, end.getBlockState(inFrame).is(Blocks.CHEST), "the chest in the frame stays");
        check(helper, besideIt != null && !besideIt.equals(door), "the door set down elsewhere: " + besideIt);
        end.removeBlock(besideIt.above(), false);
        end.removeBlock(besideIt, false);
        end.removeBlock(inFrame, false);
        BlockPos chest = new BlockPos(ServerLevel.END_SPAWN_POINT.getX() + 5, y + 1, 1);
        end.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        BlockPos elsewhere = DaemonTravel.endLanding(end);
        check(helper, end.getBlockState(chest).is(Blocks.CHEST), "a chest where the ledge's room would be stays");
        check(helper, elsewhere != null && DaemonDoors.standsAt(end, elsewhere) && !elsewhere.equals(door), "the door set down elsewhere: " + elsewhere);
        check(helper, Math.abs(elsewhere.getX() - ServerLevel.END_SPAWN_POINT.getX()) > 2 || Math.abs(elsewhere.getZ() - ServerLevel.END_SPAWN_POINT.getZ()) > 2
                || elsewhere.getY() > y + 3, "not on the platform, which is cleared each time someone comes through: " + elsewhere);
        end.removeBlock(elsewhere.above(), false);
        end.removeBlock(elsewhere, false);
        end.removeBlock(chest, false);
        forgetAllDoors(helper.getLevel());
        helper.succeed();
    }

    /**
     * Through a door whose front has lost its floor - a ghast's blast through a Nether door's, the lava sea far below -
     * one comes out behind it, on the floor there, not into what is under the front. (A step down, off a pillar, is still
     * in front: the other door tests stand theirs on a block.)
     */
    private static void comesOutOnAFloor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int x = 1; x <= 5; x++) {
            for (int z = 1; z <= 5; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        BlockPos at = new BlockPos(3, 2, 3);
        for (int down = 1; down <= 6; down++) {
            helper.setBlock(at.north().below(down), Blocks.AIR); // the front's floor gone, and a long way down under it
        }
        BlockPos stand = DaemonTravel.standingRoom(level, helper.absolutePos(at), Direction.NORTH);
        check(helper, helper.absolutePos(at.south()).equals(stand), "out behind it, on the floor: " + stand);
        helper.succeed();
    }

    /**
     * Only set into a wall does a door go anywhere: with a corner of its frame a slab it is a door like any other, and
     * any full block - glass too - makes it a gate again.
     */
    private static void onlyInAWall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        Direction facing = shut.getValue(DoorBlock.FACING);
        check(helper, DaemonDoorBlock.framed(level, home, facing), "walled in");
        BlockPos corner = DaemonDoorBlock.frame(home, facing).get(0);
        level.setBlockAndUpdate(corner, Blocks.STONE_SLAB.defaultBlockState());
        check(helper, !DaemonDoorBlock.framed(level, home, facing), "a slab in a corner is a gap");
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        DaemonTravel.through(player, home);
        check(helper, player.level() == level, "unframed, it takes no one anywhere");
        level.setBlockAndUpdate(corner, Blocks.GLASS.defaultBlockState());
        check(helper, DaemonDoorBlock.framed(level, home, facing), "glass is a full block");
        helper.runAfterDelay(45, () -> { // past the pause the first try started
            DaemonTravel.through(player, home);
            check(helper, player.level().dimension() == Level.NETHER, "set into a wall, through: " + player.level().dimension());
            BlockPos there = DaemonDoors.get(level.getServer()).doorIn(Level.NETHER);
            ServerLevel nether = level.getServer().getLevel(Level.NETHER);
            if (there != null) {
                nether.removeBlock(there.above(), false);
                nether.removeBlock(there, false);
            }
            forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /** Walked into from behind, an open door set into a wall is a doorway like any other: the player stays. */
    private static void fromBehind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        stepIn(level, player, home, false);
        level.getBlockState(home).entityInside(level, home, player, InsideBlockEffectApplier.NOOP, true);
        helper.runAfterDelay(5, () -> {
            check(helper, player.level() == level, "from behind it is a door: " + player.level().dimension());
            forgetAllDoors(level);
            helper.succeed();
        });
    }

    /** With its panel set off, the home door is a door like any other: walked into, it takes the player nowhere, and says nothing. */
    private static void setOff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.empty()));
        DaemonTravel.through(player, home);
        check(helper, player.level() == level, "off, it takes no one anywhere");
        check(helper, DaemonDoors.get(level.getServer()).doorIn(Level.NETHER) == null, "and sets no door down anywhere");
        forgetAllDoors(level);
        helper.succeed();
    }

    /**
     * Whose gate a door shows: the other world's, and on the way back to the overworld the one being left - so the Nether's
     * door is the Nether's swirl both ways, as vanilla's portals are.
     */
    private static void gates(GameTestHelper helper) {
        check(helper, DaemonDestinations.gate(Level.OVERWORLD, Level.NETHER).equals(Level.NETHER), "out to the Nether: its gate");
        check(helper, DaemonDestinations.gate(Level.NETHER, Level.OVERWORLD).equals(Level.NETHER), "back from it: the same");
        check(helper, DaemonDestinations.gate(Level.OVERWORLD, Level.END).equals(Level.END), "the End's");
        check(helper, DaemonDestinations.gate(IfWorld.KEY, Level.OVERWORLD).equals(IfWorld.KEY), "home from the If: the mirror");
        check(helper, DaemonDestinations.gate(Level.NETHER, Level.END).equals(Level.END), "from one world to another: the far one's");
        helper.succeed();
    }

    /**
     * Another mod's dimension goes by its id made readable, by its namespace where the path names no place (LIMBO's
     * {@code limbo:dimension}); its window and swirl take the fog and sky its dimension type was sent with.
     */
    private static void otherModsWorlds(GameTestHelper helper) {
        check(helper, DaemonDestinations.name(dimension("deeper_dark", "deeper_dark")).getString().equals("Deeper Dark"), "an id made readable");
        String limbo = DaemonDestinations.name(dimension("limbo", "dimension")).getString();
        check(helper, limbo.equals("Limbo"), "a path that names no place: the namespace, not " + limbo);
        check(helper, DaemonDestinations.name(dimension("somemod", "realms/glade")).getString().equals("Glade"), "the path's last part");
        // vanilla's End type stands in for a mod's: its fog (#181318) and black sky
        List<Integer> end = DaemonDestinations.colours(Level.END, helper.getLevel().registryAccess()).air();
        check(helper, end.contains(0x181318) && end.contains(0x000000), "the End type's fog and sky: " + end);
        var nowhere = DaemonDestinations.colours(dimension("nomod", "nowhere"), helper.getLevel().registryAccess());
        check(helper, nowhere.air().isEmpty() && nowhere.water().isEmpty(), "nothing sent of a world the client does not know");
        // Deeper Dark, as it is sent: a black-teal fog, grey biome fog, near-black red skies (#230000, #0a0500, their mean),
        // a deep blue water fog and a blue sea - its swirl takes the sea's blue, from near black to a mid blue (it came out
        // grey, then rust, r7)
        var deeper = DaemonDestinations.lookOf(new DaemonDestinations.Sent(List.of(0x000A0A, 0x6A6968, 0x160200),
                List.of(0x050533, 0x264582)), 0);
        check(helper, blueish(deeper.bottom()) && blueish(deeper.top()) && luma(deeper.top()) < 50 && luma(deeper.bottom()) > 70,
                "Deeper Dark's swirl dark to mid blue: " + Integer.toHexString(deeper.top()) + " / " + Integer.toHexString(deeper.bottom()));
        // Limbo: a grey-purple fog and sky over grey biomes - purple, not grey (it came out barely tinted)
        var limboLook = DaemonDestinations.lookOf(new DaemonDestinations.Sent(List.of(0x504B64, 0x504B64, 0x646464), List.of(0x2D2D37)), 0);
        check(helper, net.minecraft.util.ARGB.blue(limboLook.bottom()) - net.minecraft.util.ARGB.green(limboLook.bottom()) > 25,
                "Limbo's swirl lilac: " + Integer.toHexString(limboLook.bottom()));
        // and a grey place stays grey
        var grey = DaemonDestinations.lookOf(new DaemonDestinations.Sent(List.of(0x404040, 0xA0A0A0), List.of()), 0);
        check(helper, net.minecraft.util.ARGB.red(grey.bottom()) == net.minecraft.util.ARGB.blue(grey.bottom()), "a grey place's swirl grey");
        helper.succeed();
    }

    private static boolean blueish(int colour) {
        return net.minecraft.util.ARGB.blue(colour) > net.minecraft.util.ARGB.red(colour) + 15
                && net.minecraft.util.ARGB.blue(colour) > net.minecraft.util.ARGB.green(colour);
    }

    private static int luma(int colour) {
        return (net.minecraft.util.ARGB.red(colour) * 299 + net.minecraft.util.ARGB.green(colour) * 587
                + net.minecraft.util.ARGB.blue(colour) * 114) / 1000;
    }

    private static ResourceKey<Level> dimension(String namespace, String path) {
        return ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(namespace, path));
    }

    /**
     * A return door standing out of any wall (set down before doors had to be in one) is framed in stone bricks when
     * someone comes through to it, or they would find no way back.
     */
    private static void bareReturnDoor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        BlockPos bare = DaemonTravel.setDown(nether, new BlockPos(64, 70, 64), false, (where, lower, out) -> {
            BlockState door = ModTravel.DAEMON_DOOR.get().defaultBlockState().setValue(DoorBlock.FACING, out.getOpposite())
                    .setValue(DoorBlock.OPEN, true);
            where.setBlock(lower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
            where.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        });
        check(helper, bare != null, "a bare door set down in the Nether");
        DaemonDoors.get(level.getServer()).record(Level.NETHER, bare);
        Direction facing = nether.getBlockState(bare).getValue(DoorBlock.FACING);
        check(helper, !DaemonDoorBlock.framed(nether, bare, facing), "standing in no wall");
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        DaemonTravel.through(player, home);
        check(helper, player.level() == nether, "through to it");
        check(helper, DaemonDoorBlock.framed(nether, bare, facing), "and it is walled in now");
        nether.removeBlock(bare.above(), false);
        nether.removeBlock(bare, false);
        forgetAllDoors(level);
        player.connection.disconnect(Component.empty());
        helper.succeed();
    }

    /**
     * A trip sets a dimension's door down free only the first time: one taken up since (and so in someone's pocket, or
     * gone) is not made again from nothing, which would make doors without end. Going through with a door in one's
     * pocket sets it down there, and the pocket is a door lighter; without one, the trip says so and goes nowhere.
     */
    private static void fromThePocket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        @SuppressWarnings("removal")
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL); // a creative player sets one down regardless
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        DaemonDoors doors = DaemonDoors.get(level.getServer());
        ServerLevel nether = level.getServer().getLevel(Level.NETHER);
        doors.record(Level.NETHER, BlockPos.ZERO); // a door stood there once, and was taken up
        doors.forget(Level.NETHER, BlockPos.ZERO);
        BlockState shut = level.getBlockState(home);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        player.setData(TravelLog.TYPE, TravelLog.of(player).withVisit(Level.NETHER).withDestination(Optional.of(Level.NETHER)));
        DaemonTravel.through(player, home);
        check(helper, player.level() == level && doors.doorIn(Level.NETHER) == null, "no door in the pocket: nowhere, and none made");
        player.getInventory().setItem(9, new ItemStack(ModTravel.DAEMON_DOOR_ITEM.get(), 2));
        helper.runAfterDelay(45, () -> { // past the pause the first try started
            DaemonTravel.through(player, home);
            BlockPos there = doors.doorIn(Level.NETHER);
            check(helper, player.level() == nether && there != null && DaemonDoors.standsAt(nether, there), "with one, through, and it stands there");
            check(helper, player.getInventory().getItem(9).getCount() == 1, "from the pocket: " + player.getInventory().getItem(9));
            nether.removeBlock(there.above(), false);
            nether.removeBlock(there, false);
            forgetAllDoors(level);
            player.connection.disconnect(Component.empty());
            helper.succeed();
        });
    }

    /**
     * A sliding door: shut, its leaf stands out in front of the wall and is in the way; open, the doorway is clear (a click
     * still finds it). A Destination Panel put up beside it on the side it slides to turns it to slide the other way.
     */
    private static void slidesAwayFromItsPanel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        forgetAllDoors(level);
        ServerPlayer player = player(helper);
        placeDoor(helper, player, new BlockPos(2, 1, 2));
        BlockPos home = helper.absolutePos(new BlockPos(2, 2, 2));
        BlockState shut = level.getBlockState(home);
        Direction front = DaemonDoorBlock.front(shut);
        var leaf = shut.getCollisionShape(level, home);
        check(helper, !leaf.isEmpty(), "shut, its leaf is in the way");
        double out = front.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? -leaf.min(front.getAxis()) : leaf.max(front.getAxis()) - 1;
        check(helper, out > 0.1, "standing out in front of the wall: " + out);
        ((DoorBlock) shut.getBlock()).setOpen(null, level, shut, home, true);
        BlockState open = level.getBlockState(home);
        check(helper, open.getCollisionShape(level, home).isEmpty(), "open, the doorway is clear");
        check(helper, !open.getShape(level, home).isEmpty(), "and a click on it still finds it, to shut it");
        Direction side = DaemonDoorBlock.slideSide(open);
        BlockPos wall = home.relative(side).above(); // a frame block beside the upper half
        ItemStack panel = new ItemStack(ModTravel.DAEMON_PANEL_ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, panel);
        panel.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(wall).relative(front, 0.5), front, wall, false)));
        check(helper, level.getBlockState(wall.relative(front)).getBlock() instanceof DaemonPanelBlock, "the panel is up beside it");
        check(helper, DaemonDoorBlock.slideSide(level.getBlockState(home)) == side.getOpposite(), "and the door slides the other way now");
        check(helper, DaemonDoorBlock.slideSide(level.getBlockState(home.above())) == side.getOpposite(), "both halves");
        forgetAllDoors(level);
        helper.succeed();
    }

    /** Standing in the doorway's cell before the door at {@code lower}, or behind it should the front be shut: not in it. */
    private static boolean beside(ServerLevel level, BlockPos lower, ServerPlayer player) {
        Direction facing = level.getBlockState(lower).getValue(DoorBlock.FACING);
        BlockPos at = player.blockPosition();
        return at.equals(lower.relative(facing)) || at.equals(lower.relative(facing.getOpposite()));
    }

    private TravelTests() {}
}
