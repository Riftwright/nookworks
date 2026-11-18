package dev.nookworks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import dev.nookworks.furniture.SeatEntity;
import dev.nookworks.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import dev.nookworks.port.render.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import dev.nookworks.port.render.EntityRenderState;
import dev.nookworks.port.render.CameraRenderState;

/**
 * Draws nothing: the seat under a sitting player is meant to be invisible.
 * <p>
 * It still has to exist. The client asks the dispatcher for a renderer for every entity it can see, and an
 * entity type with none at all brings the whole render frame down with a null renderer, so "invisible" has to
 * be a renderer that declines to draw rather than no renderer.
 */
public class SeatRenderer extends EntityRenderer<SeatEntity, EntityRenderState> {
    public SeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }

    @Override
    public boolean shouldRender(SeatEntity entity, Frustum frustum, double x, double y, double z, float partialTicks) {
        return false;
    }

    @Override
    public void submit(EntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera) {
    }
}
