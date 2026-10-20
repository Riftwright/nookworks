package dev.nookworks.block;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.machine.MachineIO;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Energy Cable: connects to other cables and to anything with an energy capability. It stores nothing:
 * energy pushed into it (by our generators, or by other mods through its capability) goes straight to every
 * receiver on the connected network. See {@link MachineIO#receivers}.
 */
public class EnergyCableBlock extends PipeBlock {
    public EnergyCableBlock(BlockBehaviour.Properties properties) {
        super(4.0F, properties); // four pixels across, as its models are
        BlockState state = this.stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        this.registerDefaultState(PipeSides.allOn(state));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
        PipeSides.addTo(builder);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        for (Direction dir : Direction.values()) {
            BlockPos next = context.getClickedPos().relative(dir);
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(context.getLevel(), next, dir, false)
                    && PipeSides.mayJoin(state, context.getLevel(), context.getClickedPos(), dir));
        }
        return state;
    }

    /**
     * Set down, or changed - joined to something new, a side turned: the network here is walked again, and whatever
     * holds this cable's capability is told to look it up afresh (a cable has no block entity to tell it so).
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        MachineIO.networkChanged(level, pos);
        level.invalidateCapabilities(pos);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MachineIO.networkChanged(level, pos);
        level.invalidateCapabilities(pos);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean was = state.getValue(PROPERTY_BY_DIRECTION.get(dir));
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(level, neighbourPos, dir, was) && PipeSides.mayJoin(state, level, pos, dir));
    }

    /**
     * Whether a cable joins what is at {@code neighbour}. Capabilities are asked of a level only: where none can be
     * asked - world generation, setting a structure's template down - the side stays as it was ({@code was}), so a
     * working line saved in a template is set down working (Nook of the Dead's refuge, 2026-10-19: every side facing a
     * machine came out unjoined, and nothing ran). Whatever stands there yet: a template cut at a chunk's edge sets its
     * far part down later, and the machine there was still air when this side was asked (the review of 8bfc4544). A
     * side kept toward something that never came is only drawn; the network asks for the capability as it walks.
     */
    private static boolean connectsTo(LevelReader level, BlockPos neighbour, Direction dir, boolean was) {
        BlockState there = level.getBlockState(neighbour);
        if (there.getBlock() instanceof EnergyCableBlock) {
            return true;
        }
        if (!(level instanceof Level world)) {
            return was;
        }
        return world.getCapability(Capabilities.Energy.BLOCK, neighbour, dir.getOpposite()) != null;
    }

    /**
     * The capability exposed by every cable block: a pass-through into the network.
     * <p>
     * A cable stores nothing, but reporting a capacity of zero makes it look full to anything that checks
     * before it sends. So it answers with what the machines behind it could take right now: empty, with room
     * for however much the network can currently swallow.
     */
    public static EnergyHandler networkHandler(ServerLevel level, BlockPos pos) {
        return networkHandler(level, pos, null);
    }

    /** Whether a cable's handler is inserting now: another mod's block that passes power straight on is not sent back round. */
    private static boolean inserting;

    /**
     * The handler a neighbour on {@code side} sees. That neighbour is where the energy comes from, so it is left out
     * of the machines the energy goes to: a battery that both takes and gives would otherwise be handed straight
     * back what it just sent, and nothing would reach the rest of the network. A handler kept after the cable was
     * broken, or the side turned off, takes nothing.
     */
    public static EnergyHandler networkHandler(ServerLevel level, BlockPos pos, @Nullable Direction side) {
        BlockPos sender = side == null ? null : pos.relative(side);
        return new EnergyHandler() {
            @Override
            public long getAmountAsLong() {
                return 0;
            }

            @Override
            public long getCapacityAsLong() {
                return this.stillOpen() ? roomIn(this.targets(false)) : 0;
            }

            /** Shared out as a Generator shares its own: evenly, then the rest to whoever takes it. */
            @Override
            public int insert(int amount, TransactionContext transaction) {
                if (inserting || amount <= 0 || !this.stillOpen()) {
                    return 0;
                }
                inserting = true;
                try {
                    List<EnergyHandler> targets = this.targets(true);
                    if (targets.isEmpty()) {
                        return 0;
                    }
                    int accepted = 0;
                    int share = amount / targets.size();
                    if (share > 0) {
                        for (EnergyHandler target : targets) {
                            if (accepted >= amount) {
                                break;
                            }
                            accepted += target.insert(Math.min(share, amount - accepted), transaction);
                        }
                    }
                    for (EnergyHandler target : targets) {
                        if (accepted >= amount) {
                            break;
                        }
                        accepted += target.insert(amount - accepted, transaction);
                    }
                    return accepted;
                } finally {
                    inserting = false;
                }
            }

            /** Whether this is still a cable, and the side it was asked through still on. */
            private boolean stillOpen() {
                var chunk = MachineIO.loadedChunk(level, pos);
                BlockState state = chunk == null ? null : chunk.getBlockState(pos);
                return state != null && state.getBlock() instanceof EnergyCableBlock && (side == null || !PipeSides.isOff(state, side));
            }

            /**
             * What the power goes to, in an order that starts further along each tick, as a Generator's does. Power put
             * in is given only where claims let it go from this cable ({@code giving}: MachineIO#handlerOf); the room
             * told beforehand is every receiver's, nothing asked for a question.
             */
            private List<EnergyHandler> targets(boolean giving) {
                List<MachineIO.Receiver> receivers = MachineIO.receivers(level, pos);
                List<EnergyHandler> targets = new ArrayList<>(receivers.size());
                // one store reached through two blocks (a Recycler cabled at its body and its intake) is one target
                java.util.Set<EnergyHandler> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                int start = receivers.isEmpty() ? 0 : Math.floorMod(level.getGameTime() + pos.hashCode(), receivers.size());
                for (int i = 0; i < receivers.size(); i++) {
                    MachineIO.Receiver r = receivers.get((start + i) % receivers.size());
                    if (!r.pos().equals(sender)) {
                        EnergyHandler target = giving ? MachineIO.handlerOf(level, r, pos, null) : MachineIO.handlerOf(level, r);
                        if (target != null && seen.add(target)) {
                            targets.add(target);
                        }
                    }
                }
                return targets;
            }

            @Override
            public int extract(int amount, TransactionContext transaction) {
                return 0;
            }
        };
    }

    /**
     * The room {@code targets} have between them, held at {@link Long#MAX_VALUE}: another mod's endless store tells of
     * that much room itself, and two of them added up ran past it to below zero, so the cable looked full and was sent
     * nothing (r15 late audit, N1). A store telling of less than it holds has no room, not less than none. Public for
     * tests.
     */
    public static long roomIn(Iterable<EnergyHandler> targets) {
        long room = 0;
        for (EnergyHandler target : targets) {
            long capacity = target.getCapacityAsLong();
            long amount = Math.max(0, target.getAmountAsLong());
            long free = capacity <= amount ? 0 : capacity - amount;
            room = free > Long.MAX_VALUE - room ? Long.MAX_VALUE : room + free;
        }
        return room;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipeSides.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipeSides.turned(state, mirror::mirror);
    }

    /** A cable: no way through, as vanilla's chorus plant of the same make is not. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
