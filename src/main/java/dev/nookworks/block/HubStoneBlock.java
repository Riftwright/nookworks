package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneWireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.redstone.Orientation;

/**
 * Hub Stone: a full block that carries redstone like dust, but in all six directions. Its power is the
 * strongest signal around it, where a neighbouring Hub Stone counts one less than its own power; it then powers
 * every neighbour (strongly, like dust pointing into a block). While it measures its surroundings it emits
 * nothing, the same trick vanilla dust uses, so it never feeds on its own signal.
 */
public class HubStoneBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;
    /**
     * Cleared while this block works out its own power, so the neighbours it asks do not ask it back.
     * <p>
     * Vanilla redstone dust does exactly this, with the same caveat: it is per-block, not per-position, so a
     * hub stone recomputing inside another hub stone's update sees the flag already down. That is the behaviour dust has
     * had for years and what any contraption built against dust will expect.
     */
    private boolean shouldSignal = true;

    public HubStoneBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    /**
     * Its power worked out on the server only: the flag that keeps it from reading itself is the block's one field, and
     * in single player the client's placement working it out at the same moment as the server's update could lower it
     * under the server's feet. The client is sent the state.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return context.getLevel().isClientSide() ? this.defaultBlockState()
                : this.defaultBlockState().setValue(POWER, this.computePower(context.getLevel(), context.getClickedPos()));
    }

    /** Strongest input: other blocks' signals as they are, neighbouring Hub Stones minus one. */
    public int computePower(Level level, BlockPos pos) {
        this.shouldSignal = false;
        int best = 0;
        for (Direction dir : Direction.values()) {
            BlockPos at = pos.relative(dir);
            BlockState neighbour = level.getBlockState(at);
            if (neighbour.is(this)) {
                best = Math.max(best, neighbour.getValue(POWER) - 1);
            } else if (neighbour.is(Blocks.REDSTONE_WIRE)) {
                // Dust passes on one less, as it does to other dust. Read at full strength it would hand the Hub Stone
                // back the signal the Hub Stone gave it, and the pair would stay on after the lever was off.
                best = Math.max(best, neighbour.getValue(RedstoneWireBlock.POWER) - 1);
            } else {
                best = Math.max(best, level.getSignal(at, dir));
            }
        }
        this.shouldSignal = true;
        return Math.max(0, Math.min(15, best));
    }

    private void update(Level level, BlockPos pos, BlockState state) {
        int power = this.computePower(level, pos);
        if (power != state.getValue(POWER) && level.getBlockState(pos) == state) {
            level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_CLIENTS);
            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        if (!level.isClientSide()) {
            this.update(level, pos, state);
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!oldState.is(state.getBlock()) && !level.isClientSide()) {
            this.update(level, pos, state);
            level.updateNeighborsAt(pos, this);
            // Set down already powered (its placement read its inputs), update() found nothing to change, and what it
            // powers through a block beside it - a lamp past a stone - was never told; as when its power changes, the
            // blocks beside each neighbour hear of it too (r7 review)
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        if (!movedByPiston) {
            level.updateNeighborsAt(pos, this);
            for (Direction dir : Direction.values()) {
                level.updateNeighborsAt(pos.relative(dir), this);
            }
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return this.shouldSignal;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.shouldSignal ? state.getValue(POWER) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return this.getSignal(state, level, pos, direction);
    }
}
