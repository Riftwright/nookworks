package dev.nookworks.block;

import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModRope;
import dev.nookworks.rope.HungItemEntity;
import dev.nookworks.rope.RopeHanging;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin Rope: a cord to hang things from, not to climb. It hangs and extends like the rope, and where it is tied to a
 * block rather than to more cord, a small black iron fitting holds it. A Hanging Planter or a Filament Bulb goes on its
 * end (the bulb used anywhere on the rope goes down to the end), and so does something to hang up - a bottle, a fish or
 * kelp to dry, a bunch of flowers, a banner ({@link dev.nookworks.rope.HungItemEntity}). Tied to fence posts it strings
 * from one to another ({@link dev.nookworks.rope.RopeSpans}).
 */
public class ThinRopeBlock extends RopeBlock {
    /** True on the top segment, tied to the block above: that one shows the fitting. */
    public static final BooleanProperty ANCHORED = BooleanProperty.create("anchored");
    private static final VoxelShape SHAPE = Block.column(2.0, 0.0, 16.0);

    public ThinRopeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ANCHORED, false).setValue(TIE, 0).setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ANCHORED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context).setValue(ANCHORED, !context.getLevel().getBlockState(context.getClickedPos().above()).is(this));
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
            Direction side, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        BlockState updated = super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
        return side == Direction.UP ? updated.setValue(ANCHORED, !neighbourState.is(this)) : updated;
    }

    /** A Filament Bulb goes on the rope's end; something to hang up hangs there; anything else as on any rope. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (!player.getAbilities().mayBuild) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        BlockPos end = this.bottomOf(level, pos);
        if (stack.is(ModRope.FILAMENT_BULB_ITEM.get())) {
            return hangBulb(stack, level, end.below(), player);
        }
        if (HungItemEntity.hangs(stack)) {
            return RopeHanging.underRope(level, player, end, stack);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    private static InteractionResult hangBulb(ItemStack stack, Level level, BlockPos target, Player player) {
        BlockState bulb = ModRope.FILAMENT_BULB.get().defaultBlockState();
        if (level.isOutsideBuildHeight(target) || !level.getBlockState(target).canBeReplaced() || !bulb.canSurvive(level, target)) {
            return Clicks.REFUSED;
        }
        if (!level.getEntitiesOfClass(HungItemEntity.class, new AABB(target)).isEmpty()) {
            // a bottle or a fish hanging there already: the bulb is not set down through it
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.rope.end_taken").withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (level instanceof ServerLevel server) {
            if (!Permission.mayPlace(server, target, Direction.UP, player)) {
                return Clicks.REFUSED;
            }
            level.setBlock(target, bulb, Block.UPDATE_ALL);
            level.playSound(null, target, bulb.getSoundType(level, target, player).getPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, target);
            stack.consume(1, player);
        }
        return InteractionResult.SUCCESS;
    }
}
