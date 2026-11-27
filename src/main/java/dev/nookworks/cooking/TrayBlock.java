package dev.nookworks.cooking;

import dev.nookworks.registry.ModSounds;
import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModBlockEntities;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wooden Tray: a board with a low rim, set down on a table or counter. It takes up to four lots of things to eat
 * ({@link TrayBlockEntity}): Bread Dough rises on it - dough is not set down on the bare table any more - and loaves,
 * a bowl of rice or a cutlet sit on it to be served. The food in hand goes on; an empty hand takes the last off, and
 * sneaking lifts the tray with everything on it, to carry. It goes with the table under it, and what was on it with it.
 */
public class TrayBlock extends BaseEntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 2, 15);

    public TrayBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
        return SHAPE;
    }

    /** It stands on anything with a top to put it on - a hopper's rim too, so that one underneath can empty it. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP) || Block.canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, level, ticks, pos, side, neighbourPos, neighbourState, random);
    }

    /** Food goes on. Anything else is used as it would be on any block. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!TrayBlockEntity.fits(stack) || !(level.getBlockEntity(pos) instanceof TrayBlockEntity tray)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!tray.takes(stack)) { // the client has what is on it too, so both sides agree
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable(tray.wantsPlate(stack) ? "message.nookworks.tray.wants_plate"
                        : "message.nookworks.tray.full").withStyle(ChatFormatting.RED));
            }
            return Clicks.REFUSED;
        }
        if (!level.isClientSide()) {
            boolean dough = stack.is(ModKitchen.BREAD_DOUGH.get());
            tray.place(level, player, stack);
            level.playSound(null, pos, dough ? ModSounds.TRAY_DOUGH.get() : ModSounds.TRAY_KNOCK.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * An empty hand takes the last thing off. Into the inventory, not the empty hand that took it: there the next click
     * put it back on the tray, and a tray of risen dough came off one, went back, came off again (the user, 2026-10-12:
     * "ふつうに一個ずつ手元に拾って欲しい").
     * <p>
     * Sneaking, the hand lifts the tray itself with all that is on it ({@link TrayBlockEntity#lift}; the user,
     * 2026-11-14) - into the hand this time: it is carried there, and set down again as a tray is. It used to take
     * everything off into the inventory, which the tray carried there does as well, and keeps the meal laid.
     * Lifting takes a block away: not in adventure mode, nor on land that is not theirs to break.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof TrayBlockEntity tray) || tray.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (!player.mayBuild()) {
                return InteractionResult.PASS;
            }
            if (level instanceof ServerLevel server) {
                if (!Permission.mayBreak(server, pos, player)) {
                    return Clicks.REFUSED;
                }
                ItemStack lifted = tray.lift();
                level.removeBlock(pos, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, lifted);
                // lifted, not broken: the tray's own knock, lower (it was a wooden block breaking, and said so in subtitles)
                level.playSound(null, pos, ModSounds.TRAY_KNOCK.get(), SoundSource.BLOCKS, 0.45F, 0.9F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            ItemStack off = tray.take();
            if (!player.getInventory().add(off)) {
                player.drop(off, false, Prediction.PREDICTED);
            }
            keepHandEmpty(player.getInventory());
            level.playSound(null, pos, ModSounds.TRAY_KNOCK.get(), SoundSource.BLOCKS, 0.3F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    /** What went into the hand's own slot moves to another free one, if there is one. Public for tests. */
    public static void keepHandEmpty(Inventory inventory) {
        int hand = inventory.getSelectedSlot();
        ItemStack held = inventory.getItem(hand);
        if (held.isEmpty()) {
            return;
        }
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            if (slot != hand && inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, held);
                inventory.setItem(hand, ItemStack.EMPTY);
                return;
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TrayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.TRAY.get(), TrayBlockEntity::serverTick);
    }

    /**
     * Broken, it tells a comparator reading it through a block, as a chest does: one there kept its last reading, the
     * block gone (r23 self-review: a removal tells comparators only when what takes its place gives a reading).
     */
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos,
            boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        net.minecraft.world.Containers.updateNeighboursAfterDestroy(state, level, pos);
    }

    /** A comparator reads how full it is ({@link ComparatorReading}). */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof TrayBlockEntity be ? ComparatorReading.of(be.itemHandler()) : 0;
    }
}
