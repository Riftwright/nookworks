package dev.nookworks.client.gui;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import dev.nookworks.spawner.SpawnerControllerExtensions;
import dev.nookworks.spawner.SpawnerControllerMenu;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The client half of add-ons' Spawner Controller pages ({@link SpawnerControllerExtensions}): what each page draws and
 * which widgets it has. Registered under its extension's id, on the client, while the add-on is constructed. With a
 * page registered the controller's screen grows tabs over its panel - its own settings first - and shows the page's
 * slots (and the player's inventory, if the extension asks) while that tab is open. Public API, kept for add-ons.
 */
public final class SpawnerControllerPages {
    /** One add-on page. Coordinates in {@link Context} are the screen's; the panel is {@code width} x {@code height}. */
    public interface Page {
        /** The tab's name. */
        Component title();

        /** A small picture on the tab (empty: the name alone). */
        default ItemStack icon() {
            return ItemStack.EMPTY;
        }

        /** Its widgets, added through {@code context}; asked each time the page opens or the screen is resized. */
        default void init(Context context) {}

        /** Draws the page over the panel (its slots' frames are drawn for it), each frame. */
        default void render(GuiGraphicsExtractor graphics, Context context, int mouseX, int mouseY, float partialTick) {}
    }

    /** What a page is given to work with. */
    public interface Context {
        int left();

        int top();

        int width();

        int height();

        Font font();

        SpawnerControllerMenu menu();

        /** Its extension's numbers, as the server keeps them ({@link SpawnerControllerExtensions.Extension#data}). */
        @Nullable ContainerData data();

        /** Presses its extension's button {@code button} (0 to {@link SpawnerControllerExtensions#BUTTONS} - 1). */
        void press(int button);

        /** Adds a widget to the page. */
        <T extends AbstractWidget> T add(T widget);
    }

    private static final Map<Identifier, Page> PAGES = new LinkedHashMap<>();

    private SpawnerControllerPages() {}

    /** Adds page {@code page} for the extension of id {@code id}. */
    public static synchronized void register(Identifier id, Page page) {
        if (PAGES.putIfAbsent(id, page) != null) {
            throw new IllegalArgumentException("A Spawner Controller page is already registered as " + id);
        }
    }

    /** The page of extension {@code id}, or null. */
    public static synchronized @Nullable Page get(Identifier id) {
        return PAGES.get(id);
    }

    /** The extensions that have a page, in their order, by id. */
    public static synchronized Map<Identifier, Page> shown() {
        Map<Identifier, Page> shown = new LinkedHashMap<>();
        for (SpawnerControllerExtensions.Extension extension : SpawnerControllerExtensions.all()) {
            Page page = PAGES.get(extension.id());
            if (page != null) {
                shown.put(extension.id(), page);
            }
        }
        return shown;
    }
}
