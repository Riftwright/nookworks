package dev.nookworks.pearl;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import dev.nookworks.Nookworks;
import dev.nookworks.feature.Features;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.GlobalPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Binding pearls to a lodestone: an ender pearl used on a lodestone becomes a Lode Pearl bound to it, one pearl a
 * click, as a compass is bound (a stack of pearls is not turned all at once by a stray click). A Lode Pearl used on
 * another lodestone is bound to that one instead. The pearl is not thrown.
 * <p>
 * Not by a spectator, nor in adventure mode, where a compass is not bound either; and at low priority, as the Wool
 * Shears are, so a claim mod at the usual priority is heard before the pearl is bound.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class LodePearls {
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onUseOnBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        Level level = event.getLevel();
        if (!(held.is(Items.ENDER_PEARL) || held.is(ModItems.LODE_PEARL.get())) || !level.getBlockState(event.getPos()).is(Blocks.LODESTONE)
                || !Features.enabled(ModItems.LODE_PEARL.get())) {
            return; // with the part off, a pearl on a lodestone is only a pearl: its one way in is closed
        }
        if (event.getEntity().isSpectator() || !event.getEntity().mayBuild()) {
            return; // as in vanilla, which binds no compass here either
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (level.isClientSide()) {
            return;
        }
        GlobalPos here = GlobalPos.of(level.dimension(), event.getPos().immutable());
        if (LodePearlItem.lodestone(held).filter(here::equals).isPresent()) {
            return; // bound here already
        }
        Player player = event.getEntity();
        ItemStack bound = LodePearlItem.bound(here);
        held.consume(1, player);
        if (held.isEmpty()) {
            player.setItemInHand(event.getHand(), bound);
        } else if (!player.getInventory().add(bound)) {
            player.drop(bound, false, Prediction.PREDICTED);
        }
        level.playSound(null, event.getPos(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
        player.sendOverlayMessage(Component.translatable("message.nookworks.lode_pearl.bound", LodestoneNames.ensure((ServerLevel) level, event.getPos())));
    }

    private LodePearls() {}
}
