package dev.nookworks.pearl;

import java.util.Locale;
import java.util.Optional;

import dev.nookworks.feature.Elevators;
import dev.nookworks.ifworld.IfGates;
import dev.nookworks.ifworld.IfWorld;
import dev.nookworks.registry.ModEntities;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;

/**
 * A thrown {@link LodePearlItem}. Where it lands does not matter: its thrower goes to the pearl's lodestone. Falling out
 * of the bottom of the world counts as landing, so one thrown into the void still brings you home. If its thrower
 * cannot go just then - asleep, dead, leaving the game - it comes back as it was, still bound: into their hands if they
 * can take it, else where it landed (an ender pearl would be lost; this one is worth more). If they have left the game
 * already, it drops where it landed, still bound (vanilla keeps a flying ender pearl with whoever leaves, which this one
 * cannot be). If it cannot take them - its
 * lodestone gone, no room to stand by it, the trip refused - nobody moves, the thrower is told, and the pearl comes back
 * into their hands: still bound when it was no fault of the pearl's (refused, no room), a plain ender pearl when its
 * lodestone is gone.
 * <p>
 * As an ender pearl does, it keeps the ground round it loaded while it flies, so it lands even when its thrower has gone
 * far off meanwhile - through the If mirror, a door, a portal (the user, 2026-10-10: pearls went missing). It stopped in
 * mid-air out there, and took them home whenever they next came by.
 */
public class ThrownLodePearl extends ThrowableItemProjectile {
    /** Ticks until its ticket wants renewing (not saved: a pearl loaded with its chunk takes one on its first tick). */
    private long ticketTimer;

    public ThrownLodePearl(EntityType<? extends ThrownLodePearl> type, Level level) {
        super(type, level);
    }

    public ThrownLodePearl(Level level, LivingEntity owner, ItemStack pearl) {
        super(ModEntities.LODE_PEARL.get(), owner, level, pearl);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.LODE_PEARL.get();
    }

    @Override
    public void tick() {
        if (!(this.level() instanceof ServerLevel level)) {
            super.tick();
            return;
        }
        int chunkX = SectionPos.blockToSectionCoord(this.getX());
        int chunkZ = SectionPos.blockToSectionCoord(this.getZ());
        super.tick();
        // vanilla's ender pearl ticket (two chunks round it, lasting a few seconds), renewed as it goes and as it crosses
        // into another chunk, while a player it may take home threw it
        if (this.isAlive() && (--this.ticketTimer <= 0 || chunkX != SectionPos.blockToSectionCoord(this.getX())
                || chunkZ != SectionPos.blockToSectionCoord(this.getZ())) && this.getOwner() instanceof ServerPlayer) {
            level.resetEmptyTime(); // a dimension its thrower has left goes on ticking it
            this.ticketTimer = ServerPlayer.placeEnderPearlTicket(level, this.chunkPosition()) - 1L;
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit) {
        super.onHitEntity(hit);
        hit.getEntity().hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
    }

    @Override
    protected void onHit(HitResult hit) {
        super.onHit(hit);
        for (int i = 0; i < 32; i++) {
            this.level().addParticle(ParticleTypes.PORTAL, this.getX(), this.getY() + this.random.nextDouble() * 2.0, this.getZ(),
                    this.random.nextGaussian(), 0.0, this.random.nextGaussian());
        }
        if (this.level() instanceof ServerLevel && !this.isRemoved()) {
            this.arrive();
        }
    }

    /** Gone out of the bottom of the world: that is where it landed. */
    @Override
    protected void onBelowWorld() {
        if (this.level() instanceof ServerLevel && !this.isRemoved()) {
            this.arrive();
        }
        super.onBelowWorld();
    }

    private void arrive() {
        if (this.getOwner() instanceof ServerPlayer player && player.isAlive() && !player.isSleeping()
                && player.connection.isAcceptingMessages()) {
            Trip trip = send(player, this.getItem());
            if (trip != Trip.HOME) {
                player.sendOverlayMessage(Component.translatable(trip == Trip.IF_CLOSED ? "message.nookworks.if.reset_pending"
                        : "item.nookworks.lode_pearl." + trip.name().toLowerCase(Locale.ROOT)));
                // Its lodestone gone, or never bound: the binding is spent, and a plain ender pearl comes back. Refused,
                // no room at the lodestone, or the If closed until it is made again: through no fault of the pearl's, so
                // it comes back as it was, still bound. Into the thrower's hands, not where it landed - in lava, or in
                // the void, it was lost.
                boolean keeps = trip == Trip.REFUSED || trip == Trip.NO_ROOM || trip == Trip.IF_CLOSED;
                ItemStack back = keeps ? this.getItem().copyWithCount(1) : new ItemStack(Items.ENDER_PEARL);
                if (!player.getInventory().add(back)) {
                    // no room for it: at their feet, still - a drop is thrown ahead of them, over the edge they stood at
                    ItemEntity left = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), back, 0.0, 0.0, 0.0);
                    left.setNoPickUpDelay();
                    player.level().addFreshEntity(left);
                }
            }
        } else if (this.getOwner() instanceof ServerPlayer player && this.level() instanceof ServerLevel level) {
            // its thrower could not go just now (asleep, dead, leaving the game): no trip, and the pearl back as it was
            ItemStack back = this.getItem().copyWithCount(1);
            if (!player.isAlive() || !player.connection.isAcceptingMessages() || !player.getInventory().add(back)) {
                this.spawnAtLocation(level, back);
            }
        } else if (this.owner != null && this.getOwner() == null && this.level() instanceof ServerLevel level) {
            // its thrower left the game while it flew (someone else keeping the chunk going): it lies where it landed,
            // still bound, not lost with no trip made
            this.spawnAtLocation(level, this.getItem().copyWithCount(1));
        }
        this.discard();
    }

    /** How a trip to a pearl's lodestone went. */
    public enum Trip {
        HOME,
        /** Not bound to anything. */
        UNBOUND,
        /** The lodestone is not there any more. */
        NO_LODESTONE,
        /** Blocks all round the lodestone: nowhere to stand. */
        NO_ROOM,
        /** Another mod forbade it (a dimension change can be cancelled). */
        REFUSED,
        /** Into an If that goes at the next start (an operator's reset): nobody is let in to be left there. */
        IF_CLOSED
    }

    /**
     * Sends {@code player} to the lodestone {@code pearl} is bound to, and sets them down on it or beside it - into or out of
     * the If too, as its mirror would (the user, 2026-10-10: the pearl was kept out of the If, 「別に使えてもよくない？」).
     */
    public static Trip send(ServerPlayer player, ItemStack pearl) {
        Optional<GlobalPos> target = LodePearlItem.lodestone(pearl);
        if (target.isEmpty()) {
            return Trip.UNBOUND;
        }
        ServerLevel there = player.level().getServer().getLevel(target.get().dimension());
        BlockPos lodestone = target.get().pos();
        if (there == null || !there.isInWorldBounds(lodestone) || !there.getBlockState(lodestone).is(Blocks.LODESTONE)) {
            if (there != null) {
                LodestoneNames.forget(there, lodestone); // gone some way a player's breaking did not see
            }
            return Trip.NO_LODESTONE;
        }
        Optional<Vec3> spot = standingSpot(there, player, lodestone);
        if (spot.isEmpty()) {
            return Trip.NO_ROOM;
        }
        BlockPos from = player.blockPosition();
        ServerLevel fromLevel = player.level();
        if (there.dimension().equals(IfWorld.KEY) && !fromLevel.dimension().equals(IfWorld.KEY)
                && IfWorld.get(player.level().getServer()).resetPending()) {
            return Trip.IF_CLOSED;
        }
        // Asked as any teleport is, so a claim mod that keeps ender pearls out keeps this one out too (a trip within the
        // dimension raised no event at all, only one to another dimension could be stopped).
        var asked = new EntityTeleportEvent(player, there, spot.get().x, spot.get().y, spot.get().z);
        if (NeoForge.EVENT_BUS.post(asked).isCanceled()) {
            return Trip.REFUSED;
        }
        // (the teleport takes a rider off their mount, once no mod has refused the trip: a refused one leaves them riding, r15)
        // one of the If's own ways in and out, as the mirror and the door are (IfGates turns every other away)
        ServerPlayer moved = IfGates.passing(() -> player.teleport(new TeleportTransition(there, spot.get(), Vec3.ZERO,
                player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING)));
        if (moved == null) {
            return Trip.REFUSED;
        }
        moved.resetFallDistance();
        moved.resetCurrentImpulseContext();
        fromLevel.playSound(null, from, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        there.playSound(null, lodestone.above(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        there.sendParticles(ParticleTypes.REVERSE_PORTAL, spot.get().x, spot.get().y + 1.0, spot.get().z, 24, 0.3, 0.6, 0.3, 0.05);
        return Trip.HOME;
    }

    /** On top of the lodestone if there is room there, or else beside it as a respawn anchor would set you down. */
    private static Optional<Vec3> standingSpot(ServerLevel level, ServerPlayer player, BlockPos lodestone) {
        Vec3 top = Vec3.atBottomCenterOf(lodestone.above());
        if (level.noCollision(player, player.getDimensions(Pose.STANDING).makeBoundingBox(top))
                && !Elevators.harmful(level.getBlockState(lodestone.above()))
                && !Elevators.harmful(level.getBlockState(lodestone.above(2)))) {
            return Optional.of(top);
        }
        return RespawnAnchorBlock.findStandUpPosition(EntityTypes.PLAYER, level, lodestone);
    }
}
