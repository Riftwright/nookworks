package dev.nookworks.port.render;

import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;

/**
 * The game's camera as it stands, but in another fluid: for working out the fog a picture has where it is not the
 * camera's own (26.3 asks its fog environments for that place's fog straight; 1.21.1's {@code FogRenderer} reads it all
 * off a camera). Everything it is asked - where it is, which way it looks, whose eyes it is - is the real camera's,
 * only {@link #getFluidInCamera()} answers the fluid given. It is never set up as a camera of its own, so nothing
 * listening for cameras being set up hears of it.
 */
public final class FogCamera extends Camera {
    private final Camera real;
    private final FogType fluid;

    public FogCamera(Camera real, FogType fluid) {
        this.real = real;
        this.fluid = fluid;
        // the way it looks, for the sunrise's tint on the fog (read off fields the rotation sets)
        this.setRotation(real.getYRot(), real.getXRot(), real.getRoll());
        this.setPosition(real.getPosition());
    }

    @Override
    public FogType getFluidInCamera() {
        return this.fluid;
    }

    @Override
    public Entity getEntity() {
        return this.real.getEntity();
    }

    @Override
    public Vec3 getPosition() {
        return this.real.getPosition();
    }

    @Override
    public BlockPos getBlockPosition() {
        return this.real.getBlockPosition();
    }

    @Override
    public float getXRot() {
        return this.real.getXRot();
    }

    @Override
    public float getYRot() {
        return this.real.getYRot();
    }

    @Override
    public Quaternionf rotation() {
        return this.real.rotation();
    }

    @Override
    public boolean isDetached() {
        return this.real.isDetached();
    }
}
