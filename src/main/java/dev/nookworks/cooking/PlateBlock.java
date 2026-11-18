package dev.nookworks.cooking;

import dev.nookworks.registry.ModSounds;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.feature.Permission;
import dev.nookworks.registry.ModKitchen;
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
import net.minecraft.world.level.LevelAccessor;
import dev.nookworks.port.core.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import dev.nookworks.port.core.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.ItemInteractionResult;
import dev.nookworks.port.core.ItemInteractions;
import dev.nookworks.port.core.BlockRemoval;
import dev.nookworks.port.core.RemovalHooks;
import com.mojang.serialization.MapCodec;
import dev.nookworks.port.core.BlockCodecs;

/**
 * Plate (お皿; the user, 2026-10-31): a white glazed plate set on a table, one dish on it ({@link PlateBlockEntity}) -
 * or a few of a small thing, as many as a plate holds of that food ({@link Serving}). Food with no dish of its own is
 * set out on a plate or a Wooden Tray, never straight on the table (「直接置くのは、なんか汚い」); a dish in a bowl stands
 * on the table itself ({@link PlacedBowlsBlock}), and fried rice and the like are served onto a plate out of their bowl
 * ({@link Plating}). Food shows as the food itself ({@code DishModels}), or lies on it as its item's picture. Food in
 * hand goes on; an empty hand takes it off, into the inventory as from a tray. It goes with the table under it, and
 * what was on it with it.
 */
public class PlateBlock extends BaseEntityBlock implements RemovalHooks {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 1.5, 14);

    public PlateBlock(Properties properties) {
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

    /**
     * The plate, and what is on it as tall as it stands ({@link Serving#heightOnPlate}): it is stood on, and clicked to
     * pile more on or take one off, where it is drawn (the user, 2026-11-14, play-testing: a doughnut tower had no shape
     * of its own - 「埋まってしまう」, and clicks at it went through to what was behind). Across the plate's room.
     */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!(level.getBlockEntity(pos) instanceof PlateBlockEntity plate) || plate.food().isEmpty()) {
            return SHAPE;
        }
        ItemStack food = plate.food();
        float high = Math.min(15, Serving.of(food).heightOnPlate(food.getCount()));
        return high < 0.5F ? SHAPE : Shapes.or(SHAPE, Block.box(2.7, 1, 2.7, 13.3, 1 + high, 13.3));
    }

    /** As a tray: on anything with a top to put it on. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP) || Block.canSupportRigidBlock(level, pos.below());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState neighbourState, LevelAccessor level,
            BlockPos pos, BlockPos neighbourPos) {
        return side == Direction.DOWN && !state.canSurvive(level, pos) ? Blocks.AIR.defaultBlockState()
                : super.updateShape(state, side, neighbourState, level, pos, neighbourPos);
    }

    /**
     * Food goes on: onto an empty plate, or beside more of the same while the plate has room for that food
     * ({@link Serving}). A dish in a bowl goes on no plate - but fried rice and the like are served onto an empty one out
     * of their bowl, which is handed back ({@link Plating}); a dish already on its own plate is slid onto this one, and
     * its own plate handed back. Anything else, or another food on a plate already served, is used as on any block.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof PlateBlockEntity plate)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (Plating.inBowl(stack)) {
            ItemStack plated = Plating.of(stack);
            if (plated.isEmpty() || !plate.isEmpty()) {
                if (!level.isClientSide()) {
                    // a soup stays in its bowl, and a bowl goes on no plate: said, with how it is set down instead
                    player.displayClientMessage(Component.translatable(plated.isEmpty() ? "message.nookworks.plate.bowl"
                            : "message.nookworks.plate.taken").withStyle(ChatFormatting.RED), true);
                }
                return ItemInteractions.of(Clicks.REFUSED);
            }
            if (!level.isClientSide()) {
                ItemStack bowl = Plating.emptied(stack);
                stack.consume(1, player);
                plate.serve(plated);
                give(player, bowl);
                level.playSound(null, pos, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.5F, 1.0F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (Plating.plated(stack)) {
            if (!plate.isEmpty()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide()) {
                plate.place(stack, player);
                give(player, new ItemStack(this)); // the plate it came on: this one is its plate now
                level.playSound(null, pos, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.5F, 1.12F);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (!PlateBlockEntity.fits(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!plate.takes(stack)) {
            if (!plate.isFull() || !ItemStack.isSameItemSameComponents(plate.food(), stack)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // another food on a plate already served
            }
            if (!level.isClientSide()) {
                player.displayClientMessage(Component.translatable("message.nookworks.plate.full").withStyle(ChatFormatting.RED), true);
            }
            return ItemInteractions.of(Clicks.REFUSED);
        }
        if (!level.isClientSide()) {
            plate.place(stack, player);
            level.playSound(null, pos, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.5F, 1.12F);
        }
        return ItemInteractionResult.SUCCESS;
    }

    /**
     * An empty hand takes one of what is on it, into the inventory (not the hand: the next click would put it back).
     * Sneaking, it lifts the plate itself with its food ({@link PlateBlockEntity#lift}), into the hand - to set on a
     * Wooden Tray or another table (the user, 2026-11-14: お皿に盛りつけたとんかつを、皿ごとにトレイに載せる). A dish
     * served on a plate is taken plate and all either way: the plate on the table was its plate.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty() || !(level.getBlockEntity(pos) instanceof PlateBlockEntity plate) || plate.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            ItemStack off;
            if (plate.plated()) {
                if (!Permission.mayBreak(server, pos, player)) {
                    return Clicks.REFUSED; // the plate would go with it, and it is not theirs to take up
                }
                off = plate.takeAll();
                level.removeBlock(pos, false);
            } else if (player.isSecondaryUseActive()) {
                if (!player.mayBuild() || !Permission.mayBreak(server, pos, player)) {
                    return Clicks.REFUSED;
                }
                ItemStack lifted = plate.lift();
                level.removeBlock(pos, false);
                player.setItemInHand(InteractionHand.MAIN_HAND, lifted);
                level.playSound(null, pos, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.45F, 0.88F);
                return InteractionResult.SUCCESS;
            } else {
                off = plate.take();
            }
            give(player, off);
            TrayBlock.keepHandEmpty(player.getInventory());
            level.playSound(null, pos, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.35F, 1.25F);
        }
        return InteractionResult.SUCCESS;
    }

    private static void give(Player player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * A plate that is a dish's own (a dish served on a plate lies on it) drops with that dish, as the one item the two
     * are - not as a Plate beside it, which made a plate out of nothing each time such a dish was knocked off a table.
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof PlateBlockEntity plate && plate.plated()
                ? List.of() : super.getDrops(state, params);
    }

    /**
     * Where a dish on its own plate would be set down, clicking {@code clicked}'s {@code face}: on top of it; null when
     * no plate can stand there.
     */
    public static @Nullable BlockPos placeFor(Level level, BlockPos clicked, Direction face) {
        if (face != Direction.UP) {
            return null;
        }
        BlockPos at = clicked.above();
        BlockState there = level.getBlockState(at);
        return there.canBeReplaced() && there.getFluidState().isEmpty()
                && ModKitchen.PLATE.get().defaultBlockState().canSurvive(level, at) ? at : null;
    }

    /**
     * Sets {@code held}'s dish down with its plate on top of {@code clicked}; whether it did. The plate is a block put
     * there by the player, so claim mods and spawn protection are asked, as for a bottle set down. Public for tests.
     */
    public static boolean setDown(Level level, BlockPos clicked, Direction face, Player player, ItemStack held) {
        BlockPos at = placeFor(level, clicked, face);
        if (at == null || !Plating.plated(held)) {
            return false;
        }
        BlockState state = ModKitchen.PLATE.get().defaultBlockState().setValue(FACING, player.getDirection().getOpposite());
        if (level instanceof ServerLevel server && !Permission.mayPlace(server, at, face, player, state)) {
            return false;
        }
        level.setBlock(at, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(at) instanceof PlateBlockEntity plate) || !plate.place(held, player)) {
            level.removeBlock(at, false);
            return false;
        }
        level.playSound(null, at, ModSounds.PLATE_CLINK.get(), SoundSource.BLOCKS, 0.5F, 1.06F);
        return true;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlateBlockEntity(pos, state);
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
    protected MapCodec<? extends PlateBlock> codec() {
        return BlockCodecs.of(this);
    }
}
