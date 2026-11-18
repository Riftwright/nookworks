package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.ItemWarpReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Item Warp Receiver: keeps who set it down, so that only their senders send to it ({@link ItemWarpReceiverBlockEntity}).
 * It does nothing itself: a sender in its column delivers into the container under it.
 */
public class ItemWarpReceiverBlock extends BaseEntityBlock implements RemovalHooks {
    public ItemWarpReceiverBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemWarpReceiverBlockEntity(pos, state);
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
    protected MapCodec<? extends ItemWarpReceiverBlock> codec() {
        return BlockCodecs.of(this);
    }
}
