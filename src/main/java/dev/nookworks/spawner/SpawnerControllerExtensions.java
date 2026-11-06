package dev.nookworks.spawner;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * Pages other mods add to the Spawner Controller's screen (nookTechnology's Mob Data Bank, 2026-11-03: the controller is
 * the laptop its spawners are set from, 「ポチポチやってスポナーの設定を変えるイメージ」). An add-on registers an
 * {@link Extension} here, on both sides, while its mod is constructed; its page is drawn by the client part
 * ({@code dev.nookworks.client.gui.SpawnerControllerPages}, registered under the same id). With none registered the
 * controller is as it always was: one page, no inventory.
 * <p>
 * Each extension may bring slots of its own (backed by a container its server side gives, an empty one of the same size
 * on the client), numbers kept in step with the client, and buttons. Its slots, and the player's inventory if it asks
 * for it, are only there on its page. Public API, kept for add-ons; checked against them by tools/check_addon_refs.py.
 */
public final class SpawnerControllerExtensions {
    /** Button ids from here on are extensions': each gets {@link #BUTTONS} of its own, in the order registered. */
    public static final int FIRST_BUTTON = 1000;
    public static final int BUTTONS = 100;
    /**
     * The page shown, told to the server (so a shift-click goes to that page's slots): this id for the settings, one more
     * for each extension in order. Sent by the screen as a tab is chosen; not for extensions' own use.
     */
    public static final int SHOW_PAGE = 900;

    /** One add-on's part of the controller: what its page needs from the server. */
    public interface Extension {
        /** Its id; its client page is registered under the same one. */
        Identifier id();

        /** How many slots its page holds (0: none). */
        default int slotCount() {
            return 0;
        }

        /**
         * The container behind its slots, on the server ({@code controller} the controller opened); null when there is
         * none to be had (then an empty one stands in). Never asked on the client.
         */
        default @Nullable Container container(SpawnerControllerBlockEntity controller) {
            return null;
        }

        /** Whether stack {@code index} of its slots may take {@code stack}. */
        default boolean mayPlace(int index, ItemStack stack) {
            return true;
        }

        /** Where its slot {@code index} is drawn on its page, relative to the panel: {x, y}. */
        default int[] slotPosition(int index) {
            return new int[] {8 + 18 * index, 40};
        }

        /** How many numbers it keeps in step with the client (0: none). */
        default int dataCount() {
            return 0;
        }

        /** Its numbers on the server, {@link #dataCount()} of them; null when there are none to be had (zeros). */
        default @Nullable ContainerData data(SpawnerControllerBlockEntity controller) {
            return null;
        }

        /** Whether its page shows the player's inventory under it (to put things into its slots). */
        default boolean showsInventory() {
            return false;
        }

        /** One of its buttons pressed, {@code button} 0 to {@link #BUTTONS} - 1, on the server. */
        default void buttonPressed(SpawnerControllerMenu menu, @Nullable SpawnerControllerBlockEntity controller, Player player, int button) {}

        /**
         * Whether the screen may stay open for {@code player} as far as this extension is concerned (on the server; the
         * controller's own reach is asked besides): false when what backs its slots is gone - its block broken - so the
         * screen closes and nothing taken out of the slots after its block dropped them is had twice.
         */
        default boolean stillValid(SpawnerControllerBlockEntity controller, Player player) {
            return true;
        }

        /**
         * Whether its page is there at all on {@code controller}'s screen (on the server, asked as long as the screen is
         * open and kept in step with the client): false leaves out its tab, its slots and its buttons - for a part of
         * the add-on a pack has switched off, say. Its slots stay in the menu, inactive, so both sides still agree.
         */
        default boolean shown(SpawnerControllerBlockEntity controller) {
            return true;
        }

        /** The screen closed, on the server: anything to give back or save. */
        default void removed(SpawnerControllerMenu menu, SpawnerControllerBlockEntity controller, Player player) {}
    }

    private static final List<Extension> EXTENSIONS = new ArrayList<>();

    private SpawnerControllerExtensions() {}

    /** Adds {@code extension}, on both sides alike (the same ones in the same order), while mods are constructed. */
    public static synchronized void register(Extension extension) {
        if (EXTENSIONS.stream().anyMatch(e -> e.id().equals(extension.id()))) {
            throw new IllegalArgumentException("A Spawner Controller extension is already registered as " + extension.id());
        }
        EXTENSIONS.add(extension);
    }

    /** Every extension, in the order registered. */
    public static synchronized List<Extension> all() {
        return List.copyOf(EXTENSIONS);
    }

    /** For the GameTests only: the extensions set to {@code keep}. */
    public static synchronized void replaceForTests(List<Extension> keep) {
        EXTENSIONS.clear();
        EXTENSIONS.addAll(keep);
    }
}
