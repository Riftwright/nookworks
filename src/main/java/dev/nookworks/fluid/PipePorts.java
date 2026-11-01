package dev.nookworks.fluid;

import java.util.Map;
import java.util.function.UnaryOperator;

import dev.nookworks.mercury.MercuryPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A pipe of ours joined on that side of a machine, for its model: a port drawn where the pipe meets it, so the pipe
 * does not end in the air (the user, 2026-10-31: the Oil Press's thin frame first, then 「口金直していいよ」 for the
 * machines whose panels are set back a pixel - the cabinets, the tanks). By world side, not turned with the block.
 * A block takes the sides it can show a port on ({@link #add}); {@link #withPorts} sets them from its neighbours.
 */
public final class PipePorts {
    public static final Map<Direction, BooleanProperty> PORTS = Map.of(
            Direction.NORTH, BooleanProperty.create("port_north"), Direction.EAST, BooleanProperty.create("port_east"),
            Direction.SOUTH, BooleanProperty.create("port_south"), Direction.WEST, BooleanProperty.create("port_west"),
            Direction.UP, BooleanProperty.create("port_up"), Direction.DOWN, BooleanProperty.create("port_down"));

    private PipePorts() {}

    /** The ports on {@code sides} added to a block's states. */
    public static void add(StateDefinition.Builder<Block, BlockState> builder, Direction... sides) {
        for (Direction side : sides) {
            builder.add(PORTS.get(side));
        }
    }

    /** {@code state} with no port shown. */
    public static BlockState none(BlockState state) {
        for (BooleanProperty port : PORTS.values()) {
            if (state.hasProperty(port)) {
                state = state.setValue(port, false);
            }
        }
        return state;
    }

    /** Whether {@code there}, on {@code side} of a block, is a pipe of ours with its arm turned to that block. */
    public static boolean joins(BlockState there, Direction side) {
        BooleanProperty arm = PipeBlock.PROPERTY_BY_DIRECTION.get(side.getOpposite());
        return (there.getBlock() instanceof FluidPipeBlock || there.getBlock() instanceof MercuryPipeBlock)
                && there.hasProperty(arm) && there.getValue(arm);
    }

    /** {@code state} with a port on each of its sides a pipe beside it is joined to. */
    public static BlockState withPorts(BlockState state, LevelReader level, BlockPos pos) {
        for (var port : PORTS.entrySet()) {
            if (state.hasProperty(port.getValue())) {
                state = state.setValue(port.getValue(), joins(level.getBlockState(pos.relative(port.getKey())), port.getKey()));
            }
        }
        return state;
    }

    /** {@code state} with the port on {@code side} set from what is there now: for updateShape. */
    public static BlockState updated(BlockState state, Direction side, BlockState there) {
        BooleanProperty port = PORTS.get(side);
        return state.hasProperty(port) ? state.setValue(port, joins(there, side)) : state;
    }

    public static BlockState rotate(BlockState state, Rotation rotation) {
        return moved(state, rotation::rotate);
    }

    public static BlockState mirror(BlockState state, Mirror mirror) {
        return moved(state, mirror::mirror);
    }

    private static BlockState moved(BlockState state, UnaryOperator<Direction> turn) {
        BlockState out = state;
        for (var port : PORTS.entrySet()) {
            BooleanProperty to = PORTS.get(turn.apply(port.getKey()));
            if (state.hasProperty(port.getValue()) && out.hasProperty(to)) {
                out = out.setValue(to, state.getValue(port.getValue()));
            }
        }
        return out;
    }

    /**
     * A block loaded from a world saved before it showed its pipes' ports: set right once the chunk is in. For the
     * block entity's onLoad.
     */
    public static void showOnLoad(BlockEntity entity) {
        if (entity.getLevel() instanceof ServerLevel server && PORTS.values().stream().anyMatch(entity.getBlockState()::hasProperty)) {
            server.getServer().execute(() -> {
                if (entity.isRemoved() || entity.getLevel() != server) {
                    return;
                }
                BlockPos pos = entity.getBlockPos();
                BlockState state = server.getBlockState(pos);
                BlockState ported = state;
                for (var port : PORTS.entrySet()) {
                    BlockPos next = pos.relative(port.getKey());
                    // a side whose chunk is not in is left as it was: reading it would load it, and the chunk past it
                    // when that one's machines load in turn (r19 self-review)
                    if (state.hasProperty(port.getValue()) && server.isLoaded(next)) {
                        ported = ported.setValue(port.getValue(), joins(server.getBlockState(next), port.getKey()));
                    }
                }
                if (ported != state) {
                    server.setBlock(pos, ported, Block.UPDATE_CLIENTS);
                }
            });
        }
    }
}
