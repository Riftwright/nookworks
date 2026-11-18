package dev.nookworks.port.render;

import org.joml.Matrix4f;

import com.mojang.math.Axis;

import net.minecraft.core.Direction;

/** 26.3's {@code ChestRenderer}, for what the mod takes of it: the turn of a chest model to the way it faces. */
public final class ChestRenderer {
    private ChestRenderer() {}

    /** A model facing south turned to face {@code facing}, about the block's upright middle line. */
    public static Matrix4f modelTransformation(Direction facing) {
        return new Matrix4f().rotationAround(Axis.YP.rotationDegrees(-facing.toYRot()), 0.5F, 0.0F, 0.5F);
    }
}
