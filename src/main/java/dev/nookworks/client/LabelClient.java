package dev.nookworks.client;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.label.LabelEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * The client's side of labels: using a label is using the block it is stuck on - a label on a chest's front must not be
 * in the way of opening the chest. Hitting it still knocks it off. (What a label item prints is drawn by its model,
 * client/render/LabelIconRenderer.)
 */
@EventBusSubscriber(modid = Nookworks.MODID, value = Dist.CLIENT)
public final class LabelClient {
    private LabelClient() {}

    /**
     * Fired for each hand before the game decides what a right click is aimed at. With a label under the crosshair,
     * the aim is moved to the block behind it, at the same point of its face, and the game goes on from there as if
     * the block had been clicked - the same checks, the same packets, the same sounds.
     * <p>
     * A label is an entity, and an entity is only in reach within three blocks, a block within four and a half: from
     * further off the game finds the label, finds it out of reach, and makes the click a miss - and the chest behind
     * it, well in reach, would not open. So a miss is looked along again for a label within a block's reach.
     */
    @SubscribeEvent
    static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        if (!event.isUseItem() || mc.player == null || mc.hitResult == null) {
            return;
        }
        EntityHitResult hit = mc.hitResult instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LabelEntity ? entityHit
                : mc.hitResult.getType() == HitResult.Type.MISS ? labelInBlockReach(mc) : null;
        if (hit == null || !(hit.getEntity() instanceof LabelEntity label)) {
            return;
        }
        Vec3 onFace = hit.getLocation().relative(label.getDirection(), -LabelEntity.DEPTH);
        mc.hitResult = new BlockHitResult(onFace, label.getDirection(), label.getPos(), false);
    }

    /** The label the player looks at within a block's reach (nothing solid in front: the pick found nothing nearer). */
    private static @Nullable EntityHitResult labelInBlockReach(Minecraft mc) {
        var player = mc.player;
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F).scale(reach);
        return ProjectileUtil.getEntityHitResult(player, eye, eye.add(look),
                player.getBoundingBox().expandTowards(look).inflate(1.0), entity -> entity instanceof LabelEntity, reach * reach);
    }
}
