package dev.nookworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.Squinting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;

/**
 * Squinting into the distance, as OptiFine's zoom did on C (the user, 2026-10-10), see {@link Squinting}: while the key
 * is held the view comes nearer - gently, two times by default, so the spyglass keeps its worth - the mouse slows to
 * match, and the off hand, with whatever it holds, comes up to the brow and shows at the top of the view. Others see
 * the arm raised. Not while the spyglass is up.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class Squint {
    public static final KeyMapping KEY = new KeyMapping("key.nookworks.squint", InputConstants.KEY_C, KeyMapping.Category.GAMEPLAY);
    /** Ticks for the hand to come up, or go down. */
    private static final float RISE_TICKS = 5.0F;
    /** How far up the hand is, 0 to 1, this tick and the last (for the frames between). */
    private static float risen;
    private static float risenBefore;
    /** What the server was last told. */
    private static boolean told;

    private Squint() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    /** Whether the player in this game squints now. */
    public static boolean squinting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.gui.screen() == null && KEY.isDown() && !mc.player.isScoping();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        boolean on = squinting();
        risenBefore = risen;
        risen = Mth.approach(risen, on ? 1.0F : 0.0F, 1.0F / RISE_TICKS);
        if (player == null) {
            told = false;
            risen = risenBefore = 0.0F;
        } else if (on != told) {
            ClientPacketDistributor.sendToServer(new NookworksNetwork.Squint(on));
            told = on;
        }
    }

    private static double zoom() {
        return NookworksClientConfig.SPEC.isLoaded() ? NookworksClientConfig.SQUINT_ZOOM.get() : 2.0;
    }

    /** The view nearer: the game eases its field of view towards this, as it does for a bow drawn or a sprint. */
    @SubscribeEvent
    static void onFov(ComputeFovModifierEvent event) {
        if (event.getPlayer() == Minecraft.getInstance().player && squinting()) {
            event.setNewFovModifier(event.getNewFovModifier() / (float) zoom());
        }
    }

    /** The mouse slowed as the view is nearer, so a turn of the hand turns the view as far across the screen. */
    @SubscribeEvent
    static void onTurn(CalculatePlayerTurnEvent event) {
        if (squinting()) {
            event.setMouseSensitivity(Squinting.sensitivityFor(event.getMouseSensitivity(), 1.0 / zoom()));
        }
    }

    /**
     * In first person, the off hand raised to the brow: the arm across the top of the view from its side, palm down,
     * coming up as the key goes down, and what it holds held still. Drawn instead of the hand where it was.
     */
    @SubscribeEvent
    static void onHand(RenderHandEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getHand() != InteractionHand.OFF_HAND || mc.player == null || mc.player.isInvisible()
                || NookworksClientConfig.SPEC.isLoaded() && !NookworksClientConfig.SQUINT_HAND.get()) {
            return;
        }
        float up = (float) Squinting.ease(Mth.lerp(event.getPartialTick(), risenBefore, risen));
        AvatarRenderState avatar = event.getPlayerState().avatarRenderState;
        if (up <= 0.0F || avatar == null) {
            return;
        }
        event.setCanceled(true);
        LocalPlayer player = mc.player;
        HumanoidArm arm = player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        SquintPose.firstPerson(pose, side, up);
        AvatarRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(avatar);
        var skin = player.getSkin().body().texturePath();
        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, collector, event.getPackedLight(), skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
        } else {
            renderer.renderLeftHand(pose, collector, event.getPackedLight(), skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
        }
        ItemStack held = event.getItemStack();
        if (!held.isEmpty()) {
            SquintPose.heldItem(pose, side, renderer.getModel());
            // as it is drawn in third person (ItemInHandRenderer#renderItem until 26.3)
            ItemDisplayContext context = arm == HumanoidArm.RIGHT
                    ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            ItemStackRenderState item = new ItemStackRenderState();
            mc.getItemModelResolver().updateForTopItem(item, held, context, player.level(), player,
                    player.getId() + context.ordinal());
            item.submit(pose, collector, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }

    /** Players squinting - this one, and others as their game says - raise the off hand to the brow, seen from outside. */
    public static void registerRenderState(RegisterRenderStateModifiersEvent event) {
        // the renderer's own bound (Avatar), as the event checks it; mannequins and the like are passed by
        event.<Avatar, AvatarRenderState>registerEntityModifier(rawAvatarRenderer(), (avatar, state) -> {
                    if (!(avatar instanceof AbstractClientPlayer player)) {
                        return;
                    }
                    boolean on = player == Minecraft.getInstance().player ? squinting() : Squinting.of(player);
                    if (!on || player.isSleeping() || player.isFallFlying() || player.isSwimming()) {
                        return;
                    }
                    if (player.getMainArm() == HumanoidArm.RIGHT) {
                        state.leftArmPose = SquintPose.SHADING.getValue();
                    } else {
                        state.rightArmPose = SquintPose.SHADING.getValue();
                    }
                });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Class<? extends net.minecraft.client.renderer.entity.EntityRenderer<? extends Avatar, ? extends AvatarRenderState>> rawAvatarRenderer() {
        return (Class) AvatarRenderer.class;
    }
}
