package dev.nookworks.port.render;

import org.joml.Quaternionf;
import org.joml.Quaternionfc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * 26.3's turns on a {@link PoseStack} and on a {@link PoseStack.Pose} that 1.21.1 lacks ({@code rotate},
 * {@code rotateDegrees}, a pose's own {@code translate}), as calls taking the pose first. A scripted edit
 * (tools/port1211/tx/render.py) rewrites the mod's {@code pose.rotate(...)} to {@code Poses.rotate(pose, ...)}.
 */
public final class Poses {
    private Poses() {}

    public static void rotate(PoseStack poseStack, Quaternionfc by) {
        poseStack.mulPose(new Quaternionf(by));
    }

    /** A turn of {@code angle} radians about {@code axis}. */
    public static void rotate(PoseStack poseStack, Axis axis, float angle) {
        poseStack.mulPose(axis.rotation(angle));
    }

    public static void rotateDegrees(PoseStack poseStack, Axis axis, float angle) {
        poseStack.mulPose(axis.rotationDegrees(angle));
    }

    public static void rotateAround(PoseStack poseStack, Quaternionfc rotation, float pivotX, float pivotY, float pivotZ) {
        poseStack.rotateAround(new Quaternionf(rotation), pivotX, pivotY, pivotZ);
    }

    /** The pose turned by {@code by}: its position matrix and its normals both. */
    public static void rotate(PoseStack.Pose pose, Quaternionfc by) {
        pose.pose().rotate(by);
        pose.normal().rotate(by);
    }

    public static void rotate(PoseStack.Pose pose, Axis axis, float angle) {
        rotate(pose, axis.rotation(angle));
    }

    public static void rotateDegrees(PoseStack.Pose pose, Axis axis, float angle) {
        rotate(pose, axis.rotationDegrees(angle));
    }

    /** The pose moved by (x, y, z) in its own frame; the normals are unchanged by a move. */
    public static void translate(PoseStack.Pose pose, float x, float y, float z) {
        pose.pose().translate(x, y, z);
    }
}
