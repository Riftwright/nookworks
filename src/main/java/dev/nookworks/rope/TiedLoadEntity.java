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
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
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
import dev.nookworks.port.storage.ValueInput;
import dev.nookworks.port.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.minecraft.nbt.CompoundTag;
import dev.nookworks.port.storage.ValueIO;

/**
 * A block tied up with rope: taken out of the world into a {@link CarriedBlock} (its contents, power, liquids kept) and
 * standing where it was as this entity. So a tied chest does not open and no hopper or pipe reaches it - it is not a
 * block while tied. It collides as a block (stood on, walked into, not pushed), is drawn as the block with rope wound
 * round it, and is saved with its value.
 * <p>
 * A Rope hanging with its bottom end right over it holds it up ({@link TiedLoads}): the rope's own clicks wind it up and
 * let it down a block at a time. Tied, it rests where it stands, as the block did, in the air or not; once a rope has
 * moved it, held by nothing it falls until something is under it, and rests there, still tied (the user, 2026-11-12:
 * a block tied in the air dropped like gravel). Untied (sneak with an empty hand, shears, a dispenser firing shears) it
 * is put back as the block where it stands. Hit, it breaks as its block would, the harder the more hits; an explosion
 * strong enough for its block breaks it too (it was proof against both, a wither's cage in the making).
 */
public class TiedLoadEntity extends Entity implements IShearable {
    private static final EntityDataAccessor<CarriedLook> DATA_LOOK = SynchedEntityData.defineId(TiedLoadEntity.class, CarriedLook.ENTITY_SERIALIZER);
    /** Falling: how much faster each tick, and the fastest, in blocks a tick (no faster than a block, so no cell is skipped). */
    private static final double GRAVITY = 0.04;
    private static final double MAX_FALL = 0.98;
    /** How far its breaking has come, 0-9 as a block's cracks, or -1 whole: for the client to draw the cracks. */
    private static final EntityDataAccessor<Integer> DATA_CRACK = SynchedEntityData.defineId(TiedLoadEntity.class, EntityDataSerializers.INT);
    /** What one hit takes off it: this many ticks of digging at its block with what the hand holds. */
    static final float HIT_TICKS = 10.0F;
    /** Hits forgotten this long after the last, as digging stopped part way. */
    private static final int HITS_LAST = 40;

    private @Nullable CarriedBlock carried;
    /** How fast it is falling, blocks a tick; 0 at rest or held. */
    private double falling;
    /**
     * At rest where it stands - tied there, or come down onto something - so held by nothing it stays, as a block does;
     * false once a rope has moved it, until it comes to rest again. Null for a load saved before this was kept, until
     * its first tick says.
     */
    private @Nullable Boolean resting = Boolean.TRUE;
    /** How far its breaking has come, 0 to 1, and when it was last hit. */
    private float hits;
    private long lastHit;

    public TiedLoadEntity(EntityType<? extends TiedLoadEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true; // nothing is set down where it stands, as where a block stands
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        entityData.define(DATA_LOOK, CarriedLook.EMPTY);
        entityData.define(DATA_CRACK, -1);
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
    protected AABB makeBoundingBox() {
        Vec3 position = this.position();
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

    /** How far its breaking has come, 0-9, or -1 whole (both sides). */
    public int crack() {
        return this.entityData.get(DATA_CRACK);
    }

    /** A hit taking {@code step} off it (a whole block's digging is 1) at {@code time}: true once that breaks it. */
    boolean hit(float step, long time) {
        this.hits += step;
        this.lastHit = time;
        this.entityData.set(DATA_CRACK, Math.min(9, (int) (this.hits * 10.0F)));
        return this.hits >= 1.0F;
    }

    private void forgetHits() {
        this.hits = 0;
        this.entityData.set(DATA_CRACK, -1);
    }

    /** Whether it is at rest where it stands, so held by nothing it stays put. */
    public boolean resting() {
        return Boolean.TRUE.equals(this.resting);
    }

    /** Moved up (or down) {@code rows} whole blocks at once - the client draws it moving there - and not falling. */
    void moveRows(int rows) {
        this.falling = 0;
        this.resting = Boolean.FALSE; // a rope has it now: let go, it falls
        this.setPos(this.getX(), Math.rint(this.getY()) + rows, this.getZ());
        this.hasImpulse = true; // (1.21.1: hasImpulse is 26.3's needsSync)
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
        if (this.hits > 0 && level.getGameTime() - this.lastHit > HITS_LAST) {
            this.forgetHits();
        }
        if (this.held()) {
            this.falling = 0;
            if (this.resting == null) {
                this.resting = Boolean.FALSE; // saved hanging from a rope
            }
            return;
        }
        if (this.resting == null) {
            this.resting = this.falling == 0; // saved standing, or saved falling
        }
        if (this.resting) {
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
            hung.hurt(level.damageSources().fallingBlock(this), 1.0F);
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
            this.hasImpulse = true; // (1.21.1: hasImpulse is 26.3's needsSync)
            this.knockDownWhatHangs(level);
            return;
        }
        boolean wasFalling = this.falling > GRAVITY;
        this.falling = 0;
        this.resting = Boolean.TRUE;
        if (!this.aligned()) {
            this.setPos(this.getX(), row + 1, this.getZ()); // landed: on whole blocks again
            this.hasImpulse = true; // (1.21.1: hasImpulse is 26.3's needsSync)
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
    public boolean canBeCollidedWith() {
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
        return true; // hit, it breaks as its block would (TiedLoads.hit)
    }

    /** A player's hit (TiedLoads.hit) or an explosion that reached it (one too weak for its block passes it by). */
    @Override
    public boolean hurt(DamageSource source, float damage) {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.hurt(source, damage); // PORT: 26.3's client side is the parent's hurtClient
        }
        if (this.isRemoved() || this.carried == null) {
            return false;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            TiedLoads.blownUp(level, this);
            return true;
        }
        if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player) {
            return TiedLoads.hit(level, this, player);
        }
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return !TiedLoads.blownApartBy(this, explosion);
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE; // a piston moving by leaves it where it stands
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public InteractionResult interactAt(Player player, Vec3 location, InteractionHand hand) {
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
            this.playSound(net.minecraft.sounds.SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F); // PORT: 1.21.1 has no shears snip
            return List.of(new ItemStack(ModItems.ROPE.get()));
        }
        return List.of();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag portTag) {
        ValueOutput output = ValueIO.output(this, portTag, this.registryAccess()); // PORT: 1.21.1 hands a tag
        if (this.carried != null) {
            output.store("carried", CarriedBlock.CODEC, this.carried);
        }
        if (this.falling > 0) {
            output.putDouble("falling", this.falling);
        }
        if (this.resting != null) {
            output.putBoolean("resting", this.resting);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag portTag) {
        ValueInput input = ValueIO.input(this, portTag, this.registryAccess()); // PORT: 1.21.1 hands a tag
        input.read("carried", CarriedBlock.CODEC).ifPresent(this::setCarried);
        this.falling = input.getDoubleOr("falling", 0);
        this.resting = input.read("resting", com.mojang.serialization.Codec.BOOL).orElse(null); // none in a load saved before it was kept
    }
}
