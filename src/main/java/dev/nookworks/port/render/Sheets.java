package dev.nookworks.port.render;

import java.util.function.Function;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;

/**
 * 26.3's {@code Sheets}, the members the mod names: the banner's textures, and the mappers from a texture's short name to
 * its sprite in an atlas (26.3's {@code SpriteId} is 1.21.1's {@link Material}: an atlas and a texture in it).
 */
public final class Sheets {
    private Sheets() {}

    /**
     * A banner's own wood and cloth ({@code entity/banner_base}): 1.21.1 keeps this one in ModelBakery, and its
     * {@code Sheets.BANNER_BASE} is 26.3's {@code BANNER_PATTERN_BASE}, the base colour's layer.
     */
    public static final Material BANNER_BASE = net.minecraft.client.resources.model.ModelBakery.BANNER_BASE;
    public static final Material BANNER_PATTERN_BASE = net.minecraft.client.renderer.Sheets.BANNER_BASE;
    /** {@code name} to the block atlas's {@code namespace:block/path}. */
    public static final Function<ResourceLocation, Material> BLOCKS_MAPPER =
            id -> new Material(TextureAtlas.LOCATION_BLOCKS, id.withPrefix("block/"));
    /** {@code name} to the chest atlas's {@code namespace:entity/chest/path}. */
    public static final Function<ResourceLocation, Material> CHEST_MAPPER =
            id -> new Material(net.minecraft.client.renderer.Sheets.CHEST_SHEET, id.withPrefix("entity/chest/"));

    // 1.21.1's own members, for the mod's code that names them (the mapping points every Sheets here)

    public static RenderType solidBlockSheet() {
        return net.minecraft.client.renderer.Sheets.solidBlockSheet();
    }

    public static RenderType cutoutBlockSheet() {
        return net.minecraft.client.renderer.Sheets.cutoutBlockSheet();
    }

    public static RenderType translucentItemSheet() {
        return net.minecraft.client.renderer.Sheets.translucentItemSheet();
    }

    public static RenderType translucentCullBlockSheet() {
        return net.minecraft.client.renderer.Sheets.translucentCullBlockSheet();
    }
}
