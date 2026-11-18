package dev.nookworks.mercury;

import dev.nookworks.block.PipeSides;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Mercury Pipe (iron: mercury does not amalgamate with iron). Connects to other Mercury Pipes and to Nookworks
 * mercury blocks only; it holds nothing itself; {@link MercuryNetwork} walks through it.
 */
public class MercuryPipeBlock extends PipeBlock implements RemovalHooks {
    /** How wide the pipe is, in pixels. The models draw a barrel this thick, so the hitbox must match. */
    private static final float THICKNESS = 8.0F;

    public MercuryPipeBlock(Properties properties) {
        super(THICKNESS / 32.0F, properties); // PORT: 26.3's PipeBlock takes the width in pixels, 1.21.1's half the width in blocks
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
    protected BlockState updateShape(BlockState state, Direction dir, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
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
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        // PORT: vanilla's part of affectNeighborsAfterRemoval runs in 1.21.1's super.onRemove
        MercuryNetwork.changed(level);
    }

    private static boolean connectsTo(LevelReader level, BlockPos neighbour) {
        return level.getBlockState(neighbour).getBlock() instanceof MercuryPipeBlock
                || level.getBlockEntity(neighbour) instanceof MercuryHandler
                || DistillerBlockEntity.isTowerCondenser(level, neighbour); // its mercury comes out at the top (2026-11-12)
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

    // PORT: 1.21.1 calls only onRemove where 26.3 calls the old block entity's preRemoveSideEffects and this
    // block's affectNeighborsAfterRemoval: BlockRemoval runs them in 26.3's order
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockRemoval.onRemove(this, state, level, pos, newState, movedByPiston,
                () -> super.onRemove(state, level, pos, newState, movedByPiston));
    }

    // PORT: 1.21.1 asks every block for a codec (26.3 has none); only the block-list data report reads it
    @Override
    protected MapCodec<? extends MercuryPipeBlock> codec() {
        return BlockCodecs.of(this);
    }
}
