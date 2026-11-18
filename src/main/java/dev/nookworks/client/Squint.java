package dev.nookworks.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.nookworks.Nookworks;
import dev.nookworks.NookworksNetwork;
import dev.nookworks.feature.Squinting;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import dev.nookworks.port.render.SubmitNodeCollector;
import dev.nookworks.port.render.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
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
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Squinting into the distance, as OptiFine's zoom did on C (the user, 2026-10-10), see {@link Squinting}: while the key
 * is held the view comes nearer - gently, two times by default, so the spyglass keeps its worth - the mouse slows to
 * match, and the off hand, with whatever it holds, comes up to the brow and shows at the top of the view. Others see
 * the arm raised. Not while the spyglass is up, nor while a thing that asks it ({@link Squinting#NO_SQUINT}) is held.
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class Squint {
    public static final KeyMapping KEY = new KeyMapping("key.nookworks.squint", InputConstants.KEY_C,
            "key.categories.gameplay"); // PORT: 1.21.1 names a key's category by its translation key
    /** Ticks for the hand to come up, or go down. */
    private static final float RISE_TICKS = 5.0F;
    /** How far up the hand is, 0 to 1, this tick and the last (for the frames between). */
    private static float risen;
    private static float risenBefore;
    /** What the server was last told. */
    private static boolean told;
    /** Another mod's zoom key on Squint's key, as last looked for ({@link Squinting#zoomClash}): Squint stands aside. */
    private static @Nullable String clash;
    /** The clash last written to the log, so it is said once and again only when it changes. */
    private static @Nullable String logged;
    /** How often (ticks) the keys are looked at again: a key rebound in Controls counts within a second. */
    private static final int CLASH_EVERY = 20;
    private static int clashIn;

    private Squint() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(KEY);
    }

    /** Whether the player in this game squints now. */
    public static boolean squinting() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.screen == null && KEY.isDown() && !mc.player.isScoping() && clash == null
                && !Squinting.heldKeepsFrom(mc.player.getMainHandItem(), mc.player.getOffhandItem());
    }

    /** Looks again for another mod's zoom on Squint's key, and says in the log when Squint stands aside. */
    private static void lookForClash(Minecraft mc) {
        // the same key and the same modifier (Shift, Ctrl, Alt), as NeoForge counts a clash
        String ours = KEY.saveString() + ":" + KEY.getKeyModifier();
        List<String[]> others = new ArrayList<>();
        for (KeyMapping key : mc.options.keyMappings) {
            if (key != KEY) {
                others.add(new String[] {key.getName(), key.saveString() + ":" + key.getKeyModifier()});
            }
        }
        clash = KEY.isUnbound() ? null : Squinting.zoomClash(ours, others);
        if (clash != null && !clash.equals(logged)) {
            Nookworks.LOGGER.info("Squint stands aside: {} is on the same key ({}). Bind one of them to another key to use both.",
                    clash, KEY.saveString());
        }
        logged = clash;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && --clashIn <= 0) {
            clashIn = CLASH_EVERY;
            lookForClash(Minecraft.getInstance());
        }
        boolean on = squinting();
        risenBefore = risen;
        risen = Mth.approach(risen, on ? 1.0F : 0.0F, 1.0F / RISE_TICKS);
        if (player == null) {
            told = false;
            risen = risenBefore = 0.0F;
        } else if (on != told) {
            PacketDistributor.sendToServer(new NookworksNetwork.Squint(on));
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
        // PORT: 1.21.1's player renderer draws a hand from the player itself (its skin and sleeves)
        if (up <= 0.0F || !(mc.getEntityRenderDispatcher().getRenderer(mc.player) instanceof PlayerRenderer renderer)) {
            return;
        }
        event.setCanceled(true);
        LocalPlayer player = mc.player;
        HumanoidArm arm = player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1.0F : -1.0F;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        SquintPose.firstPerson(pose, side, up);
        SubmitNodeCollector collector = new SubmitNodeCollector(event.getMultiBufferSource());
        if (arm == HumanoidArm.RIGHT) {
            renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        } else {
            renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        }
        ItemStack held = event.getItemStack();
        if (!held.isEmpty()) {
            SquintPose.heldItem(pose, side, renderer.getModel());
            // as it is drawn in third person (ItemInHandRenderer#renderItem until 26.3)
            ItemDisplayContext context = arm == HumanoidArm.RIGHT
                    ? ItemDisplayContext.THIRD_PERSON_RIGHT_HAND : ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            ItemStackRenderState item = new ItemStackRenderState();
            dev.nookworks.port.render.ItemModelResolver.get().updateForTopItem(item, held, context, player.level(), player,
                    player.getId() + context.ordinal());
            item.submit(pose, collector, event.getPackedLight(), OverlayTexture.NO_OVERLAY, 0);
        }
        pose.popPose();
    }

    /**
     * Players squinting - this one, and others as their game says - raise the off hand to the brow, seen from outside.
     * PORT: 26.3 sets the arm pose in the player's render state; 1.21.1's player renderer sets its model's arm poses and
     * then posts this, before the model is posed.
     */
    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) {
            return;
        }
        boolean on = player == Minecraft.getInstance().player ? squinting() : Squinting.of(player);
        if (!on || player.isSleeping() || player.isFallFlying() || player.isSwimming()) {
            return;
        }
        if (player.getMainArm() == HumanoidArm.RIGHT) {
            event.getRenderer().getModel().leftArmPose = SquintPose.SHADING.getValue();
        } else {
            event.getRenderer().getModel().rightArmPose = SquintPose.SHADING.getValue();
        }
    }
}
