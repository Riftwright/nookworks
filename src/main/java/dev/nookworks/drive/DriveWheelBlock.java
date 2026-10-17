package dev.nookworks.drive;

import java.util.EnumMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.MachineBlock;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Drive Wheel: a toothed iron wheel on an axle, placed as a Motor is (facing away from the face clicked). On a Motor's
 * shaft the motor turns it; set against a hand-worked machine, on the line it is driven by, it turns the machine as a
 * motor would; and a belt - Rope or Thin Chain - from wheel to wheel carries the turning between them, so one motor
 * turns several machines (the user, 2026-10-10). Use the belt on one wheel and then on another; sneak-use with an empty
 * hand to take its belts off. TURNING is the look alone: the wheel and its belts go round while a motor drives them.
 */
public class DriveWheelBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty TURNING = BooleanProperty.create("turning");

    /** Heard now and then while it turns, as a machine at work is, at one pitch. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(TURNING)) {
            MachineBlock.soundNowAndThen(level, pos, random, ModSounds.DRIVE_WHEEL_TURN.get(), true);
        }
    }

    /** The axle through the block and the wheel at its middle, for each axis. */
    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Direction.Axis.class);

    static {
        SHAPES.put(Direction.Axis.Z, Shapes.or(Block.box(7, 7, 0, 9, 9, 16), Block.box(2.5, 2.5, 7, 13.5, 13.5, 9)));
        SHAPES.put(Direction.Axis.X, Shapes.or(Block.box(0, 7, 7, 16, 9, 9), Block.box(7, 2.5, 2.5, 9, 13.5, 13.5)));
        SHAPES.put(Direction.Axis.Y, Shapes.or(Block.box(7, 0, 7, 9, 16, 9), Block.box(2.5, 7, 2.5, 13.5, 9, 13.5)));
    }

    public DriveWheelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TURNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TURNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
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
        return SHAPES.get(state.getValue(FACING).getAxis());
    }

    /** A belt in hand: tied on here, or tied off here to the wheel it was tied on at. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (BeltKind.of(stack) == null) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
            Belts.use(level, pos, player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    /** Sneaking with an empty hand: its belts come off, back into the hand. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof DriveWheelBlockEntity wheel) || wheel.belts().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            wheel.takeOff(player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DriveWheelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlockEntities.DRIVE_WHEEL.get(),
                level.isClientSide() ? DriveWheelBlockEntity::clientTick : DriveWheelBlockEntity::serverTick);
    }
}
