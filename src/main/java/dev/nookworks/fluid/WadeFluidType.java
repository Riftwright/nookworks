package dev.nookworks.fluid;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidType;

/**
 * A fluid of ours as a body moves through it. NeoForge 26.3's {@code LivingEntity.travelInFluid} moves an entity in a
 * modded fluid only by the fluid type's {@link #move}, and the default does nothing - no water or lava movement either -
 * so anyone who stepped into Vegetable Oil, Biodiesel, Liquid Experience or Mercury stood frozen in it, not even falling
 * (the user, 2026-11-04: 「植物油踏むと、全く動けなくなる」). Here they move as in water, the drag each fluid's own: oil a
 * little thicker than water, mercury as thick as lava and heavy enough that nobody swims up through it.
 */
public class WadeFluidType extends FluidType {
    private final float drag;

    /** {@code drag}: what is left of the speed each tick, water's 0.8, lava's 0.5. */
    public WadeFluidType(Properties properties, float drag) {
        super(properties);
        this.drag = drag;
    }

    @Override
    public boolean move(LivingEntity entity, Vec3 input, double gravity) {
        boolean falling = entity.getDeltaMovement().y <= 0.0;
        double oldY = entity.getY();
        float speed = 0.02F * (float) entity.getAttributeValue(NeoForgeMod.SWIM_SPEED);
        entity.moveRelative(speed, input);
        entity.move(MoverType.SELF, entity.getDeltaMovement());
        Vec3 movement = entity.getDeltaMovement();
        if (entity.horizontalCollision && entity.onClimbable()) {
            movement = new Vec3(movement.x, 0.2, movement.z);
        }
        movement = movement.multiply(this.drag, 0.8F, this.drag);
        entity.setDeltaMovement(entity.getFluidFallingAdjustedMovement(gravity, falling, movement));
        // up out of it onto a bank, as out of water
        Vec3 now = entity.getDeltaMovement();
        if (entity.horizontalCollision && entity.isFree(now.x, now.y + 0.6F - entity.getY() + oldY, now.z)) {
            entity.setDeltaMovement(now.x, 0.3F, now.z);
        }
        return true;
    }
}
