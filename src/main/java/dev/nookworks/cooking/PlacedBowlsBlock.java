package dev.nookworks.cooking;

import dev.nookworks.registry.ModSounds;
import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Dishes in bowls set straight down on a table (the user, 2026-11-14: 既に器に入っているのは、皿に置かずに、直接ワールドに
 * 置ける方が自然 - a bowl on a plate was a dish on a dish). Sneak and right-click the top of a block with a dish in a bowl
 * ({@link Plating#BOWL_DISHES}: rice, a soup, udon, vanilla's stews) and it stands there, as a bottle does
 * ({@code PlacedBottlesBlock}); a second beside it - the rice and the soup - and no more: four bowls to a block would be
 * dolls' bowls. An empty hand takes the last one back up, into the inventory. Without sneaking the dish is eaten, as
 * ever. What stays off the bare table is food with no dish of its own: that is for a Plate or a Wooden Tray, as before
 * (the user, 2026-10-31: 直接置くのは、なんか汚い).
 */
public class PlacedBowlsBlock extends BaseEntityBlock {
    public static final int MAX = 2;
    public static final IntegerProperty BOWLS = IntegerProperty.create("bowls", 1, MAX);
    /** The side whoever set the first one down stood on: two stand side by side as seen from there. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Two bowls stand this far either side of the middle, in pixels, and each is drawn this large. */
    public static final float APART = 3.5F;
    public static final float SCALE = 0.85F;
    private static final VoxelShape ONE = Block.box(4.5, 0, 4.5, 11.5, 4, 11.5);
    private static final VoxelShape TWO_X = Block.box(1, 0, 4.5, 15, 4, 11.5);
    private static final VoxelShape TWO_Z = Block.box(4.5, 0, 1, 11.5, 4, 15);

    public PlacedBowlsBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(BOWLS, 1).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BOWLS, FACING);
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
        return state.getValue(BOWLS) == 1 ? ONE : state.getValue(FACING).getAxis() == Direction.Axis.Z ? TWO_X : TWO_Z;
    }

    /** As a plate: on anything with a top to put it on. */
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

    /** A second dish in a bowl goes beside the first with a plain click too. Anything else is used as on any block. */
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!Plating.inBowl(stack) || state.getValue(BOWLS) >= MAX || !player.mayBuild()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            setDown(level, pos, hit.getDirection(), player, stack);
        }
        return InteractionResult.SUCCESS;
    }

    /** An empty hand takes the last one back up, into the inventory; with none left the block goes. Not in adventure mode. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !player.mayBuild()
                || !(level.getBlockEntity(pos) instanceof PlacedBowlsBlockEntity bowls)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack taken = bowls.take();
            if (!player.getInventory().add(taken)) {
                player.drop(taken, false, Prediction.PREDICTED);
            }
            TrayBlock.keepHandEmpty(player.getInventory());
            level.playSound(null, pos, ModSounds.TRAY_KNOCK.get(), SoundSource.BLOCKS, 0.3F, 1.25F);
            refresh(level, pos, bowls);
        }
        return InteractionResult.SUCCESS;
    }

    /** The block's state to match its bowls: how many; none, and it goes. */
    static void refresh(Level level, BlockPos pos, PlacedBowlsBlockEntity bowls) {
        int count = bowls.count();
        if (count == 0) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        } else {
            level.setBlock(pos, level.getBlockState(pos).setValue(BOWLS, count), Block.UPDATE_ALL);
        }
    }

    /**
     * Where {@code held}'s bowl would stand, clicking {@code clicked}'s {@code face}: beside the one there, or on top of
     * {@code clicked}; null when it cannot be set down (not a dish in a bowl, no room, water there, nothing to stand on).
     */
    public static @Nullable BlockPos placeFor(Level level, BlockPos clicked, Direction face, ItemStack held) {
        if (!Plating.inBowl(held)) {
            return null;
        }
        Block block = ModKitchen.PLACED_BOWLS.get();
        BlockPos at = level.getBlockState(clicked).is(block) ? clicked : face == Direction.UP ? clicked.above() : null;
        if (at == null) {
            return null;
        }
        BlockState there = level.getBlockState(at);
        if (there.is(block)) {
            return there.getValue(BOWLS) < MAX ? at : null;
        }
        return there.canBeReplaced() && there.getFluidState().isEmpty() && block.defaultBlockState().canSurvive(level, at) ? at : null;
    }

    /**
     * Sets {@code held}'s bowl down on top of {@code clicked}, or beside the one there; whether it did. A new one goes up
     * as a block on the one clicked, so claim mods and spawn protection are asked about it, as the player putting a block
     * there (as for a bottle: r15). Public for tests.
     */
    public static boolean setDown(Level level, BlockPos clicked, Direction face, Player player, ItemStack held) {
        BlockPos at = placeFor(level, clicked, face, held);
        if (at == null) {
            return false;
        }
        Block block = ModKitchen.PLACED_BOWLS.get();
        if (!level.getBlockState(at).is(block)) {
            BlockState state = block.defaultBlockState().setValue(FACING, player.getDirection().getOpposite());
            if (level instanceof ServerLevel server && !Permission.mayPlace(server, at, face, player, state)) {
                return false;
            }
            level.setBlock(at, state, Block.UPDATE_ALL);
        }
        if (!(level.getBlockEntity(at) instanceof PlacedBowlsBlockEntity bowls) || !bowls.add(held)) {
            return false;
        }
        held.consume(1, player);
        refresh(level, at, bowls);
        level.playSound(null, at, ModSounds.TRAY_KNOCK.get(), SoundSource.BLOCKS, 0.5F, 1.07F);
        return true;
    }

    /** The pick-block key gives the last dish set down, as it is. */
    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return level.getBlockEntity(pos) instanceof PlacedBowlsBlockEntity bowls && bowls.count() > 0
                ? bowls.bowl(bowls.count() - 1).copy() : ItemStack.EMPTY;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlacedBowlsBlockEntity(pos, state);
    }

    @EventBusSubscriber(modid = Nookworks.MODID)
    static final class Placing {
        /**
         * Sneak and right-click with a dish in a bowl: set it down (it is eaten only without sneaking, as ever); with a
         * dish on its own plate ({@link PlatedDishItem}), the plate is set down with it. Where it cannot stand the click
         * goes on as usual, unless it was on bowls already there (two, full: nothing happens). As a bottle's: not for a
         * spectator, nor in adventure mode, and heard at low priority, after a claim mod.
         */
        @SubscribeEvent(priority = EventPriority.LOW)
        static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
            Player player = event.getEntity();
            ItemStack held = event.getItemStack();
            boolean bowl = Plating.inBowl(held);
            if (!bowl && !Plating.plated(held) || !player.isSecondaryUseActive() || event.getFace() == null || player.isSpectator()
                    || event.getUseItem() == TriState.FALSE
                    || !player.mayUseItemAt(event.getPos().relative(event.getFace()), event.getFace(), held)) {
                return;
            }
            Level level = event.getLevel();
            if (bowl) {
                boolean ours = level.getBlockState(event.getPos()).is(ModKitchen.PLACED_BOWLS.get());
                if (placeFor(level, event.getPos(), event.getFace(), held) == null && !ours) {
                    return;
                }
                if (!level.isClientSide()) {
                    setDown(level, event.getPos(), event.getFace(), player, held);
                }
            } else {
                if (PlateBlock.placeFor(level, event.getPos(), event.getFace()) == null) {
                    return;
                }
                if (!level.isClientSide()) {
                    PlateBlock.setDown(level, event.getPos(), event.getFace(), player, held);
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }

        private Placing() {}
    }
}
