package dev.nookworks.port.render;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;

/** 26.3's {@code EntityRenderState}: what every entity renderer is handed of its entity, the fields the mod reads. */
public class EntityRenderState {
    public static final int NO_OUTLINE = 0;
    public @Nullable EntityType<?> entityType;
    public double x;
    public double y;
    public double z;
    public float ageInTicks;
    public float boundingBoxWidth;
    public float boundingBoxHeight;
    public float eyeHeight;
    public double distanceToCameraSq;
    public boolean isInvisible;
    public boolean isDiscrete;
    public boolean displayFireAnimation;
    public int lightCoords = dev.nookworks.port.util.LightCoordsUtil.FULL_BRIGHT;
    /**
     * The glow outline's colour, or 0. In 1.21.1 a glowing entity's outline is made by the buffers it is drawn into, so
     * nothing reads this but the mod's own code.
     */
    public int outlineColor = NO_OUTLINE;
    public @Nullable Component nameTag;
    public float shadowRadius;
    public float partialTick;

    public boolean appearsGlowing() {
        return this.outlineColor != NO_OUTLINE;
    }
}
