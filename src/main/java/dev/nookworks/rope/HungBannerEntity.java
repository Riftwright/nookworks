package dev.nookworks.rope;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A banner hung on Thin Rope (the user, 2026-10-06: "細いロープに自然な見た目で旗をかけられるように"). On a rope strung
 * between posts its bar is lashed under the rope at both ends and the cloth hangs straight down from it, its face to
 * either side of the line - the side whoever hung it stood on - and its top edge along the rope as the rope slopes
 * there. From the end of a rope let down, it hangs by a cord from both ends of its bar, as a hanging scroll does, and
 * faces whoever hung it as a standing banner faces whoever set it down.
 * <p>
 * It is the banner itself, its colour and loom patterns kept: drawn as vanilla draws a banner, waving as one does, and
 * given back as it was when it is taken down or falls. Near a block across and two high, it keeps more room along a
 * rope than a bottle does ({@link #ROOM}), needs its whole height clear below the rope, and hangs only where the rope
 * is not steep ({@link #MAX_SLOPE}).
 */
public class HungBannerEntity extends HungItemEntity {
    /** Vanilla draws a banner's model at two thirds: a flag 20 pixels by 40 and a bar 2 thick, as here. */
    public static final double SCALE = 2.0 / 3;
    public static final double WIDTH = 20 * SCALE / 16;
    /** From the top of the bar to the foot of the cloth. */
    public static final double HEIGHT = 40 * SCALE / 16;
    public static final double BAR = 2 * SCALE / 16;
    /** Along a rope it keeps half its width each way, and a little more, so two side by side do not touch. */
    public static final double ROOM = 0.45;
    /** Out from the line of the rope, either way: the bar, the cloth in front of it and the cloth's wave. */
    private static final double DEPTH = 3.0 / 16;
    /** Where the bar is tied, either side of its middle: a pixel and a half in from its ends. */
    public static final double TIE_X = WIDTH / 2 - 1.5 * SCALE / 16;
    /** The rope's own thickness, half of it: the bar hangs under it. */
    public static final double ROPE_RADIUS = 0.5 / 16;
    /** How far above the bar a rope's end is, where the cord from the bar's two ends meets it. */
    public static final double BRIDLE = 3.0 / 16;
    /**
     * The steepest a rope can be where a banner hangs, rise over run: one in two. Its cloth is drawn leaning with the
     * rope (its sides upright), which reads as a cloth hung along a sloping line up to about this; past it, a lopsided
     * flag.
     */
    public static final double MAX_SLOPE = 0.5;
    static final String TOO_STEEP = "message.nookworks.rope.too_steep";

    /** How the rope rises across it, along the way it faces left to right: its top edge follows it. */
    private static final EntityDataAccessor<Float> DATA_SLOPE = SynchedEntityData.defineId(HungBannerEntity.class, EntityDataSerializers.FLOAT);
    /** Turned round on its span, its face to the other side: whoever hung it stood there. */
    private boolean flipped;

    public HungBannerEntity(EntityType<? extends HungBannerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder entityData) {
        super.defineSynchedData(entityData);
        entityData.define(DATA_SLOPE, 0.0F);
    }

    /** Facing a player facing {@code playerYaw}, in sixteenths of a turn, as a standing banner is set down. */
    static float facing(float playerYaw) {
        return RotationSegment.convertToDegrees(RotationSegment.convertToSegment(playerYaw + 180.0F));
    }

    /** Turns its face to the side of its span {@code seenFrom} is on. */
    void faceTowards(Level level, Vec3 seenFrom) {
        BlockPos from = this.spanFrom();
        BlockPos to = this.spanTo();
        if (from == null || to == null) {
            return;
        }
        double yaw = Math.toRadians(spanYaw(level, from, to));
        Vec3 point = RopeSpans.at(level, from, to, this.along());
        // its face looks out along (-sin, cos) of its yaw, as a banner's does
        this.flipped = (seenFrom.x - point.x) * -Math.sin(yaw) + (seenFrom.z - point.z) * Math.cos(yaw) < 0;
    }

    @Override
    protected float spanYaw(Level level) {
        return super.spanYaw(level) + (this.flipped && this.spanFrom() != null ? 180.0F : 0.0F);
    }

    /**
     * Mirrored by a structure on its span: its face to the rope's other side, as a thing of its own kind is turned
     * ({@link #flipped}, not the other's flag), and its top edge leaning the other way - left and right changed places.
     */
    @Override
    protected void mirroredOnSpan() {
        this.flipped = !this.flipped;
        this.entityData.set(DATA_SLOPE, -this.slope());
    }

    /** How the rope rises across it, left to right as it faces: its top edge leans so. Nothing under a rope's end. */
    public float slope() {
        return this.entityData.get(DATA_SLOPE);
    }

    @Override
    public double room() {
        return ROOM;
    }

    /**
     * Where the rope runs at its two ties, {@code TIE_X} either way of its point: the bar hangs under the line between,
     * and rises as it does. On a span only.
     */
    private Vec3[] ties() {
        BlockPos from = this.spanFrom();
        BlockPos to = this.spanTo();
        Vec3 a = RopeSpans.tie(this.level(), from);
        Vec3 b = RopeSpans.tie(this.level(), to);
        double run = Math.max(1.0E-6, Math.hypot(b.x - a.x, b.z - a.z));
        double t = this.along();
        double half = TIE_X / run;
        return new Vec3[] {RopeSpans.at(a, b, Math.max(0, t - half)), RopeSpans.at(a, b, Math.min(1, t + half))};
    }

    /**
     * Its foot, under the middle of its bar: on a span the bar runs just under the rope from tie to tie; from a rope's
     * end, the cord's height under it.
     */
    @Override
    protected Vec3 restingAt() {
        Vec3 point = this.hangPoint();
        if (this.ropeEnd() != null || this.spanFrom() == null || this.spanTo() == null) {
            return new Vec3(point.x, point.y - BRIDLE - HEIGHT, point.z);
        }
        Vec3[] ties = this.ties();
        return new Vec3(point.x, (ties[0].y + ties[1].y) / 2 - ROPE_RADIUS - HEIGHT, point.z);
    }

    @Override
    protected void place() {
        float slope = 0.0F;
        if (this.ropeEnd() == null && this.spanFrom() != null && this.spanTo() != null) {
            Vec3[] ties = this.ties();
            slope = (float) ((ties[1].y - ties[0].y) / (2 * TIE_X) * (this.flipped ? -1 : 1));
        }
        this.entityData.set(DATA_SLOPE, slope);
        super.place();
    }

    /** Besides room below: not on a steep rope. Its box is clear of blocks under a rope's end too, two blocks high. */
    @Override
    public @Nullable String misfit(Level level) {
        if (Math.abs(this.slope()) > MAX_SLOPE) {
            return TOO_STEEP;
        }
        String misfit = super.misfit(level);
        if (misfit == null && this.ropeEnd() != null && !level.noBlockCollision(null, this.getBoundingBox())) {
            return NO_ROOM;
        }
        return misfit;
    }

    /**
     * Its cloth and bar, turned as it faces - not the square box an entity has - and leaning with the rope: what is
     * clicked to take it down, and what must be clear of blocks for it to hang.
     */
    @Override
    protected AABB makeBoundingBox(Vec3 position) {
        double yaw = Math.toRadians(this.yaw());
        double cos = Math.abs(Math.cos(yaw));
        double sin = Math.abs(Math.sin(yaw));
        double half = WIDTH / 2;
        double x = cos * half + sin * DEPTH;
        double z = sin * half + cos * DEPTH;
        double rise = Math.abs(this.slope()) * half;
        return new AABB(position.x - x, position.y - rise, position.z - z, position.x + x, position.y + HEIGHT + rise, position.z + z);
    }

    /** Turned or leaning otherwise, on the client too as the change comes in, its box goes with it. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_YAW.equals(accessor) || DATA_SLOPE.equals(accessor)) {
            this.setBoundingBox(this.makeBoundingBox());
        }
    }

    /** Lit as the middle of its cloth is, as a banner is lit as one block: not by a corner of it under a ceiling. */
    @Override
    public Vec3 getLightProbePosition(float partialTick) {
        return this.getPosition(partialTick).add(0, HEIGHT / 2, 0);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("flipped", this.flipped);
        output.putFloat("slope", this.slope());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.flipped = input.getBooleanOr("flipped", false);
        this.entityData.set(DATA_SLOPE, input.getFloatOr("slope", 0.0F));
    }
}
