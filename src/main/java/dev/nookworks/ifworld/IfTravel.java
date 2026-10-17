package dev.nookworks.ifworld;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModTravel;
import dev.nookworks.travel.DaemonTravel;
import dev.nookworks.travel.TravelLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * Stepping through an If Mirror. From home, into the If: in front of the If's mirror - set down on the first trip, near
 * where the player last was in the If, or, never seen there, at the same place as home (the If is the overworld's
 * double, so it lies where home would). From the If, home: in front of the home mirror, or with none standing, at the
 * player's respawn point. One arrives facing away from the glass.
 */
public final class IfTravel {
    /** Ticks after a trip before a mirror takes the same player again. */
    private static final int PAUSE = 20;
    private static final Map<ServerPlayer, Integer> LAST = new WeakHashMap<>();

    private IfTravel() {}

    public static void through(ServerPlayer player, ServerLevel from, BlockPos mirror) {
        MinecraftServer server = from.getServer();
        Integer last = LAST.get(player);
        if (last != null && server.getTickCount() - last < PAUSE) {
            return;
        }
        LAST.put(player, server.getTickCount());
        TeleportTransition transition = from.dimension().equals(IfWorld.KEY) ? home(player, server) : into(player, from, mirror, server);
        if (transition == null) {
            return;
        }
        // the teleport takes a rider off their mount, once no mod has refused the trip: a refused one leaves them riding (r15)
        from.playSound(null, mirror.above(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.6F);
        if (IfGates.passing(() -> player.teleport(transition)) != null) {
            ServerLevel to = transition.newLevel();
            Vec3 at = transition.position();
            to.playSound(null, BlockPos.containing(at), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F);
            to.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1.0, at.z, 12, 0.3, 0.6, 0.3, 0.01);
            LAST.put(player, server.getTickCount());
            DaemonTravel.arrived(player); // a door's doorway just there does not take them straight on
        }
    }

    /** From home into the If. */
    private static @Nullable TeleportTransition into(ServerPlayer player, ServerLevel from, BlockPos mirror, MinecraftServer server) {
        if (IfWorld.get(server).resetPending()) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if.reset_pending"));
            return null; // it goes at the next start: nobody is let in to be left there
        }
        ServerLevel target = IfLevel.open(server);
        if (target == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.throw"));
            return null;
        }
        BlockPos there = IfMirrors.standing(target);
        if (there == null) {
            // the If's first mirror is the trip's to set down; one taken up since is set down again from the traveller's pocket
            ItemStack carried = IfWorld.get(server).ifMirrorSetDown() && !player.hasInfiniteMaterials()
                    ? DaemonTravel.carried(player, ModTravel.IF_MIRROR_ITEM.get()) : null;
            if (carried != null && carried.isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.taken_up"));
                return null;
            }
            BlockPos base = TravelLog.of(player).lastPos().get(IfWorld.KEY);
            boolean firstTime = base == null;
            if (firstTime) {
                double scale = DimensionType.getTeleportationScale(from.dimensionType(), target.dimensionType());
                BlockPos same = BlockPos.containing(mirror.getX() * scale, 0, mirror.getZ() * scale);
                target.getChunk(same); // its heights are read below
                base = same.atY(target.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, same.getX(), same.getZ()));
            }
            there = DaemonTravel.setDown(target, base, firstTime, IfTravel::place, player);
            if (there == null) {
                player.sendOverlayMessage(Component.translatable(DaemonTravel.lastRefused() ? "message.nookworks.if_mirror.claimed"
                        : "message.nookworks.if_mirror.no_room"));
                return null;
            }
            if (carried != null) {
                carried.shrink(1);
            }
            IfMirrors.placed(target, there);
        }
        TeleportTransition arrival = arriveAt(target, there);
        if (arrival == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.blocked"));
        }
        return arrival;
    }

    /** From the If, home: to the home mirror, or the respawn point. */
    private static TeleportTransition home(ServerPlayer player, MinecraftServer server) {
        GlobalPos home = IfWorld.get(server).homeMirror();
        ServerLevel level = home == null ? null : server.getLevel(home.dimension());
        BlockPos there = level == null ? null : IfMirrors.standing(level);
        if (level == null || there == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.no_home"));
            return respawn(player);
        }
        TeleportTransition arrival = arriveAt(level, there);
        if (arrival == null) {
            player.sendOverlayMessage(Component.translatable("message.nookworks.if_mirror.blocked_home"));
            return respawn(player); // walled in at home: out of the If all the same
        }
        return arrival;
    }

    /** The player's respawn point - or the world's spawn when that is in the If (a bed there): home is out of it. */
    private static TeleportTransition respawn(ServerPlayer player) {
        TeleportTransition respawn = player.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
        return respawn.newLevel().dimension().equals(IfWorld.KEY) ? TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING) : respawn;
    }

    /** {@code player} to their respawn point (see {@link #respawn}), as one logging back into an If reset since is. */
    static void toRespawn(ServerPlayer player) {
        TeleportTransition transition = respawn(player);
        IfGates.passing(() -> player.teleport(transition));
    }

    /**
     * In front of the mirror at {@code mirror}, facing away from the glass - or behind it, or the nearest room near it when
     * something has been built round it since; null with none.
     */
    private static @Nullable TeleportTransition arriveAt(ServerLevel level, BlockPos mirror) {
        Direction out = level.getBlockState(mirror).getValue(IfMirrorBlock.FACING);
        BlockPos stand = DaemonTravel.standingRoom(level, mirror, out);
        if (stand == null) {
            return null;
        }
        Direction facing = stand.equals(mirror.relative(out.getOpposite())) ? out.getOpposite() : out;
        return new TeleportTransition(level, Vec3.atBottomCenterOf(stand), Vec3.ZERO, facing.toYRot(), 0.0F, TeleportTransition.DO_NOTHING);
    }

    /** The If's mirror, its glass towards {@code out}. */
    private static void place(ServerLevel level, BlockPos pos, Direction out) {
        BlockState lower = ModTravel.IF_MIRROR.get().defaultBlockState().setValue(IfMirrorBlock.FACING, out)
                .setValue(IfMirrorBlock.HALF, DoubleBlockHalf.LOWER);
        DaemonTravel.setTwoHigh(level, pos, lower, lower.setValue(IfMirrorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** For a test or the home command: {@code player} home from the If now, past the pause. */
    public static void sendHome(ServerPlayer player) {
        LAST.remove(player);
        TeleportTransition transition = home(player, player.level().getServer());
        IfGates.passing(() -> player.teleport(transition));
        DaemonTravel.arrived(player);
    }
}
