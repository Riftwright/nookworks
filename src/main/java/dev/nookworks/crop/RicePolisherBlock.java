package dev.nookworks.crop;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.MachineBlock;
import dev.nookworks.machine.Drive;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * See {@link RicePolisherBlockEntity}. Right-click with brown rice to load it, and with an empty hand to turn the handle
 * (sneaking, to take out what it holds). Either way it says how it is doing. The white rice comes out of the front.
 */
public class RicePolisherBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 10, 14), Block.box(3, 10, 3, 13, 14, 13));

    public RicePolisherBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(Drive.PROPERTY, Drive.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Drive.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(Drive.PROPERTY, Drive.at(context.getLevel(), context.getClickedPos()));
    }

    /** A Motor set beside it or taken away: the crank comes off for an axle to the motor, or goes back on. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos neighbourPos,
            BlockState neighbourState, RandomSource random) {
        return state.setValue(Drive.PROPERTY, Drive.at(level, pos));
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // the handle is turned by hand; anything else in hand is used as on any block
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof RicePolisherBlockEntity polisher) {
            if (!player.isShiftKeyDown()) {
                polisher.turn(server);
            } else {
                var taken = polisher.takeOut();
                if (!taken.isEmpty()) {
                    level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
                }
                for (ItemStack out : taken) {
                    if (!player.getInventory().add(out)) {
                        player.drop(out, false, Prediction.PREDICTED);
                    }
                }
            }
            player.sendOverlayMessage(polisher.status());
        }
        return InteractionResult.SUCCESS;
    }

    /** Brown rice goes in; anything else in hand is used as on any block, and the handle is left alone. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
        return result instanceof InteractionResult.TryEmptyHandInteraction && !stack.isEmpty() ? InteractionResult.PASS : result;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RicePolisherBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.RICE_POLISHER.get(),
                RicePolisherBlockEntity::serverTick);
    }
}
