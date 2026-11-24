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

import dev.nookworks.block.DieselGeneratorBlock;
import dev.nookworks.block.ElectricFurnaceBlock;
import dev.nookworks.block.GeneratorBlock;
import dev.nookworks.cooking.GrillBlock;
import dev.nookworks.cooking.IronPotBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

/**
 * Where the smoke goes (the user, 2026-11-22: smoke goes in only where there is a mouth to take it). What smokes - a
 * lit campfire, fire, a lit furnace, smoker or blast furnace, and ours: a pot or grill cooking, a running generator, a
 * working electric furnace ({@link #smoking}) - sends it up into the room, unless a mouth draws it off:
 * <ul>
 * <li>a Range Hood over it, up to {@link #HOOD_REACH} blocks up with nothing between;</li>
 * <li>a running Ventilation Fan it is in front of, within {@link #FAN_REACH};</li>
 * <li>the open bottom of a Flue over an open fire ({@link #openFire}: a campfire, netherrack set alight), up to
 * {@link #FLUE_REACH} blocks below with nothing between - a fireplace, with no block of its own;</li>
 * <li>the foot of a Chimney stood straight on an open fire (the user, 2026-11-23: 「焚き火の直上の煙突や煙道は煙出ても
 * いいよ。なんか出そうな気がする」).</li>
 * </ul>
 * Drawn off, it comes out where the air goes: out of the back of the fan or the hood's collar, or along the Ducts to a
 * duct's open end or up a Chimney and out of its pot. A chimney or a flue on a furnace takes nothing: a furnace has no
 * hole on top (「かまどの上面穴空いてないじゃん」, 「かまどとかは出たらおかしいけど」).
 * <p>
 * All of it is how the smoke looks (particles, on the client: VentilationClient finds the mouths round the player
 * and keeps their ways out smoking); it changes nothing else.
 */
public final class Ventilation {
    public static final int FAN_REACH = 6;
    public static final int HOOD_REACH = 3;
    public static final int FLUE_REACH = 3;
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

    /** An open fire, whose smoke a Flue's open bottom takes: a lit campfire (or soul campfire), or fire (or soul fire). */
    public static boolean openFire(BlockState state) {
        return state.is(BlockTags.CAMPFIRES) ? CampfireBlock.isLitCampfire(state) : state.is(BlockTags.FIRE);
    }

    /**
     * An open fire at {@code pos}: one of {@link #openFire(BlockState)}, or a Grill astride a lit campfire, or a pot or
     * pan set on that grill - the fire is right under the grate, empty or not, and smokes through it (the user,
     * 2026-11-23: 「焼き網つかうと煙出なくなる」: an empty grill stopped a hood from seeing the fire).
     */
    public static boolean openFire(Level level, BlockPos pos, BlockState state) {
        if (openFire(state)) {
            return true;
        }
        boolean astride = GrillBlock.overFire(state) || state.getBlock() instanceof IronPotBlock && state.getValue(IronPotBlock.ON_GRILL);
        return astride && CampfireBlock.isLitCampfire(level.getBlockState(pos.below()));
    }

    /**
     * Whether smoke rises off {@code state} at {@code pos} now: an open fire, a grill or a pot on a lit campfire among
     * them; a lit furnace, smoker or blast furnace; a pot or grill cooking; a running Generator or Diesel Generator; a
     * working Electric Furnace.
     */
    public static boolean smoking(Level level, BlockPos pos, BlockState state) {
        if (openFire(level, pos, state)) {
            return true;
        }
        Block block = state.getBlock();
        if (block instanceof AbstractFurnaceBlock || block instanceof GeneratorBlock || block instanceof DieselGeneratorBlock
                || block instanceof ElectricFurnaceBlock) {
            return state.getValueOrElse(BlockStateProperties.LIT, false); // another mod's furnace may have no LIT
        }
        if (block instanceof IronPotBlock) {
            return state.getValue(IronPotBlock.COOKING);
        }
        return block instanceof GrillBlock && GrillBlock.cooking(level, pos);
    }

    /** Whether {@code state} may be a mouth smoke goes into: a hood, a fan, a flue or chimney (whose foot may be open). */
    public static boolean mayBeIntake(BlockState state) {
        return state.getBlock() instanceof RangeHoodBlock || state.getBlock() instanceof VentFanBlock || state.getBlock() instanceof ChimneyBlock;
    }

    /**
     * The mouth at {@code pos}: a Range Hood (its collar the way on), a running Ventilation Fan (its back), a Flue
     * whose bottom is open - with no more of the chimney under it - or a Chimney stood straight on an open fire (the
     * flue or chimney itself the way on); or null.
     */
    public static @Nullable Intake intakeAt(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof RangeHoodBlock) {
            return new Intake(pos.immutable(), new Vec3(pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5), pos.above(), Direction.UP);
        }
        if (state.getBlock() instanceof VentFanBlock) {
            if (!state.getValue(VentFanBlock.ON)) {
                return null;
            }
            Direction front = state.getValue(VentFanBlock.FACING);
            Vec3 mouth = Vec3.atCenterOf(pos).add(front.getUnitVec3().scale(0.55));
            return new Intake(pos.immutable(), mouth, pos.relative(front.getOpposite()), front.getOpposite());
        }
        if (state.getBlock() instanceof ChimneyBlock) {
            BlockState below = level.getBlockState(pos.below());
            boolean open = state.getBlock() instanceof FlueBlock ? !(below.getBlock() instanceof ChimneyBlock) : openFire(level, pos.below(), below);
            if (open) {
                return new Intake(pos.immutable(), new Vec3(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5), pos.immutable(), Direction.UP);
            }
        }
        return null;
    }

    /** What {@code intake} draws smoke off now: the places it takes from that are smoking. Empty: nothing to draw. */
    public static List<BlockPos> drawing(Level level, Intake intake) {
        BlockState state = level.getBlockState(intake.pos());
        List<BlockPos> found = new ArrayList<>();
        if (state.getBlock() instanceof VentFanBlock) {
            Direction front = state.getValue(VentFanBlock.FACING);
            BlockPos fan = intake.pos();
            for (BlockPos at : BlockPos.betweenClosed(fan.offset(-FAN_REACH, -FAN_REACH, -FAN_REACH), fan.offset(FAN_REACH, FAN_REACH, FAN_REACH))) {
                if (at.distSqr(fan) <= FAN_REACH * FAN_REACH && inFront(fan, front, at) && smoking(level, at, level.getBlockState(at))) {
                    found.add(at.immutable());
                }
            }
            return found;
        }
        boolean flue = state.getBlock() instanceof ChimneyBlock; // a flue's open bottom, or a chimney's foot on a fire
        int reach = state.getBlock() instanceof FlueBlock ? FLUE_REACH : flue ? 1 : HOOD_REACH;
        for (int dy = 1; dy <= reach; dy++) {
            BlockPos at = intake.pos().below(dy);
            BlockState there = level.getBlockState(at);
            if (flue ? openFire(level, at, there) : smoking(level, at, there)) {
                found.add(at);
                break;
            }
            if (!there.getCollisionShape(level, at).isEmpty()) {
                break; // a block between: the smoke stays under it
            }
        }
        return found;
    }

    /** Whether {@code at} is in front of the fan at {@code fan} facing {@code front}, not behind or beside it. */
    private static boolean inFront(BlockPos fan, Direction front, BlockPos at) {
        Vec3 to = Vec3.atCenterOf(at).subtract(Vec3.atCenterOf(fan));
        return to.dot(front.getUnitVec3()) > 0.25;
    }

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

    /** Where smoke drawn into {@code intake} comes out, looked for now rather than remembered (the GameTests). */
    public static List<Vec3> findOutlets(Level level, Intake intake) {
        BlockPos exit = intake.exit();
        BlockState first = level.getBlockState(exit);
        if (first.isAir()) {
            return List.of(Vec3.atCenterOf(exit).relative(intake.exitSide(), -0.3));
        }
        if (first.getBlock() instanceof FlueBlock && intake.exitSide().getAxis() != Direction.Axis.Y) {
            return List.of(); // a fan blowing into a flue's side: walled, as a flue is closed but at its top and bottom
        }
        if (first.getBlock() instanceof ChimneyBlock) {
            Vec3 top = chimneyTop(level, exit);
            return top == null ? List.of() : List.of(top);
        }
        if (!(first.getBlock() instanceof DuctBlock) || !DuctBlock.connections(first).contains(intake.exitSide().getOpposite())) {
            return List.of(); // walled up, or the duct parted from it with the Wrench: drawn off all the same, to nowhere
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
     * velocity given: drawn off towards an intake if there is one, else rising as it would. Where it comes out is kept
     * smoking by VentilationClient, which finds every mouth with something smoking under it round the player.
     */
    public static void smoke(Level level, BlockPos source, ParticleOptions particle, double x, double y, double z, double vx, double vy, double vz) {
        Intake intake = intakeFor(level, source);
        if (intake == null) {
            level.addParticle(particle, x, y, z, vx, vy, vz);
            return;
        }
        Vec3 v = drawnTowards(intake.mouth(), x, y, z);
        level.addParticle(particle, x, y, z, v.x, v.y, v.z);
    }

    /** The speed of smoke at ({@code x}, {@code y}, {@code z}) drawn into a mouth at {@code mouth}: faster from further off. */
    public static Vec3 drawnTowards(Vec3 mouth, double x, double y, double z) {
        Vec3 to = mouth.subtract(x, y, z);
        return to.normalize().scale(Math.min(0.14, 0.03 + to.length() * 0.025));
    }

    /** Whether smoke coming out at {@code out} comes out of a chimney's pot or a flue's top (a column) or not (a stream). */
    public static boolean chimneyOutlet(Level level, Vec3 out) {
        return level.getBlockState(BlockPos.containing(out).below()).getBlock() instanceof ChimneyBlock;
    }
}
