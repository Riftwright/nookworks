package dev.nookworks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.nookworks.Nookworks;
import dev.nookworks.registry.ModKitchen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import dev.nookworks.port.render.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import dev.nookworks.port.render.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * A Wooden Tray lifted with its food is carried in both hands (the user, 2026-11-14, play-testing: 「トレイもってるときに、
 * 手も前に出ないと、トレイが浮いてるだけになっちゃうかな。3人称の時も、手を少し前に出してほしい」). In first person both
 * hands come up from below and hold it by its sides, as a map is held in both hands; seen from outside, both arms are
 * held out before the body, the tray on them. Only with the other hand empty, as a map is: what the other hand holds is
 * held as ever.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class TrayCarry {
    /** Both arms out before the body, a little down and in: the tray's sides in the hands (META-INF/enumextensions.json). */
    // PORT: 1.21.1's arm pose takes whether it is two-handed and its transformer
    public static final EnumProxy<HumanoidModel.ArmPose> CARRYING = new EnumProxy<>(HumanoidModel.ArmPose.class, true,
            (IArmPoseTransformer) TrayCarry::carry);

    /** How far the arms are raised from hanging, and turned in toward the middle, in radians. */
    private static final float RAISED = 0.95F;
    private static final float IN = 0.32F;

    private TrayCarry() {}

    /** Whether {@code stack} is a tray lifted with what is on it. */
    public static boolean carried(ItemStack stack) {
        return stack.is(ModKitchen.WOODEN_TRAY_ITEM.get()) && stack.has(DataComponents.CONTAINER);
    }

    /** Whether {@code entity} carries a tray in both hands: in its main hand, the other empty. */
    public static boolean carrying(LivingEntity entity) {
        return carried(entity.getMainHandItem()) && entity.getOffhandItem().isEmpty();
    }

    /** PORT: 1.21.1 poses from the entity itself, not a render state. */
    static void carry(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        boolean right = arm == HumanoidArm.RIGHT;
        ModelPart part = right ? model.rightArm : model.leftArm;
        part.xRot = -RAISED - (entity.isCrouching() ? 0.3F : 0.0F);
        part.yRot = right ? -IN : IN;
        part.zRot = 0.0F;
    }

    /**
     * Seen from outside: both arms out, whoever carries it. PORT: 26.3 sets the arm poses in the player's render state;
     * 1.21.1's player renderer sets its model's arm poses and then posts this, before the model is posed.
     */
    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (event.getEntity() instanceof AbstractClientPlayer player && carrying(player) && !player.isSleeping() && !player.isSwimming()
                && !player.isFallFlying()) {
            event.getRenderer().getModel().rightArmPose = CARRYING.getValue();
            event.getRenderer().getModel().leftArmPose = CARRYING.getValue();
        }
    }

    // First person: the view's own space (x right, y up, -z ahead, from the eye).
    /** Where the tray's foot and middle are held, and how large it is drawn (set by eye, 2026-11-14). */
    private static final float TRAY_Y = -0.33F;
    private static final float TRAY_Z = -0.80F;
    private static final float TRAY_SCALE = 0.64F;
    /** Each hand at the tray's side, a little in from its near edge, and the arm's way back to the shoulder from there. */
    private static final float HAND_X = 0.33F;
    private static final org.joml.Vector3f ARM_BACK = new org.joml.Vector3f(0.3F, -0.55F, 0.45F).normalize(0.74F);

    /** In first person: the tray before the view, held by both hands at its sides, coming up as it is taken in hand. */
    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !carrying(player)) {
            return;
        }
        event.setCanceled(true); // the other hand is empty: nothing of its own to draw
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        SubmitNodeCollector collector = new SubmitNodeCollector(event.getMultiBufferSource());
        int light = event.getPackedLight();
        float drop = event.getEquipProgress() * 0.6F;
        float swing = Mth.sin(Mth.sqrt(event.getSwingProgress()) * (float) Math.PI);
        pose.pushPose();
        pose.translate(0.0F, -drop + swing * 0.04F, -swing * 0.1F);
        // PORT: 1.21.1's player renderer draws a hand from the player itself (its skin and sleeves)
        if (!player.isInvisible() && mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            for (HumanoidArm arm : HumanoidArm.values()) {
                float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
                pose.pushPose();
                hand(pose, side);
                if (arm == HumanoidArm.RIGHT) {
                    renderer.renderRightHand(pose, event.getMultiBufferSource(), light, player);
                } else {
                    renderer.renderLeftHand(pose, event.getMultiBufferSource(), light, player);
                }
                pose.popPose();
            }
        }
        pose.pushPose();
        pose.translate(0.0F, TRAY_Y, TRAY_Z);
        pose.scale(TRAY_SCALE, TRAY_SCALE, TRAY_SCALE);
        pose.translate(0.0F, 0.5F, 0.0F); // the item renderer centres the model on the origin: its foot back to TRAY_Y
        ItemStackRenderState item = new ItemStackRenderState();
        dev.nookworks.port.render.ItemModelResolver.get().updateForTopItem(item, event.getItemStack(), ItemDisplayContext.NONE, player.level(), player, player.getId());
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
        pose.popPose();
    }

    /**
     * Where one hand is drawn: the arm laid from its shoulder, out of sight below and beside, up to the tray's side, palm
     * in. The arm's model hangs from its shoulder along +y; laid along the line shoulder to hand, its own offset taken back.
     */
    private static void hand(PoseStack pose, float side) {
        org.joml.Vector3f hand = new org.joml.Vector3f(side * HAND_X, TRAY_Y + 0.02F, TRAY_Z + 0.08F);
        org.joml.Vector3f shoulder = new org.joml.Vector3f(hand).add(side * ARM_BACK.x, ARM_BACK.y, ARM_BACK.z);
        org.joml.Vector3f along = new org.joml.Vector3f(hand).sub(shoulder).normalize();
        pose.translate(shoulder.x, shoulder.y, shoulder.z);
        dev.nookworks.port.render.Poses.rotate(pose, new org.joml.Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, along.x, along.y, along.z));
        dev.nookworks.port.render.Poses.rotate(pose, Axis.YP.rotationDegrees(side * 90.0F));
        pose.translate(side * 5.0F / 16.0F, -2.0F / 16.0F, 0.0F);
    }
}
