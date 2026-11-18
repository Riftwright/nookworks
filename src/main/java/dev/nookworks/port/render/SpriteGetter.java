package dev.nookworks.port.render;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;

/**
 * 26.3's {@code SpriteGetter}: a sprite by its atlas and name. 1.21.1 looks a {@link Material} up in the game's atlases
 * itself, so there is one getter for everyone ({@link #atlases()}), where 26.3's renderer contexts each hand one over.
 */
public final class SpriteGetter {
    private static final SpriteGetter ATLASES = new SpriteGetter();

    private SpriteGetter() {}

    /** The game's atlases, as they are now. */
    public static SpriteGetter atlases() {
        return ATLASES;
    }

    public TextureAtlasSprite get(Material sprite) {
        return sprite.sprite();
    }
}
