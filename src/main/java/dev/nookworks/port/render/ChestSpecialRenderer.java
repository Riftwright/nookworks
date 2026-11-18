package dev.nookworks.port.render;

import java.util.function.Consumer;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.resources.model.Material;

/** 26.3's {@code ChestSpecialRenderer}: a chest's item, its model drawn shut (or as far open as given) in its sprite. */
public class ChestSpecialRenderer implements NoDataSpecialModelRenderer {
    private final SpriteGetter sprites;
    private final ChestModel model;
    private final Material sprite;
    private final float openness;

    public ChestSpecialRenderer(SpriteGetter sprites, ChestModel model, Material sprite, float openness) {
        this.sprites = sprites;
        this.model = model;
        this.sprite = sprite;
        this.openness = openness;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil,
            int outlineColor) {
        submitNodeCollector.submitModel(this.model, this.openness, poseStack, lightCoords, overlayCoords, -1, this.sprite, this.sprites,
                outlineColor);
    }

    /** A chest's block. */
    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        for (float x : new float[] {0, 1}) {
            for (float y : new float[] {0, 1}) {
                for (float z : new float[] {0, 1}) {
                    output.accept(new Vector3f(x, y, z));
                }
            }
        }
    }
}
