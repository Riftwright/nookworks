package dev.nookworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.inventory.RecipeChoice;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The button by a crafting grid's result when the grid could make more than one thing ({@link RecipeChoice}): a small
 * vanilla button with two arrows over the result slot's corner; pressed, a row of what the grid could make opens above
 * it, the one it makes framed, and a click on another makes that. Placed from the result slot each frame, so it follows
 * the panel when the recipe book moves it.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class RecipeChoiceClient {
    private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
    private static final Identifier BUTTON_HOVERED = Identifier.withDefaultNamespace("widget/button_highlighted");
    private static final int SIZE = 11;
    private static final int CELL = 18;
    private static final int PER_ROW = 7;

    private static int containerId = -1;
    private static List<ItemStack> outputs = List.of();
    private static int selected = -1;
    private static boolean open;
    /** Whether the server was asked for the grid's result again while this screen has been shown. */
    private static boolean askedAgain;

    static {
        RecipeChoice.shown = RecipeChoiceClient::told;
    }

    private RecipeChoiceClient() {}

    /** Loads the class, so the server's word reaches it (called from NookworksClient). */
    public static void init() {}

    private static void told(NookworksNetwork.RecipeChoices choices) {
        if (choices.containerId() != containerId || choices.outputs().size() != outputs.size()) {
            open = false;
        }
        containerId = choices.containerId();
        outputs = choices.outputs();
        selected = choices.selected();
        if (outputs.size() < 2) {
            open = false;
        }
    }

    /**
     * The screen the choice belongs to, with its grid's result slot; null when none is shown there - or when the slot is
     * empty (the inventory's grid emptied as it closed, its menu the same one; any other change the server says).
     */
    private static Slot resultSlot(Object screen) {
        if (outputs.size() < 2 || !(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof AbstractCraftingMenu menu) || menu.containerId != containerId) {
            return null;
        }
        if (menu.getResultSlot().getItem().isEmpty()) {
            return null;
        }
        return menu.getResultSlot();
    }

    /**
     * The result slot empty with the grid still full: the inventory's own screen, gone for a look in JEI and back, emptied
     * the slot on this side only (vanilla's InventoryMenu.removed; the server's grid is untouched), and the server, having
     * said what it holds, says it again only when that changes - so the button went too (review r12 fix check). The
     * server is asked to send the menu again, once each time a screen is shown.
     */
    private static void askAgainIfEmptied(Object screen) {
        if (askedAgain || outputs.size() < 2 || !(screen instanceof AbstractContainerScreen<?> container)
                || !(container.getMenu() instanceof AbstractCraftingMenu menu) || menu.containerId != containerId
                || !menu.getResultSlot().getItem().isEmpty()
                || menu.getInputGridSlots().stream().allMatch(slot -> slot.getItem().isEmpty())) {
            return;
        }
        askedAgain = true;
        ClientPacketDistributor.sendToServer(new NookworksNetwork.ShowGridAgain(containerId));
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static int buttonX(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.getLeftPos() + slot.x + 16 - SIZE + 3;
    }

    private static int buttonY(AbstractContainerScreen<?> screen, Slot slot) {
        return screen.getTopPos() + slot.y - SIZE - 5; // clear of the crafting table's big result frame
    }

    /** The row's corner: centred over the result slot, above the button, kept on the screen. */
    private static int[] panel(AbstractContainerScreen<?> screen, Slot slot) {
        int columns = Math.min(PER_ROW, outputs.size());
        int rows = (outputs.size() + PER_ROW - 1) / PER_ROW;
        int w = columns * CELL + 6;
        int h = rows * CELL + 6;
        int x = screen.getLeftPos() + slot.x + 8 - w / 2;
        x = Math.max(2, Math.min(x, screen.width - w - 2));
        int y = Math.max(2, buttonY(screen, slot) - h - 2);
        return new int[] {x, y, w, h, columns};
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Foreground event) { // over the slots, under the tooltips
        askAgainIfEmptied(event.getScreen());
        Slot slot = resultSlot(event.getScreen());
        if (slot == null) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        GuiGraphicsExtractor g = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int mx = event.getMouseX();
        int my = event.getMouseY();
        int bx = buttonX(screen, slot);
        int by = buttonY(screen, slot);
        boolean overButton = mx >= bx && mx < bx + SIZE && my >= by && my < by + SIZE;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, overButton || open ? BUTTON_HOVERED : BUTTON, bx, by, SIZE, SIZE);
        arrows(g, bx + 2, by + 2, 0xFFE0E0E0);
        if (overButton && !open) {
            g.setTooltipForNextFrame(mc.font, Component.translatable("gui.nookworks.recipe_choice", outputs.size()), mx, my);
        }
        if (!open) {
            return;
        }
        int[] p = panel(screen, slot);
        g.fill(p[0], p[1], p[0] + p[2], p[1] + p[3], 0xF0100010);
        g.fill(p[0] + 1, p[1] + 1, p[0] + p[2] - 1, p[1] + p[3] - 1, 0xFF2B2B33);
        for (int i = 0; i < outputs.size(); i++) {
            int cx = p[0] + 3 + (i % p[4]) * CELL;
            int cy = p[1] + 3 + (i / p[4]) * CELL;
            boolean over = mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL;
            g.fill(cx + 1, cy + 1, cx + CELL - 1, cy + CELL - 1, over ? 0xFF5A5A66 : 0xFF3C3C46);
            if (i == selected) {
                g.fill(cx, cy, cx + CELL, cy + 1, 0xFFFFFFFF);
                g.fill(cx, cy + CELL - 1, cx + CELL, cy + CELL, 0xFFFFFFFF);
                g.fill(cx, cy, cx + 1, cy + CELL, 0xFFFFFFFF);
                g.fill(cx + CELL - 1, cy, cx + CELL, cy + CELL, 0xFFFFFFFF);
            }
            g.item(outputs.get(i), cx + 1, cy + 1);
            g.itemDecorations(mc.font, outputs.get(i), cx + 1, cy + 1);
            if (over) {
                g.setTooltipForNextFrame(mc.font, outputs.get(i), mx, my);
            }
        }
    }

    /** Two small arrows, one over the other and pointing apart: the sign for "other things to choose". */
    private static void arrows(GuiGraphicsExtractor g, int x, int y, int colour) {
        // upper: a shaft to the right and its head
        g.fill(x + 1, y + 2, x + 6, y + 3, colour);
        g.fill(x + 5, y + 1, x + 6, y + 4, colour);
        g.fill(x + 6, y + 2, x + 7, y + 3, colour);
        // lower: to the left
        g.fill(x + 1, y + 5, x + 6, y + 6, colour);
        g.fill(x + 1, y + 4, x + 2, y + 7, colour);
        g.fill(x, y + 5, x + 1, y + 6, colour);
    }

    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Slot slot = resultSlot(event.getScreen());
        if (slot == null || event.getButton() != InputConstants.MOUSE_BUTTON_LEFT) {
            return;
        }
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) event.getScreen();
        double mx = event.getMouseX();
        double my = event.getMouseY();
        int bx = buttonX(screen, slot);
        int by = buttonY(screen, slot);
        if (mx >= bx && mx < bx + SIZE && my >= by && my < by + SIZE) {
            open = !open;
            clickSound();
            event.setCanceled(true);
            return;
        }
        if (!open) {
            return;
        }
        int[] p = panel(screen, slot);
        if (mx >= p[0] && mx < p[0] + p[2] && my >= p[1] && my < p[1] + p[3]) {
            int column = (int) (mx - p[0] - 3) / CELL;
            int row = (int) (my - p[1] - 3) / CELL;
            int index = row * p[4] + column;
            if (column >= 0 && column < p[4] && row >= 0 && index < outputs.size() && index != selected) {
                clickSound();
                ClientPacketDistributor.sendToServer(new NookworksNetwork.PickRecipe(containerId, index));
            }
            open = false;
            event.setCanceled(true);
            return;
        }
        open = false; // a click elsewhere closes the row and does what it would have done
    }

    /**
     * A screen closing folds the row away; what the grid could make is kept, for a look in JEI and back (its screen goes
     * and comes while the menu stays open, and the server, having said it, says it again only when it changes - review
     * r12). A menu closed for good is told apart by its id, or by its grid's result.
     */
    @SubscribeEvent
    static void onClose(ScreenEvent.Closing event) {
        open = false;
        askedAgain = false;
    }

    @SubscribeEvent
    static void onLeave(ClientPlayerNetworkEvent.LoggingOut event) {
        open = false;
        askedAgain = false;
        containerId = -1;
        outputs = List.of();
        selected = -1;
    }
}
