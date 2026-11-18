package dev.nookworks.gametest;

import static dev.nookworks.gametest.NookworksGameTests.check;
import static dev.nookworks.gametest.NookworksGameTests.test;

import dev.nookworks.fluid.PipePorts;
import dev.nookworks.oil.OilPressBlock;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModOil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import dev.nookworks.port.test.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Oil Press's pipe ports (the user, 2026-10-31): a Copper Pipe joined to its side shows a port there, and taken
 * away takes the port; the port is by world side, and turns with the press when a structure is turned. The same for the
 * machines whose panels are set back a pixel (fluid/PipePorts): a Copper Tank, an Experience Tank, a Mercury Tank - each
 * kind of pipe of ours - and one set down beside a pipe already there.
 */
final class OilPressPortTests {
    private OilPressPortTests() {}

    static void register() {
        test("an_oil_press_shows_a_port_where_a_pipe_joins_it", OilPressPortTests::ports);
        test("a_machine_set_back_a_pixel_shows_a_port_where_each_kind_of_pipe_joins_it", OilPressPortTests::machines);
        test("a_condenser_shows_a_port_where_a_pipe_joins_it", OilPressPortTests::condenser);
    }

    private static void ports(GameTestHelper helper) {
        BlockPos press = new BlockPos(2, 2, 2);
        helper.setBlock(press, ModOil.OIL_PRESS.get());
        BlockPos pipe = press.east();
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get());
        helper.runAfterDelay(2, () -> { // the press's block entity, which the pipe asks for its fluid, is in by now
            var level = helper.getLevel();
            BlockPos at = helper.absolutePos(pipe);
            level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
            BlockPos pressAt = helper.absolutePos(press);
            level.setBlock(pressAt, Block.updateFromNeighbourShapes(level.getBlockState(pressAt), level, pressAt), 3);
            var state = helper.getBlockState(press);
            check(helper, state.getValue(OilPressBlock.PORTS.get(Direction.EAST)), "a port where the pipe joins: " + state);
            check(helper, !state.getValue(OilPressBlock.PORTS.get(Direction.WEST)), "and none where nothing does");
            var turned = state.rotate(Rotation.CLOCKWISE_90);
            check(helper, turned.getValue(OilPressBlock.PORTS.get(Direction.SOUTH)) && !turned.getValue(OilPressBlock.PORTS.get(Direction.EAST)),
                    "turned a quarter, the port goes round with it: " + turned);
            helper.setBlock(pipe, Blocks.AIR);
            check(helper, !helper.getBlockState(press).getValue(OilPressBlock.PORTS.get(Direction.EAST)), "the pipe taken away takes the port");
            helper.succeed();
        });
    }

    /**
     * The Condenser (the user, 2026-11-10: the gap between it and a pipe looked as if it would leak): a pipe of ours
     * joined to its side shows a port, on its four sides only. Nothing of Nookworks' own joins a pipe to it -
     * nookTechnology's Tar Still does - so the pipe's arm is set by hand. One saved before it had ports gets its port
     * from the pipe as that loads.
     */
    private static void condenser(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos condenser = new BlockPos(2, 2, 2);
        helper.setBlock(condenser, ModBlocks.CONDENSER.get());
        BlockPos pipe = condenser.east();
        BlockPos pipeAt = helper.absolutePos(pipe);
        BlockPos condenserAt = helper.absolutePos(condenser);
        BlockState joined = ModBlocks.COPPER_PIPE.get().defaultBlockState().setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST), true);
        BlockState state = helper.getBlockState(condenser).updateShape(Direction.EAST, joined, level, condenserAt, pipeAt);
        check(helper, state.getValue(PipePorts.PORTS.get(Direction.EAST)), "a port where the pipe joins: " + state);
        check(helper, !state.getValue(PipePorts.PORTS.get(Direction.WEST)), "and none where nothing does");
        check(helper, !state.hasProperty(PipePorts.PORTS.get(Direction.UP)) && !state.hasProperty(PipePorts.PORTS.get(Direction.DOWN)),
                "none above or below, where its hood and flange meet a pipe already");
        check(helper, state.rotate(Rotation.CLOCKWISE_90).getValue(PipePorts.PORTS.get(Direction.SOUTH)), "turned, the port goes round with it");
        BlockState away = state.updateShape(Direction.EAST, joined.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST), false), level, condenserAt, pipeAt);
        check(helper, !away.getValue(PipePorts.PORTS.get(Direction.EAST)), "the arm turned away takes the port");

        // as from a world saved before: the pipe's arm there, the port not, and nothing changing beside it; the pipe's
        // own block entity loading does it (set without updates, so neither block is asked its shape)
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get());
        helper.runAfterDelay(1, () -> {
            level.setBlock(pipeAt, joined, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            level.setBlock(condenserAt, ModBlocks.CONDENSER.get().defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            check(helper, helper.getBlockState(pipe).getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST))
                    && !helper.getBlockState(condenser).getValue(PipePorts.PORTS.get(Direction.EAST)), "set up as an old world's");
            // the load's work asked at once (onLoad leaves it for the server's next turn, by when the pipe has turned its
            // arm from a Condenser that gives it nothing here - nookTechnology's Tar Still does)
            PipePorts.showBeside(level, pipeAt);
            check(helper, helper.getBlockState(condenser).getValue(PipePorts.PORTS.get(Direction.EAST)),
                    "the pipe loading shows the old Condenser's port: " + helper.getBlockState(condenser));
            helper.succeed();
        });
    }

    private static void machines(GameTestHelper helper) {
        BlockPos copper = new BlockPos(1, 2, 1);
        BlockPos experience = new BlockPos(4, 2, 1);
        BlockPos mercury = new BlockPos(7, 2, 1);
        helper.setBlock(copper, ModBlocks.COPPER_TANK.get());
        helper.setBlock(experience, ModBlocks.EXPERIENCE_TANK.get());
        helper.setBlock(mercury, ModBlocks.MERCURY_TANK.get());
        helper.setBlock(copper.south(), ModBlocks.COPPER_PIPE.get());
        helper.setBlock(experience.above(), ModBlocks.EXPERIENCE_PIPE.get());
        helper.setBlock(mercury.south(), ModBlocks.MERCURY_PIPE.get());
        helper.runAfterDelay(2, () -> { // the tanks' block entities, which the pipes ask for, are in by now
            var level = helper.getLevel();
            for (BlockPos pipe : new BlockPos[] {copper.south(), experience.above(), mercury.south()}) {
                BlockPos at = helper.absolutePos(pipe);
                level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
            }
            check(helper, helper.getBlockState(copper).getValue(PipePorts.PORTS.get(Direction.SOUTH)), "a Copper Tank shows a port at its Copper Pipe: "
                    + helper.getBlockState(copper));
            check(helper, helper.getBlockState(experience).getValue(PipePorts.PORTS.get(Direction.UP)), "an Experience Tank at the Experience Pipe on it: "
                    + helper.getBlockState(experience));
            check(helper, helper.getBlockState(mercury).getValue(PipePorts.PORTS.get(Direction.SOUTH)), "a Mercury Tank at its Mercury Pipe: "
                    + helper.getBlockState(mercury));
            check(helper, !helper.getBlockState(mercury).getValue(PipePorts.PORTS.get(Direction.NORTH)), "and none where nothing joins");
            helper.setBlock(copper.south(), Blocks.AIR);
            check(helper, !helper.getBlockState(copper).getValue(PipePorts.PORTS.get(Direction.SOUTH)), "the pipe taken away takes the port");
            // set down beside a pipe already there: the pipe turns to it, and the port shows at once
            BlockPos furnace = new BlockPos(1, 2, 4);
            helper.setBlock(furnace.east(), ModBlocks.COPPER_PIPE.get());
            helper.setBlock(furnace, ModBlocks.ELECTROLYTIC_CELL.get());
            helper.runAfterDelay(2, () -> {
                BlockPos at = helper.absolutePos(furnace.east());
                level.setBlock(at, Block.updateFromNeighbourShapes(level.getBlockState(at), level, at), 3);
                var cell = helper.getBlockState(furnace);
                check(helper, cell.getValue(PipePorts.PORTS.get(Direction.EAST)), "an Electrolytic Cell at the pipe beside it: " + cell);
                check(helper, cell.rotate(Rotation.CLOCKWISE_180).getValue(PipePorts.PORTS.get(Direction.WEST)),
                        "turned half round, the port goes round with it");
                helper.succeed();
            });
        });
    }
}
