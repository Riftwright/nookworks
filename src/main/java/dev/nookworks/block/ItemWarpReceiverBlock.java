package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.ItemWarpReceiverBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Item Warp Receiver: keeps who set it down, so that only their senders send to it ({@link ItemWarpReceiverBlockEntity}).
 * It does nothing itself: a sender in its column delivers into the container under it.
 */
public class ItemWarpReceiverBlock extends BaseEntityBlock {
    public ItemWarpReceiverBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ItemWarpReceiverBlockEntity(pos, state);
    }
}
