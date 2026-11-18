package dev.nookworks.furniture;

import org.jspecify.annotations.Nullable;

import dev.nookworks.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.nbt.CompoundTag;
import dev.nookworks.port.storage.ValueIO;

/**
 * The invisible thing a player actually rides when they sit on a chair.
 * <p>
 * It exists only while somebody is sitting: it is spawned by {@link ChairBlock} on use and discards itself the
 * moment its rider gets off or the chair under it is gone, so a world cannot silt up with abandoned seats. It
 * has no hitbox to speak of and cannot be hurt, pushed or targeted - the chair is what you interact with.
 */
public class SeatEntity extends Entity {
    /** How high above the block's floor the rider sits; the chair's seat plate is at 9/16. */
    public static final double SEAT_HEIGHT = 0.42;
    /**
     * The lowest the seat goes to keep a rider's head under a low ceiling: a top slab over the chair has its underside
     * 1.5 above the floor, and sat at the usual height a player's head would reach 1.62, into the slab. A rider's feet
     * are 0.6 under the seat and their head 1.8 over their feet, so under the slab they sit at 0.28 (a hair's room left).
     */
    public static final double LOW_SEAT = 0.25;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public SeatEntity(Level level, BlockPos pos, double height) {
        this(ModEntities.SEAT.get(), level);
        this.setPos(pos.getX() + 0.5, pos.getY() + height, pos.getZ() + 0.5);
    }

    /**
     * How high {@code player} sits on the chair at {@code pos}: its usual height (a stool's is lower), or a little lower so their head stays
     * under whatever is overhead; -1 when even the lowest seat would put it in it. A rider sits with their feet as far
     * under the seat as their vehicle attachment says (0.6 for a player, as the game places a rider), so the top of
     * their head is the seat, less that, plus their height - not the seat plus their standing eye height, which put
     * them 0.6 higher than they sit and turned them away from a chair under a top slab.
     */
    public static double seatHeight(Level level, BlockPos pos, Player player) {
        EntityDimensions body = player.getDimensions(Pose.STANDING);
        double head = body.height() - body.attachments().get(EntityAttachment.VEHICLE, 0, 0).y;
        double ceiling = DismountHelper.findCeilingFrom(pos.above(), 3, p -> level.getBlockState(p).getCollisionShape(level, p));
        double top = ceiling - pos.getY() - head - 0.02; // the head's box clear of the ceiling's
        double usual = level.getBlockState(pos).getBlock() instanceof ChairBlock chair ? chair.seatHeight() : SEAT_HEIGHT;
        return top >= usual ? usual : top >= LOW_SEAT ? top : -1;
    }

    /** Sits the player on the chair at {@code pos}, unless somebody already has it. */
    public static boolean sit(Level level, BlockPos pos, Player player) {
        var box = new AABB(pos).inflate(0.1);
        for (SeatEntity taken : level.getEntitiesOfClass(SeatEntity.class, box)) {
            if (taken.isVehicle()) {
                return false;
            }
            taken.discard(); // a leftover from a crash or an unloaded chunk; reuse the spot
        }
        double height = seatHeight(level, pos, player);
        if (height < 0) {
            player.displayClientMessage(Component.translatable("message.nookworks.chair.too_low"), true);
            return false;
        }
        SeatEntity seat = new SeatEntity(level, pos, height);
        level.addFreshEntity(seat);
        boolean sat = player.startRiding(seat);
        if (sat) { // heard sitting down (the sound session's survey, 2026-11-10: our seats made no sound)
            level.playSound(null, pos, dev.nookworks.registry.ModSounds.SEAT_SIT.get(), net.minecraft.sounds.SoundSource.BLOCKS,
                    1.0F, 0.95F + level.getRandom().nextFloat() * 0.1F);
        }
        return sat;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        // Stop existing the moment there is nothing to sit on or nobody sitting. Checking both ways round
        // matters: the rider can leave by dismounting, by dying, or by the chair being broken underneath them.
        if (!this.isVehicle() || !(this.level().getBlockState(this.blockPosition()).getBlock() instanceof ChairBlock)
                || this.sharesItsChair()) {
            this.ejectPassengers();
            this.discard();
        }
    }

    /**
     * Whether another seat with a rider stands on this one's chair and keeps it. A player who logs out sitting comes
     * back in their seat - vanilla saves it with them - though someone else may have sat there meanwhile (r7
     * multiplayer test: two players in one chair). The seat sat in longer keeps the chair; the other's rider gets up.
     */
    private boolean sharesItsChair() {
        for (SeatEntity other : this.level().getEntitiesOfClass(SeatEntity.class, new AABB(this.blockPosition()))) {
            if (other != this && !other.isRemoved() && other.isVehicle() && (other.tickCount > this.tickCount
                    || other.tickCount == this.tickCount && other.getUUID().compareTo(this.getUUID()) < 0)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return Vec3.ZERO;
    }

    /**
     * Getting up: onto the floor in front of the chair if there is room to stand, else beside or behind it, and only
     * then straight up off the seat. Standing up in place under a low ceiling would leave the player's head in it.
     */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        BlockPos chair = this.blockPosition();
        BlockState state = this.level().getBlockState(chair);
        Direction front = state.hasProperty(ChairBlock.FACING) ? state.getValue(ChairBlock.FACING) : Direction.NORTH;
        for (Direction side : new Direction[] {front, front.getClockWise(), front.getCounterClockWise(), front.getOpposite()}) {
            BlockPos spot = chair.relative(side);
            double floor = this.level().getBlockFloorHeight(spot);
            if (!DismountHelper.isBlockFloorValid(floor)) {
                continue;
            }
            Vec3 at = Vec3.upFromBottomCenterOf(spot, floor);
            for (Pose pose : passenger.getDismountPoses()) {
                if (DismountHelper.canDismountTo(this.level(), passenger, passenger.getLocalBoundsForPose(pose).move(at))) {
                    passenger.setPose(pose);
                    return at;
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.hurt(source, damage); // PORT: 26.3's client side is the parent's hurtClient
        }
        return false;
    }

    @Override
    public boolean isInvisible() {
        return true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void readAdditionalSaveData(CompoundTag portTag) {
        ValueInput input = ValueIO.input(this, portTag, this.registryAccess()); // PORT: 1.21.1 hands a tag
    }

    @Override
    public void addAdditionalSaveData(CompoundTag portTag) {
        ValueOutput output = ValueIO.output(this, portTag, this.registryAccess()); // PORT: 1.21.1 hands a tag
    }
}
