package dev.nookworks.client;

import com.mojang.blaze3d.platform.NativeImage;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.photo.CameraItem;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.Photos;
import dev.nookworks.registry.ModItems;
import dev.nookworks.registry.ModSounds;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Looking through the Camera: while it is held up (use held down), the screen is its viewfinder - everything but the
 * world put away (the hotbar and the rest of the HUD, the hand, a block's outline), the world dimmed outside a 4:3 frame,
 * and its corners marked. A click takes what is in the frame: the frame after, drawn without the marks, is read back,
 * the frame's part of it shrunk to the photo's {@link Photo#WIDTH} x {@link Photo#HEIGHT} - each pixel the average of the
 * screen's under it - and sent to the server, which keeps it ({@link Photos#take}). The shutter is heard here at once
 * and by others from the server, and the screen flashes white.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class CameraView {
    public static final Identifier LAYER = Nookworks.id("viewfinder");
    /** How much of the screen's height the frame takes, unless the screen is too narrow for it. */
    private static final double FRAME_HEIGHT = 0.86;
    private static final double FRAME_WIDTH_MAX = 0.94;
    private static final int SHADE = 0xB0000000;
    private static final int MARK = 0xE6FFFFFF;
    private static final int FLASH_TICKS = 6;

    /** A photo is to be taken of the next frame drawn. */
    private static boolean shutter;
    /** Ticks of the flash left, this tick and the last. */
    private static int flash;
    private static int flashBefore;

    private CameraView() {}

    /** Whether the player in this game is looking through a camera. */
    public static boolean looking() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        return player != null && mc.gui.screen() == null && player.isUsingItem() && player.getUseItem().is(ModItems.CAMERA.get());
    }

    public static void registerLayer(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER, CameraView::drawViewfinder);
    }

    /** The frame, as left, top, width and height, on a screen {@code width} x {@code height}: the same part at any scale. */
    static double[] frame(double width, double height) {
        double h = height * FRAME_HEIGHT;
        double w = h * Photo.WIDTH / Photo.HEIGHT;
        if (w > width * FRAME_WIDTH_MAX) {
            w = width * FRAME_WIDTH_MAX;
            h = w * Photo.HEIGHT / Photo.WIDTH;
        }
        return new double[] {(width - w) / 2, (height - h) / 2, w, h};
    }

    /**
     * The click: a photo of the next frame, unless the camera is still cooling down from the last. Last of the tick's
     * listeners, so a click any of them made this tick is counted before the game throws it away.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onTick(ClientTickEvent.Pre event) {
        flashBefore = flash;
        if (flash > 0) {
            flash--;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!looking()) {
            return;
        }
        // while an item is in use the game throws attack clicks away; they are the shutter here
        while (mc.options.keyAttack.consumeClick()) {
            ItemStack camera = mc.player.getUseItem();
            if (!shutter && !mc.player.getCooldowns().isOnCooldown(camera)) {
                shutter = true;
            }
        }
    }

    /** The frame just drawn, without the marks, read back for the photo. */
    @SubscribeEvent
    static void onFrame(RenderFrameEvent.Post event) {
        if (!shutter) {
            return;
        }
        shutter = false;
        Minecraft mc = Minecraft.getInstance();
        if (!looking()) {
            return;
        }
        Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try (image) {
                if (Minecraft.getInstance().getConnection() != null) { // the frame comes back a frame or two on: left the world by then, nobody to send it to
                    ClientPacketDistributor.sendToServer(new NookworksNetwork.TakePhoto(shrink(image)));
                }
            }
        });
        mc.player.playSound(ModSounds.CAMERA_SHUTTER.get(), 0.9F, 1.0F);
        flash = flashBefore = FLASH_TICKS;
    }

    /** The frame's part of {@code image}, each of the photo's pixels the average of those it covers. */
    static byte[] shrink(NativeImage image) {
        double[] frame = frame(image.getWidth(), image.getHeight());
        double stepX = frame[2] / Photo.WIDTH;
        double stepY = frame[3] / Photo.HEIGHT;
        byte[] out = new byte[Photo.BYTES];
        for (int y = 0; y < Photo.HEIGHT; y++) {
            int y0 = Mth.clamp((int) Math.round(frame[1] + y * stepY), 0, image.getHeight() - 1);
            int y1 = Mth.clamp((int) Math.round(frame[1] + (y + 1) * stepY), y0 + 1, image.getHeight());
            for (int x = 0; x < Photo.WIDTH; x++) {
                int x0 = Mth.clamp((int) Math.round(frame[0] + x * stepX), 0, image.getWidth() - 1);
                int x1 = Mth.clamp((int) Math.round(frame[0] + (x + 1) * stepX), x0 + 1, image.getWidth());
                long r = 0;
                long g = 0;
                long b = 0;
                for (int sy = y0; sy < y1; sy++) {
                    for (int sx = x0; sx < x1; sx++) {
                        int argb = image.getPixel(sx, sy);
                        r += argb >> 16 & 0xFF;
                        g += argb >> 8 & 0xFF;
                        b += argb & 0xFF;
                    }
                }
                long count = (long) (x1 - x0) * (y1 - y0);
                int at = (x + y * Photo.WIDTH) * 3;
                out[at] = (byte) (r / count);
                out[at + 1] = (byte) (g / count);
                out[at + 2] = (byte) (b / count);
            }
        }
        return out;
    }

    /** The world alone through the viewfinder: every other layer of the screen put away. */
    @SubscribeEvent
    static void onLayer(RenderGuiLayerEvent.Pre event) {
        if (!event.getName().equals(LAYER) && (looking() || shutter)) {
            event.setCanceled(true);
        }
    }

    /** No hand in the view: the camera is up at the eye. */
    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        if (looking()) {
            event.setCanceled(true);
        }
    }

    /** Nor the outline of the block looked at. */
    @SubscribeEvent
    static void onOutline(ExtractBlockOutlineRenderStateEvent event) {
        if (looking()) {
            event.setCanceled(true);
        }
    }

    private static void drawViewfinder(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        float flashNow = Mth.lerp(delta.getGameTimeDeltaPartialTick(false), flashBefore, flash) / FLASH_TICKS;
        if (looking() && !shutter) {
            double[] frame = frame(width, height);
            int left = (int) Math.round(frame[0]);
            int top = (int) Math.round(frame[1]);
            int right = (int) Math.round(frame[0] + frame[2]);
            int bottom = (int) Math.round(frame[1] + frame[3]);
            graphics.fill(0, 0, width, top, SHADE);
            graphics.fill(0, bottom, width, height, SHADE);
            graphics.fill(0, top, left, bottom, SHADE);
            graphics.fill(right, top, width, bottom, SHADE);
            int arm = Math.max(8, (bottom - top) / 12);
            corner(graphics, left, top, arm, 1, 1);
            corner(graphics, right, top, arm, -1, 1);
            corner(graphics, left, bottom, arm, 1, -1);
            corner(graphics, right, bottom, arm, -1, -1);
            int cx = (left + right) / 2;
            int cy = (top + bottom) / 2;
            graphics.fill(cx - 4, cy, cx - 1, cy + 1, MARK);
            graphics.fill(cx + 2, cy, cx + 5, cy + 1, MARK);
            graphics.fill(cx, cy - 4, cx + 1, cy - 1, MARK);
            graphics.fill(cx, cy + 2, cx + 1, cy + 5, MARK);
            ItemStack camera = mc.player.getUseItem();
            boolean cooling = mc.player.getCooldowns().isOnCooldown(camera);
            Component count = Component.translatable("item.nookworks.camera.count", CameraItem.photos(camera).size(), Photos.KEPT);
            graphics.text(mc.font, count, right - 6 - mc.font.width(count), bottom - 6 - mc.font.lineHeight, cooling ? 0x80FFFFFF : MARK, true);
        }
        if (flashNow > 0) {
            graphics.fill(0, 0, width, height, (int) (flashNow * flashNow * 0xD0) << 24 | 0xFFFFFF);
        }
    }

    /** A corner of the frame: two strokes from ({@code x}, {@code y}) into it. */
    private static void corner(GuiGraphicsExtractor graphics, int x, int y, int arm, int dx, int dy) {
        int x0 = dx > 0 ? x : x - arm;
        int y0 = dy > 0 ? y : y - 1;
        graphics.fill(x0, y0, x0 + arm, y0 + 1, MARK);
        int x1 = dx > 0 ? x : x - 1;
        int y1 = dy > 0 ? y : y - arm;
        graphics.fill(x1, y1, x1 + 1, y1 + arm, MARK);
    }
}
