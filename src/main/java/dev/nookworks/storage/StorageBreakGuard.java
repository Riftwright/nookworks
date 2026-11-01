package dev.nookworks.storage;

import dev.nookworks.Nookworks;
import dev.nookworks.feature.Permission;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;

/**
 * Server-side backstop for the LOCKED state: refuses to break a Storage Barrel that would spill more than
 * {@code storageBreakStackLimit} stacks, in survival and creative alike (cancelled before any drop is made), and
 * says so in a word. In survival a locked barrel never breaks, so no break event would ever come to say it in:
 * the player is told as soon as they start hitting it.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class StorageBreakGuard {
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onBreak(BreakBlockEvent event) {
        if (Permission.taking()) {
            return; // asked only whose it is, to take from it: its size is no claim (nookTechnology's find, 2026-11-01)
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof StorageBlockEntity storage && storage.tooFullToBreak()) {
            event.setCanceled(true); // the answer to our own asking too: no machine breaks it either
            if (Permission.asking()) {
                return; // only asked: nobody is hitting it, and nothing is to change
            }
            storage.updateLock();
            if (event.getPlayer() != null) {
                tell(event.getPlayer());
            }
        }
    }

    /**
     * A barrel item holding contents never despawns, however it comes to lie on the ground: not only as it first drops
     * from the barrel, but thrown by a player, dropped at death, let fall by a receiver with nothing under it.
     */
    @SubscribeEvent
    static void onDropped(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item
                && item.getItem().getItem() instanceof StorageItem && StorageItem.holdsSomething(item.getItem())) {
            item.setUnlimitedLifetime();
        }
    }

    @SubscribeEvent
    static void onStartHitting(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide() || event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof StorageBlockEntity storage) {
            // Looked at afresh each time someone starts on it: the limit is a setting a server can raise, and a barrel
            // locked under the old one stayed locked, without a word, until its contents changed.
            storage.updateLock();
            if (storage.tooFullToBreak()) {
                tell(event.getEntity());
            }
        }
    }

    private static void tell(Player player) {
        player.sendOverlayMessage(Component.translatable("message.nookworks.storage.too_full"));
    }

    private StorageBreakGuard() {}
}
