package dev.nookworks.block;

import dev.nookworks.feature.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A climbable rope that hangs down from a block or from another rope.
 * <ul>
 * <li>Use a rope on a rope: extends the rope at its bottom end.</li>
 * <li>Use a rope with an empty hand: pulls the bottom-most segment back up.</li>
 * <li>Losing support from above: the rope below falls apart segment by segment.</li>
 * </ul>
 * It hangs from anything with something solid straight above its middle: a full block's underside, and also a
 * support's beam or strut, a top slab, a fence - where that underside is higher up, {@link #TIE} holds how far the
 * rope reaches up to it. It hangs in water too (down a well, into the sea) as a ladder does.
 */
public class RopeBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED =
            BlockStateProperties.WATERLOGGED;
    private static final VoxelShape SHAPE = Block.column(4.0, 0.0, 16.0);
    /** How many pixels the top segment reaches up into the block above, to that block's underside (0: flush). */
    public static final IntegerProperty TIE = IntegerProperty.create("tie", 0, 15);
    /** The rope's own width: what it hangs from has to be somewhere over this. */
    private static final VoxelShape MIDDLE = Block.column(4.0, 0.0, 16.0);
    /** Upper bound so a very long rope can never make one click loop for too long. */
    private static final int MAX_SCAN = 512;

    public RopeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIE, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIE, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.inWater(this.defaultBlockState().setValue(TIE, Math.max(0, this.tie(context.getLevel(), context.getClickedPos()))),
                context.getLevel(), context.getClickedPos());
    }

    /** {@code state}, waterlogged if it goes where water stands. */
    protected BlockState inWater(BlockState state, LevelReader level, BlockPos pos) {
        return state.setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    /**
     * How far up a rope at {@code pos} reaches to hang from what is above it: 0 from more rope or a flat underside,
     * the height of the lowest solid part over its middle otherwise, or -1 when there is nothing there to hang from.
     */
    public int tie(LevelReader level, BlockPos pos) {
        BlockPos above = pos.above();
        BlockState state = level.getBlockState(above);
        if (state.is(this) || state.isFaceSturdy(level, above, Direction.DOWN, SupportType.CENTER)) {
            return 0;
        }
        VoxelShape over = Shapes.join(state.getCollisionShape(level, above), MIDDLE, BooleanOp.AND);
        if (over.isEmpty()) {
            return -1;
        }
        int px = (int) Math.round(over.min(Direction.Axis.Y) * 16);
        return px < 16 ? px : -1;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return this.tie(level, pos) >= 0;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER,
                    Fluids.WATER.getTickDelay(level));
        }
        if (directionToNeighbour == Direction.UP) {
            int tie = this.tie(level, pos);
            if (tie < 0) {
                // Break on the next tick so a long rope unravels visibly instead of vanishing at once.
                ticks.scheduleTick(pos, this, 1);
            } else {
                return state.setValue(TIE, tie);
            }
        }
        return state;
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.canSurvive(level, pos)) {
            level.destroyBlock(pos, true);
        }
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!stack.is(this.asItem())) {
            // Only a bare hand pulls the rope up. Anything else in hand - a torch, a block to build with - is
            // used as it would be on any other block, rather than the rope being reeled in from under it.
            return stack.isEmpty() ? InteractionResult.TRY_WITH_EMPTY_HAND : InteractionResult.PASS;
        }
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS;
        }
        BlockPos target = bottomOf(level, pos).below();
        if (level.isOutsideBuildHeight(target) || !level.getBlockState(target).canBeReplaced()) {
            return Clicks.REFUSED;
        }
        if (level instanceof ServerLevel server) {
            // The far end may be a long way down from the block clicked: ask there too (claims, spawn protection).
            if (!Permission.mayPlace(server, target, Direction.UP, player)) {
                return Clicks.REFUSED;
            }
            BlockState placed = this.inWater(this.defaultBlockState(), level, target); // hangs from more rope: flush
            level.setBlock(target, placed, Block.UPDATE_ALL);
            level.playSound(null, target, placed.getSoundType(level, target, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.9F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // the rope is pulled up by hand; a torch in hand is placed, the rope left hanging
        }
        if (!player.getAbilities().mayBuild) {
            return InteractionResult.PASS; // an adventure map's ropes stay where the map put them
        }
        BlockPos bottom = bottomOf(level, pos);
        if (level instanceof ServerLevel server) {
            if (!Permission.mayBreak(server, bottom, player)) {
                return Clicks.REFUSED; // the bottom segment hangs where the player may not break blocks
            }
            level.removeBlock(bottom, false);
            level.playSound(null, bottom, state.getSoundType(level, bottom, player).getBreakSound(), SoundSource.BLOCKS, 1.0F, 1.1F);
            level.gameEvent(player, GameEvent.BLOCK_DESTROY, bottom);
            if (!player.getAbilities().instabuild) {
                ItemStack rope = new ItemStack(this.asItem());
                if (!player.getInventory().add(rope)) {
                    player.drop(rope, false, Prediction.PREDICTED);
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Returns the lowest rope segment connected below {@code pos}. */
    public BlockPos bottomOf(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < MAX_SCAN && level.getBlockState(cursor.below()).is(this); i++) {
            cursor.move(Direction.DOWN);
        }
        return cursor.immutable();
    }
}
