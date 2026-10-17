package dev.nookworks.poster;

import org.jspecify.annotations.Nullable;

import dev.nookworks.feature.StructureTurns;
import dev.nookworks.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A poster stuck flat on a wall. It hangs like a painting but is a sheet of paper, not a framed board: it is
 * {@link #DEPTH} thick instead of a painting's 1/16, and only the printed face is drawn.
 * <p>
 * The design is synced as its registry id rather than as a holder, so a poster added by a datapack needs no
 * custom network serializer; an id the client cannot resolve simply renders nothing.
 */
public class PosterEntity extends HangingEntity {
    /** A sheet of paper: one quarter of a painting's thickness. */
    public static final float DEPTH = 0.015625F;

    private static final EntityDataAccessor<String> DATA_VARIANT =
            SynchedEntityData.defineId(PosterEntity.class, EntityDataSerializers.STRING);
    /** How a structure setting it down has turned it, for the mirror it tells next. */
    private Rotation turned = Rotation.NONE;
    /**
     * Being set down by a structure: turned (and mirrored) by it, so the place it is set down at next is where its middle
     * goes ({@link #snapTo}). Not saved: it lasts from the turn to the setting down.
     */
    private boolean byStructure;

    public PosterEntity(EntityType<? extends PosterEntity> type, Level level) {
        super(type, level);
    }

    public PosterEntity(Level level, BlockPos pos, Direction direction, Identifier variant) {
        super(ModEntities.POSTER.get(), level, pos);
        this.setVariantId(variant);
        this.setDirection(direction);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_VARIANT, "");
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_VARIANT.equals(accessor)) {
            this.recalculateBoundingBox();
        }
    }

    public void setVariantId(Identifier variant) {
        this.entityData.set(DATA_VARIANT, variant.toString());
    }

    public @Nullable Identifier variantId() {
        Identifier id = Identifier.tryParse(this.entityData.get(DATA_VARIANT));
        return id == null ? null : Posters.current(id);
    }

    public @Nullable PosterVariant variant() {
        return Posters.get(this.level(), this.variantId());
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos pos, Direction direction) {
        PosterVariant variant = this.variant();
        int width = variant == null ? 1 : variant.width();
        int height = variant == null ? 1 : variant.height();
        // Sit against the wall the way a painting does, then shrink to paper thickness.
        Vec3 onWall = Vec3.atCenterOf(pos).relative(direction, -0.5 + DEPTH / 2.0);
        Vec3 position = onWall.relative(direction.getCounterClockWise(), even(width)).relative(Direction.UP, even(height));
        boolean acrossX = direction.getAxis() == Direction.Axis.X;
        return AABB.ofSize(position, acrossX ? DEPTH : width, height, acrossX ? width : DEPTH);
    }

    /** An even span straddles the block boundary, an odd one is centred, exactly as paintings do it. */
    private static double even(int size) {
        return size % 2 == 0 ? 0.5 : 0.0;
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            this.playSound(SoundEvents.ITEM_FRAME_BREAK, 1.0F, 1.2F);
            Identifier variant = this.variantId();
            if (variant != null && !(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
                this.spawnAtLocation(level, Posters.item(variant));
            }
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.ITEM_FRAME_PLACE, 1.0F, 1.2F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("facing", Direction.LEGACY_ID_CODEC_2D, this.getDirection());
        super.addAdditionalSaveData(output);
        output.putString("variant", this.entityData.get(DATA_VARIANT));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Direction direction = input.read("facing", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_VARIANT, input.getStringOr("variant", ""));
        this.setDirection(direction);
    }

    /**
     * Set down by a structure (a structure block's copy, another mod's template): on the block that puts its middle where
     * the build put it - not the block its middle is in, the next one along for a design an even number of blocks across
     * or high ({@link StructureTurns#attachedAt}). Anywhere else, on the block at the place given.
     */
    @Override
    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        if (this.byStructure) {
            this.byStructure = false;
            this.setPos(StructureTurns.attachedAt(new Vec3(x, y, z), this.calculateBoundingBox(BlockPos.ZERO, this.getDirection())));
        } else {
            // anything else - a client making it from the packet that brings it (a block's corner, sent before its design
            // and which way it faces), a summon at a block - names the block it goes on, as before
            this.setPos(x, y, z);
        }
    }

    /** A structure turns it, then mirrors it, then sets it down ({@code StructureTemplate#placeEntities}). */
    @Override
    public float rotate(Rotation rotation) {
        this.turned = rotation;
        this.byStructure = true;
        return super.rotate(rotation);
    }

    /** Mirrored by a structure: the mirror a template does before it turns the build, done after the turn it told first. */
    @Override
    public float mirror(Mirror mirror) {
        this.byStructure = true;
        return super.mirror(StructureTurns.afterTurn(mirror, this.turned));
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, this.getDirection().get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        Identifier variant = this.variantId();
        return variant == null ? ItemStack.EMPTY : Posters.item(variant);
    }
}
