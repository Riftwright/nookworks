package dev.nookworks.spawner;

import org.jspecify.annotations.Nullable;

import dev.nookworks.pearl.LodePearlItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Spawner Link Receiver: remembers the sender it was paired with; a Spawner Controller goes on top, the FE into it.
 * Its lines light ({@link #LIT}) while the controller on it runs.
 */
public class SpawnerLinkReceiverBlock extends BaseEntityBlock implements RemovalHooks {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int LIGHT_LEVEL = SpawnerLinkSenderBlock.LIGHT_LEVEL;

    public SpawnerLinkReceiverBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpawnerLinkReceiverBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // an empty hand reads it; anything else is used on it as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SpawnerLinkReceiverBlockEntity receiver) {
            GlobalPos link = receiver.link();
            // paired in another dimension it reaches nothing from here - a link works within its own - and says so
            player.displayClientMessage(link == null
                    ? Component.translatable("message.nookworks.spawner_link.unpaired")
                    : level instanceof ServerLevel server && Boolean.TRUE.equals(receiver.senderMoved(server))
                    ? Component.translatable("message.nookworks.spawner_link.moved", link.pos().getX(), link.pos().getY(), link.pos().getZ())
                    : !link.dimension().equals(level.dimension())
                    ? Component.translatable("message.nookworks.spawner_link.elsewhere", LodePearlItem.dimension(link))
                    : Component.translatable("message.nookworks.spawner_link.target", link.pos().getX(), link.pos().getY(), link.pos().getZ(),
                            LodePearlItem.dimension(link)), true);
        }
        return InteractionResult.SUCCESS;
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
    protected MapCodec<? extends SpawnerLinkReceiverBlock> codec() {
        return BlockCodecs.of(this);
    }
}
