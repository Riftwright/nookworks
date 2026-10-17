package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;

import java.util.ArrayList;
import java.util.List;

import dev.nookworks.block.PipeSides;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.ExperiencePipeBlockEntity;
import dev.nookworks.experience.ExperienceTankBlock;
import dev.nookworks.experience.ExperienceTankBlockEntity;
import dev.nookworks.experience.Move;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.experience.RepairBoxMenu;
import dev.nookworks.fluid.CopperPipeBlockEntity;
import dev.nookworks.fluid.CopperTankBlockEntity;
import dev.nookworks.fluid.FluidPipeBlockEntity;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.transfer.fluid.FluidResource;

/**
 * r9, the user's ask of 2026-10-06 (液体の処理で、パイプ関係に排出/搬入の区別がない): a pipe's side against a tank or machine
 * can be set to pull with the Wrench, so a line runs from one tank into another, or from an Experience Tank into a
 * Repair Box; and a Repair Box takes a bucket or bottle of Liquid Experience by hand.
 */
final class R9PipeTests {
    private R9PipeTests() {}

    static void register() {
        NookworksGameTests.test("a_pulling_copper_pipe_moves_one_tank_into_another", R9PipeTests::tankIntoTank);
        NookworksGameTests.test("a_pipe_never_pours_back_into_a_side_it_pulls_from", R9PipeTests::neverPoursBack);
        NookworksGameTests.test("a_pipe_side_goes_joined_pulling_off_and_old_pipes_pull_nowhere", R9PipeTests::sideCycles);
        NookworksGameTests.test("an_experience_tank_feeds_a_repair_box_through_a_pulling_pipe", R9PipeTests::tankFeedsRepairBox);
        NookworksGameTests.test("a_bucket_of_experience_pours_into_a_repair_box", R9PipeTests::bucketIntoRepairBox);
        NookworksGameTests.test("a_pipe_does_not_pull_what_it_does_not_carry", R9PipeTests::pullsOnlyWhatItCarries);
    }

    /** The tests that need Liquid Experience, which a pack or a world can switch off. */
    static final String[] NEED_LIQUID_EXPERIENCE = {"an_experience_tank_feeds_a_repair_box_through_a_pulling_pipe",
            "a_bucket_of_experience_pours_into_a_repair_box", "a_pipe_does_not_pull_what_it_does_not_carry"};

    // ---- helpers ----

    /** A player in the level, with a connection to send it messages, standing clear of the test. */
    private static ServerPlayer clicker(GameTestHelper helper, GameType mode) {
        ServerPlayer player = MockPlayers.inLevel(helper, mode);
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

    /** Sneak-clicks the Wrench on {@code side} of the pipe at {@code pos}: the arm on that side. */
    private static void wrench(GameTestHelper helper, ServerPlayer player, BlockPos pos, Direction side) {
        player.setShiftKeyDown(true);
        click(helper, player, new ItemStack(ModItems.WRENCH.get()), pos, side);
        player.setShiftKeyDown(false);
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

    private static BlockState copperPipe() {
        return ModBlocks.COPPER_PIPE.get().defaultBlockState();
    }

    private static BlockState experiencePipe() {
        return ModBlocks.EXPERIENCE_PIPE.get().defaultBlockState();
    }

    private static BlockState experienceTank() {
        return ModBlocks.EXPERIENCE_TANK.get().defaultBlockState().setValue(ExperienceTankBlock.FACING, Direction.SOUTH);
    }

    // ---- the tests ----

    /**
     * Tank A, three Copper Pipes, tank B in a row. A tank pushes nothing, so the line stands still - until the pipe's
     * side against A is set to pull with the Wrench: then A's water runs along into B, all of it, and the pipes are
     * left empty.
     */
    private static void tankIntoTank(GameTestHelper helper) {
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos first = a.east();
        BlockPos b = new BlockPos(5, 2, 3);
        lay(helper, new BlockPos[] {a, first, first.east(), first.east(2), b}, new BlockState[] {
                ModBlocks.COPPER_TANK.get().defaultBlockState(), copperPipe(), copperPipe(), copperPipe(),
                ModBlocks.COPPER_TANK.get().defaultBlockState()});
        var from = helper.getBlockEntity(a, CopperTankBlockEntity.class);
        var to = helper.getBlockEntity(b, CopperTankBlockEntity.class);
        from.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
        helper.runAfterDelay(10, () -> {
            check(helper, from.amount() == 4_000 && to.amount() == 0,
                    "with every side only joined, nothing draws the tank: " + from.amount() + " / " + to.amount());
            ServerPlayer player = clicker(helper, GameType.CREATIVE);
            wrench(helper, player, first, Direction.WEST);
            var pipe = helper.getBlockEntity(first, CopperPipeBlockEntity.class);
            check(helper, pipe.pullsFrom(Direction.WEST), "the first sneak-click on the side against the tank sets it to pull");
            check(helper, PipeSides.joined(helper.getBlockState(first), Direction.WEST), "a pulling side stays joined: its arm is there");
            helper.succeedWhen(() -> {
                check(helper, from.amount() == 0 && to.amount() == 4_000, "all of A into B: " + from.amount() + " / " + to.amount());
                for (int i = 0; i < 3; i++) {
                    var each = helper.getBlockEntity(first.east(i), CopperPipeBlockEntity.class);
                    check(helper, each.stored() == 0, "the line runs dry behind it: " + each.stored() + " in pipe " + i);
                }
            });
        });
    }

    /**
     * A pipe holding water, between an empty tank its side pulls from and a full one: the water stays in the pipe, never
     * poured into the tank it pulls from (which would take it), and that side lets nothing be drawn back out of the pipe.
     * Set back to joined - pulling, off, joined - the same pipe pours it into that tank at once: what kept it out was the
     * pulling side.
     */
    private static void neverPoursBack(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 2, 3);
        BlockPos pipePos = a.east();
        BlockPos b = pipePos.east();
        lay(helper, new BlockPos[] {a, pipePos, b}, new BlockState[] {ModBlocks.COPPER_TANK.get().defaultBlockState(), copperPipe(),
                ModBlocks.COPPER_TANK.get().defaultBlockState()});
        var source = helper.getBlockEntity(a, CopperTankBlockEntity.class);
        var full = helper.getBlockEntity(b, CopperTankBlockEntity.class);
        full.fluidHandler().set(0, FluidResource.of(Fluids.WATER), CopperTankBlockEntity.CAPACITY);
        ServerPlayer player = clicker(helper, GameType.CREATIVE);
        wrench(helper, player, pipePos, Direction.WEST);
        var pipe = helper.getBlockEntity(pipePos, CopperPipeBlockEntity.class);
        check(helper, pipe.pullsFrom(Direction.WEST), "the side against the empty tank pulls");
        check(helper, Move.insert(pipe.handlerFor(null), FluidResource.of(Fluids.WATER), 500) == 500, "half a bucket into the pipe");
        check(helper, Move.extract(pipe.handlerFor(Direction.WEST), FluidResource.of(Fluids.WATER), 100) == 0,
                "nothing is drawn back out through the pulling side");
        check(helper, Move.insert(pipe.handlerFor(Direction.WEST), FluidResource.of(Fluids.WATER), 100) == 100,
                "though it still takes in that way");
        helper.runAfterDelay(20, () -> {
            check(helper, source.amount() == 0 && pipe.stored() == 600,
                    "the pipe pours nothing into the tank it pulls from: " + source.amount() + " in the tank, " + pipe.stored() + " in the pipe");
            wrench(helper, player, pipePos, Direction.WEST); // off
            check(helper, PipeSides.isOff(helper.getBlockState(pipePos), Direction.WEST) && !pipe.pullsFrom(Direction.WEST),
                    "the second click switches the side off");
            wrench(helper, player, pipePos, Direction.WEST); // joined
            check(helper, !PipeSides.isOff(helper.getBlockState(pipePos), Direction.WEST) && !pipe.pullsFrom(Direction.WEST),
                    "the third joins it again, not pulling");
            helper.succeedWhen(() -> check(helper, source.amount() == 600 && pipe.stored() == 0,
                    "joined, it pours into the tank as before: " + source.amount()));
        });
    }

    /**
     * The sneak-click round: against a tank a side goes joined, pulling, off, joined; between two pipes it only goes off
     * and on, both pipes together, and never pulls. Jade reads a pulling side out. A pipe saved before pipes could pull
     * (no "Pulling" in its data) loads pulling from nowhere; one saved pulling loads so; and set down by a structure
     * turned a quarter, its pulling side turns with it.
     */
    private static void sideCycles(GameTestHelper helper) {
        BlockPos tank = new BlockPos(1, 2, 3);
        BlockPos pipePos = tank.east();
        BlockPos next = pipePos.east();
        lay(helper, new BlockPos[] {tank, pipePos, next}, new BlockState[] {ModBlocks.COPPER_TANK.get().defaultBlockState(),
                copperPipe(), copperPipe()});
        var pipe = helper.getBlockEntity(pipePos, CopperPipeBlockEntity.class);
        var other = helper.getBlockEntity(next, CopperPipeBlockEntity.class);
        ServerPlayer player = clicker(helper, GameType.SURVIVAL);
        for (Direction side : Direction.values()) {
            check(helper, !pipe.pullsFrom(side), "a pipe set down pulls from nowhere: " + side);
        }

        wrench(helper, player, pipePos, Direction.WEST);
        BlockState state = helper.getBlockState(pipePos);
        check(helper, pipe.pullsFrom(Direction.WEST) && state.getValue(PipeBlock.WEST) && !PipeSides.isOff(state, Direction.WEST),
                "first: pulling, still joined - " + state);
        List<Component> lines = new ArrayList<>();
        pipe.readout(lines::add);
        check(helper, lines.size() == 1 && lines.getFirst().getContents() instanceof TranslatableContents read
                && read.getKey().equals("nookworks.pipe.pulling"), "Jade reads out the pulling side: " + lines);
        wrench(helper, player, pipePos, Direction.WEST);
        state = helper.getBlockState(pipePos);
        check(helper, !pipe.pullsFrom(Direction.WEST) && PipeSides.isOff(state, Direction.WEST) && !state.getValue(PipeBlock.WEST),
                "second: off, its arm gone - " + state);
        wrench(helper, player, pipePos, Direction.WEST);
        state = helper.getBlockState(pipePos);
        check(helper, !pipe.pullsFrom(Direction.WEST) && !PipeSides.isOff(state, Direction.WEST) && state.getValue(PipeBlock.WEST),
                "third: joined again - " + state);

        wrench(helper, player, pipePos, Direction.EAST);
        check(helper, !pipe.pullsFrom(Direction.EAST) && PipeSides.isOff(helper.getBlockState(pipePos), Direction.EAST)
                && PipeSides.isOff(helper.getBlockState(next), Direction.WEST), "between two pipes the first click switches both off");
        wrench(helper, player, pipePos, Direction.EAST);
        check(helper, !pipe.pullsFrom(Direction.EAST) && !PipeSides.isOff(helper.getBlockState(pipePos), Direction.EAST)
                && helper.getBlockState(pipePos).getValue(PipeBlock.EAST) && !other.pullsFrom(Direction.WEST),
                "and the second joins them again: a side between pipes never pulls");

        // saved and loaded: a pipe from before pulling had no "Pulling" at all
        wrench(helper, player, pipePos, Direction.WEST);
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = pipe.saveWithoutMetadata(registries);
        check(helper, saved.contains("Pulling"), "a pulling side is saved: " + saved);
        CompoundTag old = saved.copy();
        old.remove("Pulling"); // as an older version saved it
        var oldPipe = new CopperPipeBlockEntity(helper.absolutePos(pipePos), helper.getBlockState(pipePos));
        oldPipe.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, old));
        var newPipe = new CopperPipeBlockEntity(helper.absolutePos(pipePos), helper.getBlockState(pipePos));
        newPipe.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        for (Direction side : Direction.values()) {
            check(helper, !oldPipe.pullsFrom(side), "an old pipe loads pulling from nowhere: " + side);
            check(helper, newPipe.pullsFrom(side) == (side == Direction.WEST), "one saved pulling loads so: " + side);
        }
        newPipe.applyStructureRotation(Mirror.NONE, Rotation.CLOCKWISE_90);
        check(helper, newPipe.pullsFrom(Direction.NORTH) && !newPipe.pullsFrom(Direction.WEST),
                "a structure turned a quarter clockwise turns its west side to the north");
        helper.succeed();
    }

    /**
     * An Experience Tank, two Experience Pipes, a Repair Box: the tank pushes nothing and the box only draws from what is
     * right beside it, so nothing moves - until the pipe's side against the tank pulls. Then the tank empties into the
     * box.
     */
    private static void tankFeedsRepairBox(GameTestHelper helper) {
        BlockPos tank = new BlockPos(1, 2, 3);
        BlockPos first = tank.east();
        BlockPos box = new BlockPos(4, 2, 3);
        lay(helper, new BlockPos[] {tank, first, first.east(), box}, new BlockState[] {experienceTank(), experiencePipe(),
                experiencePipe(), ModBlocks.REPAIR_BOX.get().defaultBlockState()});
        var source = helper.getBlockEntity(tank, ExperienceTankBlockEntity.class);
        var repair = helper.getBlockEntity(box, RepairBoxBlockEntity.class);
        source.fill(2_000);
        helper.runAfterDelay(25, () -> {
            check(helper, source.amount() == 2_000 && repair.experience() == 0,
                    "joined only, the tank keeps it: " + source.amount() + " / " + repair.experience());
            wrench(helper, clicker(helper, GameType.CREATIVE), first, Direction.WEST);
            check(helper, helper.getBlockEntity(first, ExperiencePipeBlockEntity.class).pullsFrom(Direction.WEST),
                    "the Experience Pipe's side against the tank pulls");
            helper.succeedWhen(() -> check(helper, source.amount() == 0 && repair.experience() == 2_000,
                    "the tank empties into the box: " + source.amount() + " / " + repair.experience()));
        });
    }

    /**
     * A bucket of Liquid Experience on a Repair Box pours its 1,000 mB in and an empty bucket comes back, in survival; a
     * bottle pours 250 mB and gives back a glass bottle. A box without room for a whole bucket takes none and the bucket
     * stays full; water is refused and kept; an empty bucket takes nothing out and opens the screen, as before.
     */
    private static void bucketIntoRepairBox(GameTestHelper helper) {
        BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ModBlocks.REPAIR_BOX.get().defaultBlockState());
        var box = helper.getBlockEntity(pos, RepairBoxBlockEntity.class);
        ServerPlayer player = clicker(helper, GameType.SURVIVAL);

        click(helper, player, new ItemStack(ModItems.EXPERIENCE_BUCKET.get()), pos, Direction.UP);
        check(helper, box.experience() == 1_000 && player.getMainHandItem().is(Items.BUCKET) && player.getMainHandItem().getCount() == 1,
                "a bucket pours 1,000 mB in and comes back empty: " + box.experience() + ", " + player.getMainHandItem());
        click(helper, player, new ItemStack(ModItems.LIQUID_EXPERIENCE_BOTTLE.get()), pos, Direction.UP);
        check(helper, box.experience() == 1_250 && player.getMainHandItem().is(Items.GLASS_BOTTLE),
                "a bottle pours 250 mB in and comes back empty: " + box.experience() + ", " + player.getMainHandItem());

        Move.insert(box.fluidHandler(), FluidResource.of(ExperienceFluids.SOURCE.get()), 3_500 - box.experience());
        click(helper, player, new ItemStack(ModItems.EXPERIENCE_BUCKET.get()), pos, Direction.UP);
        check(helper, box.experience() == 3_500 && player.getMainHandItem().is(ModItems.EXPERIENCE_BUCKET.get()),
                "no room for a whole bucket: none goes in and the bucket stays full - " + box.experience() + ", " + player.getMainHandItem());

        click(helper, player, new ItemStack(Items.WATER_BUCKET), pos, Direction.UP);
        check(helper, box.experience() == 3_500 && player.getMainHandItem().is(Items.WATER_BUCKET)
                && !(player.containerMenu instanceof RepairBoxMenu), "water is refused and kept, and no screen opens");

        // (asked of the block itself: the test's player cannot be sent a screen)
        ItemStack empty = new ItemStack(Items.BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, empty);
        BlockPos at = helper.absolutePos(pos);
        InteractionResult result = helper.getBlockState(pos).useItemOn(empty, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at).relative(Direction.UP, 0.5), Direction.UP, at, false));
        check(helper, result == InteractionResult.TRY_WITH_EMPTY_HAND && box.experience() == 3_500 && player.getMainHandItem().is(Items.BUCKET),
                "an empty bucket takes nothing out and goes on to open the screen, as anything else in hand does: " + result + ", "
                        + box.experience() + ", " + player.getMainHandItem());
        helper.succeed();
    }

    /**
     * An Experience Pipe set to pull from a Copper Tank of water draws none of it, with an Experience Tank beside it
     * to take what it would carry; a Copper Pipe set to pull from an Experience Tank draws no experience, with a Copper
     * Tank beside it. Each carries only its own.
     */
    private static void pullsOnlyWhatItCarries(GameTestHelper helper) {
        BlockPos water = new BlockPos(1, 2, 2);
        BlockPos glass = water.east();
        BlockPos sink = glass.east();
        BlockPos experience = new BlockPos(1, 2, 4);
        BlockPos copper = experience.east();
        BlockPos copperSink = copper.east();
        lay(helper, new BlockPos[] {water, glass, sink, experience, copper, copperSink}, new BlockState[] {
                ModBlocks.COPPER_TANK.get().defaultBlockState(), experiencePipe(), experienceTank(),
                experienceTank(), copperPipe(), ModBlocks.COPPER_TANK.get().defaultBlockState()});
        var waterTank = helper.getBlockEntity(water, CopperTankBlockEntity.class);
        waterTank.fluidHandler().set(0, FluidResource.of(Fluids.WATER), 4_000);
        var experienceTank = helper.getBlockEntity(experience, ExperienceTankBlockEntity.class);
        experienceTank.fill(2_000);
        ServerPlayer player = clicker(helper, GameType.CREATIVE);
        wrench(helper, player, glass, Direction.WEST);
        wrench(helper, player, copper, Direction.WEST);
        FluidPipeBlockEntity glassPipe = helper.getBlockEntity(glass, ExperiencePipeBlockEntity.class);
        FluidPipeBlockEntity copperPipe = helper.getBlockEntity(copper, CopperPipeBlockEntity.class);
        check(helper, glassPipe.pullsFrom(Direction.WEST) && copperPipe.pullsFrom(Direction.WEST),
                "both sides are set to pull: each is joined to the tank");
        helper.runAfterDelay(20, () -> {
            check(helper, waterTank.amount() == 4_000 && glassPipe.stored() == 0
                    && helper.getBlockEntity(sink, ExperienceTankBlockEntity.class).amount() == 0,
                    "the Experience Pipe draws no water: " + waterTank.amount() + ", " + glassPipe.stored());
            check(helper, experienceTank.amount() == 2_000 && copperPipe.stored() == 0
                    && helper.getBlockEntity(copperSink, CopperTankBlockEntity.class).amount() == 0,
                    "the Copper Pipe draws no experience: " + experienceTank.amount() + ", " + copperPipe.stored());
            helper.succeed();
        });
    }
}
