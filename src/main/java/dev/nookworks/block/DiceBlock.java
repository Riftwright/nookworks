package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.DiceBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Dice: a small random number generator. Right-click it, or give it a redstone pulse, and it is tossed up where it
 * lies, tumbles, bounces and comes to rest on 1 to 6 - rolled as a real die rolls ({@link DiceTumble}) - where it
 * comes to rest, turned as it lies: drawn there by {@code DiceRenderer}, rolling or not, and its shape there
 * ({@link DiceBlockEntity#shape}; the block's shape is dynamic). A comparator reads the number; while it is rolling the
 * comparator reads 0, so a circuit sees one clean change when the roll is decided.
 */
public class DiceBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final IntegerProperty VALUE = IntegerProperty.create("value", 1, 6);
    /** Above 0 while the die is rolling (drawn by its renderer then, not its model); 0 once it has settled. */
    public static final IntegerProperty ROLL = IntegerProperty.create("roll", 0, 4);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    public DiceBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VALUE, 1)
                .setValue(ROLL, 0).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VALUE, ROLL, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof DiceBlockEntity die ? die.shape(state) : SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            roll(level, pos, state);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED)) {
            BlockState next = state.setValue(POWERED, powered);
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
            if (powered) {
                roll(level, pos, next);
            }
        }
    }

    /** Starts a roll, tossed up where it stands. A die already rolling just keeps rolling. */
    public static void roll(Level level, BlockPos pos, BlockState state) {
        roll(level, pos, state, null);
    }

    /**
     * Starts a roll - with {@code thrownTowards}, as thrown in from the side (by a dispenser) - its whole course worked
     * out now, the number kept for the tick it comes to rest. A die already rolling just keeps rolling.
     */
    public static void roll(Level level, BlockPos pos, BlockState state, @Nullable Direction thrownTowards) {
        if (state.getValue(ROLL) > 0) {
            settleIfStuck(level, pos, state);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof DiceBlockEntity die)) {
            return;
        }
        DiceTumble.Result tumble = die.start(level.getRandom().nextLong(), level.getGameTime(), die.lying(state), thrownTowards);
        level.setBlock(pos, state.setValue(ROLL, 1), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), Math.max(1, tumble.ticks()));
        level.updateNeighbourForOutputSignal(pos, state.getBlock());
        level.playSound(null, pos, dev.nookworks.registry.ModSounds.DICE_ROLL.get(), SoundSource.BLOCKS, 0.6F, 1.6F);
    }

    /**
     * A die rolling with no tick coming to stop it - saved mid-roll in a structure, or copied by a tool that leaves the
     * ticks behind - would roll for ever, its comparator at 0, clicks and pulses doing nothing (r7 review): it comes to
     * rest on the next tick.
     */
    private static void settleIfStuck(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(ROLL) > 0 && !level.isClientSide() && !level.getBlockTicks().hasScheduledTick(pos, state.getBlock())) {
            level.scheduleTick(pos, state.getBlock(), 1);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) { // set down rolling, as a structure does; a roll begun here has its tick
            settleIfStuck(level, pos, state);
        }
    }

    /**
     * The roll is over: it shows the number it came to rest on, facing the quarter turn nearest the way it lies. (A
     * roll from before rolls were worked out keeps its face.)
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(ROLL) <= 0) {
            return;
        }
        BlockState settled = state.setValue(ROLL, 0);
        if (level.getBlockEntity(pos) instanceof DiceBlockEntity die && die.tumble() != null) {
            settled = settled.setValue(VALUE, die.tumble().value()).setValue(FACING, die.tumble().facing());
        }
        level.setBlock(pos, settled, Block.UPDATE_ALL);
        level.updateNeighbourForOutputSignal(pos, this);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiceBlockEntity(pos, state);
    }

    /** On the client only: the clack of its corners striking as it is drawn rolling. */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() && type == ModBlockEntities.DICE.get()
                ? (BlockEntityTicker<T>) (BlockEntityTicker<DiceBlockEntity>) DiceBlockEntity::clientTick : null;
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
        return state.getValue(ROLL) > 0 ? 0 : state.getValue(VALUE);
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
