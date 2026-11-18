package dev.nookworks.rope;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A Thin Chain swinging after something went through it - a player or a mob walking into it, a cart, an arrow - and
 * settling again in a few seconds: the chain's droop turns about the straight line between its posts, as a slack chain
 * swings, and what hangs on it goes with it. Only looks, on the client that sees it (the user's spec, 2026-10-10): on the
 * server nothing about the span or what hangs on it changes, and nothing falls. It rattles once a touch, not every tick
 * something stands in it, and not again within {@link #COOLDOWN}.
 * <p>
 * Each chain span's knot keeps one of these while the span swings ({@code RopeKnotEntity}, client side). The geometry of
 * a touch is here too, where it can be tested.
 */
public final class ChainSway {
    /** One swing there and back, in ticks: a slack chain a block or so down swings about so. */
    private static final double PERIOD = 24;
    private static final double STIFFNESS = (2 * Math.PI / PERIOD) * (2 * Math.PI / PERIOD);
    /** How fast a swing dies away: out of sight in about three seconds, still in under five. */
    private static final double DAMPING = 0.12;
    /**
     * The turn a touch sets going, per tick: about twenty-five degrees at the swing's height for a walk straight through,
     * the middle of an eight-block chain going three pixels or so out to the side. (Half that was too little to see.)
     */
    public static final double KICK = 0.17;
    /** An arrow or a snowball sets it going less. */
    public static final double PROJECTILE_KICK = 0.1;
    private static final double MAX_SPEED = 0.3;
    private static final double MAX_ANGLE = 0.8;
    /** Ticks after a rattle before the next touch rattles it again. */
    public static final int COOLDOWN = 10;
    /** A thing out of the chain this long, coming back, is a new touch; till then it is the same touch. */
    static final int FORGET = 10;
    /** How near a drawn chain must pass to a thing's box to touch it: half a link across. */
    static final double REACH = 1.0 / 16;

    private double angle;
    private double angleO;
    private double speed;
    private int lastRattle = Integer.MIN_VALUE / 2;
    /** What is touching it now, by entity id, and the tick it was last seen touching. */
    private final Map<Integer, Integer> touching = new HashMap<>();

    /** What a touch did: nothing (the same touch as before), set the chain swinging, or that and rattled it. */
    public enum Felt {
        NOTHING, SWAYED, RATTLED
    }

    /**
     * Something ({@code id}) touching the chain at tick {@code now}, pushing it {@code push} of a full kick (signed:
     * which way across it pushes). A new touch sets it swinging; it rattles unless it rattled just now.
     */
    public Felt feel(int id, int now, double push, double kick) {
        Integer seen = this.touching.put(id, now);
        if (seen != null && now - seen <= FORGET) {
            return Felt.NOTHING; // still the same touch: it has rattled for it already
        }
        this.speed = Mth.clamp(this.speed + kick * push, -MAX_SPEED, MAX_SPEED);
        if (now - this.lastRattle < COOLDOWN) {
            return Felt.SWAYED;
        }
        this.lastRattle = now;
        return Felt.RATTLED;
    }

    /** A tick of swinging: drawn back to hang straight, and dying away. */
    public void tick(int now) {
        this.angleO = this.angle;
        this.speed += -STIFFNESS * this.angle - DAMPING * this.speed;
        this.angle = Mth.clamp(this.angle + this.speed, -MAX_ANGLE, MAX_ANGLE);
        for (Iterator<Map.Entry<Integer, Integer>> it = this.touching.entrySet().iterator(); it.hasNext();) {
            if (now - it.next().getValue() > FORGET) {
                it.remove();
            }
        }
    }

    /** Hanging still again, and nothing in it: it can be let go of. */
    public boolean settled() {
        return Math.abs(this.angle) < 0.002 && Math.abs(this.speed) < 0.002 && Math.abs(this.angleO) < 0.002 && this.touching.isEmpty();
    }

    /** How far round from hanging straight it is, in radians, between the last tick and this one. */
    public float angle(float partialTick) {
        return (float) Mth.lerp(partialTick, this.angleO, this.angle);
    }

    // ---- the geometry ----

    /** Whether {@code entity} can set a chain swinging: someone or something going through it, or shot through it. */
    public static boolean moves(Entity entity) {
        if (entity.isSpectator() || entity.isRemoved()) {
            return false;
        }
        return entity instanceof LivingEntity || entity instanceof Projectile || entity instanceof VehicleEntity;
    }

    /** How far {@code entity} went since the last tick. */
    public static Vec3 motion(Entity entity) {
        return new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
    }

    /**
     * Whether {@code entity} goes through the chain hanging from {@code a} to {@code b} this tick: a projectile along its
     * path since the last tick (an arrow goes three blocks a tick, over a chain between two of its places), anything
     * else with its box, moving.
     */
    public static boolean touches(Vec3 a, Vec3 b, Entity entity) {
        Vec3 motion = motion(entity);
        if (motion.lengthSqr() < 1.0E-5) {
            return false; // standing in it, or stuck in a block: nothing goes on happening
        }
        if (entity instanceof Projectile) {
            Vec3 to = entity.position();
            return passes(a, b, to.subtract(motion), to, entity.getBbWidth() / 2 + REACH);
        }
        return crosses(a, b, entity.getBoundingBox().inflate(REACH));
    }

    /** Whether the chain from {@code a} to {@code b}, as it hangs, runs through {@code box}. */
    public static boolean crosses(Vec3 a, Vec3 b, AABB box) {
        int steps = steps(a, b);
        Vec3 last = a;
        for (int i = 1; i <= steps; i++) {
            Vec3 next = RopeSpans.at(a, b, i / (double) steps);
            if (box.contains(last) || box.contains(next) || box.clip(last, next).isPresent()) {
                return true;
            }
            last = next;
        }
        return false;
    }

    /** Whether the path from {@code from} to {@code to} passes within {@code reach} of the chain from {@code a} to {@code b}. */
    public static boolean passes(Vec3 a, Vec3 b, Vec3 from, Vec3 to, double reach) {
        int steps = steps(a, b);
        Vec3 last = a;
        for (int i = 1; i <= steps; i++) {
            Vec3 next = RopeSpans.at(a, b, i / (double) steps);
            if (segmentsApart(last, next, from, to) < reach) {
                return true;
            }
            last = next;
        }
        return false;
    }

    /** Pieces a chain is looked at in for a touch: a quarter of a block each. */
    private static int steps(Vec3 a, Vec3 b) {
        return Math.max(4, Mth.ceil(a.distanceTo(b) * 4));
    }

    /** How near the segments {@code p0}-{@code p1} and {@code q0}-{@code q1} come. */
    static double segmentsApart(Vec3 p0, Vec3 p1, Vec3 q0, Vec3 q1) {
        Vec3 d1 = p1.subtract(p0);
        Vec3 d2 = q1.subtract(q0);
        Vec3 r = p0.subtract(q0);
        double a = d1.dot(d1);
        double e = d2.dot(d2);
        double f = d2.dot(r);
        double s;
        double t;
        if (a < 1.0E-12 && e < 1.0E-12) {
            return p0.distanceTo(q0);
        }
        if (a < 1.0E-12) {
            s = 0;
            t = Mth.clamp(f / e, 0, 1);
        } else {
            double c = d1.dot(r);
            if (e < 1.0E-12) {
                t = 0;
                s = Mth.clamp(-c / a, 0, 1);
            } else {
                double b = d1.dot(d2);
                double denom = a * e - b * b;
                s = denom > 1.0E-12 ? Mth.clamp((b * f - c * e) / denom, 0, 1) : 0;
                t = (b * s + f) / e;
                if (t < 0) {
                    t = 0;
                    s = Mth.clamp(-c / a, 0, 1);
                } else if (t > 1) {
                    t = 1;
                    s = Mth.clamp((b - c) / a, 0, 1);
                }
            }
        }
        return p0.add(d1.scale(s)).distanceTo(q0.add(d2.scale(t)));
    }

    /**
     * The way across the chain from {@code a} to {@code b} its droop swings to as its angle grows: level, at right angles
     * to it. Null for a chain hanging straight down, which has no droop to swing.
     */
    static @Nullable Vec3 across(Vec3 a, Vec3 b) {
        Vec3 along = b.subtract(a);
        Vec3 side = along.cross(new Vec3(0, -1, 0));
        return side.lengthSqr() < 1.0E-8 ? null : side.normalize();
    }

    /**
     * How much of a kick {@code motion} gives the chain from {@code a} to {@code b}, and which way: a walk straight across
     * a whole one, along it less - but any touch a third of one at the least.
     */
    public static double push(Vec3 a, Vec3 b, Vec3 motion) {
        Vec3 side = across(a, b);
        if (side == null) {
            return 0;
        }
        double across = motion.dot(side) / 0.2; // 0.2 a tick: a walk
        double sign = across < 0 ? -1 : 1;
        return sign * Mth.clamp(Math.abs(across), 1.0 / 3, 1.0);
    }

    /**
     * Where the point {@code t} of the way along the chain from {@code a} to {@code b} is, swung {@code angle} round: its
     * droop below the straight line between the posts turned about that line.
     */
    public static Vec3 swung(Vec3 a, Vec3 b, double t, double angle) {
        Vec3 hanging = RopeSpans.at(a, b, t);
        return angle == 0 ? hanging : a.lerp(b, t).add(turn(hanging.subtract(a.lerp(b, t)), b.subtract(a), angle));
    }

    /** {@code v} turned {@code angle} about {@code axis} (any length), the right way for {@link #across}. */
    static Vec3 turn(Vec3 v, Vec3 axis, double angle) {
        if (axis.lengthSqr() < 1.0E-12) {
            return v;
        }
        Vec3 k = axis.normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        return v.scale(cos).add(k.cross(v).scale(sin)).add(k.scale(k.dot(v) * (1 - cos)));
    }
}
