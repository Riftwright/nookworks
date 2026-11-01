package dev.nookworks.decor;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The Standing Mirror: the If Mirror's frame round a plain glass, which shows what stands before it - the room, the
 * player, whoever is about - as a mirror does (the client draws it, StandingMirrorRenderer). It opens onto nothing.
 */
public class StandingMirrorBlock extends ChevalMirrorBlock implements EntityBlock {
    public StandingMirrorBlock(Properties properties) {
        super(properties);
    }

    /** The lower half's, only for the client to know where to draw the glass. */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new StandingMirrorBlockEntity(pos, state) : null;
    }
}
