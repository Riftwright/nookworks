package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.resources.model.Material;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * 26.3's {@code BannerRenderer.submitPatterns}: over a banner's (or a shield's) cloth, its base colour's layer and then
 * each of its patterns, up to sixteen, each a copy of the model in that pattern's picture tinted its dye - as 1.21.1's
 * {@code BannerRenderer.renderPatterns} lays them, without drawing the plain cloth again (the caller has).
 */
public final class BannerRenderer {
    private BannerRenderer() {}

    public static <S> void submitPatterns(SpriteGetter sprites, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords,
            int overlayCoords, Model<S> model, S state, boolean banner, DyeColor baseColor, BannerPatternLayers patterns) {
        layer(sprites, poseStack, collector, lightCoords, overlayCoords, model, state,
                banner ? Sheets.BANNER_PATTERN_BASE : net.minecraft.client.renderer.Sheets.SHIELD_BASE, baseColor);
        for (int i = 0; i < 16 && i < patterns.layers().size(); i++) {
            BannerPatternLayers.Layer layer = patterns.layers().get(i);
            Material sprite = banner ? net.minecraft.client.renderer.Sheets.getBannerMaterial(layer.pattern())
                    : net.minecraft.client.renderer.Sheets.getShieldMaterial(layer.pattern());
            layer(sprites, poseStack, collector, lightCoords, overlayCoords, model, state, sprite, layer.color());
        }
    }

    private static <S> void layer(SpriteGetter sprites, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords,
            Model<S> model, S state, Material sprite, DyeColor color) {
        collector.submitModel(model, state, poseStack, sprite.renderType(RenderTypes::bannerPattern), lightCoords, overlayCoords,
                color.getTextureDiffuseColor(), sprites.get(sprite), 0);
    }
}
