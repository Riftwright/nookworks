package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.storage.StorageBlockEntity;
import dev.nookworks.storage.StorageTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Storage Barrel block. See {@link StorageBlockEntity} and {@link StorageTier}.
 * <p>
 * A barrel rather than a chest on purpose: it is a solid cube with a lid, so it opens with a block sitting on
 * top of it and it points wherever it was placed, including up and down. Vanilla barrels work the same way,
 * and calling it a chest would promise the chest's rules - needing headroom, joining into a double - that this
 * block does not keep.
 */
public class StorageBlock extends BaseEntityBlock {
    /** The openers counter schedules this to find players who left without closing the screen. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos,
            RandomSource random) {
        if (level.getBlockEntity(pos) instanceof StorageBlockEntity barrel) {
            barrel.recheckOpen();
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    /** Somebody has it open: the lid is drawn ajar and the sounds play. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Too full to break (see StorageBlockEntity#updateLock); a block state so the client refuses to start mining. */
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public final StorageTier tier;

    public StorageBlock(StorageTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(LOCKED, false).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOCKED, OPEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StorageBlockEntity storage) {
            player.openMenu(storage); // the tier goes with it (StorageBlockEntity.writeClientSideData)
        }
        return InteractionResult.SUCCESS;
    }

    /** Set down from an item full to bursting, it shows as locked at once, not only once somebody hits it. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.getBlockEntity(pos) instanceof StorageBlockEntity storage) {
            storage.updateLock();
        }
    }

    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return state.getValue(LOCKED) ? 0.0F : super.getDestroyProgress(state, player, level, pos);
    }

    /** Comparators reading it through a block hear that it is gone, as with vanilla's containers. */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof StorageBlockEntity storage ? storage.inventory().comparatorSignal() : 0;
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
