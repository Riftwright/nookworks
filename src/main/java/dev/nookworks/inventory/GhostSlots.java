package dev.nookworks.inventory;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A menu with ghost slots - slots that show a copy of an item and take nothing from the player, as a sorter's filter -
 * which an item dragged from a recipe viewer's list (JEI) can set, as a click there with the item in hand would. The
 * client applies the drop to its own menu as it sends it ({@code NookworksNetwork.SetGhost}), and the server from the
 * packet, through {@link #dropped}.
 */
public interface GhostSlots {
    /** Whether slot {@code slot} takes a dropped item just now (a ghost slot, and shown). */
    boolean takesGhost(int slot);

    /** Sets slot {@code slot} to {@code stack} as a click there with it in hand would: whether it took it. */
    boolean setGhost(int slot, ItemStack stack);

    /**
     * {@code item} dropped onto slot {@code slot} of {@code player}'s open menu {@code containerId}: set there if that
     * menu is still the one open, may still be used, and takes it there now. Whether it was set. Never by a spectator,
     * who may look into a menu but change nothing in it, as vanilla's menu clicks keep them (r22 CXC-008).
     */
    static boolean dropped(Player player, int containerId, int slot, ItemStack item) {
        return !item.isEmpty() && !player.isSpectator() && !player.isDeadOrDying() && player.containerMenu.containerId == containerId
                && player.containerMenu instanceof GhostSlots ghosts && player.containerMenu.stillValid(player)
                && ghosts.takesGhost(slot) && ghosts.setGhost(slot, item);
    }
}
