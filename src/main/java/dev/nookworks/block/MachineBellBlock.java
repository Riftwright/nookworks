package dev.nookworks.block;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import dev.nookworks.machine.MachineAlerts;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Machine Bell: a small electric alarm bell fitted beside a machine - on its side, on the wall or floor next to it, under
 * the ceiling over it. When a machine on any side of it has stood stopped {@link MachineAlerts#SETTLE} ticks for a reason
 * to see to ({@code MachineStatus#alerts}: out of fuel or power, its output blocked, a tank full...), it rings once and
 * gives a redstone signal for as long as the machine stays so: to a lamp, a door, a line of dust to wherever the player
 * is. Never to a machine itself, so it cannot stop the machine it watches, nor switch it on and off with its own signal
 * (the user, 2026-11-10: 「できそうなことはぜんぶやっていいよ」, of the sound session's survey: its third item, the stop cue
 * grown into a block). It looks every {@link #LOOK_EVERY} ticks by a scheduled tick, so it keeps no block entity. A
 * machine is what {@link MachineAlerts#isMachine} says, its status what {@link MachineAlerts#statusOf} reads: an
 * add-on's machine with a screen of its own too.
 */
public class MachineBellBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final int LOOK_EVERY = 10;
    /** When each bell's machine was first seen stopped, by level: forgotten with the level or as it runs again. */
    private static final Map<Level, Map<BlockPos, Long>> STOPPED_SINCE = new WeakHashMap<>();
    /** Ringing bells that have seen their machine not stopped once: a second look lets go. */
    private static final Map<Level, java.util.Set<BlockPos>> CLEARING = new WeakHashMap<>();

    private static final VoxelShape FLOOR = Block.box(5, 0, 3, 11, 5, 13);
    private static final VoxelShape CEILING = Block.box(5, 11, 3, 11, 16, 13);
    private static final Map<Direction, VoxelShape> WALL = Shapes.rotateHorizontal(Block.box(5, 3, 11, 11, 13, 16));

    public MachineBellBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWERED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> WALL.get(state.getValue(FACING));
        };
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!old.is(this)) {
            level.scheduleTick(pos, this, LOOK_EVERY);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        long now = level.getGameTime();
        Map<BlockPos, Long> since = STOPPED_SINCE.computeIfAbsent(level, l -> new HashMap<>());
        java.util.Set<BlockPos> clearing = CLEARING.computeIfAbsent(level, l -> new java.util.HashSet<>());
        boolean powered = state.getValue(POWERED);
        boolean ring;
        if (machineStopped(level, pos)) {
            clearing.remove(pos);
            // found ringing with nothing noted (the world loaded again, the chunk back): it rang for this stop already, and
            // rings on with no second ring - counted from now it let go and rang again two seconds on, and a lamp, door or
            // counter on its signal saw it go off and on at every load (the last look before 1.5)
            long first = since.computeIfAbsent(pos.immutable(), p -> powered ? now - MachineAlerts.SETTLE : now);
            ring = now - first >= MachineAlerts.SETTLE;
        } else if (powered && clearing.add(pos.immutable())) {
            ring = true; // let go at the next look: a machine just loaded says idle until its first tick works it out
        } else {
            since.remove(pos);
            clearing.remove(pos);
            ring = false;
        }
        if (ring != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, ring), Block.UPDATE_ALL);
            level.updateNeighborsAt(pos, this);
            if (ring) {
                level.playSound(null, pos, ModSounds.MACHINE_BELL_RING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
        level.scheduleTick(pos, this, LOOK_EVERY);
    }

    /** Whether a machine on any side of {@code pos} has stopped for a reason to see to. Public for tests. */
    public static boolean machineStopped(Level level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (level.isLoaded(next) && MachineAlerts.stopped(level.getBlockEntity(next))) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** Its signal to whatever beside it asks - but a machine: the bell must not stop, nor start, what it watches. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        if (!state.getValue(POWERED)) {
            return 0;
        }
        BlockEntity asking = level.getBlockEntity(pos.relative(direction.getOpposite()));
        return MachineAlerts.isMachine(asking) ? 0 : 15;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        Map<BlockPos, Long> since = STOPPED_SINCE.get(level);
        if (since != null) {
            since.remove(pos);
        }
        java.util.Set<BlockPos> clearing = CLEARING.get(level);
        if (clearing != null) {
            clearing.remove(pos);
        }
        if (!movedByPiston && state.getValue(POWERED)) {
            level.updateNeighborsAt(pos, this);
        }
    }
}
