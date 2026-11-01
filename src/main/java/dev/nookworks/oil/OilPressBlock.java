package dev.nookworks.oil;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.block.MachineBlock;
import dev.nookworks.machine.Drive;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModOil;
import dev.nookworks.fluid.PipePorts;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * See {@link OilPressBlockEntity}. Right-click with seeds to load it, with a glass bottle or a bucket to pour oil off,
 * and with an empty hand to turn the handle (sneak to take the oil cakes out, or just to look at how it is doing).
 */
public class OilPressBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How much oil stands in the tray, for the model: none, some (under half the tank), plenty. */
    public static final IntegerProperty OIL =
            IntegerProperty.create("oil", 0, 2);
    /**
     * A Copper Pipe joined on that side, for the model: a copper port drawn on the tray's rim where the pipe meets it,
     * so the pipe no longer ends in the air beside the thin frame (the user, 2026-10-31: 「とりあえずそれでやってみて」).
     * By world side, not turned with the press.
     */
    public static final Map<Direction, BooleanProperty> PORTS = Map.of(Direction.NORTH, PipePorts.PORTS.get(Direction.NORTH),
            Direction.EAST, PipePorts.PORTS.get(Direction.EAST), Direction.SOUTH, PipePorts.PORTS.get(Direction.SOUTH),
            Direction.WEST, PipePorts.PORTS.get(Direction.WEST));
    // the tray, and the frame over it with its beam across x - facing north as the model is drawn; turned with it
    private static final Map<Direction, VoxelShape> SHAPES =
            Shapes.rotateHorizontal(Shapes.or(Block.box(1, 0, 1, 15, 4, 15), Block.box(2, 4, 6, 14, 16, 10)));

    public OilPressBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Drive.PROPERTY, Drive.NONE).setValue(OIL, 0);
        for (BooleanProperty port : PORTS.values()) {
            state = state.setValue(port, false);
        }
        this.registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Drive.PROPERTY, OIL);
        PORTS.values().forEach(builder::add);
    }

    /** {@code state} with a port on each side a Copper Pipe beside it is joined to it. */
    public static BlockState withPorts(BlockState state, LevelReader level, BlockPos pos) {
        return PipePorts.withPorts(state, level, pos);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withPorts(this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(Drive.PROPERTY, Drive.at(context.getLevel(), context.getClickedPos())), context.getLevel(), context.getClickedPos());
    }

    /** A Motor set beside it or taken away: the crank comes off for an axle to the motor, or goes back on. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level,
            ScheduledTickAccess ticks, BlockPos pos, Direction side, BlockPos neighbourPos,
            BlockState neighbourState, RandomSource random) {
        return withPorts(state.setValue(Drive.PROPERTY, Drive.at(level, pos)), level, pos);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return PipePorts.rotate(state, rotation).setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return PipePorts.mirror(state, mirror).setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OilPressBlockEntity press)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ItemStack filled = stack.is(Items.GLASS_BOTTLE) ? new ItemStack(ModOil.VEGETABLE_OIL_BOTTLE.get())
                : stack.is(Items.BUCKET) ? new ItemStack(ModOil.VEGETABLE_OIL_BUCKET.get()) : ItemStack.EMPTY;
        if (filled.isEmpty()) {
            // Seeds go in; anything else in hand is used as on any block, and the handle is left alone.
            InteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
            return result instanceof InteractionResult.TryEmptyHandInteraction && !stack.isEmpty() ? InteractionResult.PASS : result;
        }
        if (level.isClientSide()) {
            // The client does not know how much oil there is: the click ends here and the server decides. Had it gone
            // on, the bucket the server had just filled would be poured out in front of the press.
            return InteractionResult.SUCCESS;
        }
        int amount = stack.is(Items.BUCKET) ? 1_000 : OilFluids.BOTTLE;
        if (press.stored() < amount) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("nookworks.oil_press.too_little", press.stored(), amount)
                        .withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (!level.isClientSide()) {
            press.pour(amount);
            player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, filled));
            level.playSound(null, pos, stack.is(Items.BUCKET) ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // the handle is turned by hand; anything else in hand is used as on any block
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof OilPressBlockEntity press) {
            if (!player.isShiftKeyDown()) {
                press.turn(server);
            } else if (!press.getItem(OilPressBlockEntity.CAKE).isEmpty()) {
                ItemStack cakes = press.removeItemNoUpdate(OilPressBlockEntity.CAKE);
                press.setChanged();
                if (!player.getInventory().add(cakes)) {
                    player.drop(cakes, false, Prediction.PREDICTED);
                }
            }
            player.sendOverlayMessage(press.status());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OilPressBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.OIL_PRESS.get(), OilPressBlockEntity::serverTick);
    }
}
