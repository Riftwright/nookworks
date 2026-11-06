package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.NestedTimerBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.ExperimentalRedstoneUtils;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Nested Timer (a burst of pulses nested inside a longer cycle): pulses out of its front every so often - anything from half a second to an hour, set with sliders in
 * its screen (right-click), with as many pulses each time as it is set to, a set time apart: a dispenser with a water
 * bucket pours on the first and takes the water back on the second. A signal into its back or sides holds it. Like a repeater it faces
 * away from whoever places it and powers only the block in front, so it never hears its own pulse.
 * <p>
 * The count lives in {@link NestedTimerBlockEntity}; the block state carries the output and, for the meter on top,
 * roughly how long the interval is.
 */
public class NestedTimerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** How many bars the meter lights, less one: see {@link #bars}. */
    public static final IntegerProperty INTERVAL = IntegerProperty.create("interval", 0, 5);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** The interval, in ticks, from which the meter lights each further bar: 1 s, 2 s, 5 s, 30 s, 5 min. */
    private static final int[] BAR_FROM = {20, 40, 100, 600, 6_000};
    private static final VoxelShape SHAPE = Block.column(16.0, 0.0, 2.0);

    public NestedTimerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(INTERVAL, 1)
                .setValue(POWERED, false));
    }

    /** The meter's reading for an interval, 0-5: one bar for half a second, all six from five minutes up. */
    public static int bars(int ticks) {
        int bars = 0;
        for (int from : BAR_FROM) {
            if (ticks >= from) {
                bars++;
            }
        }
        return bars;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, INTERVAL, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    /** Right-click opens the settings. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NestedTimerBlockEntity timer) {
            if (player.getAbilities().mayBuild) {
                player.openMenu(timer);
            } else {
                player.sendOverlayMessage(Component.translatable("message.nookworks.nested_timer.may_not_build")); // never silent (2026-11-05)
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Held while anything powers its back or sides. */
    public boolean held(Level level, BlockPos pos, BlockState state) {
        Direction out = state.getValue(FACING).getOpposite();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (side != out && level.getSignal(pos.relative(side), side) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Tells the block in front (and what is around it) that the output changed. */
    public void updateFront(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        BlockPos front = pos.relative(facing.getOpposite());
        Orientation orientation = ExperimentalRedstoneUtils.initialOrientation(level, facing.getOpposite(), Direction.UP);
        level.neighborChanged(front, this, orientation);
        level.updateNeighborsAtExceptFromFacing(front, this, facing, orientation);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWERED) && state.getValue(FACING) == direction ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos,
            @Nullable Direction direction) {
        return direction != null && direction.getAxis().isHorizontal(); // the front out, the back and sides in
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston && state.getValue(POWERED)) {
            this.updateFront(level, pos, state);
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NestedTimerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.NESTED_TIMER.get()) {
            return null;
        }
        return (l, p, s, be) -> NestedTimerBlockEntity.serverTick(l, p, s, (NestedTimerBlockEntity) be);
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
