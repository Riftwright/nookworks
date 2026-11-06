package dev.nookworks.block;

import org.jspecify.annotations.Nullable;

import dev.nookworks.block.entity.SifterBlockEntity;
import dev.nookworks.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Sifter block. Right-click with a siftable block (gravel, dirt, sand...) to sift one by hand; results pop out on
 * top. Right-click with an empty hand for its screen. Hoppers automate it; see {@link SifterBlockEntity}.
 */
public class SifterBlock extends BaseEntityBlock {
    public SifterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SifterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntities.SIFTER.get(), SifterBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SifterBlockEntity sifter) {
            MachineBlock.openScreen(player, sifter);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel)) {
            // The client cannot see loot tables, but it does see item tags.
            return stack.is(SifterBlockEntity.SIFTABLE) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!SifterBlockEntity.canSift(serverLevel, stack)) {
            if (stack.is(SifterBlockEntity.SIFTABLE)) {
                // tagged to sift but with no table to sift by (a pack's tag): said, as the client already took the click
                player.sendOverlayMessage(Component.translatable("message.nookworks.sifter.nothing"));
                return Clicks.REFUSED;
            }
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        ItemStack one = stack.copyWithCount(1);
        // By hand the mesh is not used: what it costs is time, and sifting by hand takes none to pay with.
        for (ItemStack result : SifterBlockEntity.sift(serverLevel, pos, one)) {
            // out on top as a block's drop comes out, but not a drop: with the block_drops rule off, a block's drops are
            // not made, and the dirt put in would be gone for nothing
            ItemEntity item = new ItemEntity(serverLevel, pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, result);
            item.setDefaultPickUpDelay();
            serverLevel.addFreshEntity(item);
        }
        if (one.getItem() instanceof BlockItem blockItem) {
            BlockState sifted = blockItem.getBlock().defaultBlockState();
            // the block's crumble and its break sound, both from the level event (a sound of its own after it made two)
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos.above(), Block.getId(sifted));
        } else { // coarse salt and the like: a rustle of grains, and a few of them
            serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, one.getItem()),
                    pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 8, 0.2, 0.05, 0.2, 0.02);
            level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.6F, 1.3F);
        }
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
        stack.consume(1, player);
        return InteractionResult.SUCCESS;
    }
}
