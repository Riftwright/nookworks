package dev.nookworks.port.render;

import java.util.List;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * 26.3's {@code Model<S>}: a tree of model parts, posed from a state ({@link #setupAnim}), drawn with a render type made
 * from a texture. The stand-in models of the port ({@link ChestModel}, {@link BannerModel}, {@link BannerFlagModel}) are
 * these, drawn by {@link SubmitNodeCollector#submitModel}.
 */
public abstract class Model<S> {
    protected final ModelPart root;
    protected final Function<ResourceLocation, RenderType> renderType;
    private final List<ModelPart> allParts;

    protected Model(ModelPart root, Function<ResourceLocation, RenderType> renderType) {
        this.root = root;
        this.renderType = renderType;
        this.allParts = root.getAllParts().toList();
    }

    public final Function<ResourceLocation, RenderType> renderType() {
        return this.renderType;
    }

    public final RenderType renderType(ResourceLocation texture) {
        return this.renderType.apply(texture);
    }

    public final void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int lightCoords, int overlayCoords, int color) {
        this.root.render(poseStack, buffer, lightCoords, overlayCoords, color);
    }

    public final ModelPart root() {
        return this.root;
    }

    /** Every part back to its first pose, as 26.3's base does before a subclass turns its own. */
    public void setupAnim(S state) {
        for (ModelPart part : this.allParts) {
            part.resetPose();
        }
    }
}
