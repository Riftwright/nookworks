package dev.nookworks.ventilation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where the smoke goes. A pot, a grill, a generator or an electric furnace working indoors sends its smoke and steam
 * up into the room; with a Range Hood over it (up to {@link #HOOD_REACH} blocks up) or a running Ventilation Fan in
 * front of it (within {@link #FAN_REACH}), the smoke is drawn off into that instead, and comes out where the air goes:
 * out of the back of the fan, or along the Ducts behind it to a duct's open end or up a Chimney and out of its pot. A
 * Chimney over a fire - a campfire, a lit furnace, a running generator - smokes from its top of itself.
 * <p>
 * All of it is how the smoke looks (particles, on the client); it changes nothing else.
 */
public final class Ventilation {
    public static final int FAN_REACH = 6;
    public static final int HOOD_REACH = 3;
    /** How far along its ducts the smoke is followed. */
    private static final int DUCT_LIMIT = 96;
    /** How long a found way out is kept before it is looked for again, in ticks. */
    private static final int OUTLET_MEMORY = 40;

    /** Where smoke goes in: the block, the point it is drawn to, and the block beyond it the air goes on into. */
    public record Intake(BlockPos pos, Vec3 mouth, BlockPos exit, Direction exitSide) {}

    /** The fans loaded in each level (either side), which add and take themselves away. */
    private static final Map<Level, Set<BlockPos>> FANS = new WeakHashMap<>();
    private static final Map<Level, Map<BlockPos, Remembered>> OUTLETS = new WeakHashMap<>();

    private record Remembered(long time, List<Vec3> outlets) {}

    /** A way out smoking: where, whether it is a chimney's pot (a column of smoke) or not (a thin stream). */
    public record Smoking(Vec3 at, boolean chimney) {}

    /** The ways out smoking now, and until when (game time), per level: kept by smoke on its way, drawn on the client. */
    private static final Map<Level, Map<Smoking, Long>> SMOKING = new WeakHashMap<>();

    /** Keeps {@code at} smoking for {@code ticks} more ticks. */
    public static synchronized void markSmoking(Level level, Vec3 at, boolean chimney, int ticks) {
        long until = level.getGameTime() + ticks;
        SMOKING.computeIfAbsent(level, l -> new HashMap<>()).merge(new Smoking(at, chimney), until, Math::max);
    }

    /** The ways out smoking now in {@code level}; those whose time is up are forgotten. */
    public static synchronized List<Smoking> smokingNow(Level level) {
        Map<Smoking, Long> all = SMOKING.get(level);
        if (all == null) {
            return List.of();
        }
        long now = level.getGameTime();
        all.values().removeIf(until -> until < now);
        return List.copyOf(all.keySet());
    }

    private Ventilation() {}

    static synchronized void fanLoaded(Level level, BlockPos pos) {
        FANS.computeIfAbsent(level, l -> new HashSet<>()).add(pos.immutable());
    }

    static synchronized void fanUnloaded(Level level, BlockPos pos) {
        Set<BlockPos> fans = FANS.get(level);
        if (fans != null) {
            fans.remove(pos);
        }
    }

    private static synchronized List<BlockPos> fansNear(Level level, BlockPos source) {
        Set<BlockPos> fans = FANS.get(level);
        if (fans == null) {
            return List.of();
        }
        List<BlockPos> near = new ArrayList<>();
        for (BlockPos fan : fans) {
            if (fan.distSqr(source) <= FAN_REACH * FAN_REACH) {
                near.add(fan);
            }
        }
        return near;
    }

    /** What draws the smoke rising at {@code source}: a hood over it, or the nearest running fan it is in front of. */
    public static @Nullable Intake intakeFor(Level level, BlockPos source) {
        for (int dy = 1; dy <= HOOD_REACH; dy++) {
            BlockPos up = source.above(dy);
            BlockState state = level.getBlockState(up);
            if (state.getBlock() instanceof RangeHoodBlock) {
                return new Intake(up, new Vec3(up.getX() + 0.5, up.getY() + 0.2, up.getZ() + 0.5), up.above(), Direction.UP);
            }
            if (!state.getCollisionShape(level, up).isEmpty()) {
                break; // a ceiling between: the smoke stops under it
            }
        }
        Intake best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos fan : fansNear(level, source)) {
            BlockState state = level.getBlockState(fan);
            if (!(state.getBlock() instanceof VentFanBlock) || !state.getValue(VentFanBlock.ON)) {
                continue;
            }
            Direction front = state.getValue(VentFanBlock.FACING);
            Vec3 toSource = Vec3.atCenterOf(source).subtract(Vec3.atCenterOf(fan));
            if (toSource.dot(front.getUnitVec3()) <= 0.25) {
                continue; // behind the fan, or beside it
            }
            double distance = toSource.lengthSqr();
            if (distance < bestDistance) {
                bestDistance = distance;
                Vec3 mouth = Vec3.atCenterOf(fan).add(front.getUnitVec3().scale(0.55));
                best = new Intake(fan, mouth, fan.relative(front.getOpposite()), front.getOpposite());
            }
        }
        return best;
    }

    /**
     * Where smoke drawn into {@code intake} comes out: into the open beyond it; or along the ducts behind it to each
     * duct end that opens on air, and up each chimney they reach to its top. Remembered a little while, as a room's
     * ducting does not change from one puff of smoke to the next.
     */
    public static List<Vec3> outlets(Level level, Intake intake) {
        long now = level.getGameTime();
        synchronized (Ventilation.class) {
            Remembered known = OUTLETS.computeIfAbsent(level, l -> new HashMap<>()).get(intake.pos());
            if (known != null && now - known.time() < OUTLET_MEMORY) {
                return known.outlets();
            }
        }
        List<Vec3> found = findOutlets(level, intake);
        synchronized (Ventilation.class) {
            OUTLETS.computeIfAbsent(level, l -> new HashMap<>()).put(intake.pos().immutable(), new Remembered(now, found));
        }
        return found;
    }

    private static List<Vec3> findOutlets(Level level, Intake intake) {
        BlockPos exit = intake.exit();
        BlockState first = level.getBlockState(exit);
        if (first.isAir()) {
            return List.of(Vec3.atCenterOf(exit).relative(intake.exitSide(), -0.3));
        }
        if (first.getBlock() instanceof ChimneyBlock) {
            Vec3 top = chimneyTop(level, exit);
            return top == null ? List.of() : List.of(top);
        }
        if (!(first.getBlock() instanceof DuctBlock)) {
            return List.of(); // walled up: the smoke has nowhere to go but it is drawn off all the same
        }
        List<Vec3> out = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(exit);
        seen.add(exit);
        while (!queue.isEmpty() && seen.size() <= DUCT_LIMIT) {
            BlockPos duct = queue.poll();
            BlockState state = level.getBlockState(duct);
            List<Direction> joined = DuctBlock.connections(state);
            for (Direction side : joined) {
                BlockPos next = duct.relative(side);
                BlockState there = level.getBlockState(next);
                if (there.getBlock() instanceof DuctBlock && seen.add(next)) {
                    queue.add(next);
                } else if (there.getBlock() instanceof ChimneyBlock && seen.add(next)) {
                    Vec3 top = chimneyTop(level, next);
                    if (top != null) {
                        out.add(top);
                    }
                }
            }
            if (joined.size() == 1) { // a duct end: it opens on whatever is across from where it is joined
                Direction open = joined.get(0).getOpposite();
                if (level.getBlockState(duct.relative(open)).isAir()) {
                    out.add(Vec3.atCenterOf(duct).relative(open, 0.55));
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** The top of the chimney {@code part} is in, where its smoke comes out, or null when it is capped by a block. */
    static @Nullable Vec3 chimneyTop(Level level, BlockPos part) {
        BlockPos top = part;
        for (int i = 0; i < 64 && level.getBlockState(top.above()).getBlock() instanceof ChimneyBlock; i++) {
            top = top.above();
        }
        return level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty()
                ? new Vec3(top.getX() + 0.5, top.getY() + 1.05, top.getZ() + 0.5) : null;
    }

    /**
     * Smoke rising off a pot, a grill or a machine at {@code source}, from ({@code x}, {@code y}, {@code z}) with the
     * velocity given: drawn off towards an intake if there is one - and then, as much again, out at one of its
     * outlets - else rising as it would.
     */
    public static void smoke(Level level, BlockPos source, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        Intake intake = intakeFor(level, source);
        if (intake == null) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
            return;
        }
        Vec3 to = intake.mouth().subtract(x, y, z);
        Vec3 v = to.normalize().scale(Math.min(0.14, 0.03 + to.length() * 0.025));
        level.addParticle(particle, x, y, z, v.x, v.y, v.z);
        List<Vec3> outs = outlets(level, intake);
        if (outs.isEmpty()) {
            return;
        }
        for (Vec3 out : outs) { // smoking a while, as smoke keeps coming: see VentilationClient
            markSmoking(level, out, level.getBlockState(BlockPos.containing(out).below()).getBlock() instanceof ChimneyBlock, 80);
        }
    }
}
