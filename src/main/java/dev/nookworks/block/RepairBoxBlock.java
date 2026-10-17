package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.experience.ExperienceBottling;
import dev.nookworks.experience.ExperienceFluids;
import dev.nookworks.experience.RepairBoxBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidUtil;

/**
 * Repair Box: a steel-plated chest that grinds the damage out of gear. See {@link RepairBoxBlockEntity}.
 * <p>
 * It behaves as a chest does - its lid opens with the screen, it needs no headroom rules of its own, and two set
 * side by side facing the same way join into a double (sneak to place one that will not join). A double is one
 * machine: twelve slots, and the power and experience of both halves go to whichever gear needs them.
 * <p>
 * It is not a {@code ChestBlock}: vanilla treats those as plain containers (hoppers merge the two halves and
 * ignore which items a machine lets out), and this one has to keep its own rules.
 */
public class RepairBoxBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<ChestType> TYPE = BlockStateProperties.CHEST_TYPE;

    private static final VoxelShape SINGLE = Block.column(14.0, 0.0, 14.0);
    private static final VoxelShape NORTH = Block.box(1.0, 0.0, 0.0, 15.0, 14.0, 15.0);
    private static final VoxelShape SOUTH = Block.box(1.0, 0.0, 1.0, 15.0, 14.0, 16.0);
    private static final VoxelShape WEST = Block.box(0.0, 0.0, 1.0, 15.0, 14.0, 15.0);
    private static final VoxelShape EAST = Block.box(1.0, 0.0, 1.0, 16.0, 14.0, 15.0);

    public RepairBoxBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TYPE, ChestType.SINGLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TYPE);
    }

    /** Which way the other half lies, the same convention vanilla chests use (so the same models fit). */
    public static Direction connectedDirection(BlockState state) {
        Direction facing = state.getValue(FACING);
        return state.getValue(TYPE) == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState state = this.defaultBlockState().setValue(FACING, facing);
        if (context.isSecondaryUseActive()) {
            return state;
        }
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        // A lone box beside this one, facing the same way, becomes its other half. Right is tried first, as a
        // player setting two down in a row usually works from left to right.
        for (Direction side : new Direction[] {facing.getClockWise(), facing.getCounterClockWise()}) {
            BlockState neighbour = level.getBlockState(pos.relative(side));
            if (neighbour.is(this) && neighbour.getValue(TYPE) == ChestType.SINGLE && neighbour.getValue(FACING) == facing) {
                return state.setValue(TYPE, side == facing.getClockWise() ? ChestType.LEFT : ChestType.RIGHT);
            }
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        if (!direction.getAxis().isHorizontal()) {
            return state;
        }
        ChestType type = state.getValue(TYPE);
        if (type == ChestType.SINGLE && neighbour.is(this) && neighbour.getValue(TYPE) != ChestType.SINGLE
                && neighbour.getValue(FACING) == state.getValue(FACING) && connectedDirection(neighbour) == direction.getOpposite()) {
            return state.setValue(TYPE, neighbour.getValue(TYPE).getOpposite());
        }
        if (type != ChestType.SINGLE && connectedDirection(state) == direction
                && !(neighbour.is(this) && neighbour.getValue(TYPE) == type.getOpposite())) {
            return state.setValue(TYPE, ChestType.SINGLE);
        }
        return state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(TYPE) == ChestType.SINGLE) {
            return SINGLE;
        }
        return switch (connectedDirection(state)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    /** The other half of a double, if this is one and the other half is really there. */
    public static @Nullable RepairBoxBlockEntity partner(Level level, BlockPos pos, BlockState state) {
        if (!state.hasProperty(TYPE) || state.getValue(TYPE) == ChestType.SINGLE) {
            return null;
        }
        BlockPos other = pos.relative(connectedDirection(state));
        BlockState otherState = level.getBlockState(other);
        return otherState.getBlock() instanceof RepairBoxBlock && otherState.getValue(TYPE) == state.getValue(TYPE).getOpposite()
                && level.getBlockEntity(other) instanceof RepairBoxBlockEntity box ? box : null;
    }

    /**
     * A bucket or bottle of Liquid Experience (or another mod's container of #c:experience) pours into the box, the
     * empty given back, as a Diesel Generator takes biodiesel: into the half clicked, or the other half of a pair when
     * that one is full. Any other liquid is turned away with a word and kept in hand. An empty bucket or bottle takes
     * nothing out - the box uses experience up, as the generator uses its fuel - and opens the screen, as anything
     * else in hand does.
     */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        FluidStack liquid = liquidIn(stack);
        if (liquid.isEmpty() || !(level.getBlockEntity(pos) instanceof RepairBoxBlockEntity box)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND; // nothing to pour: the screen opens
        }
        if (!liquid.is(ExperienceFluids.EXPERIENCE)) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.repair_box.experience_only"));
            }
            return Clicks.REFUSED;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS; // the client does not know how full the box is: the server decides
        }
        if (box.pourFrom(player, hand, stack)) {
            return InteractionResult.SUCCESS;
        }
        player.sendOverlayMessage(Component.translatable("message.nookworks.repair_box.no_room",
                box.pairExperience(), box.pairCapacity()));
        return Clicks.REFUSED; // and the bucket or bottle stays as it was
    }

    /** The liquid a filled bottle or bucket (or another mod's container) would pour; empty for anything else. */
    private static FluidStack liquidIn(ItemStack stack) {
        if (stack.is(ModItems.LIQUID_EXPERIENCE_BOTTLE.get())) {
            return new FluidStack(ExperienceFluids.SOURCE.get(), ExperienceBottling.BOTTLE);
        }
        return FluidUtil.getFirstStackContained(stack);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RepairBoxBlockEntity box) {
            RepairBoxBlockEntity.openFor(player, box);
        }
        return InteractionResult.SUCCESS;
    }

    /** The lid's open count is rechecked on a scheduled tick, as a chest's is. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof RepairBoxBlockEntity box) {
            box.recheckOpen();
        }
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        super.triggerEvent(state, level, pos, b0, b1);
        BlockEntity entity = level.getBlockEntity(pos);
        return entity != null && entity.triggerEvent(b0, b1);
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
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RepairBoxBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, ModBlockEntities.REPAIR_BOX.get(), RepairBoxBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntities.REPAIR_BOX.get(), RepairBoxBlockEntity::serverTick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state.rotate(mirror.getRotation(state.getValue(FACING)));
        return mirror == Mirror.NONE ? turned : turned.setValue(TYPE, state.getValue(TYPE).getOpposite());
    }
}
