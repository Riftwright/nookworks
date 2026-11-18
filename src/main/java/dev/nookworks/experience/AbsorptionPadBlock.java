package dev.nookworks.experience;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.PipeSides;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LevelAccessor;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import dev.nookworks.port.core.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * See {@link AbsorptionPadBlockEntity}. A flat pad, one pixel thick, so a player stands on top of it. A redstone
 * signal - a lever beside it - switches it off ({@link #POWERED}), and its intake goes dark.
 * <p>
 * An Experience Pipe beside it or above it gets a riser: a short upright length of pipe from the plate up to the
 * pipe, so the pipe does not hang in the air a few pixels above a plate it is not touching. The side is a block
 * state, like a pipe's own arms, so the model can show it.
 */
public class AbsorptionPadBlock extends BaseEntityBlock implements RemovalHooks {
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final Map<Direction, BooleanProperty> RISERS = Map.of(
            Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH, Direction.WEST, WEST, Direction.UP, UP);
    private static final VoxelShape PLATE = Block.box(0, 0, 0, 16, 1, 16);
    private static final Map<Direction, VoxelShape> RISER_SHAPES = Map.of(
            Direction.NORTH, Block.box(4, 1, 0, 12, 12, 7),
            Direction.SOUTH, Block.box(4, 1, 9, 12, 12, 16),
            Direction.WEST, Block.box(0, 1, 4, 7, 12, 12),
            Direction.EAST, Block.box(9, 1, 4, 16, 12, 12),
            Direction.UP, Block.box(4, 1, 4, 12, 16, 12));

    public AbsorptionPadBlock(Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (BooleanProperty riser : RISERS.values()) {
            state = state.setValue(riser, false);
        }
        this.registerDefaultState(state.setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, POWERED);
    }

    /** A signal switches it off while it lasts, as a lever by it would be expected to. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
            boolean moving) {
        super.neighborChanged(state, level, pos, block, fromPos, moving);
        boolean powered = level.hasNeighborSignal(pos);
        if (!level.isClientSide() && powered != state.getValue(POWERED)) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    public static BooleanProperty riser(Direction side) {
        return RISERS.get(side);
    }

    /** A pipe at {@code pos} joined back towards the pad (not switched off on that side). */
    private static boolean pipeAt(LevelReader level, BlockPos pos, Direction fromPad) {
        return joinedPipe(level.getBlockState(pos), fromPad);
    }

    private static boolean joinedPipe(BlockState pipe, Direction fromPad) {
        return pipe.getBlock() instanceof ExperiencePipeBlock && !PipeSides.isOff(pipe, fromPad.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
        for (var entry : RISERS.entrySet()) {
            state = state.setValue(entry.getValue(), pipeAt(context.getLevel(), context.getClickedPos().relative(entry.getKey()), entry.getKey()));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        BooleanProperty riser = RISERS.get(side);
        return riser == null ? state : state.setValue(riser, joinedPipe(neighbourState, side));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = PLATE;
        for (var entry : RISERS.entrySet()) {
            if (state.getValue(entry.getValue())) {
                shape = Shapes.or(shape, RISER_SHAPES.get(entry.getKey()));
            }
        }
        return shape;
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AbsorptionPadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null
                : createTickerHelper(type, ModBlockEntities.ABSORPTION_PAD.get(), AbsorptionPadBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // an empty hand reads it; anything else is used on it as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof AbsorptionPadBlockEntity pad) {
            player.displayClientMessage(Component.translatable("nookworks.experience.amount", pad.stored(), AbsorptionPadBlockEntity.BUFFER), true);
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
    protected MapCodec<? extends AbsorptionPadBlock> codec() {
        return BlockCodecs.of(this);
    }
}
