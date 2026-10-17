package dev.nookworks.feature;

import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import dev.nookworks.Nookworks;
import dev.nookworks.NookworksConfig;
import dev.nookworks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Elevator blocks: stand on one and jump to go to the next elevator straight above, sneak to go to the
 * next one below. Destinations need two free blocks of headroom; blocked elevators are skipped.
 * Jumps are detected mid movement-packet, so the move is queued and done at the end of the player tick.
 */
@EventBusSubscriber(modid = Nookworks.MODID)
public final class Elevators {
    /** Elevator the player jumped from; by the end of the tick they are already rising off it. */
    private static final Map<ServerPlayer, BlockPos> PENDING_UP = new WeakHashMap<>();
    private static final Map<ServerPlayer, Boolean> WAS_SNEAKING = new WeakHashMap<>();

    @SubscribeEvent
    static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlockPos elevator = standingOn(player);
            if (elevator != null) {
                PENDING_UP.put(player, elevator);
            }
        }
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockPos jumpedFrom = PENDING_UP.remove(player);
        if (jumpedFrom != null) {
            moveFrom(player, jumpedFrom, true);
        }
        boolean sneaking = player.isShiftKeyDown();
        Boolean was = WAS_SNEAKING.put(player, sneaking);
        if (sneaking && !Boolean.TRUE.equals(was)) {
            tryMove(player, false);
        }
    }

    /**
     * The elevator under the player's feet, or null. Standing on it, that is: a player flying or gliding over one, or a
     * spectator passing through, goes nowhere (sneaking is how a flying player comes down).
     */
    static @Nullable BlockPos standingOn(ServerPlayer player) {
        if (player.isSpectator() || player.getAbilities().flying || player.isFallFlying()) {
            return null;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.5, player.getZ());
        return player.level().getBlockState(below).is(ModBlocks.ELEVATOR.get()) ? below : null;
    }

    /** Moves the player to the next usable elevator up or down. Returns whether it moved. */
    public static boolean tryMove(ServerPlayer player, boolean up) {
        BlockPos from = standingOn(player);
        return from != null && moveFrom(player, from, up);
    }

    private static boolean moveFrom(ServerPlayer player, BlockPos from, boolean up) {
        if (!player.level().getBlockState(from).is(ModBlocks.ELEVATOR.get()) || player.isPassenger()) {
            return false; // riding something, the mount would be left behind (or dragged into the floor)
        }
        BlockPos to = findDestination(player.level(), from, up, NookworksConfig.ELEVATOR_RANGE.getAsInt());
        if (to == null) {
            return false;
        }
        Vec3 target = arrival(player, to);
        if (!player.level().noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(target))) {
            return false;
        }
        // asked as any teleport is: a claim mod that keeps teleports out of its land keeps this one out too
        if (NeoForge.EVENT_BUS.post(new EntityTeleportEvent(player,
                (ServerLevel) player.level(), target.x, target.y, target.z)).isCanceled()) {
            return false;
        }
        player.connection.teleport(new PositionMoveRotation(target, Vec3.ZERO, 0.0F, 0.0F), Relative.ROTATION);
        player.resetFallDistance();
        player.level().playSound(null, to.above(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.4F, 1.4F);
        return true;
    }

    /**
     * Where a player riding up or down to the elevator at {@code to} arrives: the same offset within the block, but no
     * nearer its edge than half a player's width - stood at the edge below, the body would otherwise arrive inside the
     * wall beside the shaft above.
     */
    public static Vec3 arrival(Player player, BlockPos to) {
        double half = player.getBbWidth() / 2;
        return new Vec3(Mth.clamp(player.getX(), to.getX() + half, to.getX() + 1 - half), to.getY() + 1.0 + cover(player.level(), to),
                Mth.clamp(player.getZ(), to.getZ() + half, to.getZ() + 1 - half));
    }

    /** As high as a carpet or two layers of snow may lie on an elevator and still be stood on over it. */
    private static final double THIN = 2 / 16.0 + 1e-6;

    /** How high the carpet or snow over the elevator at {@code elevator} stands: one arrives on top of it. */
    private static double cover(BlockGetter level, BlockPos elevator) {
        VoxelShape shape = level.getBlockState(elevator.above()).getCollisionShape(level, elevator.above());
        return shape.isEmpty() ? 0 : shape.max(Direction.Axis.Y);
    }

    public static @Nullable BlockPos findDestination(ServerLevel level, BlockPos from, boolean up, int range) {
        int step = up ? 1 : -1;
        for (int d = 1; d <= range; d++) {
            BlockPos pos = from.above(d * step);
            if (level.isOutsideBuildHeight(pos)) {
                return null;
            }
            if (level.getBlockState(pos).is(ModBlocks.ELEVATOR.get()) && hasHeadroom(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Room to stand over the elevator, and nothing there that would hurt or carry off whoever arrives. A carpet or a
     * little snow laid on it is no obstacle (the user, 2026-10-01): one arrives standing on it.
     */
    private static boolean hasHeadroom(ServerLevel level, BlockPos elevator) {
        for (int i = 1; i <= 2; i++) {
            BlockPos p = elevator.above(i);
            BlockState state = level.getBlockState(p);
            VoxelShape shape = state.getCollisionShape(level, p);
            boolean thin = i == 1 && !shape.isEmpty() && shape.max(Direction.Axis.Y) <= THIN;
            if (!shape.isEmpty() && !thin || harmful(state)) {
                return false;
            }
        }
        return true;
    }

    /** Things with no collision box that no one should be put into: fire, lava, portals, thorns. */
    public static boolean harmful(BlockState state) {
        return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.PORTALS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.COBWEB);
    }

    private Elevators() {}
}
