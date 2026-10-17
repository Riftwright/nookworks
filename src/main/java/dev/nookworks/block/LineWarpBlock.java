package dev.nookworks.block;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.ItemWarpSenderBlockEntity;
import dev.nookworks.block.entity.LineWarpReceiverBlockEntity;
import dev.nookworks.block.entity.LineWarpSenderBlockEntity;
import dev.nookworks.feature.DrawRights;
import dev.nookworks.pearl.LodePearlItem;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Horizontal Item Warp sender or receiver. Both face the way the player looks when placing: the sender sends
 * that way, the receiver outputs that way (so placing it against a chest feeds the chest). Registered as
 * {@code nookworks:horizontal_item_warp_sender} / {@code _receiver}; "line warp" in the code.
 * <p>
 * Whoever sets one down owns it, and a sender sends only to its owner's receivers ({@link LineWarpReceiverBlockEntity}),
 * passing over anyone else's in its line. An empty hand on a receiver says whose senders it takes from.
 */
public class LineWarpBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    private final boolean sender;

    public LineWarpBlock(boolean sender, Properties properties) {
        super(properties);
        this.sender = sender;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return this.sender ? new LineWarpSenderBlockEntity(pos, state) : new LineWarpReceiverBlockEntity(pos, state);
    }

    /**
     * Whoever sets it down owns it ({@link DrawRights#ownerOf}): a sender sends to its owner's receivers, and a sender has
     * a stamp of its own too, so that no receiver paired with whatever stood here before takes from it. Set down by no
     * known player, it has no owner and works as one from before owners: a sender of stamp 0, an old receiver.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        UUID owner = DrawRights.ownerOf(by);
        if (level.getBlockEntity(pos) instanceof LineWarpSenderBlockEntity sender) {
            sender.placed(owner, owner == null ? 0L : LineWarpSenderBlockEntity.newStamp(level.getRandom()));
        } else if (level.getBlockEntity(pos) instanceof LineWarpReceiverBlockEntity receiver) {
            receiver.setOwner(owner);
        }
    }

    /**
     * An empty hand on a receiver: whose senders it takes from; for one from before, what it is paired with, or that it
     * is not, or that its sender has moved.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (this.sender || !player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof LineWarpReceiverBlockEntity receiver)) {
            return InteractionResult.PASS; // something in hand is used as it would be anywhere: a block is set down against it
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(says(level, receiver, player));
        }
        return InteractionResult.SUCCESS;
    }

    /** What a receiver tells {@code player} who clicks it with an empty hand. */
    public static Component says(Level level, LineWarpReceiverBlockEntity receiver, Player player) {
        if (receiver.owner() != null) {
            return Component.translatable(receiver.owner().equals(player.getUUID())
                    ? "message.nookworks.line_warp.yours" : "message.nookworks.line_warp.theirs");
        }
        GlobalPos link = receiver.link();
        if (link == null) {
            return Component.translatable(receiver.pairs() ? "message.nookworks.line_warp.unpaired" : "message.nookworks.line_warp.old");
        }
        boolean gone = link.dimension().equals(level.dimension()) && level.isLoaded(link.pos())
                && !(level.getBlockEntity(link.pos()) instanceof LineWarpSenderBlockEntity);
        return gone ? Component.translatable("message.nookworks.line_warp.gone", link.pos().getX(), link.pos().getY(), link.pos().getZ())
                : Boolean.TRUE.equals(receiver.senderMoved(level))
                ? Component.translatable("message.nookworks.line_warp.moved", link.pos().getX(), link.pos().getY(), link.pos().getZ())
                : !link.dimension().equals(level.dimension())
                ? Component.translatable("message.nookworks.line_warp.elsewhere", LodePearlItem.dimension(link))
                : Component.translatable("message.nookworks.line_warp.target", link.pos().getX(), link.pos().getY(), link.pos().getZ());
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || !this.sender ? null
                : createTickerHelper(type, ModBlockEntities.LINE_WARP_SENDER.get(), ItemWarpSenderBlockEntity::serverTick);
    }

    /** Comparators reading it through a block hear that it is gone, as with vanilla's containers. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return this.sender;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
