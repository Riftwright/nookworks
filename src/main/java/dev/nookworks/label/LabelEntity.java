package dev.nookworks.label;

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
 * A label stuck on the side of a block: a small printed card (8 px by 6) with the picture of what it marks, or a few
 * letters. It is put
 * where it was pressed on - anywhere on the face, snapped to its pixels - and lies on the block's own surface, so on a
 * chest, whose front stands a pixel back from the edge of its square, it lies on the chest and not in the air before
 * it. It stays while the block does; knocked off or left with nothing behind it, it drops as the label it was.
 * <p>
 * {@code pos} is the block it is stuck to. Where on that block's face it sits is kept as three small numbers: across
 * and up from the face's middle, and how far the surface stands back from the edge of the square, all in pixels.
 */
public class LabelEntity extends HangingEntity {
    public static final float WIDTH = 8 / 16F;
    public static final float HEIGHT = 6 / 16F;
    /** A card of paper: thinner than a poster's sheet on its board. */
    public static final float DEPTH = 0.5F / 16;

    private static final EntityDataAccessor<ItemStack> DATA_SHOWS = SynchedEntityData.defineId(LabelEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<String> DATA_TEXT = SynchedEntityData.defineId(LabelEntity.class, EntityDataSerializers.STRING);
    /** Across (byte 0), up (byte 1), sunk (byte 2), each a signed pixel count. */
    private static final EntityDataAccessor<Integer> DATA_PLACE = SynchedEntityData.defineId(LabelEntity.class, EntityDataSerializers.INT);
    /** How a structure setting it down has turned it, for the mirror it tells next. */
    private Rotation turned = Rotation.NONE;
    /**
     * Being set down by a structure: turned (and mirrored) by it, so the place it is set down at next is where its middle
     * goes ({@link #snapTo}). Not saved: it lasts from the turn to the setting down.
     */
    private boolean byStructure;

    public LabelEntity(EntityType<? extends LabelEntity> type, Level level) {
        super(type, level);
    }

    public LabelEntity(Level level, BlockPos onto, Direction facing, ItemStack shows, int across, int up, int sunk) {
        super(ModEntities.LABEL.get(), level, onto);
        this.entityData.set(DATA_SHOWS, shows.copyWithCount(1));
        this.entityData.set(DATA_PLACE, pack(across, up, sunk));
        this.setDirection(facing);
    }

    private static int pack(int across, int up, int sunk) {
        return (across & 0xFF) | (up & 0xFF) << 8 | (sunk & 0xFF) << 16;
    }

    private int across() {
        return (byte) (int) this.entityData.get(DATA_PLACE);
    }

    /** How far up from the face's middle the label's middle is, in pixels. */
    public int up() {
        return (byte) (this.entityData.get(DATA_PLACE) >> 8);
    }

    private int sunk() {
        return (byte) (this.entityData.get(DATA_PLACE) >> 16);
    }

    /** What the label shows: one of the item it was printed from. */
    public ItemStack shows() {
        return this.entityData.get(DATA_SHOWS);
    }

    /** What a letter label says; "" for a picture label. */
    public String text() {
        return this.entityData.get(DATA_TEXT);
    }

    public void setText(String text) {
        this.entityData.set(DATA_TEXT, LabelText.clean(text));
    }

    /**
     * The label item this came from, and drops as: a plain label if what it showed is gone (its mod taken out of the
     * game), rather than nothing.
     */
    public ItemStack asItem() {
        if (!this.text().isEmpty()) {
            return LabelItem.ofText(this.text());
        }
        ItemStack label = LabelItem.of(this.shows());
        return label.isEmpty() ? new ItemStack(dev.nookworks.registry.ModItems.LABEL.get()) : label;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SHOWS, ItemStack.EMPTY);
        entityData.define(DATA_TEXT, "");
        entityData.define(DATA_PLACE, 0);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_PLACE.equals(accessor) && this.pos != null) {
            this.recalculateBoundingBox();
        }
    }

    /** The direction "across" a face runs, as seen facing it: to the right of someone looking at it. */
    public static Direction acrossOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    protected AABB calculateBoundingBox(BlockPos onto, Direction facing) {
        // the face of the block's square, back to the block's own surface, and the card's thickness out from it
        Vec3 surface = Vec3.atCenterOf(onto).relative(facing, 0.5 - this.sunk() / 16.0 + DEPTH / 2.0);
        Vec3 centre = surface.relative(acrossOf(facing), this.across() / 16.0).relative(Direction.UP, this.up() / 16.0);
        boolean acrossX = facing.getAxis() == Direction.Axis.Z;
        return AABB.ofSize(centre, acrossX ? WIDTH : DEPTH, HEIGHT, acrossX ? DEPTH : WIDTH);
    }

    /** It stays while the face it is stuck to is still behind it, and nothing solid lies where the card does. */
    @Override
    public boolean survives() {
        return this.backed(this.level()) && !this.hasLevelCollision(this.getBoundingBox().deflate(1.0E-4)) && this.canCoexist(false);
    }

    /**
     * Whether the block behind still has a face just behind the card - not only a shape somewhere in its square. A door
     * opened swings its face away from where the card was stuck, and a label on it was left in the air.
     */
    private boolean backed(Level level) {
        BlockPos onto = this.getPos();
        var shape = level.getBlockState(onto).getCollisionShape(level, onto);
        if (shape.isEmpty()) {
            return false;
        }
        AABB card = this.getBoundingBox();
        double t = 0.05; // a sliver behind the card's back
        AABB behind = switch (this.getDirection()) {
            case NORTH -> new AABB(card.minX, card.minY, card.maxZ, card.maxX, card.maxY, card.maxZ + t);
            case SOUTH -> new AABB(card.minX, card.minY, card.minZ - t, card.maxX, card.maxY, card.minZ);
            case WEST -> new AABB(card.maxX, card.minY, card.minZ, card.maxX + t, card.maxY, card.maxZ);
            case EAST -> new AABB(card.minX - t, card.minY, card.minZ, card.minX, card.maxY, card.maxZ);
            case DOWN -> new AABB(card.minX, card.maxY, card.minZ, card.maxX, card.maxY + t, card.maxZ);
            case UP -> new AABB(card.minX, card.minY - t, card.minZ, card.maxX, card.minY, card.maxZ);
        };
        return shape.move(onto).toAabbs().stream().anyMatch(box -> box.intersects(behind));
    }

    /**
     * Comes off within a moment of the block behind it going - broken, moved by a piston - rather than at the next of
     * the hanging entities' checks, which come round only every five seconds and would leave it in the air till then.
     * It looks five times a second, each label on a beat of its own, so a wall of them is not all read in one tick.
     */
    @Override
    public void tick() {
        super.tick();
        if (this.level() instanceof ServerLevel level && !this.isRemoved() && (this.tickCount + this.getId()) % 4 == 0
                && !this.backed(level)) {
            this.discard();
            this.dropItem(level, null);
        }
    }

    @Override
    public void dropItem(ServerLevel level, @Nullable Entity causedBy) {
        if (level.getGameRules().get(GameRules.ENTITY_DROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 0.6F, 1.6F);
            if (!(causedBy instanceof Player player && player.hasInfiniteMaterials())) {
                this.spawnAtLocation(level, this.asItem());
            }
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 0.6F, 1.6F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.store("facing", Direction.LEGACY_ID_CODEC_2D, this.getDirection());
        super.addAdditionalSaveData(output);
        if (!this.shows().isEmpty()) {
            output.store("shows", ItemStack.CODEC, this.shows());
        }
        if (!this.text().isEmpty()) {
            output.putString("text", this.text());
        }
        output.putInt("place", this.entityData.get(DATA_PLACE));
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        Direction direction = input.read("facing", Direction.LEGACY_ID_CODEC_2D).orElse(Direction.SOUTH);
        super.readAdditionalSaveData(input);
        this.entityData.set(DATA_SHOWS, input.read("shows", ItemStack.CODEC).orElse(ItemStack.EMPTY));
        this.setText(input.getStringOr("text", ""));
        this.entityData.set(DATA_PLACE, input.getIntOr("place", 0));
        this.setDirection(direction);
    }

    /**
     * Set down by a structure (a structure block's copy, another mod's template): on the block that puts its middle where
     * the build put it, the copied container - not the block before its face, which held it to nothing
     * ({@link StructureTurns#attachedAt}). Anywhere else, on the block at the place given.
     */
    @Override
    public void snapTo(double x, double y, double z, float yRot, float xRot) {
        if (this.byStructure) {
            this.byStructure = false;
            this.setPos(StructureTurns.attachedAt(new Vec3(x, y, z), this.calculateBoundingBox(BlockPos.ZERO, this.getDirection())));
        } else {
            // anything else - a client making it from the packet that brings it (a block's corner, sent before what it
            // shows and which way it faces), a summon at a block - names the block it goes on, as before
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

    /**
     * Mirrored by a structure: the mirror a template does before it turns the build, done after the turn it told first
     * ({@link StructureTurns#afterTurn}) - and a label to the right of a face's middle is to its left in the mirror.
     */
    @Override
    public float mirror(Mirror mirror) {
        this.byStructure = true;
        Mirror after = StructureTurns.afterTurn(mirror, this.turned);
        if (after != Mirror.NONE) {
            this.entityData.set(DATA_PLACE, pack(-this.across(), this.up(), this.sunk()));
        }
        return super.mirror(after);
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
        return this.asItem();
    }
}
