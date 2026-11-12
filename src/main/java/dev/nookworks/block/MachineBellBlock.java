package dev.nookworks.block;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.BellMounts;
import dev.nookworks.machine.MachineAlerts;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
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
 * <p>
 * Its signal can be switched off and on by an empty-hand click, a pilot lamp on its housing lit while it is on: a bell
 * newly set gives none until switched on, so one set beside a machine never sets off what stands next to it unasked
 * (the user, 2026-11-12: 「うっかり隣にTNT置いてしまったら、大変なことになりますから」); a bell set before that keeps its
 * signal ({@link #SIGNAL}'s default is on, which is what a saved state without it reads as). It fits the face it is on
 * ({@link BellMounts}): a black iron pad behind its plate reaches back to a machine's panel set in from its frame
 * ({@link #INSET}), and on a lower machine it sits lower ({@link #SHIFT}), on one open below higher.
 */
public class MachineBellBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** Whether it gives its redstone signal when it rings: on by default (a bell saved before it), off as newly set. */
    public static final BooleanProperty SIGNAL = BooleanProperty.create("signal");
    /** How deep the pad behind its plate reaches into the block it is on, px ({@link BellMounts}). */
    public static final IntegerProperty INSET = IntegerProperty.create("inset", 0, 7);
    /**
     * How far up (above 3) or down (below) the face a wall's bell is moved, px less 3: down on a machine lower than the
     * bell's top, up on one open below ({@link BellMounts}). 3, as made, is what a bell saved before it reads as.
     */
    public static final IntegerProperty SHIFT = IntegerProperty.create("shift", 0, 6);
    /** {@link #SHIFT} as made: neither up nor down. */
    public static final int LEVEL = 3;
    public static final int LOOK_EVERY = 10;
    /** When each bell's machine was first seen stopped, by level: forgotten with the level or as it runs again. */
    private static final Map<Level, Map<BlockPos, Long>> STOPPED_SINCE = new WeakHashMap<>();
    /** Ringing bells that have seen their machine not stopped once: a second look lets go. */
    private static final Map<Level, java.util.Set<BlockPos>> CLEARING = new WeakHashMap<>();

    private static final VoxelShape FLOOR = Block.box(5, 0, 3, 11, 5, 13);
    private static final VoxelShape CEILING = Block.box(5, 11, 3, 11, 16, 13);
    private static final List<Map<Direction, VoxelShape>> WALL = java.util.stream.IntStream.rangeClosed(0, 6)
            .mapToObj(shift -> Shapes.rotateHorizontal(Block.box(5, shift, 11, 11, 10 + shift, 16))).toList();

    public MachineBellBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false).setValue(SIGNAL, true).setValue(INSET, 0).setValue(SHIFT, LEVEL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, POWERED, SIGNAL, INSET, SHIFT);
    }

    /** As newly set: its signal off, fitted to what it is on. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null : fitted(state.setValue(SIGNAL, false), context.getLevel(), context.getClickedPos());
    }

    /** {@code state} fitted to the block it is on: the pad's depth and the shift that block's face asks. */
    public static BlockState fitted(BlockState state, LevelReader level, BlockPos pos) {
        Direction out = getConnectedDirection(state);
        return fitted(state, level.getBlockState(pos.relative(out.getOpposite())), out);
    }

    private static BlockState fitted(BlockState state, BlockState support, Direction face) {
        BellMounts.Mount mount = BellMounts.of(support, face);
        return state.setValue(INSET, mount.inset()).setValue(SHIFT, LEVEL + (face.getAxis().isHorizontal() ? mount.up() : 0));
    }

    /**
     * On any sturdy face, as vanilla's face-attached blocks go, and on any machine whatever its shape - a Spawn Egg
     * Brewer's flask, a Nukadoko's tub - since it is a machine's bell (the user, 2026-11-12: 「形が特殊な機械にもちゃんと美しく
     * 張り付くように」): its pad reaches the machine where the face is set in ({@link BellMounts}).
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return super.canSurvive(state, level, pos)
                || MachineAlerts.isMachine(level.getBlockEntity(pos.relative(getConnectedDirection(state).getOpposite())));
    }

    /** Fitted again when the block it is on changes (a machine turned, set, or another put there). */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        BlockState updated = super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
        if (updated.is(this) && direction == getConnectedDirection(updated).getOpposite()) {
            return fitted(updated, neighbour, getConnectedDirection(updated));
        }
        return updated;
    }

    /** An empty-hand click switches its signal off or on, said over the hotbar, the pilot lamp showing it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            boolean on = !state.getValue(SIGNAL);
            server.setBlock(pos, state.setValue(SIGNAL, on), Block.UPDATE_ALL);
            server.updateNeighborsAt(pos, this); // a weak signal only: its neighbours are all it reaches
            server.playSound(null, pos, SoundEvents.COMPARATOR_CLICK, SoundSource.BLOCKS, 0.4F, on ? 0.6F : 0.5F);
            player.sendOverlayMessage(Component.translatable(on ? "message.nookworks.machine_bell.signal_on" : "message.nookworks.machine_bell.signal_off"));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACE)) {
            case FLOOR -> FLOOR;
            case CEILING -> CEILING;
            case WALL -> WALL.get(state.getValue(SHIFT)).get(state.getValue(FACING));
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
        if (!state.getValue(POWERED) || !state.getValue(SIGNAL)) {
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
