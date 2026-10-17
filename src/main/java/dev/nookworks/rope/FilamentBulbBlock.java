package dev.nookworks.rope;

import dev.nookworks.block.RopeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * Filament Bulb: a clear glass bulb with a glowing coil of filament in it, on a brass cap and a short black flex - the
 * kind a café hangs over its tables, a few at different heights. It hangs: from the underside of a block, a chain, or
 * the end of a Thin Rope (used on the rope, it goes on its end). Hung on a rope strung between posts, it is a string of
 * lights ({@link HungItemEntity}). Right-click it with an empty hand to switch it off and on (the user, 2026-10-16), with
 * the copper bulb's click; off, its coil is dark and it gives no light. Set down, it is on.
 */
public class FilamentBulbBlock extends Block {
    public static final int LIGHT_LEVEL = 13;
    /** Switched on: lit, its coil glowing. A bulb saved before it had a switch loads on. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /**
     * Whether a redstone signal reached it when it last looked. A signal coming switches it on, one going switches it off
     * - only as the signal changes, so a hand still switches it between (the user, 2026-10-16, as the posts switch the
     * lights on their lines, {@link RopeSignals}).
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(5.5, 2, 5.5, 10.5, 9, 10.5), Block.box(6.5, 9, 6.5, 9.5, 16, 9.5));

    public FilamentBulbBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, POWERED);
    }

    /** Set down, it notes the signal there as it stands, and stays on. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean movedByPiston) {
        if (!old.is(this) && level instanceof ServerLevel server && server.hasNeighborSignal(pos) != state.getValue(POWERED)) {
            server.setBlock(pos, state.setValue(POWERED, server.hasNeighborSignal(pos)), Block.UPDATE_CLIENTS);
        }
    }

    /** A signal come switches it on; a signal gone, off, with the copper bulb's click. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean signal = server.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            if (state.getValue(LIT) != signal) {
                server.playSound(null, pos, click(signal), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            server.setBlock(pos, state.setValue(POWERED, signal).setValue(LIT, signal), Block.UPDATE_ALL);
        }
    }

    /** Its light: a bulb's, switched on; none off. */
    public static int light(BlockState state) {
        return state.getValue(LIT) ? LIGHT_LEVEL : 0;
    }

    /** The click a bulb switched on or off makes: the copper bulb's. */
    public static SoundEvent click(boolean on) {
        return on ? SoundEvents.COPPER_BULB_TURN_ON : SoundEvents.COPPER_BULB_TURN_OFF;
    }

    /**
     * An empty hand switches it, as a button is pressed - by a player who may build there, as a lantern is put out only by
     * one (not in adventure mode, not in spawn protection or another's claim).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            BlockState switched = state.cycle(LIT);
            server.setBlock(pos, switched, Block.UPDATE_ALL);
            server.playSound(null, pos, click(switched.getValue(LIT)), SoundSource.BLOCKS, 1.0F, 1.0F);
            server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** It hangs from what is over it: a block's underside, a chain, a rope. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN) || above.getBlock() instanceof RopeBlock
                || above.getBlock() instanceof ChainBlock && above.getValue(ChainBlock.AXIS) == Direction.Axis.Y;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }
}
