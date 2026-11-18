package dev.nookworks.compat.create.client;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.nookworks.compat.create.CreateDrives;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.minecraft.core.Direction;

/**
 * Client only: how far round a Create block's shaft end is, in degrees about the positive end of its axis - the angle
 * Create draws that shaft at (its clock, the speed at that end, the half-tooth offset of its position), so the axle of
 * the Nookworks machine set against it turns as one piece with it (CrankRenderer asks it as a DriveSource).
 */
public final class ShaftLook {
    private ShaftLook() {}

    public static float degrees(KineticBlockEntity kinetic, Direction face) {
        if (kinetic.getLevel() == null) {
            return 0.0F;
        }
        Direction.Axis axis = face.getAxis();
        float time = AnimationTickHolder.getRenderTime(kinetic.getLevel());
        float offset = KineticBlockEntityRenderer.getRotationOffsetForPosition(kinetic, kinetic.getBlockPos(), axis);
        return (time * CreateDrives.faceSpeedForLook(kinetic, face) * 3.0F / 10.0F + offset) % 360.0F;
    }
}
