package dev.nookworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The hand raised to the brow while squinting ({@link Squint}): an arm pose of our own for third person (an extension
 * of vanilla's, META-INF/enumextensions.json), and the transforms that put the off hand at the top of the view in first
 * person, with whatever it holds.
 */
public final class SquintPose {
    /** The off arm up before the face, the hand at the brow: as the spyglass's arm comes to the eye, higher and further in. */
    public static final EnumProxy<HumanoidModel.ArmPose> SHADING = new EnumProxy<>(HumanoidModel.ArmPose.class, false, false,
            (IArmPoseTransformer) SquintPose::shade);

    // First person, in the view's own space (x right, y up, -z ahead, from the eye; for the right arm - the left is the
    // mirror): the shoulder out of sight above and beside, the fist at the top edge a little past the middle, the arm laid
    // from one to the other and turned a little on itself - a band across the top of the view, set by eye (2026-10-10).
    private static final Vector3f SHOULDER = new Vector3f(0.8F, 0.34F, -0.4F);
    private static final Vector3f FIST = new Vector3f(-0.08F, 0.505F, -0.6F);
    private static final float TWIST = -10.0F;

    private SquintPose() {}

    static void shade(HumanoidModel<?> model, HumanoidRenderState state, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        part.xRot = Mth.clamp(model.head.xRot - 2.45F - (state.isCrouching ? (float) (Math.PI / 12) : 0.0F), -3.1F, 3.3F);
        part.yRot = model.head.yRot + (right ? -0.6F : 0.6F);
        part.zRot = 0.0F;
    }

    /**
     * Where the first-person arm is drawn: from its shoulder, out of sight, to the fist at the top of the view, coming up
     * from below as {@code up} goes from 0 to 1. The arm's model hangs from its shoulder along +y, so +y is laid along
     * the line from shoulder to fist; the model's own offset from the shoulder is taken back first.
     */
    static void firstPerson(PoseStack pose, float side, float up) {
        // coming in from its own side and from above as it rises, never across the middle of the view
        float out = (1.0F - up) * 0.55F;
        float over = (1.0F - up) * 0.2F;
        Vector3f shoulder = new Vector3f(side * (SHOULDER.x + out), SHOULDER.y + over, SHOULDER.z);
        Vector3f fist = new Vector3f(side * (FIST.x + out), FIST.y + over, FIST.z);
        Vector3f along = new Vector3f(fist).sub(shoulder).normalize();
        pose.translate(shoulder.x, shoulder.y, shoulder.z);
        pose.rotate(new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, along.x, along.y, along.z));
        pose.rotate(Axis.YP.rotationDegrees(side * TWIST));
        // the arm part's pivot (PlayerModel: 5 px out, 2 down from the shoulder line) back to the origin
        pose.translate(side * 5.0F / 16.0F, -2.0F / 16.0F, 0.0F);
    }

    /** From the arm's own place to its hand, as a held item is put there in third person (ItemInHandLayer). */
    static void heldItem(PoseStack pose, float side, HumanoidModel<?> model) {
        (side > 0 ? model.rightArm : model.leftArm).translateAndRotate(pose);
        pose.rotate(Axis.XP.rotationDegrees(-90.0F));
        pose.rotate(Axis.YP.rotationDegrees(180.0F));
        pose.translate((side < 0 ? -1 : 1) / 16.0F, 2.0F / 16.0F, -10.0F / 16.0F);
    }
}
