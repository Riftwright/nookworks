package dev.nookworks.fluid;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
