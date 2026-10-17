package dev.nookworks.block;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.UnaryOperator;

import dev.nookworks.machine.MachineIO;
import dev.nookworks.mercury.MercuryNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.Vec3;

/**
 * Sides of a pipe or cable switched off with the Wrench.
 * <p>
 * Our pipes and cables join up with whatever they can on their own. Sneak-using a Wrench on one side turns that
 * side off: the arm goes, nothing passes through it, and it stays off whatever is placed or broken beside it until
 * it is turned back on the same way. A pipe of the same kind on the other side is switched with it, so the two
 * never disagree about whether they are joined.
 * <p>
 * The block state keeps both: {@code north} is "joined on the north side" (what the model draws and the networks
 * follow), {@code north_off} is "switched off on the north side".
 */
public final class PipeSides {
    public static final Map<Direction, BooleanProperty> OFF = new EnumMap<>(Direction.class);

    static {
        for (Direction side : Direction.values()) {
            OFF.put(side, BooleanProperty.create(side.getSerializedName() + "_off"));
        }
    }

    /** Adds the six {@code _off} properties to a pipe block's state definition. */
    public static void addTo(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OFF.values().toArray(new BooleanProperty[0]));
    }

    /**
     * {@code state} turned as a structure placed rotated or mirrored turns it: each side's join and switched-off flag go
     * round with it. Left as they were, a side switched off with the Wrench stayed off towards the old direction, and a
     * cable rotated into a base fed the machine it was kept from (r7 review).
     */
    public static BlockState turned(BlockState state, UnaryOperator<Direction> turn) {
        BlockState out = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            Direction to = turn.apply(side);
            if (state.hasProperty(OFF.get(side))) { // a pipe the wrench does not switch has no such flags
                out = out.setValue(OFF.get(to), state.getValue(OFF.get(side)));
            }
            BooleanProperty join = PipeBlock.PROPERTY_BY_DIRECTION.get(side);
            if (state.hasProperty(join)) {
                out = out.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(to), state.getValue(join));
            }
        }
        return out;
    }

    /** {@code state} with every side on (a block whose sides cannot be switched off is as it was). */
    public static BlockState allOn(BlockState state) {
        for (BooleanProperty off : OFF.values()) {
            if (state.hasProperty(off)) {
                state = state.setValue(off, false);
            }
        }
        return state;
    }

    public static boolean isOff(BlockState state, Direction side) {
        return state.hasProperty(OFF.get(side)) && state.getValue(OFF.get(side));
    }

    /** Whether the pipe at {@code pos} is joined on {@code side}: the side is on and the neighbour would join. */
    public static boolean joined(BlockState state, Direction side) {
        return state.getValue(PipeBlock.PROPERTY_BY_DIRECTION.get(side));
    }

    /**
     * Whether a pipe may join its neighbour on {@code side}, given that the neighbour is something it could join:
     * not if this side is off, and not if the neighbour is a pipe of the same kind with its facing side off.
     */
    public static boolean mayJoin(BlockState state, LevelReader level, BlockPos pos, Direction side) {
        if (isOff(state, side)) {
            return false;
        }
        BlockState neighbour = level.getBlockState(pos.relative(side));
        return neighbour.getBlock() != state.getBlock() || !isOff(neighbour, side.getOpposite());
    }

    /**
     * Which side of the pipe a wrench click means. A click on an arm means that arm; a click on the core means the
     * face that was clicked, which is how a side that is off (and so has no arm) is found again.
     */
    public static Direction sideFromHit(BlockPos pos, Vec3 hit, Direction face) {
        double x = hit.x - pos.getX() - 0.5;
        double y = hit.y - pos.getY() - 0.5;
        double z = hit.z - pos.getZ() - 0.5;
        double ax = Math.abs(x);
        double ay = Math.abs(y);
        double az = Math.abs(z);
        double most = Math.max(ax, Math.max(ay, az));
        if (most < 0.22) {
            return face; // the core
        }
        if (most == ax) {
            return x > 0 ? Direction.EAST : Direction.WEST;
        }
        if (most == ay) {
            return y > 0 ? Direction.UP : Direction.DOWN;
        }
        return z > 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /**
     * Switches {@code side} off, or back on, and does the same to a pipe of the same kind on the other side. The
     * joins are worked out again from the neighbours by the usual shape update. Returns whether the side is now off.
     */
    public static boolean toggle(Level level, BlockPos pos, BlockState state, Direction side) {
        boolean off = !isOff(state, side);
        level.setBlock(pos, state.setValue(OFF.get(side), off), Block.UPDATE_ALL);
        BlockPos neighbourPos = pos.relative(side);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() == state.getBlock()) {
            level.setBlock(neighbourPos, neighbour.setValue(OFF.get(side.getOpposite()), off), Block.UPDATE_ALL);
        }
        // Setting the property does not run the shape update on this block itself, so work the joins out now.
        refresh(level, pos);
        refresh(level, neighbourPos);
        MachineIO.networkChanged(level, pos); // the energy walks kept here are stale now
        MachineIO.networkChanged(level, neighbourPos);
        MercuryNetwork.changed(level);
        // and whatever holds either side's capability must look again: a side turned off has none
        level.invalidateCapabilities(pos);
        level.invalidateCapabilities(neighbourPos);
        return off;
    }

    private static void refresh(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.hasProperty(OFF.get(Direction.NORTH))) {
            return;
        }
        BlockState updated = Block.updateFromNeighbourShapes(state, level, pos);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    /** The blocks that have switchable sides. */
    public static boolean isPipe(BlockState state) {
        return state.hasProperty(OFF.get(Direction.NORTH)) && state.getBlock() instanceof PipeBlock;
    }

    private PipeSides() {}
}
