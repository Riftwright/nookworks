package dev.nookworks.port.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

/**
 * 26.3's two-step entity renderer on top of 1.21.1's, which draws at once: {@link #render} makes a state, fills it as
 * 26.3 does ({@link #extractRenderState}) and {@link #submit}s it to a {@link SubmitNodeCollector} that draws straight
 * into the buffers handed over; then 1.21.1's own drawing of a leash and a name tag, which is what 26.3's
 * {@code submit} adds when a subclass calls {@code super.submit}.
 */
public abstract class EntityRenderer<T extends Entity, S extends EntityRenderState>
        extends net.minecraft.client.renderer.entity.EntityRenderer<T> {
    protected EntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    public abstract S createRenderState();

    /** The fields of 26.3's base state that 1.21.1 can give, filled as 26.3 fills them. */
    public void extractRenderState(T entity, S state, float partialTicks) {
        state.entityType = entity.getType();
        state.x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        state.y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        state.z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        state.isInvisible = entity.isInvisible();
        state.partialTick = partialTicks;
        state.ageInTicks = entity.tickCount + partialTicks;
        state.boundingBoxWidth = entity.getBbWidth();
        state.boundingBoxHeight = entity.getBbHeight();
        state.eyeHeight = entity.getEyeHeight();
        state.isDiscrete = entity.isDiscrete();
        state.displayFireAnimation = entity.displayFireAnimation();
        state.distanceToCameraSq = this.entityRenderDispatcher.distanceToSqr(entity);
        state.outlineColor = Minecraft.getInstance().shouldEntityAppearGlowing(entity) ? 0xFF000000 | entity.getTeamColor() : 0;
        state.lightCoords = this.getPackedLightCoords(entity, partialTicks);
        state.shadowRadius = this.shadowRadius;
    }

    /** Draws nothing of its own: 1.21.1's leash and name tag follow in {@link #render}. */
    public void submit(S state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light) {
        S state = this.createRenderState();
        this.extractRenderState(entity, state, partialTick);
        // the light 1.21.1 passes is the entity's own, which extractRenderState has set (a renderer may set its own)
        this.submit(state, poseStack, new SubmitNodeCollector(buffers), CameraRenderState.current());
        super.render(entity, yaw, partialTick, poseStack, buffers, light);
    }

    /** Never read: the mod's renderers say what they draw with in {@link #submit}. */
    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    /** 26.3's culling, with its box from {@link #getBoundingBoxForCulling(Entity, float)}. */
    public boolean shouldRender(T entity, Frustum culler, double camX, double camY, double camZ, float partialTicks) {
        if (!entity.shouldRender(camX, camY, camZ)) {
            return false;
        }
        if (!this.affectedByCulling(entity) || entity.noCulling) {
            return true;
        }
        AABB box = this.getBoundingBoxForCulling(entity, partialTicks).inflate(0.5);
        if (box.hasNaN() || box.getSize() == 0.0) {
            box = new AABB(entity.getX() - 2.0, entity.getY() - 2.0, entity.getZ() - 2.0, entity.getX() + 2.0, entity.getY() + 2.0,
                    entity.getZ() + 2.0);
        }
        return culler.isVisible(box);
    }

    @Override
    public boolean shouldRender(T entity, Frustum culler, double camX, double camY, double camZ) {
        return this.shouldRender(entity, culler, camX, camY, camZ,
                Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
    }

    /** The entity's own box where it is drawn this frame (1.21.1 has no interpolated box: the box moved by as much). */
    protected AABB getBoundingBoxForCulling(T entity, float partialTicks) {
        return entity.getBoundingBoxForCulling().move(Mth.lerp(partialTicks, entity.xOld, entity.getX()) - entity.getX(),
                Mth.lerp(partialTicks, entity.yOld, entity.getY()) - entity.getY(),
                Mth.lerp(partialTicks, entity.zOld, entity.getZ()) - entity.getZ());
    }

    protected boolean affectedByCulling(T entity) {
        return true;
    }
}
