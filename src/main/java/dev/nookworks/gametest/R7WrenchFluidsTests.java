package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.HashSet;
import java.util.Set;

import dev.nookworks.experience.Move;
import dev.nookworks.fluid.CopperFittingBlock;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.ValveBlock;
import dev.nookworks.item.WrenchItem;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeKnotEntity;
import dev.nookworks.rope.RopeSpans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * r7 review: the Wrench and what it turns - a facing that can point up or down, stairs and gates fitted to their
 * neighbours, a Sight Glass or Valve joined up anew, a valve a pipe is set against, a rope's knot on a turned post.
 */
final class R7WrenchFluidsTests {
    private R7WrenchFluidsTests() {}

    static void register() {
        NookworksGameTests.test("wrench_turns_a_block_back_upright", R7WrenchFluidsTests::turnsBackUpright);
        NookworksGameTests.test("wrench_fits_a_turned_stair_and_gate_to_their_neighbours", R7WrenchFluidsTests::fitsStairAndGate);
        NookworksGameTests.test("a_wrenched_valve_joins_the_line_it_is_turned_into", R7WrenchFluidsTests::valveJoinsItsLine);
        NookworksGameTests.test("a_tank_stops_pouring_into_a_sight_glass_turned_on_its_side", R7WrenchFluidsTests::tankStopsPouring);
        NookworksGameTests.test("a_pipe_goes_on_a_valve_and_leaves_it_open", R7WrenchFluidsTests::pipeGoesOnAValve);
        NookworksGameTests.test("a_rope_knot_and_what_hangs_follow_a_turned_post", R7WrenchFluidsTests::knotFollowsATurnedPost);
    }

    /** A player in the level, with a connection to send it messages, standing clear of the test. */
    private static ServerPlayer clicker(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.inLevel(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(0.5, 8, 0.5));
        player.snapTo(aside.x, aside.y, aside.z);
        return player;
    }

    /** Right-clicks the middle of {@code face} of {@code pos} holding {@code stack}, as the game makes a click. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack stack, BlockPos pos, Direction face) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos at = helper.absolutePos(pos);
        Vec3 point = Vec3.atCenterOf(at).relative(face, 0.5);
        player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, new BlockHitResult(point, face, at, false));
    }

    /** Sets the blocks, then works out each one's joins from its neighbours, as placing them one by one would. */
    private static void lay(GameTestHelper helper, BlockPos[] positions, BlockState[] states) {
        for (int i = 0; i < positions.length; i++) {
            helper.setBlock(positions[i], states[i]);
        }
        for (BlockPos pos : positions) {
            BlockPos at = helper.absolutePos(pos);
            helper.getLevel().setBlock(at, Block.updateFromNeighbourShapes(helper.getLevel().getBlockState(at), helper.getLevel(), at), Block.UPDATE_ALL);
        }
    }

    private static BlockState fitting(Block block, Direction.Axis axis) {
        return block.defaultBlockState().setValue(CopperFittingBlock.AXIS, axis);
    }

    /**
     * A facing that can point up or down goes round every way and back to where it began, and sneaking undoes each
     * step: a log laid down stood up again, a hopper pointed down again (a quarter turn alone only went round the sides).
     */
    private static void turnsBackUpright(GameTestHelper helper) {
        goesRound(helper, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
                RotatedPillarBlock.AXIS, 3, "an upright log");
        goesRound(helper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), HopperBlock.FACING, 5,
                "a hopper pointing down");
        goesRound(helper, Blocks.END_ROD.defaultBlockState().setValue(RodBlock.FACING, Direction.UP), RodBlock.FACING, 6,
                "an end rod standing up");
        goesRound(helper, Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.UP), PistonBaseBlock.FACING, 6,
                "a piston facing up");
        goesRound(helper, fitting(ModBlocks.SIGHT_GLASS.get(), Direction.Axis.Y), CopperFittingBlock.AXIS, 3, "an upright sight glass");
        BlockState rod = Blocks.END_ROD.defaultBlockState().setValue(RodBlock.FACING, Direction.NORTH);
        check(helper, WrenchItem.turn(rod, false).getValue(RodBlock.FACING) == Direction.EAST,
                "round the sides it still turns a quarter turn clockwise");
        helper.succeed();
    }

    private static <T extends Comparable<T>> void goesRound(GameTestHelper helper, BlockState start, Property<T> property, int ways,
            String what) {
        Set<T> seen = new HashSet<>();
        BlockState state = start;
        for (int i = 0; i < ways; i++) {
            seen.add(state.getValue(property));
            BlockState next = WrenchItem.turn(state, false);
            check(helper, WrenchItem.turn(next, true) == state,
                    what + ": sneaking turns " + next.getValue(property) + " back to " + state.getValue(property));
            state = next;
        }
        check(helper, seen.size() == ways, what + " points every way in turn: " + seen);
        check(helper, state == start, what + " comes back round to where it began, not " + state.getValue(property));
    }

    /**
     * A stair turned away from the one it made a corner with is straight again, and a gate turned out of a wall stands
     * up again: each is fitted to its neighbours as it now lies, though no neighbour changed to tell it.
     */
    private static void fitsStairAndGate(GameTestHelper helper) {
        ServerPlayer player = clicker(helper);
        BlockPos stair = new BlockPos(2, 2, 3);
        helper.setBlock(stair, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        helper.setBlock(stair.north(), Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST));
        check(helper, helper.getBlockState(stair).getValue(StairBlock.SHAPE) == StairsShape.OUTER_RIGHT,
                "set against the other, the stair is a corner: " + helper.getBlockState(stair).getValue(StairBlock.SHAPE));
        click(helper, player, new ItemStack(ModItems.WRENCH.get()), stair, Direction.UP);
        check(helper, helper.getBlockState(stair).getValue(StairBlock.FACING) == Direction.EAST, "the stair turned");
        check(helper, helper.getBlockState(stair).getValue(StairBlock.SHAPE) == StairsShape.STRAIGHT,
                "away from the other, it is a straight stair again: " + helper.getBlockState(stair).getValue(StairBlock.SHAPE));

        BlockPos gate = new BlockPos(5, 2, 3);
        helper.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.NORTH));
        helper.setBlock(gate.west(), Blocks.COBBLESTONE_WALL);
        helper.setBlock(gate.east(), Blocks.COBBLESTONE_WALL);
        check(helper, helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "between walls, the gate sits low in them");
        click(helper, player, new ItemStack(ModItems.WRENCH.get()), gate, Direction.UP);
        check(helper, helper.getBlockState(gate).getValue(FenceGateBlock.FACING) == Direction.EAST, "the gate turned");
        check(helper, !helper.getBlockState(gate).getValue(FenceGateBlock.IN_WALL), "with no wall at its sides now, it stands at its full height");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A valve lying across a line, turned with the Wrench into it between a pipe and a tank, joins both and passes the
     * line on into the tank (its join to the tank was left as it was across the line, and nothing ever reached it).
     */
    private static void valveJoinsItsLine(GameTestHelper helper) {
        BlockPos tank = new BlockPos(1, 2, 3);
        BlockPos valve = tank.east();
        BlockPos pipe = valve.east();
        lay(helper, new BlockPos[] {tank, valve, pipe}, new BlockState[] {ModBlocks.COPPER_TANK.get().defaultBlockState(),
                fitting(ModBlocks.VALVE.get(), Direction.Axis.Z), ModBlocks.COPPER_PIPE.get().defaultBlockState()});
        check(helper, !helper.getBlockState(valve).getValue(PipeBlock.WEST) && !helper.getBlockState(valve).getValue(PipeBlock.EAST),
                "across the line, the valve joins neither");
        ServerPlayer player = clicker(helper);
        player.setShiftKeyDown(true);
        click(helper, player, new ItemStack(ModItems.WRENCH.get()), valve, Direction.UP);
        BlockState turned = helper.getBlockState(valve);
        check(helper, turned.getValue(CopperFittingBlock.AXIS) == Direction.Axis.X, "the valve turned into the line: " + turned);
        check(helper, turned.getValue(PipeBlock.WEST) && turned.getValue(PipeBlock.EAST), "and joins the tank and the pipe: " + turned);
        helper.getLevel().getServer().getPlayerList().remove(player);
        var upstream = helper.getBlockEntity(pipe, CopperPipeBlockEntity.class);
        check(helper, Move.insert(upstream.handlerFor(null), FluidResource.of(Fluids.WATER), 1_000) == 1_000, "a bucket goes into the pipe");
        var sink = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        helper.succeedWhen(() -> check(helper, sink.amount() == 1_000, "all of it through the valve into the tank: " + sink.amount()));
    }

    /**
     * A Sight Glass standing under a tank, turned on its side with the Wrench, is no longer poured into: its join up to
     * the tank goes, and so does what the tank kept of it (the tank went on pouring into its side, and the water ran out
     * along the glass into the pipes beside it).
     */
    private static void tankStopsPouring(GameTestHelper helper) {
        BlockPos glass = new BlockPos(3, 2, 3);
        BlockPos tank = glass.above();
        lay(helper, new BlockPos[] {tank, glass, glass.north(), glass.east(), glass.south(), glass.west()}, new BlockState[] {
                ModBlocks.COPPER_TANK.get().defaultBlockState(), fitting(ModBlocks.SIGHT_GLASS.get(), Direction.Axis.Y),
                ModBlocks.COPPER_PIPE.get().defaultBlockState(), ModBlocks.COPPER_PIPE.get().defaultBlockState(),
                ModBlocks.COPPER_PIPE.get().defaultBlockState(), ModBlocks.COPPER_PIPE.get().defaultBlockState()});
        check(helper, helper.getBlockState(glass).getValue(PipeBlock.UP), "standing under the tank, the glass joins it");
        var source = helper.getBlockEntity(tank, CopperTankBlockEntity.class);
        source.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 8_000);
        int[] before = new int[1];
        helper.runAfterDelay(10, () -> {
            before[0] = source.amount();
            check(helper, before[0] < 8_000, "the tank fills the glass under it: " + before[0]);
            ServerPlayer player = clicker(helper);
            player.setShiftKeyDown(true);
            click(helper, player, new ItemStack(ModItems.WRENCH.get()), glass, Direction.UP);
            helper.getLevel().getServer().getPlayerList().remove(player);
            BlockState turned = helper.getBlockState(glass);
            check(helper, turned.getValue(CopperFittingBlock.AXIS) != Direction.Axis.Y, "the glass is on its side: " + turned);
            check(helper, !turned.getValue(PipeBlock.UP), "and no longer joins the tank above it: " + turned);
        });
        helper.runAfterDelay(50, () -> {
            check(helper, source.amount() == before[0], "the tank pours no more into its side: " + before[0] + " then " + source.amount());
            helper.succeed();
        });
    }

    /** A pipe clicked onto a valve's free end is set there, and the valve stays open; an empty hand still turns it. */
    private static void pipeGoesOnAValve(GameTestHelper helper) {
        BlockPos valve = new BlockPos(2, 2, 3);
        lay(helper, new BlockPos[] {valve}, new BlockState[] {fitting(ModBlocks.VALVE.get(), Direction.Axis.X)});
        ServerPlayer player = clicker(helper);
        click(helper, player, new ItemStack(ModBlocks.COPPER_PIPE.get()), valve, Direction.EAST);
        check(helper, helper.getBlockState(valve.east()).is(ModBlocks.COPPER_PIPE.get()), "the pipe goes on the valve's end: "
                + helper.getBlockState(valve.east()));
        check(helper, helper.getBlockState(valve).getValue(ValveBlock.OPEN), "and the valve stays open");
        click(helper, player, ItemStack.EMPTY, valve, Direction.UP);
        check(helper, !helper.getBlockState(valve).getValue(ValveBlock.OPEN), "an empty hand still shuts it");
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * An end rod a rope is tied to, turned with the Wrench: the rope is tied where the rod now has it, and the knot and
     * the fish hung on the rope go there too, instead of staying in the air where they were.
     */
    private static void knotFollowsATurnedPost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = MockPlayers.inLevel(helper, GameType.SURVIVAL);
        Vec3 standing = helper.absoluteVec(new Vec3(3.5, 1, 4.5));
        player.snapTo(standing.x, standing.y, standing.z);
        ItemStack rope = new ItemStack(ModItems.THIN_ROPE.get(), 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, rope);
        BlockPos rodAt = new BlockPos(1, 2, 2);
        BlockPos postAt = new BlockPos(5, 2, 2);
        helper.setBlock(rodAt, Blocks.END_ROD.defaultBlockState().setValue(RodBlock.FACING, Direction.NORTH));
        helper.setBlock(postAt, Blocks.OAK_FENCE);
        BlockPos rod = helper.absolutePos(rodAt);
        BlockPos post = helper.absolutePos(postAt);
        check(helper, RopeSpans.tie(level, player, rod, rope) == InteractionResult.SUCCESS
                && RopeSpans.tie(level, player, post, rope) == InteractionResult.SUCCESS, "a rope strung from the end rod to the post");
        HungItemEntity fish = HungItemEntity.onSpan(level, rod, post, 0.5, new ItemStack(Items.COD));
        level.addFreshEntity(fish);
        RopeKnotEntity knot = RopeKnotEntity.at(level, rod);
        Vec3 tiedBefore = RopeSpans.tie(level, rod);
        check(helper, knot != null && knot.position().distanceTo(tiedBefore) < 1.0E-6, "its knot on the rod");

        click(helper, player, new ItemStack(ModItems.WRENCH.get()), rodAt, Direction.UP);
        check(helper, level.getBlockState(rod).getValue(RodBlock.FACING) == Direction.EAST, "the rod turned");
        check(helper, RopeSpans.tie(level, rod).distanceTo(tiedBefore) > 0.25, "and has the rope elsewhere on it now");
        helper.runAfterDelay(45, () -> {
            Vec3 tied = RopeSpans.tie(level, rod);
            check(helper, !knot.isRemoved() && knot.position().distanceTo(tied) < 1.0E-6,
                    "the knot went round with the rod: at " + knot.position() + ", the rope at " + tied);
            Vec3 point = fish.hangPoint();
            Vec3 hangs = new Vec3(point.x, point.y - HungItemEntity.STRING - fish.getBbHeight(), point.z);
            check(helper, !fish.isRemoved() && fish.position().distanceTo(hangs) < 1.0E-6,
                    "the fish hangs from the rope where it runs now: at " + fish.position() + ", under " + point);
            Vec3 to = RopeSpans.tie(level, post);
            double along = Math.toDegrees(Math.atan2(to.z - tied.z, to.x - tied.x));
            check(helper, Math.abs(Mth.wrapDegrees(fish.yaw() - along)) < 0.01, "side on to it: " + fish.yaw() + ", the rope " + along);
            helper.succeed();
        });
    }
}
