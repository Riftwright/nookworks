package dev.nookworks.cooking;

import dev.nookworks.block.MachineBlock;
import dev.nookworks.machine.Drive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A kitchen machine with a handle ({@link HandMachineBlockEntity}): right-click with what it works to load it, with an
 * empty hand to turn the handle, sneaking with an empty hand to take out what it has made. A Motor set against it turns
 * it instead (or a Drive Wheel a Motor turns by a belt), by an axle to it.
 */
public abstract class HandMachineBlock extends MachineBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected HandMachineBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Drive.PROPERTY, Drive.NONE));
    }

    /**
     * Its outline facing {@code facing}: each one's shape is written facing north, as its model is drawn (the blockstate
     * turns the model for the others), and turned with {@code Shapes.rotateHorizontal} once, when the class loads.
     */
    protected abstract VoxelShape shape(Direction facing);

    /** Whether it is driven from above or below (the quern's spindle) rather than from its sides. */
    protected boolean drivenVertically() {
        return false;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Drive.PROPERTY);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(Drive.PROPERTY, Drive.at(context.getLevel(), context.getClickedPos(), this.drivenVertically()));
    }

    /** A Motor or a Drive Wheel set against it or taken away: the axle to it shows, or goes. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(Drive.PROPERTY, Drive.at(level, pos, this.drivenVertically()));
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
        return this.shape(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // the handle is turned by hand; anything else in hand is used as on any block
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof HandMachineBlockEntity machine) {
            if (!player.isShiftKeyDown()) {
                machine.turn(server);
            } else {
                ItemStack out = machine.takeOut();
                if (!out.isEmpty() && !player.getInventory().add(out)) {
                    player.drop(out, false, Prediction.PREDICTED);
                }
            }
            player.sendOverlayMessage(machine.status());
        }
        return InteractionResult.SUCCESS;
    }

    /** What it works goes in; anything else in hand is used as on any block, and the handle is left alone. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        InteractionResult result = super.useItemOn(stack, state, level, pos, player, hand, hit);
        return result instanceof InteractionResult.TryEmptyHandInteraction && !stack.isEmpty() ? InteractionResult.PASS : result;
    }
}
