package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.neoforged.neoforge.capabilities.Capabilities;

import dev.nookworks.block.PipeSides;

/**
 * A fluid pipe block (see {@link FluidPipeBlockEntity}). It joins pipes of its own kind and anything that takes a
 * fluid, ours or another mod's, so it can be plumbed into an existing setup from either end - but never a pipe of
 * another kind, which could not take what it carries anyway.
 */
public abstract class FluidPipeBlock extends PipeBlock implements EntityBlock {
    protected FluidPipeBlock(Properties properties) {
        this(4.0F, properties);
    }

    /** {@code thickness}: how wide the pipe is drawn, in pixels - what is clicked and outlined is that wide too. */
    protected FluidPipeBlock(float thickness, Properties properties) {
        super(thickness, properties);
        BlockState state = this.stateDefinition.any();
        for (var property : PROPERTY_BY_DIRECTION.values()) {
            state = state.setValue(property, false);
        }
        this.registerDefaultState(PipeSides.allOn(state));
    }

    /** The block entity type this pipe ticks. */
    protected abstract BlockEntityType<? extends FluidPipeBlockEntity> pipeType();

    /**
     * The model drawn over an arm whose side pulls (client/render/PipePullRenderer), facing north as the arm's own
     * model does; null for a pipe whose sides cannot be set (a fitting).
     */
    public @Nullable Identifier pullFlange() {
        return null;
    }

    /** The pipes this one joins: the same family as its block entity's ({@link FluidPipeBlockEntity#family}). */
    protected Object family() {
        return this.pipeType();
    }

    /** The state it is placed in before its sides are joined: a fitting takes the line it is set in here. */
    protected BlockState placementBase(BlockPlaceContext context) {
        return this.defaultBlockState();
    }

    /** Whether it may join anything on {@code side} at all: a fitting set in a line joins only along it. */
    protected boolean joinsSide(BlockState state, Direction side) {
        return true;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
        if (this.switchableSides()) {
            PipeSides.addTo(builder);
        }
    }

    /**
     * Whether a wrench switches its sides off ({@link PipeSides}). Asked while the state definition is built, so it
     * reads no field: an answer the class gives, not the block.
     */
    protected boolean switchableSides() {
        return true;
    }

    private boolean connectsTo(BlockState state, LevelReader level, BlockPos pos, Direction side) {
        if (!this.joinsSide(state, side)) {
            return false;
        }
        BlockState thereState = level.getBlockState(pos);
        Block there = thereState.getBlock();
        if (there instanceof FluidPipeBlock pipe) {
            // the same line, if it is a pipe of this family that will join back on this side; never another kind
            return pipe.family() == this.family() && pipe.joinsSide(thereState, side.getOpposite())
                    && !PipeSides.isOff(thereState, side.getOpposite());
        }
        if (there instanceof dev.nookworks.cooking.IronPotBlock) {
            // an Iron Pot or a Frying Pan is filled by a Kitchen Tap over it, not by a pipe run into its side (the user,
            // 2026-11-15: 「蛇口使うのであれば、鍋に向かって銅管が開く必要ないよね？」)
            return false;
        }
        if (!(level instanceof Level full)) {
            // world generation, where capabilities cannot be asked: the side stays as the template saved it (EnergyCableBlock)
            return state.getValue(PROPERTY_BY_DIRECTION.get(side));
        }
        return full.getCapability(Capabilities.Fluid.BLOCK, pos, side.getOpposite()) != null;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.placementBase(context);
        for (Direction side : Direction.values()) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(side),
                    this.connectsTo(state, context.getLevel(), context.getClickedPos().relative(side), side)
                            && PipeSides.mayJoin(state, context.getLevel(), context.getClickedPos(), side));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(PROPERTY_BY_DIRECTION.get(side), this.connectsTo(state, level, neighbourPos, side)
                && PipeSides.mayJoin(state, level, pos, side));
    }

    /** How often, in ticks, a pipe looks again at the sides it joins nothing on ({@link #lookBeside}). */
    public static final int LOOK = 40;

    /**
     * A side not joined and not turned off, facing a block entity that is no pipe: if that block has a fluid handler
     * now - another mod's that put it up with no block changed (a pipe set to extract by its wrench, a conduit added to
     * a bundle already there) - the side is joined. A pipe looked at what stood beside it only when it was set down and
     * when a neighbour's block changed, and such a side stayed unjoined for good, as an Energy Cable's did
     * (MachineIO.lateJoin; found there by the 1.21.1 port with the real mods, 2026-11-17). Called by the pipe's own
     * tick, every {@link #LOOK} ticks; a block with no block entity changes its block to change what it takes, and a
     * pipe of ours is joined as it is set down or switched.
     */
    void lookBeside(ServerLevel level, BlockPos pos, BlockState state) {
        BlockState now = state;
        for (Direction side : Direction.values()) {
            if (PipeSides.joined(state, side) || PipeSides.isOff(state, side)) {
                continue;
            }
            BlockPos next = pos.relative(side);
            if (!level.isLoaded(next)) {
                continue; // never load a chunk to look at it
            }
            BlockState there = level.getBlockState(next);
            if (!there.hasBlockEntity() || there.getBlock() instanceof FluidPipeBlock) {
                continue;
            }
            if (this.connectsTo(state, level, next, side) && PipeSides.mayJoin(state, level, pos, side)) {
                now = now.setValue(PROPERTY_BY_DIRECTION.get(side), true);
            }
        }
        if (now != state) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != this.pipeType()) {
            return null;
        }
        return (l, p, s, be) -> FluidPipeBlockEntity.serverTick(l, p, s, (FluidPipeBlockEntity) be);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipeSides.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipeSides.turned(state, mirror::mirror);
    }

    /** A pipe: no way through, as vanilla's chorus plant of the same make is not (copper and experience pipes). */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
