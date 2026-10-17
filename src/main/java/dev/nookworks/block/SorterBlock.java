package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.sorter.SorterBlockEntity;
import dev.nookworks.sorter.SorterGear;
import dev.nookworks.sorter.SorterTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Item Sorter or Precision Sorter. Items go in from the top (or any side), and it draws them itself from a container
 * above it, as a hopper does; items registered in the GUI leave through the right-hand outlet, everything else through
 * the left, as seen when looking at the front. SWAPPED mirrors that. GEAR shows the Sorter Gear fitted, on the front's
 * panel. See {@link SorterBlockEntity}.
 */
public class SorterBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty SWAPPED = BooleanProperty.create("swapped");
    /** The gear fitted, as it shows: kept to what the block entity holds. */
    public static final EnumProperty<SorterGear> GEAR = EnumProperty.create("gear", SorterGear.class);

    public final SorterTier tier;

    public SorterBlock(SorterTier tier, BlockBehaviour.Properties properties) {
        super(properties);
        this.tier = tier;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SWAPPED, false)
                .setValue(GEAR, SorterGear.NONE));
    }

    /** One sorter turning into another (an old one made an Item Sorter) keeps what it holds, filter and all. */
    @Override
    protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return oldState.getBlock() instanceof SorterBlock;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SWAPPED, GEAR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Where registered items leave. "Right" is the viewer's right when looking at the front face. */
    public static Direction registeredSide(BlockState state) {
        Direction facing = state.getValue(FACING);
        Direction right = facing.getCounterClockWise();
        return state.getValue(SWAPPED) ? right.getOpposite() : right;
    }

    public static Direction otherSide(BlockState state) {
        return registeredSide(state).getOpposite();
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SorterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SORTER.get(), SorterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SorterBlockEntity sorter) {
            player.openMenu(sorter);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Mirrored (a structure placed mirrored), its front turns as a rotation would, and its hand changes too: the
     * registered outlet stays by the chest it fed, now on the other hand (it fed the other chest, r7 review).
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state.rotate(mirror.getRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? turned : turned.cycle(SWAPPED);
    }
}
