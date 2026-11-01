package dev.nookworks.anchor;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import dev.nookworks.Nookworks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * Where warping is stopped: round every Warp Anchor that is on, a 9x5x9 - a spawner's 9x3x9 spawning area with one
 * more block above and below (the user, 2026-10-31), centred on the anchor. A warp that starts in it or ends in it does
 * not happen: an enderman's or a shulker's teleport, an ender pearl's, chorus fruit's, and a Lode Pearl's (asked by
 * ThrownLodePearl). Elevators, the Dock-a-Daemon Door, Item Warps, commands and other mods' own teleports are left
 * alone - they post the plain event, not these, and the user wanted them kept (「エレベーターとかドッカデーモンドアは
 * 止めたくない」). A player stopped is told so on the action bar; a puff of reverse-portal motes marks a stopped warp.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class WarpAnchors {
    /** Blocks out from the anchor each way across, and up and down. */
    public static final int ACROSS = 4;
    public static final int UP_DOWN = 2;
    /** Every loaded anchor, on or off, by level (a level closing takes its list with it). */
    private static final Map<Level, Set<BlockPos>> ANCHORS = new WeakHashMap<>();

    private WarpAnchors() {}

    static synchronized void add(Level level, BlockPos pos) {
        ANCHORS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void remove(Level level, BlockPos pos) {
        Set<BlockPos> anchors = ANCHORS.get(level);
        if (anchors != null) {
            anchors.remove(pos);
        }
    }

    /** Whether a Warp Anchor that is on holds the place {@code at} in {@code level}: nothing warps into or out of it. */
    public static synchronized boolean holds(Level level, Vec3 at) {
        Set<BlockPos> anchors = ANCHORS.get(level);
        if (anchors == null || anchors.isEmpty()) {
            return false;
        }
        int x = net.minecraft.util.Mth.floor(at.x);
        int y = net.minecraft.util.Mth.floor(at.y);
        int z = net.minecraft.util.Mth.floor(at.z);
        for (BlockPos anchor : anchors) {
            if (Math.abs(x - anchor.getX()) <= ACROSS && Math.abs(z - anchor.getZ()) <= ACROSS && Math.abs(y - anchor.getY()) <= UP_DOWN
                    && level.isLoaded(anchor) && level.getBlockState(anchor).getBlock() instanceof WarpAnchorBlock
                    && level.getBlockState(anchor).getValue(WarpAnchorBlock.ON)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a warp of {@code entity} to {@code target} in {@code targetLevel} is stopped: it starts or ends in an anchor's hold. */
    public static boolean stops(Entity entity, Level targetLevel, Vec3 target) {
        return holds(entity.level(), entity.position()) || holds(targetLevel, target);
    }

    /**
     * A warp of {@code entity} was stopped: motes where it stands, a low hum, and a player told why. A mob is marked once
     * a second at most: an enderman in the rain tries every tick.
     */
    public static void stopped(Entity entity) {
        if (!(entity instanceof ServerPlayer) && entity.tickCount % 20 != 0) {
            return;
        }
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                    12, 0.3, entity.getBbHeight() / 3, 0.3, 0.02);
            level.playSound(null, entity.blockPosition(), SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.35F, 1.8F);
        }
        if (entity instanceof ServerPlayer player) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.warp_anchor.stopped"));
        }
    }

    // Low priority: a claim mod at the usual priority is heard first, and a warp it stopped is not ours to mark.
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onEnderEntity(EntityTeleportEvent.EnderEntity event) {
        stop(event);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        stop(event);
    }

    /** Chorus fruit, and anything else eaten that warps whoever eats it. */
    @SubscribeEvent(priority = EventPriority.LOW)
    static void onItemConsumption(EntityTeleportEvent.ItemConsumption event) {
        stop(event);
    }

    private static <E extends EntityTeleportEvent & net.neoforged.bus.api.ICancellableEvent> void stop(E event) {
        if (!event.isCanceled() && stops(event.getEntity(), event.getTargetLevel(), event.getTarget())) {
            event.setCanceled(true);
            stopped(event.getEntity());
        }
    }
}
