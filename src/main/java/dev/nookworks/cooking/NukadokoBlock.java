package dev.nookworks.cooking;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.Clicks;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Nukadoko: a wooden tub of salted rice bran to pickle vegetables in (see {@link NukadokoBlockEntity}).
 * <ul>
 * <li>Use a carrot or a beetroot (anything in {@link PicklingRecipe}) on it: in it goes, four at most.</li>
 * <li>Use it with an empty hand: the pickled ones come out, or it says how the rest are coming along. Sneak: everything
 * comes out, pickled or not.</li>
 * </ul>
 */
public class NukadokoBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 11, 15);

    public NukadokoBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || PicklingRecipe.of(stack) == null || !(level.getBlockEntity(pos) instanceof NukadokoBlockEntity tub)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            if (!tub.bury(stack)) {
                // said, as the pot, the grill and the tray say it
                player.sendOverlayMessage(Component.translatable("message.nookworks.nukadoko.full").withStyle(ChatFormatting.RED));
                return Clicks.REFUSED;
            }
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.MUD_PLACE, SoundSource.BLOCKS, 0.6F, 1.2F);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.getMainHandItem().isEmpty()) {
            return InteractionResult.PASS; // pickles are dug out by hand; anything else in hand is used as on any block
        }
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof NukadokoBlockEntity tub) {
            var out = tub.digOut(player.isSecondaryUseActive());
            if (out.isEmpty()) {
                player.sendOverlayMessage(tub.describe());
            } else {
                for (ItemStack stack : out) {
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false, Prediction.PREDICTED);
                    }
                }
                level.playSound(null, pos, SoundEvents.MUD_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NukadokoBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.NUKADOKO.get(), NukadokoBlockEntity::serverTick);
    }
}
