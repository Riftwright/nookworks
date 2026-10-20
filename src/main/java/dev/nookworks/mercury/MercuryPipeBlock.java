package dev.nookworks.mercury;

import dev.nookworks.block.PipeSides;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;

/**
 * Mercury Pipe (iron: mercury does not amalgamate with iron). Connects to other Mercury Pipes and to Nookworks
 * mercury blocks only; it holds nothing itself; {@link MercuryNetwork} walks through it.
 */
public class MercuryPipeBlock extends PipeBlock {
    /** How wide the pipe is, in pixels. The models draw a barrel this thick, so the hitbox must match. */
    private static final float THICKNESS = 8.0F;

    public MercuryPipeBlock(Properties properties) {
        super(THICKNESS, properties);
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
            state = state.setValue(PROPERTY_BY_DIRECTION.get(dir), connectsTo(context.getLevel(), context.getClickedPos().relative(dir))
                    && PipeSides.mayJoin(state, context.getLevel(), context.getClickedPos(), dir));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction dir, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        boolean joins = connectsTo(level, neighbourPos)
                // world generation may not have made the block entity there yet, or set that part of the template
                // down at all (cut at a chunk's edge): the side stays as the template saved it (EnergyCableBlock)
                || !(level instanceof Level) && state.getValue(PROPERTY_BY_DIRECTION.get(dir));
        return state.setValue(PROPERTY_BY_DIRECTION.get(dir), joins && PipeSides.mayJoin(state, level, pos, dir));
    }

    /** Set down, or joined to something new: the mercury walks kept in the level are stale. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        MercuryNetwork.changed(level);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        MercuryNetwork.changed(level);
    }

    private static boolean connectsTo(LevelReader level, BlockPos neighbour) {
        return level.getBlockState(neighbour).getBlock() instanceof MercuryPipeBlock
                || level.getBlockEntity(neighbour) instanceof MercuryHandler;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipeSides.turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipeSides.turned(state, mirror::mirror);
    }

    /** A pipe: no way through, as vanilla's chorus plant of the same make is not. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }
}
