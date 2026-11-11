package dev.nookworks.rope;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.nookworks.carry.CarriedBlock;
import dev.nookworks.carry.CarriedLook;
import dev.nookworks.registry.ModBlocks;
import dev.nookworks.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;

/**
 * A block tied up with rope: taken out of the world into a {@link CarriedBlock} (its contents, power, liquids kept) and
 * standing where it was as this entity. So a tied chest does not open and no hopper or pipe reaches it - it is not a
 * block while tied. It collides as a block (stood on, walked into, not pushed), is drawn as the block with rope wound
 * round it, and is saved with its value.
 * <p>
 * A Rope hanging with its bottom end right over it holds it up ({@link TiedLoads}): the rope's own clicks wind it up and
 * let it down a block at a time. Held by nothing, it falls until something is under it, and rests there, still tied.
 * Untied (sneak with an empty hand, shears, a dispenser firing shears) it is put back as the block where it stands.
 */
public class TiedLoadEntity extends Entity implements IShearable {
    private static final EntityDataAccessor<CarriedLook> DATA_LOOK = SynchedEntityData.defineId(TiedLoadEntity.class, CarriedLook.ENTITY_SERIALIZER);
    /** Falling: how much faster each tick, and the fastest, in blocks a tick (no faster than a block, so no cell is skipped). */
    private static final double GRAVITY = 0.04;
    private static final double MAX_FALL = 0.98;

    private @Nullable CarriedBlock carried;
    /** How fast it is falling, blocks a tick; 0 at rest or held. */
    private double falling;

    public TiedLoadEntity(EntityType<? extends TiedLoadEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true; // nothing is set down where it stands, as where a block stands
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_LOOK, CarriedLook.EMPTY);
    }

    /** What it holds (server side; null on a client, which has only {@link #look()}). */
    public @Nullable CarriedBlock carried() {
        return this.carried;
    }

    /** What clients draw: the block's state and its block entity's update tag. */
    public CarriedLook look() {
        return this.entityData.get(DATA_LOOK);
    }

    void setCarried(CarriedBlock carried) {
        this.carried = carried;
        this.entityData.set(DATA_LOOK, carried.look(this.level().registryAccess()));
        this.setBoundingBox(this.makeBoundingBox());
    }

    /** Taken out of it: what it held, the entity left with nothing (it goes on its next tick unless given more). */
    @Nullable CarriedBlock takeCarried() {
        CarriedBlock was = this.carried;
        this.carried = null;
        return was;
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_LOOK.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    /** A block's cube round each part, its first part's bottom middle at {@code position}. */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        AABB box = new AABB(position.x - 0.5, position.y, position.z - 0.5, position.x + 0.5, position.y + 1, position.z + 0.5);
        CarriedLook look = this.entityData == null ? CarriedLook.EMPTY : this.look();
        for (CarriedLook.LookPart part : look.parts()) {
            BlockPos o = part.offset();
            box = box.minmax(new AABB(position.x - 0.5 + o.getX(), position.y + o.getY(), position.z - 0.5 + o.getZ(),
                    position.x + 0.5 + o.getX(), position.y + 1 + o.getY(), position.z + 0.5 + o.getZ()));
        }
        return box;
    }

    /** The cell of its first part: where it is put back. */
    public BlockPos base() {
        return BlockPos.containing(this.getX(), this.getY() + 0.5, this.getZ());
    }

    /** The cells it stands in, the first part's first. */
    public List<BlockPos> cells() {
        BlockPos base = this.base();
        List<CarriedLook.LookPart> parts = this.look().parts();
        if (parts.isEmpty()) {
            return List.of(base);
        }
        List<BlockPos> cells = new ArrayList<>(parts.size());
        for (CarriedLook.LookPart part : parts) {
            cells.add(base.offset(part.offset()));
        }
        return cells;
    }

    /** Standing on whole blocks, as it does but while it falls. */
    public boolean aligned() {
        return Math.abs(this.getY() - Math.rint(this.getY())) < 1.0E-6;
    }

    /** Whether a Rope's bottom end hangs right over one of its parts, holding it up. */
    public boolean held() {
        if (!this.aligned()) {
            return false;
        }
        List<BlockPos> cells = this.cells();
        for (BlockPos cell : cells) {
            BlockPos over = cell.above();
            if (!cells.contains(over) && this.level().getBlockState(over).is(ModBlocks.ROPE.get())) {
                return true;
            }
        }
        return false;
    }

    /** Moved up (or down) {@code rows} whole blocks at once - the client draws it moving there - and not falling. */
    void moveRows(int rows) {
        this.falling = 0;
        this.setPos(this.getX(), Math.rint(this.getY()) + rows, this.getZ());
        this.needsSync = true;
    }

    @Override
    public void tick() {
        // Not Entity's own round of water, lava, fire and portals: it stays where it is put, as a block does.
        this.checkBelowWorld();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.carried == null) {
            this.discard(); // taken out of it (TiedLoads.release), or never given anything
            return;
        }
        if (this.held()) {
            this.falling = 0;
            return;
        }
        this.fall(level);
    }

    /**
     * What hangs where it falls to (a bottle on a rope strung under it) is knocked down, as by a hit: nothing hangs inside
     * a load, as nothing is hung into one (HungItemEntity#misfit) nor is one let down into what hangs (TiedLoads#canMove).
     */
    private void knockDownWhatHangs(ServerLevel level) {
        for (HungItemEntity hung : level.getEntitiesOfClass(HungItemEntity.class, this.getBoundingBox().deflate(1.0E-3),
                hung -> !hung.isRemoved())) {
            hung.hurtServer(level, level.damageSources().fallingBlock(this), 1.0F);
        }
    }

    /** Held by nothing: down until something is under it, a block's cell at a time. */
    private void fall(ServerLevel level) {
        double speed = Math.min(this.falling + GRAVITY, MAX_FALL);
        double to = this.getY() - speed;
        int row = Mth.floor(to + 1.0E-7); // the row its bottom has come into
        if (row == Mth.floor(this.getY() + 1.0E-7) || TiedLoads.freeAt(level, this, row)) {
            this.falling = speed;
            this.setPos(this.getX(), to, this.getZ());
            this.needsSync = true;
            this.knockDownWhatHangs(level);
            return;
        }
        boolean wasFalling = this.falling > GRAVITY;
        this.falling = 0;
        if (!this.aligned()) {
            this.setPos(this.getX(), row + 1, this.getZ()); // landed: on whole blocks again
            this.needsSync = true;
        }
        if (wasFalling) {
            BlockState state = this.carried == null ? null : this.carried.state();
            if (state != null) {
                level.playSound(null, this.getX(), this.getY(), this.getZ(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            }
        }
    }

    /**
     * Killed (a /kill, a mod clearing entities), it breaks open as the block would be broken, rather than taking what it
     * holds with it: as a chest minecart killed drops its chest and contents ({@link TiedLoads#breakOpen}).
     */
    @Override
    public void remove(RemovalReason reason) {
        if (reason == RemovalReason.KILLED && this.level() instanceof ServerLevel level && this.carried != null) {
            TiedLoads.breakOpen(level, this, this.takeCarried());
        }
        super.remove(reason);
    }

    @Override
    public boolean canBeCollidedWith(@Nullable Entity other) {
        return true; // stood on and walked into, as a block
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false; // a hit does nothing: untie it to have the block again
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE_ENTITY; // a piston moving by leaves it where it stands
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
        return TiedLoads.click(this, player, hand);
    }

    /** "Chest (tied)": the block's own name, so Jade and the like name it so. */
    @Override
    protected Component getTypeName() {
        CarriedLook look = this.look();
        return look.isEmpty() ? super.getTypeName()
                : Component.translatable("entity.nookworks.tied_load.of", look.state().getBlock().getName());
    }

    /** The block it is (pick-block). */
    @Override
    public @Nullable ItemStack getPickResult() {
        ItemStack stack = new ItemStack(this.look().state().getBlock());
        return stack.isEmpty() ? null : stack;
    }

    // a dispenser firing shears at it unties it, the rope dropped (the dispenser wears the shears)

    @Override
    public boolean isShearable(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
        return level instanceof ServerLevel && this.carried != null && !this.isRemoved() && TiedLoads.canUntie(this, player);
    }

    @Override
    public List<ItemStack> onSheared(@Nullable Player player, ItemStack item, Level level, BlockPos pos) {
        if (level instanceof ServerLevel && TiedLoads.untie(this, player).placed()) {
            this.playSound(net.minecraft.sounds.SoundEvents.SHEARS_SNIP, 1.0F, 1.0F);
            return List.of(new ItemStack(ModItems.ROPE.get()));
        }
        return List.of();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        if (this.carried != null) {
            output.store("carried", CarriedBlock.CODEC, this.carried);
        }
        if (this.falling > 0) {
            output.putDouble("falling", this.falling);
        }
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        input.read("carried", CarriedBlock.CODEC).ifPresent(this::setCarried);
        this.falling = input.getDoubleOr("falling", 0);
    }
}
