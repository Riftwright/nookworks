package dev.nookworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dev.nookworks.Nookworks;
import dev.nookworks.client.gui.StorageScreen;
import dev.nookworks.inventory.InventoryTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client side of the inventory tweaks: scroll-wheel transfer and Shift+drag quick move.
 * Turns itself off when Mouse Tweaks is installed so the two never act twice, and works only on vanilla's screens and
 * ours: another mod's screen may use the wheel itself (a list to scroll, a page to turn), and taking the wheel from it
 * would move items instead.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class InventoryTweaksClient {
    private static final boolean MOUSE_TWEAKS_PRESENT = ModList.get().isLoaded("mousetweaks");

    static {
        if (MOUSE_TWEAKS_PRESENT) {
            Nookworks.LOGGER.info("Nookworks' inventory tweaks (the scroll wheel and Shift+drag) stand down: Mouse Tweaks is installed.");
        }
    }
    /** Slot indices already quick-moved during the current Shift+drag. */
    private static final Set<Integer> dragged = new HashSet<>();

    /** A screen of vanilla's or of ours: the only ones whose use of the wheel is known. */
    static boolean ours(Screen screen) {
        String name = screen.getClass().getName();
        return name.startsWith("net.minecraft.") || name.startsWith("dev.nookworks.");
    }

    @SubscribeEvent
    static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        dragged.clear();
        if (event.getScreen() instanceof AbstractContainerScreen<?> screen && screen.getHoveredSlot() != null) {
            dragged.add(screen.getHoveredSlot().index); // vanilla handles the first slot itself
        }
    }

    // Pre, not Post: AbstractContainerScreen.mouseDragged reports every drag over a slot as handled, so Post
    // never fires there. The event is not cancelled, so vanilla dragging still runs afterwards.
    @SubscribeEvent
    static void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (MOUSE_TWEAKS_PRESENT || !NookworksClientConfig.SHIFT_DRAG.getAsBoolean()
                || event.getMouseButton() != InputConstants.MOUSE_BUTTON_LEFT || !event.getMouseButtonEvent().hasShiftDown()
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof CreativeModeInventoryScreen || !ours(screen)) {
            return;
        }
        AbstractContainerMenu menu = screen.getMenu();
        Slot slot = screen.getHoveredSlot();
        if (slot == null || !slot.hasItem() || !menu.getCarried().isEmpty() || waitingForRow(screen) || !dragged.add(slot.index)) {
            return;
        }
        if (!slot.mayPlace(slot.getItem())) {
            return; // an output - an anvil's, a trade's, a furnace's: passed over, it would be bought or forged
        }
        send(menu, List.of(new InventoryTweaks.Click(slot.index, 0, ContainerInput.QUICK_MOVE)));
    }

    @SubscribeEvent
    static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (MOUSE_TWEAKS_PRESENT || !NookworksClientConfig.WHEEL_TRANSFER.getAsBoolean()
                || event.getScrollDeltaY() == 0
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof CreativeModeInventoryScreen
                || !ours(screen)) {
            return; // another mod's screen may use the wheel itself
        }
        Slot slot = screen.getHoveredSlot();
        if (screen instanceof StorageScreen
                && (slot == null || !(slot.container instanceof Inventory))) {
            return; // over the barrel's own rows the wheel scrolls them; over your own inventory it moves things, as anywhere
        }
        // Bundles use the scroll wheel themselves.
        if (slot == null || slot.getItem().has(DataComponents.BUNDLE_CONTENTS) || waitingForRow(screen)) {
            return;
        }
        boolean down = event.getScrollDeltaY() < 0;
        boolean push = down != NookworksClientConfig.INVERT_WHEEL.getAsBoolean();
        AbstractContainerMenu menu = screen.getMenu();
        List<InventoryTweaks.Click> clicks = push ? InventoryTweaks.pushOne(menu, screen.getMinecraft().player, slot)
                : InventoryTweaks.pullOne(menu, screen.getMinecraft().player, slot);
        if (!clicks.isEmpty()) {
            send(menu, clicks);
            event.setCanceled(true);
        }
    }

    /**
     * A Storage Barrel whose next row is still on its way: what is on screen is the row before, and a click sent now
     * would land on the new one.
     */
    private static boolean waitingForRow(Screen screen) {
        return screen instanceof StorageScreen storage && storage.waitingForRow();
    }

    private static void send(AbstractContainerMenu menu, List<InventoryTweaks.Click> clicks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            return;
        }
        for (InventoryTweaks.Click click : clicks) {
            mc.gameMode.handleContainerInput(menu.containerId, click.slot(), click.button(), click.input(), mc.player);
        }
    }

    private InventoryTweaksClient() {}
}
