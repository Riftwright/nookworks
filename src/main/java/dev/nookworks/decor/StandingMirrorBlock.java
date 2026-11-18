package dev.nookworks.decor;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;

/**
 * The Standing Mirror: the If Mirror's frame round a plain glass, which shows what stands before it - the room, the
 * player, whoever is about - as a mirror does (the client draws it, StandingMirrorRenderer). It opens onto nothing.
 */
public class StandingMirrorBlock extends ChevalMirrorBlock implements RemovalHooks, EntityBlock {
    public StandingMirrorBlock(Properties properties) {
        super(properties);
    }

    /** The lower half's, only for the client to know where to draw the glass. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new StandingMirrorBlockEntity(pos, state) : null;
    }

    // PORT: 1.21.1 calls only onRemove where 26.3 calls the old block entity's preRemoveSideEffects and this
    // block's affectNeighborsAfterRemoval: BlockRemoval runs them in 26.3's order
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockRemoval.onRemove(this, state, level, pos, newState, movedByPiston,
                () -> super.onRemove(state, level, pos, newState, movedByPiston));
    }
}
