package dev.nookworks.item;

import java.util.List;

import dev.nookworks.feature.Lubrication;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * Oil Can: lubricant (vegetable oil with sulfur in it) for hinges. A drop on a door, trapdoor, fence gate or piston
 * silences it for good (see {@link Lubrication}); a door takes it on both halves. One can does a good many.
 */
public class OilCanItem extends Item {
    public OilCanItem(Properties properties) {
        super(properties);
    }

    /** Before the block's own right-click, or a door, trapdoor or gate would only open and never be oiled. */
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();
        if (!state.is(Lubrication.LUBRICATABLE)) {
            return InteractionResult.PASS;
        }
        if (Lubrication.isLubricated(level, pos)) {
            if (player != null && level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("item.nookworks.oil_can.already"));
            }
            return InteractionResult.PASS; // already oiled: the door opens as it would
        }
        if (level instanceof ServerLevel server) {
            for (BlockPos part : parts(pos, state)) {
                Lubrication.lubricate(server, part);
            }
            server.sendParticles(ParticleTypes.DRIPPING_HONEY, pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5, 4, 0.25, 0.2, 0.25, 0.0);
            server.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.5F, 1.4F);
            server.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, context.getHand());
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** The squares one drop covers: both halves of a door, one square of anything else. */
    private static List<BlockPos> parts(BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            return List.of(pos, state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos.below());
        }
        return List.of(pos);
    }
}
