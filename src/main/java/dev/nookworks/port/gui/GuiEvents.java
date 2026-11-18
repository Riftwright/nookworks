package dev.nookworks.port.gui;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * The frame-wide parts of 26.3's screen drawing and input that 1.21.1 leaves to each screen:
 * <ul>
 * <li>before any screen is drawn, the depth the 2D drawing counts from put back where the screen's pose stands (the
 * HUD drawn before it may have left it further forward);</li>
 * <li>after any screen is drawn, the tooltip kept that frame ({@link GuiGraphicsExtractor#finishFrame}) - for the mod's
 * widgets on vanilla's screens (the chest's Sort buttons) and the mixins' drawing, which no screen of the port's ends;</li>
 * <li>the pointer shape the frame asked for, set on the window, and put back to the arrow when nothing asks (26.3 sets
 * it a frame at a time) or the screen closes;</li>
 * <li>whether a press is a double click, worked out before the screen has it ({@link Inputs}).</li>
 * </ul>
 */
@EventBusSubscriber(modid = "nookworks", value = Dist.CLIENT)
public final class GuiEvents {
    private static CursorType shown = CursorType.DEFAULT;

    private GuiEvents() {}

    /** A screen starts drawing (any screen, vanilla's with our widgets too): depth counted afresh from its own pose. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeScreen(ScreenEvent.Render.Pre event) {
        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.ifCurrent(event.getGuiGraphics());
        if (extractor != null) {
            extractor.begin();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void afterScreen(ScreenEvent.Render.Post event) {
        GuiGraphicsExtractor extractor = GuiGraphicsExtractor.ifCurrent(event.getGuiGraphics());
        if (extractor != null) {
            extractor.finishFrame();
        }
        show(extractor != null ? extractor.cursor() : CursorType.DEFAULT);
    }

    @SubscribeEvent
    public static void closing(ScreenEvent.Closing event) {
        show(CursorType.DEFAULT);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void pressing(ScreenEvent.MouseButtonPressed.Pre event) {
        Inputs.pressing(event.getScreen(), event.getMouseX(), event.getMouseY(), event.getButton());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void pressed(ScreenEvent.MouseButtonPressed.Post event) {
        Inputs.pressed(event.getScreen(), event.getButton(), event.wasClickHandled());
    }

    private static void show(CursorType cursor) {
        if (cursor == shown) {
            return;
        }
        shown = cursor;
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursor.handle());
    }
}
