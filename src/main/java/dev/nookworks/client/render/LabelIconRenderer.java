package dev.nookworks.client.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * What is printed on a label, drawn as part of its item: the picture of the item it shows, half size and pressed flat
 * onto the card, or its letters in dark ink - wherever the item is drawn, so a label shows what it prints in JEI's
 * list, in a hand and in a frame as in the inventory. It is laid over the plain card ({@code items/label.json}, a
 * composite), and draws nothing for a blank label.
 * <p>
 * On a screen ({@code "gui": true}, chosen by the display context) the letters keep to whole pixels of the screen: each
 * of the font's pixels the same whole number of them, as many as fit the card and no more than two thirds of a GUI
 * pixel - a part of one would drop columns of the letters - and what still does not fit is cut at the card's edge
 * (the name says it all). Anywhere else the whole text is shrunk to fit, as on a label stuck on a chest.
 */
public final class LabelIconRenderer implements SpecialModelRenderer<LabelIconRenderer.Look> {
    /** The special model's type in an item definition. */
    public static final Identifier TYPE = Nookworks.id("label");

    /** What a label shows: an item's picture, or letters. Compared by value, so a screen draws each kind once. */
    public record Look(@Nullable Item item, String text) {}

    public record Unbaked(boolean gui) implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("gui", false).forGetter(Unbaked::gui)).apply(i, Unbaked::new));

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new LabelIconRenderer(this.gui);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    /** One pixel of the 16x16 icon. A special model draws in a block's space, 0 to 1; the card's middle is its middle. */
    private static final float PIXEL = 1F / 16;
    private static final float MIDDLE = 0.5F;
    /** Just in front of the card's face (item/generated is a pixel thick). */
    private static final float FACE = MIDDLE + PIXEL / 2 + 0.002F;
    /** The picture: half the icon across, as in the world, pressed almost flat. */
    private static final float PICTURE = 0.5F;
    private static final float FLAT = 0.02F;
    /** Letters go within this many of the icon's pixels across (the card's face is 10, its edges a pixel more). */
    private static final int TEXT_W = 10;
    private static final int GUI_TEXT_W = 11;
    /** ...and no taller than this many (the font's letters stand 7 of its pixels high, of a 9-pixel line). */
    private static final float TEXT_H = 5;
    private static final int INK = 0xFF26221E;

    private final boolean gui;

    private LabelIconRenderer(boolean gui) {
        this.gui = gui;
    }

    @Override
    public void submit(@Nullable Look look, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            boolean hasFoil, int outlineColor) {
        if (look == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!look.text().isEmpty()) {
            Font font = minecraft.font;
            String text = look.text();
            int width = Math.max(1, font.width(text));
            float pixels; // icon pixels to one of the font's
            float y;
            if (this.gui) {
                int guiScale = Math.max(1, minecraft.getWindow().getGuiScale());
                int screenPixels = Math.max(1, (int) Math.min((float) GUI_TEXT_W * guiScale / width, guiScale * 2 / 3F));
                pixels = screenPixels / (float) guiScale;
                if (width * pixels > GUI_TEXT_W) {
                    text = font.plainSubstrByWidth(text, (int) (GUI_TEXT_W / pixels));
                    width = Math.max(1, font.width(text));
                }
                y = -4; // whole font pixels: the letters' middle half of one above the card's
            } else {
                pixels = Math.min((float) TEXT_W / width, TEXT_H / 7);
                y = -3.5F;
            }
            float scale = pixels * PIXEL;
            poseStack.pushPose();
            poseStack.translate(MIDDLE, MIDDLE, FACE);
            poseStack.scale(scale, -scale, scale);
            collector.submitText(poseStack, this.gui ? -(width / 2) : -width / 2F, y, Component.literal(text).getVisualOrderText(),
                    false, Font.DisplayMode.POLYGON_OFFSET, lightCoords, INK, 0, 0);
            poseStack.popPose();
        } else if (look.item() != null) {
            ItemStackRenderState picture = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(picture, new ItemStack(look.item()), ItemDisplayContext.GUI, minecraft.level, null, 0);
            if (picture.isEmpty()) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(MIDDLE, MIDDLE, FACE);
            poseStack.scale(PICTURE, PICTURE, PICTURE);
            poseStack.last().pose().scale(1, 1, FLAT); // a printed picture: a block keeps the way its sides fall, but lies flat
            picture.submit(poseStack, collector, lightCoords, overlayCoords, outlineColor);
            poseStack.popPose();
        }
    }

    /** No further out than the card itself. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float x : new float[] {0, 1}) {
            for (float y : new float[] {0, 1}) {
                for (float z : new float[] {1 - FACE, FACE}) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }

    @Override
    public @Nullable Look extractArgument(ItemStack stack) {
        return look(stack);
    }

    /** What a label prints, or nothing for a blank one. */
    public static @Nullable Look look(ItemStack stack) {
        String text = LabelItem.text(stack);
        ItemStack shows = LabelItem.shows(stack);
        if (text.isEmpty() && shows.isEmpty()) {
            return null;
        }
        return new Look(text.isEmpty() ? shows.getItem() : null, text);
    }
}
