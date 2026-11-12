package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModSounds;
import dev.nookworks.rope.HungItemEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wind Chime: a garden chime of metal tubes under a wooden disc, hung under a block as a lantern is - from a beam, an eave,
 * a fence or a chain - or, as before, from a Thin Rope's end or on a line, where it hangs as its block too
 * (#nookworks:hangs_as_lantern). It was a picture hung on a string; the user, 2026-11-12: 「ちゃんとブロックとして作ろう。
 * アイコンぶら下げるだけはダサすぎる。」 It chimes now and then as a hung one does ({@link HungItemEntity#windChime}): every
 * fifteen seconds or so in the open, more in rain, more again in a storm, seldom under a roof - heard on the client, from
 * the display ticks the world gives blocks near the player (as a campfire's crackle), so it needs no block entity.
 */
public class WindChimeBlock extends HorizontalDirectionalBlock {
    private static final VoxelShape SHAPE = Block.box(4.5, 0.5, 4.5, 11.5, 16, 11.5);
    /**
     * How often the world's display tick reaches a given block within sixteen of the player, a tick: 667 tries in the
     * 32-block cube round them, and 667 more in the 64-block one (ClientLevel#animateTick). A chime's chance a tick,
     * divided by this, is its chance a display tick.
     */
    static final float DISPLAY_TICKS = 667F / (32 * 32 * 32) + 667F / (64 * 64 * 64);

    public WindChimeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Hung under the block over where it goes, its sail toward the player; nowhere else. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** Under anything that holds a lantern up: a full underside, a fence, a chain, a wall. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
            BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
        return direction == Direction.UP && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** A chime now and then, as a hung chime's ({@link HungItemEntity#windChime}), from its tubes. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        float chance = HungItemEntity.windChime(level.canSeeSky(pos), level.isRaining(), level.isThundering()) / DISPLAY_TICKS;
        if (random.nextFloat() < chance) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, ModSounds.WIND_CHIME_CHIME.get(),
                    SoundSource.BLOCKS, 1.0F, 1.0F, false);
        }
    }
}
