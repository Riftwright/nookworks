package dev.nookworks.client.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.serialization.MapCodec;

import dev.nookworks.Nookworks;
import dev.nookworks.client.PhotoTextures;
import dev.nookworks.photo.Photo;
import dev.nookworks.photo.PhotoPrints;
import dev.nookworks.poster.Posters;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A photo printed as a poster shows its own picture as its item: a sheet of photo paper the print's shape, with the photo
 * on it - in the inventory, in a hand, in a frame. Blank paper until the picture is here. {@code items/poster.json}
 * chooses it, by {@link IsPhoto}, for a poster carrying a photo rather than a design.
 */
public final class PhotoIconRenderer implements SpecialModelRenderer<PhotoIconRenderer.Look> {
    public static final Identifier TYPE = Nookworks.id("photo");
    private static final float PIXEL = 1F / 16;
    /** Just in front of the middle of an item's thickness, as a generated item's face is. */
    private static final float FACE = 0.5F + PIXEL / 2;
    /** The sheet's longest side, in the icon's pixels; the paper shows a pixel round the picture. */
    private static final int SHEET = 14;

    /**
     * What a print's icon shows: the print, and its picture once it is here (null till then). The picture is part of it
     * because the inventory keeps each icon drawn as long as this is the same: drawn blank before the picture came, a
     * print stayed blank for anyone but the photo's taker (review r12 fix check).
     */
    public record Look(PhotoPrints.Print print, @Nullable Identifier picture) {}

    public record Unbaked() implements SpecialModelRenderer.Unbaked<Look> {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<Look> bake(SpecialModelRenderer.BakingContext context) {
            return new PhotoIconRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }

    /** {@code nookworks:photo}: whether a poster carries a photo. */
    public record IsPhoto() implements ConditionalItemModelProperty {
        public static final MapCodec<IsPhoto> MAP_CODEC = MapCodec.unit(new IsPhoto());

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed, ItemDisplayContext context) {
            return PhotoPrints.parse(Posters.idOf(stack)) != null;
        }

        @Override
        public MapCodec<IsPhoto> type() {
            return MAP_CODEC;
        }
    }

    @Override
    public void submit(@Nullable Look look, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            int overlayCoords, boolean hasFoil, int outlineColor) {
        if (look == null) {
            return;
        }
        Photo.Size size = look.print().size();
        float w = (float) SHEET * Math.min(1, (float) size.width() / size.height());
        float h = (float) SHEET * Math.min(1, (float) size.height() / size.width());
        float x0 = (16 - w) / 2 * PIXEL;
        float y0 = (16 - h) / 2 * PIXEL;
        float x1 = x0 + w * PIXEL;
        float y1 = y0 + h * PIXEL;
        quad(poseStack, collector, PhotoTextures.blank(), x0, y0, x1, y1, FACE, lightCoords);
        if (look.picture() != null) {
            quad(poseStack, collector, look.picture(), x0 + PIXEL, y0 + PIXEL, x1 - PIXEL, y1 - PIXEL, FACE + 0.001F, lightCoords);
        }
    }

    /** A picture facing out on both sides of the item, {@code (x0, y0)} its bottom left in the item's space. */
    private static void quad(PoseStack poseStack, SubmitNodeCollector collector, Identifier texture, float x0, float y0, float x1, float y1,
            float z, int lightCoords) {
        collector.submitCustomGeometry(poseStack, RenderTypes.entityCutout(texture), (pose, buffer) -> {
            vertex(pose, buffer, x0, y0, z, 0, 1, lightCoords);
            vertex(pose, buffer, x1, y0, z, 1, 1, lightCoords);
            vertex(pose, buffer, x1, y1, z, 1, 0, lightCoords);
            vertex(pose, buffer, x0, y1, z, 0, 0, lightCoords);
        });
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v, int lightCoords) {
        buffer.addVertex(pose, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightCoords)
                .setNormal(pose, 0, 0, 1);
    }

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
        PhotoPrints.Print print = PhotoPrints.parse(Posters.idOf(stack));
        return print == null ? null : new Look(print, PhotoTextures.texture(print.photo(), print.size()));
    }
}
