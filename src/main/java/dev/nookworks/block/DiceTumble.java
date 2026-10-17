package dev.nookworks.block;

import java.util.Random;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;

/**
 * A die's roll worked out as a real die's would be (the user, 2026-10-04: "もうちょっと物理っぽく転がらないかな"): an
 * 8 px cube tossed up with a spin, falling, striking the ground corner by corner, bouncing, tumbling over its edges
 * and coming to rest on a face - a small rigid-body simulation, gravity, impacts with restitution and friction at each
 * corner, the walls of its own block as the rim of a tray. Where it rests is the number.
 * <p>
 * Fair as a real die is fair: no one follows its pips while it spins. A die worked out from where it lay, tossed and
 * bouncing only once or twice in its own block, came down by how it was tossed more than by chance - its starting face
 * or the one under it too often. So high in the air, spinning fastest, its faces are given out afresh by one of the
 * cube's 24 turns, picked at random: the same cube, the same motion, only which pips are where - unseen at that speed,
 * and every number then exactly as likely as any other, whatever the bounces do.
 * <p>
 * The whole roll is worked out at once from a seed and the die's pose before it, the same on the server (which keeps
 * the number) and on every client (which draws it): plain arithmetic in doubles, square roots and nothing else, so it
 * comes out the same everywhere. Each tick's pose is kept for drawing; once it rests, a few ticks level it onto the
 * face it lies on, where it lies and turned as it lies: it stays there, its outline and its collision with it, and its
 * next roll starts from there. (It was eased onto the middle of its block and a quarter turn, as its block model was
 * drawn; seen sliding back to the middle after a bounce, that looked wrong - the user, 2026-10-04.)
 * <p>
 * The die's faces, as its block model has them unturned (models/block/dice_1.json): 1 up, 6 down, 2 north, 5 south,
 * 3 east, 4 west. The block with {@code value} on top is that die turned by {@link #TOP} (an element rotation in the
 * model), then by its facing (the blockstate's "y", a quarter turn clockwise seen from above per step).
 */
public final class DiceTumble {
    /** Half the die's side, in blocks. */
    public static final double HALF = 0.25;
    /** Ticks the die takes, at rest, to level onto its face. */
    public static final int SETTLE_TICKS = 4;
    /**
     * The longest a roll tumbles before it is made to rest: four seconds. (At three, one roll in about six hundred was
     * still rocking, and was seen pushed flat.)
     */
    public static final int MAX_TICKS = 80;
    private static final int SUBSTEPS = 12;
    private static final double DT = 1.0 / 20 / SUBSTEPS;
    private static final double GRAVITY = 22;
    /** A uniform cube's moment of inertia about any axis through its middle, mass 1: side squared over six. */
    private static final double INV_INERTIA = 6 / ((2 * HALF) * (2 * HALF));
    private static final double RESTITUTION = 0.32;
    private static final double FRICTION = 0.45;
    /** The tick, high in the air, at which a die's faces are given out afresh. */
    private static final int RELABEL_TICK = 2;
    /** The cube's 24 turns, as 3x3 matrices: every signed permutation of the axes that is not a mirror image. */
    private static final int[][] CUBE_TURNS = cubeTurns();

    /** Each face's way out of the die, by its number (index 0 unused). */
    private static final int[][] FACE = {null, {0, 1, 0}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, -1, 0}};
    /**
     * The turn that brings each number on top (index 0 unused), as a 3x3 matrix, row by row: its block model's
     * element rotation - 2: a quarter turn about x, 5: back about x, 6: half about z, 3: a quarter about z, 4: back about z.
     */
    static final int[][] TOP = {null,
            {1, 0, 0, 0, 1, 0, 0, 0, 1},
            {1, 0, 0, 0, 0, -1, 0, 1, 0},
            {0, -1, 0, 1, 0, 0, 0, 0, 1},
            {0, 1, 0, -1, 0, 0, 0, 0, 1},
            {1, 0, 0, 0, 0, 1, 0, -1, 0},
            {-1, 0, 0, 0, -1, 0, 0, 0, 1}};

    /**
     * A worked-out roll: the number it lands on and the quarter turn nearest the way it lies; the tick it came to rest
     * and the tick it is done levelling onto its face; its pose each tick (middle x, y, z in the block, then the turn as
     * a quaternion x, y, z, w); and its impacts (tick, speed), to be heard.
     */
    public record Result(int value, Direction facing, int restTick, int ticks, double[] frames, double[] impacts) {
        /** Where and how it lies once the roll is over: its last pose. */
        public double[] rest() {
            double[] out = new double[7];
            System.arraycopy(this.frames, this.ticks * 7, out, 0, 7);
            return out;
        }

        /** The pose at {@code t} ticks (fractions between ticks blended): middle x, y, z and quaternion x, y, z, w. */
        public double[] at(double t) {
            double clamped = Math.max(0, Math.min(t, this.ticks));
            int a = (int) Math.floor(clamped);
            int b = Math.min(a + 1, this.ticks);
            double f = clamped - a;
            double[] out = new double[7];
            for (int i = 0; i < 3; i++) {
                out[i] = this.frames[a * 7 + i] * (1 - f) + this.frames[b * 7 + i] * f;
            }
            double dot = 0;
            for (int i = 3; i < 7; i++) {
                dot += this.frames[a * 7 + i] * this.frames[b * 7 + i];
            }
            double sign = dot < 0 ? -1 : 1;
            double len = 0;
            for (int i = 3; i < 7; i++) {
                out[i] = this.frames[a * 7 + i] * (1 - f) + sign * this.frames[b * 7 + i] * f;
                len += out[i] * out[i];
            }
            len = Math.sqrt(len);
            for (int i = 3; i < 7; i++) {
                out[i] /= len;
            }
            return out;
        }
    }

    /** The die lying in the middle of its block with {@code value} on top, facing {@code facing}: a pose as {@link Result#at}. */
    public static double[] centred(int value, Direction facing) {
        double[] q = quaternion(pose(value, facing));
        return new double[] {0.5, HALF, 0.5, q[0], q[1], q[2], q[3]};
    }

    /** Rolls a die lying in the middle of its block with {@code value} on top, facing {@code facing}. */
    public static Result roll(long seed, int value, Direction facing, @Nullable Direction thrownTowards) {
        return roll(seed, centred(value, facing), thrownTowards);
    }

    /**
     * Rolls a die lying as {@code from} (a pose as {@link Result#at}): tossed up where it lies, or, with
     * {@code thrownTowards}, coming in from the side it was thrown from (a dispenser's).
     */
    public static Result roll(long seed, double[] from, @Nullable Direction thrownTowards) {
        Random random = new Random(seed);
        double[] q = {from[3], from[4], from[5], from[6]};
        double[] p = {from[0], HALF + 1e-4, from[2]};
        double[] v;
        double[] w;
        double[] relabel = quaternion(CUBE_TURNS[random.nextInt(CUBE_TURNS.length)]);
        if (thrownTowards == null) {
            // tossed straight up, a little off the ground already (a die spun against the ground only grinds), and
            // spinning any way, drifting a little towards the middle of its block from wherever it lay: it comes down
            // in its own block, and its sides hardly touch before it does
            p[1] = HALF + 0.2;
            v = new double[] {(random.nextDouble() * 2 - 1) * 0.1 + (0.5 - p[0]) * 2, 3.0 + random.nextDouble() * 0.8,
                    (random.nextDouble() * 2 - 1) * 0.1 + (0.5 - p[2]) * 2};
            w = randomUnit(random);
            double spin = 16 + random.nextDouble() * 10;
            for (int i = 0; i < 3; i++) {
                w[i] *= spin;
            }
        } else {
            // a die out of a dispenser comes out any way up: nothing was seen of it before
            q = randomQuaternion(random);
            double dx = thrownTowards.getStepX();
            double dz = thrownTowards.getStepZ();
            p = new double[] {0.5 - dx * 0.05, HALF + 0.5, 0.5 - dz * 0.05};
            v = new double[] {dx * 1.4 + (random.nextDouble() * 2 - 1) * 0.3, 0.8 + random.nextDouble() * 0.6,
                    dz * 1.4 + (random.nextDouble() * 2 - 1) * 0.3};
            // tumbling forward, end over end, as a thrown die does, and a little sideways
            double[] r = randomUnit(random);
            double spin = 14 + random.nextDouble() * 10;
            w = new double[] {dz * spin + r[0] * 4, r[1] * 4, -dx * spin + r[2] * 4};
        }
        double[] frames = new double[(MAX_TICKS + SETTLE_TICKS + 1) * 7];
        double[] impacts = new double[64];
        int impactCount = 0;
        // the first frame is the die as it lay (or as it leaves the dispenser)
        put(frames, 0, thrownTowards == null ? new double[] {from[0], from[1], from[2]} : p, q);
        int still = 0;
        int rest = MAX_TICKS;
        double lastImpact = -1;
        for (int tick = 1; tick <= MAX_TICKS; tick++) {
            boolean touching = false;
            for (int s = 0; s < SUBSTEPS; s++) {
                double time = tick - 1 + (s + 1) / (double) SUBSTEPS;
                v[1] -= GRAVITY * DT;
                for (int i = 0; i < 3; i++) {
                    p[i] += v[i] * DT;
                }
                integrate(q, w, DT);
                double hit = collide(p, q, v, w);
                if (hit >= 0) {
                    touching = true;
                }
                if (hit > 0.7 && time - lastImpact > 0.25 && impactCount < impacts.length) {
                    impacts[impactCount++] = time;
                    impacts[impactCount++] = hit;
                    lastImpact = time;
                }
                // rolling resistance on the ground; nothing slows it in the air
                double drag = touching ? 1 - 2.2 * DT : 1;
                for (int i = 0; i < 3; i++) {
                    w[i] *= drag;
                }
            }
            if (tick == RELABEL_TICK) {
                q = multiply(q, relabel); // its faces given out afresh: the same cube, turned within itself
            }
            put(frames, tick, p, q);
            boolean slow = touching && length(v) < 0.08 && length(w) < 0.9 && flat(q);
            still = slow ? still + 1 : 0;
            if (still >= 4) {
                rest = tick;
                break;
            }
        }
        // what it shows: the face most nearly up, and the quarter turn nearest the way it lies (the blockstate's)
        int top = 1;
        double best = -2;
        double[][] m = matrix(q);
        for (int n = 1; n <= 6; n++) {
            double up = m[1][0] * FACE[n][0] + m[1][1] * FACE[n][1] + m[1][2] * FACE[n][2];
            if (up > best) {
                best = up;
                top = n;
            }
        }
        Direction lands = Direction.NORTH;
        double nearest = -2;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            double[] candidate = quaternion(pose(top, side));
            double dot = Math.abs(candidate[0] * q[0] + candidate[1] * q[1] + candidate[2] * q[2] + candidate[3] * q[3]);
            if (dot > nearest) {
                nearest = dot;
                lands = side;
            }
        }
        // levelled where it lies: the face up made exactly flat, its turn about the upright and its middle kept - only
        // the few degrees it may still lean are taken out (and kept inside its block, which levelling could poke a
        // corner out of)
        int[] upright = pose(top, Direction.NORTH);
        double[][] lying = matrix(q);
        // its turn about the upright, near enough: lying times upright's transpose, rows x and z
        double c = 0;
        double s = 0;
        for (int k = 0; k < 3; k++) {
            c += lying[0][k] * upright[k] + lying[2][k] * upright[6 + k];
            s += lying[0][k] * upright[6 + k] - lying[2][k] * upright[k];
        }
        double norm = Math.sqrt(c * c + s * s);
        c /= norm;
        s /= norm;
        // that turn as a quaternion about y, its half angle by square roots alone
        double[] yaw = {0, Math.sqrt(Math.max(0, (1 - c) / 2)) * (s < 0 ? -1 : 1), 0, Math.sqrt(Math.max(0, (1 + c) / 2))};
        double[] endQ = multiply(yaw, quaternion(upright));
        if (endQ[0] * q[0] + endQ[1] * q[1] + endQ[2] * q[2] + endQ[3] * q[3] < 0) {
            for (int i = 0; i < 4; i++) {
                endQ[i] = -endQ[i];
            }
        }
        double reach = HALF * (Math.abs(c) + Math.abs(s));
        double[] endP = {Math.max(reach, Math.min(1 - reach, p[0])), HALF, Math.max(reach, Math.min(1 - reach, p[2]))};
        double[] fromP = p.clone();
        double[] fromQ = q.clone();
        for (int k = 1; k <= SETTLE_TICKS; k++) {
            double f = k / (double) SETTLE_TICKS;
            f = f * f * (3 - 2 * f);
            double[] pk = {fromP[0] + (endP[0] - fromP[0]) * f, fromP[1] + (HALF - fromP[1]) * f, fromP[2] + (endP[2] - fromP[2]) * f};
            double[] qk = new double[4];
            double len = 0;
            for (int i = 0; i < 4; i++) {
                qk[i] = fromQ[i] * (1 - f) + endQ[i] * f;
                len += qk[i] * qk[i];
            }
            len = Math.sqrt(len);
            for (int i = 0; i < 4; i++) {
                qk[i] /= len;
            }
            double[] pose = {pk[0], pk[1], pk[2], qk[0], qk[1], qk[2], qk[3]};
            double low = extent(pose)[1];
            if (low < 0 && k < SETTLE_TICKS) {
                pk[1] -= low; // a die levelled off its edge onto its face is lifted, not pushed into the ground
            }
            put(frames, rest + k, pk, qk);
        }
        int ticks = rest + SETTLE_TICKS;
        double[] kept = new double[(ticks + 1) * 7];
        System.arraycopy(frames, 0, kept, 0, kept.length);
        double[] heard = new double[impactCount];
        System.arraycopy(impacts, 0, heard, 0, impactCount);
        return new Result(top, lands, rest, ticks, kept, heard);
    }

    // ---- the die's poses ----

    /** The die with {@code value} on top facing {@code facing}, as its block shows it: a 3x3 matrix, die to world. */
    public static int[] pose(int value, Direction facing) {
        int[] top = TOP[Math.max(1, Math.min(6, value))];
        // the blockstate's "y": a quarter turn clockwise seen from above for each step from north
        int turns = switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        int[] yaw = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int i = 0; i < turns; i++) {
            // clockwise from above: x' = -z, z' = x
            yaw = multiply(new int[] {0, 0, -1, 0, 1, 0, 1, 0, 0}, yaw);
        }
        return multiply(yaw, top);
    }

    static int[] multiply(int[] a, int[] b) {
        int[] out = new int[9];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                out[r * 3 + c] = a[r * 3] * b[c] + a[r * 3 + 1] * b[3 + c] + a[r * 3 + 2] * b[6 + c];
            }
        }
        return out;
    }

    /** The quaternion (x, y, z, w) of a rotation matrix with entries 0 and ±1. */
    static double[] quaternion(int[] m) {
        double m00 = m[0], m01 = m[1], m02 = m[2], m10 = m[3], m11 = m[4], m12 = m[5], m20 = m[6], m21 = m[7], m22 = m[8];
        double trace = m00 + m11 + m22;
        double x, y, z, w;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1) * 2;
            w = 0.25 * s;
            x = (m21 - m12) / s;
            y = (m02 - m20) / s;
            z = (m10 - m01) / s;
        } else if (m00 > m11 && m00 > m22) {
            double s = Math.sqrt(1 + m00 - m11 - m22) * 2;
            w = (m21 - m12) / s;
            x = 0.25 * s;
            y = (m01 + m10) / s;
            z = (m02 + m20) / s;
        } else if (m11 > m22) {
            double s = Math.sqrt(1 + m11 - m00 - m22) * 2;
            w = (m02 - m20) / s;
            x = (m01 + m10) / s;
            y = 0.25 * s;
            z = (m12 + m21) / s;
        } else {
            double s = Math.sqrt(1 + m22 - m00 - m11) * 2;
            w = (m10 - m01) / s;
            x = (m02 + m20) / s;
            y = (m12 + m21) / s;
            z = 0.25 * s;
        }
        return new double[] {x, y, z, w};
    }

    // ---- the physics ----

    private static double[][] matrix(double[] q) {
        double x = q[0], y = q[1], z = q[2], w = q[3];
        return new double[][] {
                {1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)},
                {2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)},
                {2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)}};
    }

    /** q turned on by the spin w for dt, kept a unit quaternion. */
    private static void integrate(double[] q, double[] w, double dt) {
        double x = q[0], y = q[1], z = q[2], qw = q[3];
        double h = dt * 0.5;
        // dq = 1/2 (0, w) * q
        q[0] = x + h * (w[0] * qw + w[1] * z - w[2] * y);
        q[1] = y + h * (w[1] * qw + w[2] * x - w[0] * z);
        q[2] = z + h * (w[2] * qw + w[0] * y - w[1] * x);
        q[3] = qw + h * (-w[0] * x - w[1] * y - w[2] * z);
        double len = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int i = 0; i < 4; i++) {
            q[i] /= len;
        }
    }

    /**
     * Corners against the ground and the block's sides: pushed back out, and given the impulse that stops them going
     * in - bounced if they struck hard, rubbed by friction along the surface. The hardest strike's speed, 0 for a
     * resting touch, -1 for none.
     */
    private static double collide(double[] p, double[] q, double[] v, double[] w) {
        double hardest = -1;
        // planes: a point inside has n . c - offset >= 0
        double[][] planes = {{0, 1, 0, 0}, {1, 0, 0, 0}, {-1, 0, 0, -1}, {0, 0, 1, 0}, {0, 0, -1, -1}};
        for (int pass = 0; pass < 3; pass++) {
            double[][] m = matrix(q);
            for (double[] plane : planes) {
                double deepest = 0;
                for (int corner = 0; corner < 8; corner++) {
                    double bx = (corner & 1) == 0 ? -HALF : HALF;
                    double by = (corner & 2) == 0 ? -HALF : HALF;
                    double bz = (corner & 4) == 0 ? -HALF : HALF;
                    double rx = m[0][0] * bx + m[0][1] * by + m[0][2] * bz;
                    double ry = m[1][0] * bx + m[1][1] * by + m[1][2] * bz;
                    double rz = m[2][0] * bx + m[2][1] * by + m[2][2] * bz;
                    double depth = plane[0] * (p[0] + rx) + plane[1] * (p[1] + ry) + plane[2] * (p[2] + rz) - plane[3];
                    if (depth >= 0) {
                        continue;
                    }
                    deepest = Math.min(deepest, depth);
                    // the corner's velocity: v + w x r
                    double vx = v[0] + w[1] * rz - w[2] * ry;
                    double vy = v[1] + w[2] * rx - w[0] * rz;
                    double vz = v[2] + w[0] * ry - w[1] * rx;
                    double vn = vx * plane[0] + vy * plane[1] + vz * plane[2];
                    if (vn >= 0) {
                        hardest = Math.max(hardest, 0);
                        continue;
                    }
                    hardest = Math.max(hardest, -vn);
                    double e = plane[1] > 0 ? (-vn > 0.6 ? RESTITUTION : 0) : 0.4;
                    // r x n
                    double cx = ry * plane[2] - rz * plane[1];
                    double cy = rz * plane[0] - rx * plane[2];
                    double cz = rx * plane[1] - ry * plane[0];
                    double k = 1 + INV_INERTIA * (cx * cx + cy * cy + cz * cz);
                    double jn = -(1 + e) * vn / k;
                    applyImpulse(v, w, rx, ry, rz, plane[0] * jn, plane[1] * jn, plane[2] * jn);
                    // friction along the surface, no more than the push allows
                    vx = v[0] + w[1] * rz - w[2] * ry;
                    vy = v[1] + w[2] * rx - w[0] * rz;
                    vz = v[2] + w[0] * ry - w[1] * rx;
                    vn = vx * plane[0] + vy * plane[1] + vz * plane[2];
                    double tx = vx - vn * plane[0];
                    double ty = vy - vn * plane[1];
                    double tz = vz - vn * plane[2];
                    double speed = Math.sqrt(tx * tx + ty * ty + tz * tz);
                    if (speed > 1e-6 && plane[1] > 0) { // the ground grips; the block's sides, a smooth rim, do not
                        tx /= speed;
                        ty /= speed;
                        tz /= speed;
                        double ax = ry * tz - rz * ty;
                        double ay = rz * tx - rx * tz;
                        double az = rx * ty - ry * tx;
                        double kt = 1 + INV_INERTIA * (ax * ax + ay * ay + az * az);
                        double jt = Math.min(speed / kt, FRICTION * jn);
                        applyImpulse(v, w, rx, ry, rz, -tx * jt, -ty * jt, -tz * jt);
                    }
                }
                if (deepest < 0) {
                    p[0] -= plane[0] * deepest;
                    p[1] -= plane[1] * deepest;
                    p[2] -= plane[2] * deepest;
                }
            }
        }
        return hardest;
    }

    private static void applyImpulse(double[] v, double[] w, double rx, double ry, double rz, double jx, double jy, double jz) {
        v[0] += jx;
        v[1] += jy;
        v[2] += jz;
        w[0] += INV_INERTIA * (ry * jz - rz * jy);
        w[1] += INV_INERTIA * (rz * jx - rx * jz);
        w[2] += INV_INERTIA * (rx * jy - ry * jx);
    }

    /** Lying on a face: one of its axes within a few degrees of straight up. */
    private static boolean flat(double[] q) {
        double[][] m = matrix(q);
        return Math.max(Math.abs(m[1][0]), Math.max(Math.abs(m[1][1]), Math.abs(m[1][2]))) > 0.995;
    }

    /** The box the die's corners reach in {@code pose} (as {@link Result#at}): min x, y, z, then max x, y, z. */
    public static double[] extent(double[] pose) {
        double[][] m = matrix(new double[] {pose[3], pose[4], pose[5], pose[6]});
        double[] box = {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int corner = 0; corner < 8; corner++) {
            double[] b = {(corner & 1) == 0 ? -HALF : HALF, (corner & 2) == 0 ? -HALF : HALF, (corner & 4) == 0 ? -HALF : HALF};
            for (int i = 0; i < 3; i++) {
                double c = pose[i] + m[i][0] * b[0] + m[i][1] * b[1] + m[i][2] * b[2];
                box[i] = Math.min(box[i], c);
                box[i + 3] = Math.max(box[i + 3], c);
            }
        }
        return box;
    }

    /**
     * The corners of the die's foot in {@code pose}, x and z, in turn round it - or null if it is not lying on a face
     * (none of its axes upright).
     */
    public static double @Nullable [][] footCorners(double[] pose) {
        double[][] m = matrix(new double[] {pose[3], pose[4], pose[5], pose[6]});
        int upright = -1;
        for (int k = 0; k < 3; k++) {
            if (Math.abs(m[1][k]) > 0.99) {
                upright = k;
            }
        }
        if (upright < 0) {
            return null;
        }
        int a = (upright + 1) % 3;
        int b = (upright + 2) % 3;
        double[][] out = new double[4][];
        double[][] signs = {{1, 1}, {1, -1}, {-1, -1}, {-1, 1}};
        for (int i = 0; i < 4; i++) {
            out[i] = new double[] {pose[0] + HALF * (signs[i][0] * m[0][a] + signs[i][1] * m[0][b]),
                    pose[2] + HALF * (signs[i][0] * m[2][a] + signs[i][1] * m[2][b])};
        }
        return out;
    }

    /** The quaternion (x, y, z, w) of a pose matrix from {@link #pose}. */
    public static double[] quaternionOf(int[] matrix) {
        return quaternion(matrix);
    }

    private static double length(double[] a) {
        return Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    /** q * r: r turns the die within itself (its own axes), then q as before. */
    private static double[] multiply(double[] q, double[] r) {
        return new double[] {
                q[3] * r[0] + q[0] * r[3] + q[1] * r[2] - q[2] * r[1],
                q[3] * r[1] - q[0] * r[2] + q[1] * r[3] + q[2] * r[0],
                q[3] * r[2] + q[0] * r[1] - q[1] * r[0] + q[2] * r[3],
                q[3] * r[3] - q[0] * r[0] - q[1] * r[1] - q[2] * r[2]};
    }

    private static int[][] cubeTurns() {
        int[][] perms = {{0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}};
        java.util.List<int[]> turns = new java.util.ArrayList<>();
        for (int[] perm : perms) {
            for (int signs = 0; signs < 8; signs++) {
                int[] m = new int[9];
                for (int r = 0; r < 3; r++) {
                    m[r * 3 + perm[r]] = (signs >> r & 1) == 0 ? 1 : -1;
                }
                int det = m[0] * (m[4] * m[8] - m[5] * m[7]) - m[1] * (m[3] * m[8] - m[5] * m[6]) + m[2] * (m[3] * m[7] - m[4] * m[6]);
                if (det == 1) {
                    turns.add(m);
                }
            }
        }
        return turns.toArray(new int[0][]);
    }

    /** A turn of any way up, each as likely as any other. */
    private static double[] randomQuaternion(Random random) {
        double[] q = {random.nextGaussian(), random.nextGaussian(), random.nextGaussian(), random.nextGaussian()};
        double len = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int i = 0; i < 4; i++) {
            q[i] /= len;
        }
        return q;
    }

    private static double[] randomUnit(Random random) {
        while (true) {
            double x = random.nextDouble() * 2 - 1;
            double y = random.nextDouble() * 2 - 1;
            double z = random.nextDouble() * 2 - 1;
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len > 0.1 && len <= 1) {
                return new double[] {x / len, y / len, z / len};
            }
        }
    }

    private static void put(double[] frames, int tick, double[] p, double[] q) {
        System.arraycopy(p, 0, frames, tick * 7, 3);
        System.arraycopy(q, 0, frames, tick * 7 + 3, 4);
    }

    private DiceTumble() {}
}
