package dev.nookworks.port.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * Stands in for 26.3's {@code net.minecraft.client.gui.GuiGraphicsExtractor}, over 1.21.1's {@link GuiGraphics}: the
 * drawing calls the mod's screens, widgets and overlays make, each meaning what it means in 26.3. One is kept for the
 * {@code GuiGraphics} of the frame ({@link #of}), so a screen and the widgets it draws share one.
 * <p>
 * <b>What is over what.</b> 26.3 collects a frame's drawing and lays it out so that whatever is drawn later over
 * something earlier shows over it (items included: an item drawn and then a veil over it on the next stratum, a count
 * written over an item, a strike over a price). 1.21.1 draws at once and sorts by depth: an item stands 150 deep in
 * front of the plane, so a fill or a word drawn after it at the plane is hidden behind it. Here each item is drawn
 * pushed back into a thin slab (from the current depth to 20 in front of it), and whatever is drawn later over the
 * place of an item or a text is first lifted in front of it - only where the two overlap, as 26.3 itself goes up a layer
 * only for what overlaps, so a screen of many items does not climb far. {@link #nextStratum} adds nothing to that: what
 * comes after is over what came before already.
 * <p>
 * <b>Tooltips</b> are kept, as 26.3 keeps them, and drawn after everything else in the frame, the first one set winning
 * ({@link #finishFrame}: at the end of the port's screens' drawing, or after any screen's by {@link GuiEvents}).
 * <b>Colours</b> are ARGB as in 26.3: a text in a colour with no alpha is not drawn (1.21.1 would draw it opaque).
 */
public final class GuiGraphicsExtractor {
    /** How far 1.21.1's {@code renderItem} stands an item in front of the plane, and the slab an item is drawn into. */
    private static final float ITEM_Z = 150.0F;
    private static final float ITEM_HALF_DEPTH = 10.0F;
    /** What a drawing of each kind takes in front of where it is drawn: later drawing over it goes this far forward. */
    private static final float ITEM_DEPTH = 2 * ITEM_HALF_DEPTH + 1.0F;
    private static final float TEXT_DEPTH = 1.0F;
    /** How far 1.21.1's {@code renderItemDecorations} stands the count in front (it is drawn back at the plane here). */
    private static final float DECORATION_Z = 200.0F;

    private static @Nullable GuiGraphicsExtractor current;

    private final GuiGraphics graphics;
    private final Minecraft minecraft;
    private final GuiPose pose;
    /** The depth the next drawing goes at, at least. */
    private float level;
    /** Where items and texts lie in front of {@link #level} (screen rectangles), and how far in front the most does. */
    private final List<float[]> raised = new ArrayList<>();
    private float raisedTop;
    private @Nullable Runnable deferredTooltip;
    private CursorType cursor = CursorType.DEFAULT;

    private GuiGraphicsExtractor(GuiGraphics graphics) {
        this.graphics = graphics;
        this.minecraft = Minecraft.getInstance();
        this.pose = new GuiPose(graphics.pose());
        this.level = this.depth();
        this.raisedTop = this.level;
    }

    /** The one for {@code graphics}: the same through a frame (1.21.1 makes a new {@code GuiGraphics} each frame). */
    public static GuiGraphicsExtractor of(GuiGraphics graphics) {
        GuiGraphicsExtractor extractor = current;
        if (extractor == null || extractor.graphics != graphics) {
            extractor = new GuiGraphicsExtractor(graphics);
            current = extractor;
        }
        return extractor;
    }

    /** The one of this frame, if {@code graphics} is this frame's. */
    static @Nullable GuiGraphicsExtractor ifCurrent(GuiGraphics graphics) {
        GuiGraphicsExtractor extractor = current;
        return extractor != null && extractor.graphics == graphics ? extractor : null;
    }

    /** 1.21.1's graphics underneath, for code that hands the drawing on to 1.21.1 (vanilla's own drawing). */
    public GuiGraphics graphics() {
        return this.graphics;
    }

    // ---- frames ----

    /**
     * A screen starts drawing: depth is counted from where the pose stands now, not from where the overlays drawn before
     * it left it, so the screen's own 1.21.1 widgets (drawn at the pose's depth) are not left behind its panels.
     */
    public GuiGraphicsExtractor begin() {
        this.level = this.depth();
        this.raised.clear();
        this.raisedTop = this.level;
        return this;
    }

    /**
     * The end of a screen's drawing: the pose put in front of everything drawn (so 1.21.1's own tooltip, drawn by the
     * screen after this, is over it too) and the tooltip kept for this frame drawn there.
     */
    public void finishFrame() {
        this.above(null);
        Runnable tooltip = this.deferredTooltip;
        this.deferredTooltip = null;
        if (tooltip != null) {
            tooltip.run();
        }
    }

    /** The pointer shape asked for this frame (the default unless asked). */
    CursorType cursor() {
        return this.cursor;
    }

    /**
     * Before 1.21.1 draws into a screen for the port (a vanilla slot, label or widget): lifted over whatever of ours
     * {@code x0..y1} (in the pose's coordinates; null: anywhere) overlaps, so it is over what was drawn before it.
     */
    public void beforeVanilla(float x0, float y0, float x1, float y1) {
        this.above(this.rect(x0, y0, x1, y1));
    }

    /** As {@link #beforeVanilla}, for drawing that may cover anything. */
    public void beforeVanilla() {
        this.above(null);
    }

    /**
     * After 1.21.1 has drawn something with depth into {@code x0..y1} (pose coordinates) - an item in a slot, standing
     * {@code depth} in front of where it was drawn: what is drawn over it later goes in front of it.
     */
    public void afterVanilla(float x0, float y0, float x1, float y1, float depth) {
        this.raise(this.rect(x0, y0, x1, y1), this.level + depth);
    }

    // ---- state ----

    public int guiWidth() {
        return this.graphics.guiWidth();
    }

    public int guiHeight() {
        return this.graphics.guiHeight();
    }

    public GuiPose pose() {
        return this.pose;
    }

    /** 26.3 starts a layer over everything drawn so far; here what is drawn later is over what came before already. */
    public void nextStratum() {
        this.above(null);
    }

    public void requestCursor(CursorType cursorType) {
        this.cursor = cursorType;
    }

    /** 26.3 clips to the rectangle as the pose maps it; the mod's callers clip with an unmoved pose. */
    // PORT: 1.21.1 clips to the rectangle as given, not moved or scaled by the pose
    public void enableScissor(int x0, int y0, int x1, int y1) {
        this.graphics.enableScissor(x0, y0, x1, y1);
    }

    public void disableScissor() {
        this.graphics.disableScissor();
    }

    public boolean containsPointInScissor(int x, int y) {
        return this.graphics.containsPointInScissor(x, y);
    }

    // ---- fills ----

    public void fill(int x0, int y0, int x1, int y1, int col) {
        this.fill(GuiPipeline.GUI, x0, y0, x1, y1, col);
    }

    public void fill(GuiPipeline pipeline, int x0, int y0, int x1, int y1, int col) {
        this.flat(x0, y0, x1, y1);
        this.graphics.fill(x0, y0, x1, y1, col);
    }

    public void fillGradient(int x0, int y0, int x1, int y1, int col1, int col2) {
        this.flat(x0, y0, x1, y1);
        this.graphics.fillGradient(x0, y0, x1, y1, col1, col2);
    }

    public void horizontalLine(int x0, int x1, int y, int col) {
        if (x1 < x0) {
            int tmp = x0;
            x0 = x1;
            x1 = tmp;
        }
        this.fill(x0, y, x1 + 1, y + 1, col);
    }

    public void verticalLine(int x, int y0, int y1, int col) {
        if (y1 < y0) {
            int tmp = y0;
            y0 = y1;
            y1 = tmp;
        }
        this.fill(x, y0 + 1, x + 1, y1, col);
    }

    public void outline(int x, int y, int width, int height, int color) {
        this.fill(x, y, x + width, y + 1, color);
        this.fill(x, y + height - 1, x + width, y + height, color);
        this.fill(x, y + 1, x + 1, y + height - 1, color);
        this.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    // ---- text ----

    public void text(Font font, @Nullable String str, int x, int y, int color) {
        this.text(font, str, x, y, color, true);
    }

    public void text(Font font, @Nullable String str, int x, int y, int color, boolean dropShadow) {
        if (str != null) {
            this.text(font, Language.getInstance().getVisualOrder(FormattedText.of(str)), x, y, color, dropShadow);
        }
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color) {
        this.text(font, str, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence str, int x, int y, int color, boolean dropShadow) {
        // 26.3 leaves out a text with no alpha; 1.21.1's font would make it opaque (it does so below an alpha of 4)
        if ((color & 0xFC000000) == 0) {
            return;
        }
        float[] r = this.rect(x - 1, y - 1, x + font.width(str) + 1, y + font.lineHeight + 1);
        this.above(r);
        this.graphics.drawString(font, str, x, y, color, dropShadow);
        this.raise(r, this.level + TEXT_DEPTH);
    }

    public void text(Font font, Component str, int x, int y, int color) {
        this.text(font, str, x, y, color, true);
    }

    public void text(Font font, Component str, int x, int y, int color, boolean dropShadow) {
        this.text(font, str.getVisualOrderText(), x, y, color, dropShadow);
    }

    public void centeredText(Font font, String str, int x, int y, int color) {
        this.text(font, str, x - font.width(str) / 2, y, color);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        FormattedCharSequence toRender = text.getVisualOrderText();
        this.text(font, toRender, x - font.width(toRender) / 2, y, color);
    }

    public void centeredText(Font font, FormattedCharSequence text, int x, int y, int color) {
        this.text(font, text, x - font.width(text) / 2, y, color);
    }

    public int textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col) {
        return this.textWithWordWrap(font, string, x, y, width, col, true);
    }

    public int textWithWordWrap(Font font, FormattedText string, int x, int y, int width, int col, boolean dropShadow) {
        for (FormattedCharSequence line : font.split(string, width)) {
            this.text(font, line, x, y, col, dropShadow);
            y += 9;
        }
        return y;
    }

    // ---- textures ----

    /** Part of a texture, {@code width} x {@code height} from ({@code u}, {@code v}), at its own size. */
    public void blit(GuiPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
            int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, -1);
    }

    public void blit(GuiPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int textureWidth,
            int textureHeight, int color) {
        this.blit(pipeline, texture, x, y, u, v, width, height, width, height, textureWidth, textureHeight, color);
    }

    public void blit(GuiPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int srcWidth,
            int srcHeight, int textureWidth, int textureHeight) {
        this.blit(pipeline, texture, x, y, u, v, width, height, srcWidth, srcHeight, textureWidth, textureHeight, -1);
    }

    /** {@code srcWidth} x {@code srcHeight} of the texture from ({@code u}, {@code v}), stretched to {@code width} x {@code height}. */
    public void blit(GuiPipeline pipeline, ResourceLocation texture, int x, int y, float u, float v, int width, int height, int srcWidth,
            int srcHeight, int textureWidth, int textureHeight, int color) {
        this.quad(pipeline, texture, x, x + width, y, y + height, u / textureWidth, (u + srcWidth) / textureWidth, v / textureHeight,
                (v + srcHeight) / textureHeight, color);
    }

    /** A texture's ({@code u0}..{@code u1}, {@code v0}..{@code v1}) over the rectangle (x0, y0)-(x1, y1). */
    public void blit(ResourceLocation location, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1) {
        this.quad(GuiPipeline.GUI_TEXTURED, location, x0, x1, y0, y1, u0, u1, v0, v1, -1);
    }

    public void blitSprite(GuiPipeline pipeline, ResourceLocation location, int x, int y, int width, int height) {
        this.blitSprite(pipeline, location, x, y, width, height, -1);
    }

    public void blitSprite(GuiPipeline pipeline, ResourceLocation location, int x, int y, int width, int height, float alpha) {
        this.blitSprite(pipeline, location, x, y, width, height, FastColor.ARGB32.colorFromFloat(alpha, 1.0F, 1.0F, 1.0F));
    }

    /** A GUI sprite over the rectangle, stretched, tiled or nine-sliced as its metadata says (1.21.1 reads it the same). */
    public void blitSprite(GuiPipeline pipeline, ResourceLocation location, int x, int y, int width, int height, int color) {
        if (width == 0 || height == 0) {
            return;
        }
        this.flat(x, y, x + width, y + height);
        this.tinted(pipeline, color, () -> this.graphics.blitSprite(location, x, y, width, height));
    }

    /** The part of a {@code spriteWidth} x {@code spriteHeight} sprite from ({@code textureX}, {@code textureY}) at its size. */
    public void blitSprite(GuiPipeline pipeline, ResourceLocation location, int spriteWidth, int spriteHeight, int textureX, int textureY, int x,
            int y, int width, int height) {
        this.blitSprite(pipeline, location, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height, -1);
    }

    public void blitSprite(GuiPipeline pipeline, ResourceLocation location, int spriteWidth, int spriteHeight, int textureX, int textureY, int x,
            int y, int width, int height, int color) {
        if (width == 0 || height == 0) {
            return;
        }
        this.flat(x, y, x + width, y + height);
        this.tinted(pipeline, color,
                () -> this.graphics.blitSprite(location, spriteWidth, spriteHeight, textureX, textureY, x, y, width, height));
    }

    /** 1.21.1's sprite drawing in {@code color} (multiplied, as 26.3's colour argument is), blended as the pipeline is. */
    private void tinted(GuiPipeline pipeline, int color, Runnable draw) {
        if (pipeline.blended()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        boolean tint = color != -1;
        if (tint) {
            this.graphics.setColor(FastColor.ARGB32.red(color) / 255.0F, FastColor.ARGB32.green(color) / 255.0F,
                    FastColor.ARGB32.blue(color) / 255.0F, FastColor.ARGB32.alpha(color) / 255.0F);
        }
        draw.run();
        if (tint) {
            this.graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        if (pipeline.blended()) {
            RenderSystem.disableBlend();
        }
    }

    /** One textured quad at the current depth, in {@code color}: 26.3's {@code innerBlit}, written for 1.21.1. */
    private void quad(GuiPipeline pipeline, ResourceLocation texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1,
            int color) {
        if (x0 == x1 || y0 == y1) {
            return;
        }
        this.flat(x0, y0, x1, y1);
        this.graphics.flush(); // anything batched goes first, as it was drawn first
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (pipeline.blended()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        } else {
            RenderSystem.disableBlend();
        }
        Matrix4f matrix = this.graphics.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x0, y0, 0.0F).setUv(u0, v0).setColor(color);
        buffer.addVertex(matrix, x0, y1, 0.0F).setUv(u0, v1).setColor(color);
        buffer.addVertex(matrix, x1, y1, 0.0F).setUv(u1, v1).setColor(color);
        buffer.addVertex(matrix, x1, y0, 0.0F).setUv(u1, v0).setColor(color);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.disableBlend();
    }

    // ---- items ----

    public void item(ItemStack itemStack, int x, int y) {
        this.drawItem(this.minecraft.player, itemStack, x, y, 0, false);
    }

    public void item(ItemStack itemStack, int x, int y, int seed) {
        this.drawItem(this.minecraft.player, itemStack, x, y, seed, false);
    }

    public void item(LivingEntity owner, ItemStack itemStack, int x, int y, int seed) {
        this.drawItem(owner, itemStack, x, y, seed, false);
    }

    /** An item drawn for show: no holder (as 26.3's, 1.21.1's own). */
    public void fakeItem(ItemStack itemStack, int x, int y) {
        this.drawItem(null, itemStack, x, y, 0, true);
    }

    public void fakeItem(ItemStack itemStack, int x, int y, int seed) {
        this.drawItem(null, itemStack, x, y, seed, true);
    }

    private void drawItem(@Nullable LivingEntity owner, ItemStack stack, int x, int y, int seed, boolean fake) {
        if (stack.isEmpty()) {
            return;
        }
        float[] r = this.rect(x, y, x + 16, y + 16);
        this.above(r);
        PoseStack poses = this.graphics.pose();
        poses.pushPose();
        poses.translate(0.0F, 0.0F, ITEM_HALF_DEPTH - ITEM_Z); // into the slab in front of the current depth
        if (fake || owner == null) {
            this.graphics.renderFakeItem(stack, x, y, seed);
        } else {
            this.graphics.renderItem(owner, stack, x, y, seed);
        }
        poses.popPose();
        this.raise(r, this.level + ITEM_DEPTH);
    }

    public void itemDecorations(Font font, ItemStack itemStack, int x, int y) {
        this.itemDecorations(font, itemStack, x, y, null);
    }

    /** The durability bar, the cooldown and the count ({@code countText} in its place; "" for none), over the item. */
    public void itemDecorations(Font font, ItemStack itemStack, int x, int y, @Nullable String countText) {
        if (itemStack.isEmpty()) {
            return;
        }
        float[] r = this.rect(x, y, x + 17, y + 17);
        this.above(r);
        PoseStack poses = this.graphics.pose();
        poses.pushPose();
        poses.translate(0.0F, 0.0F, -DECORATION_Z); // the count back at the current depth, over the item drawn before it
        this.graphics.renderItemDecorations(font, itemStack, x, y, countText);
        poses.popPose();
        this.raise(r, this.level + TEXT_DEPTH);
    }

    // ---- tooltips, kept for the end of the frame ----

    public void setTooltipForNextFrame(Component component, int x, int y) {
        this.setTooltipForNextFrame(List.of(component.getVisualOrderText()), x, y);
    }

    public void setTooltipForNextFrame(List<FormattedCharSequence> lines, int x, int y) {
        this.setTooltipForNextFrame(this.minecraft.font, lines, x, y);
    }

    public void setTooltipForNextFrame(Font font, ItemStack itemStack, int xo, int yo) {
        this.defer(() -> this.graphics.renderTooltip(font, itemStack, xo, yo));
    }

    public void setTooltipForNextFrame(Font font, List<Component> texts, Optional<TooltipComponent> image, ItemStack stack, int mouseX,
            int mouseY) {
        if (!texts.isEmpty()) {
            this.defer(() -> this.graphics.renderTooltip(font, texts, image, stack, mouseX, mouseY));
        }
    }

    public void setTooltipForNextFrame(Font font, List<Component> texts, Optional<TooltipComponent> image, int xo, int yo) {
        if (!texts.isEmpty()) {
            this.defer(() -> this.graphics.renderTooltip(font, texts, image, xo, yo));
        }
    }

    public void setTooltipForNextFrame(Font font, Component text, int xo, int yo) {
        this.setTooltipForNextFrame(font, List.of(text.getVisualOrderText()), xo, yo);
    }

    public void setTooltipForNextFrame(Font font, List<? extends FormattedCharSequence> lines, int xo, int yo) {
        if (!lines.isEmpty()) {
            List<? extends FormattedCharSequence> kept = List.copyOf(lines);
            this.defer(() -> this.graphics.renderTooltip(font, kept, xo, yo));
        }
    }

    public void setComponentTooltipForNextFrame(Font font, List<Component> lines, int xo, int yo) {
        if (!lines.isEmpty()) {
            this.defer(() -> this.graphics.renderComponentTooltip(font, lines, xo, yo));
        }
    }

    /**
     * The first tooltip of a frame is the one shown, as in 26.3.
     * PORT: drawn by 1.21.1's tooltip, which leaves 2 px after the first line of every tooltip (26.3: only an item's) and
     * knows no tooltip style (26.3's TOOLTIP_STYLE component).
     */
    private void defer(Runnable tooltip) {
        if (this.deferredTooltip == null) {
            this.deferredTooltip = tooltip;
        }
    }

    // ---- depth ----

    /** The depth the pose stands at now (its translation out of the screen). */
    private float depth() {
        return this.graphics.pose().last().pose().m32();
    }

    /** Drawing at the plane over (x0, y0)-(x1, y1) in pose coordinates: in front of what of ours it overlaps. */
    private void flat(float x0, float y0, float x1, float y1) {
        this.above(this.rect(x0, y0, x1, y1));
    }

    /** Moves the depth in front of every item and text {@code area} (screen rectangle; null: all) overlaps, and the pose to it. */
    private void above(float @Nullable [] area) {
        if (!this.raised.isEmpty()) {
            boolean hit = area == null;
            for (int i = 0; !hit && i < this.raised.size(); i++) {
                float[] r = this.raised.get(i);
                hit = area[0] < r[2] && r[0] < area[2] && area[1] < r[3] && r[1] < area[3];
            }
            if (hit) {
                this.level = Math.max(this.level, this.raisedTop);
                this.raised.clear();
                this.raisedTop = this.level;
            }
        }
        float z = this.depth();
        if (z < this.level) {
            this.graphics.pose().translate(0.0F, 0.0F, this.level - z);
        } else {
            this.level = z;
        }
    }

    private void raise(float[] area, float top) {
        this.raised.add(area);
        this.raisedTop = Math.max(this.raisedTop, top);
    }

    /** (x0, y0)-(x1, y1) in pose coordinates, on the screen. */
    private float[] rect(float x0, float y0, float x1, float y1) {
        Matrix4f m = this.graphics.pose().last().pose();
        float ax = m.m00() * x0 + m.m10() * y0 + m.m30();
        float ay = m.m01() * x0 + m.m11() * y0 + m.m31();
        float bx = m.m00() * x1 + m.m10() * y1 + m.m30();
        float by = m.m01() * x1 + m.m11() * y1 + m.m31();
        float cx = m.m00() * x0 + m.m10() * y1 + m.m30();
        float cy = m.m01() * x0 + m.m11() * y1 + m.m31();
        float dx = m.m00() * x1 + m.m10() * y0 + m.m30();
        float dy = m.m01() * x1 + m.m11() * y0 + m.m31();
        return new float[] {Math.min(Math.min(ax, bx), Math.min(cx, dx)), Math.min(Math.min(ay, by), Math.min(cy, dy)),
                Math.max(Math.max(ax, bx), Math.max(cx, dx)), Math.max(Math.max(ay, by), Math.max(cy, dy))};
    }
}
