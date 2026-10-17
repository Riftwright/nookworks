package dev.nookworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.inventory.ChestSorting;
import dev.nookworks.storage.SortOrder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The Sort button on vanilla's chest and shulker box screens ({@link ChestSorting}), with the order button beside it,
 * where a Storage Barrel's screen has them: the top right of the panel. The order is this player's own, kept with the
 * client settings, since a vanilla chest has nowhere to keep one. Not added when a mod that sorts inventories is
 * installed, so a chest never has two Sort buttons, nor when it is switched off in the settings. A title too long for
 * the room left beside the buttons is cut short with an ellipsis (mixin client/AbstractContainerScreenMixin), and its
 * whole shown on hover, as a barrel's is.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class ChestSortClient {
    /** Mods with Sort buttons of their own on chests. */
    private static final List<String> SORTING_MODS = List.of("inventoryprofilesnext", "inventorysorter", "quark", "invtweaks");
    private static final @Nullable String OTHER_SORTER = SORTING_MODS.stream().filter(ModList.get()::isLoaded).findFirst().orElse(null);
    private static final Identifier ORDER_ICONS = Nookworks.id("textures/gui/sort_orders.png");
    private static final int ORDER_W = 13;
    /** Where the vanilla screens draw their title, from the panel's corner. */
    private static final int TITLE_X = 8;
    private static final int TITLE_Y = 6;
    /** The screens given the buttons, and the room each leaves its title. */
    private static final Map<AbstractContainerScreen<?>, Integer> TITLE_ROOM = new WeakHashMap<>();

    static {
        if (OTHER_SORTER != null) {
            Nookworks.LOGGER.info("Nookworks' Sort button on chests stands down: {} sorts them.", OTHER_SORTER);
        }
    }

    @SubscribeEvent
    static void onInit(ScreenEvent.Init.Post event) {
        if (OTHER_SORTER != null || !NookworksClientConfig.CHEST_SORT.getAsBoolean()
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen.getClass() != ContainerScreen.class && screen.getClass() != ShulkerBoxScreen.class
                || !ChestSorting.sortable(screen.getMenu())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Component label = Component.translatable("gui.nookworks.storage.sort");
        int w = mc.font.width(label) + 10;
        int x = screen.getLeftPos() + screen.getImageWidth() - 7 - w;
        int y = screen.getTopPos() + 3;
        int containerId = screen.getMenu().containerId;
        event.addListener(Button.builder(label, b -> ClientPacketDistributor.sendToServer(
                new NookworksNetwork.SortChest(containerId, order().ordinal()))).bounds(x, y, w, 13).build());
        event.addListener(new OrderButton(x - ORDER_W - 2, y));
        TITLE_ROOM.put(screen, x - ORDER_W - 2 - screen.getLeftPos() - TITLE_X - 3);
    }

    /** The room {@code screen}'s title has before the buttons, or 0 if it has none of ours. */
    public static int titleRoom(AbstractContainerScreen<?> screen) {
        return TITLE_ROOM.getOrDefault(screen, 0);
    }

    /** A title cut short: the whole of it over the cut one. */
    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Foreground event) { // (Post comes after the frame's tooltips are drawn)
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen) || titleRoom(screen) <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        int room = titleRoom(screen);
        int x = screen.getLeftPos() + TITLE_X;
        int y = screen.getTopPos() + TITLE_Y;
        if (mc.font.width(screen.getTitle()) > room && event.getMouseX() >= x && event.getMouseX() < x + room
                && event.getMouseY() >= y - 1 && event.getMouseY() < y + mc.font.lineHeight) {
            event.getGuiGraphics().setTooltipForNextFrame(mc.font, screen.getTitle(), event.getMouseX(), event.getMouseY());
        }
    }

    private static SortOrder order() {
        SortOrder order = NookworksClientConfig.CHEST_SORT_ORDER.get();
        return ChestSorting.ORDERS.contains(order) ? order : ChestSorting.ORDERS.getFirst();
    }

    /** Steps the order by {@code by} among the chest's orders, and keeps it. */
    private static void step(int by) {
        List<SortOrder> orders = ChestSorting.ORDERS;
        int at = orders.indexOf(order());
        NookworksClientConfig.CHEST_SORT_ORDER.set(orders.get(Math.floorMod(at + by, orders.size())));
        NookworksClientConfig.CHEST_SORT_ORDER.save();
    }

    /** The order the chest's Sort sorts in, as the barrel's icon: a click for the next, a right-click for the one before. */
    private static final class OrderButton extends AbstractButton {
        OrderButton(int x, int y) {
            super(x, y, ORDER_W, 13, Component.empty());
        }

        @Override
        public Component getMessage() {
            return Component.translatable("gui.nookworks.storage.order", Component.translatable(order().translationKey()));
        }

        @Override
        protected boolean isValidClickButton(MouseButtonInfo info) {
            return info.button() == InputConstants.MOUSE_BUTTON_LEFT || info.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            step(event.button() == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            step(1);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            this.extractDefaultSprite(graphics);
            graphics.blit(RenderPipelines.GUI_TEXTURED, ORDER_ICONS, this.getX() + 3, this.getY() + 3, order().ordinal() * 8, 0, 8, 8, 32, 8);
            if (this.isHovered()) {
                Minecraft mc = Minecraft.getInstance();
                graphics.setTooltipForNextFrame(mc.font, List.of(this.getMessage().getVisualOrderText(),
                        Component.translatable("gui.nookworks.storage.order.hint").withStyle(ChatFormatting.GRAY).getVisualOrderText()),
                        mouseX, mouseY);
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private ChestSortClient() {}
}
